package com.magusgeek.brutaltester;

import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;

/**
 * Sequential Probability Ratio Test (SPRT) implementation.
 * Ported from fastchess (C++) — supports logistic, normalized, and bayesian models,
 * with both trinomial (W/D/L) and pentanomial (game-pair) statistics.
 *
 * References:
 * - Michel Van den Bergh, "Comments on Normalized Elo"
 *   https://www.cantate.be/Fishtest/normalized_elo_practical.pdf
 * - I.F.D. Oliveira, R.H.C. Takahashi, "An Enhancement of the Bisection Method
 *   Average Performance Preserving Minmax Optimality", ACM Trans. Math. Softw. 47(1), 2021.
 */
public class Sprt {

    public enum Result { H0, H1, CONTINUE }

    public enum Model {
        LOGISTIC, NORMALIZED, BAYESIAN;

        public static Model fromString(String s) {
            switch (s.toLowerCase()) {
                case "logistic":   return LOGISTIC;
                case "normalized": return NORMALIZED;
                case "bayesian":   return BAYESIAN;
                default: throw new IllegalArgumentException("Invalid SPRT model: " + s
                        + ". Must be one of: logistic, normalized, bayesian");
            }
        }
    }

    private final double lower;
    private final double upper;
    private final double elo0;
    private final double elo1;
    private final Model model;

    public Sprt(double alpha, double beta, double elo0, double elo1, Model model) {
        this.lower = Math.log(beta / (1.0 - alpha));
        this.upper = Math.log((1.0 - beta) / alpha);
        this.elo0 = elo0;
        this.elo1 = elo1;
        this.model = model;
    }

    /**
     * Validate SPRT parameters. Returns true if pentanomial should be used
     * (may be disabled if bayesian model is requested).
     */
    public static boolean validate(double alpha, double beta, double elo0, double elo1,
                                   String modelStr, boolean requestedPenta) {
        if (elo0 >= elo1) {
            throw new IllegalArgumentException("SPRT: elo0 must be less than elo1");
        }
        if (alpha <= 0 || alpha >= 1) {
            throw new IllegalArgumentException("SPRT: alpha must be a decimal number between 0 and 1 (exclusive)");
        }
        if (beta <= 0 || beta >= 1) {
            throw new IllegalArgumentException("SPRT: beta must be a decimal number between 0 and 1 (exclusive)");
        }
        if (alpha + beta >= 1) {
            throw new IllegalArgumentException("SPRT: sum of alpha and beta must be less than 1");
        }
        Model.fromString(modelStr); // throws if invalid

        if (modelStr.equalsIgnoreCase("bayesian") && requestedPenta) {
            System.err.println("Warning: Bayesian SPRT model not available with pentanomial statistics. "
                    + "Disabling pentanomial reports...");
            return false;
        }
        return requestedPenta;
    }

    // --- Elo-to-score conversions ---

    static double leloToScore(double lelo) {
        return 1.0 / (1.0 + Math.pow(10.0, -lelo / 400.0));
    }

    static double bayeseloToScore(double bayeselo, double drawelo) {
        double pwin  = 1.0 / (1.0 + Math.pow(10.0, (-bayeselo + drawelo) / 400.0));
        double ploss = 1.0 / (1.0 + Math.pow(10.0, (bayeselo + drawelo) / 400.0));
        double pdraw = 1.0 - pwin - ploss;
        return pwin + 0.5 * pdraw;
    }

    static double neloToScoreWDL(double nelo, double variance) {
        return nelo * Math.sqrt(variance) / (800.0 / Math.log(10.0)) + 0.5;
    }

    static double neloToScorePenta(double nelo, double variance) {
        return nelo * Math.sqrt(2.0 * variance) / (800.0 / Math.log(10.0)) + 0.5;
    }

    // --- Regularization ---

    private static double regularize(int value) {
        return value == 0 ? 1e-3 : value;
    }

    // --- LLR computation (trinomial) ---

    public double getLLR(int wins, int draws, int losses) {
        double L = regularize(losses);
        double D = regularize(draws);
        double W = regularize(wins);
        double total = L + D + W;
        double[] probs = { L / total, D / total, W / total };
        double[] scores = { 0.0, 0.5, 1.0 };

        switch (model) {
            case NORMALIZED: {
                double t0 = elo0 / (800.0 / Math.log(10.0));
                double t1 = elo1 / (800.0 / Math.log(10.0));
                return getLLR_normalized(total, scores, probs, t0, t1);
            }
            case BAYESIAN: {
                if (wins == 0 || losses == 0) return 0.0;
                double pL = probs[0];
                double pW = probs[2];
                double drawelo = 200.0 * Math.log10((1.0 - pL) / pL * (1.0 - pW) / pW);
                double score0 = bayeseloToScore(elo0, drawelo);
                double score1 = bayeseloToScore(elo1, drawelo);
                return getLLR_logistic(total, scores, probs, score0, score1);
            }
            default: { // LOGISTIC
                double score0 = leloToScore(elo0);
                double score1 = leloToScore(elo1);
                return getLLR_logistic(total, scores, probs, score0, score1);
            }
        }
    }

