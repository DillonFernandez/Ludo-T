package com.ludot.factory;

import com.ludot.board.Board;
import com.ludot.model.Colour;
import com.ludot.model.Piece;
import com.ludot.output.GameLogger;
import com.ludot.player.*;
import com.ludot.random.CoinToss;
import com.ludot.random.Dice;
import com.ludot.rules.RuleEngine;

import java.util.List;

// Builds the player objects that share the same game services.
public class PlayerFactory {

    // These shared services are injected into every player instance.
    private final Board board;
    private final RuleEngine ruleEngine;
    private final Dice dice;
    private final CoinToss coinToss;
    private final GameLogger logger;

    // The factory validates its dependencies so players are created with a usable
    // setup.
    public PlayerFactory(
            Board board,
            RuleEngine ruleEngine,
            Dice dice,
            CoinToss coinToss,
            GameLogger logger) {
        if (board == null)
            throw new IllegalArgumentException("Board must not be null.");
        if (ruleEngine == null)
            throw new IllegalArgumentException("RuleEngine must not be null.");
        if (dice == null)
            throw new IllegalArgumentException("Dice must not be null.");
        if (coinToss == null)
            throw new IllegalArgumentException("CoinToss must not be null.");
        if (logger == null)
            throw new IllegalArgumentException("GameLogger must not be null.");

        this.board = board;
        this.ruleEngine = ruleEngine;
        this.dice = dice;
        this.coinToss = coinToss;
        this.logger = logger;
    }

    // The correct player class is chosen from the colour provided.
    public AbstractPlayer createPlayer(Colour colour, List<Piece> pieces) {
        if (colour == null)
            throw new IllegalArgumentException("Colour must not be null.");
        if (pieces == null)
            throw new IllegalArgumentException("Piece list must not be null.");

        // Each colour maps to its own player implementation.
        return switch (colour) {
            case RED -> new RedPlayer(
                    colour,
                    pieces,
                    board,
                    ruleEngine,
                    dice,
                    coinToss,
                    logger);
            case GREEN -> new GreenPlayer(
                    colour,
                    pieces,
                    board,
                    ruleEngine,
                    dice,
                    coinToss,
                    logger);
            case YELLOW -> new YellowPlayer(
                    colour,
                    pieces,
                    board,
                    ruleEngine,
                    dice,
                    coinToss,
                    logger);
            case BLUE -> new BluePlayer(
                    colour,
                    pieces,
                    board,
                    ruleEngine,
                    dice,
                    coinToss,
                    logger);
        };
    }
}