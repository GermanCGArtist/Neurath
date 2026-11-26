package dev.neurath.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.liskov.core.SemanticColor;
import dev.liskov.net.LiskovReloadBus;
import dev.liskov.ui.host.LiskovUiHost;
import dev.liskov.ui.theme.MubWidgets;
import dev.neurath.NeurathMod;
import dev.neurath.client.jm.JmFullscreenToolStripLayer;
import dev.neurath.client.spectate.ClientSpectateController;
import dev.neurath.client.ui.SpectateHudPillLayer;
import dev.neurath.client.ui.TransitDiagramWindow;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Fabric Client entrypoint for Neurath.
 * Wires Liskov storage/UI layers, Spectate networking + standalone keybind, JourneyMap tool strip,
 * and Command Palette entries.
 */
public final class NeurathClientMod implements ClientModInitializer {
    private static KeyMapping spectateKey;
    private static KeyMapping diagramKey;

    @Override
    public void onInitializeClient() {
        NeurathClientState.get().initStorage();
        ClientSpectateController.get().initNetworking();

        // Register Liskov ImGui layers
        LiskovUiHost.register(SpectateHudPillLayer.get());
        LiskovUiHost.register(JmFullscreenToolStripLayer.get());
        LiskovUiHost.register(TransitDiagramWindow.get());

        // Reload transit network when Liskov Sync updates a .ntransit file
        LiskovReloadBus.subscribe(notice -> {
            if (notice != null && notice.relativePath() != null && notice.relativePath().endsWith(".ntransit")) {
                NeurathClientState.get().loadForContext(resolveCurrentContextKey());
                MubWidgets.ToastHost.push("Reloaded synced transit map (v" + notice.versionNum() + ")", SemanticColor.MR);
            }
        });

        // Switch network context per world/server on join and reset spectate state on disconnect
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> NeurathClientState.get().loadForContext(resolveCurrentContextKey())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(() -> ClientSpectateController.get().resetOnDisconnect()));

        // Register keybindings
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(NeurathMod.MOD_ID, "main"));
        spectateKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.neurath.spectate", InputConstants.KEY_V, cat));
        diagramKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.neurath.diagram", InputConstants.KEY_N, cat));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (spectateKey.consumeClick()) {
                ClientSpectateController.get().toggleStandalone();
            }
            while (diagramKey.consumeClick()) {
                TransitDiagramWindow.get().toggle();
            }
        });

        registerCommandPalette();
    }

    private static void registerCommandPalette() {
        MubWidgets.CommandPalette.register(new MubWidgets.CommandPalette.Entry(
                "neurath.diagram", "Neurath: Open Transit Diagram (N / J Menu)", "Nt", SemanticColor.FOCUS,
                () -> TransitDiagramWindow.get().toggle()));
        MubWidgets.CommandPalette.register(new MubWidgets.CommandPalette.Entry(
                "neurath.spectate", "Neurath: Toggle Spectate / Return to Anchor (V)", "Sp", SemanticColor.PLAYER,
                () -> ClientSpectateController.get().toggleStandalone()));
        MubWidgets.CommandPalette.register(new MubWidgets.CommandPalette.Entry(
                "neurath.add_station", "Neurath: Add Transit Station at Current Position", "Nt", SemanticColor.MR,
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.player != null) {
                        String dim = mc.level != null ? mc.level.dimension().identifier().toString() : "minecraft:overworld";
                        TransitDiagramWindow.get().editModal().openNewStation(
                                dim,
                                (int) Math.floor(mc.player.getX()),
                                (int) Math.floor(mc.player.getY()),
                                (int) Math.floor(mc.player.getZ())
                        );
                    }
                }));
    }

    private static String resolveCurrentContextKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return "default";
        if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null) {
            return "server_" + mc.getCurrentServer().ip;
        }
        if (mc.getSingleplayerServer() != null) {
            return "world_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        return "default";
    }
}