    // --- LLR computation (pentanomial) ---

    public double getLLR(int pentaWW, int pentaWD, int pentaWL, int pentaDD, int pentaLD, int pentaLL) {
        double LL    = regularize(pentaLL);
        double LD    = regularize(pentaLD);
        double WL_DD = regularize(pentaDD + pentaWL);
        double WD    = regularize(pentaWD);
        double WW    = regularize(pentaWW);
        double total = WW + WD + WL_DD + LD + LL;
        double[] probs = { LL / total, LD / total, WL_DD / total, WD / total, WW / total };
        double[] scores = { 0.0, 0.25, 0.5, 0.75, 1.0 };

        switch (model) {
            case NORMALIZED: {
                double t0 = Math.sqrt(2.0) * elo0 / (800.0 / Math.log(10.0));
                double t1 = Math.sqrt(2.0) * elo1 / (800.0 / Math.log(10.0));
                return getLLR_normalized(total, scores, probs, t0, t1);
            }
            default: { // LOGISTIC (bayesian not supported for pentanomial)
                double score0 = leloToScore(elo0);
                double score1 = leloToScore(elo1);
                return getLLR_logistic(total, scores, probs, score0, score1);
            }
        }
    }

    // --- Result determination ---

    public Result getResult(double llr) {
        if (llr >= upper) return Result.H1;
        if (llr <= lower) return Result.H0;
        return Result.CONTINUE;
    }

    public double getFraction(double llr) {
        return llr >= 0 ? llr / upper : -llr / lower;
    }

    public String getBounds() {
        return String.format("(%.2f, %.2f)", lower, upper);
    }

    public String getElo() {
        return String.format("[%.2f, %.2f]", elo0, elo1);
    }

    public double getLowerBound() { return lower; }
    public double getUpperBound() { return upper; }
    public Model getModel() { return model; }

    // --- ITP root-finding method ---
    // Oliveira & Takahashi (2020), ACM Trans. Math. Softw.

    private static double itp(DoubleUnaryOperator f, double a, double b,
                              double f_a, double f_b,
                              double k1, double k2, double n0, double epsilon) {
        if (f_a > 0) {
            double tmp;
            tmp = a; a = b; b = tmp;
            tmp = f_a; f_a = f_b; f_b = tmp;
        }

        double n_half = Math.ceil(Math.log(Math.abs(b - a) / (2.0 * epsilon)) / Math.log(2.0));
        double n_max = n_half + n0;

        for (int i = 0; Math.abs(b - a) > 2.0 * epsilon; i++) {
            double x_half = (a + b) / 2.0;
            double r = epsilon * Math.pow(2.0, n_max - i) - (b - a) / 2.0;
            double delta = k1 * Math.pow(b - a, k2);

            double x_f = (f_b * a - f_a * b) / (f_b - f_a);

            double sigma = Math.signum(x_half - x_f);
            double x_t = delta <= Math.abs(x_half - x_f) ? x_f + sigma * delta : x_half;

            double x_itp = Math.abs(x_t - x_half) <= r ? x_t : x_half - sigma * r;

            double f_itp = f.applyAsDouble(x_itp);
            if (f_itp == 0.0) {
                a = x_itp;
                b = x_itp;
            } else if (f_itp < 0) {
                a = x_itp;
                f_a = f_itp;
            } else {
                b = x_itp;
                f_b = f_itp;
            }
        }

        return (a + b) / 2.0;
    }

    // --- MLE: Logistic model ---
    // Compute the maximum likelihood estimate for a discrete probability distribution with an expectation of s,
    // given an empirical distribution. See proposition 1.1 of [1] for details.
    //
    // [1]: Michel Van den Bergh, Comments on Normalized Elo,
    // https://www.cantate.be/Fishtest/normalized_elo_practical.pdf

