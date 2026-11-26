package dev.neurath;

import dev.neurath.net.ServerSpectateManager;
import dev.neurath.net.SpectatePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Common Fabric entrypoint for Neurath. Registers the {@code neurath:spectate} payload and
 * manages optional server-side Spectate anchors and permissions.
 */
public final class NeurathMod implements ModInitializer {
    public static final String MOD_ID = "neurath";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    private volatile ServerSpectateManager spectateManager;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(SpectatePayload.TYPE, SpectatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SpectatePayload.TYPE, SpectatePayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                Path dir = server.getWorldPath(LevelResource.ROOT).resolve(MOD_ID);
                spectateManager = new ServerSpectateManager(dir);
                LOG.info("[Neurath] Server Spectate manager ready at {}", dir);
            } catch (IOException e) {
                LOG.error("[Neurath] Failed to initialize ServerSpectateManager", e);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> spectateManager = null);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerSpectateManager mgr = spectateManager;
            if (mgr != null) {
                mgr.onPlayerJoin(handler.getPlayer());
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(SpectatePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = context.server();
            server.execute(() -> {
                ServerSpectateManager mgr = spectateManager;
                if (mgr != null) {
                    mgr.handlePacket(server, player, payload);
                }
            });
        });
    }
}
