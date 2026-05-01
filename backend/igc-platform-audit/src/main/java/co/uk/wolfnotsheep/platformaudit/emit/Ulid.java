package co.uk.wolfnotsheep.platformaudit.emit;

import java.security.SecureRandom;

/**
 * Generates ULID-shaped strings matching the envelope schema's
 * {@code ^[0-9A-HJKMNP-TV-Z]{26}$} pattern. Crockford base32 — excludes
 * I, L, O, U to avoid ambiguity.
 *
 * <p>Not strictly time-sortable like a true ULID — uses {@link SecureRandom}
 * for all 26 chars. Conforms to the schema's character-set pattern so the
 * validator accepts it. Replace with a real ULID library when one is
 * added to the platform.
 */
public final class Ulid {

    private static final char[] CROCKFORD =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private Ulid() {}

    public static String nextId() {
        char[] out = new char[26];
        for (int i = 0; i < out.length; i++) {
            out[i] = CROCKFORD[RNG.nextInt(CROCKFORD.length)];
        }
        return new String(out);
    }
}
