package io.github.pokemeetup.system.gameplay.overworld;

public class ChunkPosition {
    public final int x;
    public final int y;

    public ChunkPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // Override equals method
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChunkPosition that = (ChunkPosition) o;

        return x == that.x && y == that.y;
    }

    // Override hashCode method
    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        return result;
    }
}
