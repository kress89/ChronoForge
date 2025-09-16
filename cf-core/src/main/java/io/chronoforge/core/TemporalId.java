package io.chronoforge.core;

import java.util.UUID;

public record TemporalId(UUID value) {
    public static TemporalId newId(){ return new TemporalId(UUID.randomUUID()); }
    @Override public String toString(){ return value.toString(); }
}
