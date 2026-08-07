package com.survivalfly;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client-side configuration for SurvivalFly.
 *
 * The config file lives in the shared Fabric config directory. In a single-player
 * (integrated server) game the client and server share the same JVM/ClassLoader,
 * so they also share this same in-memory instance - which means toggling
 * "landing auto-revoke" in the settings panel takes effect on the server-side
 * auto-revoke logic immediately, with no networking required.
 *
 * NOTE: this class must NOT reference LWJGL/GLFW directly, because it is loaded
 * on dedicated servers where those natives are unavailable. Key codes are stored
 * as plain GLFW integer constants instead.
 */
public class SurvivalFlyConfig {
	// GLFW key codes kept as plain ints so this class stays server-safe.
	public static final int GLFW_KEY_F7 = 296; // GLFW.GLFW_KEY_F7
	public static final int GLFW_KEY_F9 = 298; // GLFW.GLFW_KEY_F9

	/** When true (default) flight permission is auto-revoked on landing. */
	public boolean landingRevokeEnabled = true;

	/** Key that runs /fly. */
	public KeyBindingSetting flyBinding = new KeyBindingSetting(false, GLFW_KEY_F7, -1);
	/** Key that runs /defly. */
	public KeyBindingSetting deflyBinding = new KeyBindingSetting(false, GLFW_KEY_F9, -1);
	/** Key that opens the settings panel. */
	public KeyBindingSetting settingsBinding = new KeyBindingSetting(true, GLFW_KEY_F7, GLFW_KEY_F9);

	public static class KeyBindingSetting {
		/** true = combo (modifier + key), false = single key. */
		public boolean isCombo;
		/** For single bindings: the key. For combos: the main key. */
		public int key;
		/** For combos: the modifier key. -1 for single bindings. */
		public int modifier;

		public KeyBindingSetting() {
		}

		public KeyBindingSetting(boolean isCombo, int key, int modifier) {
			this.isCombo = isCombo;
			this.key = key;
			this.modifier = modifier;
		}

		public boolean isSet() {
			return key != -1;
		}
	}

	private static final Path CONFIG_PATH = FabricLoader.getInstance()
			.getConfigDir().resolve("survivalfly.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static SurvivalFlyConfig instance;

	public static SurvivalFlyConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static SurvivalFlyConfig load() {
		try {
			if (Files.exists(CONFIG_PATH)) {
				String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
				instance = GSON.fromJson(json, SurvivalFlyConfig.class);
			}
		} catch (Exception ignored) {
			// fall through to defaults
		}
		if (instance == null) {
			instance = new SurvivalFlyConfig();
			instance.save();
		}
		// Defensive: make sure nested objects exist after a partial/old config.
		if (instance.flyBinding == null) instance.flyBinding = new KeyBindingSetting(false, GLFW_KEY_F7, -1);
		if (instance.deflyBinding == null) instance.deflyBinding = new KeyBindingSetting(false, GLFW_KEY_F9, -1);
		if (instance.settingsBinding == null) instance.settingsBinding = new KeyBindingSetting(true, GLFW_KEY_F7, GLFW_KEY_F9);
		return instance;
	}

	public void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
			// best-effort persistence
		}
	}
}
