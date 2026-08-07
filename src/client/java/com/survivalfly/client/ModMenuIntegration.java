package com.survivalfly.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.minecraft.client.gui.screens.Screen;

/**
 * Integrates SurvivalFly's settings panel with Mod Menu. Mod Menu calls
 * getConfigScreenFactory() to obtain the screen shown when the user clicks the
 * mod's "Config" button in the Mod Menu list.
 *
 * This class references Mod Menu APIs, so it is compiled against Mod Menu
 * (modCompileOnly) and only loaded when Mod Menu is actually installed (the
 * "modmenu" entrypoint is only requested by Mod Menu itself).
 *
 * The raw ConfigScreenFactory type is used so this compiles regardless of
 * whether Mod Menu declares the factory as generic or raw.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return (Screen parent) -> new SurvivalFlyConfigScreen(parent);
	}
}
