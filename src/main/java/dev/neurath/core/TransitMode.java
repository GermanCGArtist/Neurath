package dev.neurath.core;

import dev.liskov.core.SemanticColor;

import java.util.Locale;

/**
 * Supported public transit modes for stations and lines in Neurath.
 */
public enum TransitMode {
    METRO("Metro", "MT", 0xF45B69, SemanticColor.DANGER, LineStyle.SOLID),
    RAIL("Rail / Train", "RL", 0x38BDF8, SemanticColor.PLAYER, LineStyle.DOUBLE),
    TRAM("Tram", "TR", 0xF5B731, SemanticColor.WARN, LineStyle.SOLID),
    ICE("Ice Boat Highway", "IC", 0x22D37E, SemanticColor.MR, LineStyle.SOLID),
    NETHER("Nether Hub", "NH", 0xC084FC, SemanticColor.BOT, LineStyle.DASHED),
    FERRY("Ferry / Port", "FR", 0x0EA5E9, SemanticColor.PLAYER, LineStyle.DASHED),
    AIR("Elytra / Air", "AR", 0xF97316, SemanticColor.CF, LineStyle.DASHED);

    public enum LineStyle {
        SOLID,
        DASHED,
        DOTTED,
        DOUBLE
    }

    private final String displayName;
    private final String monogram;
    private final int defaultRgb;
    private final SemanticColor semanticColor;
    private final LineStyle defaultLineStyle;

    TransitMode(String displayName, String monogram, int defaultRgb, SemanticColor semanticColor, LineStyle defaultLineStyle) {
        this.displayName = displayName;
        this.monogram = monogram;
        this.defaultRgb = defaultRgb;
        this.semanticColor = semanticColor;
        this.defaultLineStyle = defaultLineStyle;
    }

    public String displayName() {
        return displayName;
    }

    public String monogram() {
        return monogram;
    }

    public int defaultRgb() {
        return defaultRgb;
    }

    public SemanticColor semanticColor() {
        return semanticColor;
    }

    public LineStyle defaultLineStyle() {
        return defaultLineStyle;
    }

    public static TransitMode fromName(String raw) {
        if (raw == null || raw.isBlank()) return METRO;
        String norm = raw.trim().toUpperCase(Locale.ROOT);
        for (TransitMode m : values()) {
            if (m.name().equals(norm) || m.monogram.equalsIgnoreCase(norm)) {
                return m;
            }
        }
        return METRO;
    }
}
