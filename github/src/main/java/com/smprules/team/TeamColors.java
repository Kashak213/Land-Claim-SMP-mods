package com.smprules.team;

import net.minecraft.text.TextColor;

public class TeamColors {

    /**
     * Parses the team's hex color string (e.g. "#FF6B6B") into a Minecraft TextColor.
     * Falls back to a default gray color if parsing fails.
     */
    public static TextColor fromTeam(Team team) {
        if (team == null) {
            return TextColor.fromRgb(0xAAAAAA); // Default gray fallback
        }
        
        String hex = team.getColorHex();
        if (hex == null || !hex.startsWith("#")) {
            return TextColor.fromRgb(0xAAAAAA); 
        }

        try {
            // Remove the '#' and parse as hex
            int colorInt = Integer.parseInt(hex.substring(1), 16);
            return TextColor.fromRgb(colorInt);
        } catch (NumberFormatException e) {
            return TextColor.fromRgb(0xAAAAAA);
        }
    }
}
