package com.smprules.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

public class PlayerMemory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File saveFile;

    // Map of Lowercase Name -> UUID string (for finding players by name)
    private static final Map<String, UUID> nameToUuid = new HashMap<>();

    // Map of UUID -> Original Cased Name
    private static final Map<UUID, String> uuidToName = new HashMap<>();

    public static void init(MinecraftServer server) {
        File dataDir = server.getRunDirectory().resolve("world/smprules").toFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        saveFile = new File(dataDir, "player_memory.json");
        load();
    }

    public static void onPlayerJoin(ServerPlayerEntity player) {
        if (player == null) return;
        String name = player.getName().getString();
        UUID uuid = player.getUuid();

        nameToUuid.put(name.toLowerCase(), uuid);
        uuidToName.put(uuid, name);
        save();
        System.out.println("[PlayerMemory] Recorded player: " + name + " -> " + uuid);
    }

    public static Set<String> getAllKnownNames() {
        // Return original-cased names for display
        return new HashSet<>(uuidToName.values());
    }

    public static UUID getUuidByName(String name) {
        if (name == null) return null;
        return nameToUuid.get(name.toLowerCase());
    }

    public static String getNameByUuid(UUID uuid) {
        return uuidToName.get(uuid);
    }

    /**
     * Save as Map<String, String> (name -> uuid string) to avoid Gson UUID key serialization issues.
     * Gson cannot deserialize UUID map keys — it stores them as strings but can't parse them back.
     */
    private static synchronized void save() {
        if (saveFile == null) return;
        try (FileWriter writer = new FileWriter(saveFile)) {
            // Save as name (lowercase) -> uuid string
            Map<String, String> toSave = new HashMap<>();
            for (Map.Entry<UUID, String> entry : uuidToName.entrySet()) {
                toSave.put(entry.getKey().toString(), entry.getValue());
            }
            GSON.toJson(toSave, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Load from Map<String, String> (uuid string -> name) and rebuild both maps.
     */
    private static synchronized void load() {
        if (saveFile == null || !saveFile.exists()) return;
        try (FileReader reader = new FileReader(saveFile)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                nameToUuid.clear();
                uuidToName.clear();
                for (Map.Entry<String, String> entry : loaded.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        String name = entry.getValue();
                        uuidToName.put(uuid, name);
                        nameToUuid.put(name.toLowerCase(), uuid);
                    } catch (IllegalArgumentException e) {
                        System.out.println("[PlayerMemory] Skipping invalid UUID entry: " + entry.getKey());
                    }
                }
                System.out.println("[PlayerMemory] Loaded " + uuidToName.size() + " known player(s).");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
