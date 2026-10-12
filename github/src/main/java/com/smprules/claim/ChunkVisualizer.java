package com.smprules.claim;

import com.smprules.team.Team;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkVisualizer {

    // Track repeating highlights: playerUUID -> tick counter remaining
    private static final Map<UUID, int[]> activeHighlights = new ConcurrentHashMap<>();
    // Store highlight data: playerUUID -> [chunkX, chunkZ, colorHex]
    private static final Map<UUID, Object[]> highlightData = new ConcurrentHashMap<>();

    private static final int HIGHLIGHT_DURATION_TICKS = 200; // 10 seconds at 20 tps
    private static final int HIGHLIGHT_INTERVAL_TICKS = 10;  // Re-render every 0.5 seconds

    public static DustParticleEffect getParticleForColor(String hex) {
        float r = 0.2f, g = 0.6f, b = 1.0f; // Default Blue
        if (hex != null && hex.startsWith("#") && hex.length() == 7) {
            try {
                r = Integer.parseInt(hex.substring(1, 3), 16) / 255.0f;
                g = Integer.parseInt(hex.substring(3, 5), 16) / 255.0f;
                b = Integer.parseInt(hex.substring(5, 7), 16) / 255.0f;
            } catch (Exception ignored) {}
        }
        return new DustParticleEffect(new Vector3f(r, g, b), 2.0f); // Increased particle scale
    }

    public static void highlightChunk(ServerPlayerEntity player, int chunkX, int chunkZ, Team ownerTeam) {
        String hex = (ownerTeam != null) ? ownerTeam.getColorHex() : "#3388FF";

        // Store highlight data and start the repeating timer
        activeHighlights.put(player.getUuid(), new int[]{HIGHLIGHT_DURATION_TICKS});
        highlightData.put(player.getUuid(), new Object[]{chunkX, chunkZ, hex});

        // Render immediately
        renderChunkBorder(player, chunkX, chunkZ, hex);
    }

    /**
     * Called every server tick from SMPRulesMod to re-render active highlights.
     */
    public static void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        int[] counter = activeHighlights.get(uuid);
        if (counter == null) return;

        counter[0]--;
        if (counter[0] <= 0) {
            // Highlight expired
            activeHighlights.remove(uuid);
            highlightData.remove(uuid);
            return;
        }

        // Re-render at intervals
        if (counter[0] % HIGHLIGHT_INTERVAL_TICKS == 0) {
            Object[] data = highlightData.get(uuid);
            if (data != null) {
                renderChunkBorder(player, (int) data[0], (int) data[1], (String) data[2]);
            }
        }
    }

    private static void renderChunkBorder(ServerPlayerEntity player, int chunkX, int chunkZ, String hex) {
        ServerWorld world = player.getServerWorld();
        double playerY = player.getY();
        DustParticleEffect particle = getParticleForColor(hex);

        int minX = chunkX * 16;
        int maxX = minX + 16;
        int minZ = chunkZ * 16;
        int maxZ = minZ + 16;

        // Wider vertical range for better visibility
        for (double y = playerY - 2; y <= playerY + 5; y += 0.5) {
            // North & South borders
            for (int x = minX; x <= maxX; x++) {
                world.spawnParticles(player, particle, true, x + 0.5, y, minZ + 0.5, 1, 0, 0, 0, 0);
                world.spawnParticles(player, particle, true, x + 0.5, y, maxZ + 0.5, 1, 0, 0, 0, 0);
            }

            // East & West borders
            for (int z = minZ; z <= maxZ; z++) {
                world.spawnParticles(player, particle, true, minX + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
                world.spawnParticles(player, particle, true, maxX + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
            }
        }
    }

    public static void onPlayerDisconnect(UUID uuid) {
        activeHighlights.remove(uuid);
        highlightData.remove(uuid);
    }
}
