// Calculates legal piece and block moves, including deployment, blockades and Home entry.
package ludot.movement;

import java.util.ArrayList;
import java.util.List;
import ludot.config.GameSettings;
import ludot.model.Board;
import ludot.model.Colour;
import ludot.model.Direction;
import ludot.model.Piece;
import ludot.model.Player;

public final class MoveRules {
    private final GameSettings settings = GameSettings.getInstance();
    private final Board board;

    public MoveRules(Board board) {
        this.board = board;
    }

    public List<MovePlan> possibleMoves(Player player, int roll) {
        checkRoll(roll);
        List<MovePlan> plans = new ArrayList<>();
        List<Integer> visitedBlocks = new ArrayList<>();
        for (Piece piece : player.getPieces()) {
            plans.add(planPiece(piece, roll));
            List<Piece> block = board.companions(piece);
            if (block.size() > 1 && !visitedBlocks.contains(piece.getLocation())) {
                plans.add(planBlock(block, roll));
                visitedBlocks.add(piece.getLocation());
            }
        }
        return plans;
    }

    public MovePlan planPiece(Piece piece, int roll) {
        checkRoll(roll);
        List<Piece> pieces = new ArrayList<>();
        pieces.add(piece);
        return plan(pieces, roll);
    }

    public MovePlan planBlock(List<Piece> block, int roll) {
        checkRoll(roll);
        if (block.size() < 2) {
            throw new IllegalArgumentException("A block needs at least two pieces.");
        }
        return plan(block, roll);
    }

    public MovePlan planForcedMove(Piece piece, int steps) {
        List<Piece> pieces = new ArrayList<>();
        pieces.add(piece);
        MovePlan plan = new MovePlan(pieces);
        if (piece.isAtBase() || !piece.canMove() || steps < 1) {
            return plan;
        }
        plan.direction = piece.getOriginalDirection();
        plan.steps = steps;
        return planPath(plan);
    }

    private MovePlan plan(List<Piece> pieces, int roll) {
        MovePlan plan = new MovePlan(pieces);
        Piece first = pieces.get(0);
        for (Piece piece : pieces) {
            if (!piece.canMove() || piece.getLocation() != plan.from
                    || piece.getColour() != first.getColour()) {
                return plan;
            }
        }
        if (first.isAtBase()) {
            return planEntry(plan, roll);
        }
        plan.direction = board.blockDirection(pieces);
        // A piece leaving a block that has moved must resume its original direction.
        if (pieces.size() == 1 && first.hasMovedInBlock()
                && board.companions(first).size() > 1) {
            plan.direction = first.getOriginalDirection();
        }
        plan.steps = first.movementFor(roll) / pieces.size();
        for (Piece piece : pieces) {
            if (piece.movementFor(roll) / pieces.size() != plan.steps) {
                return plan;
            }
        }
        if (plan.steps == 0) {
            return plan;
        }
        return planPath(plan);
    }

    private MovePlan planEntry(MovePlan plan, int roll) {
        if (roll != settings.getDiceFaces()) {
            return plan;
        }
        plan.entry = true;
        plan.destination = settings.getStartingCell(plan.pieces.get(0).getColour());
        plan.intendedDestination = plan.destination;
        checkLanding(plan);
        return plan;
    }

    private MovePlan planPath(MovePlan plan) {
        int position = plan.from;
        boolean validDestination = true;
        for (int step = 1; step <= plan.steps; step++) {
            int next = nextCell(plan, position);
            if (next < 0 || position == settings.getHome()) {
                validDestination = false;
                break;
            }
            if (next < settings.getStandardCells()) {
                countApproachVisits(plan, next);
            }
            position = next;
        }
        plan.destination = position;
        plan.intendedDestination = position;
        findBlockade(plan);
        if ((!validDestination && !plan.blocked)
                || (plan.blocked && plan.destination == plan.from)) {
            return plan;
        }
        checkLanding(plan);
        return plan;
    }

    private int nextCell(MovePlan plan, int position) {
        if (position >= settings.getStandardCells()) {
            return position + 1;
        }
        Piece first = plan.pieces.get(0);
        if (position == settings.getApproachCell(first.getColour())) {
            boolean entersHome = first.canEnterHome(plan.direction,
                    plan.approachVisits[0]);
            for (int index = 1; index < plan.pieces.size(); index++) {
                if (plan.pieces.get(index).canEnterHome(plan.direction,
                        plan.approachVisits[index]) != entersHome) {
                    return -1;
                }
            }
            if (entersHome) {
                return settings.getStandardCells();
            }
        }
        return settings.wrapCell(position + plan.direction.getStep());
    }

    private void countApproachVisits(MovePlan plan, int position) {
        for (int index = 0; index < plan.pieces.size(); index++) {
            if (plan.direction == Direction.COUNTERCLOCKWISE
                    && position == settings.getApproachCell(
                            plan.pieces.get(index).getColour())) {
                plan.approachVisits[index]++;
            }
        }
    }

    private void findBlockade(MovePlan plan) {
        for (int index = 0; index < plan.pieces.size(); index++) {
            plan.approachVisits[index] = plan.pieces.get(index).getApproachVisits();
        }
        int position = plan.from;
        for (int step = 1; step <= plan.steps; step++) {
            int next = nextCell(plan, position);
            if (next < 0 || position == settings.getHome()) {
                return;
            }
            List<Piece> opponents = board.opponentsAt(next,
                    plan.pieces.get(0).getColour());
            if (opponents.size() > 1) {
                boolean equalBlockCapture = step == plan.steps
                        && opponents.size() == plan.pieces.size();
                if (!equalBlockCapture) {
                    plan.blockers.addAll(opponents);
                    plan.blocked = true;
                    if (step < plan.steps) {
                        plan.destination = position;
                        plan.steps = step - 1;
                    } else {
                        plan.destination = plan.from;
                    }
                    return;
                }
            }
            if (next < settings.getStandardCells()) {
                countApproachVisits(plan, next);
            }
            position = next;
        }
    }

    private void checkLanding(MovePlan plan) {
        Colour colour = plan.pieces.get(0).getColour();
        List<Piece> opponents = board.opponentsAt(plan.destination, colour);
        if (opponents.size() > 1 && opponents.size() != plan.pieces.size()) {
            plan.blocked = true;
            plan.blockers.addAll(opponents);
            return;
        }
        plan.legal = true;
        plan.capturesOpponent = !opponents.isEmpty();
        for (Piece opponent : opponents) {
            plan.capturedDistance = Math.min(plan.capturedDistance,
                    board.distanceFromHome(opponent));
        }
        for (Piece occupant : board.piecesAt(plan.destination, colour)) {
            if (occupant.getColour() == colour && !plan.pieces.contains(occupant)) {
                plan.formsBlock = true;
            }
        }
    }

    private void checkRoll(int roll) {
        if (roll < 1 || roll > settings.getDiceFaces()) {
            throw new IllegalArgumentException("A dice roll must be from 1 to 6.");
        }
    }
}
