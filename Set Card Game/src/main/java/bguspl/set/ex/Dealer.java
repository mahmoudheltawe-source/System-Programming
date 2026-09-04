package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    private static final long REGULAR_TIMER_UPDATE_MILLIS = 1000;
    private static final long WARNING_TIMER_UPDATE_MILLIS = 10;

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
    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    private final Thread[] playerThreads;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        playerThreads = new Thread[players.length];
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        placeCardsOnTable();
        for (int i = 0; i < playerThreads.length; i++) {
            playerThreads[i] = new Thread(players[i]); // create the player thread
            playerThreads[i].start(); // start it
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            updateTimerDisplay(true);
            table.isStarted = true;
            timerLoop();
            drainPendingClaims();
            removeAllCardsFromTable();
        }
        terminate();
        for (int i = playerThreads.length - 1; i >= 0; i--) {
            playerThreads[i].interrupt();
        }
        for (int i = playerThreads.length - 1; i >= 0; i--) {
            try {
                playerThreads[i].join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            updateTimerDisplay(false);
            Integer id = sleepUntilWokenOrTimeout();
            if (id != null) {
                removeCardsFromTableByPlayerId(id);
                placeCardsOnTable();
            }
        }
        table.isStarted = false;
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        for(Player p : players){
            p.terminate();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        List<Integer> remainingCards = new LinkedList<>(deck);
        synchronized (table) {
            for (Integer card : table.slotToCard) {
                if (card != null) {
                    remainingCards.add(card);
                }
            }
        }
        return terminate || env.util.findSets(remainingCards, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTableByPlayerId(int id){
        List<Integer> tokens = new LinkedList<>();//the tokens of the player with id
        boolean legal;
        synchronized (table) {
            if (table.player_To_Tokens.get(id) != null) {
                for(Integer s : table.player_To_Tokens.get(id).keySet()){
                    tokens.add(s);
                }
            }
            legal = isLegal(tokens);
            if(legal){
                for(Integer slot : tokens){
                    if(table.slotToCard[slot] != null){
                        table.removeCard(slot);
                    }
                }
                reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
                updateTimerDisplay(true);
            }
        }
        synchronized (players[id]) {
            players[id].reply = legal;
            players[id].isReply = true;
            players[id].notifyAll();
        }
    } 

    private void drainPendingClaims() {
        Integer id;
        while ((id = table.playersQ.poll()) != null) {
            removeCardsFromTableByPlayerId(id);
            placeCardsOnTable();
        }
    }

    public boolean isLegal(List<Integer> pressed) {
        if (pressed.size() != env.config.featureSize) {
            return false;
        }
        int[] chosenSlots = new int[env.config.featureSize];
        for (int i = 0; i < env.config.featureSize; i++)
            chosenSlots[i] = pressed.get(i);

        int[] chosenCards = new int[env.config.featureSize];

        for (int i = 0; i < chosenSlots.length; i++)
            if (table.slotToCard[chosenSlots[i]] != null)
                chosenCards[i] = table.slotToCard[chosenSlots[i]];
            else
                return false;

        return env.util.testSet(chosenCards);
    }

    @SuppressWarnings("unused")
    public boolean isLegal(int id, List<Integer> pressed) {
        return isLegal(pressed);
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        List<Integer> indexesLst = new LinkedList<>();
        synchronized (table) {
            for(int i = 0; i < table.slotToCard.length;i++){
                if(table.slotToCard[i] == null){
                    indexesLst.add(i);
                }
            }
            shuffle(indexesLst);
            shuffle(deck);
            while (!indexesLst.isEmpty() && !deck.isEmpty()) {
                int slot = indexesLst.remove(0);
                int card = deck.remove(0);
                table.placeCard(card, slot);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private Integer sleepUntilWokenOrTimeout() {
        long millisUntilReshuffle = reshuffleTime - System.currentTimeMillis();
        if (millisUntilReshuffle <= 0) {
            return null;
        }
        long timerUpdateMillis = millisUntilReshuffle <= env.config.turnTimeoutWarningMillis
                ? WARNING_TIMER_UPDATE_MILLIS
                : REGULAR_TIMER_UPDATE_MILLIS;
        try {
            return table.playersQ.poll(Math.min(millisUntilReshuffle, timerUpdateMillis), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            terminate();
            return null;
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        long remainingMillis = reset
                ? env.config.turnTimeoutMillis
                : Math.max(reshuffleTime - System.currentTimeMillis(), 0);
        env.ui.setCountdown(remainingMillis, remainingMillis <= env.config.turnTimeoutWarningMillis);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        List<Integer> tableIndex = new LinkedList<>();
        synchronized (table) {
            for(int i = 0; i < table.slotToCard.length; i++){
                if(table.slotToCard[i] != null){
                    tableIndex.add(i);
                }
            }
            while(!tableIndex.isEmpty()) {
                int slot = tableIndex.remove(0);
                deck.add(table.slotToCard[slot]);
                table.removeCard(slot);
            }
        }
        shuffle(deck);
    }




    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        List<Player> winnersPlayers = new LinkedList<>();
        int maximum = maxScore();
        for(Player p : players){
            if(p.score() == maximum){
                winnersPlayers.add(p);
            }
        }
        int[] winnersPlayersArray = new int[winnersPlayers.size()];
        for(int i = 0; i<winnersPlayersArray.length;i++){
            winnersPlayersArray[i] = winnersPlayers.remove(0).id;
        }
        env.ui.announceWinner(winnersPlayersArray);
    }
    /**
     * Find the max score between the players
     * 
     * @return the max score between the players
     */
    public int maxScore(){
        int maximum = 0;
        for(Player p : players){
            if(p.score() > maximum){
                maximum = p.score();
            }
        }
        return maximum;
    }

    /**
     * shuffle the list lst
     *  
     * @param lst list that contains Integer values
     */
    public void shuffle(List<Integer> lst){
        Collections.shuffle(lst);
    }
  
}
