# NBT Item Builder (Fabric, Minecraft 1.21.10, 1.21.11 and 26.2)

An in-game screen for building items with custom names, lore, enchantments, attribute
modifiers, food, equipment, spawner data and any other component. Type `/nbtbuilder` in chat to open it.

A live preview of the item sits in the top-right corner. Hover it to see its tooltip,
just like in an inventory.

New here? Follow the three steps on the Item tab:

1. **Pick an item**
2. **Style it** on the next tabs: name, colors, enchantments, food, spawner settings.
   Leave any box empty to keep the vanilla default. Boxes with fixed options get
   dropdowns, icon buttons or sliders instead of typing.
3. **Give to me** (or **Apply to held item**, or **Copy command** for a command block).

The screen starts empty. It has seven tabs. Numbers are slider + box pairs almost
everywhere: drag for speed, type for exact. Anything with a fixed set of options is a
**Pick** list or a cycling button instead of typing.

- **Item**: item id (dropdown with icons), count slider capped by the max
  stack size, rarity, max stack size slider (1-99), damage and max damage sliders
  (capped by the real item), repair slider, item model (dropdown), glint, unbreakable,
  custom model data and custom data. Items with durability (tools, armor) must keep
  max stack 1 — the builder says so before the game has to.
- **Style**: name and lore text, each with *Default*, *Solid* or *Gradient* colouring. Pick
  colours on the colour wheel (drag the bar beside it for brightness) or type a hex code.
  "Name" and "Name end" are the two ends of a name gradient, same for lore.
  **Bold**, **Under**, **Strike** and **Italic** toggle decorations for the side shown
  above them (pick Name or Lore first).
  **Add picture...** picks an image from the `nbtitembuilder-pictures` folder in your
  `config` folder (put .png or .jpg files there first) and turns it into colored rows
  at the end of the lore, scaled to 16 wide. **Clear picture** removes it again.
- **Enchants**: press **+ Add enchantment**, **Pick** from the list or type an id, then
  drag the slider or type any level from 1 to 255 (empty uses the vanilla maximum).
  Enchanted books automatically use stored enchantments.
- **Attrs**: press **+ Add attribute**, pick an attribute, set the amount, operation and slot.
- **Food**: set **Nutrition** to make any item edible, plus saturation, always-edible,
  eat time, eat/drink animation and sound. Pick an **equip slot** to make it wearable,
  with an equip sound. Cooldown and remainder (dropdown) control reuse. Every number
  is a labeled slider next to its box: drag, or type exact.
- **Block**: container contents, spawner mob (every summonable entity, with an
  entity-data box for extras like xp amounts and falling-block states) and sliders,
  extra block data and custom components.
  Hold the items to store and press **Add hand item** to put them in one by one.
- **More**: switch the submenu for **Potion** (dropdown, bottle color, custom effects
  with level and duration sliders, live bottle preview), **Banner** (base color,
  pattern layers with per-layer previews, live banner preview), **Wear** (trim
  material as ingredient icons, pattern dropdown with template icon, armor piece
  icons, live preview of that piece worn on an armor stand, leather dye sliders), and **Blocks** (beehive
  count, firework flight, loot tables, pot sherds, player heads, goat horn
  instruments and jukebox songs with disc icons).

Tabs run down the left, commands down the right, so the middle stays open for editing.
**Saved items** sits top-left, like in the inventory.

- **Load held item** reads the item in your main hand into the builder so you can edit it.
  Spawner numbers, food, equipment and the other new fields are all read back, so a
  spawner's spawn delay and mob can be changed directly.
- **Apply to held item** replaces your main-hand item with the result.
- **Item NBT** shows the item NBT or the `/give` command, with colours, and can copy either.
  Press **Edit NBT** there to change the raw item data by hand: keys complete with **Tab**,
  and mistakes get a "did you mean ...?" suggestion. Editing only changes the builder;
  giving still runs a command (see below).
- **Give to me**, **Copy command**, **Reset**, **Close**.

### Saved items

Press the **Saved items** button in the top-left of your inventory (survival or creative), run
`/nbtsaved`, or use the **Saved items** button in the builder.

- **Save builder item** stores what is currently in the builder. **Save held item** stores the
  item in your main hand exactly as it is. The name box is optional.
- **Load** puts the item into the builder. **Give** runs `/give` for it straight away.
- Click an item's name to select it, then use **Rename selected** (type the new name in the
  box), **Move up** or **Move down**. The × button asks "Sure?" before it deletes.

