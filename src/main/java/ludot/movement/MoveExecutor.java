// Updates pieces for planned moves, captures, mystery teleports and movement effects.
package ludot.movement;

import java.util.ArrayList;
import java.util.List;
import ludot.config.GameSettings;
import ludot.model.Board;
import ludot.model.Direction;
import ludot.model.Piece;
import ludot.model.Player;
import ludot.mystery.MysteryCell;
import ludot.output.GameMessages;
import ludot.random.RandomSource;
import ludot.state.BriefingState;
import ludot.state.EnergisedState;
import ludot.state.SickState;

public final class MoveExecutor {
    private final GameSettings settings = GameSettings.getInstance();
    private final Board board;
    private final RandomSource random;
    private final GameMessages messages;
    private final MysteryCell mystery;
    private int round = 1;

    public MoveExecutor(Board board, RandomSource random,
            GameMessages messages, MysteryCell mystery) {
        this.board = board;
        this.random = random;
        this.messages = messages;
        this.mystery = mystery;
    }

    public boolean execute(MovePlan plan) {
        if (!plan.legal) {
            throw new IllegalArgumentException("An illegal move cannot be made.");
        }
        for (int index = 0; index < plan.pieces.size(); index++) {
            Piece piece = plan.pieces.get(index);
            if (plan.entry) {
                piece.enterBoard(Direction.CLOCKWISE);
                messages.entry(piece);
            } else {
                if (plan.pieces.size() > 1) {
                    piece.recordBlockMove();
                } else if (piece.hasMovedInBlock() && board.companions(piece).size() > 1) {
                    piece.restoreOriginalDirection();
                }
                piece.moveTo(plan.destination, plan.approachVisits[index]);
            }
        }
        mystery.observeBoard(round);
        if (plan.blocked) {
            messages.blocked(plan, true);
        } else if (!plan.entry) {
            messages.movement(plan);
        }
        boolean captured = captureAt(plan.pieces, plan.destination);
        if (plan.entry) {
            Piece piece = plan.pieces.get(0);
            if (random.nextInt(settings.getCoinSides()) == 1) {
                piece.chooseStartingDirection(Direction.COUNTERCLOCKWISE);
            }
            if (!captured) {
                messages.counts(ownerOf(piece));
            }
        }
        for (Piece piece : plan.pieces) {
            if (mystery.isActive() && piece.isOnStandardPath()
                    && piece.getLocation() == mystery.getLocation()) {
                // Gamma uses the group's travel direction rather than each member's stored
                // direction.
                Direction travelDirection = plan.entry ? piece.getDirection() : plan.direction;
                captured = teleport(piece, travelDirection) || captured;
            }
        }
        return captured;
    }

    private boolean captureAt(List<Piece> capturers, int destination) {
        Piece first = capturers.get(0);
        List<Piece> opponents = board.opponentsAt(destination, first.getColour());
        if (opponents.isEmpty()) {
            return false;
        }
        for (Piece opponent : opponents) {
            messages.capture(first, opponent, destination);
            opponent.returnToBase();
        }
        for (Piece capturer : capturers) {
            capturer.recordCapture();
        }
        messages.counts(ownerOf(first));
        return true;
    }

    private boolean teleport(Piece piece, Direction travelDirection) {
        int choice = random.nextInt(settings.getMysteryDestinations());
        if (choice == GameSettings.BASE_DESTINATION) {
            messages.teleport(piece, "Base");
            piece.returnToBase();
            return false;
        }
        int destination = destinationFor(choice, piece);
        if (board.opponentsAt(destination, piece.getColour()).size() > 1) {
            return false;
        }
        messages.teleport(piece, destinationName(choice));
        boolean captured = teleportTo(piece, destination);
        if (choice == GameSettings.ALPHA_DESTINATION) {
            applyAlpha(piece);
        } else if (choice == GameSettings.BETA_DESTINATION) {
            applyBeta(piece);
        } else if (choice == GameSettings.GAMMA_DESTINATION) {
            captured = applyGamma(piece, travelDirection) || captured;
        }
        return captured;
    }

    private boolean teleportTo(Piece piece, int destination) {
        List<Piece> pieces = new ArrayList<>();
        pieces.add(piece);
        boolean captured = captureAt(pieces, destination);
        piece.moveTo(destination, piece.getApproachVisits());
        return captured;
    }

    private int destinationFor(int choice, Piece piece) {
        switch (choice) {
            case GameSettings.ALPHA_DESTINATION:
                return settings.getAlphaCell();
            case GameSettings.BETA_DESTINATION:
                return settings.getBetaCell();
            case GameSettings.GAMMA_DESTINATION:
                return settings.getGammaCell();
            case GameSettings.START_DESTINATION:
                return settings.getStartingCell(piece.getColour());
            case GameSettings.APPROACH_DESTINATION:
                return settings.getApproachCell(piece.getColour());
            default:
                throw new IllegalArgumentException("Unknown mystery destination.");
        }
    }

    private String destinationName(int choice) {
        switch (choice) {
            case GameSettings.ALPHA_DESTINATION:
                return "Alpha";
            case GameSettings.BETA_DESTINATION:
                return "Beta";
            case GameSettings.GAMMA_DESTINATION:
                return "Gamma";
            case GameSettings.START_DESTINATION:
                return "X";
            case GameSettings.APPROACH_DESTINATION:
                return "Approach";
            default:
                throw new IllegalArgumentException("Unknown mystery destination.");
        }
    }

    private void applyAlpha(Piece piece) {
        if (random.nextInt(settings.getCoinSides()) == 0) {
            piece.applyState(new EnergisedState(), round);
            messages.energised(piece);
        } else {
            piece.applyState(new SickState(), round);
            messages.sick(piece);
        }
    }

    private void applyBeta(Piece piece) {
        piece.applyState(new BriefingState(), round);
        messages.briefing(piece);
    }

    private boolean applyGamma(Piece piece, Direction travelDirection) {
        if (travelDirection == Direction.CLOCKWISE) {
            messages.reverseDirection(piece);
            piece.turnCounterclockwise();
            return false;
        }
        int destination = settings.getBetaCell();
        if (board.opponentsAt(destination, piece.getColour()).size() > 1) {
            return false;
        }
        messages.gammaToBeta(piece);
        boolean captured = teleportTo(piece, destination);
        applyBeta(piece);
        return captured;
    }

    private Player ownerOf(Piece piece) {
        for (Player player : board.getPlayers()) {
            if (player.getColour() == piece.getColour()) {
                return player;
            }
        }
        throw new IllegalStateException("The piece has no player on this board.");
    }

    public void setRound(int round) {
        this.round = round;
    }

    public GameMessages getMessages() {
        return messages;
    }
}
