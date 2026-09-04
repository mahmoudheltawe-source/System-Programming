package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    // ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

    /**
     * true if the game is starts,and false if the game is finished.
     */
    public volatile boolean isStarted;
    
    /**
     * the players who are waiting.
     */
    public BlockingQueue<Integer> playersQ;
    
    /**
     * for each slot the players that choose it.
     */
    public ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Integer>> slot_To_Players;

    /**
     * for each player the token cards slots.
     */
    public ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Integer>> player_To_Tokens;
    // ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if
     *                   none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if
     *                   none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        // ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

        playersQ = new LinkedBlockingQueue<>();
        slot_To_Players = new ConcurrentHashMap<>(); // for each slot
        for (int i = 0; i < slotToCard.length; i++)
            slot_To_Players.put(i, new ConcurrentHashMap<>());
        player_To_Tokens = new ConcurrentHashMap<>();
        isStarted = false;
        // ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the
     * table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted()
                    .collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(
                    sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * 
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

        if (slot < 0 || slot >= slotToCard.length) {
            return;
        }

        if (card < 0 || card >= cardToSlot.length) {
            return;
        }

        if (cardToSlot[card] != null || slotToCard[slot] != null) {
            return;
        }
        // ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        
        // ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

        env.ui.placeCard(card, slot);
        // ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

    }

    /**
     * Removes a card from a grid slot on the table.
     * 
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        
        // ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
        if (slot < 0 || slot >= slotToCard.length) {
            return;
        }

        Integer card = slotToCard[slot];

        if (card == null) {
            return;
        }

        cardToSlot[slotToCard[slot]] = null;
        slotToCard[slot] = null;

        slot_To_Players.put(slot, new ConcurrentHashMap<>());// remove all the players that point on this card

        for (Integer player : player_To_Tokens.keySet())
            if (player_To_Tokens.get(player).containsKey(slot))
                removeToken(player, slot);
        env.ui.removeCard(slot);
        // ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

    }

    /**
     * Places a player token on a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        
        // :::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
        if (slot < 0 || slot >= slotToCard.length) {
            return;
        }

        if (slotToCard[slot] == null)
            return;

        player_To_Tokens.get(player).put(slot, slot); // add the slot of the token card
        slot_To_Players.get(slot).put(player, player);
        env.ui.placeToken(player, slot);
        // ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

    }

    /**
     * Removes a token of a player from a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        
        // ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
        if (!player_To_Tokens.containsKey(player) || !player_To_Tokens.get(player).containsKey(slot)) {
            return false;
        }
        player_To_Tokens.get(player).remove(slot);
        if (slot >= 0 && slot < slot_To_Players.size()) {
            slot_To_Players.get(slot).remove(player);
        }
        env.ui.removeToken(player, slot);
        return true;
        // ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::

    }
}
