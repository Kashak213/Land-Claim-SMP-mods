package com.smprules.combat;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PlayerHeadDropHandler {

    public static void dropPlayerHead(ServerPlayerEntity victim, ServerPlayerEntity attacker) {
        if (victim == null) return;

        ItemStack head = new ItemStack(Items.PLAYER_HEAD);

        // Set player skin profile
        head.set(DataComponentTypes.PROFILE, new ProfileComponent(victim.getGameProfile()));

        // Custom Display Name
        String victimName = victim.getName().getString();
        head.set(DataComponentTypes.CUSTOM_NAME, 
            Text.literal("💀 " + victimName + "'s Trophy Head")
                .formatted(Formatting.GOLD, Formatting.BOLD)
        );

        // Date and Time formatting
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/New_York"));
        String dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String timeStr = now.format(DateTimeFormatter.ofPattern("hh:mm:ss a")) + " ET";

        String attackerName = (attacker != null) ? attacker.getName().getString() : "PvP Tag";

        // Custom Lore with Date, Time, and Killer (Coordinates removed)
        LoreComponent lore = new LoreComponent(List.of(
            Text.literal("⚔ Slain by: ").formatted(Formatting.RED)
                .append(Text.literal(attackerName).formatted(Formatting.YELLOW, Formatting.BOLD)),
            Text.literal("📅 Date: ").formatted(Formatting.GRAY)
                .append(Text.literal(dateStr).formatted(Formatting.AQUA)),
            Text.literal("⏰ Time: ").formatted(Formatting.GRAY)
                .append(Text.literal(timeStr).formatted(Formatting.AQUA))
        ));

        head.set(DataComponentTypes.LORE, lore);

        // Drop item at victim's death position
        victim.dropItem(head, true, false);
    }
}
