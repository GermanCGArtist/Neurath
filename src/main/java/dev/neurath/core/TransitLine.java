package dev.neurath.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A colored public transit line consisting of one or more ordered stop sequences (branches).
 */
public final class TransitLine {
    public String id;
    public String code;
    public String name;
    public TransitMode mode = TransitMode.METRO;
    public int rgbColor = 0xF45B69;
    public boolean visible = true;
    public String folderId = "";
    public List<LineBranch> branches = new ArrayList<>();

    public static final class LineBranch {
        public String name = "Main";
        public boolean extension = false;
        public List<String> stationIds = new ArrayList<>();

        public LineBranch() {}

        public LineBranch(String name, List<String> stationIds) {
            this.name = (name == null || name.isBlank()) ? "Main" : name;
            this.stationIds = stationIds == null ? new ArrayList<>() : new ArrayList<>(stationIds);
        }
    }

    public TransitLine() {
        this.id = UUID.randomUUID().toString();
    }

    public TransitLine(String id, String code, String name, TransitMode mode, int rgbColor) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.code = (code == null || code.isBlank()) ? "L1" : code.trim().toUpperCase();
        this.name = (name == null || name.isBlank()) ? this.code : name.trim();
        this.mode = mode == null ? TransitMode.METRO : mode;
        this.rgbColor = rgbColor & 0xFFFFFF;
    }

    public LineBranch mainBranch() {
        if (branches == null) branches = new ArrayList<>();
        if (branches.isEmpty()) {
            branches.add(new LineBranch("Main", new ArrayList<>()));
        }
        return branches.get(0);
    }

    public boolean servesStation(String stationId) {
        if (stationId == null || branches == null) return false;
        for (LineBranch b : branches) {
            if (b != null && b.stationIds != null && b.stationIds.contains(stationId)) {
                return true;
            }
        }
        return false;
    }

    public int uniqueStopCount() {
        if (branches == null) return 0;
        List<String> seen = new ArrayList<>();
        for (LineBranch b : branches) {
            if (b == null || b.stationIds == null) continue;
            for (String s : b.stationIds) {
                if (!seen.contains(s)) seen.add(s);
            }
        }
        return seen.size();
    }

    public void appendStop(String stationId) {
        LineBranch main = mainBranch();
        if (!main.stationIds.isEmpty() && main.stationIds.get(main.stationIds.size() - 1).equals(stationId)) {
            return;
        }
        main.stationIds.add(stationId);
    }

    public void connectStops(String fromStationId, String toStationId) {
        if (fromStationId == null || toStationId == null || fromStationId.equals(toStationId)) return;
        LineBranch main = mainBranch();
        List<String> stops = main.stationIds;
        if (stops.isEmpty()) {
            stops.add(fromStationId);
            stops.add(toStationId);
            return;
        }
        if (stops.get(stops.size() - 1).equals(fromStationId)) {
            stops.add(toStationId);
            return;
        }
        if (stops.get(0).equals(fromStationId)) {
            stops.add(0, toStationId);
            return;
        }
        // Create a branch spur if connecting from an interior station
        for (LineBranch b : branches) {
            if (!b.stationIds.isEmpty() && b.stationIds.get(b.stationIds.size() - 1).equals(fromStationId)) {
                b.stationIds.add(toStationId);
                return;
            }
        }
        branches.add(new LineBranch("Branch " + branches.size(), List.of(fromStationId, toStationId)));
    }

    public void removeStation(String stationId) {
        if (branches == null) return;
        for (LineBranch b : branches) {
            if (b != null && b.stationIds != null) {
                b.stationIds.removeIf(s -> s.equals(stationId));
            }
        }
    }

    public boolean moveStopUp(int branchIndex, int stopIndex) {
        if (branches == null || branchIndex < 0 || branchIndex >= branches.size()) return false;
        List<String> stops = branches.get(branchIndex).stationIds;
        if (stops == null || stopIndex <= 0 || stopIndex >= stops.size()) return false;
        String item = stops.remove(stopIndex);
        stops.add(stopIndex - 1, item);
        return true;
    }

    public boolean moveStopDown(int branchIndex, int stopIndex) {
        if (branches == null || branchIndex < 0 || branchIndex >= branches.size()) return false;
        List<String> stops = branches.get(branchIndex).stationIds;
        if (stops == null || stopIndex < 0 || stopIndex >= stops.size() - 1) return false;
        String item = stops.remove(stopIndex);
        stops.add(stopIndex + 1, item);
        return true;
    }

    public boolean removeStopAt(int branchIndex, int stopIndex) {
        if (branches == null || branchIndex < 0 || branchIndex >= branches.size()) return false;
        List<String> stops = branches.get(branchIndex).stationIds;
        if (stops == null || stopIndex < 0 || stopIndex >= stops.size()) return false;
        stops.remove(stopIndex);
        return true;
    }

    public void reverseBranch(int branchIndex) {
        if (branches == null || branchIndex < 0 || branchIndex >= branches.size()) return;
        List<String> stops = branches.get(branchIndex).stationIds;
        if (stops != null && stops.size() > 1) {
            java.util.Collections.reverse(stops);
        }
    }
}
