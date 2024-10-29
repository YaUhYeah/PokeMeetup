package io.github.pokemeetup.multiplayer.server.events;


import io.github.pokemeetup.multiplayer.server.events.ServerEvent;

// Base event class to simplify implementation
public abstract class BaseServerEvent implements ServerEvent {
    private final long timestamp;

    protected BaseServerEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }
}
