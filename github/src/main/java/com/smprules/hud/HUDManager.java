package com.smprules.hud;

import com.smprules.claim.ClaimChunk;
import com.smprules.claim.ClaimManager;
import com.smprules.combat.CombatTracker;
import com.smprules.team.Team;
import com.smprules.team.TeamManager;
import com.smprules.team.TeamColors;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HUDManager {
    private static final Map<UUID, String> lastPlayerChunkKey = new HashMap<>();
    private static final Set<UUID> hiddenCoordsPlayers = new HashSet<>();

    public static void tickPlayer(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        ChunkPos chunkPos = player.getChunkPos();
        String currentChunkKey = dimension + ":" + chunkPos.x + ":" + chunkPos.z;

        // Check if player crossed into a new chunk
        String lastKey = lastPlayerChunkKey.put(uuid, currentChunkKey);
        if (lastKey != null && !lastKey.equalsIgnoreCase(currentChunkKey)) {
            onChunkChange(player, dimension, chunkPos.x, chunkPos.z);
        }

        // Build bottom-of-screen Actionbar text
        Text hudText = buildHUDText(player, dimension, chunkPos.x, chunkPos.z);
        player.sendMessage(hudText, true); // Actionbar overlay (bottom of screen above hotbar)
    }

    private static Text buildHUDText(ServerPlayerEntity player, String dimension, int chunkX, int chunkZ) {
        long combatSeconds = CombatTracker.getRemainingTagSeconds(player.getUuid());
        Team team = TeamManager.getPlayerTeam(player.getUuid());
        ClaimChunk claim = ClaimManager.getClaim(dimension, chunkX, chunkZ);

        // If player is combat tagged, prioritize bold combat timer at bottom of screen
        if (combatSeconds > 0) {
            long minutes = combatSeconds / 60;
            long seconds = combatSeconds % 60;
            String timeStr = String.format("%02d:%02d", minutes, seconds);

            Text combatText = Text.literal("⚔ COMBAT: " + timeStr).formatted(Formatting.RED, Formatting.BOLD);
            if (team != null) {
                combatText = Text.empty().append(combatText).append(Text.literal(" | " + team.getName()).styled(s -> s.withColor(TeamColors.fromTeam(team))));
            }
            return combatText;
        }

        // Normal HUD: Team Box + Claim Location status
        String locationInfo;
        Formatting locationColor;

        if (claim != null) {
            Team claimOwner = TeamManager.getTeam(claim.getTeamName());
            String ownerColor = (claimOwner != null) ? claimOwner.getColorName() : "WHITE";
            locationInfo = "Claim: " + claim.getTeamName() + " [" + ownerColor + "]";
            locationColor = Formatting.AQUA;
        } else {
            locationInfo = "Wilderness";
            locationColor = Formatting.GRAY;
        }

        MutableText hudText = Text.empty();
        
        // Add coordinates to the beginning, unless hidden
        if (!hiddenCoordsPlayers.contains(player.getUuid())) {
            hudText = hudText.append(Text.literal(String.format("X: %d  Y: %d  Z: %d   |   ", 
                player.getBlockX(), player.getBlockY(), player.getBlockZ())).formatted(Formatting.WHITE));
        }

        if (team != null) {
            hudText = hudText.append(Text.literal("[" + team.getName() + "] ").styled(s -> s.withColor(TeamColors.fromTeam(team))));
        }
        hudText = hudText.append(Text.literal(locationInfo).formatted(locationColor));
        return hudText;
    }

    private static void onChunkChange(ServerPlayerEntity player, String dimension, int chunkX, int chunkZ) {
        ClaimChunk claim = ClaimManager.getClaim(dimension, chunkX, chunkZ);
        if (claim != null) {
            Team owner = TeamManager.getTeam(claim.getTeamName());
            String colorName = (owner != null) ? owner.getColorName() : "AQUA";
            String typeName = (owner != null) ? owner.getType().name() : "CLAIM";
            player.sendMessage(
                Text.empty()
                    .append(Text.literal("🟦 Entering Claim: ").formatted(Formatting.AQUA, Formatting.BOLD))
                    .append(Text.literal(claim.getTeamName()).styled(s -> s.withColor(owner != null ? TeamColors.fromTeam(owner) : TextColor.fromFormatting(Formatting.AQUA)).withBold(true)))
                    .append(Text.literal(" (" + colorName + " | " + typeName + ")").formatted(Formatting.AQUA, Formatting.BOLD)),
                false  // Regular chat message (actionbar is reserved for the HUD)
            );
        }
    }

    public static void onPlayerDisconnect(UUID uuid) {
        lastPlayerChunkKey.remove(uuid);
        // We can choose to keep or clear their preference on disconnect.
        // For now, keeping it in memory until server restarts is fine.
    }

    public static void toggleCoords(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (hiddenCoordsPlayers.contains(uuid)) {
            hiddenCoordsPlayers.remove(uuid);
            player.sendMessage(Text.literal("✅ Coordinates are now VISIBLE on your HUD.").formatted(Formatting.GREEN), false);
        } else {
            hiddenCoordsPlayers.add(uuid);
            player.sendMessage(Text.literal("❌ Coordinates are now HIDDEN from your HUD.").formatted(Formatting.RED), false);
        }
    }
}
