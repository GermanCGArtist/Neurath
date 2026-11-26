package dev.neurath.net;

import com.google.gson.Gson;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Fabric custom payload on {@code neurath:spectate} for server-verified Spectate entry, hopping,
 * return, and anchor state synchronization.
 */
public record SpectatePayload(String json) implements CustomPacketPayload {
    public static final Identifier CHANNEL_ID = Identifier.fromNamespaceAndPath("neurath", "spectate");
    public static final Type<SpectatePayload> TYPE = new Type<>(CHANNEL_ID);
    private static final int MAX_JSON_BYTES = 32 * 1024;
    private static final Gson GSON = new Gson();

    public static final StreamCodec<FriendlyByteBuf, SpectatePayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.json() == null ? "{}" : payload.json(), MAX_JSON_BYTES),
            buf -> new SpectatePayload(buf.readUtf(MAX_JSON_BYTES))
    );

    public enum Action {
        ENTER_IN_PLACE,
        ENTER_OR_HOP,
        RETURN,
        STATE_SYNC,
        ERROR
    }

    public static final class Message {
        public Action action;
        public boolean active;
        public String targetDimension;
        public double targetX;
        public double targetY;
        public double targetZ;
        public float targetYaw;
        public float targetPitch;
        public String targetLabel;
        public SpectateAnchor anchor;
        public String error;
    }

    public static SpectatePayload encode(Message msg) {
        return new SpectatePayload(GSON.toJson(msg));
    }

    public Message decode() {
        try {
            return GSON.fromJson(json, Message.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