    private static double getLLR_logistic(double total, double[] scores, double[] probs,
                                          double s0, double s1) {
        int n = scores.length;

        double[] p0 = mleLogistic(scores, probs, s0);
        double[] p1 = mleLogistic(scores, probs, s1);

        double[] lpr = new double[n];
        for (int i = 0; i < n; i++) {
            lpr[i] = Math.log(p1[i]) - Math.log(p0[i]);
        }
        return total * mean(lpr, probs);
    }

    private static double[] mleLogistic(double[] scores, double[] probs, double s) {
        int n = scores.length;
        double thetaEpsilon = 1e-3;

        // Solve equation 1.3 in [1] for theta.
        double minTheta = -1.0 / (scores[n - 1] - s);
        double maxTheta = -1.0 / (scores[0] - s);

        double theta = itp(
                x -> {
                    double result = 0.0;
                    for (int i = 0; i < n; i++) {
                        result += probs[i] * (scores[i] - s) / (1.0 + x * (scores[i] - s));
                    }
                    return result;
                },
                minTheta, maxTheta,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                0.1, 2.0, 0.99, thetaEpsilon);

        // ML distribution is given by equation 1.2 in [1].
        double[] p = new double[n];
        for (int i = 0; i < n; i++) {
            p[i] = probs[i] / (1.0 + theta * (scores[i] - s));
        }
        return p;
    }

    // --- MLE: Normalized model ---
    // Compute the maximum likelihood estimate for a discrete probability distribution that has t = (mu - mu_ref) /
    // sigma, given an empirical distribution. See section 4.1 of [1] for details.
    //
    // [1]: Michel Van den Bergh, Comments on Normalized Elo,
    // https://www.cantate.be/Fishtest/normalized_elo_practical.pdf

    private static double getLLR_normalized(double total, double[] scores, double[] probs,
                                             double t0, double t1) {
        int n = scores.length;

        double[] p0 = mleNormalized(scores, probs, 0.5, t0);
        double[] p1 = mleNormalized(scores, probs, 0.5, t1);

        double[] lpr = new double[n];
        for (int i = 0; i < n; i++) {
            lpr[i] = Math.log(p1[i]) - Math.log(p0[i]);
        }
        return total * mean(lpr, probs);
    }

    private static double[] mleNormalized(double[] scores, double[] probs,
                                           double muRef, double tStar) {
        int n = scores.length;
        double thetaEpsilon = 1e-7;
        double mleEpsilon = 1e-4;

        // This is an iterative method, so we need to start with an initial value. As suggested in [1], we start with a
        // uniform distribution.
        double[] p = new double[n];
        Arrays.fill(p, 1.0 / n);

        for (int iter = 0; iter < 10; iter++) {
            double mu = mean(scores, p);
            double sigma = Math.sqrt(variance(scores, p, mu));

            // Calculate phi.
            double[] phi = new double[n];
            for (int i = 0; i < n; i++) {
                double ai = scores[i];
                phi[i] = ai - muRef - 0.5 * tStar * sigma
                        * (1.0 + ((ai - mu) / sigma) * ((ai - mu) / sigma));
            }

            // We need to find a subset of the possible solutions for theta,
            // so we need to calculate our constraints for theta.
            double u = phi[0], v = phi[0];
            for (int i = 1; i < n; i++) {
                if (phi[i] < u) u = phi[i];
                if (phi[i] > v) v = phi[i];
            }
            double minTheta = -1.0 / v;
            double maxTheta = -1.0 / u;

            // Solve equation 4.9 in [1] for theta.
            final double[] phiFinal = phi;
            double theta = itp(
                    x -> {
                        double result = 0.0;
                        for (int i = 0; i < n; i++) {
                            result += probs[i] * phiFinal[i] / (1.0 + x * phiFinal[i]);
                        }
                        return result;
                    },
                    minTheta, maxTheta,
                    Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    0.1, 2.0, 0.99, thetaEpsilon);

            // Update p
            double maxDiff = 0.0;
            for (int i = 0; i < n; i++) {
                double newP = probs[i] / (1.0 + theta * phi[i]);
                maxDiff = Math.max(maxDiff, Math.abs(newP - p[i]));
                p[i] = newP;
            }

            if (maxDiff < mleEpsilon) break;
        }

        return p;
    }

    // --- Helpers ---

    private static double mean(double[] x, double[] p) {
        double result = 0.0;
        for (int i = 0; i < x.length; i++) result += x[i] * p[i];
        return result;
    }

    private static double variance(double[] x, double[] p, double mu) {
        double result = 0.0;
        for (int i = 0; i < x.length; i++) result += p[i] * (x[i] - mu) * (x[i] - mu);
        return result;
    }
}
