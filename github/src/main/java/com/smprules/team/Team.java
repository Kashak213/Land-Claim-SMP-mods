package com.smprules.team;

import com.smprules.config.ModConfig;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Team {
    public enum TeamType {
        PEACEFUL,
        PVP
    }

    private String name;
    private TeamType type = TeamType.PEACEFUL;
    private UUID leader;
    private Set<UUID> members = new HashSet<>();
    private Set<UUID> invites = new HashSet<>();
    private long nextTaxPaymentDueMs = System.currentTimeMillis() + (3L * 24L * 60L * 60L * 1000L);

    // Team Color representation
    private String colorName = "BLUE";
    private String colorHex = "#3388FF";

    public Team(String name, TeamType type, UUID leader, String colorName, String colorHex) {
        this.name = name;
        this.type = type;
        this.leader = leader;
        this.colorName = colorName != null ? colorName : "BLUE";
        this.colorHex = colorHex != null ? colorHex : "#3388FF";
        if (members == null) members = new HashSet<>();
        if (invites == null) invites = new HashSet<>();
        if (leader != null) members.add(leader);
    }

    public String getName() {
        return name;
    }

    public TeamType getType() {
        return type != null ? type : TeamType.PEACEFUL;
    }

    public void setType(TeamType type) {
        this.type = type;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Set<UUID> getMembers() {
        if (members == null) members = new HashSet<>();
        return members;
    }

    public Set<UUID> getInvites() {
        if (invites == null) invites = new HashSet<>();
        return invites;
    }

    public long getNextTaxPaymentDueMs() {
        return nextTaxPaymentDueMs;
    }

    public void setNextTaxPaymentDueMs(long nextTaxPaymentDueMs) {
        this.nextTaxPaymentDueMs = nextTaxPaymentDueMs;
    }

    public String getColorName() {
        return colorName != null ? colorName : "BLUE";
    }

    public void setColorName(String colorName) {
        this.colorName = colorName != null ? colorName : "BLUE";
    }

    public String getColorHex() {
        return colorHex != null ? colorHex : "#3388FF";
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex != null ? colorHex : "#3388FF";
    }

    public int getMaxMembers() {
        return getType() == TeamType.PEACEFUL 
                ? ModConfig.get().peacefulMaxTeamMembers 
                : ModConfig.get().pvpMaxTeamMembers;
    }

    public boolean isFull() {
        return getMembers().size() >= getMaxMembers();
    }

    public boolean addMember(UUID uuid) {
        if (uuid == null || isFull()) return false;
        getMembers().add(uuid);
        getInvites().remove(uuid);
        return true;
    }

    public void removeMember(UUID uuid) {
        if (uuid == null) return;
        getMembers().remove(uuid);
        getInvites().remove(uuid);
    }
}
