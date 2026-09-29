package com.ludot.random;

import com.ludot.model.MysteryEffect;

// A fixed mystery effect keeps tests deterministic.
public final class FixedMysteryEffectSelector implements MysteryEffectSelector {

    // Stores the effect returned by this test helper.
    private final MysteryEffect effect;

    public FixedMysteryEffectSelector(MysteryEffect effect) {
        this.effect = effect;
    }

    @Override
    public MysteryEffect selectEffect() {
        return effect;
    }
}
