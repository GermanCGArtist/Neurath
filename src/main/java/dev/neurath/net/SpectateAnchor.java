package dev.neurath.net;

/**
 * Saved non-spectator return anchor so the player can always return to their exact starting
 * position, orientation, dimension, and previous gamemode after spectating.
 */
public record SpectateAnchor(
        String dimensionId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String previousGameMode
) {
    public String formattedCoords() {
        return String.format("X: %d · Y: %d · Z: %d", (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public String shortDimensionName() {
        if (dimensionId == null) return "OVERWORLD";
        if (dimensionId.endsWith("overworld")) return "OVERWORLD";
        if (dimensionId.contains("nether")) return "NETHER";
        if (dimensionId.contains("end")) return "THE END";
        int idx = dimensionId.indexOf(':');
        return (idx >= 0 ? dimensionId.substring(idx + 1) : dimensionId).toUpperCase();
    }
}
