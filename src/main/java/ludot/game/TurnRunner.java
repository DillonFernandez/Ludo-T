// Controls a player's rolls, bonus actions, third-six block breaking and briefing returns.
package ludot.game;

import java.util.List;
import ludot.behaviour.PlayerBehaviour;
import ludot.config.GameSettings;
import ludot.model.Piece;
import ludot.model.Player;
import ludot.movement.BlockBreaker;
import ludot.movement.MoveCommand;
import ludot.movement.MoveExecutor;
import ludot.movement.MovePlan;
import ludot.movement.MoveRules;
import ludot.random.RandomSource;

public final class TurnRunner {
    private final GameSettings settings = GameSettings.getInstance();
    private final RandomSource random;
    private final MoveRules rules;
    private final MoveExecutor executor;
    private final BlockBreaker blockBreaker;

    public TurnRunner(RandomSource random, MoveRules rules,
            MoveExecutor executor, BlockBreaker blockBreaker) {
        this.random = random;
        this.rules = rules;
        this.executor = executor;
        this.blockBreaker = blockBreaker;
    }

    public void playTurn(Player player, PlayerBehaviour behaviour) {
        int consecutiveSixes = 0;
        boolean rollAgain = true;
        while (rollAgain && !player.hasWon()) {
            int roll = random.nextInt(settings.getDiceFaces()) + 1;
            executor.getMessages().roll(player, roll);
            returnRestrictedPieces(player, roll);
            if (roll == settings.getDiceFaces()) {
                consecutiveSixes++;
            } else {
                consecutiveSixes = 0;
            }
            if (consecutiveSixes == settings.getMaximumConsecutiveSixes()) {
                blockBreaker.breakBlocks(player);
                return;
            }
            List<MovePlan> moves = rules.possibleMoves(player, roll);
            MoveCommand command = behaviour.chooseMove(moves, executor);
            command.execute();
            rollAgain = roll == settings.getDiceFaces()
                    || command.capturedOpponent();
        }
    }

    private void returnRestrictedPieces(Player player, int roll) {
        for (Piece piece : player.getPieces()) {
            if (piece.mustReturnAfterRoll(roll)) {
                executor.getMessages().briefingReturn(piece);
                piece.returnToBase();
            }
        }
    }
}
