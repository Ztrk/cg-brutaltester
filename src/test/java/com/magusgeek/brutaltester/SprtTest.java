package com.magusgeek.brutaltester;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SprtTest {

    /**
     * Assert that actual is within 1% relative tolerance of expected,
     * matching doctest::Approx().epsilon(0.01) semantics from fastchess.
     */
    private static void assertApprox(double expected, double actual) {
        double tolerance = Math.abs(expected) * 0.01;
        assertEquals(expected, actual, tolerance);
    }

    // --- Normalized trinomial ---

    @Test
    public void normalizedTrinomial1() {
        Sprt sprt = new Sprt(0.05, 0.05, 0, 2, Sprt.Model.NORMALIZED);
        double llr = sprt.getLLR(36433, 68692, 36027);
        assertApprox(0.92, llr);
    }

    @Test
    public void normalizedTrinomial2() {
        Sprt sprt = new Sprt(0.05, 0.05, -1.75, 0.25, Sprt.Model.NORMALIZED);
        double llr = sprt.getLLR(10871, 20431, 10650);
        assertApprox(2.30, llr);
    }

    @Test
    public void normalizedTrinomial3() {
        Sprt sprt = new Sprt(0.05, 0.05, 0, 10, Sprt.Model.NORMALIZED);
        double llr = sprt.getLLR(4250, 0, 0);
        assertApprox(120.56, llr);
    }

    // --- Logistic trinomial ---

    @Test
    public void logisticTrinomial1() {
        Sprt sprt = new Sprt(0.05, 0.05, 0.5, 2.5, Sprt.Model.LOGISTIC);
        double llr = sprt.getLLR(21404, 40708, 21184);
        assertApprox(-1.57, llr);
    }

    @Test
    public void logisticTrinomial2() {
        Sprt sprt = new Sprt(0.05, 0.05, 0, 2, Sprt.Model.LOGISTIC);
        double llr = sprt.getLLR(57433, 106593, 57030);
        assertApprox(-2.59, llr);
    }

    // --- Bayesian trinomial ---

    @Test
    public void bayesianTrinomial1() {
        Sprt sprt = new Sprt(0.05, 0.05, 0, 2, Sprt.Model.BAYESIAN);
        double llr = sprt.getLLR(68965, 128429, 68526);
        assertApprox(-1.26, llr);
    }

    @Test
    public void bayesianTrinomial2() {
        Sprt sprt = new Sprt(0.05, 0.05, 0.5, 2.5, Sprt.Model.BAYESIAN);
        double llr = sprt.getLLR(21629, 41111, 21484);
        assertApprox(-1.13, llr);
    }

    // --- Normalized pentanomial ---

    @Test
    public void normalizedPentanomial1() {
        // LL=365, LD=16618, WL=36029, DD=200, WD=16974, WW=390
        Sprt sprt = new Sprt(0.05, 0.05, 0, 2, Sprt.Model.NORMALIZED);
        double llr = sprt.getLLR(390, 16974, 36029, 200, 16618, 365);
        assertApprox(2.25, llr);
    }

    @Test
    public void normalizedPentanomial2() {
        // LL=127, LD=4883, WL=10311, DD=401, WD=5150, WW=104
        Sprt sprt = new Sprt(0.05, 0.05, -1.75, 0.25, Sprt.Model.NORMALIZED);
        double llr = sprt.getLLR(104, 5150, 10311, 401, 4883, 127);
        assertApprox(3.01, llr);
    }

    @Test
    public void normalizedPentanomial3() {
        // LL=0, LD=0, WL=0, DD=0, WD=0, WW=5550
        Sprt sprt = new Sprt(0.05, 0.05, 0, 5, Sprt.Model.NORMALIZED);
        double llr = sprt.getLLR(5550, 0, 0, 0, 0, 0);
        assertApprox(111.82, llr);
    }

    // --- Logistic pentanomial ---

    @Test
    public void logisticPentanomial1() {
        // LL=223, LD=9863, WL=20279, DD=1000, WD=10037, WW=246
        Sprt sprt = new Sprt(0.05, 0.05, 0.5, 2.5, Sprt.Model.LOGISTIC);
        double llr = sprt.getLLR(246, 10037, 20279, 1000, 9863, 223);
        assertApprox(-3.07, llr);
    }

    @Test
    public void logisticPentanomial2() {
        // LL=871, LD=26175, WL=55003, DD=980, WD=26678, WW=821
        Sprt sprt = new Sprt(0.05, 0.05, 0, 2, Sprt.Model.LOGISTIC);
        double llr = sprt.getLLR(821, 26678, 55003, 980, 26175, 871);
        assertApprox(-4.98, llr);
    }

    // --- Validation tests ---

    @Test
    public void validationRejectsElo0GreaterThanElo1() {
        assertThrows(IllegalArgumentException.class, () -> {
            Sprt.validate(0.05, 0.05, 5.0, 2.0, "logistic", false);
        });
    }

    @Test
    public void validationRejectsAlphaPlusBetaGreaterThanOne() {
        assertThrows(IllegalArgumentException.class, () -> {
            Sprt.validate(0.6, 0.6, 0, 5, "logistic", false);
        });
    }

    @Test
    public void validationRejectsInvalidModel() {
        assertThrows(IllegalArgumentException.class, () -> {
            Sprt.validate(0.05, 0.05, 0, 5, "invalid", false);
        });
    }

    @Test
    public void validationRejectsAlphaOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> {
            Sprt.validate(1.0, 0.05, 0, 5, "logistic", false);
        });
    }

    @Test
    public void validationRejectsBetaOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> {
            Sprt.validate(0.05, 1.0, 0, 5, "logistic", false);
        });
    }

    // --- Result determination ---

    @Test
    public void resultH1WhenLlrAboveUpper() {
        Sprt sprt = new Sprt(0.05, 0.05, 0, 5, Sprt.Model.LOGISTIC);
        assertEquals(Sprt.Result.H1, sprt.getResult(sprt.getUpperBound()));
        assertEquals(Sprt.Result.H1, sprt.getResult(sprt.getUpperBound() + 1));
    }

    @Test
    public void resultH0WhenLlrBelowLower() {
        Sprt sprt = new Sprt(0.05, 0.05, 0, 5, Sprt.Model.LOGISTIC);
        assertEquals(Sprt.Result.H0, sprt.getResult(sprt.getLowerBound()));
        assertEquals(Sprt.Result.H0, sprt.getResult(sprt.getLowerBound() - 1));
    }

    @Test
    public void resultContinueWhenLlrBetweenBounds() {
        Sprt sprt = new Sprt(0.05, 0.05, 0, 5, Sprt.Model.LOGISTIC);
        assertEquals(Sprt.Result.CONTINUE, sprt.getResult(0.0));
    }
}
