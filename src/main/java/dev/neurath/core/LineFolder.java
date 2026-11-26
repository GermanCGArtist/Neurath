package dev.neurath.core;

import java.util.Objects;
import java.util.UUID;

/**
 * A named folder in the Lines outliner used to group transit lines by system or region
 * (e.g. "U-Bahn", "S-Bahn", "Nether Express") with folder-level visibility and collapse state.
 */
public final class LineFolder {
    public String id;
    public String name;
    public boolean visible = true;
    public boolean collapsed = false;
    public int rgbColor = 0x818CF8;

    public LineFolder() {
        this.id = UUID.randomUUID().toString();
        this.name = "System";
    }

    public LineFolder(String id, String name, int rgbColor) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.name = (name == null || name.isBlank()) ? "System" : name.trim();
        this.rgbColor = rgbColor & 0xFFFFFF;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LineFolder other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
