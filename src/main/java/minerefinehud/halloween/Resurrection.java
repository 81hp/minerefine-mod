package minerefinehud.halloween;

import minerefinehud.shop.RomanNumerals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resurrections: the player's level, what the next one costs, and how close they are.
 *
 * Seen in game, as plain text:
 *
 *   sidebar   "Resurrection 5", "Next: Nether Wart Hoe II"
 *   chat      "RESURRECTED! You are now Resurrection 5!" (only to the player who did it)
 *             " 81hp_ rose to Resurrection 5! " (to everyone, about anyone: never read)
 *             "UPGRADE! Fire Coral Hoe VII: Fire Coral: +7", "NEW CROP! Beetroot Hoe I: ..."
 *   menu      Resurrection 6
 *             Crop Multiplier: x8 → x10
 *             Unlocks the Scarecrow Upgrade
 *             Cost:
 *             ⊢ Fire Coral Hoe VII ✔
 *             ⊢ 350,000,000 Total Crops ✘ (303,808,740)
 *             ⊢ Your crops & hoe reset, but your candy & refines are kept.
 *
 * The level is only ever what the server states, the sidebar row or the player's own chat line,
 * never counted up from a menu or from crops vanishing, so nothing can add a resurrection that
 * did not happen. "Total Crops" is the crops held, so progress is the sidebar's crops added up.
 *
 * No Minecraft types; times are epoch milliseconds from the caller.
 */
public final class Resurrection {

    /** There are seven; at the last one the bar goes away. */
    public static final int MAX = 7;

    /** The hoes in the order they are bought, each from I to VII. */
    public static final List<String> HOE_CROPS = List.of("Carrot", "Beetroot", "Nether Wart", "Fire Coral", "Hallowed");
    public static final int HOE_TIERS = 7;

    private Resurrection() {
    }

    /** What the menu says one resurrection costs and gives. */
    public static final class Card {
        public int target;
        /** Crops needed. */
        public double crops;
        /** Crops the menu said were held, or -1. */
        public double have = -1.0;
        /** The sidebar's crops added up when the menu was read, or -1; /mrhud debug compares the two. */
        public double sidebarCrops = -1.0;
        /** "Fire Coral Hoe VII", or empty. */
        public String hoe = "";
        /** Its place in {@link #HOE_CROPS} order, or -1 when not one of those. */
        public int hoeRank = -1;
        /** The menu's tick or cross next to the hoe; null when it showed neither. */
        public Boolean hoeMet;
        /** "x8 → x10", or empty. */
        public String multiplier = "";
        /** "Scarecrow", or empty. */
        public String unlocks = "";
        public long readAt;

        public Card() {
        }

        /** Same cost and rewards; the readings that change by the second are left out. */
        boolean sameAs(Card o) {
            return o != null && target == o.target && crops == o.crops && hoeRank == o.hoeRank
                    && Objects.equals(hoe, o.hoe) && Objects.equals(hoeMet, o.hoeMet)
                    && Objects.equals(multiplier, o.multiplier) && Objects.equals(unlocks, o.unlocks);
        }
    }

    /** Kept in the config. */
    public static final class Saved {
        /** The resurrection the player is at, or -1 until the server has said. */
        public int level = -1;
        /** The hoe they hold as a {@link #rank}, -1 for none; only meaningful once {@link #hoeAt} is set. */
        public int hoeRank = -1;
        /** When the hoe last changed, or 0 while it is not known. */
        public long hoeAt;
        public List<Card> cards = new ArrayList<>();

        public Saved() {
        }
    }

    // ---------------------------------------------------------------- reading

    private static final String ROMAN = "[IVXivx]+";
    private static final String HOE_NAME = "(?<crop>" + String.join("|", HOE_CROPS) + ")\\s+Hoe\\s+(?<tier>" + ROMAN + ")\\b";

