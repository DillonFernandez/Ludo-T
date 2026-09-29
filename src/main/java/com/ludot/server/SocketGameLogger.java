package com.ludot.server;

import com.ludot.database.GameResultRepository;
import com.ludot.model.Colour;
import com.ludot.model.Direction;
import com.ludot.model.Location;
import com.ludot.model.Piece;
import com.ludot.output.GameLogger;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SocketGameLogger extends GameLogger {

    private final List<PrintWriter> clients = new CopyOnWriteArrayList<>();
    private final GameResultRepository gameResultRepository;

    public SocketGameLogger(GameResultRepository gameResultRepository) {
        if (gameResultRepository == null) {
            throw new IllegalArgumentException(
                    "GameResultRepository must not be null.");
        }

        this.gameResultRepository = gameResultRepository;
    }

    public void addClient(PrintWriter output) {
        clients.add(output);
    }

    public void removeClient(PrintWriter output) {
        clients.remove(output);
    }

    @Override
    public void log(String message) {
        broadcast(message);
    }

    @Override
    public void logWinner(Colour colour) {
        super.logWinner(colour);
        gameResultRepository.saveWinner(colour);
    }

    @Override
    public void publishPieceState(Piece piece) {
        broadcastPieceState(piece);
    }

    @Override
    public void logMove(
            Colour colour,
            Piece piece,
            Location from,
            Location to,
            int value,
            Direction direction) {
        super.logMove(colour, piece, from, to, value, direction);
        broadcastPieceState(piece);
    }

    @Override
    public void logMoveToStartingPoint(
            Colour colour,
            Piece piece) {
        super.logMoveToStartingPoint(colour, piece);
        broadcastPieceState(piece);
    }

    @Override
    public void logCapture(
            Piece capturingPiece,
            Location location,
            Piece capturedPiece) {
        super.logCapture(capturingPiece, location, capturedPiece);

        broadcastPieceState(capturingPiece);
        broadcastPieceState(capturedPiece);
    }

    @Override
    public void logBlockadeCapture(
            List<Piece> capturingPieces,
            Location location,
            List<Piece> capturedPieces) {
        super.logBlockadeCapture(
                capturingPieces,
                location,
                capturedPieces);

        capturingPieces.forEach(this::broadcastPieceState);
        capturedPieces.forEach(this::broadcastPieceState);
    }

    @Override
    public void logTeleportedTo(
            Piece piece,
            String destination) {
        super.logTeleportedTo(piece, destination);
    }

    @Override
    public void logBriefingRestrictedTeleport(Piece piece) {
        super.logBriefingRestrictedTeleport(piece);
    }

    @Override
    public void logGammaCounterClockwiseToBeta(Piece piece) {
        super.logGammaCounterClockwiseToBeta(piece);
    }

    @Override
    public void logThirdConsecutiveSixBlockBreak(
            Colour colour,
            Piece piece,
            int units,
            Direction direction) {
        super.logThirdConsecutiveSixBlockBreak(
                colour,
                piece,
                units,
                direction);

        broadcastPieceState(piece);
    }

    @Override
    public void logPieceLocation(Piece piece) {
        super.logPieceLocation(piece);
        broadcastPieceState(piece);
    }

    private void broadcastPieceState(Piece piece) {
        Location location = piece.getLocation();

        String locationColour = location.getColour() == null
                ? "-"
                : location.getColour().name();

        broadcast(
                "PIECE|"
                        + piece.getName()
                        + "|"
                        + location.getType().name()
                        + "|"
                        + locationColour
                        + "|"
                        + location.getIndex());
    }

    private void broadcast(String message) {
        for (PrintWriter client : clients) {
            client.println(message);
        }
    }
}