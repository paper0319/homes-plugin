package com.example.homes.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateCheckerSemverTest {

    @Test
    void parsesFirstVersionNumberFromArray() {
        String json = "[{\"version_number\":\"1.16.0\",\"name\":\"a\"},"
                + "{\"version_number\":\"1.15.0\",\"name\":\"b\"}]";
        assertEquals("1.16.0", UpdateChecker.parseLatestVersionNumber(json));
    }

    @Test
    void parseToleratesWhitespaceAndExtraFields() {
        String json = "[ { \"id\": \"x\", \"version_number\" : \"2.0.1\" } ]";
        assertEquals("2.0.1", UpdateChecker.parseLatestVersionNumber(json));
    }

    @Test
    void parseReturnsNullForEmptyArray() {
        assertNull(UpdateChecker.parseLatestVersionNumber("[]"));
    }

    @Test
    void parseReturnsNullWhenVersionNumberMissing() {
        assertNull(UpdateChecker.parseLatestVersionNumber("[{\"name\":\"no-version\"}]"));
    }

    @Test
    void parseReturnsNullForNonArrayJson() {
        assertNull(UpdateChecker.parseLatestVersionNumber("{\"version_number\":\"1.0.0\"}"));
    }

    @Test
    void parseReturnsNullForMalformedJson() {
        assertNull(UpdateChecker.parseLatestVersionNumber("not json at all"));
    }

    @Test
    void equalVersions() {
        assertEquals(0, UpdateChecker.compareSemver("1.13.1", "1.13.1"));
    }

    @Test
    void patchDifference() {
        assertTrue(UpdateChecker.compareSemver("1.13.1", "1.13.2") < 0);
        assertTrue(UpdateChecker.compareSemver("1.13.2", "1.13.1") > 0);
    }

    @Test
    void minorAndMajorDifference() {
        assertTrue(UpdateChecker.compareSemver("1.13.9", "1.14.0") < 0);
        assertTrue(UpdateChecker.compareSemver("1.99.0", "2.0.0") < 0);
    }

    @Test
    void missingTrailingSegmentsAreZero() {
        assertEquals(0, UpdateChecker.compareSemver("1.13", "1.13.0"));
        assertTrue(UpdateChecker.compareSemver("1.13", "1.13.1") < 0);
    }

    @Test
    void suffixIsStripped() {
        assertEquals(0, UpdateChecker.compareSemver("1.13.1-SNAPSHOT", "1.13.1"));
        assertTrue(UpdateChecker.compareSemver("1.13.1-RC1", "1.13.2") < 0);
    }

    @Test
    void nonNumericSegmentThrows() {
        assertThrows(NumberFormatException.class,
                () -> UpdateChecker.compareSemver("1.13.x", "1.13.1"));
    }
}
