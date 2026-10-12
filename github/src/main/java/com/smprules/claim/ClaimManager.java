package com.smprules.claim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.smprules.config.ModConfig;
import com.smprules.team.Team;
import com.smprules.team.TeamManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

public class ClaimManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, ClaimChunk> claims = new HashMap<>();
    private static final Map<String, Set<String>> teamClaims = new HashMap<>();
    private static final Map<String, Long> pvpTeamOfflineSince = new HashMap<>();
    private static File saveFile;

    public static void init(MinecraftServer server) {
        File dataDir = server.getRunDirectory().resolve("world/smprules").toFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        saveFile = new File(dataDir, "claims.json");
        load();
    }

    public static synchronized void clearAll() {
        claims.clear();
        teamClaims.clear();
        pvpTeamOfflineSince.clear();
        save();
    }

    public static String getChunkKey(String dimension, int x, int z) {
        return (dimension != null ? dimension : "minecraft:overworld") + ":" + x + ":" + z;
    }

    public static ClaimChunk getClaim(String dimension, int x, int z) {
        return claims.get(getChunkKey(dimension, x, z));
    }

    public static int getClaimCount(String teamName) {
        if (teamName == null) return 0;
        Set<String> set = teamClaims.get(teamName.toLowerCase());
        return set != null ? set.size() : 0;
    }

    public static Collection<ClaimChunk> getTeamClaims(String teamName) {
        if (teamName == null) return Collections.emptyList();
        Set<String> keys = teamClaims.get(teamName.toLowerCase());
        if (keys == null) return Collections.emptyList();

        List<ClaimChunk> list = new ArrayList<>();
        for (String key : keys) {
            ClaimChunk chunk = claims.get(key);
            if (chunk != null) {
                list.add(chunk);
            }
        }
        return list;
    }

    public static int getMaxClaimsForTeam(Team team) {
        if (team == null) return 0;
        if (team.getType() == Team.TeamType.PEACEFUL) {
            return ModConfig.get().peacefulTeamMaxClaims;
        } else {
            return ModConfig.get().pvpTeamMaxClaims;
        }
    }

    public static boolean claimChunk(String dimension, int x, int z, Team team) {
        if (team == null) return false;
        String key = getChunkKey(dimension, x, z);
        if (claims.containsKey(key)) {
            return false;
        }

        int maxAllowed = getMaxClaimsForTeam(team);
        if (getClaimCount(team.getName()) >= maxAllowed) {
            return false;
        }

        ClaimChunk chunk = new ClaimChunk(dimension, x, z, team.getName());
        claims.put(key, chunk);
        teamClaims.computeIfAbsent(team.getName().toLowerCase(), k -> new HashSet<>()).add(key);
        save();
        return true;
    }

    public static boolean unclaimChunk(String dimension, int x, int z, Team team) {
        if (team == null) return false;
        String key = getChunkKey(dimension, x, z);
        ClaimChunk chunk = claims.get(key);
        if (chunk == null || !chunk.getTeamName().equalsIgnoreCase(team.getName())) {
            return false;
        }

        claims.remove(key);
        Set<String> set = teamClaims.get(team.getName().toLowerCase());
        if (set != null) {
            set.remove(key);
        }
        save();
        return true;
    }

    public static int trimClaimsForPvPSwitch(MinecraftServer server, Team team) {
        if (team == null || server == null) return 0;
        Collection<ClaimChunk> allTeamClaims = getTeamClaims(team.getName());
        int currentCount = allTeamClaims.size();
        int maxAllowed = ModConfig.get().pvpTeamMaxClaims;

        if (currentCount <= maxAllowed) {
            return 0;
        }

        int toRemove = currentCount - maxAllowed;

        class ChunkScore implements Comparable<ChunkScore> {
            ClaimChunk chunk;
            long buildScore;

            ChunkScore(ClaimChunk chunk, long buildScore) {
                this.chunk = chunk;
                this.buildScore = buildScore;
            }

            @Override
            public int compareTo(ChunkScore o) {
                return Long.compare(this.buildScore, o.buildScore);
            }
        }

        List<ChunkScore> scoredChunks = new ArrayList<>();

        for (ClaimChunk chunk : allTeamClaims) {
            ServerWorld world = null;
            for (ServerWorld w : server.getWorlds()) {
                if (w.getRegistryKey().getValue().toString().equalsIgnoreCase(chunk.getDimension())) {
                    world = w;
                    break;
                }
            }

            long score = calculateChunkBuildScore(world, chunk.getX(), chunk.getZ());
            scoredChunks.add(new ChunkScore(chunk, score));
        }

        Collections.sort(scoredChunks);

        int removedCount = 0;
        for (int i = 0; i < toRemove && i < scoredChunks.size(); i++) {
            ClaimChunk chunkToUnclaim = scoredChunks.get(i).chunk;
            unclaimChunk(chunkToUnclaim.getDimension(), chunkToUnclaim.getX(), chunkToUnclaim.getZ(), team);
            removedCount++;
        }

        return removedCount;
    }

    private static long calculateChunkBuildScore(ServerWorld world, int chunkX, int chunkZ) {
        if (world == null) return 0;

        long score = 0;
        int startX = chunkX * 16;
        int startZ = chunkZ * 16;

        for (int x = startX; x < startX + 16; x += 4) {
            for (int z = startZ; z < startZ + 16; z += 4) {
                for (int y = world.getBottomY(); y < world.getTopY(); y += 8) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    if (state.isAir()) continue;

                    if (isManMadeBlock(state)) {
                        score += 10;
                    } else if (!state.isOf(Blocks.STONE) && !state.isOf(Blocks.DIRT) && !state.isOf(Blocks.GRASS_BLOCK) && !state.isOf(Blocks.DEEPSLATE) && !state.isOf(Blocks.WATER)) {
                        score += 1;
                    }
                }
            }
        }
        return score;
    }

    private static boolean isManMadeBlock(BlockState state) {
        return state.isOf(Blocks.CHEST) ||
               state.isOf(Blocks.BARREL) ||
               state.isOf(Blocks.TRAPPED_CHEST) ||
               state.isOf(Blocks.CRAFTING_TABLE) ||
               state.isOf(Blocks.FURNACE) ||
               state.isOf(Blocks.BLAST_FURNACE) ||
               state.isOf(Blocks.SMOKER) ||
               state.isOf(Blocks.ANVIL) ||
               state.isOf(Blocks.ENCHANTING_TABLE) ||
               state.isOf(Blocks.OBSIDIAN) ||
               state.isOf(Blocks.GLASS) ||
               state.isOf(Blocks.OAK_PLANKS) || state.isOf(Blocks.SPRUCE_PLANKS) || state.isOf(Blocks.BIRCH_PLANKS) ||
               state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.STONE_BRICKS) ||
               state.isOf(Blocks.REDSTONE_WIRE) || state.isOf(Blocks.HOPPER) ||
               state.isOf(Blocks.IRON_BLOCK) || state.isOf(Blocks.GOLD_BLOCK) || state.isOf(Blocks.DIAMOND_BLOCK);
    }

    public static void onPlayerLogoutCheckTeam(MinecraftServer server, Team team) {
        if (team == null || team.getType() != Team.TeamType.PVP || server == null) return;

        boolean anyOnline = false;
        for (UUID memberUuid : team.getMembers()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(memberUuid);
            if (player != null) {
                anyOnline = true;
                break;
            }
        }

        if (!anyOnline) {
            pvpTeamOfflineSince.put(team.getName().toLowerCase(), System.currentTimeMillis());
        }
    }

    public static void onPlayerLoginCheckTeam(Team team) {
        if (team != null && team.getType() == Team.TeamType.PVP) {
            pvpTeamOfflineSince.remove(team.getName().toLowerCase());
        }
    }

    public static boolean canBuild(ServerPlayerEntity player, String dimension, int chunkX, int chunkZ) {
        if (player == null) return false;
        ClaimChunk claim = getClaim(dimension, chunkX, chunkZ);
        if (claim == null) {
            return true;
        }

        Team ownerTeam = TeamManager.getTeam(claim.getTeamName());
        if (ownerTeam == null) {
            return true;
        }

        Team playerTeam = TeamManager.getPlayerTeam(player.getUuid());

        if (playerTeam != null && playerTeam.getName().equalsIgnoreCase(ownerTeam.getName())) {
            return true;
        }

        if (ownerTeam.getType() == Team.TeamType.PEACEFUL) {
            return false;
        }

        // PvP team claim — raidable while members are ONLINE, protected only during offline grace period
        boolean memberOnline = false;
        MinecraftServer server = player.getServer();
        if (server != null) {
            for (UUID memberUuid : ownerTeam.getMembers()) {
                if (server.getPlayerManager().getPlayer(memberUuid) != null) {
                    memberOnline = true;
                    break;
                }
            }
        }

        if (memberOnline) {
            // Members are online — PvP claims are fully raidable!
            return true;
        }

        // Members are offline — check grace period
        Long offlineTimestamp = pvpTeamOfflineSince.get(ownerTeam.getName().toLowerCase());
        if (offlineTimestamp != null) {
            long elapsedSeconds = (System.currentTimeMillis() - offlineTimestamp) / 1000;
            long delay = ModConfig.get().pvpOfflineProtectionDelaySeconds;
            if (elapsedSeconds < delay) {
                // Still within grace raid window — raidable!
                return true;
            }
            // Grace period expired — base is now locked/protected
            return false;
        }

        // No offline timestamp recorded — protect by default (safe fallback)
        return false;
    }

    public static boolean canDamagePlayerInClaim(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        if (victim == null) return true;
        String dimension = victim.getWorld().getRegistryKey().getValue().toString();
        ChunkPos pos = victim.getChunkPos();
        ClaimChunk claim = getClaim(dimension, pos.x, pos.z);

        if (claim == null) {
            return true;
        }

        Team ownerTeam = TeamManager.getTeam(claim.getTeamName());
        if (ownerTeam != null && ownerTeam.getType() == Team.TeamType.PEACEFUL) {
            return false;
        }

        return true;
    }

    /**
     * Checks if a chunk contains or is part of a village structure.
     * Village chunks are public territory and cannot be claimed by any team.
     */
    public static boolean isVillageChunk(ServerWorld world, int chunkX, int chunkZ) {
        if (world == null) return false;

        var chunk = world.getChunk(chunkX, chunkZ);
        if (chunk == null) return false;

        var structureRegistry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);

        // Check structure starts (the chunk where a village center generates)
        for (var entry : chunk.getStructureStarts().entrySet()) {
            var start = entry.getValue();
            if (start == null || !start.hasChildren()) continue;

            var id = structureRegistry.getId(entry.getKey());
            if (id != null && id.getPath().startsWith("village")) {
                return true;
            }
        }

        // Check structure references (chunks that are part of a village from a neighboring start chunk)
        for (var refEntry : chunk.getStructureReferences().entrySet()) {
            if (refEntry.getValue() == null || refEntry.getValue().isEmpty()) continue;

            var id = structureRegistry.getId(refEntry.getKey());
            if (id != null && id.getPath().startsWith("village")) {
                return true;
            }
        }

        return false;
    }

    public static synchronized void save() {
        if (saveFile == null) return;
        try (FileWriter writer = new FileWriter(saveFile)) {
            GSON.toJson(claims.values(), writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void load() {
        if (saveFile == null || !saveFile.exists()) return;
        try (FileReader reader = new FileReader(saveFile)) {
            Type listType = new TypeToken<List<ClaimChunk>>() {}.getType();
            List<ClaimChunk> loadedClaims = GSON.fromJson(reader, listType);
            if (loadedClaims != null) {
                claims.clear();
                teamClaims.clear();
                for (ClaimChunk chunk : loadedClaims) {
                    if (chunk.getKey() != null && chunk.getTeamName() != null) {
                        claims.put(chunk.getKey(), chunk);
                        teamClaims.computeIfAbsent(chunk.getTeamName().toLowerCase(), k -> new HashSet<>()).add(chunk.getKey());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
