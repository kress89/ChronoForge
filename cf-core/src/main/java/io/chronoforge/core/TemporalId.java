
package io.chronoforge.core;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.UUID;
public record TemporalId(UUID value) {
    @JsonValue public String json() { return value.toString(); }
    @Override public String toString(){ return value.toString(); }
}
