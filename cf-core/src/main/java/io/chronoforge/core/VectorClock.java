package io.chronoforge.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** Minimal immutable-ish vector clock with useful ops. */
public final class VectorClock {
    private final Map<String, Long> v = new HashMap<>();

    /** Partial order classification for two clocks. */
    public enum Order { LESS, GREATER, EQUAL, CONCURRENT }

    public VectorClock tick(String node) {
        v.merge(Objects.requireNonNull(node), 1L, Long::sum);
        return this;
    }

    /** Convenience tick using the deterministic/current node. */
    public VectorClock tick() { return tick(Determinism.node()); }

    public long get(String node) { return v.getOrDefault(node, 0L); }

    /** Element-wise max join (a := max(a,b)). */
    public VectorClock join(VectorClock other) {
        Objects.requireNonNull(other);
        var keys = new HashSet<>(v.keySet());
        keys.addAll(other.v.keySet());
        for (var k : keys) {
            long nv = Math.max(get(k), other.get(k));
            if (nv > 0) v.put(k, nv);
        }
        return this;
    }

    /** Happens-before check (strict). */
    public boolean happensBefore(VectorClock other) { return order(other) == Order.LESS; }
    /** Happens-after check (strict). */
    public boolean happensAfter(VectorClock other) { return order(other) == Order.GREATER; }
    /** Concurrent check. */
    public boolean concurrentWith(VectorClock other) { return order(other) == Order.CONCURRENT; }

    /** Classify the partial order. */
    public Order order(VectorClock other) {
        boolean less = false, more = false;
        var keys = new HashSet<>(v.keySet());
        keys.addAll(other.v.keySet());
        for (var k : keys) {
            long a = get(k), b = other.get(k);
            less |= a < b;
            more |= a > b;
            if (less && more) return Order.CONCURRENT;
        }
        if (more) return Order.GREATER;
        if (less) return Order.LESS;
        return Order.EQUAL;
    }

    /** Back-compat: -1 less, +1 more, 0 equal or concurrent. */
    public int compare(VectorClock other) {
        return switch (order(other)) {
            case LESS -> -1;
            case GREATER -> 1;
            case EQUAL, CONCURRENT -> 0;
        };
    }

    public Map<String, Long> snapshot() { return Map.copyOf(v); }

    @Override public String toString() { return v.toString(); }

    @JsonValue public Map<String, Long> json() { return snapshot(); }

    public static VectorClock from(Map<String, Long> data) {
        var vc = new VectorClock();
        if (data != null) vc.v.putAll(data);
        return vc;
    }

    @JsonCreator static VectorClock jsonCreate(Map<String, Long> data) { return from(data); }
}
