package com.smprules.admin;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.*;

public class StaffModeManager {

    public static class PlayerDataSnapshot {
        public List<ItemStack> mainInventory = new ArrayList<>();
        public List<ItemStack> armorInventory = new ArrayList<>();
        public ItemStack offHandStack = ItemStack.EMPTY;
        public GameMode gameMode = GameMode.SURVIVAL;
        public int xpLevel = 0;
        public float xpProgress = 0.0f;
        public float health = 20.0f;
        public int hunger = 20;
        public String dimension = "minecraft:overworld";
        public double x, y, z;
        public float yaw, pitch;

        public PlayerDataSnapshot(ServerPlayerEntity player) {
            if (player == null) return;
            for (int i = 0; i < player.getInventory().main.size(); i++) {
                mainInventory.add(player.getInventory().main.get(i).copy());
            }
            for (int i = 0; i < player.getInventory().armor.size(); i++) {
                armorInventory.add(player.getInventory().armor.get(i).copy());
            }
            offHandStack = player.getOffHandStack().copy();

            gameMode = player.interactionManager.getGameMode();
            xpLevel = player.experienceLevel;
            xpProgress = player.experienceProgress;
            health = player.getHealth();
            hunger = player.getHungerManager().getFoodLevel();

            dimension = player.getWorld().getRegistryKey().getValue().toString();
            x = player.getX();
            y = player.getY();
            z = player.getZ();
            yaw = player.getYaw();
            pitch = player.getPitch();
        }

        public void restoreToPlayer(ServerPlayerEntity player) {
            if (player == null) return;
            player.getInventory().clear();

            for (int i = 0; i < mainInventory.size() && i < player.getInventory().main.size(); i++) {
                player.getInventory().main.set(i, mainInventory.get(i).copy());
            }
            for (int i = 0; i < armorInventory.size() && i < player.getInventory().armor.size(); i++) {
                player.getInventory().armor.set(i, armorInventory.get(i).copy());
            }
            player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, offHandStack.copy());

            // We no longer rely on the stored gameMode to prevent getting stuck in Creative.
            // Gamemodes are explicitly forced during the toggle switch.

            player.setExperienceLevel(xpLevel);
            player.setExperiencePoints((int) (xpProgress * player.getNextLevelExperience()));

            player.setHealth(health);
            player.getHungerManager().setFoodLevel(hunger);

            ServerWorld targetWorld = null;
            if (player.getServer() != null) {
                for (ServerWorld w : player.getServer().getWorlds()) {
                    if (w.getRegistryKey().getValue().toString().equalsIgnoreCase(dimension)) {
                        targetWorld = w;
                        break;
                    }
                }
            }
            if (targetWorld != null) {
                player.teleport(targetWorld, x, y, z, Set.of(), yaw, pitch);
            }
        }
    }

    private static final Map<UUID, PlayerDataSnapshot> survivalProfiles = new HashMap<>();
    private static final Map<UUID, PlayerDataSnapshot> staffProfiles = new HashMap<>();
    private static final Set<UUID> activeStaffMode = new HashSet<>();

    public static boolean isStaffModeActive(UUID uuid) {
        return uuid != null && activeStaffMode.contains(uuid);
    }

    public static void toggleStaffMode(ServerPlayerEntity player) {
        if (player == null) return;
        UUID uuid = player.getUuid();

        if (activeStaffMode.contains(uuid)) {
            // Switch back to SURVIVAL
            staffProfiles.put(uuid, new PlayerDataSnapshot(player));

            PlayerDataSnapshot survivalData = survivalProfiles.remove(uuid);
            if (survivalData != null) {
                survivalData.restoreToPlayer(player);
            } else {
                player.getInventory().clear();
            }
            
            // Explicitly force them back to survival regardless of what was saved
            player.changeGameMode(GameMode.SURVIVAL);

            activeStaffMode.remove(uuid);

            player.sendMessage(
                Text.literal("⚔ [Staff Mode] Switched back to SURVIVAL MODE. Your survival inventory and position have been safely restored!")
                    .formatted(Formatting.GREEN, Formatting.BOLD),
                false
            );

        } else {
            // Switch to STAFF MODE
            survivalProfiles.put(uuid, new PlayerDataSnapshot(player));

            PlayerDataSnapshot staffData = staffProfiles.get(uuid);
            if (staffData != null) {
                staffData.restoreToPlayer(player);
            } else {
                player.getInventory().clear();
            }
            
            // Explicitly force them into creative mode
            player.changeGameMode(GameMode.CREATIVE);

            activeStaffMode.add(uuid);

            player.sendMessage(
                Text.literal("👑 [Staff Mode] Switched to ADMIN / MOD MODE! Creative mode & flight enabled. Your survival profile is safely saved.")
                    .formatted(Formatting.GOLD, Formatting.BOLD),
                false
            );
        }
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        if (player == null) return;
        UUID uuid = player.getUuid();
        if (activeStaffMode.contains(uuid)) {
            PlayerDataSnapshot survivalData = survivalProfiles.remove(uuid);
            if (survivalData != null) {
                survivalData.restoreToPlayer(player);
            }
            activeStaffMode.remove(uuid);
        }
    }
}
