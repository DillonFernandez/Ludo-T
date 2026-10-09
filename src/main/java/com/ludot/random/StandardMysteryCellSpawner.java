package com.ludot.random;

import com.ludot.config.GameConfig;
import com.ludot.exception.GameConfigurationException;

import java.util.*;

// Chooses a free main-path position for the next mystery cell.
public class StandardMysteryCellSpawner implements MysteryCellSpawner {

    // Random source used to choose among the valid candidates.
    private final Random random;

    // Uses the default random seed for normal game play.
    public StandardMysteryCellSpawner() {
        this.random = new Random();
    }

    // Uses a fixed seed so tests can repeat the same outcomes.
    public StandardMysteryCellSpawner(long seed) {
        this.random = new Random(seed);
    }

    // Uses the last position as a preference, but falls back to any free cell.
    // A configuration error is raised if every main-path cell is already occupied.
    @Override
    public int spawn(int previousIndex, Set<Integer> occupiedIndices) {
        Set<Integer> blocked = (occupiedIndices != null) ? occupiedIndices : Collections.emptySet();

        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < GameConfig.STANDARD_PATH_SIZE; i++) {
            if (!blocked.contains(i)) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) {
            throw new GameConfigurationException(
                    "Cannot spawn a mystery cell: every standard path cell is occupied.");
        }

        List<Integer> preferred = new ArrayList<>(candidates);
        if (previousIndex >= 0) {
            preferred.remove(Integer.valueOf(previousIndex));
        }

        List<Integer> pool = preferred.isEmpty() ? candidates : preferred;
        return pool.get(random.nextInt(pool.size()));
    }
}