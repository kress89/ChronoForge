package io.chronoforge.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class VectorClock {
    private final Map<String, Long> v = new HashMap<>();
    public VectorClock tick(String node){
        v.merge(node, 1L, Long::sum); return this;
    }
    public long get(String node){
        return v.getOrDefault(node, 0L);
    }

    public int compare(VectorClock other){
        boolean less = false, more = false;
        var keys = new HashSet<>(v.keySet());
        keys.addAll(other.v.keySet());

        for(var k: keys){
            long a = get(k), b = other.get(k);
            less |= a < b; more |= a > b; if(less & more) return 0;

        }
        return more ? 1 : (less ? - 1 : 0 );
    }

    public Map<String,Long> snapshot(){ return Map.copyOf(v); }
    public String toString(){ return v.toString(); }

    @JsonValue
    public Map<String, Long> json() {
        return snapshot();
    }

    public static VectorClock from(Map<String, Long> data){
        var vc = new VectorClock();
        if (data != null) vc.v.putAll(data);
        return vc;
    }

    @JsonCreator
    static VectorClock jsonCreate(Map<String, Long> data){
        return from(data);
    }
}
