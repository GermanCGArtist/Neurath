package dev.neurath.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.liskov.core.LiskovPaths;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side Spectate manager: validates permissions, persists each player's original
 * non-spectator {@link SpectateAnchor} in {@code <world>/neurath/spectate-anchors.json},
 * executes cross-dimension teleports and gamemode switches, and syncs state back to the client.
 */
public final class ServerSpectateManager {
    private static final Logger LOG = LoggerFactory.getLogger("neurath-spectate");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ANCHOR_MAP_TYPE = new TypeToken<Map<String, SpectateAnchor>>() {}.getType();

    private final Path anchorFile;
    private final Map<UUID, SpectateAnchor> activeAnchors = new ConcurrentHashMap<>();

    public ServerSpectateManager(Path worldNeurathDir) throws IOException {
        Files.createDirectories(worldNeurathDir);
        this.anchorFile = worldNeurathDir.resolve("spectate-anchors.json");
        loadFromDisk();
    }

    public boolean canSpectate(ServerPlayer player) {
        if (player == null) return false;
        return player.createCommandSourceStack().permissions()
                .hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS));
    }

    public void onPlayerJoin(ServerPlayer player) {
        SpectateAnchor saved = activeAnchors.get(player.getUUID());
        if (saved != null) {
            sendSync(player, true, saved, "Resumed Spectate");
        }
    }

    public void handlePacket(MinecraftServer server, ServerPlayer player, SpectatePayload payload) {
        SpectatePayload.Message msg = payload.decode();
        if (msg == null || msg.action == null) return;

        if (!canSpectate(player)) {
            sendError(player, "Permission denied (requires OP level 2+).");
            return;
        }

        switch (msg.action) {
            case ENTER_IN_PLACE -> enterInPlace(player, msg.targetLabel);
            case ENTER_OR_HOP -> enterOrHop(server, player, msg);
            case RETURN -> returnToAnchor(server, player);
            default -> {}
        }
    }

    private void enterInPlace(ServerPlayer player, String label) {
        SpectateAnchor anchor = activeAnchors.computeIfAbsent(player.getUUID(), id -> captureAnchor(player));
        saveToDisk();
        player.setGameMode(GameType.SPECTATOR);
        sendSync(player, true, anchor, (label == null || label.isBlank()) ? "In-Place Spectate" : label);
    }

    private void enterOrHop(MinecraftServer server, ServerPlayer player, SpectatePayload.Message msg) {
        SpectateAnchor anchor = activeAnchors.computeIfAbsent(player.getUUID(), id -> captureAnchor(player));
        saveToDisk();

        ServerLevel targetLevel = resolveLevel(server, msg.targetDimension);
        if (targetLevel == null) {
            targetLevel = player.level();
        }
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(targetLevel, msg.targetX, msg.targetY, msg.targetZ, Set.of(), msg.targetYaw, msg.targetPitch, false);
        sendSync(player, true, anchor, msg.targetLabel);
    }

    private void returnToAnchor(MinecraftServer server, ServerPlayer player) {
        SpectateAnchor anchor = activeAnchors.remove(player.getUUID());
        saveToDisk();
        if (anchor == null) {
            sendSync(player, false, null, "");
            return;
        }
        ServerLevel origLevel = resolveLevel(server, anchor.dimensionId());
        if (origLevel == null) {
            origLevel = server.overworld();
        }
        player.teleportTo(origLevel, anchor.x(), anchor.y(), anchor.z(), Set.of(), anchor.yaw(), anchor.pitch(), false);
        GameType restored = GameType.byName(anchor.previousGameMode(), GameType.SURVIVAL);
        if (restored == GameType.SPECTATOR) restored = GameType.SURVIVAL;
        player.setGameMode(restored);
        sendSync(player, false, null, "");
    }

    private static SpectateAnchor captureAnchor(ServerPlayer player) {
        GameType cur = player.gameMode();
        if (cur == null || cur == GameType.SPECTATOR) cur = GameType.SURVIVAL;
        String dim = player.level().dimension().identifier().toString();
        return new SpectateAnchor(dim, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), cur.getName());
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) return server.overworld();
        Identifier id = Identifier.tryParse(dimensionId);
        if (id == null) return server.overworld();
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        return server.getLevel(key);
    }

    private static void sendSync(ServerPlayer player, boolean active, SpectateAnchor anchor, String label) {
        SpectatePayload.Message out = new SpectatePayload.Message();
        out.action = SpectatePayload.Action.STATE_SYNC;
        out.active = active;
        out.anchor = anchor;
        out.targetLabel = label;
        ServerPlayNetworking.send(player, SpectatePayload.encode(out));
    }

    private static void sendError(ServerPlayer player, String error) {
        SpectatePayload.Message out = new SpectatePayload.Message();
        out.action = SpectatePayload.Action.ERROR;
        out.error = error;
        ServerPlayNetworking.send(player, SpectatePayload.encode(out));
    }

    private void loadFromDisk() {
        if (!Files.isRegularFile(anchorFile)) return;
        try {
            String raw = Files.readString(anchorFile, StandardCharsets.UTF_8);
            Map<String, SpectateAnchor> loaded = GSON.fromJson(raw, ANCHOR_MAP_TYPE);
            if (loaded != null) {
                for (Map.Entry<String, SpectateAnchor> e : loaded.entrySet()) {
                    try {
                        activeAnchors.put(UUID.fromString(e.getKey()), e.getValue());
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Exception e) {
            LOG.warn("[Neurath] Failed loading spectate anchors from {}", anchorFile, e);
        }
    }

    private void saveToDisk() {
        try {
            Map<String, SpectateAnchor> out = new ConcurrentHashMap<>();
            for (Map.Entry<UUID, SpectateAnchor> e : activeAnchors.entrySet()) {
                out.put(e.getKey().toString(), e.getValue());
            }
            LiskovPaths.writeAtomicUtf8(anchorFile, GSON.toJson(out));
        } catch (Exception e) {
            LOG.warn("[Neurath] Failed saving spectate anchors to {}", anchorFile, e);
        }
    }
}
