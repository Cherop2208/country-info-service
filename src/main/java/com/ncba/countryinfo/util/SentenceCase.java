package com.ncba.countryinfo.util;

import java.util.Locale;

public final class SentenceCase {
    private SentenceCase() {}

    /** "  kENYA " -> "Kenya". First letter upper-case, rest lower-case, whitespace normalised. */
    public static String of(String input) {
        if (input == null) return null;
        String t = input.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) return t;
        return t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1).toLowerCase(Locale.ROOT);
    }
}
