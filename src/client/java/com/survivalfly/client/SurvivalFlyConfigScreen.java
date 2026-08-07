package com.survivalfly.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;
import com.survivalfly.SurvivalFlyConfig;
import com.survivalfly.SurvivalFlyConfig.KeyBindingSetting;

/**
 * Settings panel built entirely from Button widgets. In Minecraft 26.2 the GUI
 * renderer was rewritten (no GuiGraphics / drawCenteredString), but widgets such
 * as Button still render and lay out themselves, so we avoid any custom drawing
 * and convey all text through button labels. The inherited background is used.
 *
 * All user-facing strings are translation keys (survivalfly.screen.*) so the
 * panel is localised via the mod's language files.
 */
public class SurvivalFlyConfigScreen extends Screen {
	private final Screen parent;

	// Capture state: which binding is being (re)assigned.
	private static final int NONE = 0;
	private static final int FLY = 1;
	private static final int DEFLY = 2;
	private static final int SETTINGS = 3;
	private int capturing = NONE;
	private int capturedFirst = -1;
	private int captureStartTick = 0;
	private int tickCounter = 0;

	// How many ticks to wait for a possible second key before committing a single key.
	private static final int CAPTURE_TIMEOUT = 40;

	private Button toggleBtn;
	private Button flyBtn;
	private Button deflyBtn;
	private Button settingsBtn;

	public SurvivalFlyConfigScreen(Screen parent) {
		super(Component.translatable("survivalfly.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int btnW = 320;
		int btnH = 20;
		int y = 50;

		toggleBtn = Button.builder(toggleLabel(), b -> {
			SurvivalFlyConfig cfg = SurvivalFlyConfig.get();
			cfg.landingRevokeEnabled = !cfg.landingRevokeEnabled;
			cfg.save();
			b.setMessage(toggleLabel());
		}).bounds(centerX - btnW / 2, y, btnW, btnH).build();
		this.addRenderableWidget(toggleBtn);
		y += 32;

		flyBtn = Button.builder(flyLabel(), b -> startCapture(FLY))
				.bounds(centerX - btnW / 2, y, btnW, btnH).build();
		this.addRenderableWidget(flyBtn);
		y += 28;

		deflyBtn = Button.builder(deflyLabel(), b -> startCapture(DEFLY))
				.bounds(centerX - btnW / 2, y, btnW, btnH).build();
		this.addRenderableWidget(deflyBtn);
		y += 28;

		settingsBtn = Button.builder(settingsLabel(), b -> startCapture(SETTINGS))
				.bounds(centerX - btnW / 2, y, btnW, btnH).build();
		this.addRenderableWidget(settingsBtn);
		y += 40;

		Button done = Button.builder(Component.translatable("survivalfly.screen.done"), b -> this.onClose())
				.bounds(centerX - 60, y, 120, btnH).build();
		this.addRenderableWidget(done);
	}

	private Component toggleLabel() {
		return Component.translatable("survivalfly.screen.toggle.label")
				.append(Component.literal(": "))
				.append(Component.translatable(SurvivalFlyConfig.get().landingRevokeEnabled
						? "survivalfly.screen.state.on" : "survivalfly.screen.state.off"));
	}

	private Component flyLabel() {
		return Component.translatable("survivalfly.screen.key.fly")
				.append(Component.literal(": " + bindingText(SurvivalFlyConfig.get().flyBinding)));
	}

	private Component deflyLabel() {
		return Component.translatable("survivalfly.screen.key.defly")
				.append(Component.literal(": " + bindingText(SurvivalFlyConfig.get().deflyBinding)));
	}

	private Component settingsLabel() {
		return Component.translatable("survivalfly.screen.key.settings")
				.append(Component.literal(": " + bindingText(SurvivalFlyConfig.get().settingsBinding)));
	}

	private static String bindingText(KeyBindingSetting b) {
		if (!b.isSet()) return Component.translatable("survivalfly.screen.unset").getString();
		if (b.isCombo) {
			return keyName(b.modifier) + " + " + keyName(b.key);
		}
		return keyName(b.key);
	}

	private static String keyName(int code) {
		if (code < 0) return "?";
		return InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
	}

	private void startCapture(int which) {
		capturing = which;
		capturedFirst = -1;
		captureStartTick = tickCounter;
		updateCaptureLabels();
	}

	private void updateCaptureLabels() {
		Component prompt = Component.translatable("survivalfly.screen.capture.prompt");
		if (capturing == FLY) flyBtn.setMessage(prompt);
		else if (capturing == DEFLY) deflyBtn.setMessage(prompt);
		else if (capturing == SETTINGS) settingsBtn.setMessage(prompt);
	}

	private void refreshLabels() {
		toggleBtn.setMessage(toggleLabel());
		flyBtn.setMessage(flyLabel());
		deflyBtn.setMessage(deflyLabel());
		settingsBtn.setMessage(settingsLabel());
	}

	private void setBinding(int which, boolean combo, int modifier, int key) {
		SurvivalFlyConfig cfg = SurvivalFlyConfig.get();
		KeyBindingSetting b;
		if (which == FLY) b = cfg.flyBinding;
		else if (which == DEFLY) b = cfg.deflyBinding;
		else b = cfg.settingsBinding;

		b.isCombo = combo;
		if (combo) {
			b.modifier = modifier;
			b.key = key;
		} else {
			b.modifier = -1;
			b.key = modifier; // single key is stored in 'key'
		}
		cfg.save();
		capturing = NONE;
		refreshLabels();
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		int key = keyEvent.key();

		if (capturing != NONE) {
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				capturing = NONE;
				refreshLabels();
				return true;
			}
			if (capturedFirst == -1) {
				capturedFirst = key;
				captureStartTick = tickCounter;
			} else if (key != capturedFirst) {
				// Second distinct key -> combo binding.
				setBinding(capturing, true, capturedFirst, key);
			} else {
				// Same key again -> single binding.
				setBinding(capturing, false, capturedFirst, -1);
			}
			return true;
		}

		if (key == GLFW.GLFW_KEY_ESCAPE) {
			this.onClose();
			return true;
		}
		return super.keyPressed(keyEvent);
	}

	@Override
	public void tick() {
		tickCounter++;
		if (capturing != NONE && capturedFirst != -1
				&& (tickCounter - captureStartTick) > CAPTURE_TIMEOUT) {
			// No second key arrived -> commit as a single-key binding.
			setBinding(capturing, false, capturedFirst, -1);
		}
	}

	@Override
	public void onClose() {
		SurvivalFlyConfig.get().save();
		Minecraft.getInstance().setScreenAndShow(parent);
	}
}
