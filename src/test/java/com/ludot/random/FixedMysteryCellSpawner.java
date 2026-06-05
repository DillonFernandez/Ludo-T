package com.ludot.random;

import java.util.Set;

// A fixed mystery-cell position keeps tests deterministic.
public final class FixedMysteryCellSpawner implements MysteryCellSpawner {

    // Stores the mystery-cell index returned by this test helper.
    private final int index;

    public FixedMysteryCellSpawner(int index) {
        this.index = index;
    }

    @Override
    public int spawn(int previousIndex, Set<Integer> occupiedIndices) {
        return index;
    }
}
