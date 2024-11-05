package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.Vector2;
import io.github.pokemeetup.multiplayer.network.NetworkProtocol;

public abstract class WorldObjectOperation {
    public enum OperationType {
        ADD,
        REMOVE,
        UPDATE
    }

    public OperationType type;
}

