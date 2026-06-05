package com.ludot.engine;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.config.GameConfig;
import com.ludot.factory.PlayerFactory;
import com.ludot.model.Colour;
import com.ludot.model.Piece;
import com.ludot.output.GameLogger;
import com.ludot.player.AbstractPlayer;
import com.ludot.random.*;
import com.ludot.rules.*;

import java.util.ArrayList;
import java.util.List;

// Creates a ready-to-play game instance with standard defaults for the board, rules, and players.
public class GameBuilder {

    // These defaults provide the normal game components when no custom
    // implementation is supplied.
    private Dice dice = new StandardDice();
    private CoinToss coinToss = new StandardCoinToss();
    private MysteryEffectSelector mysteryEffectSelector = new StandardMysteryEffectSelector();
    private MysteryCellSpawner mysteryCellSpawner = new StandardMysteryCellSpawner();
    private GameLogger logger = new GameLogger();

    // These setters let tests or callers replace any default component before the
    // game is built.
    public GameBuilder withDice(Dice dice) {
        if (dice == null)
            throw new IllegalArgumentException("Dice must not be null.");
        this.dice = dice;
        return this;
    }

    public GameBuilder withCoinToss(CoinToss coinToss) {
        if (coinToss == null)
            throw new IllegalArgumentException("CoinToss must not be null.");
        this.coinToss = coinToss;
        return this;
    }

    public GameBuilder withMysteryEffectSelector(MysteryEffectSelector selector) {
        if (selector == null)
            throw new IllegalArgumentException("MysteryEffectSelector must not be null.");
        this.mysteryEffectSelector = selector;
        return this;
    }

    public GameBuilder withMysteryCellSpawner(MysteryCellSpawner spawner) {
        if (spawner == null)
            throw new IllegalArgumentException("MysteryCellSpawner must not be null.");
        this.mysteryCellSpawner = spawner;
        return this;
    }

    public GameBuilder withLogger(GameLogger logger) {
        if (logger == null)
            throw new IllegalArgumentException("GameLogger must not be null.");
        this.logger = logger;
        return this;
    }

    // Build the complete game object from the configured components.
    public GameEngine build() {
        GameConfig.getInstance().validate();

        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = buildRuleEngine(board);
        List<AbstractPlayer> playerList = buildPlayers(board, ruleEngine);
        TurnManager turnManager = new TurnManager(playerList);
        return new GameEngine(board, ruleEngine, turnManager, logger);
    }

    // This assembles the rule engine used to evaluate moves, captures, blocks, and
    // win conditions.
    private RuleEngine buildRuleEngine(Board board) {
        MovementRule movementRule = new MovementRule(board);
        CaptureRule captureRule = new CaptureRule(board);
        BlockRule blockRule = new BlockRule(board);
        BonusRollRule bonusRollRule = new BonusRollRule();
        WinCondition winCondition = new WinCondition();

        // The mystery rule adds the special teleport and twist effects that can appear
        // during play.
        MysteryRule mysteryRule = new MysteryRule(
                board, mysteryEffectSelector, mysteryCellSpawner, logger);

        // The completed rule engine is returned so the game can evaluate turns
        // consistently.
        return new RuleEngine(
                movementRule, captureRule, blockRule,
                bonusRollRule, mysteryRule, winCondition);
    }

    // This creates the standard four players and gives each one its starting
    // pieces.
    private List<AbstractPlayer> buildPlayers(Board board, RuleEngine ruleEngine) {
        PlayerFactory playerFactory = new PlayerFactory(
                board, ruleEngine, dice, coinToss, logger);

        List<AbstractPlayer> playerList = new ArrayList<>();
        for (Colour colour : new Colour[]{Colour.RED, Colour.GREEN, Colour.YELLOW, Colour.BLUE}) {
            List<Piece> pieces = new ArrayList<>(board.getHomeArea(colour).pieces());
            AbstractPlayer player = playerFactory.createPlayer(colour, pieces);
            playerList.add(player);
        }
        return playerList;
    }
}