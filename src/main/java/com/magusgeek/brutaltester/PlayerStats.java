package com.magusgeek.brutaltester;

import java.util.HashMap;
import java.util.Map;

public class PlayerStats {
	private static final int VICTORY = 0;
	private static final int DEFEAT = 1;
	private static final int DRAW = 2;

	private int[][][] stats;
	private int[][] global;
	private int n;
	private int total;

	// SPRT fields
	private Sprt sprt;
	private volatile boolean stopped;
	private boolean usePentanomial;
	private int pentaWW, pentaWD, pentaWL, pentaDD, pentaLD, pentaLL;
	private final Map<Integer, int[]> gamePairCache = new HashMap<>();
	private double lastLLR;
	private Sprt.Result lastResult = Sprt.Result.CONTINUE;

	public PlayerStats(int n, Sprt sprt, boolean usePentanomial) {
		this.n = n;
		total = 0;
		stats = new int[n][n][3];
		global = new int[n][3];
		this.sprt = sprt;
		this.usePentanomial = usePentanomial;
	}

	public boolean isStopped() {
		return stopped;
	}

	synchronized public void add(int[] scores) {
		add(scores, -1);
	}

	synchronized public void add(int[] scores, int seed) {
		for (int i = 0; i < n; ++i) {
			for (int j = i + 1; j < n; ++j) {
				if (scores[i] > scores[j]) {
					stats[i][j][VICTORY] += 1;
					stats[j][i][DEFEAT] += 1;
					global[i][VICTORY] += 1;
					global[j][DEFEAT] += 1;
				} else if (scores[i] < scores[j]) {
					stats[j][i][VICTORY] += 1;
					stats[i][j][DEFEAT] += 1;
					global[j][VICTORY] += 1;
					global[i][DEFEAT] += 1;
				} else {
					stats[i][j][DRAW] += 1;
					stats[j][i][DRAW] += 1;
					global[i][DRAW] += 1;
					global[j][DRAW] += 1;
				}
			}
		}

		total += 1;
		if (sprt != null && n == 2) {
			int p1result = scores[0] > scores[1] ? VICTORY : (scores[0] < scores[1] ? DEFEAT : DRAW);
			updateSprtStats(p1result, seed);
		}
	}

	synchronized public void add(String line) {
		add(line, -1);
	}

	synchronized public void add(String line, int seed) {
		String[] params = line.split(" ");

		int[] scores = new int[n];

		for (int i = 1; i < params.length; ++i) {
			for (char c : params[i].toCharArray()) {
				scores[Character.getNumericValue(c)] = -i + 1;
			}
		}

		add(scores, seed);
	}

	/**
	 * Check SPRT after a game. p1outcome is VICTORY/DEFEAT/DRAW for player 0 vs player 1.
	 * seed is used for pentanomial pairing (-1 if not applicable).
	 */
	private void updateSprtStats(int p1outcome, int seed) {
		if (usePentanomial && seed >= 0) {
			if (!gamePairCache.containsKey(seed)) {
				// First game of pair — cache individual game outcome
				gamePairCache.put(seed, new int[]{ p1outcome });
				return;
			} else {
				// Second game of pair — classify into pentanomial bucket
				int[] cached = gamePairCache.remove(seed);
				int g1 = cached[0]; // first game outcome
				int g2 = p1outcome; // second game outcome
				updatePentanomialStats(g1, g2);
			}
		}

		computeAndCheckLLR();
	}

	private void updatePentanomialStats(int g1, int g2) {
		// Count wins and losses across the pair
		int pairWins = (g1 == VICTORY ? 1 : 0) + (g2 == VICTORY ? 1 : 0);
		int pairLosses = (g1 == DEFEAT ? 1 : 0) + (g2 == DEFEAT ? 1 : 0);
		int pairDraws = (g1 == DRAW ? 1 : 0) + (g2 == DRAW ? 1 : 0);

		if (pairWins == 2) pentaWW++;
		else if (pairWins == 1 && pairDraws == 1) pentaWD++;
		else if (pairWins == 1 && pairLosses == 1) pentaWL++;
		else if (pairDraws == 2) pentaDD++;
		else if (pairLosses == 1 && pairDraws == 1) pentaLD++;
		else if (pairLosses == 2) pentaLL++;
	}

