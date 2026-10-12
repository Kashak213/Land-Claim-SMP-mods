package com.smprules.claim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.smprules.admin.StaffModeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class SpawnProtectionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<UUID> spawnBuilders = new HashSet<>();
    private static File saveFile;
    private static int spawnRadiusChunks = 4; // 9x9 chunk area
    private static boolean spawnProtectionEnabled = true;

    public static void init(MinecraftServer server) {
        File dataDir = server.getRunDirectory().resolve("world/smprules").toFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        saveFile = new File(dataDir, "spawn_builders.json");
        load();
    }

    public static boolean isSpawnProtectionEnabled() {
        return spawnProtectionEnabled;
    }

    public static boolean toggleSpawnProtection() {
        spawnProtectionEnabled = !spawnProtectionEnabled;
        return spawnProtectionEnabled;
    }

    public static boolean isInsideSpawnProtection(ServerWorld world, int chunkX, int chunkZ) {
        if (!spawnProtectionEnabled) {
            return false;
        }

        if (!world.getRegistryKey().getValue().toString().equalsIgnoreCase("minecraft:overworld")) {
            return false;
        }

        BlockPos spawnPos = world.getSpawnPos();
        int spawnChunkX = spawnPos.getX() >> 4;
        int spawnChunkZ = spawnPos.getZ() >> 4;

        return Math.abs(chunkX - spawnChunkX) <= spawnRadiusChunks &&
               Math.abs(chunkZ - spawnChunkZ) <= spawnRadiusChunks;
    }

    public static boolean canBuildInSpawn(ServerPlayerEntity player, ServerWorld world, int chunkX, int chunkZ) {
        if (!isInsideSpawnProtection(world, chunkX, chunkZ)) {
            return true;
        }

        if (player.hasPermissionLevel(2) || StaffModeManager.isStaffModeActive(player.getUuid())) {
            return true;
        }

        return spawnBuilders.contains(player.getUuid());
    }

    public static boolean allowSpawnBuilder(UUID playerUuid) {
        boolean added = spawnBuilders.add(playerUuid);
        if (added) {
            System.out.println("[SpawnProtection] Granted spawn builder: " + playerUuid + " | Total builders: " + spawnBuilders.size());
            save();
        }
        return added;
    }

    public static boolean revokeSpawnBuilder(UUID playerUuid) {
        boolean removed = spawnBuilders.remove(playerUuid);
        if (removed) {
            System.out.println("[SpawnProtection] Revoked spawn builder: " + playerUuid + " | Total builders: " + spawnBuilders.size());
            save();
        }
        return removed;
    }

    public static void resetAll() {
        spawnBuilders.clear();
        spawnProtectionEnabled = true;
        save();
    }

    public static Set<UUID> getSpawnBuilders() {
        return spawnBuilders;
    }

    /** Save UUIDs as strings — Gson cannot deserialize UUID objects directly */
    public static void save() {
        if (saveFile == null) return;
        try (FileWriter writer = new FileWriter(saveFile)) {
            Set<String> asStrings = spawnBuilders.stream()
                .map(UUID::toString)
                .collect(Collectors.toSet());
            GSON.toJson(asStrings, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Load UUIDs from string representation */
    public static void load() {
        if (saveFile == null || !saveFile.exists()) return;
        try (FileReader reader = new FileReader(saveFile)) {
            Type setType = new TypeToken<Set<String>>() {}.getType();
            Set<String> loaded = GSON.fromJson(reader, setType);
            if (loaded != null) {
                spawnBuilders.clear();
                for (String s : loaded) {
                    try {
                        spawnBuilders.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {
                        System.out.println("[SpawnProtection] Skipping invalid UUID in save file: " + s);
                    }
                }
            }
            System.out.println("[SpawnProtection] Loaded " + spawnBuilders.size() + " spawn builder(s).");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
