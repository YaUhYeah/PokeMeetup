package io.github.pokemeetup.system.gameplay.overworld;

import com.badlogic.gdx.math.Vector2;

public class RemoveOperation extends WorldObjectOperation {
    public Vector2 chunkPos;
    public String objectId;

    public RemoveOperation(Vector2 chunkPos, String objectId) {
        this.type = OperationType.REMOVE;
        this.chunkPos = chunkPos;
        this.objectId = objectId;
    }
}
