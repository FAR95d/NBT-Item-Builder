package dev.nbtitembuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the builder screen edits. Plain data, no Minecraft classes. */
public final class ItemModel {
    /** How a piece of text is colored and decorated. */
    public static final class ColorStyle {
        public static final int DEFAULT = 0;
        public static final int SOLID = 1;
        public static final int GRADIENT = 2;

        public int mode = DEFAULT;
        public int c1;
        public int c2;
        public boolean bold;
        public boolean underline;
        public boolean strikethrough;
        /** False writes italic:0b (the builder default); true leaves vanilla italics. */
        public boolean italic;

        public ColorStyle(int c1, int c2) {
            this.c1 = c1;
            this.c2 = c2;
        }
    }

    public static final class Ench {
        public String id = "";
        /** Any whole number from 1 to 255. Empty means the vanilla maximum. */
        public String level = "";
    }

    public static final class Attr {
        public String id = "";
        public String amount = "1";
        public String op = "add";
        public String slot = "mainhand";
    }

    /** One custom potion effect: amplifier 0-255, duration in seconds. Empty means defaults. */
    public static final class CustomEffect {
        public String id = "";
        public String amp = "";
        public String dur = "";
    }

    /** One banner layer: pattern id and dye color. */
    public static final class BannerPat {
        public String pattern = "";
        public String color = "";
    }

    public String id = "";
    public String count = "1";
    public boolean unbreakable;

    // Item tab: extra properties.
    /** "", "common", "uncommon", "rare" or "epic". Empty means the vanilla default. */
    public String rarity = "";
    /** 1-99. Empty means the vanilla default. */
    public String maxStack = "";
    /** Damage taken (0 and up). Empty means undamaged. */
    public String damage = "";
    /** Durability of the item. Empty means the vanilla default. */
    public String maxDamage = "";
    /** Anvil repair cost. Empty means 0. */
    public String repairCost = "";
    /** e.g. minecraft:stick. Empty means the vanilla model. */
    public String itemModelId = "";
    /** Raw SNBT for custom_model_data, e.g. {floats:[1]}. Empty means none. */
    public String customModelData = "";
    /** Raw SNBT for custom_data, e.g. {my_key:1}. Empty means none. */
    public String customData = "";
    /** "", "true" or "false". Empty means the vanilla default. */
    public String glint = "";

    public String name = "";
    /** Lore lines separated by |. */
    public String lore = "";
    /**
     * Picture rows shown after the lore text: one int per pixel (0xRRGGBB),
     * drawn as colored block characters. Empty means no picture.
     */
    public List<int[]> picture = new ArrayList<>();
    public ColorStyle nameStyle = new ColorStyle(0xFFAA00, 0x55FFFF);
    public ColorStyle loreStyle = new ColorStyle(0xAAAAAA, 0xFFFFFF);

    public List<Ench> enchants = new ArrayList<>();
    public List<Attr> attrs = new ArrayList<>();

    // Food & Gear tab.
    /** Nutrition (hunger points). Empty means the item is not food. */
    public String nutrition = "";
    /** Saturation. Empty means 0 when food is set. */
    public String saturation = "";
    public boolean alwaysEat;
    /** Seconds to eat. Empty means the vanilla 1.6s. Only used when food is set. */
    public String eatSeconds = "";
    /** Eat/drink animation: "", "eat" or "drink". */
    public String eatAnimation = "";
    /** Sound played while eating, e.g. entity.generic.eat. Empty means default. */
    public String eatSound = "";
    /** Equip slot: "" (not equipable), head, chest, legs, feet, mainhand, offhand, body, saddle. */
    public String equipSlot = "";
    /** Sound when equipped, e.g. item.armor.equip_diamond. Empty means default. */
    public String equipSound = "";
    /** Cooldown in seconds after use. Empty means none. */
    public String useCooldown = "";
    /** Item left behind after use (e.g. bowl, bucket). Empty means none. */
    public String useRemainder = "";

