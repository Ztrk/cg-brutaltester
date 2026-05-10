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

	// TODO: Refactor
	// SPRT fields
	private Sprt sprt;
	private volatile boolean stopped;
	private boolean usePentanomial;
	private int pentaWW, pentaWD, pentaWL, pentaDD, pentaLD, pentaLL;
	private final Map<Integer, int[]> gamePairCache = new HashMap<>();
	private double lastLLR;
	private Sprt.Result lastResult = Sprt.Result.CONTINUE;

	public PlayerStats(int n) {
		this.n = n;
		total = 0;
		stats = new int[n][n][3];
		global = new int[n][3];
	}

	public void setSprt(Sprt sprt, boolean usePentanomial) {
		this.sprt = sprt;
		this.usePentanomial = usePentanomial;
	}

	public boolean isStopped() {
		return stopped;
	}

	public double getLastLLR() {
		return lastLLR;
	}

	public Sprt.Result getLastResult() {
		return lastResult;
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
		int p1result = addStringInternal(line);
		if (sprt != null && n == 2) {
			updateSprtStats(p1result, seed);
		}
	}

	private int addStringInternal(String line) {
		String[] params = line.split(" ");

		int[] positions = new int[n];

		for (int i = 1; i < params.length; ++i) {
			for (char c : params[i].toCharArray()) {
				positions[Character.getNumericValue(c)] = i - 1;
			}
		}

		for (int i = 0; i < n; ++i) {
			for (int j = i + 1; j < n; ++j) {
				if (positions[i] < positions[j]) {
					stats[i][j][VICTORY] += 1;
					stats[j][i][DEFEAT] += 1;
					global[i][VICTORY] += 1;
					global[j][DEFEAT] += 1;
				} else if (positions[i] > positions[j]) {
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

		// Return p1's result vs p2 (only meaningful for 2-player)
		if (n == 2) {
			if (positions[0] < positions[1]) return VICTORY;
			if (positions[0] > positions[1]) return DEFEAT;
			return DRAW;
		}
		return -1;
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

	public String toString() {
		StringBuffer sb = new StringBuffer();

		for (int i = 0; i < n; ++i) {
			sb.append(" ").append(percent(global[i][VICTORY] / ((float) n - 1)));
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

		printSprt();
	}

	public void printSprt() {
		if (sprt == null) return;

		int w = stats[0][1][VICTORY];
		int l = stats[0][1][DEFEAT];
		int d = stats[0][1][DRAW];

		double llr;
		if (usePentanomial) {
			llr = sprt.getLLR(pentaWW, pentaWD, pentaWL, pentaDD, pentaLD, pentaLL);
		} else {
			llr = sprt.getLLR(w, d, l);
		}
		Sprt.Result result = sprt.getResult(llr);
		double fraction = sprt.getFraction(llr);

		System.out.println();
		System.out.printf("SPRT: LLR: %.2f (%.1f%%) %s %s%n",
				llr, fraction * 100.0, sprt.getBounds(), sprt.getElo());
		System.out.printf("Games: %d  W: %d  L: %d  D: %d%n", w + l + d, w, l, d);
		if (usePentanomial) {
			System.out.printf("Pentanomial: [%d, %d, %d, %d, %d]%n",
					pentaLL, pentaLD, pentaWL + pentaDD, pentaWD, pentaWW);
		}
		if (result == Sprt.Result.H1) {
			System.out.println("H1 was accepted");
		} else if (result == Sprt.Result.H0) {
			System.out.println("H0 was accepted");
		} else {
			System.out.println("Test inconclusive (not enough games)");
		}
	}

	public String sprtStatus() {
		if (sprt == null) return "";
		return String.format(" LLR: %.2f (%.1f%%)", lastLLR, sprt.getFraction(lastLLR) * 100.0);
	}
}
