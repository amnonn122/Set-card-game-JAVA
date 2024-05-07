package bguspl.set.ex;

import bguspl.set.Env;

import java.sql.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    // Dealer field
    protected Dealer dealer;

    // Maintain the player key presses
    protected BlockingQueue<Integer> inComingActionns;

    // Maintain the "leggal" key presses
    BlockingQueue<Integer> holding;

    /**
     * Saves delaer answer about the sent set
     * 0 - default.
     * 1 - leggal
     * -1 - ileggal
     */
    private int leggalOrNot;

    // Tell us if the aiThread can work or not
    private boolean canAiWork;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.canAiWork = false;

        this.dealer = dealer;
        this.leggalOrNot = 0;
        holding = new LinkedBlockingQueue<Integer>();
        this.inComingActionns = new LinkedBlockingQueue<>(env.config.featureSize);
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human)
            createArtificialIntelligence();
        int[] checkArr = new int[env.config.featureSize];
        BlockingQueue<Integer> holding = new LinkedBlockingQueue<Integer>();

        while (!terminate) {
            // TODO implement main player loop
            try {
                if (leggalOrNot == 1) {
                    point();
                } else if (leggalOrNot == -1) {
                    penalty();
                }

                synchronized (table.tableObjectLock) {
                    if (table.tokensOnScreenInEachPlayer[id] >= env.config.featureSize
                            && table.tokensOnScreenInEachPlayer[id] != holding.size()) {
                        table.removeAllTokensToPlayer(id);
                    }

                    if (!inComingActionns.isEmpty() && leggalOrNot == 0 && holding.size() < env.config.featureSize) {
                        int pr = inComingActionns.poll();
                        boolean isRemovedToken = table.removeToken(id, pr);
                        if (isRemovedToken) {
                            holding.remove(pr);
                        } else if (!isRemovedToken) {
                            holding.add(pr);
                            table.placeToken(id, pr);
                        }
                    } else if (holding.size() == env.config.featureSize && leggalOrNot == 0) {
                        for (int count = 0; count < env.config.featureSize; count++) {
                            checkArr[count] = holding.poll();
                        }
                        boolean isOK = true;
                        for (int i = 0; i < env.config.featureSize; i++) {
                            if (!table.slotsForEachPlayer[id][checkArr[i]])
                                isOK = false;
                        }
                        if (isOK)
                            dealer.addSlotForDealarsCheck(this, checkArr);
                    }
                    // Waiting to answer from the dealer about the sent set
                    table.tableObjectLock.wait();

                }
            } catch (InterruptedException ignored) {
            }

            if (!human)
                try {
                    aiThread.join(1);
                } catch (InterruptedException ignored) {
                }

        }

    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            Random random = new Random();
            while (!terminate) {
                // TODO implement player key press simulator
                if (canAiWork)
                    keyPressed(random.nextInt(0, table.slotToCard.length));
            }
            env.logger.info("thread " + Thread.currentThread().getName() + "terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        playerThread.interrupt();
        if (!human)
            aiThread.interrupt();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if (inComingActionns.size() < env.config.featureSize) {
            try {
                inComingActionns.put(slot);
            } catch (InterruptedException e) {
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        this.score++;
        env.ui.setScore(id, score);
        try {
            for (long i = env.config.pointFreezeMillis; i > 0; i -= 1000) {
                env.ui.setFreeze(id, i);
                this.playerThread.sleep(1000);

            }
            env.ui.setFreeze(id, 0);
        } catch (InterruptedException e) {
        }
        inComingActionns.clear();
        holding.clear();
        leggalOrNot = 0;

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests

    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        try {
            for (long i = env.config.penaltyFreezeMillis; i > 0; i -= 1000) {
                env.ui.setFreeze(id, i);
                this.playerThread.sleep(1000);
            }
            env.ui.setFreeze(id, 0);
        } catch (InterruptedException e) {
        }
        inComingActionns.clear();
        holding.clear();
        leggalOrNot = 0;
    }

    public int score() {
        return score;
    }

    public void setCanAiWork(boolean answer) {
        canAiWork = answer;
    }

    public void setLeggalOrNotSet(int delaerCheck) {
        this.leggalOrNot = delaerCheck;
    }
}
