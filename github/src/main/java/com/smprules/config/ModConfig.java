package com.smprules.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config", "smprules.json");

    public int peacefulMaxTeamMembers = 3;
    public int pvpMaxTeamMembers = 5;

    // Fixed chunk claim limits per team category
    public int peacefulTeamMaxClaims = 35;
    public int pvpTeamMaxClaims = 20;

    public int pvpOfflineProtectionDelaySeconds = 300; // 5 minutes
    public int combatTagDurationSeconds = 120; // 2 minutes

    public boolean banNetheriteWeaponsAndArmor = true;
    public boolean banMace = true;
    public boolean banEndCrystal = true;
    public boolean banRespawnAnchor = true;

    private static ModConfig INSTANCE;

    public static ModConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                e.printStackTrace();
                INSTANCE = new ModConfig();
            }
        } else {
            INSTANCE = new ModConfig();
            save();
        }
    }

    public static void save() {
        try {
            File dir = CONFIG_FILE.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
