package com.ludot.model;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

// These tests confirm the display names used for board locations are generated as expected.
class LocationTest {

    @Test
    void homePathDisplaysExpectedNameForEveryColourAndCell() {
        for (Colour colour : Colour.values()) {
            String prefix = colour.name().toLowerCase(Locale.ROOT);
            for (int i = 0; i < 5; i++) {
                assertEquals(prefix + "homepath" + i, Location.homePath(colour, i).getDisplayName());
            }
        }
    }

}
