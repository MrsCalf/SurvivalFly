package com.survivalfly;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SurvivalFlyMod implements ModInitializer {
	public static final String MOD_ID = "survivalfly";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Track players who have been granted flight permission by the mod.
	// The boolean value records whether the player has flown at least once
	// since being granted permission, so we only revoke on an actual landing.
	private static final Map<UUID, Boolean> trackedPlayers = new HashMap<>();

	@Override
	public void onInitialize() {
		LOGGER.info("SurvivalFly mod initializing!");

		// Load (and create if missing) the shared config so the server-side
		// auto-revoke logic can honour the landing-revoke toggle. In a
		// single-player game this is the same instance the client panel edits.
		SurvivalFlyConfig.get();

		// Register /fly command - grants flight permission in survival mode
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("fly")
					.executes(SurvivalFlyMod::executeFly));
		});

		// Register /defly command - revokes flight permission
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("defly")
					.executes(SurvivalFlyMod::executeDefly));
		});

		// Register server tick event to detect when a player stops flying
		ServerTickEvents.END_SERVER_TICK.register(SurvivalFlyMod::checkFlyingPlayers);
	}

	/**
	 * /fly command - grants flight permission in survival/adventure mode.
	 * When the player flies up and then lands (stops flying), the permission
	 * is automatically revoked.
	 */
	private static int executeFly(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();

		// Don't allow in creative or spectator mode
		if (player.isCreative()) {
			source.sendSuccess(() -> Component.translatable("survivalfly.msg.creative").withStyle(ChatFormatting.YELLOW), false);
			return 0;
		}
		if (player.isSpectator()) {
			source.sendSuccess(() -> Component.translatable("survivalfly.msg.spectator").withStyle(ChatFormatting.YELLOW), false);
			return 0;
		}

		UUID uuid = player.getUUID();

		// Check if player already has flight permission from this mod
		if (trackedPlayers.containsKey(uuid)) {
			source.sendSuccess(() -> Component.translatable("survivalfly.msg.fly.already").withStyle(ChatFormatting.YELLOW), false);
			return 0;
		}

		// Grant flight permission
		Abilities abilities = player.getAbilities();
		abilities.mayfly = true;
		player.onUpdateAbilities();

		// Track the player - not currently flying
		trackedPlayers.put(uuid, false);

		source.sendSuccess(() -> Component.translatable(
				"survivalfly.msg.fly.granted").withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	/**
	 * /defly command - revokes flight permission if the player has it.
	 */
	private static int executeDefly(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();

		UUID uuid = player.getUUID();

		if (!trackedPlayers.containsKey(uuid)) {
			source.sendSuccess(() -> Component.translatable("survivalfly.msg.defly.none").withStyle(ChatFormatting.RED), false);
			return 0;
		}

		// Revoke flight permission
		Abilities abilities = player.getAbilities();
		abilities.flying = false;
		abilities.mayfly = false;
		player.onUpdateAbilities();

		// Remove from tracking
		trackedPlayers.remove(uuid);

		source.sendSuccess(() -> Component.translatable("survivalfly.msg.defly.revoked").withStyle(ChatFormatting.YELLOW), true);
		return 1;
	}

	/**
	 * Server tick handler - monitors tracked players for the landing event.
	 * When a tracked player (who has flown at least once) actually touches the
	 * ground while not flying, their flight permission is automatically revoked.
	 * Free-falling after stopping flight does NOT revoke the permission - only a
	 * real landing does.
	 */
	private static void checkFlyingPlayers(MinecraftServer server) {
		if (trackedPlayers.isEmpty()) return;

		// Respect the landing auto-revoke toggle from the shared config.
		boolean autoRevoke = SurvivalFlyConfig.get().landingRevokeEnabled;

		// Iterate over a copy of the entries to avoid ConcurrentModificationException
		Map<UUID, Boolean> snapshot = new HashMap<>(trackedPlayers);

		for (Map.Entry<UUID, Boolean> entry : snapshot.entrySet()) {
			UUID uuid = entry.getKey();
			boolean hasFlown = entry.getValue();

			ServerPlayer player = server.getPlayerList().getPlayer(uuid);

			// Player disconnected or changed to creative/spectator mode - clean up
			if (player == null || player.isCreative() || player.isSpectator()) {
				trackedPlayers.remove(uuid);
				continue;
			}

			boolean isFlying = player.getAbilities().flying;

			if (!hasFlown && isFlying) {
				// Player just started flying - mark as flown so that we can
				// revoke the permission once they actually land.
				trackedPlayers.put(uuid, true);
			} else if (autoRevoke && hasFlown && !isFlying && player.onGround()) {
				// Player has flown before, is now no longer flying, and is
				// actually touching the ground (landed). Free-fall after
				// stopping flight does NOT revoke the permission - only a
				// real landing does. Revoke the flight permission now.
				Abilities abilities = player.getAbilities();
				abilities.mayfly = false;
				player.onUpdateAbilities();

				trackedPlayers.remove(uuid);

				player.sendSystemMessage(Component.translatable(
						"survivalfly.msg.landing.revoked").withStyle(ChatFormatting.YELLOW));
			}
		}
	}
}
