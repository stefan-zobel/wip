package misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BlackScholesNumericalTest {

    private static final double STRIKE = 100.0;
    private static final double MATURITY = 1.0;
    private static final double RATE = 0.05;
    private static final double VOLATILITY = 0.2;

    /**
     * The Crank-Nicolson grid must reproduce the closed-form price. The measured error at the
     * default resolution is 6.1e-4, dominated by truncating the domain at S_MAX = 3 * K, so the
     * tolerance leaves some room while still catching a wrong discretization.
     */
    @Test
    public void matchesClosedForm() {
        double numeric = BlackScholesNumerical.price(100.0);
        double exact = BlackScholesNumerical.blackScholesCall(100.0, STRIKE, MATURITY, RATE, VOLATILITY);
        assertEquals("Crank-Nicolson price", exact, numeric, 1.0e-3);
    }

    /** A known textbook value, independent of the grid: S = K = 100, T = 1, r = 5%, sigma = 20%. */
    @Test
    public void closedFormMatchesAKnownValue() {
        double exact = BlackScholesNumerical.blackScholesCall(100.0, STRIKE, MATURITY, RATE, VOLATILITY);
        assertEquals(10.450584, exact, 1.0e-5);
    }

    /** Deep in the money the call is worth at least its discounted intrinsic value. */
    @Test
    public void deepInTheMoneyApproachesIntrinsicValue() {
        double spot = 200.0;
        double numeric = BlackScholesNumerical.price(spot);
        double intrinsic = spot - STRIKE * Math.exp(-RATE * MATURITY);
        assertTrue("price " + numeric + " must be at least the discounted intrinsic value " + intrinsic,
                numeric >= intrinsic - 1.0e-2);
    }

    /** Below the strike the call must still be worth something, but less than the spot. */
    @Test
    public void outOfTheMoneyIsPositiveAndBelowSpot() {
        double spot = 60.0;
        double numeric = BlackScholesNumerical.price(spot);
        assertTrue("price must be positive, was " + numeric, numeric > 0.0);
        assertTrue("price must stay below the spot, was " + numeric, numeric < spot);
    }
}
