package dev.neurath.client.spectate;

import dev.liskov.core.SemanticColor;
import dev.liskov.ui.theme.MubWidgets;
import dev.neurath.net.SpectateAnchor;
import dev.neurath.net.SpectatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;

import java.util.Locale;

/**
 * Client-side Spectate controller supporting both:
 * <ul>
 *     <li>Server-verified Spectate over {@code neurath:spectate} when Neurath is installed on the server</li>
 *     <li>Client-only fallback using {@code /gamemode} + {@code /execute in <dim> run tp} on vanilla/OP servers</li>
 * </ul>
 * Preserves the original non-spectator {@link SpectateAnchor} when hopping between multiple map targets.
 */
public final class ClientSpectateController {
    private static final ClientSpectateController INSTANCE = new ClientSpectateController();

    private boolean spectating;
    private SpectateAnchor anchor;
    private String targetLabel = "";

    private ClientSpectateController() {}

    public static ClientSpectateController get() {
        return INSTANCE;
    }

    public boolean isSpectating() {
        return spectating;
    }

    public SpectateAnchor anchor() {
        return anchor;
    }

    public String targetLabel() {
        return targetLabel;
    }

    public void initNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(SpectatePayload.TYPE, (payload, context) -> {
            SpectatePayload.Message msg = payload.decode();
            if (msg == null) return;
            context.client().execute(() -> handleS2C(msg));
        });
    }

    public void resetOnDisconnect() {
        spectating = false;
        anchor = null;
        targetLabel = "";
    }

    private void handleS2C(SpectatePayload.Message msg) {
        if (msg.action == SpectatePayload.Action.STATE_SYNC) {
            this.spectating = msg.active;
            this.anchor = msg.anchor;
            this.targetLabel = msg.targetLabel != null ? msg.targetLabel : "";
            if (msg.active) {
                MubWidgets.ToastHost.push(
                        "Spectating " + (targetLabel.isBlank() ? "area" : targetLabel) + " — press [V] to return",
                        SemanticColor.PLAYER);
            } else {
                MubWidgets.ToastHost.push("Returned to saved anchor", SemanticColor.MR);
            }
        } else if (msg.action == SpectatePayload.Action.ERROR && msg.error != null) {
            MubWidgets.ToastHost.push("Spectate: " + msg.error, SemanticColor.DANGER);
        }
    }

    /**
     * Standalone shortcut (default V): toggles Spectator mode in-place or returns to the saved anchor.
     */
    public void toggleStandalone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        if (spectating) {
            returnToAnchor();
            return;
        }

        if (ClientPlayNetworking.canSend(SpectatePayload.TYPE)) {
            SpectatePayload.Message req = new SpectatePayload.Message();
            req.action = SpectatePayload.Action.ENTER_IN_PLACE;
            req.targetLabel = "In-Place Spectate";
            ClientPlayNetworking.send(SpectatePayload.encode(req));
            return;
        }

        // Client-only command fallback
        saveLocalAnchorIfNeeded(mc);
        spectating = true;
        targetLabel = "In-Place Spectate";
        sendCommand(mc, "gamemode spectator");
        MubWidgets.ToastHost.push(
                "Saved anchor (" + anchor.formattedCoords() + ") · Entered SPECTATOR",
                SemanticColor.PLAYER);
    }

    /**
     * Enters Spectator mode (preserving any existing anchor) and teleports above the given world point.
     */
    public void spectatePoint(String dimensionId, double x, double y, double z, float yaw, float pitch, String label) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        String dim = (dimensionId == null || dimensionId.isBlank()) ? currentDimensionId(mc) : dimensionId;
        String lbl = (label == null || label.isBlank())
                ? String.format("Point (%d, %d)", (int) Math.floor(x), (int) Math.floor(z))
                : label;

        if (ClientPlayNetworking.canSend(SpectatePayload.TYPE)) {
            SpectatePayload.Message req = new SpectatePayload.Message();
            req.action = SpectatePayload.Action.ENTER_OR_HOP;
            req.targetDimension = dim;
            req.targetX = x;
            req.targetY = y;
            req.targetZ = z;
            req.targetYaw = yaw;
            req.targetPitch = pitch;
            req.targetLabel = lbl;
            ClientPlayNetworking.send(SpectatePayload.encode(req));
            return;
        }

        // Client-only command fallback
        saveLocalAnchorIfNeeded(mc);
        spectating = true;
        targetLabel = lbl;
        sendCommand(mc, "gamemode spectator");
        sendCommand(mc, String.format(Locale.ROOT,
                "execute in %s run tp @s %.2f %.2f %.2f %.1f %.1f",
                dim, x, y, z, yaw, pitch));
        MubWidgets.ToastHost.push("Spectating " + lbl + " — press [V] to return", SemanticColor.PLAYER);
    }

    /**
     * Computes an elevated framing viewpoint above a selected map area and spectates it.
     */
    public void spectateArea(String dimensionId, double minX, double minZ, double maxX, double maxZ, int surfaceY) {
        double cx = (minX + maxX) * 0.5;
        double cz = (minZ + maxZ) * 0.5;
        double span = Math.max(Math.abs(maxX - minX), Math.abs(maxZ - minZ));
        double altitude = Math.min(280.0, Math.max(surfaceY + 28.0, surfaceY + 24.0 + span * 0.55));
        float pitch = span < 12.0 ? 45f : 68f;
        String label = span < 12.0
                ? String.format("Map (%d, %d)", (int) Math.floor(cx), (int) Math.floor(cz))
                : String.format("Area %dx%d (Y=%d)", (int) Math.round(span), (int) Math.round(span), (int) Math.round(altitude));
        spectatePoint(dimensionId, cx, altitude, cz, 0f, pitch, label);
    }

    public void returnToAnchor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        if (ClientPlayNetworking.canSend(SpectatePayload.TYPE)) {
            SpectatePayload.Message req = new SpectatePayload.Message();
            req.action = SpectatePayload.Action.RETURN;
            ClientPlayNetworking.send(SpectatePayload.encode(req));
            return;
        }

        if (anchor == null) {
            spectating = false;
            return;
        }
        SpectateAnchor saved = anchor;
        spectating = false;
        anchor = null;
        targetLabel = "";
        sendCommand(mc, String.format(Locale.ROOT,
                "execute in %s run tp @s %.2f %.2f %.2f %.1f %.1f",
                saved.dimensionId(), saved.x(), saved.y(), saved.z(), saved.yaw(), saved.pitch()));
        sendCommand(mc, "gamemode " + saved.previousGameMode());
        MubWidgets.ToastHost.push(
                "Returned to " + saved.formattedCoords() + " · Restored " + saved.previousGameMode().toUpperCase(Locale.ROOT),
                SemanticColor.MR);
    }

    private void saveLocalAnchorIfNeeded(Minecraft mc) {
        if (spectating && anchor != null) return; // preserve original anchor across hops
        LocalPlayer p = mc.player;
        String prevMode = detectLocalGameMode(mc);
        anchor = new SpectateAnchor(
                currentDimensionId(mc),
                p.getX(), p.getY(), p.getZ(),
                p.getYRot(), p.getXRot(),
                prevMode
        );
    }

    private static String detectLocalGameMode(Minecraft mc) {
        ClientPacketListener conn = mc.getConnection();
        if (conn != null && mc.player != null) {
            PlayerInfo info = conn.getPlayerInfo(mc.player.getUUID());
            if (info != null && info.getGameMode() != null && info.getGameMode() != GameType.SPECTATOR) {
                return info.getGameMode().getName();
            }
        }
        return GameType.SURVIVAL.getName();
    }

    private static String currentDimensionId(Minecraft mc) {
        if (mc.level != null) {
            return mc.level.dimension().identifier().toString();
        }
        return "minecraft:overworld";
    }

    private static void sendCommand(Minecraft mc, String commandWithoutSlash) {
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand(commandWithoutSlash);
        }
    }
}
