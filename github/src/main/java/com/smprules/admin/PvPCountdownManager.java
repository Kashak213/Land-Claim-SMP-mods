package com.smprules.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages the global PvP countdown system.
 * PvP starts DISABLED. An admin runs /smpadmin pvpstart to begin a 48-hour countdown.
 * After 48 hours, PvP is globally enabled. Persists across server restarts.
 */
public class PvPCountdownManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long COUNTDOWN_DURATION_MS = 48 * 60 * 60 * 1000L; // 48 hours

    private static boolean pvpEnabled = false;
    private static long countdownStartTimeMs = -1; // -1 = countdown not yet started
    private static File saveFile;

    // Track which milestone announcements have already been broadcast
    private static final Set<String> announcedMilestones = new HashSet<>();

    // Milestone thresholds (remaining time) at which the server broadcasts a warning
    private static final long[] MILESTONES_MS = {
        24 * 60 * 60 * 1000L,  // 24 hours
        12 * 60 * 60 * 1000L,  // 12 hours
         6 * 60 * 60 * 1000L,  //  6 hours
         1 * 60 * 60 * 1000L,  //  1 hour
              30 * 60 * 1000L, // 30 minutes
              10 * 60 * 1000L, // 10 minutes
               5 * 60 * 1000L, //  5 minutes
               1 * 60 * 1000L, //  1 minute
    };

    public static void init(MinecraftServer server) {
        File dataDir = server.getRunDirectory().resolve("world/smprules").toFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        saveFile = new File(dataDir, "pvp_countdown.json");
        load();

        // If the countdown elapsed while the server was offline, auto-enable PvP
        if (countdownStartTimeMs > 0 && !pvpEnabled) {
            long elapsed = System.currentTimeMillis() - countdownStartTimeMs;
            if (elapsed >= COUNTDOWN_DURATION_MS) {
                pvpEnabled = true;
                save();
                System.out.println("[SMPRules] PvP countdown elapsed during server downtime — PvP is now ENABLED!");
            }
        }
    }

    /**
     * Returns true if PvP is globally enabled (countdown finished or admin forced it on).
     */
    public static boolean isPvPEnabled() {
        return pvpEnabled;
    }

    /**
     * Returns true if the countdown has been started but PvP is not yet enabled.
     */
    public static boolean isCountdownActive() {
        return countdownStartTimeMs > 0 && !pvpEnabled;
    }

    /**
     * Returns the remaining countdown time in milliseconds (0 if not active).
     */
    public static long getTimeRemainingMs() {
        if (!isCountdownActive()) return 0;
        long elapsed = System.currentTimeMillis() - countdownStartTimeMs;
        long remaining = COUNTDOWN_DURATION_MS - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Starts the 48-hour PvP countdown. Called by /smpadmin pvpstart.
     * Returns false if PvP is already enabled or the countdown was already started.
     */
    public static boolean startCountdown(MinecraftServer server) {
        if (pvpEnabled) return false;
        if (countdownStartTimeMs > 0) return false;

        countdownStartTimeMs = System.currentTimeMillis();
        announcedMilestones.clear();
        save();

        server.getPlayerManager().broadcast(
            Text.literal("⚔ [PvP Countdown] The 48-hour PvP countdown has STARTED! PvP will be enabled in 48 hours!")
                .formatted(Formatting.GOLD, Formatting.BOLD),
            false
        );
        return true;
    }

    /**
     * Admin force-enable PvP immediately (skips the countdown).
     */
    public static void forceEnablePvP(MinecraftServer server) {
        pvpEnabled = true;
        save();

        server.getPlayerManager().broadcast(
            Text.literal("⚔ [PvP] PvP has been FORCE-ENABLED by an Admin!")
                .formatted(Formatting.RED, Formatting.BOLD),
            false
        );
    }

    /**
     * Admin force-disable PvP and reset the countdown entirely.
     */
    public static void forceDisablePvP(MinecraftServer server) {
        pvpEnabled = false;
        countdownStartTimeMs = -1;
        announcedMilestones.clear();
        save();

        server.getPlayerManager().broadcast(
            Text.literal("🛡 [PvP] PvP has been DISABLED by an Admin! The countdown has been reset.")
                .formatted(Formatting.GREEN, Formatting.BOLD),
            false
        );
    }

    /**
     * Called once per second from the server tick loop.
     * Checks if the countdown has completed and broadcasts milestone warnings.
     */
    public static void tickCheck(MinecraftServer server) {
        if (!isCountdownActive()) return;

        long remaining = getTimeRemainingMs();

        // Countdown complete!
        if (remaining <= 0) {
            pvpEnabled = true;
            save();

            server.getPlayerManager().broadcast(
                Text.literal("⚔ ⚔ ⚔ [PvP ENABLED] The 48-hour grace period is OVER! PvP is now ACTIVE across the server! ⚔ ⚔ ⚔")
                    .formatted(Formatting.RED, Formatting.BOLD),
                false
            );
            return;
        }

        // Broadcast milestone warnings (e.g. "PvP enables in 24h!", "PvP enables in 1h!")
        for (long milestone : MILESTONES_MS) {
            String key = String.valueOf(milestone);
            if (remaining <= milestone && !announcedMilestones.contains(key)) {
                announcedMilestones.add(key);
                String timeStr = formatDuration(milestone);
                server.getPlayerManager().broadcast(
                    Text.literal("⚔ [PvP Countdown] PvP will be enabled in " + timeStr + "!")
                        .formatted(Formatting.GOLD, Formatting.BOLD),
                    false
                );
                break; // Only announce one milestone per tick check
            }
        }
    }

    /**
     * Formats a duration in ms to a human-readable string like "24h 0m" or "5m 30s".
     */
    public static String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }

    public static synchronized void save() {
        if (saveFile == null) return;
        try (FileWriter writer = new FileWriter(saveFile)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("pvpEnabled", pvpEnabled);
            obj.addProperty("countdownStartTimeMs", countdownStartTimeMs);
            GSON.toJson(obj, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void load() {
        if (saveFile == null || !saveFile.exists()) return;
        try (FileReader reader = new FileReader(saveFile)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj != null) {
                pvpEnabled = obj.has("pvpEnabled") && obj.get("pvpEnabled").getAsBoolean();
                countdownStartTimeMs = obj.has("countdownStartTimeMs") ? obj.get("countdownStartTimeMs").getAsLong() : -1;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