    // Block & NBT tab.
    /** Spawner mob, e.g. zombie. Empty means no spawner data is written. */
    public String spawnerEntity = "";
    /** Extra entity data merged into the spawner mob, e.g. {Value:100}. Empty means none. */
    public String entityData = "";
    public String spawnDelay = "";
    public String minDelay = "";
    public String maxDelay = "";
    public String spawnCount = "";
    public String spawnRange = "";
    public String requiredPlayers = "";
    public String maxNearby = "";
    /** Raw SNBT for block_entity_data, merged with the spawner fields above. Empty means none. */
    public String blockEntityRaw = "";
    /**
     * Extra components, one per entry separated by ;, each shaped like
     * {@code minecraft:some_component=SNBT}. Covers everything the tabs above
     * have no field for.
     */
    public String customComps = "";

    // More tab: potion & effects.
    /** Potion id, e.g. swiftness. Empty means none (water when effects are set). */
    public String potion = "";
    /** Potion bottle color as hex, e.g. #ff0000. Empty means the vanilla color. */
    public String potionColor = "";
    public List<CustomEffect> effects = new ArrayList<>();

    // More tab: banner.
    /** Shield/banner base dye. Empty means default. */
    public String bannerBase = "";
    public List<BannerPat> bannerPats = new ArrayList<>();

    // More tab: wear (trim + leather dye).
    public String trimMaterial = "";
    public String trimPattern = "";
    /** Armor piece the Wear preview shows: helmet, chestplate, leggings or boots. */
    public String trimPiece = "chestplate";
    /** Leather dye as hex, e.g. #ff0000. Empty means undyed. */
    public String dye = "";

    // More tab: blocks & bits.
    /** Bee count. Empty means none. Complex hives load into beesRaw instead. */
    public String beeCount = "";
    /** Beehive data too complex for the count slider, written back unchanged. */
    public String beesRaw;
    /** Firework flight duration. Empty means none. */
    public String flight = "";
    /** Loot table id for containers. Empty means none. */
    public String loot = "";
    /** Four sherd ids for decorated pots: back,left,right,front. Empty means none. */
    public String sherds = "";
    /** Player name for player heads. Empty means none. */
    public String headName = "";
    /** Goat horn instrument. Empty means default. */
    public String instrument = "";
    /** Jukebox song id. Empty means none. */
    public String song = "";

    /** Contents typed as text, see ItemSpec for the format. */
    public String contents = "";
    /** Container data this screen cannot express as text. It is written back unchanged. */
    public String rawContainer;
    /** Components the builder has no fields for, kept as they were loaded: full key to SNBT. */
    public Map<String, String> extras = new LinkedHashMap<>();

    public void reset() {
        id = "";
        count = "1";
        unbreakable = false;
        rarity = "";
        maxStack = "";
        damage = "";
        maxDamage = "";
        repairCost = "";
        itemModelId = "";
        customModelData = "";
        customData = "";
        glint = "";
        name = "";
        lore = "";
        picture = new ArrayList<>();
        nameStyle = new ColorStyle(0xFFAA00, 0x55FFFF);
        loreStyle = new ColorStyle(0xAAAAAA, 0xFFFFFF);
        enchants = new ArrayList<>();
        attrs = new ArrayList<>();
        nutrition = "";
        saturation = "";
        alwaysEat = false;
        eatSeconds = "";
        eatAnimation = "";
        eatSound = "";
        equipSlot = "";
        equipSound = "";
        useCooldown = "";
        useRemainder = "";
        spawnerEntity = "";
        entityData = "";
        spawnDelay = "";
        minDelay = "";
        maxDelay = "";
        spawnCount = "";
        spawnRange = "";
        requiredPlayers = "";
        maxNearby = "";
        blockEntityRaw = "";
        customComps = "";
        potion = "";
        potionColor = "";
        effects = new ArrayList<>();
        bannerBase = "";
        bannerPats = new ArrayList<>();
        trimMaterial = "";
        trimPattern = "";
        trimPiece = "chestplate";
        dye = "";
        beeCount = "";
        beesRaw = null;
        flight = "";
        loot = "";
        sherds = "";
        headName = "";
        instrument = "";
        song = "";
        contents = "";
        rawContainer = null;
        extras = new LinkedHashMap<>();
    }
}
