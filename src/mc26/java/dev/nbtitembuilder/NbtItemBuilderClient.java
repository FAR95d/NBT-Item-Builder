package dev.nbtitembuilder;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public class NbtItemBuilderClient implements ClientModInitializer {
    // Set by the commands, handled on the next client tick.
    private static boolean openBuilder = false;
    private static boolean openSaved = false;

    @Override
    public void onInitializeClient() {
        SavedItems.setFile(FabricLoader.getInstance().getConfigDir().resolve("nbtitembuilder-saved.snbt"));
        PictureImport.setDir(FabricLoader.getInstance().getConfigDir().resolve("nbtitembuilder-pictures"));

        // The commands run on the client only, so they work on any server.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("nbtbuilder")
                    .executes(ctx -> {
                        openBuilder = true;
                        return 1;
                    }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("nbtsaved")
                    .executes(ctx -> {
                        openSaved = true;
                        return 1;
                    }));
        });

        // The screens open one tick later, after the chat screen has closed itself.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openBuilder) {
                openBuilder = false;
                client.setScreenAndShow(new BuilderScreen());
            } else if (openSaved) {
                openSaved = false;
                client.setScreenAndShow(new SavedScreen(null));
            }
        });

        // A "Saved items" button in the survival and creative inventory screens.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
                Screens.getWidgets(screen).add(Button.builder(Component.literal("Saved items"),
                        b -> client.setScreenAndShow(new SavedScreen(null)))
                        .bounds(4, 4, 80, 16).build());
            }
        });
    }
}
