package minerefinehud.client;

import minerefinehud.halloween.HalloweenText;
import minerefinehud.shop.ShopItemParser;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Runs a menu command now and then (/bossindex, /stats) and reads the menu without the player
 * ever seeing it.
 *
 * The command makes the server send a menu. The mixin on the packet listener hands that menu here
 * instead of opening it: nothing is drawn, the player keeps moving and mining, and the menu's
 * items are read as they arrive. A moment after the last of them it is closed, as if the player
 * had pressed Escape. The server has it open for a fraction of a second.
 *
 * Only a menu that comes back within a few seconds of the command, with the expected title, is
 * taken, so a shop the player opens is never swallowed. Nothing is sent off MineRefine, nor
 * whenever {@link AutoCommands} holds it (a screen open, another command just sent, an
 * Expedition), nor while another poller waits for its own menu, so two hidden menus never cross.
 */
public abstract class MenuPoller {

    private static final long REPLY_WITHIN_MS = 2_500L;
    /** A reply later than that is still hidden when it comes, up to this, so lag never shows it. */
    private static final long LATE_REPLY_MS = 10_000L;
    /** Items stop coming this long after the last one: read and close. */
    private static final long SETTLE_MS = 150L;
    /** However the items come, the menu is closed after this. */
    private static final long GIVE_UP_MS = 2_000L;
    /** Checks in a row that read nothing before the automatic check turns itself off. */
    private static final int MAX_MISSES = 3;
    /** On MineRefine this long before the first check, so the sidebar and world have settled. */
    private static final long SETTLE_AFTER_JOIN_MS = 10_000L;

    private static final List<MenuPoller> INSTANCES = new CopyOnWriteArrayList<>();

    private final BooleanSupplier onMineRefine;

    private long sentAt;
    /** Until when a menu with the expected title is taken as the reply. */
    private long acceptUntil;
    private long lastPollAt;
    private long mineRefineSince;
    private int hiddenId = -1;
    private long hiddenSince;
    private long lastItemAt;
    private boolean gotItems;
    private final List<ItemStack> items = new ArrayList<>();
    private int misses;
    private boolean sendingOurs;
    /** One check asked for by the player ({@link #requestNow}), sent even with the automatic check off. */
    private boolean wanted;
    /** Hidden menus read, so the client game test can prove the mixin is applied. */
    private volatile int menusRead;

    MenuPoller(BooleanSupplier onMineRefine) {
        this.onMineRefine = onMineRefine;
        INSTANCES.add(this);
    }

    // ------------------------------------------------------- what differs

    /** The command, without the slash: "bossindex". */
    abstract String command();

    /** Whether the automatic check is on. */
    abstract boolean auto();

    abstract long intervalMs();

    /** Whether a menu with this title is the one asked for. */
    abstract boolean isMenuTitle(String title);

    /** Reads the menu's items, at the time given; true if there was anything in it. */
    abstract boolean read(List<ShopItemParser.ItemView> items, long now);

    /** Called after {@link #MAX_MISSES} checks in a row read nothing: turn the check off and say so. */
    abstract void giveUp(Minecraft client, int misses);

    // ----------------------------------------------------------- common

    int menusRead() {
        return menusRead;
    }

    /**
     * Sends the command once as soon as it may, automatic check on or off: right away on
     * MineRefine, else once the player is back on it.
     */
    void requestNow() {
        wanted = true;
    }

    /** For the client game test: take the next menu as this one's, as if the command had gone. */
    void expectNow() {
        sentAt = System.currentTimeMillis();
        acceptUntil = sentAt + LATE_REPLY_MS;
    }

    void register() {
        // Typed by the player: they want to see it, so it is never hidden.
        ClientSendMessageEvents.COMMAND.register(command -> {
            if (!sendingOurs && command != null && typed(command)) {
                sentAt = 0L;
                acceptUntil = 0L;
            }
        });
    }

    /** "/stats" or "/stats 81hp_" typed, but not "/statsreset". */
    private boolean typed(String command) {
        String c = command.trim().toLowerCase(Locale.ROOT);
        return c.equals(command()) || c.startsWith(command() + " ");
    }

    void onJoin() {
        sentAt = 0L;
        acceptUntil = 0L;
        misses = 0;
        hiddenId = -1;
        items.clear();
        mineRefineSince = 0L;
        // A first check soon after joining, not a whole interval in.
        lastPollAt = 0L;
    }

    /** Waiting for its menu or holding one: no other poller may send meanwhile. */
    private boolean busy(long now) {
        return hiddenId >= 0 || sentAt > 0L || now <= acceptUntil;
    }

    private static boolean anotherBusy(MenuPoller self, long now) {
        for (MenuPoller p : INSTANCES) {
            if (p != self && p.busy(now)) {
                return true;
            }
        }
        return false;
    }

    void tick(Minecraft client, long now) {
        if (hiddenId >= 0) {
            boolean settled = gotItems && now - lastItemAt >= SETTLE_MS;
            if (settled || now - hiddenSince >= GIVE_UP_MS) {
                finish(client, now);
            }
            return;
        }
        if (sentAt > 0L && now - sentAt > REPLY_WITHIN_MS) {
            sentAt = 0L;   // no menu came; try again next interval
            missed(client);
        }
        if (!onMineRefine.getAsBoolean()) {
            mineRefineSince = 0L;
            return;
        }
        if (mineRefineSince == 0L) {
            mineRefineSince = now;
        }
        if (!auto() && !wanted || sentAt > 0L || now - mineRefineSince < SETTLE_AFTER_JOIN_MS
                || !wanted && now - lastPollAt < intervalMs() || anotherBusy(this, now)) {
            return;
        }
        sendingOurs = true;
        try {
            // Held (a screen open, a command just sent, an Expedition): tried again next tick.
            if (AutoCommands.send(client, command(), now)) {
                wanted = false;
                lastPollAt = now;
                sentAt = now;
                acceptUntil = now + LATE_REPLY_MS;
            }
        } finally {
            sendingOurs = false;
        }
    }

