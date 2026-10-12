package com.smprules.team;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

public class TeamManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Team> teams = new HashMap<>();
    private static final Map<UUID, String> playerTeamMap = new HashMap<>();
    private static File saveFile;

    public static class ColorEntry {
        public String name;
        public String hex;
        public ColorEntry(String name, String hex) {
            this.name = name;
            this.hex = hex;
        }
    }

    private static final List<ColorEntry> COLOR_PALETTE = List.of(
        new ColorEntry("PINK", "#FF66CC"),
        new ColorEntry("BLUE", "#3388FF"),
        new ColorEntry("GREEN", "#33CC33"),
        new ColorEntry("YELLOW", "#FFE600"),
        new ColorEntry("PURPLE", "#AA33FF"),
        new ColorEntry("RED", "#FF3333"),
        new ColorEntry("ORANGE", "#FF8800"),
        new ColorEntry("CYAN", "#00E5FF"),
        new ColorEntry("LIME", "#88FF00"),
        new ColorEntry("GOLD", "#FFAA00")
    );

    public static void init(MinecraftServer server) {
        File dataDir = server.getRunDirectory().resolve("world/smprules").toFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        saveFile = new File(dataDir, "teams.json");
        load();
    }

    public static void clearAll() {
        teams.clear();
        playerTeamMap.clear();
        save();
    }

    public static Team createTeam(String name, Team.TeamType type, UUID leader) {
        if (name == null || name.isBlank() || leader == null) return null;
        if (teams.containsKey(name.toLowerCase()) || playerTeamMap.containsKey(leader)) {
            return null;
        }

        ColorEntry color = COLOR_PALETTE.get(teams.size() % COLOR_PALETTE.size());

        Team team = new Team(name, type, leader, color.name, color.hex);
        teams.put(name.toLowerCase(), team);
        playerTeamMap.put(leader, name.toLowerCase());
        save();
        return team;
    }

    public static boolean disbandTeam(String name) {
        if (name == null) return false;
        Team team = teams.remove(name.toLowerCase());
        if (team != null) {
            for (UUID member : team.getMembers()) {
                playerTeamMap.remove(member);
            }
            save();
            return true;
        }
        return false;
    }

    public static Team getTeam(String name) {
        if (name == null) return null;
        return teams.get(name.toLowerCase());
    }

    public static Team getPlayerTeam(UUID playerUuid) {
        if (playerUuid == null) return null;
        String teamName = playerTeamMap.get(playerUuid);
        return teamName != null ? teams.get(teamName) : null;
    }

    public static Team getPlayerTeamByName(String playerName) {
        if (playerName == null) return null;
        UUID uuid = com.smprules.admin.PlayerMemory.getUuidByName(playerName);
        return getPlayerTeam(uuid);
    }

    public static Collection<Team> getAllTeams() {
        return teams.values();
    }

    public static void setTeamColor(Team team, String colorName, String colorHex) {
        if (team == null) return;
        team.setColorName(colorName);
        team.setColorHex(colorHex);
        save();
    }

    public static synchronized void save() {
        if (saveFile == null) return;
        try (FileWriter writer = new FileWriter(saveFile)) {
            GSON.toJson(teams.values(), writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void load() {
        if (saveFile == null || !saveFile.exists()) return;
        try (FileReader reader = new FileReader(saveFile)) {
            Type listType = new TypeToken<List<Team>>() {}.getType();
            List<Team> loadedTeams = GSON.fromJson(reader, listType);
            if (loadedTeams != null) {
                teams.clear();
                playerTeamMap.clear();
                for (Team team : loadedTeams) {
                    if (team.getName() != null) {
                        teams.put(team.getName().toLowerCase(), team);
                        for (UUID member : team.getMembers()) {
                            if (member != null) {
                                playerTeamMap.put(member, team.getName().toLowerCase());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updatePlayerMapping(UUID playerUuid, String teamName) {
        if (playerUuid == null) return;
        if (teamName == null) {
            playerTeamMap.remove(playerUuid);
        } else {
            playerTeamMap.put(playerUuid, teamName.toLowerCase());
        }
        save();
    }

    // Individual tax logic is now handled in TaxManager
}
