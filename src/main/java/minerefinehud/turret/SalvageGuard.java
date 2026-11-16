package minerefinehud.turret;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import minerefinehud.halloween.HalloweenText;
import minerefinehud.server.ServerName;

/**
 * Decides which clicks in the salvage menu have to be made twice.
 *
 * Salvaging destroys the turret and the menu takes one on a single shift-click, so a big turret is
 * one slip of the mouse from gone. The first mouse click on a turret above the player's size is
 * stopped with a warning; the same turret clicked again within {@link #CONFIRM_MS} goes through.
 * A key (a hotbar number, drop) never moves one: a held key repeats by itself, which would be its
 * own second click.
 *
 * Only turrets in the player's own inventory are guarded: taking one back out of the menu is the
 * safe direction and needs no second click.
 *
 * A click only counts as the player's when it is the first one a press of the mouse button
 * causes, made while the game is handling that press or its release ({@link Presses}). The game
 * makes more by itself when the button is let go: after a double click it gathers or shift-clicks
 * every stack like the one just clicked, and in touchscreen mode a drag is two clicks at once.
 * Those are stopped on a guarded turret and are neither its first click nor its second, however
 * long the button was held. So is a click from anywhere else, another mod's shortcut.
 *
 * The menu's title is the only check that this is MineRefine. The sidebar is deliberately not
 * asked, and the "Only on MineRefine" setting does not switch this off: a sidebar unread or
 * restyled would stop the guard without a word, and that costs a turret. A menu and item that
 * match by chance on another server cost an extra click, a refused key and one chat line.
 *
 * No Minecraft types.
 */
public final class SalvageGuard {

    public static final int DEFAULT_SIZE = 1000;
    /** Four digits, what the settings box holds. No turret gets near it: sizes cap at 2,000. */
    public static final int MAX_SIZE = 9999;
    /** How long after the warning the same turret clicked again counts as meant. */
    public static final long CONFIRM_MS = 3_000L;
    /**
     * A second press sooner than this is a mouse switch that bounced, not a second click. Well
     * under any double click.
     */
    static final long MIN_GAP_MS = 80L;
    /**
     * Warnings closer together than this are stopped quietly instead, and still need their own
     * two clicks: one sound and one chat line at a time.
     */
    static final long QUIET_MS = 150L;
    /** The size of an item named like a turret whose size line could not be read: guarded always. */
    public static final double UNREADABLE = Double.POSITIVE_INFINITY;
    /**
     * The real title is 26 characters, though a resource pack may pad one with invisible ones.
     * Far longer than that is not it.
     */
    private static final int MAX_TITLE = 500;

    /** Whose click it is. */
    public enum Origin {
        /** The player's: the first click a press of the mouse button causes. */
        OWN,
        /** The game's, made by itself as a double click is let go. */
        GAME,
        /**
         * Neither: a click made outside the game's handling of the mouse button, or a second one
         * inside it. Another mod's shortcut (a scroll wheel that moves items), or the second click
         * of a touchscreen drag.
         */
        OTHER
    }

    /**
     * Tells the player's own click from every other, from the mouse button going down and up on
     * the menu screen and from whether a click arrives while the game is handling one of those.
     *
     * A press causes at most one click of the player's: the first after it, made while the game
     * handles the press, or its release when something is on the cursor (and always in touchscreen
     * mode). What else comes while a double press is let go is the game's own: it gathers every
     * stack like the one on the cursor, or with shift held shift-clicks every stack like the one
     * last shift-clicked, wherever the pointer is by then. A click outside both is no press at all.
     *
     * Until one click has been seen arriving inside that handling, it is not known to work here: a
     * Minecraft version that renamed it, or a client that handles the mouse its own way. Every
     * click then has to count as the player's, and only the times between them tell them apart.
     */
    public static final class Presses {

        private int presses;
        private int used;
        private boolean doublePress;
        private boolean inPress;
        private boolean inRelease;
        private boolean handlingSeen;
        private boolean skipped;

        /**
         * The game starts handling a press.
         *
         * @param doubleClick the game took this press for the second of a double click
         */
        public void pressed(boolean doubleClick) {
            presses++;
            doublePress = doubleClick;
            inPress = true;
            inRelease = false;
        }

        public void pressDone() {
            inPress = false;
        }

        /** The game starts handling the button being let go. */
        public void released() {
            inRelease = true;
            inPress = false;
        }

        public void releaseDone() {
            inRelease = false;
        }

        /** Any click in any menu, before it is acted on: learns whether the handling is seen at all. */
        public void click() {
            handlingSeen |= inPress || inRelease;
        }

