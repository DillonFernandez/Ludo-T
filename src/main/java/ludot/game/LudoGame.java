// Controls starting order, player turns, completed rounds and the end of a simulation.
package ludot.game;

import java.util.ArrayList;
import java.util.List;
import ludot.behaviour.PlayerBehaviour;
import ludot.config.GameSettings;
import ludot.model.Board;
import ludot.model.Colour;
import ludot.model.Piece;
import ludot.model.Player;
import ludot.movement.BlockBreaker;
import ludot.movement.MoveExecutor;
import ludot.movement.MoveRules;
import ludot.mystery.MysteryCell;
import ludot.output.GameMessages;
import ludot.random.RandomSource;

public final class LudoGame {
    private final GameSettings settings = GameSettings.getInstance();
    private final Board board;
    private final RandomSource random;
    private final GameMessages messages;
    private final MysteryCell mystery;
    private final MoveExecutor executor;
    private final TurnRunner turns;
    private final PlayerBehaviour[] behaviours;
    private int completedRounds;
    private boolean started;
    private boolean finished;
    private Player[] order;

    public LudoGame(Board board, RandomSource random,
            GameMessages messages, GameFactory factory) {
        this.board = board;
        this.random = random;
        this.messages = messages;
        mystery = new MysteryCell(board, random, messages);
        executor = new MoveExecutor(board, random, messages, mystery);
        MoveRules rules = new MoveRules(board);
        BlockBreaker breaker = new BlockBreaker(board, rules, executor);
        turns = new TurnRunner(random, rules, executor, breaker);
        behaviours = new PlayerBehaviour[settings.getPlayerCount()];
        for (Player player : board.getPlayers()) {
            behaviours[player.getColour().ordinal()] = factory.createBehaviour(player, board, mystery);
        }
    }

    public void play() {
        start();
        while (!finished) {
            playNextRound();
        }
    }

    public void start() {
        if (started) {
            throw new IllegalStateException("This simulation has already started.");
        }
        started = true;
        for (Player player : board.getPlayers()) {
            messages.introduce(player);
        }
        Player first = chooseStartingPlayer();
        order = orderFrom(first);
        messages.startingPlayer(order);
    }

    public void playNextRound() {
        if (!started) {
            throw new IllegalStateException("The simulation has not started.");
        }
        if (finished) {
            return;
        }
        if (completedRounds < settings.getMaximumRounds()) {
            int round = completedRounds + 1;
            messages.roundStarted(round);
            executor.setRound(round);
            for (int index = 0; index < order.length; index++) {
                Player player = order[index];
                turns.playTurn(player, behaviours[player.getColour().ordinal()]);
                if (player.hasWon()) {
                    if (index == order.length - 1) {
                        completedRounds++;
                        finishRound();
                    }
                    messages.winner(player);
                    finished = true;
                    return;
                }
            }
            completedRounds++;
            finishRound();
        }
        if (completedRounds == settings.getMaximumRounds()) {
            messages.noWinner();
            finished = true;
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public int getMysteryLocation() {
        return mystery.getLocation();
    }

    private void finishRound() {
        for (Player player : board.getPlayers()) {
            for (Piece piece : player.getPieces()) {
                piece.finishRound(completedRounds);
            }
        }
        mystery.finishRound(completedRounds);
        messages.roundStatus(board, mystery);
    }

    private Player chooseStartingPlayer() {
        List<Player> contenders = new ArrayList<>();
        for (Player player : board.getPlayers()) {
            contenders.add(player);
        }
        while (contenders.size() > 1) {
            List<Player> highestPlayers = new ArrayList<>();
            int highest = 0;
            for (Player player : contenders) {
                int roll = random.nextInt(settings.getDiceFaces()) + 1;
                messages.startingRoll(player, roll);
                if (roll > highest) {
                    highest = roll;
                    highestPlayers.clear();
                }
                if (roll == highest) {
                    highestPlayers.add(player);
                }
            }
            contenders = highestPlayers;
        }
        return contenders.get(0);
    }

    private Player[] orderFrom(Player first) {
        Player[] players = board.getPlayers();
        Player[] order = new Player[players.length];
        for (int offset = 0; offset < order.length; offset++) {
            Colour colour = Colour.values()[(first.getColour().ordinal() + offset)
                    % order.length];
            for (Player player : players) {
                if (player.getColour() == colour) {
                    order[offset] = player;
                }
            }
        }
        return order;
    }

    public int getCompletedRounds() {
        return completedRounds;
    }

    public Board getBoard() {
        return board;
    }
}
