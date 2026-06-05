package com.ludot.random;

import com.ludot.model.MysteryEffect;

// Chooses the effect applied when a piece lands on a mystery cell.
public interface MysteryEffectSelector {

    // Returns one mystery effect for the current turn.
    MysteryEffect selectEffect();
}