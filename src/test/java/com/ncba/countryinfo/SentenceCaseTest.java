package com.ncba.countryinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ncba.countryinfo.util.SentenceCase;
import org.junit.jupiter.api.Test;

class SentenceCaseTest {
    @Test void lowercase() { assertEquals("Kenya", SentenceCase.of("kenya")); }
    @Test void mixed() { assertEquals("Tanzania", SentenceCase.of("  tANZANIA ")); }
    @Test void multiWord() { assertEquals("South africa", SentenceCase.of("SOUTH   AFRICA")); }
}