        /**
         * Whose the click arriving now is. Keys are not asked about; this uses the press up.
         *
         * @param input the game's name for the kind of click
         */
        public Origin origin(String input) {
            skipped = presses - used > 1;
            boolean first = presses != used;
            used = presses;
            if (!handlingSeen) {
                return Origin.OWN;
            }
            if (inRelease && doublePress && !"PICKUP".equals(input)) {
                // The sweep is a gathering or shift-clicks. A plain click as a double press is let
                // go is the player's, with something on the cursor. Their own shift-click then,
                // on another slot than the press before, cannot be told from the sweep and is
                // stopped with it, unheard: a slower click warns as usual.
                return Origin.GAME;
            }
            if (inPress || inRelease) {
                return first ? Origin.OWN : Origin.OTHER;
            }
            return Origin.OTHER;
        }

        /**
         * A press went by since the last click that caused no click at all: on the menu's frame,
         * or between two slots. The player did something else, which ends a wait for a second
         * click like any other click of theirs. Says so for the click {@link #origin} was last
         * asked about.
         */
        public boolean skippedAPress() {
            return skipped;
        }

        public int count() {
            return presses;
        }

        /** Whether clicks have been seen arriving inside the game's handling of the mouse button. */
        public boolean handlingSeen() {
            return handlingSeen;
        }
    }

    public enum Verdict {
        /** Nothing guarded is touched. */
        PASS,
        /** The second click on a warned turret. */
        CONFIRMED,
        /** Stopped, with the sound and the chat line: click again. */
        WARN,
        /** Stopped, with the sound and the chat line: a key or shortcut does not move it. */
        WARN_KEY,
        /** Stopped without a word. */
        BLOCK;

        public boolean stops() {
            return this == WARN || this == WARN_KEY || this == BLOCK;
        }
    }

    /**
     * One click, as the game is about to send it. The turrets are read off the items already,
     * null where there is none or where it cannot matter.
     *
     * @param menu        the open menu's id, new each time a menu opens
     * @param input       the game's name for the kind of click: PICKUP, QUICK_MOVE, SWAP, THROW...
     * @param button      for SWAP the hotbar slot whose key was pressed, 40 for the off hand
     * @param origin      whose click it is, from {@link Presses}
     * @param inInventory the clicked slot is one of the player's own, not the menu's
     * @param inSlot      the turret in the clicked slot
     * @param onKey       for SWAP, the turret in the hotbar slot whose key was pressed
     * @param carried     the turret on the cursor
     */
    public record Click(int menu, int slot, int button, String input, Origin origin, boolean inInventory,
                        TurretItem.Turret inSlot, TurretItem.Turret onKey, TurretItem.Turret carried) {
    }

    /** @param turret the guarded turret the click would move, null on PASS */
    public record Result(Verdict verdict, TurretItem.Turret turret) {
    }

    /** Compared by name, never subtracted from: sums with it overflow. */
    private static final long NEVER = Long.MIN_VALUE;
    private static final Result PASS = new Result(Verdict.PASS, null);

    /** The warned turret waiting for its second click, and when it was warned. */
    private String pending;
    private long pendingAt;
    /** The turret a key was last refused for, so a held key warns once and not thirty times a second. */
    private String keyWarned;
    private long keyWarnedAt;
    private long lastWarnAt = NEVER;

    /**
     * The salvage menu is a chest titled "Shift+Click Turrets Inside" (seen November 2026). Only
     * the letters are compared, read the way the server's name is, so colours, small capitals, any
     * other lettering and a reworded "Shift-Click" still match.
     */
    // UNVERIFIED: another menu with "turrets inside" in its title (the merger?) would be guarded
    // too, with the same chat line. One extra click there, never a lost turret.
    public static boolean isSalvageMenu(String title) {
        return title != null && title.length() <= MAX_TITLE && ServerName.letters(title).contains("turretsinside");
    }

    /**
     * The turret an item is, or null. An item named like a turret whose size cannot be read still
     * counts, as {@link #UNREADABLE}: if the server rewords the size line, every turret takes two
     * clicks instead of the guard going quiet.
     */
    public static TurretItem.Turret read(String title, List<String> lore) {
        Optional<TurretItem.Turret> turret = TurretItem.parse(title, lore);
        if (turret.isPresent()) {
            return turret.get();
        }
        if (title == null || title.length() > TurretItem.MAX_NAME) {
            return null;
        }
        String name = HalloweenText.strip(title);
        return TurretItem.fold(name).contains("turret")
                ? new TurretItem.Turret(name, UNREADABLE, 0) : null;
    }