	private void computeAndCheckLLR() {
		int w = stats[0][1][VICTORY];
		int l = stats[0][1][DEFEAT];
		int d = stats[0][1][DRAW];

		if (usePentanomial) {
			// Only compute when we have at least one completed pair
			if (pentaWW + pentaWD + pentaWL + pentaDD + pentaLD + pentaLL == 0) return;
			lastLLR = sprt.getLLR(pentaWW, pentaWD, pentaWL, pentaDD, pentaLD, pentaLL);
		} else {
			lastLLR = sprt.getLLR(w, d, l);
		}
		lastResult = sprt.getResult(lastLLR);
		if (lastResult != Sprt.Result.CONTINUE) {
			stopped = true;
		}
	}

	private String percent(float amount) {
		return String.format("%.2f", amount * 100.0 / total) + "%";
	}

	private String formatSprt() {
		if (sprt == null) return "";
		return String.format("LLR: %.2f (%.1f%%)", lastLLR, sprt.getFraction(lastLLR) * 100.0);
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();

		for (int i = 0; i < n; ++i) {
			sb.append(" ").append(percent(global[i][VICTORY] / ((float) n - 1)));
		}

		if (sprt != null) {
			sb.append("\t").append(formatSprt());
		}

		return sb.toString();
	}

	synchronized public void print() {
		/*
        +----------+----------+----------+----------+----------+
        | Results  | Player 1 | Player 2 | Player 3 | Player 4 |
        +----------+----------+----------+----------+----------+
        | Player 1 |          |  42%     | 100%     |  32.46%  |
        +----------+----------+----------+----------+----------+
        | Player 2 | 42.36%   |          | 100%     |  100%    |
        +----------+----------+----------+----------+----------+
        | Player 3 | 42.36%   |  99.99%  |          |  100%    |
        +----------+----------+----------+----------+----------+
        | Player 4 | 42.36%   |  99.99%  | 17.8%    |          |
        +----------+----------+----------+----------+----------+
        */
		
		String separator = "";

		if (n == 2) {
			separator = "+----------+----------+----------+";
		} else if (n == 3) {
			separator = "+----------+----------+----------+----------+";
		} else if (n == 4) {
			separator = "+----------+----------+----------+----------+----------+";
		}

		System.out.println(separator);
		System.out.print("| Results  |");

		for (int i = 0; i < n; ++i) {
			System.out.print(" Player " + (i + 1) + " |");
		}
		System.out.println();
		System.out.println(separator);

		for (int i = 0; i < n; ++i) {
			System.out.print("| Player " + (i + 1) + " |");

			for (int j = 0; j < n; ++j) {
				String result = "";

				if (i != j) {
					result = percent(stats[i][j][VICTORY]);
				}

				System.out.print(" " + result + "         ".substring(result.length()) + "|");
			}

			System.out.println();
			System.out.println(separator);
		}

		int w = stats[0][1][VICTORY];
		int l = stats[0][1][DEFEAT];
		int d = stats[0][1][DRAW];
		System.out.printf("Games: %d, Wins: %d, Losses: %d, Draws: %d%n", w + l + d, w, l, d);

		printSprt();
	}

	public void printSprt() {
		if (sprt == null) return;

		if (usePentanomial) {
			double wl_dd_ratio = pentaWL / (double)(pentaDD);
			System.out.printf("Pentanomial: [%d, %d, %d, %d, %d], WL/DD Ratio: %.2f%n",
					pentaLL, pentaLD, pentaWL + pentaDD, pentaWD, pentaWW, wl_dd_ratio);
		}

		double fraction = sprt.getFraction(lastLLR);
		System.out.printf("LLR: %.2f (%.1f%%) %s %s%n", lastLLR, fraction * 100.0, sprt.getBounds(), sprt.getElo());

		if (lastResult == Sprt.Result.H1) {
			System.out.println("SPRT completed - H1 was accepted");
		} else if (lastResult == Sprt.Result.H0) {
			System.out.println("SPRT completed - H0 was accepted");
		} else {
			System.out.println("SPRT completed - result inconclusive");
		}
	}
}
