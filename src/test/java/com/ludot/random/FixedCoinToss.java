package com.ludot.random;

// A fixed coin toss is useful when tests need the same result every time.
public final class FixedCoinToss implements CoinToss {

    // Stores the coin side returned by this test helper.
    private final CoinFace face;

    public FixedCoinToss(CoinFace face) {
        this.face = face;
    }

    @Override
    public CoinFace toss() {
        return face;
    }
}
