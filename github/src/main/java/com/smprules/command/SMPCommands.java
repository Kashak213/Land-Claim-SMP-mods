package com.smprules.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.smprules.admin.AntiCheatManager;
import com.smprules.admin.DashboardStatsManager;
import com.smprules.admin.EndLockManager;
import com.smprules.admin.PvPCountdownManager;
import com.smprules.admin.StaffModeManager;
import com.smprules.admin.TaxManager;
import com.smprules.admin.PlayerMemory;
import com.smprules.claim.ClaimChunk;
import com.smprules.claim.ClaimManager;
import com.smprules.claim.ChunkVisualizer;
import com.smprules.claim.SpawnProtectionManager;
import com.smprules.config.ModConfig;
import com.smprules.hud.HUDManager;
import com.smprules.team.Team;
import com.smprules.team.TeamManager;
import com.smprules.team.TeamColors;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.border.WorldBorder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SMPCommands {

    // Team name suggestion provider for autocomplete
    private static final SuggestionProvider<ServerCommandSource> TEAM_SUGGESTIONS = (context, builder) -> {
        for (Team team : TeamManager.getAllTeams()) {
            builder.suggest(team.getName());
        }
        return builder.buildFuture();
    };

    // All known players suggestion provider
    private static final SuggestionProvider<ServerCommandSource> ALL_PLAYERS_SUGGESTION_PROVIDER = (context, builder) -> {
        for (String name : PlayerMemory.getAllKnownNames()) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        
        // /smprules
        dispatcher.register(
            CommandManager.literal("smprules")
                .executes(ctx -> {
                    ServerCommandSource source = ctx.getSource();
                    source.sendMessage(Text.literal("=== 📜 SMP SERVER RULES ===").formatted(Formatting.GOLD, Formatting.BOLD));
                    source.sendMessage(Text.literal("1. Peaceful Teams: Max 3 players. Up to 35 Chunks. No-PvP & No-Build inside claim.").formatted(Formatting.GREEN));
                    source.sendMessage(Text.literal("2. PvP Teams: Max 5 players. Up to 20 Chunks. Building/PvP ON while online (+ 5-min offline grace period).").formatted(Formatting.RED));
                    source.sendMessage(Text.literal("3. Combat Tag: 2-minute timer on PvP. Combat logging = Instant death & drop inventory.").formatted(Formatting.YELLOW));
                    source.sendMessage(Text.literal("4. Item Restrictions: No Netherite armor/weapons (Only pickaxes/hoes), No Mace, No Crystals, No Anchors.").formatted(Formatting.DARK_RED));
                    source.sendMessage(Text.literal("5. The End Dimension: Currently " + (EndLockManager.isEndLocked() ? "LOCKED 🔒" : "UNLOCKED 🌌")).formatted(Formatting.LIGHT_PURPLE));
                    // PvP countdown status
                    if (PvPCountdownManager.isPvPEnabled()) {
                        source.sendMessage(Text.literal("6. PvP: ENABLED ⚔").formatted(Formatting.RED));
                    } else if (PvPCountdownManager.isCountdownActive()) {
                        String timeStr = PvPCountdownManager.formatDuration(PvPCountdownManager.getTimeRemainingMs());
                        source.sendMessage(Text.literal("6. PvP: DISABLED 🛡 (Enabling in: " + timeStr + ")").formatted(Formatting.YELLOW));
                    } else {
                        source.sendMessage(Text.literal("6. PvP: DISABLED 🛡 (Countdown not started)").formatted(Formatting.GREEN));
                    }
                    return 1;
                })
        );

        // /smphelp — available to all players
        dispatcher.register(
            CommandManager.literal("smphelp")
                .executes(ctx -> showHelp(ctx.getSource()))
        );

        // Direct Manual Admin Ban / Unban Shortcuts (OP Level 2 Required)
        dispatcher.register(
            CommandManager.literal("smpban")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                    .executes(ctx -> manualBan(ctx.getSource(), StringArgumentType.getString(ctx, "player"), "Manual Admin Ban"))
                    .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> manualBan(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "reason")))
                    )
                )
        );

        dispatcher.register(
            CommandManager.literal("smpunban")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("player_name", StringArgumentType.word())
                    .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                    .executes(ctx -> manualUnban(ctx.getSource(), StringArgumentType.getString(ctx, "player_name")))
                )
        );

        // /staffmode or /smpstaff (OP Level 2+)
        dispatcher.register(
            CommandManager.literal("staffmode")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        StaffModeManager.toggleStaffMode(player);
                        return 1;
                    }
                    return 0;
                })
        );

        dispatcher.register(
            CommandManager.literal("smpstaff")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        StaffModeManager.toggleStaffMode(player);
                        return 1;
                    }
                    return 0;
                })
        );

        // /smpdashboard (OP Level 2+ Security & Activity Dashboard)
        dispatcher.register(
            CommandManager.literal("smpdashboard")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(ctx -> viewSecurityDashboard(ctx.getSource()))
                .then(CommandManager.literal("kills")
                    .executes(ctx -> viewPvPKills(ctx.getSource()))
                )
                .then(CommandManager.literal("deaths")
                    .executes(ctx -> viewDeathStats(ctx.getSource()))
                )
                .then(CommandManager.literal("playtime")
                    .executes(ctx -> viewPlaytime(ctx.getSource()))
                )
                .then(CommandManager.literal("activity")
                    .executes(ctx -> viewActivityLogs(ctx.getSource()))
                )
                .then(CommandManager.literal("strikes")
                    .executes(ctx -> viewPlayerStrikes(ctx.getSource()))
                )
                .then(CommandManager.literal("tax")
                    .executes(ctx -> viewTaxDashboard(ctx.getSource()))
                )
                .then(CommandManager.literal("clear")
                    .executes(ctx -> clearDashboardLogs(ctx.getSource()))
                )
                .then(CommandManager.literal("pardon")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                        .executes(ctx -> pardonPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                    )
                )
        );

        // /smpadmin command tree (OP Level 2 Required)
        dispatcher.register(
            CommandManager.literal("smpadmin")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("border")
                    .then(CommandManager.literal("set")
                        .then(CommandManager.argument("blocks", IntegerArgumentType.integer(10, 30000000))
                            .executes(ctx -> setWorldBorder(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "blocks")))
                        )
                    )
                    .then(CommandManager.literal("expand")
                        .then(CommandManager.argument("blocks", IntegerArgumentType.integer(1, 1000000))
                            .executes(ctx -> expandWorldBorder(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "blocks")))
                        )
                    )
                    .then(CommandManager.literal("status")
                        .executes(ctx -> checkWorldBorderStatus(ctx.getSource()))
                    )
                )
                .then(CommandManager.literal("endlock")
                    .executes(ctx -> lockEndDimension(ctx.getSource(), true))
                )
                .then(CommandManager.literal("endunlock")
                    .executes(ctx -> lockEndDimension(ctx.getSource(), false))
                )
                .then(CommandManager.literal("endstatus")
                    .executes(ctx -> checkEndStatus(ctx.getSource()))
                )
                .then(CommandManager.literal("ban")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                        .executes(ctx -> manualBan(ctx.getSource(), StringArgumentType.getString(ctx, "player"), "Manual Admin Ban"))
                        .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                            .executes(ctx -> manualBan(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "reason")))
                        )
                    )
                )
                .then(CommandManager.literal("unban")
                    .then(CommandManager.argument("player_name", StringArgumentType.word())
                        .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                        .executes(ctx -> manualUnban(ctx.getSource(), StringArgumentType.getString(ctx, "player_name")))
                    )
                )
                .then(CommandManager.literal("spawntoggle")
                    .executes(ctx -> toggleSpawnProtection(ctx.getSource()))
                )
                .then(CommandManager.literal("resetdata")
                    .executes(ctx -> resetAllData(ctx.getSource()))
                )
                .then(CommandManager.literal("spawnallow")
                    .then(CommandManager.argument("player_name", StringArgumentType.word())
                        .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                        .executes(ctx -> allowSpawnBuilder(ctx.getSource(), StringArgumentType.getString(ctx, "player_name")))
                    )
                )
                .then(CommandManager.literal("spawnrevoke")
                    .then(CommandManager.argument("player_name", StringArgumentType.word())
                        .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                        .executes(ctx -> revokeSpawnBuilder(ctx.getSource(), StringArgumentType.getString(ctx, "player_name")))
                    )
                )
                .then(CommandManager.literal("spawnbuilders")
                    .executes(ctx -> listSpawnBuilders(ctx.getSource()))
                )
                .then(CommandManager.literal("pvpstart")
                    .executes(ctx -> startPvPCountdown(ctx.getSource()))
                )
                .then(CommandManager.literal("pvpstatus")
                    .executes(ctx -> checkPvPStatus(ctx.getSource()))
                )
                .then(CommandManager.literal("pvpon")
                    .executes(ctx -> forceEnablePvP(ctx.getSource()))
                )
                .then(CommandManager.literal("pvpoff")
                    .executes(ctx -> forceDisablePvP(ctx.getSource()))
                )
                .then(CommandManager.literal("tpspawn")
                    .executes(ctx -> tpToSpawn(ctx.getSource(), null))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> tpToSpawn(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                    )
                )
                .then(CommandManager.literal("resetinventories")
                    .executes(ctx -> resetAllInventories(ctx.getSource()))
                )
                .then(CommandManager.literal("markpaid")
                    .then(CommandManager.argument("team", StringArgumentType.word())
                        .suggests(TEAM_SUGGESTIONS)
                        .executes(ctx -> markTeamPaid(ctx.getSource(), StringArgumentType.getString(ctx, "team"), true))
                    )
                )
                .then(CommandManager.literal("markunpaid")
                    .then(CommandManager.argument("team", StringArgumentType.word())
                        .suggests(TEAM_SUGGESTIONS)
                        .executes(ctx -> markTeamPaid(ctx.getSource(), StringArgumentType.getString(ctx, "team"), false))
                    )
                )
                .then(CommandManager.literal("flaghacker")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                        .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                            .executes(ctx -> flagHacker(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "reason")))
                        )
                        .executes(ctx -> flagHacker(ctx.getSource(), StringArgumentType.getString(ctx, "player"), "Manual Admin Flag"))
                    )
                )
                .then(CommandManager.literal("bounty")
                    .then(CommandManager.literal("add")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                            .executes(ctx -> addBounty(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                        )
                    )
                    .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                            .executes(ctx -> removeBounty(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                        )
                    )
                    .then(CommandManager.literal("list")
                        .executes(ctx -> listBounties(ctx.getSource()))
                    )
                )
        );

        // /smpstrike command tree (OP Level 2 Required)
        dispatcher.register(
            CommandManager.literal("smpstrike")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                        .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                            .executes(ctx -> flagHacker(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "reason")))
                        )
                        .executes(ctx -> flagHacker(ctx.getSource(), StringArgumentType.getString(ctx, "player"), "Manual Admin Strike"))
                    )
                )
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                        .executes(ctx -> removeOneStrike(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                    )
                )
                .then(CommandManager.literal("clear")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                        .executes(ctx -> pardonPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                    )
                )
        );

        // /smpteam command tree
        dispatcher.register(
            CommandManager.literal("smpteam")
                // ADMIN ONLY COMMANDS (Requires OP Permission Level 2+)
                .then(CommandManager.literal("create")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("peaceful")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .then(CommandManager.argument("leader", StringArgumentType.word())
                                .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                                .executes(ctx -> adminCreateTeam(ctx.getSource(), StringArgumentType.getString(ctx, "name"), Team.TeamType.PEACEFUL, StringArgumentType.getString(ctx, "leader")))
                            )
                        )
                    )
                    .then(CommandManager.literal("pvp")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .then(CommandManager.argument("leader", StringArgumentType.word())
                                .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                                .executes(ctx -> adminCreateTeam(ctx.getSource(), StringArgumentType.getString(ctx, "name"), Team.TeamType.PVP, StringArgumentType.getString(ctx, "leader")))
                            )
                        )
                    )
                )
                .then(CommandManager.literal("addplayer")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("team", StringArgumentType.word())
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                            .executes(ctx -> adminAddPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "team"), StringArgumentType.getString(ctx, "player")))
                        )
                    )
                )
                .then(CommandManager.literal("removeplayer")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests(ALL_PLAYERS_SUGGESTION_PROVIDER)
                        .executes(ctx -> adminRemovePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                    )
                )
                .then(CommandManager.literal("setpeaceful")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("team", StringArgumentType.word())
                        .suggests(TEAM_SUGGESTIONS)
                        .executes(ctx -> adminSetType(ctx.getSource(), StringArgumentType.getString(ctx, "team"), Team.TeamType.PEACEFUL))
                    )
                )
                .then(CommandManager.literal("setpvp")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("team", StringArgumentType.word())
                        .suggests(TEAM_SUGGESTIONS)
                        .executes(ctx -> adminSetType(ctx.getSource(), StringArgumentType.getString(ctx, "team"), Team.TeamType.PVP))
                    )
                )
                .then(CommandManager.literal("settype")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("team", StringArgumentType.word())
                        .suggests(TEAM_SUGGESTIONS)
                        .then(CommandManager.literal("peaceful")
                            .executes(ctx -> adminSetType(ctx.getSource(), StringArgumentType.getString(ctx, "team"), Team.TeamType.PEACEFUL))
                        )
                        .then(CommandManager.literal("pvp")
                            .executes(ctx -> adminSetType(ctx.getSource(), StringArgumentType.getString(ctx, "team"), Team.TeamType.PVP))
                        )
                    )
                )
                .then(CommandManager.literal("disband")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("team", StringArgumentType.word())
                        .suggests(TEAM_SUGGESTIONS)
                        .executes(ctx -> adminDisbandTeam(ctx.getSource(), StringArgumentType.getString(ctx, "team")))
                    )
                )
                // PLAYER ACCESSIBLE COMMANDS
                .then(CommandManager.literal("info")
                    .executes(ctx -> teamInfo(ctx.getSource(), null))
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests(TEAM_SUGGESTIONS)
                        .executes(ctx -> teamInfo(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                    )
                )
                .then(CommandManager.literal("list")
                    .executes(ctx -> listTeams(ctx.getSource()))
                )
        );

        dispatcher.register(CommandManager.literal("smpcoords")
            .executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player != null) {
                    HUDManager.toggleCoords(player);
                }
                return 1;
            })
        );

        // /smpclaim command tree
        dispatcher.register(
            CommandManager.literal("smpclaim")
                .then(CommandManager.literal("claim")
                    .executes(ctx -> claimCurrentChunk(ctx.getSource()))
                )
                .then(CommandManager.literal("unclaim")
                    .executes(ctx -> unclaimCurrentChunk(ctx.getSource()))
                )
                .then(CommandManager.literal("highlight")
                    .executes(ctx -> highlightChunk(ctx.getSource()))
                )
                .then(CommandManager.literal("view")
                    .executes(ctx -> viewTeamClaims(ctx.getSource()))
                )
                .then(CommandManager.literal("info")
                    .executes(ctx -> claimInfo(ctx.getSource()))
                )
        );
    }

    // --- WORLD BORDER ADMIN HANDLERS ---

    private static int setWorldBorder(ServerCommandSource source, int blocks) {
        ServerWorld world = source.getWorld();
        BlockPos spawnPos = world.getSpawnPos();
        WorldBorder border = world.getWorldBorder();

        border.setCenter(spawnPos.getX() + 0.5, spawnPos.getZ() + 0.5);
        border.setSize(blocks);

        source.sendMessage(
            Text.literal("🌐 [World Border] World Border centered on World Spawn and set to " + blocks + "x" + blocks + " blocks!")
                .formatted(Formatting.GREEN, Formatting.BOLD)
        );

        source.getServer().getPlayerManager().broadcast(
            Text.literal("🌐 [World Border] The World Border is now set to " + blocks + "x" + blocks + " blocks around Spawn!")
                .formatted(Formatting.AQUA, Formatting.BOLD),
            false
        );
        return 1;
    }

    private static int expandWorldBorder(ServerCommandSource source, int additionalBlocks) {
        ServerWorld world = source.getWorld();
        WorldBorder border = world.getWorldBorder();
        double currentSize = border.getSize();
        double newSize = currentSize + additionalBlocks;

        border.setSize(newSize);

        source.sendMessage(
            Text.literal("🌐 [World Border] Expanded World Border by " + additionalBlocks + " blocks! (New size: " + (int) newSize + "x" + (int) newSize + " blocks)")
                .formatted(Formatting.GREEN, Formatting.BOLD)
        );

        source.getServer().getPlayerManager().broadcast(
            Text.literal("🌐 [World Border Announcement] The World Border has EXPANDED by " + additionalBlocks + " blocks! (New Size: " + (int) newSize + "x" + (int) newSize + ")")
                .formatted(Formatting.GOLD, Formatting.BOLD),
            false
        );
        return 1;
    }

    private static int checkWorldBorderStatus(ServerCommandSource source) {
        WorldBorder border = source.getWorld().getWorldBorder();
        double size = border.getSize();
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();

        source.sendMessage(Text.literal("=== 🌐 WORLD BORDER STATUS ===").formatted(Formatting.GOLD, Formatting.BOLD));
        source.sendMessage(Text.literal("Current Size: " + (int) size + " x " + (int) size + " blocks").formatted(Formatting.GREEN));
        source.sendMessage(Text.literal(String.format("Center Position: X: %.1f, Z: %.1f", centerX, centerZ)).formatted(Formatting.YELLOW));
        return 1;
    }

    // --- END DIMENSION LOCK HANDLERS ---

    private static int lockEndDimension(ServerCommandSource source, boolean locked) {
        EndLockManager.setEndLocked(source.getServer(), locked);
        return 1;
    }

    private static int checkEndStatus(ServerCommandSource source) {
        boolean locked = EndLockManager.isEndLocked();
        if (locked) {
            source.sendMessage(Text.literal("🔒 The End Dimension is currently LOCKED.").formatted(Formatting.RED, Formatting.BOLD));
        } else {
            source.sendMessage(Text.literal("🌌 The End Dimension is currently UNLOCKED and open!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        }
        return 1;
    }

    // --- PVP COUNTDOWN HANDLERS ---

    private static int startPvPCountdown(ServerCommandSource source) {
        if (PvPCountdownManager.isPvPEnabled()) {
            source.sendError(Text.literal("PvP is already enabled! Use /smpadmin pvpoff to disable and reset first."));
            return 0;
        }
        if (PvPCountdownManager.isCountdownActive()) {
            String timeStr = PvPCountdownManager.formatDuration(PvPCountdownManager.getTimeRemainingMs());
            source.sendError(Text.literal("The PvP countdown is already active! PvP enables in: " + timeStr));
            return 0;
        }
        PvPCountdownManager.startCountdown(source.getServer());
        return 1;
    }

    private static int checkPvPStatus(ServerCommandSource source) {
        source.sendMessage(Text.literal("=== ⚔ PVP COUNTDOWN STATUS ===").formatted(Formatting.GOLD, Formatting.BOLD));
        if (PvPCountdownManager.isPvPEnabled()) {
            source.sendMessage(Text.literal("Status: PvP is ENABLED ⚔").formatted(Formatting.RED, Formatting.BOLD));
        } else if (PvPCountdownManager.isCountdownActive()) {
            String timeStr = PvPCountdownManager.formatDuration(PvPCountdownManager.getTimeRemainingMs());
            source.sendMessage(Text.literal("Status: Countdown ACTIVE ⏳").formatted(Formatting.YELLOW, Formatting.BOLD));
            source.sendMessage(Text.literal("PvP enables in: " + timeStr).formatted(Formatting.GOLD));
        } else {
            source.sendMessage(Text.literal("Status: PvP DISABLED 🛡 (Countdown not started)").formatted(Formatting.GREEN, Formatting.BOLD));
            source.sendMessage(Text.literal("Use /smpadmin pvpstart to begin the 48-hour countdown.").formatted(Formatting.GRAY));
        }
        return 1;
    }

    private static int forceEnablePvP(ServerCommandSource source) {
        PvPCountdownManager.forceEnablePvP(source.getServer());
        source.sendMessage(Text.literal("⚔ PvP has been force-enabled immediately!").formatted(Formatting.RED, Formatting.BOLD));
        return 1;
    }

    private static int forceDisablePvP(ServerCommandSource source) {
        PvPCountdownManager.forceDisablePvP(source.getServer());
        source.sendMessage(Text.literal("🛡 PvP has been disabled and the countdown reset.").formatted(Formatting.GREEN, Formatting.BOLD));
        return 1;
    }

    // --- MANUAL ADMIN BAN / UNBAN OVERRIDES ---

    private static int manualBan(ServerCommandSource source, String targetName, String reason) {
        UUID uuid = PlayerMemory.getUuidByName(targetName);
        if (uuid == null) {
            source.sendError(Text.literal("Player '" + targetName + "' has never joined this server."));
            return 0;
        }
        AntiCheatManager.manualBanPlayer(source, uuid, targetName, reason);
        return 1;
    }

    private static int manualUnban(ServerCommandSource source, String targetName) {
        if (AntiCheatManager.manualUnbanPlayer(source, targetName)) {
            return 1;
        }
        return 0;
    }

    // --- DASHBOARD & ACTIVITY STATS HANDLERS ---

    private static int viewSecurityDashboard(ServerCommandSource source) {
        List<AntiCheatManager.SecurityLogEntry> logs = AntiCheatManager.getLogs();
        source.sendMessage(Text.literal("=== 🛡 ADMIN SECURITY DASHBOARD ===").formatted(Formatting.GOLD, Formatting.BOLD));

        if (logs.isEmpty()) {
            source.sendMessage(Text.literal("No security violations logged.").formatted(Formatting.GREEN));
        } else {
            int limit = Math.min(5, logs.size());
            source.sendMessage(Text.literal("Security Flags (Last " + limit + "):").formatted(Formatting.YELLOW));
            for (int i = 0; i < limit; i++) {
                AntiCheatManager.SecurityLogEntry log = logs.get(i);
                source.sendMessage(Text.literal(String.format("⏱ [%s] %s - %s (Strike %d)", log.timestamp, log.playerName, log.violationType, log.strikeNumber))
                    .formatted(log.strikeNumber == 3 ? Formatting.RED : Formatting.GOLD));
            }
        }

        source.sendMessage(Text.literal("💡 Type '/smpdashboard kills', '/smpdashboard deaths', or '/smpdashboard playtime' for details.").formatted(Formatting.AQUA));
        return 1;
    }

    private static int viewPvPKills(ServerCommandSource source) {
        source.sendMessage(Text.literal("=== ⚔ PVP KILL FEED (WHO KILLED WHO) ===").formatted(Formatting.RED, Formatting.BOLD));
        List<DashboardStatsManager.ActivityLogEntry> logs = DashboardStatsManager.getActivityLogs();

        int count = 0;
        for (DashboardStatsManager.ActivityLogEntry log : logs) {
            if ("PVP_KILL".equalsIgnoreCase(log.type)) {
                source.sendMessage(Text.literal(String.format("⏱ [%s] %s", log.timestamp, log.description)).formatted(Formatting.YELLOW));
                count++;
                if (count >= 10) break;
            }
        }

        if (count == 0) {
            source.sendMessage(Text.literal("No PvP kills recorded while you were away.").formatted(Formatting.GREEN));
        }
        return 1;
    }

    private static int viewDeathStats(ServerCommandSource source) {
        source.sendMessage(Text.literal("=== 💀 PLAYER DEATHS & KILLS LEADERBOARD ===").formatted(Formatting.GOLD, Formatting.BOLD));
        Map<UUID, DashboardStatsManager.PlayerStats> statsMap = DashboardStatsManager.getPlayerStatsMap();

        if (statsMap.isEmpty()) {
            source.sendMessage(Text.literal("No player deaths or kills recorded yet.").formatted(Formatting.GRAY));
            return 1;
        }

        for (DashboardStatsManager.PlayerStats stats : statsMap.values()) {
            Team team = TeamManager.getPlayerTeamByName(stats.playerName);
            source.sendMessage(Text.empty()
                .append(Text.literal("- "))
                .append(Text.literal(stats.playerName).styled(s -> s.withColor(TeamColors.fromTeam(team))))
                .append(Text.literal(String.format(": %d Deaths | %d PvP Kills", stats.deaths, stats.pvpKills)).formatted(Formatting.AQUA))
            );
        }
        return 1;
    }

    private static int viewPlaytime(ServerCommandSource source) {
        source.sendMessage(Text.literal("=== ⏳ PLAYER PLAYTIME & SESSION LOGS ===").formatted(Formatting.GREEN, Formatting.BOLD));
        Map<UUID, DashboardStatsManager.PlayerStats> statsMap = DashboardStatsManager.getPlayerStatsMap();

        if (statsMap.isEmpty()) {
            source.sendMessage(Text.literal("No playtime stats recorded yet.").formatted(Formatting.GRAY));
            return 1;
        }

        for (DashboardStatsManager.PlayerStats stats : statsMap.values()) {
            String timeStr = DashboardStatsManager.formatDuration(stats.totalPlaytimeMs);
            Team team = TeamManager.getPlayerTeamByName(stats.playerName);
            source.sendMessage(Text.empty()
                .append(Text.literal("- "))
                .append(Text.literal(stats.playerName).styled(s -> s.withColor(TeamColors.fromTeam(team))))
                .append(Text.literal(String.format(": Total Playtime: %s", timeStr)).formatted(Formatting.YELLOW))
            );
        }
        return 1;
    }

    private static int viewActivityLogs(ServerCommandSource source) {
        source.sendMessage(Text.literal("=== 🚪 RECENT PLAYER SESSIONS & ACTIVITY ===").formatted(Formatting.AQUA, Formatting.BOLD));
        List<DashboardStatsManager.ActivityLogEntry> logs = DashboardStatsManager.getActivityLogs();

        if (logs.isEmpty()) {
            source.sendMessage(Text.literal("No recent player activity logged.").formatted(Formatting.GRAY));
            return 1;
        }

        int limit = Math.min(10, logs.size());
        for (int i = 0; i < limit; i++) {
            DashboardStatsManager.ActivityLogEntry log = logs.get(i);
            source.sendMessage(Text.literal(String.format("⏱ [%s] %s", log.timestamp, log.description)).formatted(Formatting.GRAY));
        }
        return 1;
    }

    private static int viewPlayerStrikes(ServerCommandSource source) {
        Map<UUID, Integer> strikes = AntiCheatManager.getPlayerStrikes();
        source.sendMessage(Text.literal("=== ⚠️ PLAYER STRIKE LOG ===").formatted(Formatting.GOLD, Formatting.BOLD));

        if (strikes.isEmpty()) {
            source.sendMessage(Text.literal("No players currently have strikes.").formatted(Formatting.GREEN));
            return 1;
        }

        for (Map.Entry<UUID, Integer> entry : strikes.entrySet()) {
            ServerPlayerEntity p = source.getServer().getPlayerManager().getPlayer(entry.getKey());
            String name = (p != null) ? p.getName().getString() : entry.getKey().toString();
            source.sendMessage(Text.literal("- " + name + ": " + entry.getValue() + " Strike(s)")
                .formatted(entry.getValue() >= 2 ? Formatting.RED : Formatting.YELLOW));
        }
        return 1;
    }

    private static int clearDashboardLogs(ServerCommandSource source) {
        AntiCheatManager.clearLogs();
        source.sendMessage(Text.literal("🧹 Security dashboard log history cleared.").formatted(Formatting.GREEN));
        return 1;
    }

    private static int pardonPlayer(ServerCommandSource source, String targetName) {
        UUID uuid = PlayerMemory.getUuidByName(targetName);
        if (uuid == null) {
            source.sendError(Text.literal("Player '" + targetName + "' has never joined this server."));
            return 0;
        }
        AntiCheatManager.resetPlayerStrikes(uuid);
        source.sendMessage(Text.literal("✅ Pardoned " + targetName + ". Strikes reset to 0.").formatted(Formatting.GREEN));
        return 1;
    }

    private static int removeOneStrike(ServerCommandSource source, String targetName) {
        UUID uuid = PlayerMemory.getUuidByName(targetName);
        if (uuid == null) {
            source.sendError(Text.literal("Player '" + targetName + "' has never joined this server."));
            return 0;
        }
        int current = AntiCheatManager.getPlayerStrikes().getOrDefault(uuid, 0);
        if (current <= 0) {
            source.sendError(Text.literal("Player '" + targetName + "' has no strikes to remove."));
            return 0;
        }
        int newCount = current - 1;
        if (newCount <= 0) {
            AntiCheatManager.resetPlayerStrikes(uuid);
        } else {
            AntiCheatManager.getPlayerStrikes().put(uuid, newCount);
            AntiCheatManager.save();
        }
        source.sendMessage(Text.literal("✅ Removed 1 strike from " + targetName + ". Now at " + newCount + " strike(s).").formatted(Formatting.GREEN));
        return 1;
    }

    // --- FLAG HACKER COMMAND HANDLER ---

    private static int flagHacker(ServerCommandSource source, String playerName, String reason) {
        UUID targetUuid = PlayerMemory.getUuidByName(playerName);
        if (targetUuid == null) {
            source.sendError(Text.literal("Player '" + playerName + "' has never joined this server."));
            return 0;
        }

        AntiCheatManager.flagPlayer(playerName, reason);
        int strikes = AntiCheatManager.getPlayerStrikes().getOrDefault(targetUuid, 0);

        source.sendMessage(Text.literal("🚨 Flagged " + playerName + " for: " + reason + " (Now at " + strikes + " strike(s))").formatted(Formatting.RED, Formatting.BOLD));

        // Notify all other online admins
        for (ServerPlayerEntity admin : source.getServer().getPlayerManager().getPlayerList()) {
            if (admin.hasPermissionLevel(2) && !admin.getName().getString().equals(source.getName())) {
                admin.sendMessage(Text.literal("🚨 [AntiCheat] " + source.getName() + " flagged " + playerName + " for: " + reason + " (Strike " + strikes + ")").formatted(Formatting.RED), false);
            }
        }

        // Execute strike punishment if applicable
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetUuid);
        if (target != null) {
            if (strikes == 1) {
                target.sendMessage(Text.literal("⚠ [Warning] You have been flagged by an admin for: " + reason + ". This is your first strike.").formatted(Formatting.YELLOW, Formatting.BOLD), false);
            } else if (strikes == 2) {
                target.sendMessage(Text.literal("🚨 [Strike 2] You have been flagged again for: " + reason + ". Your inventory has been wiped as punishment.").formatted(Formatting.RED, Formatting.BOLD), false);
                target.getInventory().clear();
            } else if (strikes >= 3) {
                AntiCheatManager.manualBanPlayer(source, targetUuid, playerName, "3 strikes: " + reason);
            }
        }

        return 1;
    }

    // --- BOUNTY SYSTEM HANDLERS ---

    private static int addBounty(ServerCommandSource source, String targetName) {
        UUID uuid = PlayerMemory.getUuidByName(targetName);
        if (uuid == null) {
            source.sendError(Text.literal("Player '" + targetName + "' has never joined this server."));
            return 0;
        }
        boolean success = com.smprules.admin.BountyManager.addBounty(source.getServer(), uuid);
        if (success) {
            source.sendMessage(Text.literal("🎯 Added " + targetName + " to the active bounties!").formatted(Formatting.GREEN));
        } else {
            source.sendError(Text.literal("Could not add bounty! Either the player is already a bounty, or the maximum limit (2) is reached."));
            return 0;
        }
        return 1;
    }

    private static int removeBounty(ServerCommandSource source, String targetName) {
        UUID uuid = PlayerMemory.getUuidByName(targetName);
        if (uuid == null) {
            source.sendError(Text.literal("Player '" + targetName + "' has never joined this server."));
            return 0;
        }
        boolean success = com.smprules.admin.BountyManager.removeBounty(source.getServer(), uuid);
        if (success) {
            source.sendMessage(Text.literal("✅ Removed " + targetName + " from active bounties.").formatted(Formatting.GREEN));
        } else {
            source.sendError(Text.literal("Player '" + targetName + "' is not currently a bounty."));
            return 0;
        }
        return 1;
    }

    private static int listBounties(ServerCommandSource source) {
        Map<UUID, Long> bounties = com.smprules.admin.BountyManager.getActiveBounties();
        if (bounties.isEmpty()) {
            source.sendMessage(Text.literal("No active bounties.").formatted(Formatting.YELLOW));
            return 1;
        }
        source.sendMessage(Text.literal("=== 🎯 ACTIVE BOUNTIES ===").formatted(Formatting.GOLD, Formatting.BOLD));
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : bounties.entrySet()) {
            String name = PlayerMemory.getNameByUuid(entry.getKey());
            if (name == null) name = entry.getKey().toString();
            long elapsedMs = now - entry.getValue();
            long hours = elapsedMs / (60 * 60 * 1000);
            long minutes = (elapsedMs / (60 * 1000)) % 60;
            source.sendMessage(Text.literal("- " + name + " (Active for " + hours + "h " + minutes + "m)").formatted(Formatting.YELLOW));
        }
        return 1;
    }

    // --- TESTING & SPAWN ADMIN HANDLERS ---

    private static int toggleSpawnProtection(ServerCommandSource source) {
        boolean state = SpawnProtectionManager.toggleSpawnProtection();
        if (state) {
            source.sendMessage(Text.literal("🔒 [Spawn Protection] Spawn Protection is now ENABLED! (No-build active for regular players)").formatted(Formatting.GREEN, Formatting.BOLD));
        } else {
            source.sendMessage(Text.literal("🔓 [Spawn Protection] Spawn Protection is now DISABLED for testing! (Everyone can build/break in spawn)").formatted(Formatting.YELLOW, Formatting.BOLD));
        }
        return 1;
    }

    private static int resetAllData(ServerCommandSource source) {
        TeamManager.clearAll();
        ClaimManager.clearAll();
        SpawnProtectionManager.resetAll();
        source.sendMessage(Text.literal("🧹 [Testing Reset] All test teams, test claims, and test permissions have been CLEARED to factory defaults!")
            .formatted(Formatting.RED, Formatting.BOLD));
        return 1;
    }

    private static int allowSpawnBuilder(ServerCommandSource source, String targetName) {
        UUID targetUuid = PlayerMemory.getUuidByName(targetName);
        if (targetUuid == null) {
            source.sendError(Text.literal("Player '" + targetName + "' has never joined this server."));
            return 0;
        }

        if (SpawnProtectionManager.allowSpawnBuilder(targetUuid)) {
            source.sendMessage(Text.literal("🏗 [Spawn Protection] Granted temporary spawn building permission to " + targetName).formatted(Formatting.GREEN));
            ServerPlayerEntity onlinePlayer = source.getServer().getPlayerManager().getPlayer(targetUuid);
            if (onlinePlayer != null) {
                onlinePlayer.sendMessage(Text.literal("🏗 You have been granted permission to build and decorate in the Spawn Area!").formatted(Formatting.GOLD));
            }
            return 1;
        } else {
            source.sendError(Text.literal("Player " + targetName + " already has spawn builder permission."));
            return 0;
        }
    }

    private static int revokeSpawnBuilder(ServerCommandSource source, String targetName) {
        UUID targetUuid = PlayerMemory.getUuidByName(targetName);
        if (targetUuid == null) {
            source.sendError(Text.literal("Player '" + targetName + "' has never joined this server."));
            return 0;
        }

        if (SpawnProtectionManager.revokeSpawnBuilder(targetUuid)) {
            source.sendMessage(Text.literal("🏗 [Spawn Protection] Revoked spawn building permission from " + targetName).formatted(Formatting.YELLOW));
            ServerPlayerEntity onlinePlayer = source.getServer().getPlayerManager().getPlayer(targetUuid);
            if (onlinePlayer != null) {
                onlinePlayer.sendMessage(Text.literal("🏗 Your spawn building permission has been revoked by the admin.").formatted(Formatting.RED));
            }
            return 1;
        } else {
            source.sendError(Text.literal("Player " + targetName + " does not have spawn builder permission."));
            return 0;
        }
    }

    private static int listSpawnBuilders(ServerCommandSource source) {
        source.sendMessage(Text.literal("=== Permitted Spawn Builders ===").formatted(Formatting.GOLD));
        if (SpawnProtectionManager.getSpawnBuilders().isEmpty()) {
            source.sendMessage(Text.literal("No temporary spawn builders currently assigned.").formatted(Formatting.GRAY));
            return 1;
        }
        for (UUID uuid : SpawnProtectionManager.getSpawnBuilders()) {
            ServerPlayerEntity p = source.getServer().getPlayerManager().getPlayer(uuid);
            String name = (p != null) ? p.getName().getString() : uuid.toString();
            source.sendMessage(Text.literal("- " + name).formatted(Formatting.YELLOW));
        }
        return 1;
    }

    // --- ADMIN TEAM COMMAND HANDLERS ---

    private static int adminCreateTeam(ServerCommandSource source, String name, Team.TeamType type, String leaderName) {
        UUID leaderUuid = PlayerMemory.getUuidByName(leaderName);
        if (leaderUuid == null) {
            source.sendError(Text.literal("Player '" + leaderName + "' has never joined this server."));
            return 0;
        }

        if (TeamManager.getPlayerTeam(leaderUuid) != null) {
            source.sendError(Text.literal("Player " + leaderName + " is already in a team!"));
            return 0;
        }

        Team team = TeamManager.createTeam(name, type, leaderUuid);
        if (team == null) {
            source.sendError(Text.literal("A team with that name already exists!"));
            return 0;
        }

        source.sendMessage(Text.empty().append(Text.literal("🛠 [Admin] Created " + type.name() + " team: ").formatted(Formatting.GOLD)).append(Text.literal(name).styled(s -> s.withColor(TeamColors.fromTeam(team)))).append(Text.literal(" with leader: " + leaderName).formatted(Formatting.GOLD)));
        
        ServerPlayerEntity onlineLeader = source.getServer().getPlayerManager().getPlayer(leaderUuid);
        if (onlineLeader != null) {
            onlineLeader.sendMessage(Text.empty().append(Text.literal("👑 You have been made the leader of a new team: ").formatted(Formatting.GOLD)).append(Text.literal(name).styled(s -> s.withColor(TeamColors.fromTeam(team)))));
        }
        return 1;
    }

    private static int adminAddPlayer(ServerCommandSource source, String teamName, String targetName) {
        UUID targetUuid = PlayerMemory.getUuidByName(targetName);
        if (targetUuid == null) {
            source.sendError(Text.literal("Player '" + targetName + "' has never joined this server."));
            return 0;
        }

        Team team = TeamManager.getTeam(teamName);
        if (team == null) {
            source.sendError(Text.literal("Team '" + teamName + "' not found!"));
            return 0;
        }

        if (TeamManager.getPlayerTeam(targetUuid) != null) {
            source.sendError(Text.literal("Player " + targetName + " is already in a team!"));
            return 0;
        }

        if (!team.addMember(targetUuid)) {
            source.sendError(Text.literal("Team '" + teamName + "' is full! (Max " + team.getMaxMembers() + " players for " + team.getType().name() + ")"));
            return 0;
        }

        TeamManager.updatePlayerMapping(targetUuid, team.getName());
        source.sendMessage(Text.empty().append(Text.literal("🛠 [Admin] Added " + targetName + " to team ").formatted(Formatting.GREEN)).append(Text.literal(team.getName()).styled(s -> s.withColor(TeamColors.fromTeam(team)))));
        
        ServerPlayerEntity onlinePlayer = source.getServer().getPlayerManager().getPlayer(targetUuid);
        if (onlinePlayer != null) {
            onlinePlayer.sendMessage(Text.empty().append(Text.literal("🛡 You have been added to " + team.getType().name() + " team: ").formatted(Formatting.GREEN)).append(Text.literal(team.getName()).styled(s -> s.withColor(TeamColors.fromTeam(team)))));
        }
        return 1;
    }

    private static int adminRemovePlayer(ServerCommandSource source, String targetName) {
        UUID targetUuid = PlayerMemory.getUuidByName(targetName);
        if (targetUuid == null) {
            source.sendError(Text.literal("Player '" + targetName + "' has never joined this server."));
            return 0;
        }

        Team team = TeamManager.getPlayerTeam(targetUuid);
        if (team == null) {
            source.sendError(Text.literal("Player " + targetName + " is not in a team!"));
            return 0;
        }

        team.removeMember(targetUuid);
        TeamManager.updatePlayerMapping(targetUuid, null);
        source.sendMessage(Text.empty().append(Text.literal("👑 [Admin] Removed " + targetName + " from team ").formatted(Formatting.YELLOW)).append(Text.literal(team.getName()).styled(s -> s.withColor(TeamColors.fromTeam(team)))));
        
        ServerPlayerEntity onlinePlayer = source.getServer().getPlayerManager().getPlayer(targetUuid);
        if (onlinePlayer != null) {
            onlinePlayer.sendMessage(Text.empty().append(Text.literal("👑 You were removed from team ").formatted(Formatting.RED)).append(Text.literal(team.getName()).styled(s -> s.withColor(TeamColors.fromTeam(team)))));
        }
        return 1;
    }

    private static int adminSetType(ServerCommandSource source, String teamName, Team.TeamType type) {
        Team team = TeamManager.getTeam(teamName);
        if (team == null) {
            source.sendError(Text.literal("Team '" + teamName + "' not found!"));
            return 0;
        }

        team.setType(type);
        TeamManager.save();

        source.sendMessage(Text.empty().append(Text.literal("👑 [Admin] Updated team ").formatted(Formatting.GOLD, Formatting.BOLD)).append(Text.literal(team.getName()).styled(s -> s.withColor(TeamColors.fromTeam(team)).withBold(true))).append(Text.literal(" category to: " + type.name()).formatted(Formatting.GOLD, Formatting.BOLD)));

        if (type == Team.TeamType.PVP && source.getServer() != null) {
            int removed = ClaimManager.trimClaimsForPvPSwitch(source.getServer(), team);
            if (removed > 0) {
                source.sendMessage(Text.literal("🏗 [Claim Auto-Trim] Automatically unclaimed the " + removed + " least built-on chunk(s) to match the 20-chunk PvP team limit.")
                    .formatted(Formatting.YELLOW));
            }
        }

        return 1;
    }

    private static int adminDisbandTeam(ServerCommandSource source, String teamName) {
        if (TeamManager.disbandTeam(teamName)) {
            source.sendMessage(Text.literal("👑 [Admin] Disbanded team " + teamName).formatted(Formatting.RED));
            return 1;
        } else {
            source.sendError(Text.literal("Team '" + teamName + "' not found!"));
            return 0;
        }
    }

    // --- PLAYER COMMAND HANDLERS ---

    private static int listTeams(ServerCommandSource source) {
        Collection<Team> teams = TeamManager.getAllTeams();
        if (teams.isEmpty()) {
            source.sendMessage(Text.literal("No teams currently registered.").formatted(Formatting.GRAY));
            return 1;
        }

        source.sendMessage(Text.literal("=== Registered Teams ===").formatted(Formatting.GOLD));
        for (Team team : teams) {
            source.sendMessage(Text.empty()
                .append(Text.literal("- "))
                .append(Text.literal(team.getName()).styled(s -> s.withColor(TeamColors.fromTeam(team))))
                .append(Text.literal(" [" + team.getColorName() + " | " + team.getType().name() + "] (" + team.getMembers().size() + "/" + team.getMaxMembers() + " members, " + ClaimManager.getClaimCount(team.getName()) + "/" + ClaimManager.getMaxClaimsForTeam(team) + " claims)")
                    .formatted(team.getType() == Team.TeamType.PEACEFUL ? Formatting.GREEN : Formatting.RED))
            );
        }
        return 1;
    }

    private static int teamInfo(ServerCommandSource source, String targetName) {
        Team team = null;
        if (targetName != null) {
            team = TeamManager.getTeam(targetName);
        } else if (source.getEntity() instanceof ServerPlayerEntity player) {
            team = TeamManager.getPlayerTeam(player.getUuid());
        }

        if (team == null) {
            source.sendError(Text.literal("Team not found."));
            return 0;
        }

        int maxClaims = ClaimManager.getMaxClaimsForTeam(team);
        final Team finalTeam = team;
        source.sendMessage(Text.empty()
            .append(Text.literal("=== Team: ").formatted(Formatting.GOLD))
            .append(Text.literal(team.getName()).styled(s -> s.withColor(TeamColors.fromTeam(finalTeam))))
            .append(Text.literal(" (").formatted(Formatting.GOLD))
            .append(Text.literal(team.getColorName()).styled(s -> s.withColor(TeamColors.fromTeam(finalTeam))))
            .append(Text.literal(" | " + team.getType().name() + ") ===").formatted(Formatting.GOLD))
        );
        source.sendMessage(Text.literal("Members: " + team.getMembers().size() + " / " + team.getMaxMembers()));
        source.sendMessage(Text.literal("Land Claims: " + ClaimManager.getClaimCount(team.getName()) + " / " + maxClaims + " Chunks"));
        return 1;
    }

    private static int claimCurrentChunk(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        Team team = TeamManager.getPlayerTeam(player.getUuid());

        if (team == null) {
            source.sendError(Text.literal("You must be assigned to a team by an Admin to claim land!"));
            return 0;
        }

        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        ChunkPos chunkPos = player.getChunkPos();

        // Village protection — villages are public territory
        if (ClaimManager.isVillageChunk(player.getServerWorld(), chunkPos.x, chunkPos.z)) {
            source.sendError(Text.literal("⛔ Village chunks cannot be claimed! Villages are public territory for all players."));
            return 0;
        }

        if (ClaimManager.claimChunk(dimension, chunkPos.x, chunkPos.z, team)) {
            source.sendMessage(Text.empty()
                .append(Text.literal("🟦 Successfully claimed chunk [" + chunkPos.x + ", " + chunkPos.z + "] for team ").formatted(Formatting.GREEN))
                .append(Text.literal(team.getName()).styled(s -> s.withColor(TeamColors.fromTeam(team))))
                .append(Text.literal("! (" + ClaimManager.getClaimCount(team.getName()) + "/" + ClaimManager.getMaxClaimsForTeam(team) + " chunks used)").formatted(Formatting.GREEN))
            );
            
            ChunkVisualizer.highlightChunk(player, chunkPos.x, chunkPos.z, team);
            return 1;
        } else {
            source.sendError(Text.literal("Failed to claim chunk! Chunk may already be claimed or max claim limit reached (" + ClaimManager.getMaxClaimsForTeam(team) + " chunks max)."));
            return 0;
        }
    }

    private static int unclaimCurrentChunk(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        Team team = TeamManager.getPlayerTeam(player.getUuid());

        if (team == null) {
            source.sendError(Text.literal("You must be in a team to unclaim land!"));
            return 0;
        }

        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        ChunkPos chunkPos = player.getChunkPos();

        if (ClaimManager.unclaimChunk(dimension, chunkPos.x, chunkPos.z, team)) {
            source.sendMessage(Text.literal("Unclaimed chunk [" + chunkPos.x + ", " + chunkPos.z + "]").formatted(Formatting.YELLOW));
            return 1;
        } else {
            source.sendError(Text.literal("You do not own this claimed chunk!"));
            return 0;
        }
    }

    private static int highlightChunk(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        Team team = TeamManager.getPlayerTeam(player.getUuid());
        ChunkPos pos = player.getChunkPos();
        ChunkVisualizer.highlightChunk(player, pos.x, pos.z, team);
        source.sendMessage(Text.literal("🟦 Highlighting current chunk border in team color!").formatted(Formatting.AQUA));
        return 1;
    }

    private static int viewTeamClaims(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        Team team = TeamManager.getPlayerTeam(player.getUuid());

        if (team == null) {
            source.sendError(Text.literal("You are not in a team!"));
            return 0;
        }

        Collection<ClaimChunk> teamClaims = ClaimManager.getTeamClaims(team.getName());
        if (teamClaims.isEmpty()) {
            source.sendMessage(Text.literal("Your team has no claimed chunks yet.").formatted(Formatting.GRAY));
            return 1;
        }

        String currentDimension = player.getWorld().getRegistryKey().getValue().toString();
        int highlighted = 0;

        for (ClaimChunk claim : teamClaims) {
            if (claim.getDimension().equalsIgnoreCase(currentDimension)) {
                ChunkVisualizer.highlightChunk(player, claim.getX(), claim.getZ(), team);
                highlighted++;
            }
        }

        source.sendMessage(Text.literal("🟦 Highlighting " + highlighted + " team claim border(s) in this dimension!").formatted(Formatting.AQUA, Formatting.BOLD));
        return 1;
    }

    private static int claimInfo(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        ChunkPos chunkPos = player.getChunkPos();

        ClaimChunk claim = ClaimManager.getClaim(dimension, chunkPos.x, chunkPos.z);
        if (claim == null) {
            source.sendMessage(Text.literal("Wilderness (Unclaimed Land)").formatted(Formatting.GRAY));
        } else {
            Team owner = TeamManager.getTeam(claim.getTeamName());
            String colorStr = (owner != null) ? owner.getColorName() : "WHITE";
            source.sendMessage(Text.empty()
                .append(Text.literal("Claimed by: ").formatted(Formatting.AQUA))
                .append(Text.literal(claim.getTeamName()).styled(s -> s.withColor(owner != null ? TeamColors.fromTeam(owner) : net.minecraft.text.TextColor.fromFormatting(Formatting.WHITE))))
                .append(Text.literal(" (").formatted(Formatting.AQUA))
                .append(Text.literal(colorStr).styled(s -> s.withColor(owner != null ? TeamColors.fromTeam(owner) : net.minecraft.text.TextColor.fromFormatting(Formatting.WHITE))))
                .append(Text.literal(" | " + (owner != null ? owner.getType().name() : "UNKNOWN") + ")").formatted(Formatting.AQUA))
            );
        }
        return 1;
    }

    // --- HELP COMMAND ---

    private static int showHelp(ServerCommandSource source) {
        source.sendMessage(Text.literal("=== 🛡 SMP HELP ===").formatted(Formatting.GOLD, Formatting.BOLD));
        source.sendMessage(Text.empty());

        // PvP Status
        if (PvPCountdownManager.isPvPEnabled()) {
            source.sendMessage(Text.literal("⚔ PvP: ENABLED").formatted(Formatting.RED, Formatting.BOLD));
        } else if (PvPCountdownManager.isCountdownActive()) {
            String timeStr = PvPCountdownManager.formatDuration(PvPCountdownManager.getTimeRemainingMs());
            source.sendMessage(Text.literal("🛡 PvP: DISABLED (Enabling in: " + timeStr + ")").formatted(Formatting.YELLOW));
        } else {
            source.sendMessage(Text.literal("🛡 PvP: DISABLED (Countdown not started)").formatted(Formatting.GREEN));
        }

        // End Status
        source.sendMessage(Text.literal("🌌 The End: " + (EndLockManager.isEndLocked() ? "LOCKED" : "UNLOCKED")).formatted(EndLockManager.isEndLocked() ? Formatting.RED : Formatting.GREEN));
        source.sendMessage(Text.empty());

        // Rules Summary
        source.sendMessage(Text.literal("--- Rules ---").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("• 2-minute combat tag on PvP hit. Combat logging = death.").formatted(Formatting.GRAY));
        source.sendMessage(Text.literal("• No Netherite armor/weapons (pickaxes/hoes OK), No Mace, No Crystals.").formatted(Formatting.GRAY));
        source.sendMessage(Text.literal("• Team friendly fire: Melee blocked, Explosions allowed.").formatted(Formatting.GRAY));
        source.sendMessage(Text.literal("• Peaceful team bases cannot be raided.").formatted(Formatting.GRAY));
        source.sendMessage(Text.literal("• Villages are public territory and cannot be claimed.").formatted(Formatting.GRAY));
        source.sendMessage(Text.empty());

        // Commands
        source.sendMessage(Text.literal("--- Player Commands ---").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("/smpcoords").formatted(Formatting.YELLOW).append(Text.literal(" - Toggle your HUD coordinates on/off").formatted(Formatting.GRAY)));
        source.sendMessage(Text.literal("/smprules").formatted(Formatting.YELLOW).append(Text.literal(" - View full server rules").formatted(Formatting.GRAY)));
        source.sendMessage(Text.literal("/smpteam list").formatted(Formatting.YELLOW).append(Text.literal(" - See all teams").formatted(Formatting.GRAY)));
        source.sendMessage(Text.literal("/smpteam info [name]").formatted(Formatting.YELLOW).append(Text.literal(" - Team details").formatted(Formatting.GRAY)));
        source.sendMessage(Text.literal("/smpclaim claim").formatted(Formatting.YELLOW).append(Text.literal(" - Claim the chunk you're in").formatted(Formatting.GRAY)));
        source.sendMessage(Text.literal("/smpclaim unclaim").formatted(Formatting.YELLOW).append(Text.literal(" - Unclaim the chunk you're in").formatted(Formatting.GRAY)));
        source.sendMessage(Text.literal("/smpclaim highlight").formatted(Formatting.YELLOW).append(Text.literal(" - Show chunk borders").formatted(Formatting.GRAY)));
        source.sendMessage(Text.literal("/smpclaim view").formatted(Formatting.YELLOW).append(Text.literal(" - List your team's claims").formatted(Formatting.GRAY)));
        source.sendMessage(Text.literal("/smpclaim info").formatted(Formatting.YELLOW).append(Text.literal(" - Who owns this chunk?").formatted(Formatting.GRAY)));
        source.sendMessage(Text.literal("/smphelp").formatted(Formatting.YELLOW).append(Text.literal(" - This help menu").formatted(Formatting.GRAY)));
        return 1;
    }

    // --- ADMIN: TP TO SPAWN ---

    private static int tpToSpawn(ServerCommandSource source, ServerPlayerEntity target) {
        ServerWorld overworld = source.getServer().getOverworld();
        BlockPos spawnPos = overworld.getSpawnPos();

        ServerPlayerEntity playerToTp = target;
        if (playerToTp == null && source.getEntity() instanceof ServerPlayerEntity self) {
            playerToTp = self;
        }
        if (playerToTp == null) {
            source.sendError(Text.literal("No player specified."));
            return 0;
        }

        playerToTp.teleport(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, playerToTp.getYaw(), playerToTp.getPitch());
        source.sendMessage(Text.literal("✨ Teleported " + playerToTp.getName().getString() + " to spawn ("
            + spawnPos.getX() + ", " + spawnPos.getY() + ", " + spawnPos.getZ() + ").").formatted(Formatting.GREEN));
        return 1;
    }

    // --- ADMIN: RESET ALL INVENTORIES ---

    private static int resetAllInventories(ServerCommandSource source) {
        int count = 0;
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            player.getInventory().clear();
            player.sendMessage(Text.literal("⚠ [Server Reset] Your inventory has been cleared by an admin.").formatted(Formatting.RED, Formatting.BOLD), false);
            count++;
        }
        source.getServer().getPlayerManager().broadcast(
            Text.literal("🚨 [Server Reset] All player inventories have been wiped by " + source.getName() + "!")
                .formatted(Formatting.RED, Formatting.BOLD),
            false
        );
        source.sendMessage(Text.literal("Cleared inventories for " + count + " online players.").formatted(Formatting.GREEN));
        return 1;
    }

    // --- ADMIN: TAX SYSTEM ---

    private static int viewTaxDashboard(ServerCommandSource source) {
        source.sendMessage(Text.literal("=== 🏛 TAX DASHBOARD ===").formatted(Formatting.GOLD, Formatting.BOLD));
        source.sendMessage(Text.empty());

        int count = 0;
        long currentTime = System.currentTimeMillis();

        for (Team team : TeamManager.getAllTeams()) {
            if (team.getType() == Team.TeamType.PEACEFUL) {
                count++;
                long dueTime = team.getNextTaxPaymentDueMs();
                if (dueTime > currentTime) {
                    source.sendMessage(Text.empty()
                        .append(Text.literal("🟢 [PAID] Team: ").formatted(Formatting.GREEN))
                        .append(Text.literal(team.getName()).styled(s -> s.withColor(TeamColors.fromTeam(team))))
                        .append(Text.literal(" (Due in: " + TaxManager.formatDuration(dueTime - currentTime) + ")").formatted(Formatting.GREEN))
                    );
                } else {
                    source.sendMessage(Text.empty()
                        .append(Text.literal("🔴 [OVERDUE] Team: ").formatted(Formatting.RED, Formatting.BOLD))
                        .append(Text.literal(team.getName()).styled(s -> s.withColor(TeamColors.fromTeam(team)).withBold(true)))
                    );
                }
            }
        }
        
        if (count == 0) {
            source.sendMessage(Text.literal("No peaceful teams currently exist.").formatted(Formatting.GRAY));
        }
        return 1;
    }

    private static int markTeamPaid(ServerCommandSource source, String teamName, boolean paid) {
        Team team = TeamManager.getTeam(teamName);
        if (team == null) {
            source.sendError(Text.literal("Team '" + teamName + "' not found."));
            return 0;
        }
        if (team.getType() != Team.TeamType.PEACEFUL) {
            source.sendError(Text.literal("Team '" + teamName + "' is a PvP team and does not pay taxes."));
            return 0;
        }
        
        if (paid) {
            TaxManager.markPaid(team);
            source.sendMessage(Text.literal("✅ Marked peaceful team '" + team.getName() + "' as PAID. Their timer has been reset to 3 days.").formatted(Formatting.GREEN));
        } else {
            TaxManager.markUnpaid(team);
            source.sendMessage(Text.literal("🛑 Marked peaceful team '" + team.getName() + "' as UNPAID. They are now overdue.").formatted(Formatting.RED));
        }
        return 1;
    }
}
