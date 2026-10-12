package com.smprules;

import com.smprules.admin.AntiCheatManager;
import com.smprules.admin.DashboardStatsManager;
import com.smprules.admin.EndLockManager;
import com.smprules.admin.PvPCountdownManager;
import com.smprules.admin.StaffModeManager;
import com.smprules.admin.TaxManager;
import com.smprules.claim.ClaimManager;
import com.smprules.claim.ChunkVisualizer;
import com.smprules.claim.SpawnProtectionManager;
import com.smprules.combat.CombatTracker;
import com.smprules.combat.PlayerHeadDropHandler;
import com.smprules.combat.TrophyHeadTracker;
import com.smprules.command.SMPCommands;
import com.smprules.config.ModConfig;
import com.smprules.hud.HUDManager;
import com.smprules.restriction.ItemRestrictionHandler;
import com.smprules.team.Team;
import com.smprules.team.TeamManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.damage.DamageTypes;

public class SMPRulesMod implements ModInitializer {
    public static final String MOD_ID = "smprules";
    private static int tickCounter = 0;

    @Override
    public void onInitialize() {
        System.out.println("[SMPRulesMod] Initializing custom SMP Server Rules & Land Claim mod...");

        // Load configuration
        ModConfig.load();

        // Server lifecycle listeners
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            TeamManager.init(server);
            ClaimManager.init(server);
            SpawnProtectionManager.init(server);
            AntiCheatManager.init(server);
            DashboardStatsManager.init(server);
            EndLockManager.init(server);
            PvPCountdownManager.init(server);
            TrophyHeadTracker.init(server);
            TaxManager.init(server);
            com.smprules.admin.PlayerMemory.init(server);
            com.smprules.admin.BountyManager.init(server);
            System.out.println("[SMPRulesMod] All databases loaded successfully.");

            // Enforce SMP gamerules
            net.minecraft.world.GameRules gameRules = server.getGameRules();
            gameRules.get(net.minecraft.world.GameRules.DO_INSOMNIA).set(false, server);         // No Phantoms
            gameRules.get(net.minecraft.world.GameRules.PLAYERS_SLEEPING_PERCENTAGE).set(50, server); // Half the server needs to sleep
            System.out.println("[SMPRulesMod] Gamerules enforced: Phantoms OFF, Sleep percentage 50%.");
        });

        // Decorate chat messages to prepend [TeamName]
        net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent.EVENT.register((sender, message) -> {
            if (sender != null) {
                Team team = TeamManager.getPlayerTeam(sender.getUuid());
                if (team != null) {
                    return Text.empty()
                        .append(Text.literal("[" + team.getName() + "] ").styled(s -> s.withColor(com.smprules.team.TeamColors.fromTeam(team)).withBold(true)))
                        .append(Text.literal(sender.getName().getString()).styled(s -> s.withColor(com.smprules.team.TeamColors.fromTeam(team))))
                        .append(Text.literal(": "))
                        .append(message);
                }
            }
            return message;
        });

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SMPCommands.register(dispatcher);
        });

        // Item Use Event Handler (Blocks Firework Rockets during PvP Combat Tag)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                ItemStack stack = player.getStackInHand(hand);
                if (stack.isOf(Items.FIREWORK_ROCKET) && CombatTracker.isCombatTagged(serverPlayer.getUuid())) {
                    serverPlayer.sendMessage(Text.literal("⛔ [Combat Tag] You cannot use Firework Rockets during PvP combat!").formatted(Formatting.RED), true);
                    return TypedActionResult.fail(stack);
                }
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });

        // Block Break Event Handler (Spawn Protection + Land Claim protection)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                if (StaffModeManager.isStaffModeActive(serverPlayer.getUuid())) {
                    return true;
                }

                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;

                // 1. Spawn Protection Check
                if (!SpawnProtectionManager.canBuildInSpawn(serverPlayer, serverPlayer.getServerWorld(), chunkX, chunkZ)) {
                    serverPlayer.sendMessage(Text.literal("⛔ [Spawn Protection] Only server admins or permitted spawn builders can break blocks in spawn!").formatted(Formatting.RED), true);
                    syncHeldItem(serverPlayer, net.minecraft.util.Hand.MAIN_HAND);
                    return false;
                }

                // 2. Claim Protection Check
                String dimension = world.getRegistryKey().getValue().toString();
                if (!ClaimManager.canBuild(serverPlayer, dimension, chunkX, chunkZ)) {
                    serverPlayer.sendMessage(Text.literal("⛔ [Land Claim] You do not have permission to break blocks here!").formatted(Formatting.RED), true);
                    syncHeldItem(serverPlayer, net.minecraft.util.Hand.MAIN_HAND);
                    return false;
                }
            }
            return true;
        });

        // Block Attack Event Handler
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                if (StaffModeManager.isStaffModeActive(serverPlayer.getUuid())) {
                    return ActionResult.PASS;
                }

                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;

                if (!SpawnProtectionManager.canBuildInSpawn(serverPlayer, serverPlayer.getServerWorld(), chunkX, chunkZ)) {
                    return ActionResult.FAIL;
                }

                String dimension = world.getRegistryKey().getValue().toString();
                if (!ClaimManager.canBuild(serverPlayer, dimension, chunkX, chunkZ)) {
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        // Block Use Event Handler
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                if (StaffModeManager.isStaffModeActive(serverPlayer.getUuid())) {
                    return ActionResult.PASS;
                }

                ItemStack stack = player.getStackInHand(hand);

                // Check item restrictions
                if (!stack.isEmpty() && ItemRestrictionHandler.isBannedItem(stack.getItem())) {
                    serverPlayer.sendMessage(Text.literal("⛔ [SMP Rules] Restricted item! You cannot use this item.").formatted(Formatting.RED), false);
                    syncHeldItem(serverPlayer, hand);
                    return ActionResult.FAIL;
                }

                BlockPos pos = hitResult.getBlockPos();
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;

                if (!SpawnProtectionManager.canBuildInSpawn(serverPlayer, serverPlayer.getServerWorld(), chunkX, chunkZ)) {
                    serverPlayer.sendMessage(Text.literal("⛔ [Spawn Protection] Only server admins or permitted spawn builders can build/interact in spawn!").formatted(Formatting.RED), true);
                    syncHeldItem(serverPlayer, hand);
                    return ActionResult.FAIL;
                }

                String dimension = world.getRegistryKey().getValue().toString();
                if (!ClaimManager.canBuild(serverPlayer, dimension, chunkX, chunkZ)) {
                    serverPlayer.sendMessage(Text.literal("⛔ [Land Claim] You do not have permission to interact/build here!").formatted(Formatting.RED), true);
                    syncHeldItem(serverPlayer, hand);
                    return ActionResult.FAIL;
                }

                // Track trophy head placement for lore preservation
                if (stack.isOf(Items.PLAYER_HEAD)) {
                    TrophyHeadTracker.TrophyData data = TrophyHeadTracker.extractFromItem(stack);
                    if (data != null) {
                        BlockPos placedPos = pos.offset(hitResult.getSide());
                        TrophyHeadTracker.onHeadPlaced(dimension, placedPos, data);
                    }
                }
            }
            return ActionResult.PASS;
        });

        // Block Break Event — restore trophy head lore when a skull block is broken
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClient && state.isOf(Blocks.PLAYER_HEAD) || state.isOf(Blocks.PLAYER_WALL_HEAD)) {
                String dimension = world.getRegistryKey().getValue().toString();
                TrophyHeadTracker.TrophyData data = TrophyHeadTracker.onHeadBroken(dimension, pos);
                if (data != null && player instanceof ServerPlayerEntity serverPlayer) {
                    // Find the dropped player head item near the break position and apply lore
                    world.getServer().execute(() -> {
                        var items = world.getEntitiesByClass(
                            net.minecraft.entity.ItemEntity.class,
                            new net.minecraft.util.math.Box(pos).expand(2.0),
                            item -> item.getStack().isOf(Items.PLAYER_HEAD)
                        );
                        for (var itemEntity : items) {
                            TrophyHeadTracker.applyToItem(itemEntity.getStack(), data);
                        }
                    });
                }
            }
        });

        // Entity Damage & Death Event Handler
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity victim) {
                ServerPlayerEntity attacker = (source.getAttacker() instanceof ServerPlayerEntity p) ? p : null;

                if (attacker != null) {
                    if (StaffModeManager.isStaffModeActive(attacker.getUuid()) || StaffModeManager.isStaffModeActive(victim.getUuid())) {
                        return false;
                    }

                    // Global PvP countdown check — block ALL player-vs-player damage until countdown finishes
                    if (!PvPCountdownManager.isPvPEnabled()) {
                        if (PvPCountdownManager.isCountdownActive()) {
                            String timeStr = PvPCountdownManager.formatDuration(PvPCountdownManager.getTimeRemainingMs());
                            attacker.sendMessage(Text.literal("⛔ PvP is disabled! Enabling in: " + timeStr).formatted(Formatting.RED), true);
                        } else {
                            attacker.sendMessage(Text.literal("⛔ PvP is currently disabled on this server.").formatted(Formatting.RED), true);
                        }
                        return false;
                    }

                    Team attackerTeam = TeamManager.getPlayerTeam(attacker.getUuid());
                    Team victimTeam = TeamManager.getPlayerTeam(victim.getUuid());

                    if (attackerTeam != null && victimTeam != null && attackerTeam.getName().equalsIgnoreCase(victimTeam.getName())) {
                        // Allow explosion damage between teammates (TNT, creepers, etc.)
                        if (!source.isOf(DamageTypes.EXPLOSION) && !source.isOf(DamageTypes.PLAYER_EXPLOSION)) {
                            attacker.sendMessage(Text.literal("⚠ Friendly fire is disabled for team members.").formatted(Formatting.YELLOW), true);
                            return false;
                        }
                    }

                    if (!ClaimManager.canDamagePlayerInClaim(attacker, victim)) {
                        attacker.sendMessage(Text.literal("⛔ PvP is disabled inside Peaceful Land Claims!").formatted(Formatting.RED), true);
                        return false;
                    }

                    CombatTracker.tagPlayer(attacker);
                    CombatTracker.tagPlayer(victim);
                }

            }
            return true;
        });

        // Player Death Event Handler (fires AFTER confirmed death for accurate stats & trophy heads)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity victim) {
                DashboardStatsManager.onPlayerDeath(victim, damageSource);
                CombatTracker.removeTag(victim.getUuid()); // Clear combat tag on death
                com.smprules.admin.BountyManager.removeBounty(victim.getServer(), victim.getUuid()); // Clear bounty on death

                if (damageSource.getAttacker() instanceof ServerPlayerEntity attacker) {
                    PlayerHeadDropHandler.dropPlayerHead(victim, attacker);
                }
            }
        });

        // Player Connection Listeners
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            Team team = TeamManager.getPlayerTeam(player.getUuid());
            ClaimManager.onPlayerLoginCheckTeam(team);

            // Record session join time
            DashboardStatsManager.onPlayerJoin(player);
            com.smprules.admin.PlayerMemory.onPlayerJoin(player);

            // Show team claim count on join
            if (team != null) {
                int claimed = ClaimManager.getClaimCount(team.getName());
                int max = ClaimManager.getMaxClaimsForTeam(team);
                player.sendMessage(Text.literal("🟦 Your team [" + team.getName() + "] has claimed " + claimed + "/" + max + " chunks.").formatted(Formatting.AQUA), false);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            
            // Record session duration
            DashboardStatsManager.onPlayerDisconnect(player);

            // Restore staff mode profile on disconnect
            StaffModeManager.onPlayerDisconnect(player);

            // Check combat logging
            CombatTracker.onPlayerDisconnect(player);

            // Check PvP claim offline timer
            Team team = TeamManager.getPlayerTeam(player.getUuid());
            ClaimManager.onPlayerLogoutCheckTeam(server, team);

            // Clean up HUD manager
            HUDManager.onPlayerDisconnect(player.getUuid());
            ChunkVisualizer.onPlayerDisconnect(player.getUuid());
        });

        // Server Tick Listener (Item enforcement, AntiCheat, End Lock, Elytra Combat, PvP Countdown, HUD)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            boolean isSecondTick = (tickCounter % 20 == 0);

            // PvP countdown, Tax check & Bounty tick (once per second)
            if (isSecondTick) {
                PvPCountdownManager.tickCheck(server);
                TaxManager.tickCheck(server);
                com.smprules.admin.BountyManager.tick(server);
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!StaffModeManager.isStaffModeActive(player.getUuid())) {
                    ItemRestrictionHandler.checkPlayerInventory(player);
                    EndLockManager.checkPlayerEndAccess(player);
                    CombatTracker.checkElytraCombatRestriction(player);
                }
                if (isSecondTick) {
                    HUDManager.tickPlayer(player);
                }
                // Chunk highlight repeating particles
                ChunkVisualizer.tick(player);
            }
        });
    }

    /**
     * Forces the client to restore the item in the player's hand.
     * Required because UseBlockCallback returning FAIL doesn't trigger
     * sendContentUpdates() since the server never actually changed anything —
     * the client's prediction already removed the item visually.
     */
    private static void syncHeldItem(ServerPlayerEntity player, net.minecraft.util.Hand hand) {
        int rawSlot;
        if (hand == net.minecraft.util.Hand.MAIN_HAND) {
            // Hotbar slots in the player screen handler start at index 36
            rawSlot = 36 + player.getInventory().selectedSlot;
        } else {
            rawSlot = 45; // Offhand slot index in PlayerScreenHandler
        }
        ItemStack heldStack = player.getStackInHand(hand);
        player.networkHandler.sendPacket(
            new ScreenHandlerSlotUpdateS2CPacket(
                player.playerScreenHandler.syncId,
                player.playerScreenHandler.nextRevision(),
                rawSlot,
                heldStack
            )
        );
    }
}
