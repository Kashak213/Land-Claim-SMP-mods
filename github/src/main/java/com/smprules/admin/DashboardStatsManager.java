package com.smprules.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DashboardStatsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, PlayerStats> playerStatsMap = new HashMap<>();
    private static final List<ActivityLogEntry> activityLogs = new ArrayList<>();
    private static final Map<UUID, Long> sessionStartTimes = new HashMap<>();
    private static File statsFile;
    private static File activityLogsFile;

    public static class PlayerStats {
        public String playerName;
        public int deaths = 0;
        public int pvpKills = 0;
        public long totalPlaytimeMs = 0;

        public PlayerStats(String playerName) {
            this.playerName = playerName;
        }
    }

    public static class ActivityLogEntry {
        public String timestamp;
        public String type; // "PVP_KILL", "DEATH", "SESSION"
        public String description;

        public ActivityLogEntry(String timestamp, String type, String description) {
            this.timestamp = timestamp;
            this.type = type;
            this.description = description;
        }
    }

    public static void init(MinecraftServer server) {
        File dataDir = server.getRunDirectory().resolve("world/smprules").toFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        statsFile = new File(dataDir, "player_stats.json");
        activityLogsFile = new File(dataDir, "activity_logs.json");
        load();
    }

    public static void onPlayerJoin(ServerPlayerEntity player) {
        if (player == null) return;
        sessionStartTimes.put(player.getUuid(), System.currentTimeMillis());
        playerStatsMap.computeIfAbsent(player.getUuid(), k -> new PlayerStats(player.getName().getString())).playerName = player.getName().getString();
        save();
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        if (player == null) return;
        UUID uuid = player.getUuid();
        Long startTime = sessionStartTimes.remove(uuid);

        if (startTime != null) {
            long sessionDurationMs = System.currentTimeMillis() - startTime;
            PlayerStats stats = playerStatsMap.computeIfAbsent(uuid, k -> new PlayerStats(player.getName().getString()));
            stats.totalPlaytimeMs += sessionDurationMs;

            String formattedDuration = formatDuration(sessionDurationMs);
            String timestamp = LocalDateTime.now(ZoneId.of("America/New_York")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            activityLogs.add(0, new ActivityLogEntry(timestamp, "SESSION", "🚪 " + player.getName().getString() + " logged off. Session Duration: " + formattedDuration));
            save();
        }
    }

    public static void onPlayerDeath(ServerPlayerEntity victim, DamageSource damageSource) {
        if (victim == null) return;
        UUID victimUuid = victim.getUuid();
        PlayerStats victimStats = playerStatsMap.computeIfAbsent(victimUuid, k -> new PlayerStats(victim.getName().getString()));
        victimStats.deaths++;

        String timestamp = LocalDateTime.now(ZoneId.of("America/New_York")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String location = String.format("[%s] X:%d Y:%d Z:%d", 
            victim.getWorld().getRegistryKey().getValue().toString(), 
            victim.getBlockX(), victim.getBlockY(), victim.getBlockZ());

        if (damageSource != null && damageSource.getAttacker() instanceof ServerPlayerEntity attacker) {
            UUID attackerUuid = attacker.getUuid();
            PlayerStats attackerStats = playerStatsMap.computeIfAbsent(attackerUuid, k -> new PlayerStats(attacker.getName().getString()));
            attackerStats.pvpKills++;

            String pvpLog = String.format("⚔ %s killed %s at %s", attacker.getName().getString(), victim.getName().getString(), location);
            activityLogs.add(0, new ActivityLogEntry(timestamp, "PVP_KILL", pvpLog));

        } else {
            String deathMsg = (damageSource != null) ? damageSource.getName() : "Unknown";
            String deathLog = String.format("💀 %s died (%s) at %s", victim.getName().getString(), deathMsg, location);
            activityLogs.add(0, new ActivityLogEntry(timestamp, "DEATH", deathLog));
        }

        save();
    }

    public static List<ActivityLogEntry> getActivityLogs() {
        return activityLogs;
    }

    public static Map<UUID, PlayerStats> getPlayerStatsMap() {
        return playerStatsMap;
    }

    public static String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        minutes = minutes % 60;
        seconds = seconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    public static synchronized void save() {
        try {
            if (statsFile != null) {
                try (FileWriter writer = new FileWriter(statsFile)) {
                    GSON.toJson(playerStatsMap, writer);
                }
            }
            if (activityLogsFile != null) {
                try (FileWriter writer = new FileWriter(activityLogsFile)) {
                    GSON.toJson(activityLogs, writer);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void load() {
        try {
            if (statsFile != null && statsFile.exists()) {
                try (FileReader reader = new FileReader(statsFile)) {
                    Type mapType = new TypeToken<Map<UUID, PlayerStats>>() {}.getType();
                    Map<UUID, PlayerStats> loaded = GSON.fromJson(reader, mapType);
                    if (loaded != null) {
                        playerStatsMap.clear();
                        playerStatsMap.putAll(loaded);
                    }
                }
            }
            if (activityLogsFile != null && activityLogsFile.exists()) {
                try (FileReader reader = new FileReader(activityLogsFile)) {
                    Type listType = new TypeToken<List<ActivityLogEntry>>() {}.getType();
                    List<ActivityLogEntry> loaded = GSON.fromJson(reader, listType);
                    if (loaded != null) {
                        activityLogs.clear();
                        activityLogs.addAll(loaded);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
