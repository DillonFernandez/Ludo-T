package com.ludot.random;

import java.util.Set;

// Chooses a free main-path position for the next mystery cell.
public interface MysteryCellSpawner {

    // Returns a main-path index that is not already occupied.
    // previousIndex remembers the last used position, and occupiedIndices
    // prevents reusing a cell that already contains a piece.
    int spawn(int previousIndex, Set<Integer> occupiedIndices);
}