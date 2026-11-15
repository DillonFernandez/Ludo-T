// Searches for legal ways to split blocks by distributing six steps among departing pieces.
package ludot.movement;

import java.util.ArrayList;
import java.util.List;
import ludot.config.GameSettings;
import ludot.model.Board;
import ludot.model.Piece;
import ludot.model.Player;

public final class BlockBreaker {
    private final Board board;
    private final MoveRules rules;
    private final MoveExecutor executor;

    public BlockBreaker(Board board, MoveRules rules, MoveExecutor executor) {
        this.board = board;
        this.rules = rules;
        this.executor = executor;
    }

    public void breakBlocks(Player player) {
        List<List<Piece>> blocks = findBlocks(player);
        if (blocks.isEmpty()) {
            return;
        }
        for (List<Piece> departing : departingChoices(blocks)) {
            List<MovePlan> moves = findSixStepMoves(departing);
            if (!moves.isEmpty()) {
                for (MovePlan move : moves) {
                    move.pieces.get(0).restoreOriginalDirection();
                    executor.execute(move);
                }
                return;
            }
        }
        new NoMove(rules.possibleMoves(player,
                GameSettings.getInstance().getDiceFaces()),
                executor.getMessages()).execute();
    }

    private List<List<Piece>> findBlocks(Player player) {
        List<List<Piece>> blocks = new ArrayList<>();
        List<Integer> visited = new ArrayList<>();
        for (Piece piece : player.getPieces()) {
            if (piece.isAtBase() || piece.isAtHome()
                    || visited.contains(piece.getLocation())) {
                continue;
            }
            List<Piece> block = board.companions(piece);
            visited.add(piece.getLocation());
            if (block.size() > 1) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    private List<List<Piece>> departingChoices(List<List<Piece>> blocks) {
        List<List<Piece>> choices = new ArrayList<>();
        choices.add(new ArrayList<Piece>());
        for (List<Piece> block : blocks) {
            List<List<Piece>> nextChoices = new ArrayList<>();
            for (List<Piece> previous : choices) {
                for (Piece remaining : block) {
                    List<Piece> departing = new ArrayList<>(previous);
                    for (Piece piece : block) {
                        if (piece != remaining) {
                            departing.add(piece);
                        }
                    }
                    nextChoices.add(departing);
                }
            }
            choices = nextChoices;
        }
        return choices;
    }

    private List<MovePlan> findSixStepMoves(List<Piece> departing) {
        int totalSteps = GameSettings.getInstance().getDiceFaces();
        if (departing.size() == 1) {
            return plansFor(departing, new int[] { totalSteps });
        }
        for (int first = 1; first < totalSteps; first++) {
            if (departing.size() == 2) {
                List<MovePlan> plans = plansFor(departing,
                        new int[] { first, totalSteps - first });
                if (!plans.isEmpty()) {
                    return plans;
                }
            } else {
                for (int second = 1; second < totalSteps - first; second++) {
                    List<MovePlan> plans = plansFor(departing,
                            new int[] { first, second, totalSteps - first - second });
                    if (!plans.isEmpty()) {
                        return plans;
                    }
                }
            }
        }
        return new ArrayList<>();
    }

    private List<MovePlan> plansFor(List<Piece> departing, int[] steps) {
        List<MovePlan> plans = new ArrayList<>();
        List<Integer> destinations = new ArrayList<>();
        for (int index = 0; index < departing.size(); index++) {
            MovePlan plan = rules.planForcedMove(departing.get(index), steps[index]);
            boolean sharesDestination = destinations.contains(plan.destination)
                    && plan.destination != GameSettings.getInstance().getHome();
            if (!plan.legal || plan.blocked || plan.formsBlock
                    || sharesDestination) {
                return new ArrayList<>();
            }
            plans.add(plan);
            destinations.add(plan.destination);
        }
        return plans;
    }
}
