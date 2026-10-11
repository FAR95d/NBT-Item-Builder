package dev.nbtitembuilder;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class NbtItemBuilderClient implements ClientModInitializer {
    // Set by the command, handled on the next client tick.
    private static boolean openRequested = false;

    @Override
    public void onInitializeClient() {
        // /nbtbuilder runs on the client only, so it works on any server.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("nbtbuilder")
                        .executes(ctx -> {
                            openRequested = true;
                            return 1;
                        })));

        // The screen opens one tick later, after the chat screen has closed itself.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openRequested) {
                openRequested = false;
                client.setScreen(new BuilderScreen());
            }
        });
    }
}
