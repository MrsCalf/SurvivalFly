package com.survivalfly.client;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

import com.survivalfly.SurvivalFlyConfig;
import com.survivalfly.SurvivalFlyConfig.KeyBindingSetting;

public class SurvivalFlyClient implements ClientModInitializer {
	// In Minecraft 26.2 Minecraft no longer exposes a public `screen` field or
	// getter, so we track the currently open screen ourselves via ScreenEvents.
	private static Screen openScreen = null;

	// Previous "all keys down" state for each binding, used for rising-edge detection.
	private boolean prevFly = false;
	private boolean prevDefly = false;
	private boolean prevSettings = false;

	// Debounce so that pressing a key which is also part of the settings combo
	// (e.g. F7 or F9 when the settings hotkey is F7+F9) does not immediately fire
	// /fly or /defly if the partner key arrives shortly after (to open settings).
	private int pendingAction = 0; // 0 = none, 1 = fly, 2 = defly
	private int pendingTimer = 0;
	private static final int DEBOUNCE_TICKS = 5;

	@Override
	public void onInitializeClient() {
		// Ensure the config is loaded (and the file created) on the client.
		SurvivalFlyConfig.get();

		// Track the currently open screen (26.2 removed Minecraft.screen).
		// AFTER_INIT fires when any screen is initialised; we register a removal
		// listener for that specific screen so we know when it closes.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			openScreen = screen;
			ScreenEvents.remove(screen).register(s -> {
				if (openScreen == s) openScreen = null;
			});
		});

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private static boolean keyDown(Minecraft client, int glfwKey) {
		if (glfwKey < 0) return false;
		long window = client.getWindow().handle();
		return GLFW.glfwGetKey(window, glfwKey) == GLFW.GLFW_PRESS;
	}

	private static boolean bindingDown(Minecraft client, KeyBindingSetting b) {
		if (!b.isSet()) return false;
		if (b.isCombo) {
			return keyDown(client, b.modifier) && keyDown(client, b.key);
		}
		return keyDown(client, b.key);
	}

	private void tick(Minecraft client) {
		LocalPlayer player = client.player;

		// Only react while actually in-game (no screen open). The settings panel
		// has its own key capture and is excluded here. We still refresh the
		// previous-state快照 to avoid spurious edges when a screen closes.
		if (player == null || openScreen != null) {
			SurvivalFlyConfig cfg = SurvivalFlyConfig.get();
			prevFly = bindingDown(client, cfg.flyBinding);
			prevDefly = bindingDown(client, cfg.deflyBinding);
			prevSettings = bindingDown(client, cfg.settingsBinding);
			pendingAction = 0;
			pendingTimer = 0;
			return;
		}

		SurvivalFlyConfig cfg = SurvivalFlyConfig.get();
		boolean flyNow = bindingDown(client, cfg.flyBinding);
		boolean deflyNow = bindingDown(client, cfg.deflyBinding);
		boolean settingsNow = bindingDown(client, cfg.settingsBinding);

		boolean flyEdge = flyNow && !prevFly;
		boolean deflyEdge = deflyNow && !prevDefly;
		boolean settingsEdge = settingsNow && !prevSettings;

		if (settingsEdge) {
			// Open the settings panel and suppress the single-key actions.
			client.setScreenAndShow(new SurvivalFlyConfigScreen(null));
			pendingAction = 0;
			pendingTimer = 0;
		} else if (settingsNow) {
			// Settings combo keys are held -> a settings gesture is in progress;
			// do not fire the single-key actions this tick.
			pendingAction = 0;
			pendingTimer = 0;
		} else {
			KeyBindingSetting settings = cfg.settingsBinding;
			boolean flyShares = settings.isCombo
					&& (settings.modifier == cfg.flyBinding.key || settings.key == cfg.flyBinding.key);
			boolean deflyShares = settings.isCombo
					&& (settings.modifier == cfg.deflyBinding.key || settings.key == cfg.deflyBinding.key);

			if (flyEdge && flyShares) {
				pendingAction = 1;
				pendingTimer = DEBOUNCE_TICKS;
			} else if (deflyEdge && deflyShares) {
				pendingAction = 2;
				pendingTimer = DEBOUNCE_TICKS;
			} else {
				if (flyEdge) sendCommand(player, "fly");
				else if (deflyEdge) sendCommand(player, "defly");
			}
		}

		// Fire a pending single-key action once its debounce window elapses
		// without the settings combo completing.
		if (pendingAction != 0) {
			pendingTimer--;
			if (pendingTimer <= 0) {
				if (pendingAction == 1) sendCommand(player, "fly");
				else if (pendingAction == 2) sendCommand(player, "defly");
				pendingAction = 0;
			}
		}

		prevFly = flyNow;
		prevDefly = deflyNow;
		prevSettings = settingsNow;
	}

	private static void sendCommand(LocalPlayer player, String command) {
		player.connection.sendCommand(command);
	}
}
