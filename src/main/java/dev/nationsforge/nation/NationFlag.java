package dev.nationsforge.nation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores a nation's custom banner-style flag.
 *
 * Represented internally as a base {@link DyeColorId} (0–15) plus up to
 * {@value #MAX_LAYERS} overlay layers, each with a banner-pattern code and a
 * dye colour.  The data maps 1-to-1 onto Minecraft's banner NBT so the flag
 * can be rendered as a standard {@link ItemStack}.
 */
public class NationFlag {

    public static final int MAX_LAYERS = 6;

    /** All banner pattern short-codes available in the flag editor. */
    public static final String[] PATTERN_CODES = {
        "ts",  "bs",  "ls",  "rs",  "cs",  "ms",
        "sc",  "cr",  "ld",  "rd",  "hh",  "hhb",
        "vh",  "vhr", "bt",  "tt",  "bo",  "mr",
        "ss",  "gra", "gru", "bri", "cbo", "mc"
    };

    /** Human-readable names matching {@link #PATTERN_CODES} index-for-index. */
    public static final String[] PATTERN_NAMES = {
        "Top Stripe",    "Bottom Stripe",  "Left Stripe",   "Right Stripe",
        "Center Stripe", "Mid Stripe",     "Square Cross",  "Diagonal Cross",
        "Left Diagonal", "Right Diagonal", "Top Half",      "Bottom Half",
        "Left Half",     "Right Half",     "Bot Triangle",  "Top Triangle",
        "Border",        "Rhombus",        "Small Stripes", "Gradient Down",
        "Gradient Up",   "Bricks",         "Curly Border",  "Circle"
    };

    /**
     * Packed RGB (no alpha) colours for each DyeColor ordinal 0–15.
     * Used for colour-picker swatches in the GUI.
     */
    public static final int[] DYE_COLORS_RGB = {
        0xF9FFFE, // 0  WHITE
        0xF9801D, // 1  ORANGE
        0xC74EBD, // 2  MAGENTA
        0x3AB3DA, // 3  LIGHT_BLUE
        0xFED83D, // 4  YELLOW
        0x80C71F, // 5  LIME
        0xF38BAA, // 6  PINK
        0x474F52, // 7  GRAY
        0x9D9D97, // 8  LIGHT_GRAY
        0x169C9C, // 9  CYAN
        0x8932B8, // 10 PURPLE
        0x3C44AA, // 11 BLUE
        0x835432, // 12 BROWN
        0x5E7C16, // 13 GREEN
        0xB02E26, // 14 RED
        0x1D1D21  // 15 BLACK
    };

    /** Dye colour names matching index 0–15. */
    public static final String[] DYE_COLOR_NAMES = {
        "White","Orange","Magenta","Lt. Blue","Yellow","Lime","Pink","Gray",
        "Lt. Gray","Cyan","Purple","Blue","Brown","Green","Red","Black"
    };

    private static final Item[] BANNER_ITEMS = {
        Items.WHITE_BANNER, Items.ORANGE_BANNER, Items.MAGENTA_BANNER, Items.LIGHT_BLUE_BANNER,
        Items.YELLOW_BANNER, Items.LIME_BANNER, Items.PINK_BANNER, Items.GRAY_BANNER,
        Items.LIGHT_GRAY_BANNER, Items.CYAN_BANNER, Items.PURPLE_BANNER, Items.BLUE_BANNER,
        Items.BROWN_BANNER, Items.GREEN_BANNER, Items.RED_BANNER, Items.BLACK_BANNER
    };

    // ── Fields ───────────────────────────────────────────────────────────────────

    /** DyeColor ordinal (0 = WHITE … 15 = BLACK). */
    private int baseColorId = 0;
    private final List<Layer> layers = new ArrayList<>();

    // ── Layer ────────────────────────────────────────────────────────────────────

    public static class Layer {
        /** Minecraft banner pattern short-code, e.g. {@code "bs"}. */
        public final String patternCode;
        /** DyeColor ordinal 0–15. */
        public final int colorId;

        public Layer(String patternCode, int colorId) {
            this.patternCode = patternCode;
            this.colorId = colorId & 0xF;
        }

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("pattern", patternCode);
            tag.putInt("color", colorId);
            return tag;
        }

        public static Layer fromNBT(CompoundTag tag) {
            return new Layer(tag.getString("pattern"), tag.getInt("color"));
        }
    }

    // ── Construction ─────────────────────────────────────────────────────────────

    public NationFlag() {}

    public NationFlag(int baseColorId) {
        this.baseColorId = baseColorId & 0xF;
    }

    /** Deep-copy constructor. */
    public NationFlag(NationFlag src) {
        this.baseColorId = src.baseColorId;
        for (Layer l : src.layers) this.layers.add(new Layer(l.patternCode, l.colorId));
    }

    // ── Mutation ─────────────────────────────────────────────────────────────────

    public void setBaseColorId(int id) { this.baseColorId = id & 0xF; }

    public boolean addLayer(String patternCode, int colorId) {
        if (layers.size() >= MAX_LAYERS) return false;
        if (!isValidPattern(patternCode)) return false;
        layers.add(new Layer(patternCode, colorId));
        return true;
    }

    public void removeLayer(int index) {
        if (index >= 0 && index < layers.size()) layers.remove(index);
    }

    public void setLayer(int index, String patternCode, int colorId) {
        if (index >= 0 && index < layers.size()) {
            layers.set(index, new Layer(patternCode, colorId));
        }
    }

    // ── Accessors ────────────────────────────────────────────────────────────────

    public int getBaseColorId() { return baseColorId; }
    public List<Layer> getLayers() { return Collections.unmodifiableList(layers); }
    public int getLayerCount() { return layers.size(); }

    // ── Rendering helper ─────────────────────────────────────────────────────────

    /**
     * Builds an {@link ItemStack} representing this flag as a Minecraft banner.
     * The stack has the appropriate base-colour banner item and {@code BlockEntityTag}
     * NBT with all overlay patterns applied.
     */
    public ItemStack buildBannerStack() {
        int idx = Math.max(0, Math.min(15, baseColorId));
        ItemStack stack = new ItemStack(BANNER_ITEMS[idx]);

        if (!layers.isEmpty()) {
            ListTag patternList = new ListTag();
            for (Layer layer : layers) {
                CompoundTag entry = new CompoundTag();
                entry.putString("Pattern", layer.patternCode);
                // Minecraft banner NBT stores color as (15 - dyeOrdinal)
                entry.putInt("Color", 15 - layer.colorId);
                patternList.add(entry);
            }
            CompoundTag bet = new CompoundTag();
            bet.put("Patterns", patternList);
            stack.addTagElement("BlockEntityTag", bet);
        }

        return stack;
    }

    // ── NBT ──────────────────────────────────────────────────────────────────────

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("base", baseColorId);
        ListTag layerList = new ListTag();
        for (Layer l : layers) layerList.add(l.toNBT());
        tag.put("layers", layerList);
        return tag;
    }

    public static NationFlag fromNBT(CompoundTag tag) {
        NationFlag flag = new NationFlag(tag.getInt("base"));
        ListTag layerList = tag.getList("layers", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < layerList.size() && i < MAX_LAYERS; i++) {
            flag.layers.add(Layer.fromNBT(layerList.getCompound(i)));
        }
        return flag;
    }

    // ── Validation ───────────────────────────────────────────────────────────────

    public static boolean isValidPattern(String code) {
        if (code == null) return false;
        for (String c : PATTERN_CODES) if (c.equals(code)) return true;
        return false;
    }

    /** Returns the display name for a pattern code, or the code itself as fallback. */
    public static String patternDisplayName(String code) {
        for (int i = 0; i < PATTERN_CODES.length; i++) {
            if (PATTERN_CODES[i].equals(code)) return PATTERN_NAMES[i];
        }
        return code;
    }
}
