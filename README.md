# NBT Item Builder (Fabric, Minecraft 1.21.10 and 1.21.11)

An in-game screen for building items with custom names, lore, enchantments, attribute
modifiers and container contents. Type `/nbtbuilder` in chat to open it.

The screen starts empty. It has five tabs:

- **Item**: item id, count, unbreakable.
- **Style**: name and lore text, each with *Default*, *Solid* or *Gradient* colouring. Pick
  colours on the colour wheel (drag the bar beside it for brightness) or type a hex code.
  "Name" and "Name end" are the two ends of a name gradient, same for lore.
- **Enchants**: press **+ Add enchantment**, pick from the list with **v** or type an id, and
  type any level from 1 to 255 (empty uses the vanilla maximum).
- **Attributes**: press **+ Add attribute**, pick an attribute, set the amount, operation and slot.
- **Contents**: items inside a barrel, chest or shulker box, written as text.

Buttons along the bottom:

- **Load held item** reads the item in your main hand into the builder so you can edit it.
- **Apply to held item** replaces your main-hand item with the result.
- **View NBT** shows the item NBT or the `/give` command, with colours, and can copy either.
- **Give to me**, **Copy command**, **Reset**, **Close**.

Components the builder has no field for (and container contents too complex to show as
text) are kept untouched when you load an item and write it back.

The mod is client-side only. It works on any server, but **Give to me** and **Apply to held
item** run commands as you, so you need cheats on (singleplayer) or operator permission
(server). **Copy command** puts the full `/give` command on your clipboard so you can paste
it into a command block.

## Getting the jar

You do not need a GitHub repository. Pick whichever fits you.

### Option 1: build on your computer

You need JDK 21 and an internet connection (Gradle downloads Minecraft and Fabric).

```
./gradlew build          # Minecraft 1.21.11. On Windows: gradlew.bat build
./gradlew build -Pminecraft_version=1.21.10 -Pfabric_api_version=0.138.4+1.21.10
```

The jar is in `build/libs/`, named like `nbt-item-builder-1.1.0+mc1.21.11.jar`. Ignore the
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

1. Install Fabric Loader 0.19.3 or newer for your Minecraft version.
2. Put the Fabric API that matches your Minecraft version in your `mods` folder.
3. Put the mod jar in the same `mods` folder.

Use the jar built for your version. The mod declares that it supports both 1.21.10 and
1.21.11, and it only uses long-standing client classes, but a jar compiled for the exact
version is the safest choice.

## Contents syntax

`item [xCount] [@slot] [name="Text"] [enchant=level] [unbreakable]`, separated by `;`.
Slots are numbered from 0 and filled in order unless you set one. Up to 27 items.

Example: `netherite_pickaxe efficiency=5 mending; torch x64`

Attributes replace an item's defaults. A sword with a custom `attack_damage` loses its
normal attack speed unless you add `attack_speed -2.4` back.

The status line above the buttons shows the command length, or what to fix.

## Notes

- The mod writes the 1.21.5+ item component format, which 1.21.10 and 1.21.11 both use.
- The item logic (`ItemSpec`, `ItemLoader`, `Snbt`, `Colors`, `ItemModel`) has no Minecraft dependencies, so it can be tested on its own.
- Written against the Fabric example mod templates for 1.21.10 and 1.21.11, with official
  Mojang mappings. Screen and command class names are the Mojang ones.
