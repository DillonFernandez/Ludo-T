// Generates game messages describing turns, moves, effects, locations and results.
package ludot.output;

import ludot.config.GameSettings;
import ludot.model.Board;
import ludot.model.Piece;
import ludot.model.Player;
import ludot.movement.MovePlan;
import ludot.mystery.MysteryCell;

public final class GameMessages {
    private final GameSettings settings = GameSettings.getInstance();
    private final GameOutput output;

    public GameMessages(GameOutput output) {
        if (output == null) {
            throw new IllegalArgumentException("Game output is required.");
        }
        this.output = output;
    }

    public void introduce(Player player) {
        String initial = player.getColour().getInitial();
        output.write("The " + colour(player) + " player has four (04) pieces named "
                + initial + "1, " + initial + "2, " + initial + "3, and "
                + initial + "4.");
    }

    public void startingRoll(Player player, int roll) {
        output.write(colour(player) + " rolls " + roll);
    }

    public void startingPlayer(Player[] order) {
        output.write(colour(order[0])
                + " player has the highest roll and will begin the game.");
        output.write("The order of a single round is " + colour(order[0])
                + ", " + colour(order[1]) + ", " + colour(order[2]) + ", and "
                + colour(order[3]) + ".");
    }

    public void roundStarted(int round) {
        output.write("Round " + round);
    }

    public void roll(Player player, int roll) {
        output.write(colour(player) + " player rolled " + roll + ".");
    }

    public void entry(Piece piece) {
        output.write(piece.getColour().getName() + " player moves piece "
                + piece.getName() + " to the starting point.");
    }

    public void counts(Player player) {
        output.write(colour(player) + " player now has " + player.countOnBoard()
                + "/4 on pieces on the board and " + player.countAtBase()
                + "/4 pieces on the base.");
    }

    public void movement(MovePlan plan) {
        for (Piece piece : plan.pieces) {
            output.write(piece.getColour().getName() + " moves piece "
                    + piece.getName() + " from location "
                    + location(piece, plan.from) + " to "
                    + location(piece, piece.getLocation()) + " by " + plan.steps
                    + " units in " + plan.direction.getName() + " direction.");
        }
    }

    public void blocked(MovePlan plan, boolean movedPartly) {
        Piece piece = plan.pieces.get(0);
        Piece blocker = plan.blockers.get(0);
        String colour = piece.getColour().getName();
        output.write(colour + " piece " + piece.getNumber()
                + " is blocked from moving from " + location(piece, plan.from)
                + " to " + location(piece, plan.intendedDestination) + " by "
                + blocker.getColour().getName() + " piece "
                + blocker.getNumber() + ".");
        output.write(colour + " does not have other pieces in the board to move "
                + "instead of the blocked piece.");
        if (movedPartly) {
            output.write("Moved the piece to square "
                    + location(piece, plan.destination)
                    + " which is the cell before the block.");
        } else {
            output.write("Ignoring the throw and moving on to the next player.");
        }
    }

    public void capture(Piece capturer, Piece opponent, int location) {
        output.write(capturer.getColour().getName() + " piece "
                + capturer.getNumber() + " lands on square " + location
                + ", captures " + opponent.getColour().getName() + " piece "
                + opponent.getNumber() + ", and returns it to the base.");
    }

    public void roundStatus(Board board, MysteryCell mystery) {
        for (Player player : board.getPlayers()) {
            counts(player);
            output.write("============================");
            output.write("Location of pieces " + colour(player));
            output.write("============================");
            for (Piece piece : player.getPieces()) {
                output.write("Piece " + piece.getNumber() + " -> "
                        + location(piece, piece.getLocation()) + ".");
            }
            if (mystery.isActive()) {
                output.write("The mystery cell is at " + mystery.getLocation()
                        + " and will be at that location for the next "
                        + mystery.getRoundsRemaining() + " values.");
            }
        }
    }

    public void mysterySpawned(int location) {
        output.write("A mystery cell has spawned in location " + location
                + " and will be at this location for the next four rounds.");
    }

    public void teleport(Piece piece, String destination) {
        output.write(piece.getColour().getName()
                + " player lands on a mystery cell and is teleported to "
                + destination + ".");
        output.write(piece.getColour().getName() + " piece " + piece.getNumber()
                + " teleported to " + destination + ".");
    }

    public void energised(Piece piece) {
        output.write(prefix(piece) + " feels energized, and movement speed doubles.");
    }

    public void sick(Piece piece) {
        output.write(prefix(piece) + " feels sick, and movement speed halves.");
    }

    public void briefing(Piece piece) {
        output.write(prefix(piece)
                + " attends briefing and cannot move for four rounds.");
    }

    public void briefingReturn(Piece piece) {
        output.write(prefix(piece) + " is movement-restricted and has rolled three "
                + "consecutively. Teleporting piece " + piece.getNumber()
                + " to base.");
    }

    public void reverseDirection(Piece piece) {
        output.write("The " + prefix(piece) + ", which was moving clockwise, "
                + "has changed to moving counterclockwise.");
    }

    public void gammaToBeta(Piece piece) {
        output.write("The " + prefix(piece) + " is moving in a counterclockwise "
                + "direction. Teleporting to Beta from Gamma.");
    }

    public void winner(Player player) {
        output.write(colour(player) + " player wins!!!");
    }

    public void noWinner() {
        output.write("The game ran 10,000 rounds without a winner, "
                + "so the simulation has ended.");
    }

    public String location(Piece piece, int location) {
        if (location == settings.getBase()) {
            return "Base";
        }
        if (location == settings.getHome()) {
            return "Home";
        }
        if (location >= settings.getStandardCells()) {
            return piece.getColour().getName() + "homepath"
                    + (location - settings.getStandardCells());
        }
        return Integer.toString(location);
    }

    private String colour(Player player) {
        return player.getColour().getName();
    }

    private String prefix(Piece piece) {
        return piece.getColour().getName() + " piece " + piece.getNumber();
    }
}
