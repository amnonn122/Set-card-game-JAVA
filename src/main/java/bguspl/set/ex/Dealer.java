package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    // Dealer thread
    private Thread dealerThread;

    // Maintain delears slots queue that he need to check
    private BlockingQueue<Integer[]> slotsSetCheck;

    // Maintain the playres that need an answer if their set is leggal
    private BlockingQueue<Player> playersThatPutSlots;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        this.slotsSetCheck = new LinkedBlockingDeque<>();
        this.playersThatPutSlots = new LinkedBlockingDeque<>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        this.dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        startPlayerThreads();
        while (!shouldFinish()) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 2000;
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        terminate();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Start player threads.
     */
    private void startPlayerThreads() {
        for (Player p : players) {
            Thread playerThread = new Thread(p, "Player #" + p.id);
            playerThread.start();
        }
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
            // check if there are no sets
            if (deck.size() > 0 && tableHasNoSets()) {
                removeAllCardsFromTable();
                updateTimerDisplay(true);
            } else if (deck.size() == 0 && tableHasNoSets()) {
                removeAllCardsFromTable();
                terminate = true;
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        Collections.shuffle(deck);
        setAllPlayersCanAiWork(true);
        synchronized (table.tableObjectLock) {
            for (int slot = 0; slot < env.config.tableSize; slot++) {
                // for each empty slot
                if (table.slotToCard[slot] == null && deck.size() > 0) {
                    // place card from deck if possible
                    table.placeCard(deck.remove(0), slot);
                }
            }

            table.tableObjectLock.notifyAll();
        }
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        boolean isRelevant = true;

        if (slotsSetCheck.size() != 0) {
            try {
                Player playerThatPlaceTheSet = playersThatPutSlots.take();

                playerThatPlaceTheSet.setCanAiWork(false);

                int[] slotsArray = Arrays.stream(slotsSetCheck.take()).mapToInt(Integer::intValue).toArray();

                // System.err.println("player " + playerThatPlaceTheSet.id + " put" +
                // Arrays.toString(slotsArray));

                int[] slotsToCardForChecking = new int[env.config.featureSize];
                for (int i = 0; i < env.config.featureSize; i++) {
                    if (table.slotToCard[slotsArray[i]] != null)
                        slotsToCardForChecking[i] = table.slotToCard[slotsArray[i]];
                    else {
                        isRelevant = false;
                        playerThatPlaceTheSet.setCanAiWork(true);
                    }

                }
                synchronized (table.tableObjectLock) {
                    if (isRelevant && env.util.testSet(slotsToCardForChecking)) {
                        whatToDoWIthTheSet(slotsArray);
                        playerThatPlaceTheSet.setLeggalOrNotSet(1);
                    } else if (isRelevant) {
                        WhatToDoWithPenaltyTokkens(slotsArray, playerThatPlaceTheSet.id);
                        playerThatPlaceTheSet.setLeggalOrNotSet(-1);

                    }

                    playerThatPlaceTheSet.setCanAiWork(true);
                    table.tableObjectLock.notifyAll();
                }

            } catch (InterruptedException e) {
            }
        }

    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        setAllPlayersCanAiWork(false);
        table.removeAllTokens();
        table.removeAllCards(deck);
        playersThatPutSlots.clear();
        slotsSetCheck.clear();
        Collections.shuffle(deck);
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (reset) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        } else if (reshuffleTime - System.currentTimeMillis() > env.config.turnTimeoutWarningMillis)
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        else if (reshuffleTime - System.currentTimeMillis() > 0)
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);
        else
            env.ui.setCountdown(0, false);

    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int winnerScore = 0;
        int count = 0;
        for (Player player : players) {
            int checkScore = player.score();
            if (checkScore == winnerScore)
                count++;
            else if (checkScore > winnerScore) {
                count = 1;
                winnerScore = checkScore;
            }
        }
        int index = 0;
        int[] finalyAnnounce = new int[count];
        for (Player player : players) {
            if (player.score() == winnerScore) {
                finalyAnnounce[index] = player.id;
                index++;
            }
        }
        env.ui.announceWinner(finalyAnnounce);
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        for (int id = players.length - 1; id >= 0; id--)
            players[id].terminate();
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        try {
            dealerThread.sleep(1);
        } catch (InterruptedException e) {
        }
    }

    public void addSlotForDealarsCheck(Player player, int[] slotsToCheck) {
        try {
            Integer[] slots = Arrays.stream(slotsToCheck).boxed().toArray(Integer[]::new);
            slotsSetCheck.put(slots);
            playersThatPutSlots.put(player);
        } catch (InterruptedException e) {
        }
    }

    private void WhatToDoWithPenaltyTokkens(int[] set, int playerId) {
        for (int slot : set)
            table.removeToken(playerId, slot);
    }

    private void whatToDoWIthTheSet(int[] set) {
        for (int slot : set) {
            table.removeCard(slot);
            for (Player otherPlayer : players) {
                table.removeToken(otherPlayer.id, slot);
            }
        }
        updateTimerDisplay(true);
    }

    private void setAllPlayersCanAiWork(boolean answer) {
        for (Player otherPlayer : players) {
            otherPlayer.setCanAiWork(answer);
        }
    }

    private boolean tableHasNoSets() {
        LinkedList<Integer> toList = new LinkedList<>();
        for (Integer i : table.slotToCard) {
            if (i != null)
                toList.add(i);
        }
        return env.util.findSets(toList, 1).isEmpty();
    }

}
