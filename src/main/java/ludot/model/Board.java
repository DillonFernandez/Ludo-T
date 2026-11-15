// Finds board occupants and calculates distances to Home and movement directions for blocks.
package ludot.model;

import java.util.ArrayList;
import java.util.List;
import ludot.config.GameSettings;

public final class Board {
    private final GameSettings settings = GameSettings.getInstance();
    private final Player[] players;

    public Board(Player[] players) {
        if (players == null || players.length != settings.getPlayerCount()) {
            throw new IllegalArgumentException("The board needs four players.");
        }
        boolean[] found = new boolean[settings.getPlayerCount()];
        for (Player player : players) {
            if (player == null || found[player.getColour().ordinal()]) {
                throw new IllegalArgumentException("Player colours must differ.");
            }
            found[player.getColour().ordinal()] = true;
        }
        this.players = players.clone();
    }

    public Player[] getPlayers() {
        return players.clone();
    }

    public List<Piece> piecesAt(int location, Colour colour) {
        List<Piece> occupants = new ArrayList<>();
        if (location < 0 || location >= settings.getHome()) {
            return occupants;
        }
        for (Player player : players) {
            for (Piece piece : player.getPieces()) {
                boolean samePath = location < settings.getStandardCells()
                        || piece.getColour() == colour;
                if (samePath && piece.getLocation() == location) {
                    occupants.add(piece);
                }
            }
        }
        return occupants;
    }

    public List<Piece> companions(Piece piece) {
        List<Piece> companions = new ArrayList<>();
        for (Piece occupant : piecesAt(piece.getLocation(), piece.getColour())) {
            if (occupant.getColour() == piece.getColour()) {
                companions.add(occupant);
            }
        }
        return companions;
    }

    public List<Piece> opponentsAt(int location, Colour colour) {
        List<Piece> opponents = new ArrayList<>();
        for (Piece piece : piecesAt(location, colour)) {
            if (piece.getColour() != colour) {
                opponents.add(piece);
            }
        }
        return opponents;
    }

    public boolean hasStandardPieces() {
        for (Player player : players) {
            for (Piece piece : player.getPieces()) {
                if (piece.isOnStandardPath()) {
                    return true;
                }
            }
        }
        return false;
    }

    public int distanceFromHome(Piece piece) {
        if (piece.isAtHome()) {
            return 0;
        }
        if (piece.isAtBase()) {
            return settings.getStandardCells() + settings.getHomeStraightCells();
        }
        if (piece.isInHomeStraight()) {
            return settings.getHome() - piece.getLocation();
        }
        return distanceFromHome(piece, piece.getDirection());
    }

    public int distanceFromHome(Piece piece, Direction direction) {
        int approach = settings.getApproachCell(piece.getColour());
        int distance = settings.wrapCell((approach - piece.getLocation())
                * direction.getStep());
        if (direction == Direction.COUNTERCLOCKWISE
                && (piece.getApproachVisits() == 0
                        || (distance == 0 && piece.getApproachVisits() < settings.getRequiredApproachVisits()))) {
            distance += settings.getStandardCells();
        }
        return distance + settings.getHomeStraightCells() + 1;
    }

    public Direction blockDirection(List<Piece> block) {
        Direction direction = block.get(0).getDirection();
        int longestDistance = -1;
        for (Piece piece : block) {
            int distance = distanceFromHome(piece);
            if (distance > longestDistance) {
                longestDistance = distance;
                direction = piece.getDirection();
            }
        }
        return direction;
    }
}
