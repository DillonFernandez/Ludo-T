// Schedules mystery-cell spawning and relocation among unoccupied standard cells.
package ludot.mystery;

import java.util.ArrayList;
import java.util.List;
import ludot.config.GameSettings;
import ludot.model.Board;
import ludot.model.Colour;
import ludot.output.GameMessages;
import ludot.random.RandomSource;

public final class MysteryCell {
    private final GameSettings settings = GameSettings.getInstance();
    private final Board board;
    private final RandomSource random;
    private final GameMessages messages;
    private int location = -1;
    private int firstStandardRound;
    private int roundsRemaining;

    public MysteryCell(Board board, RandomSource random, GameMessages messages) {
        this.board = board;
        this.random = random;
        this.messages = messages;
    }

    public void finishRound(int round) {
        observeBoard(round);
        if (isActive()) {
            roundsRemaining--;
            if (roundsRemaining == 0) {
                spawn();
            }
        } else if (firstStandardRound > 0
                && round >= firstStandardRound + settings.getMysteryDelay()) {
            spawn();
        }
    }

    public void observeBoard(int round) {
        if (firstStandardRound == 0 && board.hasStandardPieces()) {
            firstStandardRound = round;
        }
    }

    private void spawn() {
        List<Integer> emptyCells = new ArrayList<>();
        for (int cell = 0; cell < settings.getStandardCells(); cell++) {
            if (cell != location && board.piecesAt(cell, Colour.YELLOW).isEmpty()) {
                emptyCells.add(cell);
            }
        }
        location = emptyCells.get(random.nextInt(emptyCells.size()));
        roundsRemaining = settings.getMysteryRounds();
        messages.mysterySpawned(location);
    }

    public boolean isActive() {
        return location >= 0;
    }

    public int getLocation() {
        return location;
    }

    public int getRoundsRemaining() {
        return roundsRemaining;
    }
}
