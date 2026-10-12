package com.smprules.admin;

import com.smprules.team.Team;
import com.smprules.team.TeamManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages individual 3-day tax countdowns for Peaceful teams.
 */
public class TaxManager {
    public static final long TAX_CYCLE_DURATION_MS = 3L * 24L * 60L * 60L * 1000L; // 3 days

    // Track which teams have already been warned this cycle so we don't spam them every second
    private static final Set<String> overdueWarnedTeams = new HashSet<>();

    public static void init(MinecraftServer server) {
        // Individual team tax data is saved within TeamManager's JSON.
    }

    /**
     * Called once per second from the server tick loop.
     */
    public static void tickCheck(MinecraftServer server) {
        long currentTime = System.currentTimeMillis();

        for (Team team : TeamManager.getAllTeams()) {
            if (team.getType() == Team.TeamType.PEACEFUL) {
                if (currentTime > team.getNextTaxPaymentDueMs()) {
                    // They are overdue!
                    if (!overdueWarnedTeams.contains(team.getName())) {
                        overdueWarnedTeams.add(team.getName());
                        server.getPlayerManager().broadcast(
                            Text.literal("🏛 [Taxes] The peaceful team '" + team.getName() + "' is OVERDUE on their taxes! Admins, take action!")
                                .formatted(Formatting.RED, Formatting.BOLD),
                            false
                        );
                    }
                } else {
                    // If they are no longer overdue (e.g. they paid), remove from warned set
                    overdueWarnedTeams.remove(team.getName());
                }
            }
        }
    }

    public static void markPaid(Team team) {
        if (team == null) return;
        team.setNextTaxPaymentDueMs(System.currentTimeMillis() + TAX_CYCLE_DURATION_MS);
        overdueWarnedTeams.remove(team.getName());
        TeamManager.save();
    }

    public static void markUnpaid(Team team) {
        if (team == null) return;
        team.setNextTaxPaymentDueMs(0); // Instantly overdue
        TeamManager.save();
    }

    /**
     * Formats a duration in ms to a human-readable string like "2d 14h" or "5m 30s".
     */
    public static String formatDuration(long ms) {
        long totalSeconds = ms / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            return days + "d " + hours + "h";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }
}
