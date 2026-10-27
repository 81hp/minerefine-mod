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
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/**
 * Opens /bossindex now and then without the player ever seeing it, so every boss timer stays
 * right in every world.
 *
 * The command makes the server send a menu. The mixin on the packet listener hands that menu here
 * instead of opening it: nothing is drawn, the player keeps moving and mining, and the menu's
 * items are read as they arrive. A moment after the last of them it is closed, as if the player
 * had pressed Escape. The server has it open for a fraction of a second.
 *
 * Only a menu that comes back within a few seconds of the command, with the index's title,
 * is taken, so a shop the player opens is never swallowed. Nothing is sent off MineRefine, nor
 * whenever {@link AutoCommands} holds it: a screen open, another command just sent, an Expedition.
 */
public final class BossIndexPoller {

    /** Never more often than this, whatever is typed: each check is a command to the server. */
    public static final long MIN_INTERVAL_MS = 30_000L;
    private static final long REPLY_WITHIN_MS = 2_500L;
    /** A reply later than that is still hidden when it comes, up to this, so lag never shows it. */
    private static final long LATE_REPLY_MS = 10_000L;
    /** Items stop coming this long after the last one: read and close. */
    private static final long SETTLE_MS = 150L;
    /** However the items come, the menu is closed after this. */
    private static final long GIVE_UP_MS = 2_000L;
    /** Checks in a row that brought no bosses before the automatic check turns itself off. */
    private static final int MAX_MISSES = 3;

    private static volatile BossIndexPoller instance;

    private final ModConfig config;
    private final Runnable save;
    private final BooleanSupplier onMineRefine;
    /** Reads the boss items out of the menu, at the time given; true if there were any. */
    private final BiFunction<List<ShopItemParser.ItemView>, Long, Boolean> read;

    private long sentAt;
    /** Until when a menu with the index's title is taken as the reply. */
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
    /** Hidden menus read, so the client game test can prove the mixin is applied. */
    private static volatile int menusRead;

    BossIndexPoller(ModConfig config, Runnable save, BooleanSupplier onMineRefine,
                    BiFunction<List<ShopItemParser.ItemView>, Long, Boolean> read) {
        this.config = config;
        this.save = save;
        this.onMineRefine = onMineRefine;
        this.read = read;
        instance = this;
    }

    public static int readCount() {
        return menusRead;
    }

    /** For the client game test: take the next menu as the index's, as if the command had gone. */
    public static void expectForTest() {
        BossIndexPoller p = instance;
        if (p != null) {
            p.sentAt = System.currentTimeMillis();
            p.acceptUntil = p.sentAt + LATE_REPLY_MS;
        }
    }

    void register() {
        // Typed by the player: they want to see it, so it is never hidden.
        ClientSendMessageEvents.COMMAND.register(command -> {
            if (!sendingOurs && command != null && plain(command).startsWith("bossindex")) {
                sentAt = 0L;
                acceptUntil = 0L;
            }
        });
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

    long intervalMs() {
        return Math.max(MIN_INTERVAL_MS, config.bossIndexEveryMs);
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
            missed();
        }
        if (!onMineRefine.getAsBoolean()) {
            mineRefineSince = 0L;
            return;
        }
        if (mineRefineSince == 0L) {
            mineRefineSince = now;
        }
        if (!config.bossIndexAuto || sentAt > 0L || now - mineRefineSince < 10_000L
                || now - lastPollAt < intervalMs()) {
            return;
        }
        sendingOurs = true;
        try {
            // Held (a screen open, a command just sent, an Expedition): tried again next tick.
            if (AutoCommands.send(client, "bossindex", now)) {
                lastPollAt = now;
                sentAt = now;
                acceptUntil = now + LATE_REPLY_MS;
            }
        } finally {
            sendingOurs = false;
        }
    }

    // ------------------------------------------------------- from the mixin

    /** A menu is opening. True to keep it from the screen: it is the index asked for. */
    public static boolean onOpenScreen(int containerId, Component title) {
        BossIndexPoller p = instance;
        return p != null && p.open(containerId, title == null ? "" : title.getString());
    }

    /** A menu's whole contents. True if it is the hidden one, taken here. */
    public static boolean onContent(int containerId, List<ItemStack> stacks) {
        BossIndexPoller p = instance;
        if (p == null || containerId != p.hiddenId || p.hiddenId < 0) {
            return false;
        }
        p.items.clear();
        for (ItemStack s : stacks) {
            p.items.add(s == null ? ItemStack.EMPTY : s.copy());
        }
        p.gotItems = true;
        p.lastItemAt = System.currentTimeMillis();
        return true;
    }

    /** One slot of a menu. True if it is the hidden one, taken here. */
    public static boolean onSlot(int containerId, int slot, ItemStack stack) {
        BossIndexPoller p = instance;
        if (p == null || containerId != p.hiddenId || p.hiddenId < 0) {
            return false;
        }
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

    /** The server closing a menu. True if it is the hidden one: the player's own screen stays. */
    public static boolean onClose(int containerId) {
        BossIndexPoller p = instance;
        if (p == null || p.hiddenId < 0 || containerId != p.hiddenId) {
            return false;
        }
        // Read what came; it is already closed on the server's side.
        p.hiddenId = -1;
        p.readItems(System.currentTimeMillis());
        return true;
    }

    private boolean open(int containerId, String title) {
        long now = System.currentTimeMillis();
        boolean asked = now <= acceptUntil;
        // The index can open itself again to refresh while it is being read.
        boolean reopened = hiddenId >= 0;
        boolean index = isIndexTitle(title);
        if (reopened && !index) {
            // Another menu took its place on the server (the player opened a shop): it is closed
            // there already, and closing it from here would close the shop instead.
            hiddenId = -1;
            readItems(now);
            return false;
        }
        if (!asked && !reopened || !index) {
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

    /**
     * The index's title as last seen open with bosses in it, or before that any title with "boss"
     * in it. A different title shows the menu once, is learned from its items, and is hidden from
     * then on.
     */
    boolean isIndexTitle(String title) {
        String plain = plain(title);
        if (config.bossIndexTitle != null && !config.bossIndexTitle.isBlank()) {
            return plain.equals(plain(config.bossIndexTitle));
        }
        return plain.contains("boss");
    }

    /** A screen the player has open held bosses: remember its title as the index's. */
    void learnTitle(String title) {
        if (title != null && !title.isBlank() && !plain(title).equals(plain(config.bossIndexTitle))) {
            config.bossIndexTitle = title;
            save.run();
        }
    }

    private static String plain(String title) {
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
        readItems(now);
    }

    private void readItems(long now) {
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
            any = Boolean.TRUE.equals(read.apply(views, now));
        } catch (Exception ignored) {
            any = false;   // a bad item costs this reading, never the game
        }
        if (any) {
            misses = 0;
        } else {
            missed();
        }
    }

    /**
     * No bosses came back. A few times in a row means /bossindex does not work as expected (a
     * changed command or menu): the check stops, rather than sending it for nothing for ever, and
     * says so.
     */
    private void missed() {
        if (++misses < MAX_MISSES || !config.bossIndexAuto) {
            return;
        }
        misses = 0;
        config.bossIndexAuto = false;
        save.run();
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            Compat.chat(client, Component.literal("[MineRefine] /bossindex brought no boss timers "
                    + MAX_MISSES + " times in a row, so the automatic check is off. Turn it back on in "
                    + "Settings, Bosses."));
        }
    }
}
