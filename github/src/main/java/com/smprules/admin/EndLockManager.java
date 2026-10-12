package com.smprules.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;

public class EndLockManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean endLocked = true; // Locked by default for launch!
    private static File saveFile;

    public static void init(MinecraftServer server) {
        File dataDir = server.getRunDirectory().resolve("world/smprules").toFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        saveFile = new File(dataDir, "end_lock.json");
        load();
    }

    public static boolean isEndLocked() {
        return endLocked;
    }

    public static void setEndLocked(MinecraftServer server, boolean locked) {
        endLocked = locked;
        save();

        if (server != null) {
            if (locked) {
                server.getPlayerManager().broadcast(
                    Text.literal("🔒 [The End] The End Dimension has been LOCKED by the Admin.").formatted(Formatting.RED, Formatting.BOLD),
                    false
                );
            } else {
                server.getPlayerManager().broadcast(
                    Text.literal("🌌 [The End] The End Dimension has been officially UNLOCKED by the Admin! The End Portals are now OPEN!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                    false
                );
            }
        }
    }

    public static boolean checkPlayerEndAccess(ServerPlayerEntity player) {
        if (!endLocked) {
            return true; // Unlocked
        }

        // OP / Staff Mode bypass
        if (player.hasPermissionLevel(2) || StaffModeManager.isStaffModeActive(player.getUuid())) {
            return true;
        }

        // Check if player is standing in End Portal block or attempting to enter The End
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        if (dimension.equalsIgnoreCase("minecraft:the_end")) {
            teleportPlayerAwayFromEnd(player);
            return false;
        }

        BlockPos pos = player.getBlockPos();
        if (player.getWorld().getBlockState(pos).isOf(Blocks.END_PORTAL) || player.getWorld().getBlockState(pos.up()).isOf(Blocks.END_PORTAL)) {
            teleportPlayerAwayFromEnd(player);
            return false;
        }

        return true;
    }

    private static void teleportPlayerAwayFromEnd(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            ServerWorld overworld = server.getOverworld();
            BlockPos spawn = overworld.getSpawnPos();
            player.teleport(overworld, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5, java.util.Set.of(), player.getYaw(), player.getPitch());
            player.sendMessage(
                Text.literal("⛔ [The End] The End dimension is currently LOCKED! The server admin will unlock it soon.")
                    .formatted(Formatting.RED, Formatting.BOLD),
                true
            );
        }
    }

    public static synchronized void save() {
        if (saveFile == null) return;
        try (FileWriter writer = new FileWriter(saveFile)) {
            GSON.toJson(endLocked, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void load() {
        if (saveFile == null || !saveFile.exists()) return;
        try (FileReader reader = new FileReader(saveFile)) {
            Type type = new TypeToken<Boolean>() {}.getType();
            Boolean loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                endLocked = loaded;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
