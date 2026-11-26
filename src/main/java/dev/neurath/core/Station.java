package dev.neurath.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A public transit station anchored to Minecraft world coordinates with an optional manual pin
 * override on the Neurath octilinear transit diagram, supporting multiple transit modes and
 * selectable station signs (e.g. U-Bahn {@code U} + S-Bahn {@code S} side-by-side).
 */
public final class Station {
    public String id;
    public String code;
    public String name;
    public TransitMode mode = TransitMode.METRO;
    public Set<TransitMode> modes = new LinkedHashSet<>();
    public List<StationSign> signs = new ArrayList<>();
    public String dimensionId = "minecraft:overworld";
    public int x;
    public int y = 64;
    public int z;
    public boolean diagramPinned;
    public float diagramX;
    public float diagramY;
    public boolean extension = false;
    public String notes = "";

    public Station() {
        this.id = UUID.randomUUID().toString();
    }

    public Station(String id, String code, String name, TransitMode mode, String dimensionId, int x, int y, int z) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.code = sanitizeCode(code, name);
        this.name = (name == null || name.isBlank()) ? "Station" : name.trim();
        this.mode = mode == null ? TransitMode.METRO : mode;
        this.modes.add(this.mode);
        this.signs.add(StationSign.defaultForMode(this.mode));
        this.dimensionId = (dimensionId == null || dimensionId.isBlank()) ? "minecraft:overworld" : dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Returns all transit modes assigned to this station (backward compatible with legacy single-mode JSON). */
    public Set<TransitMode> effectiveModes() {
        Set<TransitMode> out = new LinkedHashSet<>();
        if (modes != null) out.addAll(modes);
        if (out.isEmpty() && mode != null) out.add(mode);
        return out;
    }

    public void toggleMode(TransitMode m) {
        if (m == null) return;
        if (modes == null) modes = new LinkedHashSet<>();
        if (modes.isEmpty() && mode != null) modes.add(mode);
        if (modes.contains(m)) {
            if (modes.size() > 1) {
                modes.remove(m);
                mode = modes.iterator().next();
            }
        } else {
            modes.add(m);
            if (mode == null) mode = m;
        }
    }

    public void toggleSign(StationSign sign) {
        if (sign == null) return;
        if (signs == null) signs = new ArrayList<>();
        if (signs.contains(sign)) {
            signs.remove(sign);
        } else {
            signs.add(sign);
        }
    }

    /**
     * Returns all signs to display next to this station: explicitly chosen signs plus any
     * additional default signs inferred from calling transit lines (e.g. an S-Bahn line calling
     * at a U-Bahn station adds the S sign automatically).
     */
    public List<StationSign> effectiveSigns(TransitNetwork network) {
        Set<StationSign> result = new LinkedHashSet<>();
        if (signs != null) {
            result.addAll(signs);
        }
        if (result.isEmpty()) {
            for (TransitMode m : effectiveModes()) {
                result.add(StationSign.defaultForMode(m));
            }
        }
        if (network != null) {
            for (TransitLine line : network.linesServing(this.id)) {
                if (line.mode == null) continue;
                boolean modeAlreadySigned = false;
                for (StationSign s : result) {
                    if (s.defaultMode() == line.mode) {
                        modeAlreadySigned = true;
                        break;
                    }
                }
                if (!modeAlreadySigned) {
                    result.add(StationSign.defaultForMode(line.mode));
                }
            }
        }
        return new ArrayList<>(result);
    }

    public String formattedSignPrefix(TransitNetwork network) {
        List<StationSign> eff = effectiveSigns(network);
        if (eff.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (StationSign s : eff) {
            sb.append('[').append(s.glyph()).append(']');
        }
        return sb.toString();
    }

    public static String sanitizeCode(String code, String fallbackName) {
        String src = (code != null && !code.isBlank()) ? code : fallbackName;
        if (src == null || src.isBlank()) return "STN";
        String cleaned = src.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (cleaned.isEmpty()) return "STN";
        return cleaned.length() > 4 ? cleaned.substring(0, 4) : cleaned;
    }

    public void pinAt(float dx, float dy) {
        this.diagramPinned = true;
        this.diagramX = dx;
        this.diagramY = dy;
    }

    public void unpin() {
        this.diagramPinned = false;
    }

    public String shortDimensionName() {
        if (dimensionId == null) return "Overworld";
        if (dimensionId.endsWith("overworld")) return "Overworld";
        if (dimensionId.endsWith("the_nether") || dimensionId.contains("nether")) return "Nether";
        if (dimensionId.endsWith("the_end") || dimensionId.contains("end")) return "The End";
        int colon = dimensionId.indexOf(':');
        return colon >= 0 ? dimensionId.substring(colon + 1) : dimensionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Station other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
