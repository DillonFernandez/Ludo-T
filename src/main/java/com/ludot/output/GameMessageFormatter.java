package com.ludot.output;

import com.ludot.board.Path;
import com.ludot.config.GameConfig;
import com.ludot.model.Colour;
import com.ludot.model.Direction;
import com.ludot.model.Location;
import com.ludot.model.Piece;

import java.util.List;

// Builds human-readable game messages for the logger to print.
public class GameMessageFormatter {
    // The formatter converts game events into readable text lines.

    // This helper makes missing inputs fail fast with a clear error.
    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null.");
        }
    }

    // These methods format the opening and round-order messages.
    public String formatSimulationTitle() {
        return "========================================\n"
                + "          LUDO-T SIMULATION\n"
                + "========================================";
    }

    public String formatPlayerPiecesIntro(Colour colour) {
        requireNonNull(colour, "colour");
        char initial = colour.name().charAt(0);
        return "The " + displayColourName(colour).toLowerCase() + " player has four (04) pieces named "
                + initial + "1, " + initial + "2, " + initial + "3, and " + initial + "4.";
    }

    public String formatPlayersIntro() {
        StringBuilder builder = new StringBuilder();
        for (Colour colour : Colour.values()) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(formatPlayerPiecesIntro(colour));
        }
        return builder.toString();
    }

    public String formatInitialRoll(Colour colour, int value) {
        requireNonNull(colour, "colour");
        return displayColourName(colour) + " rolls " + value;
    }

    public String formatHighestRoll(Colour colour) {
        requireNonNull(colour, "colour");
        return displayColourName(colour) + " player has the highest roll and will begin the game.";
    }

    public String formatRoundOrder(List<Colour> order) {
        requireNonNull(order, "order");
        if (order.isEmpty()) {
            throw new IllegalArgumentException("order must not be empty.");
        }

        StringBuilder sb = new StringBuilder("The order of a single round is ");
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) {
                if (i == order.size() - 1) {
                    sb.append(", and ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append(displayColourName(order.get(i)));
        }
        sb.append('.');
        return sb.toString();
    }

    public String formatRoundHeader(int roundNumber) {
        if (roundNumber < 1) {
            throw new IllegalArgumentException("Round number must be at least 1.");
        }
        return "---------- Round " + roundNumber + " ----------";
    }

    // These messages describe a player's turn, including extra rolls and special
    // rule cases.
    public String formatPlayerRolled(Colour colour, int value) {
        requireNonNull(colour, "colour");
        return displayColourName(colour) + " player rolled " + value + ".";
    }

    public String formatBonusRollForDice(Colour colour) {
        requireNonNull(colour, "colour");
        return displayColourName(colour) + " player gets another roll as he/she rolled 6.";
    }

    public String formatThirdConsecutiveSixIgnored(Colour colour) {
        requireNonNull(colour, "colour");
        return displayColourName(colour)
                + " player rolled three consecutively, ignoring the throw and moving on to the next player.";
    }

    public String formatThirdConsecutiveSixBlockBreak(Colour colour, Piece piece,
                                                      int units, Direction direction) {
        requireNonNull(colour, "colour");
        requireNonNull(piece, "piece");
        requireNonNull(direction, "direction");
        return displayColourName(colour)
                + " player rolled three consecutively and break the block removing the piece "
                + pieceName(piece)
                + " from the block, and piece "
                + pieceName(piece)
                + " moves "
                + units
                + " units in "
                + directionName(direction)
                + " direction.";
    }

    public String formatBonusRollForCapture(Piece capturingPiece, Piece capturedPiece) {
        requireNonNull(capturingPiece, "capturingPiece");
        requireNonNull(capturedPiece, "capturedPiece");
        return displayColourName(capturingPiece.getColour()) + " player gets another roll as he/she captured "
                + displayColourName(capturedPiece.getColour()) + " piece " + pieceName(capturedPiece) + ".";
    }

    public String formatMoveToStartingPoint(Colour colour, Piece piece) {
        requireNonNull(colour, "colour");
        requireNonNull(piece, "piece");
        return displayColourName(colour) + " player moves piece " + pieceName(piece)
                + " to the starting point.";
    }

    public String formatBoardBaseCount(Colour colour, int onBoardCount, int baseCount) {
        requireNonNull(colour, "colour");
        int total = GameConfig.PIECES_PER_PLAYER;
        return displayColourName(colour) + " player now has " + onBoardCount + "/" + total
                + " on pieces on the board and " + baseCount + "/" + total
                + " pieces on the base.";
    }

    // These methods describe movement, blocks, captures, and blockade interactions.
    public String formatMove(Colour colour, Piece piece, Location from, Location to,
                             int value, Direction direction) {
        requireNonNull(colour, "colour");
        requireNonNull(piece, "piece");
        requireNonNull(from, "from");
        requireNonNull(to, "to");
        requireNonNull(direction, "direction");
        return displayColourName(colour) + " moves piece " + pieceName(piece)
                + " from location " + from.getIndex()
                + " to " + to.getIndex()
                + " by " + value + " units in " + directionName(direction) + " direction.";
    }

    public String formatBlocked(Piece blockedPiece, Location from, Location to,
                                Colour blockingColour, Piece blockingPiece) {
        requireNonNull(blockedPiece, "blockedPiece");
        requireNonNull(from, "from");
        requireNonNull(to, "to");
        requireNonNull(blockingColour, "blockingColour");
        requireNonNull(blockingPiece, "blockingPiece");
        return displayColourName(blockedPiece.getColour()) + " piece " + pieceName(blockedPiece)
                + " is blocked from moving from " + locationName(from)
                + " to " + locationName(to)
                + " by " + displayColourName(blockingColour) + " piece " + pieceName(blockingPiece) + ".";
    }

    public String formatNoAlternativeMove(Colour colour) {
        requireNonNull(colour, "colour");
        return displayColourName(colour)
                + " does not have other pieces in the board to move instead of the blocked piece.";
    }

    public String formatIgnoredThrow() {
        return "Ignoring the throw and moving on to the next player.";
    }

    public String formatMovedBeforeBlock(Colour colour, Location location) {
        requireNonNull(colour, "colour");
        requireNonNull(location, "location");
        return "Moved the piece to square "
                + locationName(location) + " which is the cell before the block.";
    }

    public String formatCapture(Piece capturingPiece, Location location, Piece capturedPiece) {
        requireNonNull(capturingPiece, "capturingPiece");
        requireNonNull(location, "location");
        requireNonNull(capturedPiece, "capturedPiece");
        return displayColourName(capturingPiece.getColour()) + " piece " + pieceName(capturingPiece)
                + " lands on square " + locationName(location)
                + ", captures " + displayColourName(capturedPiece.getColour()) + " piece "
                + pieceName(capturedPiece) + ", and returns it to the base.";
    }

    public String formatBlockadeCapture(List<Piece> capturingPieces, Location location,
                                        List<Piece> capturedPieces) {
        requireNonNull(capturingPieces, "capturingPieces");
        requireNonNull(location, "location");
        requireNonNull(capturedPieces, "capturedPieces");
        if (capturingPieces.isEmpty()) {
            throw new IllegalArgumentException("capturingPieces must not be empty.");
        }
        if (capturedPieces.isEmpty()) {
            throw new IllegalArgumentException("capturedPieces must not be empty.");
        }
        return displayColourName(capturingPieces.get(0).getColour()) + " block captures "
                + displayColourName(capturedPieces.get(0).getColour()) + " block on square "
                + location.getIndex() + ". "
                + displayColourName(capturedPieces.get(0).getColour())
                + " block pieces are returned to base.";
    }

    // These messages show each player's current piece locations.
    public String formatStatusHeader(Colour colour) {
        requireNonNull(colour, "colour");
        String bar = "============================";
        return bar + "\nLocation of pieces " + displayColourName(colour) + "\n" + bar;
    }

    public String formatPieceLocation(Piece piece) {
        requireNonNull(piece, "piece");
        return "Piece " + pieceName(piece) + " -> " + locationName(piece.getLocation()) + ".";
    }

    // These messages explain mystery-cell placement and teleport effects.
    public String formatMysteryCellStatus(Location location, int remainingRounds) {
        requireNonNull(location, "location");
        return "The mystery cell is at " + locationName(location)
                + " and will be at that location for the next " + remainingRounds + " values.";
    }

    public String formatMysteryCellSpawned(Location location) {
        requireNonNull(location, "location");
        return "A mystery cell has spawned in location " + locationName(location)
                + " and will be at this location for the next four rounds.";
    }

    public String formatMysteryTeleport(Colour colour, Location destination) {
        requireNonNull(colour, "colour");
        requireNonNull(destination, "destination");
        return displayColourName(colour) + " player lands on a mystery cell and is teleported to "
                + mysteryDestinationName(destination) + ".";
    }

    public String formatTeleportedTo(Piece piece, String destination) {
        requireNonNull(piece, "piece");
        requireNonNull(destination, "destination");
        return displayColourName(piece.getColour()) + " piece " + pieceName(piece)
                + " teleported to " + destination + ".";
    }

    // These messages describe temporary piece effects and movement restrictions.
    public String formatEnergized(Piece piece) {
        requireNonNull(piece, "piece");
        return displayColourName(piece.getColour()) + " piece " + pieceName(piece)
                + " feels energized, and movement speed doubles.";
    }

    public String formatSick(Piece piece) {
        requireNonNull(piece, "piece");
        return displayColourName(piece.getColour()) + " piece " + pieceName(piece)
                + " feels sick, and movement speed halves.";
    }

    public String formatBriefing(Piece piece) {
        requireNonNull(piece, "piece");
        return displayColourName(piece.getColour()) + " piece " + pieceName(piece)
                + " attends briefing and cannot move for four rounds.";
    }

    public String formatBriefingRestrictedTeleport(Piece piece) {
        requireNonNull(piece, "piece");
        return displayColourName(piece.getColour()) + " piece " + pieceName(piece)
                + " is movement-restricted and has rolled three consecutively."
                + " Teleporting piece " + pieceName(piece) + " to base.";
    }

    public String formatGammaClockwiseChanged(Piece piece) {
        requireNonNull(piece, "piece");
        return "The " + displayColourName(piece.getColour()) + " piece " + pieceName(piece)
                + ", which was moving clockwise, has changed to moving counterclockwise.";
    }

    public String formatGammaCounterClockwiseToBeta(Piece piece) {
        requireNonNull(piece, "piece");
        return "The " + displayColourName(piece.getColour()) + " piece " + pieceName(piece)
                + " is moving in a counterclockwise direction. Teleporting to Beta from Gamma.";
    }

    // These messages report the winner or the safety-limit stop condition.
    public String formatWinner(Colour colour) {
        requireNonNull(colour, "colour");
        return displayColourName(colour) + " player wins!!!";
    }

    public String formatMaxRoundsReached(int maxRounds) {
        String bar = "========================================";
        return bar + "\nSimulation ended because the safety limit was reached after " + maxRounds + " rounds.\n"
                + "No winner was found.\n" + bar;
    }

    // These helpers turn internal values into readable words for the output text.
    public String colourName(Colour colour) {
        requireNonNull(colour, "colour");
        return colour.name().toLowerCase();
    }

    public String displayColourName(Colour colour) {
        requireNonNull(colour, "colour");
        String lower = colour.name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public String directionName(Direction direction) {
        requireNonNull(direction, "direction");
        return switch (direction) {
            case CLOCKWISE -> "clockwise";
            case COUNTER_CLOCKWISE -> "counter-clockwise";
        };
    }

    public String pieceName(Piece piece) {
        requireNonNull(piece, "piece");
        return piece.getName();
    }

    public String locationName(Location location) {
        requireNonNull(location, "location");
        return location.getDisplayName();
    }

    public String mysteryDestinationName(Location destination) {
        requireNonNull(destination, "destination");
        if (destination.isStartingSquare()) {
            return "X";
        }
        if (destination.isApproach()) {
            return "Approach";
        }
        if (destination.isBase()) {
            return "Base";
        }

        Path path = new Path();
        if (destination.getIndex() == path.getAlphaIndex()) {
            return "Alpha";
        }
        if (destination.getIndex() == path.getBetaIndex()) {
            return "Beta";
        }
        if (destination.getIndex() == path.getGammaIndex()) {
            return "Gamma";
        }

        return locationName(destination);
    }

}