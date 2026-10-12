package com.smprules.combat;

import com.smprules.config.ModConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatTracker {
    private static final Map<UUID, Long> combatTagMap = new ConcurrentHashMap<>();

    public static void tagPlayer(ServerPlayerEntity player) {
        if (player != null) {
            combatTagMap.put(player.getUuid(), System.currentTimeMillis());

            // Instantly stop Elytra flight if player was gliding when tagged
            if (player.isFallFlying()) {
                player.stopFallFlying();
                player.sendMessage(Text.literal("⛔ [Combat Tag] Elytra flight disabled! You have been tagged in PvP combat.").formatted(Formatting.RED, Formatting.BOLD), true);
            }
        }
    }

    public static boolean isCombatTagged(UUID uuid) {
        if (uuid == null) return false;
        Long timestamp = combatTagMap.get(uuid);
        if (timestamp == null) return false;

        long durationMs = ModConfig.get().combatTagDurationSeconds * 1000L;
        if (System.currentTimeMillis() - timestamp < durationMs) {
            return true;
        } else {
            combatTagMap.remove(uuid);
            return false;
        }
    }

    public static long getRemainingTagSeconds(UUID uuid) {
        if (uuid == null) return 0;
        Long timestamp = combatTagMap.get(uuid);
        if (timestamp == null) return 0;

        long durationMs = ModConfig.get().combatTagDurationSeconds * 1000L;
        long elapsed = System.currentTimeMillis() - timestamp;
        if (elapsed < durationMs) {
            return (durationMs - elapsed) / 1000;
        }
        return 0;
    }

    public static void removeTag(UUID uuid) {
        if (uuid != null) {
            combatTagMap.remove(uuid);
        }
    }

    public static void checkElytraCombatRestriction(ServerPlayerEntity player) {
        if (player == null) return;
        if (isCombatTagged(player.getUuid())) {
            if (player.isFallFlying()) {
                player.stopFallFlying();
                player.sendMessage(Text.literal("⛔ [Combat Tag] Elytra flight is disabled during PvP combat!").formatted(Formatting.RED, Formatting.BOLD), true);
            }
        }
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        if (player == null) return;
        UUID uuid = player.getUuid();

        if (isCombatTagged(uuid)) {
            combatTagMap.remove(uuid);

            MinecraftServer server = player.getServer();
            if (server != null) {
                server.getPlayerManager().broadcast(
                    Text.literal("⚔ [Combat Log] " + player.getName().getString() + " logged out during combat and was eliminated!")
                        .formatted(Formatting.RED, Formatting.BOLD),
                    false
                );
            }

            // Drop all inventory items
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (!stack.isEmpty()) {
                    player.dropItem(stack.copy(), true, false);
                    player.getInventory().setStack(i, ItemStack.EMPTY);
                }
            }

            // Kill player
            player.kill();
        }
    }
}