    /** Above the size, not at it: with 1000 set, a 1000 turret moves on one click. */
    public static boolean guarded(TurretItem.Turret turret, int size) {
        return turret != null && turret.size() > size;
    }

    public Result check(Click click, int size, long now) {
        String input = click.input() == null ? "" : click.input();
        Origin origin = click.origin() == null ? Origin.OWN : click.origin();
        boolean key = input.equals("SWAP") || input.equals("THROW");
        boolean mouse = input.equals("PICKUP") || input.equals("QUICK_MOVE");

        // The slot under the mouse first; a number key pressed over the menu's own slots moves
        // the turret on that hotbar slot in instead.
        boolean fromSlot = (key || mouse) && click.inInventory() && guarded(click.inSlot(), size);
        boolean fromKey = !fromSlot && input.equals("SWAP") && !click.inInventory()
                && guarded(click.onKey(), size);
        if (!fromSlot && !fromKey) {
            if (input.equals("PICKUP_ALL")) {
                // The game's own click after a double click: it gathers every stack like the one
                // on the cursor. With a big turret there, that would pick up its twins without
                // their own second click. Never the player's click, so the wait goes on.
                return guarded(click.carried(), size) ? new Result(Verdict.BLOCK, click.carried()) : PASS;
            }
            if (key || origin == Origin.OWN) {
                // The player did something else: that ends the wait for a second click.
                pending = null;
            }
            return PASS;
        }

        TurretItem.Turret turret = fromSlot ? click.inSlot() : click.onKey();
        String id = click.menu() + (fromSlot ? "|slot " + click.slot() : "|key " + click.button())
                + "|" + turret.name() + "|" + turret.size() + "|" + turret.merges();

        if (!key && origin == Origin.GAME) {
            // Not a first click and not a second, and nothing the player needs telling about.
            return new Result(Verdict.BLOCK, turret);
        }
        if (key || origin == Origin.OTHER) {
            // Refused whatever came before, and told once: a held key repeats, and a shortcut
            // that never works must not fail without a word.
            pending = null;
            boolean warnedAlready = id.equals(keyWarned) && within(keyWarnedAt, CONFIRM_MS + 1, now);
            if (warnedAlready || within(lastWarnAt, QUIET_MS, now)) {
                return new Result(Verdict.BLOCK, turret);
            }
            keyWarned = id;
            keyWarnedAt = now;
            lastWarnAt = now;
            return new Result(Verdict.WARN_KEY, turret);
        }

        if (id.equals(pending) && within(pendingAt, MIN_GAP_MS, now)) {
            // The switch bounced. The wait goes on.
            return new Result(Verdict.BLOCK, turret);
        }
        if (id.equals(pending) && within(pendingAt, CONFIRM_MS + 1, now)) {
            pending = null;
            return new Result(Verdict.CONFIRMED, turret);
        }
        pending = null;
        if (within(lastWarnAt, QUIET_MS, now)) {
            // Stopped unheard, so it must not count as this turret's first click either.
            return new Result(Verdict.BLOCK, turret);
        }
        pending = id;
        pendingAt = now;
        lastWarnAt = now;
        return new Result(Verdict.WARN, turret);
    }

    /** At {@code since} or less than {@code span} after it. A clock set back is never within. */
    private static boolean within(long since, long span, long now) {
        return since != NEVER && now >= since && now - since < span;
    }

    /** The player did something else: no turret is waiting for its second click any more. */
    public void forget() {
        pending = null;
    }

    /** A new connection: nothing is waiting for a second click any more. */
    public void reset() {
        pending = null;
        pendingAt = 0L;
        keyWarned = null;
        keyWarnedAt = 0L;
        lastWarnAt = NEVER;
    }

    /** The chat line that goes with the warning sound. */
    public static String warning(TurretItem.Turret turret, int size, boolean byKey) {
        String what = turret.size() == UNREADABLE
                ? turret.name() + " looks like a turret but its size could not be read."
                : turret.name() + " is size " + number(turret.size()) + ", above your salvage warning of "
                        + number(size) + ".";
        String how = byKey ? " Keys and shortcuts do not move it here: click it twice with the mouse."
                : " Click it again within " + CONFIRM_MS / 1000L + " seconds to move it.";
        return "[MineRefine] " + what + how;
    }

    private static String number(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15
                ? String.format(Locale.ROOT, "%,d", (long) v) : String.format(Locale.ROOT, "%,.2f", v);
    }
}