    // ------------------------------------------------------- from the mixin

    /** A menu is opening. True to keep it from the screen: it is a menu one of the pollers asked for. */
    public static boolean onOpenScreen(int containerId, Component title) {
        String text = title == null ? "" : title.getString();
        boolean taken = false;
        for (MenuPoller p : INSTANCES) {
            if (!taken) {
                taken = p.open(containerId, text);
            } else {
                // Taken by another poller: one holding an older menu lets go of it, since the
                // server has closed it to open this one.
                p.release(containerId);
            }
        }
        return taken;
    }

    /** A menu's whole contents. True if it is a hidden one, taken here. */
    public static boolean onContent(int containerId, List<ItemStack> stacks) {
        for (MenuPoller p : INSTANCES) {
            if (p.hiddenId >= 0 && containerId == p.hiddenId) {
                p.items.clear();
                for (ItemStack s : stacks) {
                    p.items.add(s == null ? ItemStack.EMPTY : s.copy());
                }
                p.gotItems = true;
                p.lastItemAt = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    /** One slot of a menu. True if it is a hidden one, taken here. */
    public static boolean onSlot(int containerId, int slot, ItemStack stack) {
        for (MenuPoller p : INSTANCES) {
            if (p.hiddenId >= 0 && containerId == p.hiddenId) {
                if (slot >= 0 && slot < 1_000) {
                    while (p.items.size() <= slot) {
                        p.items.add(ItemStack.EMPTY);
                    }
                    p.items.set(slot, stack == null ? ItemStack.EMPTY : stack.copy());
                    p.gotItems = true;
                }
                p.lastItemAt = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    /** The server closing a menu. True if it is a hidden one: the player's own screen stays. */
    public static boolean onClose(int containerId) {
        for (MenuPoller p : INSTANCES) {
            if (p.hiddenId >= 0 && containerId == p.hiddenId) {
                // Read what came; it is already closed on the server's side.
                p.hiddenId = -1;
                p.readItems(Minecraft.getInstance(), System.currentTimeMillis());
                return true;
            }
        }
        return false;
    }

    /** Another poller took a new menu: one this poller was holding is gone on the server. */
    private void release(int containerId) {
        if (hiddenId >= 0 && hiddenId != containerId) {
            hiddenId = -1;
            readItems(Minecraft.getInstance(), System.currentTimeMillis());
        }
    }

    private boolean open(int containerId, String title) {
        long now = System.currentTimeMillis();
        boolean asked = now <= acceptUntil;
        // The menu can open itself again to refresh while it is being read.
        boolean reopened = hiddenId >= 0;
        boolean ours = isMenuTitle(title);
        if (reopened && !ours) {
            // Another menu took its place on the server (the player opened a shop): it is closed
            // there already, and closing it from here would close the shop instead.
            hiddenId = -1;
            readItems(Minecraft.getInstance(), now);
            return false;
        }
        if (!asked && !reopened || !ours) {
            return false;
        }
        sentAt = 0L;
        acceptUntil = 0L;
        if (!reopened) {
            hiddenSince = now;
            items.clear();
            gotItems = false;
        }
        hiddenId = containerId;
        lastItemAt = now;
        return true;
    }

    static String plain(String title) {
        return HalloweenText.plainLetters(HalloweenText.strip(title == null ? "" : title))
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private void finish(Minecraft client, long now) {
        int id = hiddenId;
        hiddenId = -1;
        try {
            if (client.getConnection() != null) {
                client.getConnection().send(new ServerboundContainerClosePacket(id));
            }
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError ignored) {
            // The server closes it the next time anything else opens.
        }
        readItems(client, now);
    }

    private void readItems(Minecraft client, long now) {
        List<ShopItemParser.ItemView> views = new ArrayList<>();
        for (ItemStack stack : items) {
            if (stack != null && !stack.isEmpty()) {
                views.add(new ShopItemParser.ItemView(stack.getHoverName().getString(), ShopScanner.lore(stack)));
            }
        }
        items.clear();
        menusRead++;
        boolean any;
        try {
            any = read(views, now);
        } catch (Exception ignored) {
            any = false;   // a bad item costs this reading, never the game
        }
        if (any) {
            misses = 0;
        } else {
            missed(client);
        }
    }

    /**
     * Nothing came back. A few times in a row means the command does not work as expected (a
     * changed command or menu): the check stops, rather than sending it for nothing for ever.
     */
    private void missed(Minecraft client) {
        // Only the automatic check's misses count: a one-off check asked for with it off must not
        // leave misses behind that turn it off as soon as the player turns it on.
        if (!auto() || ++misses < MAX_MISSES) {
            return;
        }
        misses = 0;
        try {
            giveUp(client, MAX_MISSES);
        } catch (Exception ignored) {
            // Telling the player is a courtesy; failing to must not cost the tick.
        }
    }

    /** For /mrhud debug. */
    String describe() {
        return auto() ? "every " + minerefinehud.boss.Interval.format(intervalMs()) : "off";
    }
}
