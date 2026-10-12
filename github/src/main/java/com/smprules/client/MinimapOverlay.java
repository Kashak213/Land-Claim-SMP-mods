package com.smprules.client;

import com.smprules.claim.ClaimChunk;
import com.smprules.claim.ClaimManager;
import com.smprules.team.Team;
import com.smprules.team.TeamManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class MinimapOverlay implements HudRenderCallback {

    private static final int MAP_SIZE = 96; // 96x96 pixels minimap
    private static final int HALF_SIZE = MAP_SIZE / 2;

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null || client.world == null || client.options.hudHidden) return;

        int screenWidth = client.getWindow().getScaledWidth();
        int mapX = screenWidth - MAP_SIZE - 10;
        int mapY = 10;

        // 1. Draw Minimap Background & Border
        drawContext.fill(mapX - 2, mapY - 2, mapX + MAP_SIZE + 2, mapY + MAP_SIZE + 2, 0xFF111111);
        drawContext.fill(mapX, mapY, mapX + MAP_SIZE, mapY + MAP_SIZE, 0x88000000);

        BlockPos playerPos = player.getBlockPos();
        String dimension = player.getWorld().getRegistryKey().getValue().toString();

        // 2. Render Team Claim Overlays (No mobs, No players, Only Claims & Terrain)
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;

        for (int cx = centerChunkX - 3; cx <= centerChunkX + 3; cx++) {
            for (int cz = centerChunkZ - 3; cz <= centerChunkZ + 3; cz++) {
                ClaimChunk claim = ClaimManager.getClaim(dimension, cx, cz);
                if (claim != null) {
                    Team owner = TeamManager.getTeam(claim.getTeamName());
                    if (owner != null) {
                        int colorInt = parseHexColor(owner.getColorHex(), 0x66); // 40% opacity fill

                        int chunkMinWorldX = cx * 16;
                        int chunkMinWorldZ = cz * 16;

                        int screenMinX = mapX + HALF_SIZE + (chunkMinWorldX - playerPos.getX());
                        int screenMinZ = mapY + HALF_SIZE + (chunkMinWorldZ - playerPos.getZ());
                        int screenMaxX = screenMinX + 16;
                        int screenMaxZ = screenMinZ + 16;

                        // Clamp to minimap viewport box
                        int clampMinX = Math.max(mapX, screenMinX);
                        int clampMinZ = Math.max(mapY, screenMinZ);
                        int clampMaxX = Math.min(mapX + MAP_SIZE, screenMaxX);
                        int clampMaxZ = Math.min(mapY + MAP_SIZE, screenMaxZ);

                        if (clampMinX < clampMaxX && clampMinZ < clampMaxZ) {
                            drawContext.fill(clampMinX, clampMinZ, clampMaxX, clampMaxZ, colorInt);
                        }
                    }
                }
            }
        }

        // 3. Render Player Pointer (Center Dot)
        int centerX = mapX + HALF_SIZE;
        int centerY = mapY + HALF_SIZE;
        drawContext.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, 0xFFFFFFFF);

        // 4. Render Coordinates Display below Minimap
        String coordsText = String.format("X: %d  Y: %d  Z: %d", playerPos.getX(), playerPos.getY(), playerPos.getZ());
        int textWidth = client.textRenderer.getWidth(coordsText);
        int textX = mapX + HALF_SIZE - (textWidth / 2);
        int textY = mapY + MAP_SIZE + 4;

        drawContext.fill(textX - 4, textY - 2, textX + textWidth + 4, textY + 10, 0xCC000000);
        drawContext.drawText(client.textRenderer, Text.literal(coordsText).formatted(Formatting.YELLOW), textX, textY, 0xFFFFFF, true);
    }

    private int parseHexColor(String hex, int alpha) {
        int r = 51, g = 136, b = 255;
        if (hex != null && hex.startsWith("#") && hex.length() == 7) {
            try {
                r = Integer.parseInt(hex.substring(1, 3), 16);
                g = Integer.parseInt(hex.substring(3, 5), 16);
                b = Integer.parseInt(hex.substring(5, 7), 16);
            } catch (Exception ignored) {}
        }
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }
}
