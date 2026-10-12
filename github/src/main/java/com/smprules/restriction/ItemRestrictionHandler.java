package com.smprules.restriction;

import com.smprules.config.ModConfig;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ItemRestrictionHandler {

    public static boolean isBannedItem(Item item) {
        if (item == null) return false;
        ModConfig config = ModConfig.get();

        if (config.banMace && item == Items.MACE) {
            return true;
        }

        if (config.banEndCrystal && item == Items.END_CRYSTAL) {
            return true;
        }

        if (config.banRespawnAnchor && item == Items.RESPAWN_ANCHOR) {
            return true;
        }

        if (config.banNetheriteWeaponsAndArmor) {
            // Explicitly allow Netherite Pickaxe and Hoe
            if (item == Items.NETHERITE_PICKAXE || item == Items.NETHERITE_HOE) {
                return false;
            }

            // Ban all other Netherite equipment (Weapons, Tools, Armor)
            if (item == Items.NETHERITE_SWORD ||
                item == Items.NETHERITE_AXE ||
                item == Items.NETHERITE_SHOVEL ||
                item == Items.NETHERITE_HELMET ||
                item == Items.NETHERITE_CHESTPLATE ||
                item == Items.NETHERITE_LEGGINGS ||
                item == Items.NETHERITE_BOOTS) {
                return true;
            }
        }

        return false;
    }

    public static void checkPlayerInventory(ServerPlayerEntity player) {
        if (player == null || player.isSpectator() || player.isCreative()) return;

        // Check armor slots
        for (int slot = 0; slot < player.getInventory().armor.size(); slot++) {
            ItemStack stack = player.getInventory().armor.get(slot);
            if (!stack.isEmpty() && isBannedItem(stack.getItem())) {
                notifyAndDropItem(player, stack);
                player.getInventory().armor.set(slot, ItemStack.EMPTY);
            }
        }

        // Check main hand
        ItemStack mainHand = player.getMainHandStack();
        if (!mainHand.isEmpty() && isBannedItem(mainHand.getItem())) {
            notifyAndDropItem(player, mainHand);
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, ItemStack.EMPTY);
        }

        // Check offhand
        ItemStack offHand = player.getOffHandStack();
        if (!offHand.isEmpty() && isBannedItem(offHand.getItem())) {
            notifyAndDropItem(player, offHand);
            player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private static void notifyAndDropItem(ServerPlayerEntity player, ItemStack stack) {
        player.dropItem(stack.copy(), true, false);
        player.sendMessage(
            Text.literal("⛔ [SMP Rules] Restricted item detected! (" + stack.getName().getString() + " is banned on this server)")
                .formatted(Formatting.RED),
            false
        );
    }
}
