package io.chronoforge.core;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Deterministic execution utilities.
 *
 * Scope provides:
 *  - Fixed Clock (EPOCH + seed seconds; UTC)
 *  - Seeded RNG (L64X256MixRandom) for reproducible randomness
 *  - Logical node identity
 *
 * Requires --enable-preview (ScopedValue).
 */
public final class Determinism {

    /** Fixed Clock for deterministic runs (falls back to system clock if unset). */
    public static final ScopedValue<Clock> CLOCK = ScopedValue.newInstance();
    /** Logical node/actor name participating in the causal chain. */
    public static final ScopedValue<String> NODE = ScopedValue.newInstance();
    /** Seeded RNG bound to current scope. */
    public static final ScopedValue<RandomGenerator> RNG = ScopedValue.newInstance();

    private Determinism() {}

    /* ---------- Scope management ---------- */

    /**
     * Run under a deterministic scope.
     * Clock = EPOCH + seed seconds (UTC), RNG = L64X256MixRandom(seed), NODE = node (or "api").
     */
    public static <T> T withDeterminism(String node, long seed, Supplier<T> body) {
        var fixedClock = Clock.fixed(Instant.EPOCH.plusSeconds(seed), ZoneOffset.UTC);
        var rng = RandomGeneratorFactory.of("L64X256MixRandom").create(seed);
        try {
            return ScopedValue
                    .where(CLOCK, fixedClock)
                    .where(NODE, (node == null || node.isBlank()) ? "api" : node)
                    .where(RNG, rng)
                    .call(body::get);
        } catch (Exception e) {
            throw new RuntimeException("Error in deterministic scope", e);
        }
    }

    /** Runnable overload. */
    public static void withDeterminism(String node, long seed, Runnable body) {
        withDeterminism(node, seed, () -> { body.run(); return null; });
    }

    /** Derive a 64-bit seed from arbitrary parts (stable). */
    public static long seedFrom(Object... parts) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            for (Object p : parts) md.update(Objects.toString(p, "null").getBytes());
            var bytes = md.digest();
            // take first 8 bytes as signed long
            return ByteBuffer.wrap(bytes, 0, 8).getLong();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /* ---------- Accessors ---------- */

    /** Current deterministic Clock (system UTC if not in scope). */
    public static Clock clock() { return CLOCK.orElse(Clock.systemUTC()); }

    /** Now using deterministic clock if present. */
    public static Instant now() { return clock().instant(); }

    /**
     * Effective node name.
     * Precedence: ScopedValue NODE → env(CF_NODE) → "api"
     */
    private static final String ENV_NODE =
            Optional.ofNullable(System.getenv("CF_NODE"))
                    .filter(s -> !s.isBlank())
                    .orElse(null);

    public static String node() {
        return NODE.orElse(ENV_NODE != null ? ENV_NODE : "api");
    }

    /** Current RNG (default JDK if not in scope). */
    public static RandomGenerator rng() { return RNG.orElse(RandomGenerator.getDefault()); }

    /* ---------- Deterministic helpers ---------- */

    /** Deterministic UUID from current RNG (or default RNG). */
    public static UUID randomUUID() { return randomUUID(rng()); }

    /** Deterministic UUID from a specific RNG (sets v4/version+variant bits). */
    public static UUID randomUUID(RandomGenerator r) {
        byte[] b = new byte[16];
        r.nextBytes(b);
        b[6] = (byte) ((b[6] & 0x0f) | 0x40); // version 4
        b[8] = (byte) ((b[8] & 0x3f) | 0x80); // variant 2
        var bb = ByteBuffer.wrap(b);
        long msb = bb.getLong();
        long lsb = bb.getLong();
        return new UUID(msb, lsb);
    }
}
