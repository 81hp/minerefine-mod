package minerefinehud.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import minerefinehud.hud.Anchor;
import minerefinehud.hud.HudLayout;
import minerefinehud.hud.HudModel;
import minerefinehud.hud.Theme;
import minerefinehud.progress.ProgressPlanner;
import minerefinehud.progress.ProgressSlot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** User settings. Changed in the settings screen and F6, or by hand in config.json. */
public final class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final HudLayout.Placement DEFAULT_MINE_PLACEMENT =
            new HudLayout.Placement(Anchor.TOP_LEFT, 4, 4);

    /** Middle left is empty in vanilla: chat is bottom left, effects and the sidebar are right. */
    public static final HudLayout.Placement DEFAULT_BOSS_PLACEMENT =
            new HudLayout.Placement(Anchor.MIDDLE_LEFT, 4, 0);

    /**
     * Which corner or edge the mine panel is pinned to. Named hud* because it predates the boss
     * panel having its own position, and renaming it would silently reset existing configs.
     *
     * Stored as a string so a hand-edited config with a typo degrades to the default instead of
     * refusing to parse and wiping every other setting with it.
     */
    public String hudAnchor = DEFAULT_MINE_PLACEMENT.anchor().name();

    /** Nudge away from that anchor, in pixels. Set by dragging in the position screen. */
    public int hudX = DEFAULT_MINE_PLACEMENT.offsetX();
    public int hudY = DEFAULT_MINE_PLACEMENT.offsetY();

    /** Same again for the boss timers, which can sit anywhere independently of the mine panel. */
    public String bossAnchor = DEFAULT_BOSS_PLACEMENT.anchor().name();
    public int bossX = DEFAULT_BOSS_PLACEMENT.offsetX();
    public int bossY = DEFAULT_BOSS_PLACEMENT.offsetY();

    /** Bottom right is clear in vanilla, and well away from the other two panels' defaults. */
    public static final HudLayout.Placement DEFAULT_PROGRESS_PLACEMENT =
            new HudLayout.Placement(Anchor.BOTTOM_RIGHT, -4, -4);

    /**
     * Before several bars existed, the one bar's item: SWORD, PICKAXE ... CHARM, or OFF. Only read
     * to carry an old config over into {@link #progressBars}, then set to OFF.
     */
    public String progressSlot = "OFF";

    /** One progress bar: the item it follows and how many are being bought together. */
    public static final class Bar {
        /** SWORD, PICKAXE, AXE, SHOVEL, HELMET, CHESTPLATE, LEGGINGS, BOOTS or CHARM. */
        public String slot = "SWORD";
        /** 1 for a single piece. 12 for twelve chestplates bought at the same tier. */
        public int quantity = 1;

        public Bar() {
        }

        public Bar(String slot, int quantity) {
            this.slot = slot;
            this.quantity = quantity;
        }

        public Optional<ProgressSlot> choice() {
            return ProgressSlot.parse(slot);
        }
    }

    /** At most this many bars. More stops being a glance. */
    public static final int MAX_BARS = 6;

    /** The progress bars, stacked in one panel in this order. Edited in the settings screen. */
    public java.util.List<Bar> progressBars = new java.util.ArrayList<>();

    /**
     * Block sprite to mine, learned by matching a shop balance against the mining total, e.g.
     * "block/cobblestone": "Rubble". Written by the mod; delete an entry to make it relearn.
     */
    public java.util.Map<String, String> minedBlocks = new java.util.LinkedHashMap<>();

    /**
     * Block sprites more than one mine shows, with those mines, e.g. "block/deepslate_copper_ore":
     * ["Woodland Copper", "Rust"]. Each block is then decided by which mine's balance the total
     * continues. Written by the mod; the mod also knows some by itself.
     */
    public java.util.Map<String, java.util.List<String>> sharedIcons = new java.util.LinkedHashMap<>();

    /**
     * Where each mine was last mined, dimension and x/z, so a shared icon is told apart by where
     * the player stands instead of needing a shop visit. Written by the mod.
     */
    public java.util.Map<String, minerefinehud.mine.MineSpots.Spot> mineSpots = new java.util.LinkedHashMap<>();

    /**
     * Last known balance per currency, so recognising a mine by its balance works straight after
     * logging in, without opening a shop. Written by the mod.
     */
    public java.util.Map<String, Long> savedBalances = new java.util.LinkedHashMap<>();

    /**
     * Which mine comes before which, read from shop prerequisites, e.g. "shovel|Debris":
     * "Suspicious Sand". Written by the mod; lets the progress bar follow dimensions newer than
     * the bundled data.
     */
    public java.util.Map<String, String> learnedProgression = new java.util.LinkedHashMap<>();
    public String progressAnchor = DEFAULT_PROGRESS_PLACEMENT.anchor().name();
    public int progressX = DEFAULT_PROGRESS_PLACEMENT.offsetX();
    public int progressY = DEFAULT_PROGRESS_PLACEMENT.offsetY();

    public boolean showMine = true;
    public boolean showBosses = true;

    /** Show the four armour pieces instead of the bundled armour cost. */
    public boolean showArmorPieces = false;

    /** Also show costs converted to credits. */
    public boolean showCredits = false;

    /** The job calculator's "block rate" field, in millions of blocks per credit. */
    public double blocksPerCreditMillions = 200.0;

    public int maxBosses = 5;

    // ------------------------------------------------------------- rows shown

    public boolean showSword = true;
    public boolean showTool = true;
    public boolean showArmor = true;
    public boolean showCharm = true;
    public boolean showMineTotal = true;
    /** The "Shovel IV/VI, Next: ..." block for the item in hand. */
    public boolean showHeldUpgrade = true;

    public boolean showBossUpTimers = true;
    public boolean showBossLearning = true;
    /** Bosses from every dimension rather than only the one being mined in. */
    public boolean showAllBossWorlds = false;

    public boolean showProgressText = true;
    /** Bars count every tier left to max the piece at its mine, not just the next tier. */
    public boolean progressToMax = false;
    public boolean showProgressBar = true;

    // ------------------------------------------------------- respawn reminder

    public boolean bossReminder = true;
    /** How long before a respawn the reminder fires. */
    public int bossReminderSeconds = 10;
    public boolean bossReminderSound = true;
    /** 0 to 100. */
    public int bossReminderVolume = 100;

    public static final HudLayout.Placement DEFAULT_ALERT_PLACEMENT =
            new HudLayout.Placement(Anchor.TOP_CENTER, 0, 40);
    public String alertAnchor = DEFAULT_ALERT_PLACEMENT.anchor().name();
    public int alertX = DEFAULT_ALERT_PLACEMENT.offsetX();
    public int alertY = DEFAULT_ALERT_PLACEMENT.offsetY();

    /** Bosses the spreadsheet does not list, mapped to the world they were heard in. Written by the mod. */
    public java.util.Map<String, String> bossWorlds = new java.util.LinkedHashMap<>();

    // ----------------------------------------------------------------- looks

    /** Size and background of one panel. */
    public static final class Look {
        /** 0.5 to 4. Set in the F6 screen with the size buttons or the scroll wheel. */
        public double scale = 1.0;
        public boolean background = false;
        /** Background opacity, 0 to 100. */
        public int opacity = 50;

        public Look() {
        }

        Look(double scale) {
            this.scale = scale;
        }
    }

    public Look mineLook = new Look();
    public Look bossLook = new Look();
    public Look progressLook = new Look();
    /** The reminder is meant to be noticed, so it starts out large. */
    public Look alertLook = new Look(2.0);

    /** Colours as "#RRGGBB". A value that does not parse falls back to the default for that colour. */
    public String colorHeader = Theme.format(Theme.defaults().header());
    public String colorLabel = Theme.format(Theme.defaults().label());
    public String colorValue = Theme.format(Theme.defaults().value());
    public String colorGood = Theme.format(Theme.defaults().good());
    public String colorWarn = Theme.format(Theme.defaults().warn());
    public String colorDim = Theme.format(Theme.defaults().dim());
    public String colorBar = Theme.format(Theme.defaults().barFill());
    public String colorBarDone = Theme.format(Theme.defaults().barDone());
    public String colorBarTrack = Theme.format(Theme.defaults().barTrack());

    public Theme theme() {
        Theme d = Theme.defaults();
        return new Theme(
                Theme.parseOr(colorHeader, d.header()),
                Theme.parseOr(colorLabel, d.label()),
                Theme.parseOr(colorValue, d.value()),
                Theme.parseOr(colorGood, d.good()),
                Theme.parseOr(colorWarn, d.warn()),
                Theme.parseOr(colorDim, d.dim()),
                Theme.parseOr(colorBar, d.barFill()),
                Theme.parseOr(colorBarDone, d.barDone()),
                Theme.parseOr(colorBarTrack, d.barTrack()));
    }

    public void resetColors() {
        ModConfig fresh = new ModConfig();
        colorHeader = fresh.colorHeader;
        colorLabel = fresh.colorLabel;
        colorValue = fresh.colorValue;
        colorGood = fresh.colorGood;
        colorWarn = fresh.colorWarn;
        colorDim = fresh.colorDim;
        colorBar = fresh.colorBar;
        colorBarDone = fresh.colorBarDone;
        colorBarTrack = fresh.colorBarTrack;
    }

    /** Hide the overlay entirely. Also toggled by the keybind in Options, Controls. */
    public boolean enabled = true;

    public HudLayout.Placement minePlacement() {
        return new HudLayout.Placement(
                Anchor.parse(hudAnchor).orElse(DEFAULT_MINE_PLACEMENT.anchor()), hudX, hudY);
    }

    public void setMinePlacement(HudLayout.Placement placement) {
        this.hudAnchor = placement.anchor().name();
        this.hudX = placement.offsetX();
        this.hudY = placement.offsetY();
    }

    public HudLayout.Placement bossPlacement() {
        return new HudLayout.Placement(
                Anchor.parse(bossAnchor).orElse(DEFAULT_BOSS_PLACEMENT.anchor()), bossX, bossY);
    }

    public void setBossPlacement(HudLayout.Placement placement) {
        this.bossAnchor = placement.anchor().name();
        this.bossX = placement.offsetX();
        this.bossY = placement.offsetY();
    }

    public HudLayout.Placement progressPlacement() {
        return new HudLayout.Placement(
                Anchor.parse(progressAnchor).orElse(DEFAULT_PROGRESS_PLACEMENT.anchor()),
                progressX, progressY);
    }

    public void setProgressPlacement(HudLayout.Placement placement) {
        this.progressAnchor = placement.anchor().name();
        this.progressX = placement.offsetX();
        this.progressY = placement.offsetY();
    }

    public HudLayout.Placement alertPlacement() {
        return new HudLayout.Placement(
                Anchor.parse(alertAnchor).orElse(DEFAULT_ALERT_PLACEMENT.anchor()), alertX, alertY);
    }

    public void setAlertPlacement(HudLayout.Placement placement) {
        this.alertAnchor = placement.anchor().name();
        this.alertX = placement.offsetX();
        this.alertY = placement.offsetY();
    }

    public HudModel.Options toOptions() {
        return new HudModel.Options(showMine, showArmorPieces, showCredits,
                blocksPerCreditMillions, showBosses, maxBosses,
                new HudModel.MineLines(showSword, showTool, showArmor, showCharm, showMineTotal, showHeldUpgrade),
                new HudModel.BossLines(showBossUpTimers, showBossLearning),
                new HudModel.ProgressLines(showProgressText, showProgressBar));
    }

    /**
     * Repairs whatever a hand edit or an older version left behind: missing sections, the old
     * single progress bar, out-of-range numbers. Run after every load.
     */
    void normalise() {
        if (progressBars == null) {
            progressBars = new java.util.ArrayList<>();
        }
        Optional<ProgressSlot> old = ProgressSlot.parse(progressSlot);
        if (old.isPresent() && progressBars.isEmpty()) {
            progressBars.add(new Bar(old.get().name(), 1));
        }
        progressSlot = "OFF";
        progressBars.removeIf(b -> b == null || b.choice().isEmpty());
        while (progressBars.size() > MAX_BARS) {
            progressBars.remove(progressBars.size() - 1);
        }
        for (Bar b : progressBars) {
            b.quantity = Math.max(1, Math.min(ProgressPlanner.ProgressView.MAX_QUANTITY, b.quantity));
        }

        if (minedBlocks == null) {
            minedBlocks = new java.util.LinkedHashMap<>();
        }
        if (sharedIcons == null) {
            sharedIcons = new java.util.LinkedHashMap<>();
        }
        if (mineSpots == null) {
            mineSpots = new java.util.LinkedHashMap<>();
        }
        if (savedBalances == null) {
            savedBalances = new java.util.LinkedHashMap<>();
        }
        if (learnedProgression == null) {
            learnedProgression = new java.util.LinkedHashMap<>();
        }
        if (bossWorlds == null) {
            bossWorlds = new java.util.LinkedHashMap<>();
        }
        mineLook = fix(mineLook, 1.0);
        bossLook = fix(bossLook, 1.0);
        progressLook = fix(progressLook, 1.0);
        alertLook = fix(alertLook, 2.0);

        maxBosses = Math.max(1, Math.min(MAX_BOSSES, maxBosses));
        bossReminderSeconds = Math.max(MIN_REMINDER_SECONDS, Math.min(MAX_REMINDER_SECONDS, bossReminderSeconds));
        bossReminderVolume = Math.max(0, Math.min(100, bossReminderVolume));
    }

    public static final int MAX_BOSSES = 15;
    public static final int MIN_REMINDER_SECONDS = 3;
    public static final int MAX_REMINDER_SECONDS = 60;

    private static Look fix(Look look, double defaultScale) {
        Look out = look == null ? new Look(defaultScale) : look;
        out.scale = HudLayout.clampScale(out.scale);
        out.opacity = Math.max(0, Math.min(100, out.opacity));
        return out;
    }

    public static ModConfig load(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                ModConfig loaded =
                        GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ModConfig.class);
                if (loaded != null) {
                    loaded.normalise();
                    return loaded;
                }
            }
        } catch (Exception ignored) {
            // Fall through to defaults rather than refusing to start.
        }
        return new ModConfig();
    }

    public void save(Path file) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
