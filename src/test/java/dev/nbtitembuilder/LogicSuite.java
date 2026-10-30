package dev.nbtitembuilder;

import java.util.List;

/** Lightweight logic regression suite: cleanup must not change output. */
public class LogicSuite {
    static int passed = 0, failed = 0;

    static void eq(String what, String got, String want) {
        if (want.equals(got)) passed++;
        else { failed++; System.out.println("FAIL " + what + "\n  got: " + got + "\n  want: " + want); }
    }

    static void ok(String what, boolean cond) {
        if (cond) passed++;
        else { failed++; System.out.println("FAIL " + what); }
    }

    public static void main(String[] args) {
        // Registry-driven lists: inject sample data the way GameLists does.
        ItemSpec.setEnchants(List.of(
            new String[] {"sharpness", "Sharpness", "5"},
            new String[] {"my_cool_mod_enchant", "Modded Enchant from a mod", "7"}));
        ItemSpec.setAttributes(List.of(
            new String[] {"max_health", "Max Health"},
            new String[] {"modattr", "Modded Attribute"}));
        Components.bannerIds = List.of("base", "stripe_bottom", "modded_pattern");
        Components.trimPatternIds = List.of("sentry", "modded_sentry");
        Components.trimMaterialIds = List.of("quartz", "modded_ore");
        Components.songIds = List.of("11", "pigstep", "modded_song");
        Components.setInstruments(List.of("added_by_mod_goat_horn"));

        eq("options include modded", "" + ItemSpec.enchantOptions().size(), "2");
        eq("maxLevel injected", "" + ItemSpec.maxLevel("sharpness"), "5");
        eq("maxLevel modded", "" + ItemSpec.maxLevel("my_cool_mod_enchant"), "7");
        eq("modded attr label", ItemSpec.attributeOptions().get(1)[1], "Modded Attribute");
        eq("banner list", "" + Components.bannerIds.size(), "3");
        ok("instrument default first", Components.instrumentKeys[0].equals(""));
        ok("instrument labels built", Components.instrumentLabels[1].equals("Added By Mod"));
        ok("instrument modded present", Components.instrumentKeys.length == 2);

        // --- give command, vanilla shapes unchanged ---
        ItemModel m = new ItemModel();
        m.id = "diamond_sword";
        m.name = "Cool Sword";
        ItemModel.Ench e = new ItemModel.Ench();
        e.id = "sharpness";
        m.enchants.add(e);
        eq("give command", ItemSpec.giveCommand(m, "@s"),
            "give @s minecraft:diamond_sword[custom_name={text:\"Cool Sword\",italic:0b},"
            + "enchantments={\"minecraft:sharpness\":5}] 1");

        // Enchantment level default = registry max when box empty (5 for sharpness).
        ok("level defaulted", ItemSpec.giveCommand(m, "@s").contains("sharpness\":5"));

        // --- custom components untouched passthrough ---
        ItemModel m2 = new ItemModel();
        m2.id = "stick";
        m2.customComps = "my_mod_custom={Value:2}";
        eq("custom comp passthrough", ItemSpec.giveCommand(m2, "@s"),
            "give @s minecraft:stick[my_mod_custom={Value:2}] 1");

        // --- trim & banner round-trip in SNBT build ---
        ItemModel m3 = new ItemModel();
        m3.id = "diamond_chestplate";
        m3.trimMaterial = "modded_ore";
        m3.trimPattern = "modded_sentry";
        String nbt3 = ItemSpec.itemNbt(m3);
        ok("trim written", nbt3.contains(
            "\"minecraft:trim\":{material:\"minecraft:modded_ore\",pattern:\"minecraft:modded_sentry\"}"));

        ItemModel m4 = new ItemModel();
        m4.id = "minecraft:white_banner";
        ItemModel.BannerPat p = new ItemModel.BannerPat();
        p.pattern = "modded_pattern";
        p.color = "yellow";
        m4.bannerPats.add(p);
        String nbt4 = ItemSpec.itemNbt(m4);
        ok("banner layer written", nbt4.contains(
            "\"minecraft:banner_patterns\":[{pattern:\"minecraft:modded_pattern\",color:\"yellow\"}]"));

        // --- round trip: parse what we wrote ---
        ItemModel back = new ItemModel();
        ItemLoader.load(back, ItemSpec.itemNbt(m3));
        ok("trim parsed back", "modded_ore".equals(back.trimMaterial) && "modded_sentry".equals(back.trimPattern));
        ItemModel back4 = new ItemModel();
        ItemLoader.load(back4, ItemSpec.itemNbt(m4));
        ok("banner parsed back", back4.bannerPats.size() == 1
            && "modded_pattern".equals(back4.bannerPats.get(0).pattern));

        // --- humanize + suggest helpers still working ---
        eq("humanize", Components.humanize("modded_song"), "Modded Song");
        ok("suggest components", Components.suggestComponents("repair", 3).contains("minecraft:repairable"));

        // --- correct()/matches unaffected ---
        ok("matches", Components.matches("command_block", "Command block", "comm_"));
        eq("correct", Components.correct("custom_nama"), "custom_data");

        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) System.exit(1);
    }
}
