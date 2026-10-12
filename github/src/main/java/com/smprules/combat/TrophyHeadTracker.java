package com.smprules.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks trophy head metadata (killer, date, time) so lore is preserved
 * when a trophy head is placed as a block and later broken.
 */
public class TrophyHeadTracker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, TrophyData> trophyDataMap = new HashMap<>();
    private static File saveFile;

    public static class TrophyData {
        public String victimName;
        public String killerName;
        public String dateStr;
        public String timeStr;

        public TrophyData() {}

        public TrophyData(String victimName, String killerName, String dateStr, String timeStr) {
            this.victimName = victimName;
            this.killerName = killerName;
            this.dateStr = dateStr;
            this.timeStr = timeStr;
        }
    }

    public static void init(MinecraftServer server) {
        File dataDir = server.getRunDirectory().resolve("world/smprules").toFile();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        saveFile = new File(dataDir, "trophy_heads.json");
        load();
    }

    /**
     * Creates a position key for storage.
     */
    private static String posKey(String dimension, BlockPos pos) {
        return dimension + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    /**
     * Extracts trophy data from a player head ItemStack's lore, if it's a trophy head.
     * Returns null if the item is not a trophy head.
     */
    public static TrophyData extractFromItem(ItemStack stack) {
        if (stack == null || !stack.isOf(Items.PLAYER_HEAD)) return null;

        // Check if it has a custom name containing "Trophy Head"
        Text customName = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (customName == null || !customName.getString().contains("Trophy Head")) return null;

        // Extract lore lines
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null || lore.lines().isEmpty()) return null;

        String victimName = customName.getString().replace("💀 ", "").replace("'s Trophy Head", "");
        String killerName = "Unknown";
        String dateStr = "Unknown";
        String timeStr = "Unknown";

        for (Text line : lore.lines()) {
            String plain = line.getString();
            if (plain.startsWith("⚔ Slain by: ")) {
                killerName = plain.substring("⚔ Slain by: ".length());
            } else if (plain.startsWith("📅 Date: ")) {
                dateStr = plain.substring("📅 Date: ".length());
            } else if (plain.startsWith("⏰ Time: ")) {
                timeStr = plain.substring("⏰ Time: ".length());
            }
        }

        return new TrophyData(victimName, killerName, dateStr, timeStr);
    }

    /**
     * Stores trophy data when a trophy head is placed as a block.
     */
    public static void onHeadPlaced(String dimension, BlockPos pos, TrophyData data) {
        if (data == null) return;
        trophyDataMap.put(posKey(dimension, pos), data);
        save();
    }

    /**
     * Retrieves and removes stored trophy data when a trophy head block is broken.
     */
    public static TrophyData onHeadBroken(String dimension, BlockPos pos) {
        TrophyData data = trophyDataMap.remove(posKey(dimension, pos));
        if (data != null) {
            save();
        }
        return data;
    }

    /**
     * Applies stored trophy data as lore and custom name to a player head ItemStack.
     */
    public static void applyToItem(ItemStack stack, TrophyData data) {
        if (stack == null || data == null || !stack.isOf(Items.PLAYER_HEAD)) return;

        // Set custom name
        stack.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal("💀 " + data.victimName + "'s Trophy Head")
                .formatted(Formatting.GOLD, Formatting.BOLD)
        );

        // Set lore
        LoreComponent lore = new LoreComponent(List.of(
            Text.literal("⚔ Slain by: ").formatted(Formatting.RED)
                .append(Text.literal(data.killerName).formatted(Formatting.YELLOW, Formatting.BOLD)),
            Text.literal("📅 Date: ").formatted(Formatting.GRAY)
                .append(Text.literal(data.dateStr).formatted(Formatting.AQUA)),
            Text.literal("⏰ Time: ").formatted(Formatting.GRAY)
                .append(Text.literal(data.timeStr).formatted(Formatting.AQUA))
        ));
        stack.set(DataComponentTypes.LORE, lore);
    }

    private static void load() {
        if (saveFile != null && saveFile.exists()) {
            try (FileReader reader = new FileReader(saveFile)) {
                Type type = new TypeToken<Map<String, TrophyData>>() {}.getType();
                Map<String, TrophyData> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    trophyDataMap.clear();
                    trophyDataMap.putAll(loaded);
                }
            } catch (Exception e) {
                System.err.println("[SMPRules] Failed to load trophy head data: " + e.getMessage());
            }
        }
    }

    private static synchronized void save() {
        if (saveFile == null) return;
        try (FileWriter writer = new FileWriter(saveFile)) {
            GSON.toJson(trophyDataMap, writer);
        } catch (Exception e) {
            System.err.println("[SMPRules] Failed to save trophy head data: " + e.getMessage());
        }
    }
}
