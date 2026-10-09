package com.ludot.engine;

import com.ludot.model.Colour;
import com.ludot.player.AbstractPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Tracks the current player order and advances turns around the circle.
public class TurnManager {

    // The turn manager stores the player list and the current index.
    private final List<AbstractPlayer> players;
    private int currentIndex;

    // The constructor validates the player list and keeps a safe copy for later
    // use.
    public TurnManager(List<AbstractPlayer> players) {
        if (players == null)
            throw new IllegalArgumentException("Player list must not be null.");
        if (players.isEmpty())
            throw new IllegalArgumentException("Player list must not be empty.");
        for (AbstractPlayer p : players) {
            if (p == null)
                throw new IllegalArgumentException("Player list must not contain null players.");
        }
        this.players = new ArrayList<>(players);
        this.currentIndex = 0;
    }

    // These accessors expose the current player order and the active turn.
    public List<AbstractPlayer> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public AbstractPlayer getCurrentPlayer() {
        return players.get(currentIndex);
    }

    public int getCurrentPlayerIndex() {
        return currentIndex;
    }

    // This returns the turn order for the current round, which is useful for
    // logging.
    public List<Colour> getRoundOrderColours() {
        List<Colour> order = new ArrayList<>();
        for (int offset = 0; offset < players.size(); offset++) {
            AbstractPlayer player = players.get((currentIndex + offset) % players.size());
            order.add(player.getColour());
        }
        return order;
    }

    // This advances the turn in a circular order so the game wraps correctly.
    public void moveToNextPlayer() {
        currentIndex = (currentIndex + 1) % players.size();
    }

    // This moves the turn directly to a specific player, but only if that player
    // belongs to this manager.
    public void moveToPlayer(AbstractPlayer player) {
        if (player == null)
            throw new IllegalArgumentException("Player must not be null.");
        int index = players.indexOf(player);
        if (index < 0)
            throw new IllegalArgumentException(
                    "Player " + player.getColour() + " is not in the turn manager.");
        currentIndex = index;
    }
}