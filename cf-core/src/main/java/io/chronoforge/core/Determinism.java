package io.chronoforge.core;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Deterministic execution utilities:
 * - Fixed Clock bound to a seed (transaction time anchored to Instant.EPOCH + seed seconds)
 * - Seeded RNG (L64X256MixRandom) for reproducible randomness
 * - Causal node identity via ScopedValue
 *
 * Requires --enable-preview (ScopedValue).
 */
public final class Determinism {

    /** Fixed Clock for deterministic runs (falls back to system clock if unset). */
    public static final ScopedValue<Clock> CLOCK = ScopedValue.newInstance();
    /** Logical node/actor name participating in the causal chain. */
    public static final ScopedValue<String> NODE = ScopedValue.newInstance();
    /** Seeded RNG bound to the current deterministic scope. */
    public static final ScopedValue<RandomGenerator> RNG = ScopedValue.newInstance();

    private Determinism() {}

    /**
     * Run the supplied body under a deterministic scope:
     * - Clock is fixed to EPOCH + seed seconds (UTC)
     * - RNG is L64X256MixRandom seeded with {@code seed}
     * - NODE is set to {@code node}
     */
    public static <T> T withDeterminism(String node, long seed, Supplier<T> body) {
        var fixedClock = Clock.fixed(Instant.EPOCH.plusSeconds(seed), ZoneOffset.UTC);
        var rng = RandomGeneratorFactory.of("L64X256MixRandom").create(seed);

        try {
            return ScopedValue
                    .where(CLOCK, fixedClock)
                    .where(NODE, node == null ? "api" : node)
                    .where(RNG, rng)
                    .call(body::get);
        } catch (Exception e) {
            throw new RuntimeException("Error in deterministic scope", e);
        }
    }

    /** Current deterministic Clock (or system UTC if not in scope). */
    public static Clock clock() {
        return CLOCK.orElse(Clock.systemUTC());
    }

    /** Current logical node (or "api" if not in scope). */
    public static String node() {
        return NODE.orElse("api");
    }

    /** Current RNG (or default JDK RNG if not in scope). */
    public static RandomGenerator rng() {
        return RNG.orElse(RandomGenerator.getDefault());
    }
}
