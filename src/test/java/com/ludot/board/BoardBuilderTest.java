package com.ludot.board;

import com.ludot.config.GameConfig;
import com.ludot.model.Colour;
import com.ludot.model.LocationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Verifies that the default board layout includes the expected path and home setup.
class BoardBuilderTest {

    @Test
    void buildCreatesExpectedBoardAndHomePathSetup() {
        Board board = new BoardBuilder().build();

        int standardPathCount = 0;
        for (int i = 0; i < GameConfig.STANDARD_PATH_SIZE; i++) {
            board.getStandardCell(i);
            standardPathCount++;
        }

        assertEquals(52, standardPathCount);

        for (Colour colour : Colour.values()) {
            assertEquals(4, board.getHomeArea(colour).countPieces());
            for (int i = 0; i < GameConfig.HOME_PATH_SIZE; i++) {
                assertEquals(LocationType.HOME_PATH, board.getHomePathCell(colour, i).getLocation().getType());
            }
        }

        assertEquals(0, board.getPath().getStartingIndex(Colour.YELLOW));
        assertEquals(13, board.getPath().getStartingIndex(Colour.BLUE));
        assertEquals(26, board.getPath().getStartingIndex(Colour.RED));
        assertEquals(39, board.getPath().getStartingIndex(Colour.GREEN));

        assertEquals(51, board.getPath().getApproachIndex(Colour.YELLOW));
        assertEquals(12, board.getPath().getApproachIndex(Colour.BLUE));
        assertEquals(25, board.getPath().getApproachIndex(Colour.RED));
        assertEquals(38, board.getPath().getApproachIndex(Colour.GREEN));

        assertEquals(4, board.getHomeArea(Colour.RED).pieces().size());
    }
}