    private static final Pattern CHAT_RESURRECTED = Pattern.compile(
            "^RESURRECTED!\\s*You are now Resurrection\\s+(?<n>[0-9]{1,2})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAT_HOE = Pattern.compile(
            "^(?:UPGRADE|NEW CROP)!\\s*" + HOE_NAME, Pattern.CASE_INSENSITIVE);
    /** The whole row, past any frame or icon in front, so no other row that mentions it counts. */
    private static final Pattern SIDEBAR_LEVEL = Pattern.compile(
            "^[^\\p{L}\\p{N}]*Resurrection\\s*:?\\s*(?<n>[0-9]{1,2}|" + ROMAN + ")[^\\p{L}\\p{N}]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SIDEBAR_NEXT_HOE = Pattern.compile(
            "^[^\\p{L}\\p{N}]*Next\\s*:\\s*" + HOE_NAME, Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_TITLE = SIDEBAR_LEVEL;
    private static final Pattern CARD_CROPS = Pattern.compile(
            "(?<need>[0-9][0-9,]*(?:\\.[0-9]+)?[kKmMbB]?)\\s+Total\\s+Crops\\b(?:.*?\\((?<have>[0-9][0-9,]*(?:\\.[0-9]+)?[kKmMbB]?)\\))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_HOE = Pattern.compile("\\b" + HOE_NAME, Pattern.CASE_INSENSITIVE);
    /** A cost line naming some other hoe: "⊢ Spooky Hoe III ✘". */
    private static final Pattern CARD_OTHER_HOE = Pattern.compile(
            "^[^\\p{L}]*(?<crop>\\p{L}[\\p{L}' ]*?)\\s+Hoe\\s+(?<tier>" + ROMAN + ")\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_MULTIPLIER = Pattern.compile(
            "Crop\\s+Multiplier\\s*:?\\s*x?(?<from>[0-9]+(?:\\.[0-9]+)?)\\s*[^0-9x]*x?(?<to>[0-9]+(?:\\.[0-9]+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_UNLOCKS = Pattern.compile(
            "^Unlocks\\s+(?:the\\s+)?(?<what>.+?)(?:\\s+Upgrade)?[.!]?$", Pattern.CASE_INSENSITIVE);

    private static String plain(String raw) {
        return HalloweenText.plainLetters(HalloweenText.strip(raw));
    }

    /** A number from 0 to 99, written out or in Roman numerals. */
    private static OptionalInt level(String s) {
        if (s.chars().allMatch(Character::isDigit)) {
            return OptionalInt.of(Integer.parseInt(s));
        }
        return RomanNumerals.parse(s.toUpperCase(Locale.ROOT));
    }

    /**
     * The hoe's place in the buying order: Carrot Hoe I is 0, Carrot Hoe VII is 6, Beetroot Hoe I
     * is 7. Empty for a crop or tier that is not one of these.
     */
    public static OptionalInt rank(String crop, String tier) {
        int c = -1;
        for (int i = 0; i < HOE_CROPS.size(); i++) {
            if (HOE_CROPS.get(i).equalsIgnoreCase(crop.trim())) {
                c = i;
            }
        }
        OptionalInt t = RomanNumerals.parse(tier.toUpperCase(Locale.ROOT));
        if (c < 0 || t.isEmpty() || t.getAsInt() < 1 || t.getAsInt() > HOE_TIERS) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(c * HOE_TIERS + t.getAsInt() - 1);
    }

    private static OptionalInt rank(Matcher m) {
        return rank(m.group("crop"), m.group("tier"));
    }

    /** "RESURRECTED! You are now Resurrection 5!" said to the player: 5. Anyone else's news is not read. */
    public static OptionalInt chatLevel(String text) {
        Matcher m = CHAT_RESURRECTED.matcher(plain(text));
        return m.find() ? OptionalInt.of(Integer.parseInt(m.group("n"))) : OptionalInt.empty();
    }

    /** "UPGRADE! Fire Coral Hoe VII: ...": the hoe now held, as a {@link #rank}. */
    public static OptionalInt chatHoe(String text) {
        Matcher m = CHAT_HOE.matcher(plain(text));
        return m.find() ? rank(m) : OptionalInt.empty();
    }

    /** The sidebar's "Resurrection 5" row. Empty without one, or with two that disagree. */
    public static OptionalInt sidebarLevel(List<String> lines) {
        OptionalInt found = OptionalInt.empty();
        for (String raw : lines == null ? List.<String>of() : lines) {
            Matcher m = SIDEBAR_LEVEL.matcher(plain(raw));
            if (!m.find()) {
                continue;
            }
            OptionalInt n = level(m.group("n"));
            if (n.isEmpty()) {
                continue;
            }
            if (found.isPresent() && found.getAsInt() != n.getAsInt()) {
                return OptionalInt.empty();
            }
            found = n;
        }
        return found;
    }

    /**
     * The hoe held, from the sidebar's "Next: Nether Wart Hoe II" (the one before it), as a
     * {@link #rank}; -1 when the next is Carrot Hoe I. Empty without that row.
     */
    public static OptionalInt sidebarHoe(List<String> lines) {
        OptionalInt found = OptionalInt.empty();
        for (String raw : lines == null ? List.<String>of() : lines) {
            Matcher m = SIDEBAR_NEXT_HOE.matcher(plain(raw));
            if (!m.find()) {
                continue;
            }
            OptionalInt next = rank(m);
            if (next.isEmpty()) {
                continue;
            }
            if (found.isPresent() && found.getAsInt() != next.getAsInt() - 1) {
                return OptionalInt.empty();
            }
            found = OptionalInt.of(next.getAsInt() - 1);
        }
        return found;
    }

    /** A resurrection's item in its menu: a "Resurrection 6" title and a "Total Crops" cost. */
    public static Optional<Card> card(String title, List<String> lore) {
        Matcher t = CARD_TITLE.matcher(plain(title));
        if (!t.find() || lore == null) {
            return Optional.empty();
        }
        OptionalInt target = level(t.group("n"));
        if (target.isEmpty() || target.getAsInt() < 1) {
            return Optional.empty();
        }
        Card c = new Card();
        c.target = target.getAsInt();
        boolean crops = false;
        for (String raw : lore) {
            String line = plain(raw);
            Matcher m = CARD_CROPS.matcher(line);
            if (!crops && m.find()) {
                double need = number(m.group("need"));
                if (need > 0) {
                    c.crops = need;
                    crops = true;
                    if (m.group("have") != null) {
                        c.have = number(m.group("have"));
                    }
                }
                continue;
            }
            m = CARD_HOE.matcher(line);
            Matcher other = CARD_OTHER_HOE.matcher(line);
            boolean known = c.hoe.isEmpty() && m.find();
            if (known || (c.hoe.isEmpty() && other.find())) {
                // A hoe not in the list still counts as asked for, so the bar never says ready
                // without it; only the menu's tick can then say it is there.
                Matcher hit = known ? m : other;
                OptionalInt r = rank(hit);
                c.hoeRank = r.orElse(-1);
                c.hoe = (r.isPresent() ? HOE_CROPS.get(r.getAsInt() / HOE_TIERS) : hit.group("crop").trim())
                        + " Hoe " + hit.group("tier").toUpperCase(Locale.ROOT);
                c.hoeMet = mark(line);
                continue;
            }
            m = CARD_MULTIPLIER.matcher(line);
            if (c.multiplier.isEmpty() && m.find()) {
                c.multiplier = "x" + m.group("from") + " → x" + m.group("to");
                continue;
            }
            m = CARD_UNLOCKS.matcher(line);
            if (c.unlocks.isEmpty() && m.find()) {
                c.unlocks = m.group("what").trim();
            }
        }
        return crops ? Optional.of(c) : Optional.empty();
    }

    /** The menu's tick or cross on a cost line. */
    private static Boolean mark(String line) {
        if (line.matches(".*[✔✓☑].*")) {
            return Boolean.TRUE;
        }
        if (line.matches(".*[✘✗✖❌☒].*")) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** A number read with {@link HalloweenText#number}, or -1. */
    private static double number(String s) {
        return HalloweenText.number(s).orElse(-1.0);
    }

    /**
     * A sidebar row, acted on once two reads in a row agree and only when it says something new.
     * So a row that redraws for a moment is never taken, and a sidebar a beat behind the chat
     * ("Resurrection 5" still showing after "You are now Resurrection 6!") never takes the
     * player back down.
     */
    public static final class Steady {
        private boolean pending;
        private int pendingValue;
        private boolean accepted;
        private int acceptedValue;

        /** The reading now; a value only when it has just become the steady one. */
        public OptionalInt read(OptionalInt reading) {
            if (reading.isEmpty()) {
                pending = false;
                return OptionalInt.empty();
            }
            int v = reading.getAsInt();
            if (!pending || pendingValue != v) {
                pending = true;
                pendingValue = v;
                return OptionalInt.empty();
            }
            if (accepted && acceptedValue == v) {
                return OptionalInt.empty();
            }
            accepted = true;
            acceptedValue = v;
            return OptionalInt.of(v);
        }

        /** After joining, the first steady reading is taken whatever it says. */
        public void forget() {
            pending = false;
            accepted = false;
        }
    }

    // ----------------------------------------------------------------- state

    /**
     * The server says the player is at this resurrection. Going up means they just resurrected,
     * so the hoe is back to Carrot Hoe I and the cards they have passed are dropped. Saying the
     * same again changes nothing, so one resurrection told twice is still one.
     */
    public static boolean setLevel(Saved s, int level, long now) {
        if (level < 0 || level == s.level) {
            return false;
        }
        boolean up = s.level >= 0 && level > s.level;
        s.level = level;
        if (up) {
            s.hoeRank = 0;
            s.hoeAt = now;
        }
        s.cards.removeIf(c -> c.target <= level);
        return true;
    }

    /** The hoe the player now holds, as a {@link #rank} (-1 for none). */
    public static boolean setHoe(Saved s, int rank, long now) {
        if (s.hoeAt > 0L && rank == s.hoeRank) {
            return false;
        }
        s.hoeRank = rank;
        s.hoeAt = now;
        return true;
    }

    /**
     * A card read from the menu. One for a resurrection already done (a menu that also lists the
     * past ones) is not kept. True when what it says is new, for saving.
     */
    public static boolean learn(Saved s, Card card, long now) {
        if (s.level >= 0 && card.target <= s.level) {
            return false;
        }
        card.readAt = now;
        for (int i = 0; i < s.cards.size(); i++) {
            Card old = s.cards.get(i);
            if (old.target == card.target) {
                s.cards.set(i, card);
                return !old.sameAs(card);
            }
        }
        s.cards.add(card);
        return true;
    }

    // ------------------------------------------------------------------ view

    public enum State {
        /** Not enough crops, or the hoe is short. */
        SAVING,
        /** Everything the card asks for is there. */
        READY,
        /** The level is known but not what the next one costs. */
        OPEN_MENU
    }

    /** What the panel shows. */
    public record View(State state, int target, double have, double need, String hoe, Boolean hoeMet,
                       String multiplier, String unlocks, long msLeft) {

        public double fraction() {
            return need <= 0 ? 0.0 : Math.min(1.0, have / need);
        }
    }

    /**
     * The next resurrection, with the crops held now and the crop rate per hour (NaN while not
     * measured). Empty when nothing is known yet, or at the last one.
     */
    public static Optional<View> view(Saved s, double crops, double perHour) {
        Card card = null;
        int target;
        if (s.level >= 0) {
            target = s.level + 1;
            for (Card c : s.cards) {
                if (c.target == target) {
                    card = c;
                }
            }
            if (card == null) {
                return target > MAX ? Optional.empty()
                        : Optional.of(new View(State.OPEN_MENU, target, 0, 0, "", null, "", "", -1L));
            }
        } else {
            // Not told the level yet: the card read last is the best guess at the next one.
            for (Card c : s.cards) {
                if (card == null || c.readAt > card.readAt || (c.readAt == card.readAt && c.target < card.target)) {
                    card = c;
                }
            }
            if (card == null) {
                return Optional.empty();
            }
            target = card.target;
        }
        Boolean hoeMet = hoeMet(s, card);
        double have = Math.max(0.0, crops);
        boolean enough = have >= card.crops;
        long msLeft = -1L;
        if (!enough && perHour > 0 && Double.isFinite(perHour)) {
            msLeft = (long) Math.ceil((card.crops - have) / perHour * 3_600_000.0);
        }
        State state = enough && !Boolean.FALSE.equals(hoeMet) && (hoeMet != null || card.hoe.isEmpty())
                ? State.READY : State.SAVING;
        return Optional.of(new View(state, target, have, card.crops, card.hoe, hoeMet, card.multiplier,
                card.unlocks, msLeft));
    }

    /**
     * The menu's tick, unless the hoe has changed since the menu was read; then the hoe held
     * against the one asked for. Null when neither is known.
     */
    static Boolean hoeMet(Saved s, Card card) {
        if (card.hoe.isEmpty()) {
            return null;
        }
        boolean ranksKnown = card.hoeRank >= 0 && s.hoeAt > 0L;
        if (ranksKnown && (card.hoeMet == null || s.hoeAt > card.readAt)) {
            return s.hoeRank >= card.hoeRank;
        }
        return card.hoeMet;
    }
}
