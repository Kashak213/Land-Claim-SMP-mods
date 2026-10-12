package com.smprules.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.smprules.team.Team;
import com.smprules.team.TeamManager;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

public class BountyManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Long> activeBounties = new HashMap<>();
    private static File saveFile;

    private static final ServerBossBar HUNTER_BOSS_BAR = new ServerBossBar(
            Text.literal("No Bounties").formatted(Formatting.YELLOW, Formatting.BOLD),
            BossBar.Color.YELLOW,
            BossBar.Style.PROGRESS
    );

    // A dedicated BossBar for each active bounty so they see "You are bounty"
    private static final Map<UUID, ServerBossBar> bountyBossBars = new HashMap<>();

    // Timers (in milliseconds)
    private static final long FIFTY_HOURS_MS = 50L * 60L * 60L * 1000L;
    private static final long GLOW_INTERVAL_MS = 10L * 60L * 1000L; // Every 10 mins
    private static final long GLOW_DURATION_MS = 10L * 1000L;       // Lasts 10 seconds
    private static final long COORD_REFRESH_MS = 15L * 60L * 1000L; // Every 15 mins

    private static long lastGlowCycleTime = 0;
    private static long lastCoordRefreshTime = 0;

    public static void init(MinecraftServer server) {
        File dataDir = server.getRunDirectory().resolve("world/smprules").toFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        saveFile = new File(dataDir, "bounties.json");
        load();
        
        // Ensure the glow team exists
        setupGlowTeam(server);

        // Re-create BossBars for loaded bounties
        for (UUID bountyUuid : activeBounties.keySet()) {
            createBountyBossBar(bountyUuid);
        }
    }

    private static void setupGlowTeam(MinecraftServer server) {
        ServerScoreboard scoreboard = server.getScoreboard();
        net.minecraft.scoreboard.Team glowTeam = scoreboard.getTeam("glow_yellow");
        if (glowTeam == null) {
            glowTeam = scoreboard.addTeam("glow_yellow");
        }
        glowTeam.setColor(Formatting.YELLOW);
    }

    private static void createBountyBossBar(UUID bountyUuid) {
        if (!bountyBossBars.containsKey(bountyUuid)) {
            bountyBossBars.put(bountyUuid, new ServerBossBar(
                Text.literal("You are a bounty!").formatted(Formatting.RED, Formatting.BOLD),
                BossBar.Color.RED,
                BossBar.Style.NOTCHED_10
            ));
        }
    }

    public static boolean addBounty(MinecraftServer server, UUID playerUuid) {
        if (activeBounties.size() >= 2 || activeBounties.containsKey(playerUuid)) {
            return false;
        }
        activeBounties.put(playerUuid, System.currentTimeMillis());
        save();
        
        createBountyBossBar(playerUuid);

        // Force an immediate coordinate refresh
        lastCoordRefreshTime = 0; 

        // Notify the player
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(playerUuid);
        if (target != null) {
            target.sendMessage(Text.literal("🚨 YOU HAVE BEEN ASSIGNED AS A BOUNTY! Hunters are coming for you.").formatted(Formatting.RED, Formatting.BOLD), false);
        }

        return true;
    }

    public static boolean removeBounty(MinecraftServer server, UUID playerUuid) {
        if (activeBounties.remove(playerUuid) != null) {
            save();
            disableGlow(server, playerUuid);
            
            // Clean up their specific boss bar
            ServerBossBar bar = bountyBossBars.remove(playerUuid);
            if (bar != null) {
                bar.clearPlayers();
            }

            // Force an immediate coordinate refresh
            lastCoordRefreshTime = 0;
            return true;
        }
        return false;
    }

    public static Map<UUID, Long> getActiveBounties() {
        return activeBounties;
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();

        // 1. Check for 50-hour expirations
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : activeBounties.entrySet()) {
            if (now - entry.getValue() >= FIFTY_HOURS_MS) {
                toRemove.add(entry.getKey());
            }
        }

        for (UUID expired : toRemove) {
            removeBounty(server, expired);
            String name = PlayerMemory.getNameByUuid(expired);
            notifyAdmins(server, "🚨 [Bounty System] The 50-hour bounty timer for " + (name != null ? name : expired) + " has EXPIRED!");
            
            // Notify the bounty themselves
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(expired);
            if (target != null) {
                target.sendMessage(Text.literal("✅ Your 50-hour bounty timer has expired! You are no longer a bounty.").formatted(Formatting.GREEN, Formatting.BOLD), false);
            }
        }

        // 2. Manage BossBar viewers
        updateBossBarViewers(server);

        // 3. Handle 15-Minute Coordinate Refresh
        if (now - lastCoordRefreshTime >= COORD_REFRESH_MS || lastCoordRefreshTime == 0) {
            lastCoordRefreshTime = now;
            updateBossBarText(server);
        }

        // 4. Handle 10-Minute Glowing Cycle
        boolean shouldGlow = (now % GLOW_INTERVAL_MS) < GLOW_DURATION_MS;
        if (shouldGlow) {
            for (UUID bountyUuid : activeBounties.keySet()) {
                enableGlow(server, bountyUuid);
            }
        } else {
            for (UUID bountyUuid : activeBounties.keySet()) {
                disableGlow(server, bountyUuid);
            }
        }
    }

    private static void updateBossBarViewers(MinecraftServer server) {
        Set<ServerPlayerEntity> hunterViewers = new HashSet<>();
        
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // If they are an active bounty, they go to their OWN boss bar, NOT the hunter one.
            if (activeBounties.containsKey(player.getUuid())) {
                ServerBossBar theirBar = bountyBossBars.get(player.getUuid());
                if (theirBar != null && !theirBar.getPlayers().contains(player)) {
                    theirBar.addPlayer(player);
                }
                continue; // Skip adding to hunter bar
            }

            // Admins always see it
            if (player.hasPermissionLevel(2)) {
                hunterViewers.add(player);
                continue;
            }
            
            // Check if they are in a PvP team
            Team team = TeamManager.getPlayerTeam(player.getUuid());
            if (team != null && team.getType() == Team.TeamType.PVP) {
                hunterViewers.add(player);
            }
        }

        Set<ServerPlayerEntity> currentHunters = new HashSet<>(HUNTER_BOSS_BAR.getPlayers());

        // Add missing hunters
        for (ServerPlayerEntity player : hunterViewers) {
            if (!currentHunters.contains(player)) {
                HUNTER_BOSS_BAR.addPlayer(player);
            }
        }

        // Remove extra hunters
        for (ServerPlayerEntity player : currentHunters) {
            if (!hunterViewers.contains(player)) {
                HUNTER_BOSS_BAR.removePlayer(player);
            }
        }
    }

    private static void updateBossBarText(MinecraftServer server) {
        if (activeBounties.isEmpty()) {
            HUNTER_BOSS_BAR.setName(Text.literal("No Bounties").formatted(Formatting.YELLOW, Formatting.BOLD));
            return;
        }

        // 1. Build the global text for hunters
        StringBuilder globalText = new StringBuilder();
        int count = 0;
        for (UUID uuid : activeBounties.keySet()) {
            globalText.append(formatBountyText(server, uuid, false));
            count++;
            if (count < activeBounties.size()) {
                globalText.append("      ");
            }
        }
        HUNTER_BOSS_BAR.setName(Text.literal(globalText.toString()).formatted(Formatting.YELLOW, Formatting.BOLD));

        // 2. Build the specific text for each bounty
        for (Map.Entry<UUID, ServerBossBar> entry : bountyBossBars.entrySet()) {
            UUID thisBountyUuid = entry.getKey();
            ServerBossBar theirBar = entry.getValue();

            StringBuilder theirText = new StringBuilder();
            theirText.append("You are bounty!");

            // Find if there's another bounty
            for (UUID otherUuid : activeBounties.keySet()) {
                if (!otherUuid.equals(thisBountyUuid)) {
                    theirText.append("      ").append(formatBountyText(server, otherUuid, true));
                }
            }

            theirBar.setName(Text.literal(theirText.toString()).formatted(Formatting.RED, Formatting.BOLD));
        }
    }

    private static String formatBountyText(MinecraftServer server, UUID uuid, boolean shortName) {
        String name = PlayerMemory.getNameByUuid(uuid);
        if (name == null) name = "Unknown";
        
        ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(uuid);
        if (onlinePlayer != null) {
            int x = onlinePlayer.getBlockX();
            int y = onlinePlayer.getBlockY();
            int z = onlinePlayer.getBlockZ();
            return name + ": " + x + ", " + y + ", " + z;
        } else {
            return name + ": [Offline]";
        }
    }

    private static void enableGlow(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null) return;
        
        ServerScoreboard scoreboard = server.getScoreboard();
        net.minecraft.scoreboard.Team glowTeam = scoreboard.getTeam("glow_yellow");
        if (glowTeam != null) {
            if (scoreboard.getScoreHolderTeam(player.getNameForScoreboard()) != glowTeam) {
                scoreboard.addScoreHolderToTeam(player.getNameForScoreboard(), glowTeam);
            }
        }
        if (!player.isGlowing()) {
            player.setGlowing(true);
        }
    }

    private static void disableGlow(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null) return;
        
        ServerScoreboard scoreboard = server.getScoreboard();
        net.minecraft.scoreboard.Team glowTeam = scoreboard.getTeam("glow_yellow");
        if (glowTeam != null) {
            if (scoreboard.getScoreHolderTeam(player.getNameForScoreboard()) == glowTeam) {
                scoreboard.removeScoreHolderFromTeam(player.getNameForScoreboard(), glowTeam);
            }
        }
        if (player.isGlowing()) {
            player.setGlowing(false);
        }
    }

    private static void notifyAdmins(MinecraftServer server, String message) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.hasPermissionLevel(2)) {
                p.sendMessage(Text.literal(message).formatted(Formatting.RED, Formatting.BOLD), false);
            }
        }
    }

    public static void save() {
        try {
            if (saveFile != null) {
                try (FileWriter writer = new FileWriter(saveFile)) {
                    GSON.toJson(activeBounties, writer);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        try {
            if (saveFile != null && saveFile.exists()) {
                try (FileReader reader = new FileReader(saveFile)) {
                    Type mapType = new TypeToken<Map<UUID, Long>>() {}.getType();
                    Map<UUID, Long> loaded = GSON.fromJson(reader, mapType);
                    if (loaded != null) {
                        activeBounties.clear();
                        activeBounties.putAll(loaded);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
