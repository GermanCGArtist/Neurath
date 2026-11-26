package dev.neurath.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Root serializable {@code .ntransit} network document holding stations, lines, and transfer links.
 */
public final class TransitNetwork {
    public int schemaVersion = 1;
    public String networkName = "Default Transit Network";
    public float lineThickness = 4.0f;
    public Map<String, Station> stations = new LinkedHashMap<>();
    public Map<String, TransitLine> lines = new LinkedHashMap<>();
    public List<LineFolder> folders = new ArrayList<>();
    public List<TransferLink> transfers = new ArrayList<>();

    public float effectiveLineThickness() {
        if (lineThickness <= 0.1f || Float.isNaN(lineThickness)) return 4.0f;
        return Math.max(1.5f, Math.min(10.0f, lineThickness));
    }

    public LineFolder addFolder(LineFolder folder) {
        if (folder == null) return null;
        if (folders == null) folders = new ArrayList<>();
        folders.add(folder);
        return folder;
    }

    public LineFolder findFolder(String folderId) {
        if (folderId == null || folderId.isBlank() || folders == null) return null;
        for (LineFolder f : folders) {
            if (f != null && folderId.equals(f.id)) return f;
        }
        return null;
    }

    public void removeFolder(String folderId) {
        if (folderId == null || folders == null) return;
        folders.removeIf(f -> f != null && folderId.equals(f.id));
        for (TransitLine l : lines.values()) {
            if (folderId.equals(l.folderId)) {
                l.folderId = "";
            }
        }
    }

    /**
     * Returns true if the line itself is visible AND its parent folder (if any) is visible.
     */
    public boolean isLineEffectivelyVisible(TransitLine line) {
        if (line == null || !line.visible) return false;
        if (line.folderId != null && !line.folderId.isBlank()) {
            LineFolder folder = findFolder(line.folderId);
            if (folder != null && !folder.visible) return false;
        }
        return true;
    }

    public Station addStation(Station station) {
        if (station == null) return null;
        stations.put(station.id, station);
        return station;
    }

    public void removeStation(String stationId) {
        if (stationId == null) return;
        stations.remove(stationId);
        for (TransitLine line : lines.values()) {
            line.removeStation(stationId);
        }
        transfers.removeIf(t -> stationId.equals(t.fromStationId) || stationId.equals(t.toStationId));
    }

    public TransitLine addLine(TransitLine line) {
        if (line == null) return null;
        lines.put(line.id, line);
        return line;
    }

    public void removeLine(String lineId) {
        if (lineId != null) lines.remove(lineId);
    }

    public TransferLink addTransfer(String fromId, String toId, String label) {
        if (fromId == null || toId == null || fromId.equals(toId)) return null;
        for (TransferLink existing : transfers) {
            if (existing.connects(fromId, toId)) return existing;
        }
        Station a = stations.get(fromId);
        Station b = stations.get(toId);
        boolean crossDim = a != null && b != null && !a.dimensionId.equals(b.dimensionId);
        TransferLink link = new TransferLink(fromId, toId, label, crossDim);
        transfers.add(link);
        return link;
    }

    public List<TransitLine> linesServing(String stationId) {
        List<TransitLine> out = new ArrayList<>();
        for (TransitLine l : lines.values()) {
            if (l.servesStation(stationId)) out.add(l);
        }
        return out;
    }

    public boolean isInterchange(String stationId) {
        int count = 0;
        for (TransitLine l : lines.values()) {
            if (l.servesStation(stationId)) {
                count++;
                if (count > 1) return true;
            }
        }
        for (TransferLink t : transfers) {
            if (stationId.equals(t.fromStationId) || stationId.equals(t.toStationId)) {
                return true;
            }
        }
        return false;
    }

    public void resetAllPins() {
        for (Station s : stations.values()) {
            s.unpin();
        }
    }

    public Station findNearestStation(String dimensionId, double worldX, double worldZ, double maxRadius) {
        Station best = null;
        double bestDistSq = maxRadius * maxRadius;
        for (Station s : stations.values()) {
            if (dimensionId != null && !dimensionId.equals(s.dimensionId)) continue;
            double dx = s.x - worldX;
            double dz = s.z - worldZ;
            double dSq = dx * dx + dz * dz;
            if (dSq <= bestDistSq) {
                bestDistSq = dSq;
                best = s;
            }
        }
        return best;
    }
}
