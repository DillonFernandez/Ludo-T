package com.ludot.board;

import com.ludot.config.GameConfig;
import com.ludot.factory.PieceFactory;
import com.ludot.model.*;

import java.util.*;

// Builds the board objects used by LUDO-T: the main ring, each home path, and the starting home areas.
public class BoardBuilder {

    private final PieceFactory pieceFactory;
    private final Path path;

    // The builder depends on a piece factory and path metadata so tests can replace
    // them easily.
    public BoardBuilder(PieceFactory pieceFactory, Path path) {
        if (pieceFactory == null) {
            throw new IllegalArgumentException("pieceFactory must not be null.");
        }
        if (path == null) {
            throw new IllegalArgumentException("path must not be null.");
        }
        this.pieceFactory = pieceFactory;
        this.path = path;
    }

    public BoardBuilder() {
        this(new PieceFactory(), new Path());
    }

    // This creates one complete board instance ready for the game to use.
    public Board build() {
        Map<Integer, Cell> standardCells = buildStandardCells();
        Map<Colour, List<Cell>> homePathCells = buildHomePathCells();
        Map<Colour, HomeArea> homeAreas = buildHomeAreas();

        return new Board(standardCells, homePathCells, homeAreas, path);
    }

    // The main ring is classified into special, entry, approach, and normal cells
    // before the board is assembled.
    private Map<Integer, Cell> buildStandardCells() {
        int alphaIndex = path.getAlphaIndex();
        int betaIndex = path.getBetaIndex();
        int gammaIndex = path.getGammaIndex();

        Map<Integer, Cell> cells = new HashMap<>();

        for (int i = 0; i < GameConfig.STANDARD_PATH_SIZE; i++) {
            CellType cellType = resolveCellType(i, alphaIndex, betaIndex, gammaIndex);
            Optional<Colour> owner = resolveOwner(cellType, i);
            Location location = resolveLocation(cellType, i, owner.orElse(null));

            cells.put(i, new Cell(location, cellType, owner.orElse(null)));
        }
        return cells;
    }

    // This mapping is based on the fixed special-square indexes and each colour's
    // entry and approach positions.
    private CellType resolveCellType(int index, int alphaIndex, int betaIndex, int gammaIndex) {
        if (index == alphaIndex)
            return CellType.ALPHA;
        if (index == betaIndex)
            return CellType.BETA;
        if (index == gammaIndex)
            return CellType.GAMMA;

        for (Colour colour : Colour.values()) {
            if (index == path.getStartingIndex(colour))
                return CellType.STARTING_SQUARE;
        }

        for (Colour colour : Colour.values()) {
            if (index == path.getApproachIndex(colour))
                return CellType.APPROACH;
        }

        return CellType.STANDARD;
    }

    // Only entry and approach cells need an owner colour; standard cells do not.
    private Optional<Colour> resolveOwner(CellType cellType, int index) {
        if (cellType == CellType.STARTING_SQUARE) {
            for (Colour colour : Colour.values()) {
                if (index == path.getStartingIndex(colour))
                    return Optional.of(colour);
            }
        }
        if (cellType == CellType.APPROACH) {
            for (Colour colour : Colour.values()) {
                if (index == path.getApproachIndex(colour))
                    return Optional.of(colour);
            }
        }
        return Optional.empty();
    }

    // The location object carries the square type and, when needed, the colour that
    // owns it.
    private Location resolveLocation(CellType cellType, int index, Colour owner) {
        return switch (cellType) {
            case ALPHA -> Location.alpha(index);
            case BETA -> Location.beta(index);
            case GAMMA -> Location.gamma(index);
            case STARTING_SQUARE -> Location.startingSquare(owner, index);
            case APPROACH -> Location.approach(owner, index);
            default -> Location.standardPath(index);
        };
    }

    // Each colour gets its own short path into the finish area.
    private Map<Colour, List<Cell>> buildHomePathCells() {
        Map<Colour, List<Cell>> map = new EnumMap<>(Colour.class);
        for (Colour colour : Colour.values()) {
            List<Cell> cells = new ArrayList<>();
            for (int i = 0; i < GameConfig.HOME_PATH_SIZE; i++) {
                Location location = Location.homePath(colour, i);
                cells.add(new Cell(location, CellType.HOME_PATH, colour));
            }
            map.put(colour, cells);
        }
        return map;
    }

    // The home area also creates the pieces that begin the game for each colour.
    private Map<Colour, HomeArea> buildHomeAreas() {
        Map<Colour, HomeArea> map = new EnumMap<>(Colour.class);
        for (Colour colour : Colour.values()) {
            List<Piece> pieces = pieceFactory.createPieces(colour);
            map.put(colour, new HomeArea(colour, pieces));
        }
        return map;
    }
}