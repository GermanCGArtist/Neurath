package dev.neurath.core;

import java.util.Locale;

/**
 * Selectable public transit station signs (e.g. U-Bahn {@code U}, S-Bahn {@code S}, Metro {@code M},
 * Rail {@code R}, Tram {@code T}, Bus {@code B}, Ice {@code I}, Nether {@code N}, Ferry {@code F}, Air {@code A}).
 * Multiple signs can be assigned to a single station and rendered side-by-side.
 */
public enum StationSign {
    UBAHN("U", "U-Bahn", Shape.SQUARE, 0x0052A5, 0xFFFFFF, TransitMode.METRO),
    SBAHN("S", "S-Bahn", Shape.CIRCLE, 0x008D4F, 0xFFFFFF, TransitMode.RAIL),
    METRO("M", "Metro", Shape.SQUARE, 0xD92332, 0xFFFFFF, TransitMode.METRO),
    RAIL("R", "Regional Rail", Shape.SQUARE, 0xB91C1C, 0xFFFFFF, TransitMode.RAIL),
    TRAM("T", "Tram", Shape.SQUARE, 0xEAB308, 0x0C0E14, TransitMode.TRAM),
    BUS("B", "Bus", Shape.CIRCLE, 0x9333EA, 0xFFFFFF, TransitMode.TRAM),
    ICE("I", "Ice Boat", Shape.SQUARE, 0x0EA5E9, 0x0C0E14, TransitMode.ICE),
    NETHER("N", "Nether Hub", Shape.SQUARE, 0x7C3AED, 0xFFFFFF, TransitMode.NETHER),
    FERRY("F", "Ferry / Port", Shape.CIRCLE, 0x0284C7, 0xFFFFFF, TransitMode.FERRY),
    AIR("A", "Elytra / Air", Shape.SQUARE, 0xEA580C, 0xFFFFFF, TransitMode.AIR);

    public enum Shape {
        SQUARE,
        CIRCLE
    }

    private final String glyph;
    private final String displayName;
    private final Shape shape;
    private final int bgRgb;
    private final int fgRgb;
    private final TransitMode defaultMode;

    StationSign(String glyph, String displayName, Shape shape, int bgRgb, int fgRgb, TransitMode defaultMode) {
        this.glyph = glyph;
        this.displayName = displayName;
        this.shape = shape;
        this.bgRgb = bgRgb;
        this.fgRgb = fgRgb;
        this.defaultMode = defaultMode;
    }

    public String glyph() {
        return glyph;
    }

    public String displayName() {
        return displayName;
    }

    public Shape shape() {
        return shape;
    }

    public int bgRgb() {
        return bgRgb;
    }

    public int fgRgb() {
        return fgRgb;
    }

    public TransitMode defaultMode() {
        return defaultMode;
    }

    public static StationSign defaultForMode(TransitMode mode) {
        if (mode == null) return UBAHN;
        return switch (mode) {
            case METRO -> UBAHN;
            case RAIL -> SBAHN;
            case TRAM -> TRAM;
            case ICE -> ICE;
            case NETHER -> NETHER;
            case FERRY -> FERRY;
            case AIR -> AIR;
        };
    }

    public static StationSign fromName(String raw) {
        if (raw == null || raw.isBlank()) return UBAHN;
        String norm = raw.trim().toUpperCase(Locale.ROOT);
        for (StationSign s : values()) {
            if (s.name().equals(norm) || s.glyph.equalsIgnoreCase(norm)) return s;
        }
        return UBAHN;
    }
}
