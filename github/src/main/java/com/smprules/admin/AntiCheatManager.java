package com.smprules.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AntiCheatManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Integer> playerStrikes = new HashMap<>();
    private static final List<SecurityLogEntry> logEntries = new ArrayList<>();
    private static File strikesFile;
    private static File logsFile;

    public static class SecurityLogEntry {
        public String timestamp;
        public String playerName;
        public String playerUuid;
        public String violationType;
        public int strikeNumber;
        public String actionTaken;
        public String location;

        public SecurityLogEntry(String timestamp, String playerName, String playerUuid, String violationType, int strikeNumber, String actionTaken, String location) {
            this.timestamp = timestamp;
            this.playerName = playerName;
            this.playerUuid = playerUuid;
            this.violationType = violationType;
            this.strikeNumber = strikeNumber;
            this.actionTaken = actionTaken;
            this.location = location;
        }
    }

    public static void init(MinecraftServer server) {
        File dataDir = server.getRunDirectory().resolve("world/smprules").toFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        strikesFile = new File(dataDir, "player_strikes.json");
        logsFile = new File(dataDir, "anticheat_logs.json");
        load();
        System.out.println("[AntiCheat] Security Dashboard & GrimAC Hook initialized successfully.");
    }

    public static void flagPlayer(String playerName, String violationType) {
        UUID uuid = PlayerMemory.getUuidByName(playerName);
        if (uuid == null) return;
        flagPlayer(uuid, playerName, violationType);
    }

    public static void flagPlayer(ServerPlayerEntity player, String violationType) {
        if (player == null) return;
        if (player.hasPermissionLevel(2) || StaffModeManager.isStaffModeActive(player.getUuid())) {
            return;
        }
        flagPlayer(player.getUuid(), player.getName().getString(), violationType);
    }

    private static void flagPlayer(UUID uuid, String playerName, String violationType) {
        int currentStrikes = playerStrikes.getOrDefault(uuid, 0) + 1;
        playerStrikes.put(uuid, currentStrikes);

        String timestamp = LocalDateTime.now(ZoneId.of("America/New_York")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String location = "Offline/Unknown Location";

        String actionTaken;

        if (currentStrikes == 1) {
            actionTaken = "Strike 1 Recorded - Admin Notified";
            notifyAdmins(null, "🚨 [AntiCheat Alert] " + playerName + " flagged for " + violationType + " at " + location + " (Strike 1)");

        } else if (currentStrikes == 2) {
            actionTaken = "Strike 2 Recorded - Admin Notified";
            notifyAdmins(null, "🚨 [AntiCheat Alert] " + playerName + " flagged for " + violationType + " at " + location + " (Strike 2)");

        } else {
            actionTaken = "Strike 3 Recorded - Banned";
            notifyAdmins(null, "🚨 [AntiCheat Alert] " + playerName + " flagged for " + violationType + " at " + location + " (Strike 3 - Banning...)");
        }

        SecurityLogEntry entry = new SecurityLogEntry(timestamp, playerName, uuid.toString(), violationType, currentStrikes, actionTaken, location);
        logEntries.add(0, entry);
        save();
    }

    /**
     * Manual Admin Ban Override
     */
    public static void manualBanPlayer(ServerCommandSource adminSource, UUID targetUuid, String targetName, String reason) {
        MinecraftServer server = adminSource.getServer();
        String adminName = adminSource.getName();
        String banReason = (reason != null && !reason.isBlank()) ? reason : "Manual Admin Ban";

        // Try to get GameProfile to ban
        java.util.Optional<com.mojang.authlib.GameProfile> profileOpt = server.getUserCache().getByUuid(targetUuid);
        if (profileOpt.isPresent()) {
            BannedPlayerList banList = server.getPlayerManager().getUserBanList();
            banList.add(new BannedPlayerEntry(profileOpt.get(), new java.util.Date(), adminName, null, banReason));
        }

        // Disconnect player if they are online
        ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(targetUuid);
        if (onlinePlayer != null) {
            onlinePlayer.networkHandler.disconnect(Text.literal("🛑 BANNED: " + banReason).formatted(Formatting.RED, Formatting.BOLD));
        }

        // Broadcast to server
        server.getPlayerManager().broadcast(
            Text.literal("🔨 " + targetName + " has been manually banned by an Admin.").formatted(Formatting.RED, Formatting.BOLD),
            false
        );

        // Log to security dashboard
        String timestamp = LocalDateTime.now(ZoneId.of("America/New_York")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String location = "Offline/Unknown Location";
        
        if (onlinePlayer != null) {
            location = String.format("[%s] X:%d Y:%d Z:%d", 
                onlinePlayer.getWorld().getRegistryKey().getValue().toString(), 
                onlinePlayer.getBlockX(), onlinePlayer.getBlockY(), onlinePlayer.getBlockZ());
        }

        SecurityLogEntry entry = new SecurityLogEntry(timestamp, targetName, targetUuid.toString(), "MANUAL_ADMIN_BAN", 3, "Banned by " + adminName + " (" + banReason + ")", location);
        logEntries.add(0, entry);
        save();
    }

    /**
     * Manual Admin Unban Override
     */
    public static boolean manualUnbanPlayer(ServerCommandSource adminSource, String targetName) {
        if (targetName == null || targetName.isBlank()) return false;
        MinecraftServer server = adminSource.getServer();
        String adminName = adminSource.getName();

        BannedPlayerList banList = server.getPlayerManager().getUserBanList();
        UUID targetUuid = PlayerMemory.getUuidByName(targetName);
        
        GameProfile bannedProfile = null;
        if (targetUuid != null) {
            // Check if they are in the user cache
            bannedProfile = server.getUserCache().getByUuid(targetUuid).orElse(null);
            
            if (bannedProfile == null) {
                // If not in cache, create a dummy profile to remove them by UUID
                bannedProfile = new com.mojang.authlib.GameProfile(targetUuid, targetName);
            }
        } else {
            // Fallback to name search if not in PlayerMemory
            bannedProfile = server.getUserCache().findByName(targetName).orElse(null);
        }

        // Verify the profile is actually banned
        if (bannedProfile != null && !banList.contains(bannedProfile)) {
            bannedProfile = null;
        }

        if (bannedProfile != null) {
            banList.remove(bannedProfile);

            // Reset player strikes
            if (bannedProfile.getId() != null) {
                playerStrikes.remove(bannedProfile.getId());
            }

            // Log to security dashboard
            String timestamp = LocalDateTime.now(ZoneId.of("America/New_York")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            SecurityLogEntry entry = new SecurityLogEntry(timestamp, bannedProfile.getName(), (bannedProfile.getId() != null ? bannedProfile.getId().toString() : "UNKNOWN"), "MANUAL_ADMIN_UNBAN", 0, "Pardoned & Unbanned by " + adminName, "GLOBAL");
            logEntries.add(0, entry);
            save();

            adminSource.sendMessage(Text.literal("🟢 [Admin Override] Successfully unbanned player: " + targetName + " and reset their anti-cheat strikes!").formatted(Formatting.GREEN, Formatting.BOLD));
            return true;
        } else {
            adminSource.sendError(Text.literal("Player '" + targetName + "' is not currently in the server banlist."));
            return false;
        }
    }

    public static void onGrimACFlag(ServerPlayerEntity player, String checkName, double verbose) {
        if (player == null || checkName == null) return;
        String violation = "GrimAC: " + checkName;
        flagPlayer(player, violation);
    }

    private static void wipeInventoryToInkSacs(ServerPlayerEntity player) {
        if (player == null) return;
        player.getInventory().clear();

        for (int i = 0; i < player.getInventory().main.size(); i++) {
            player.getInventory().main.set(i, new ItemStack(Items.INK_SAC, 64));
        }

        for (int i = 0; i < player.getInventory().armor.size(); i++) {
            player.getInventory().armor.set(i, ItemStack.EMPTY);
        }
        player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, ItemStack.EMPTY);
    }

    private static void banPlayer(ServerPlayerEntity player, String reason) {
        if (player == null) return;
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.getPlayerManager().getUserBanList().add(
                new BannedPlayerEntry(player.getGameProfile(), new Date(), "AntiCheat", null, "Permanently banned by AntiCheat: " + reason)
            );
            player.networkHandler.disconnect(Text.literal("🔨 Permanently banned by AntiCheat after 3 strikes (" + reason + ")").formatted(Formatting.RED, Formatting.BOLD));
            server.getPlayerManager().broadcast(
                Text.literal("🔨 [AntiCheat] " + player.getName().getString() + " has been permanently banned after 3 strikes.")
                    .formatted(Formatting.RED, Formatting.BOLD),
                false
            );
        }
    }

    private static void notifyAdmins(MinecraftServer server, String message) {
        if (server == null || message == null) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.hasPermissionLevel(2)) {
                p.sendMessage(Text.literal(message).formatted(Formatting.RED), false);
            }
        }
    }

    public static List<SecurityLogEntry> getLogs() {
        return logEntries;
    }

    public static Map<UUID, Integer> getPlayerStrikes() {
        return playerStrikes;
    }

    public static synchronized void clearLogs() {
        logEntries.clear();
        save();
    }

    public static synchronized void resetPlayerStrikes(UUID uuid) {
        if (uuid == null) return;
        playerStrikes.remove(uuid);
        save();
    }

    public static synchronized void save() {
        try {
            if (strikesFile != null) {
                try (FileWriter writer = new FileWriter(strikesFile)) {
                    GSON.toJson(playerStrikes, writer);
                }
            }
            if (logsFile != null) {
                try (FileWriter writer = new FileWriter(logsFile)) {
                    GSON.toJson(logEntries, writer);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void load() {
        try {
            if (strikesFile != null && strikesFile.exists()) {
                try (FileReader reader = new FileReader(strikesFile)) {
                    Type mapType = new TypeToken<Map<UUID, Integer>>() {}.getType();
                    Map<UUID, Integer> loaded = GSON.fromJson(reader, mapType);
                    if (loaded != null) {
                        playerStrikes.clear();
                        playerStrikes.putAll(loaded);
                    }
                }
            }
            if (logsFile != null && logsFile.exists()) {
                try (FileReader reader = new FileReader(logsFile)) {
                    Type listType = new TypeToken<List<SecurityLogEntry>>() {}.getType();
                    List<SecurityLogEntry> loaded = GSON.fromJson(reader, listType);
                    if (loaded != null) {
                        logEntries.clear();
                        logEntries.addAll(loaded);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Custom movement checks removed in favor of GrimAC
}
