package io.github.pokemeetup.multiplayer.network;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NetworkedWorldObject {
    protected String id;
    protected float x;
    protected float y;
    protected ObjectType type;
    protected boolean isDirty;
    protected Map<String, Object> additionalData;
    protected String textureName; // Serializable identifier

    public NetworkedWorldObject() {
        this.id = UUID.randomUUID().toString();
    }

    public NetworkedWorldObject(float x, float y, ObjectType type, String textureName) {
        this.id = UUID.randomUUID().toString();
        this.x = x;
        this.y = y;
        this.type = type;
        this.textureName = textureName;
        this.isDirty = true;
        this.additionalData = new HashMap<>();
    }


    public void updateFromNetwork(NetworkProtocol.WorldObjectUpdate update) {
        this.x = update.x;
        this.y = update.y;
        this.textureName = update.textureName; // Update texture identifier
        this.additionalData.clear();
        this.additionalData.putAll(update.data);
        isDirty = false;
    }

    public static NetworkedWorldObject createFromUpdate(NetworkProtocol.WorldObjectUpdate update) {
        NetworkedWorldObject obj = null;

        if (update.objectType == ObjectType.TREE) {
            obj = new NetworkedTree(update.x, update.y, update.textureName);
        } else if (update.objectType == ObjectType.POKEBALL) {
            obj = new NetworkedPokeball(update.x, update.y, update.textureName);
        }
        // Add other object types as needed

        if (obj != null) {
            obj.updateFromNetwork(update);
        }
        return obj;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public ObjectType getType() {
        return type;
    }

    public void setType(ObjectType type) {
        this.type = type;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }

    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(Map<String, Object> additionalData) {
        this.additionalData = additionalData;
    }

    public String getTextureName() {
        return textureName;
    }

    public void setTextureName(String textureName) {
        this.textureName = textureName;
    }

    public enum ObjectType {
        TREE,
        POKEBALL,
        ITEM,
        NPC
    }
}
