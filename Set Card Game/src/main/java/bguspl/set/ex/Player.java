package bguspl.set.ex;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    private static final long AI_ACTION_DELAY_MILLIS = 10;

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
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
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
    // ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

    /**
     * The queue to save the player actions
     */
    public final BlockingQueue<Integer> actions;
    /**
     * true if the dealer responds to action
     */
    public volatile boolean isReply;
    /**
     * true iff the dealer responds a point to action
     */
    public volatile boolean reply;
    /**
     * to indicate whether the player is currently asleep (frozen and unable to perform any actions).
     */
    public volatile boolean sleep;
    private volatile boolean waitingForDealer;
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    @SuppressWarnings("unused")
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.actions = new LinkedBlockingQueue<>(env.config.featureSize); 
        isReply = false;
        reply = false;
        sleep = false;
        waitingForDealer = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            try {
                handleAction(actions.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            Random random = new Random();
            try {
                while (!terminate) {
                    if (table.isStarted && !sleep && !waitingForDealer) {
                        List<Integer> slots = findSetSlots(random);
                        if (!slots.isEmpty()) {
                            for (Integer slot : slots) {
                                keyPressed(slot);
                            }
                        }
                    }
                    Thread.sleep(AI_ACTION_DELAY_MILLIS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    private List<Integer> findSetSlots(Random random) {
        List<Integer> cards = new LinkedList<>();
        Integer[] cardToSlotSnapshot;
        synchronized (table) {
            for (Integer card : table.slotToCard) {
                if (card != null) {
                    cards.add(card);
                }
            }
            cardToSlotSnapshot = table.cardToSlot.clone();
        }

        List<int[]> sets = env.util.findSets(cards, 1);
        if (sets.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> slots = new LinkedList<>();
        for (int card : sets.get(0)) {
            Integer slot = cardToSlotSnapshot[card];
            if (slot == null) {
                return Collections.emptyList();
            }
            slots.add(slot);
        }
        Collections.shuffle(slots, random);
        return slots;
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        this.terminate = true;
        if (playerThread != null) {
            playerThread.interrupt();
        }
        if (aiThread != null) {
            aiThread.interrupt();
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (terminate || sleep || waitingForDealer || !table.isStarted) {
            return;
        }
        actions.offer(slot);
    }

    private void handleAction(int slot) throws InterruptedException {
        // to ensure thread safety when accessing shared data.
        boolean shouldAskDealer = false;
        synchronized(table){
            if(!table.player_To_Tokens.containsKey(id)){
                table.player_To_Tokens.put(id, new ConcurrentHashMap<>());
            }
            if(sleep || waitingForDealer || !table.isStarted){
                return;
            }
            //we checks if the player already placed three takens in the table
            if(table.player_To_Tokens.get(id).size() == env.config.featureSize && !table.player_To_Tokens.get(id).containsKey(slot)){
                return;
            }
            //if the player hasn't placed three tokens yet, we check if the slot corresponding to key press is 
            //already chosen by a token placed by the same player then we removed the token from the slot. 
            //otherwise, we placed a token in the slot.
            if(table.player_To_Tokens.get(id).size() <= env.config.featureSize){
                if(!table.player_To_Tokens.get(id).containsKey(slot)){
                    table.placeToken(id, slot);
                }
                else{
                    table.removeToken(id, slot);
                }
            }
            shouldAskDealer = table.player_To_Tokens.get(id).size() == env.config.featureSize;
        }

        if(shouldAskDealer){
            synchronized(table){
                if(!table.isStarted || table.player_To_Tokens.get(id).size() != env.config.featureSize){
                    return;
                }
                waitingForDealer = true;
                table.playersQ.put(id);
            }
            waitForDealerResponse();
        }
    }

    private void waitForDealerResponse() throws InterruptedException {
        boolean pointAwarded;
        synchronized(this){
            while (!isReply && !terminate) {
                wait();
            }
            if (terminate) {
                waitingForDealer = false;
                return;
            }
            pointAwarded = reply;
            isReply = false;
            reply = false;
            sleep = true;
            actions.clear();
        }
        if(pointAwarded){
            point();
        }
        else{
            penalty();
        }
        waitingForDealer = false;
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);

        freeze(env.config.pointFreezeMillis);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        freeze(env.config.penaltyFreezeMillis);
    }

    private void freeze(long freezeMillis) {
        sleep = true;
        long freeze = freezeMillis;
        while (freeze > 0) {
            env.ui.setFreeze(id, freeze);
            try{
                Thread.sleep(Math.min(freeze, 1000));
            }
            catch(InterruptedException e){
                Thread.currentThread().interrupt();
                break;
            }
            freeze = freeze - 1000;
        }
        env.ui.setFreeze(id, 0);
        sleep = false;
    }

    public int score() {
        return score;
    }
}
