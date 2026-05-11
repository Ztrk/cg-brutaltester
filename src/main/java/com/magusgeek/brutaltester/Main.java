package com.magusgeek.brutaltester;

import com.magusgeek.brutaltester.util.Mutable;
import com.magusgeek.brutaltester.util.SeedGenerator;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

public class Main {

    private static final Log LOG = LogFactory.getLog(Main.class);

    private static PlayerStats playerStats;
    private static int t;
    private static int finished = 0;

    public static void main(String[] args) {
        try {
            Options options = new Options();

            options.addOption("h", false, "Print the help")
                   .addOption("v", false, "Verbose mode. Spam incoming.")
                   .addOption("n", true, "Number of games to play. Default 1.")
                   .addOption("t", true, "Number of thread to spawn for the games. Default 1.")
                   .addOption("r", true, "Required. Referee command line.")
                   .addOption("p1", true, "Required. Player 1 command line.")
                   .addOption("p2", true, "Required. Player 2 command line.")
                   .addOption("p3", true, "Player 3 command line.")
                   .addOption("p4", true, "Player 4 command line.")
                   .addOption("l", true, "A directory for games logs")
                   .addOption("s", false, "Swap player positions")
                   .addOption("i", true, "Initial seed. For repeatable tests")
                   .addOption("o", false, "Old mode")
                   .addOption(Option.builder().longOpt("elo0").hasArg().desc("SPRT H0 Elo (default 0)").build())
                   .addOption(Option.builder().longOpt("elo1").hasArg().desc("SPRT H1 Elo (enables SPRT)").build())
                   .addOption(Option.builder().longOpt("alpha").hasArg().desc("SPRT Type I error rate (default 0.05)").build())
                   .addOption(Option.builder().longOpt("beta").hasArg().desc("SPRT Type II error rate (default 0.05)").build())
                   .addOption(Option.builder().longOpt("model").hasArg().desc("SPRT model: logistic, normalized, bayesian (default logistic)").build())
                   .addOption(Option.builder().longOpt("no-penta").desc("Disable pentanomial statistics for SPRT").build());

            CommandLine cmd = new DefaultParser().parse(options, args);

            // Need help ?
            if (cmd.hasOption("h") || !cmd.hasOption("r") || !cmd.hasOption("p1") || !cmd.hasOption("p2")) {
                new HelpFormatter().printHelp("-r <referee command line> -p1 <player1 command line> -p2 <player2 command line> -p3 <player3 command line> -p4 <player4 command line> [-o -v -n <games> -t <thread>]", options);
                System.exit(0);
            }

            // Verbose mode
            if (cmd.hasOption("v")) {
                Configurator.setRootLevel(Level.ALL);
                LOG.info("Verbose mode activated");
            }

            // Referee command line
            String refereeCmd = cmd.getOptionValue("r");
            LOG.info("Referee command line: " + refereeCmd);

            // Players command lines
            List<String> playersCmd = new ArrayList<>();
            for (int i = 1; i <= 4; ++i) {
                String value = cmd.getOptionValue("p" + i);

                if (value != null) {
                    playersCmd.add(value);
                    LOG.info("Player " + i + " command line: " + value);
                }
            }

            // Games count
            int n = 1;
            try {
                n = Integer.valueOf(cmd.getOptionValue("n"));
            } catch (Exception exception) {

            }
            LOG.info("Number of games to play: " + n);

            // Thread count
            t = 1;
            try {
                t = Integer.valueOf(cmd.getOptionValue("t"));
            } catch (Exception exception) {

            }
            LOG.info("Number of threads to spawn: " + t);

            // Logs directory
            Path logs = null;
            if (cmd.hasOption("l")) {
                logs = FileSystems.getDefault().getPath(cmd.getOptionValue("l"));
                if (!Files.isDirectory(logs)) {
                    throw new NotDirectoryException("Given path for the logs directory is not a directory: " + logs);
                }
            }

            boolean swap = cmd.hasOption("s");
            //Seed Initialization
            if (cmd.hasOption("i")){
                long newSeed = Integer.valueOf(cmd.getOptionValue("i"));
                SeedGenerator.initialSeed(newSeed);
                LOG.info("Initial Seed: " + newSeed);
            }

            // SPRT configuration
            Sprt sprt = null;
            boolean usePenta = false;
            if (cmd.hasOption("elo1")) {
                if (playersCmd.size() != 2) {
                    LOG.fatal("SPRT requires exactly 2 players");
                    System.exit(1);
                }

                double elo0 = 0.0;
                double elo1 = 0.0;
                double alpha = 0.05;
                double beta = 0.05;
                String modelStr = "logistic";

                try { elo0 = Double.parseDouble(cmd.getOptionValue("elo0")); } catch (Exception e) {}
                try { elo1 = Double.parseDouble(cmd.getOptionValue("elo1")); } catch (Exception e) {}
                try { alpha = Double.parseDouble(cmd.getOptionValue("alpha")); } catch (Exception e) {}
                try { beta = Double.parseDouble(cmd.getOptionValue("beta")); } catch (Exception e) {}
                if (cmd.hasOption("model")) modelStr = cmd.getOptionValue("model");

                usePenta = swap && !cmd.hasOption("no-penta");
                usePenta = Sprt.validate(alpha, beta, elo0, elo1, modelStr, usePenta);

                sprt = new Sprt(alpha, beta, elo0, elo1, Sprt.Model.fromString(modelStr));

                LOG.info("SPRT: model=" + modelStr + " elo0=" + elo0 + " elo1=" + elo1
                        + " alpha=" + alpha + " beta=" + beta
                        + " bounds=" + sprt.getBounds() + " elo=" + sprt.getElo()
                        + " pentanomial=" + usePenta);
            }

            // Prepare stats objects
            playerStats = new PlayerStats(playersCmd.size(), sprt, usePenta);
            Mutable<Integer> count = new Mutable<>(0);

            // Start the threads
            for (int i = 0; i < t; ++i) {
            	if (cmd.hasOption("o")) {
            		new OldGameThread(i + 1, refereeCmd, playersCmd, count, playerStats, n, logs, swap).start();
            	} else {
            		new GameThread(i + 1, refereeCmd, playersCmd, count, playerStats, n, logs, swap).start();
            	}
            }
        } catch (Exception exception) {
            LOG.fatal("cg-brutaltester failed to start", exception);
            System.exit(1);
        }
    }

    public static void finish() {
        synchronized (playerStats) {
            finished += 1;

            if (finished >= t) {
                LOG.info("*** End of games ***");
                playerStats.print();
            }
        }
    }
}
