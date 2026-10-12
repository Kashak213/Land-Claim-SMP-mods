package com.smprules.claim;

import java.util.Objects;

public class ClaimChunk {
    private String dimension;
    private int x;
    private int z;
    private String teamName;

    public ClaimChunk(String dimension, int x, int z, String teamName) {
        this.dimension = dimension;
        this.x = x;
        this.z = z;
        this.teamName = teamName;
    }

    public String getDimension() {
        return dimension;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getKey() {
        return dimension + ":" + x + ":" + z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClaimChunk that = (ClaimChunk) o;
        return x == that.x && z == that.z && Objects.equals(dimension, that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, x, z);
    }
}
