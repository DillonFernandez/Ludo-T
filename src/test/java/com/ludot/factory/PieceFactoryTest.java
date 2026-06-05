package com.ludot.factory;

import com.ludot.model.Colour;
import com.ludot.model.Piece;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// These tests confirm the factory creates four pieces for each colour and starts them in base.
class PieceFactoryTest {

    @Test
    void createPiecesGiveCorrectNamesAndStartInBaseForAllColours() {
        PieceFactory factory = new PieceFactory();

        for (Colour colour : Colour.values()) {
            List<Piece> pieces = factory.createPieces(colour);
            String prefix = colour.name().substring(0, 1);

            assertEquals(4, pieces.size());
            assertTrue(pieces.stream().allMatch(Piece::isInBase));
            assertEquals(prefix + "1", pieces.get(0).getName());
            assertEquals(prefix + "2", pieces.get(1).getName());
            assertEquals(prefix + "3", pieces.get(2).getName());
            assertEquals(prefix + "4", pieces.get(3).getName());
        }
    }
}