The list is saved in `config/nbtitembuilder-saved.snbt`, so it survives restarting the game
and works across servers and worlds. Copy that file to move your list to another computer.
An item copied from a server only contains what the server sends to your client.

Components the builder has no field for (and container contents too complex to show as
text) are kept untouched when you load an item and write it back.

The mod is client-side only. It works on any server, but **Give to me** and **Apply to held
item** run commands as you, so you need cheats on (singleplayer, including creative) or operator permission
(server). **Copy command** puts the full `/give` command on your clipboard so you can paste
it into a command block. Placing a custom spawner also needs operator permission.

## Getting the jar

You do not need a GitHub repository. Pick whichever fits you.

### Option 1: build on your computer

You need JDK 21 for the 1.21.x jars, JDK 25 for the 26.x jars, and an internet
connection (Gradle downloads Minecraft and Fabric).

```
./gradlew build          # Minecraft 1.21.11. On Windows: gradlew.bat build
./gradlew build -Pminecraft_version=1.21.10 -Pfabric_api_version=0.138.4+1.21.10
./gradlew build -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2   # needs JDK 25
```

The jar is in `build/libs/`, named like `nbt-item-builder-1.2.0+mc1.21.11.jar`. Ignore the
`-sources` jar. Build once per Minecraft version you play.

### Option 2: let GitHub build it (no Java needed)

A free GitHub account is enough, and the repository can be private.

1. Create a new repository on github.com.
2. Upload the contents of this folder. The hidden `.github` folder must be included,
   because it holds the build recipe. With git installed, this is simpler:
   ```
   git init
   git add .
   git commit -m "NBT Item Builder"
   git branch -M main
   git remote add origin https://github.com/YOUR-NAME/nbt-item-builder.git
   git push -u origin main
   ```
3. Open the repository's **Actions** tab and wait for the **build** run to finish.
4. Open the run and download the artifact for your Minecraft version. It is a zip with the jar inside.

The workflow builds both versions on every push, so a green run for both means the
source compiles against each. Artifacts are kept for 90 days.

## Install

1. Install Fabric Loader 0.19.5 or newer for your Minecraft version.
2. Put the Fabric API that matches your Minecraft version in your `mods` folder.
3. Put the mod jar in the same `mods` folder. Playing 26.x needs Java 25.

Use the jar built for your version. Each jar only supports the version it was
compiled for (1.21.10, 1.21.11 or 26.2): the 1.21 and 26 code paths differ, so
mixing them cannot work.

## Contents syntax

`item [xCount] [@slot] [name="Text"] [enchant=level] [unbreakable]`, separated by `;`.
Slots are numbered from 0 and filled in order unless you set one. Up to 27 items.

Example: `netherite_pickaxe efficiency=5 mending; torch x64`

## Spawners

Hold a spawner and press **Load held item**, or type `spawner` on the Item tab. On the
Block & NBT tab, pick the mob (the **v** button lists them, typing completes with **Tab**)
and set the spawn delay, min/max delay, spawn count, spawn range, player range and max
nearby entities. Anything else in the block data stays in the "Extra block data" box and
is written back unchanged.

## Custom components

Everything without its own field goes in the **Custom** box on the Block & NBT tab, one
entry per `;`, shaped like `minecraft:some_component=SNBT`:

```
minecraft:note_block_sound="block.stone.break"; minecraft:tooltip_style="minecraft:tas"
```

The **v** button lists common components and typing completes with **Tab**. Entries that
already have their own tab or field (food, rarity, ...) are rejected there so there is
exactly one place to edit each thing.

Attributes replace an item's defaults. A sword with a custom `attack_damage` loses its
normal attack speed unless you add `attack_speed -2.4` back.

The status line above the buttons shows the command length, or what to fix.

## Notes

- The mod writes the 1.21.5+ item component format, which 1.21.10, 1.21.11 and 26.2 all use.
- The item logic (`ItemSpec`, `ItemLoader`, `Snbt`, `Colors`, `Components`, `ItemModel`) has no Minecraft dependencies, so it can be tested on its own.
- Written against the Fabric example mod templates for 1.21.10, 1.21.11 and 26.2. The 26.x
  game is unobfuscated, so that jar uses Mojang's real names directly; the 1.21 jars use
  official Mojang mappings.
- This project was fully vibecoded so some things may be buggy in the UI.
