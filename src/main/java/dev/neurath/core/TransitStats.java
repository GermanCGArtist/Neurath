package dev.neurath.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes network-wide, per-system (folder), per-category ({@link TransitMode}), and per-line
 * transit statistics (lengths in blocks, built vs. extension breakdown, stop counts, spacing,
 * and network share percentages) for the Neurath viewer.
 */
public final class TransitStats {

    public record LineStat(
            String lineId,
            String code,
            String name,
            String folderId,
            String folderName,
            TransitMode mode,
            int rgbColor,
            boolean visible,
            int totalStops,
            int builtStops,
            int extensionStops,
            double builtLength,
            double extensionLength,
            double totalLength,
            double avgSpacing,
            double longestSpan
    ) {}

    public record FolderStat(
            String folderId,
            String name,
            int rgbColor,
            boolean visible,
            int lineCount,
            int uniqueStationCount,
            double builtLength,
            double extensionLength,
            double totalLength,
            double share
    ) {}

    public record ModeStat(
            TransitMode mode,
            int lineCount,
            int uniqueStationCount,
            double builtLength,
            double extensionLength,
            double totalLength,
            double share
    ) {}

    public record NetworkSummary(
            int totalStations,
            int builtStations,
            int extensionStations,
            int interchangeStations,
            int totalLines,
            int visibleLines,
            int totalFolders,
            double builtLength,
            double extensionLength,
            double totalLength,
            double avgStopSpacing,
            LineStat longestLine,
            List<FolderStat> folderStats,
            List<ModeStat> modeStats,
            List<LineStat> lineStats
    ) {}

    private TransitStats() {}

    public static NetworkSummary compute(TransitNetwork net) {
        if (net == null) {
            return new NetworkSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, List.of(), List.of(), List.of());
        }

        int totalStations = net.stations.size();
        int builtStations = 0;
        int extensionStations = 0;
        int interchangeStations = 0;
        for (Station s : net.stations.values()) {
            if (s.extension) extensionStations++;
            else builtStations++;
            if (net.isInterchange(s.id)) interchangeStations++;
        }

        List<LineStat> lineStats = new ArrayList<>();
        double netBuiltLen = 0.0;
        double netExtLen = 0.0;
        int netSpanCount = 0;
        int visibleLines = 0;

        // Accumulators for folders and modes
        Map<String, List<LineStat>> linesByFolder = new LinkedHashMap<>();
        Map<String, Set<String>> stationsByFolder = new LinkedHashMap<>();
        if (net.folders != null) {
            for (LineFolder f : net.folders) {
                linesByFolder.put(f.id, new ArrayList<>());
                stationsByFolder.put(f.id, new HashSet<>());
            }
        }
        linesByFolder.put("", new ArrayList<>());
        stationsByFolder.put("", new HashSet<>());

        Map<TransitMode, List<LineStat>> linesByMode = new EnumMap<>(TransitMode.class);
        Map<TransitMode, Set<String>> stationsByMode = new EnumMap<>(TransitMode.class);

        for (TransitLine line : net.lines.values()) {
            boolean effVis = net.isLineEffectivelyVisible(line);
            if (effVis) visibleLines++;

            String fid = (line.folderId != null && net.findFolder(line.folderId) != null) ? line.folderId : "";
            LineFolder folder = fid.isEmpty() ? null : net.findFolder(fid);
            String folderName = folder != null ? folder.name : "Ungrouped";
            TransitMode mode = line.mode != null ? line.mode : TransitMode.METRO;

            Set<String> uniqueStops = new HashSet<>();
            int builtStopCount = 0;
            int extStopCount = 0;
            double builtLen = 0.0;
            double extLen = 0.0;
            double longestSpan = 0.0;
            int spanCount = 0;

            if (line.branches != null) {
                for (TransitLine.LineBranch branch : line.branches) {
                    if (branch == null || branch.stationIds == null) continue;
                    for (String sid : branch.stationIds) {
                        if (uniqueStops.add(sid)) {
                            Station st = net.stations.get(sid);
                            if (st != null) {
                                if (st.extension || branch.extension) extStopCount++;
                                else builtStopCount++;
                            }
                        }
                    }
                    for (int i = 0; i < branch.stationIds.size() - 1; i++) {
                        Station a = net.stations.get(branch.stationIds.get(i));
                        Station b = net.stations.get(branch.stationIds.get(i + 1));
                        if (a == null || b == null) continue;
                        if (a.dimensionId != null && !a.dimensionId.equals(b.dimensionId)) continue;
                        double d = Math.hypot(b.x - a.x, b.z - a.z);
                        boolean isExt = branch.extension || a.extension || b.extension;
                        if (isExt) extLen += d;
                        else builtLen += d;
                        if (d > longestSpan) longestSpan = d;
                        spanCount++;
                    }
                }
            }

            double totalLen = builtLen + extLen;
            double avgSpacing = spanCount > 0 ? totalLen / spanCount : 0.0;
            LineStat ls = new LineStat(
                    line.id, line.code, line.name, fid, folderName, mode, line.rgbColor, effVis,
                    uniqueStops.size(), builtStopCount, extStopCount,
                    builtLen, extLen, totalLen, avgSpacing, longestSpan
            );
            lineStats.add(ls);

            netBuiltLen += builtLen;
            netExtLen += extLen;
            netSpanCount += spanCount;

            linesByFolder.computeIfAbsent(fid, k -> new ArrayList<>()).add(ls);
            stationsByFolder.computeIfAbsent(fid, k -> new HashSet<>()).addAll(uniqueStops);

            linesByMode.computeIfAbsent(mode, k -> new ArrayList<>()).add(ls);
            stationsByMode.computeIfAbsent(mode, k -> new HashSet<>()).addAll(uniqueStops);
        }

        double netTotalLen = netBuiltLen + netExtLen;
        double netAvgSpacing = netSpanCount > 0 ? netTotalLen / netSpanCount : 0.0;

        lineStats.sort(Comparator.comparingDouble(LineStat::totalLength).reversed().thenComparing(LineStat::code));
        LineStat longestLine = lineStats.isEmpty() ? null : lineStats.get(0);

        List<FolderStat> folderStats = new ArrayList<>();
        if (net.folders != null) {
            for (LineFolder f : net.folders) {
                List<LineStat> fl = linesByFolder.getOrDefault(f.id, List.of());
                double fb = fl.stream().mapToDouble(LineStat::builtLength).sum();
                double fe = fl.stream().mapToDouble(LineStat::extensionLength).sum();
                double ft = fb + fe;
                double share = netTotalLen > 0.001 ? ft / netTotalLen : 0.0;
                folderStats.add(new FolderStat(
                        f.id, f.name, f.rgbColor, f.visible, fl.size(),
                        stationsByFolder.getOrDefault(f.id, Set.of()).size(),
                        fb, fe, ft, share
                ));
            }
        }
        List<LineStat> ungroupedLines = linesByFolder.getOrDefault("", List.of());
        if (!ungroupedLines.isEmpty()) {
            double ub = ungroupedLines.stream().mapToDouble(LineStat::builtLength).sum();
            double ue = ungroupedLines.stream().mapToDouble(LineStat::extensionLength).sum();
            double ut = ub + ue;
            double share = netTotalLen > 0.001 ? ut / netTotalLen : 0.0;
            folderStats.add(new FolderStat(
                    "", "Ungrouped", 0x64748B, true, ungroupedLines.size(),
                    stationsByFolder.getOrDefault("", Set.of()).size(),
                    ub, ue, ut, share
            ));
        }

        List<ModeStat> modeStats = new ArrayList<>();
        for (TransitMode m : TransitMode.values()) {
            List<LineStat> ml = linesByMode.get(m);
            if (ml == null || ml.isEmpty()) continue;
            double mb = ml.stream().mapToDouble(LineStat::builtLength).sum();
            double me = ml.stream().mapToDouble(LineStat::extensionLength).sum();
            double mt = mb + me;
            double share = netTotalLen > 0.001 ? mt / netTotalLen : 0.0;
            modeStats.add(new ModeStat(
                    m, ml.size(), stationsByMode.getOrDefault(m, Set.of()).size(),
                    mb, me, mt, share
            ));
        }
        modeStats.sort(Comparator.comparingDouble(ModeStat::totalLength).reversed());

        int folderCount = net.folders != null ? net.folders.size() : 0;
        return new NetworkSummary(
                totalStations, builtStations, extensionStations, interchangeStations,
                net.lines.size(), visibleLines, folderCount,
                netBuiltLen, netExtLen, netTotalLen, netAvgSpacing,
                longestLine, folderStats, modeStats, lineStats
        );
    }

    public static String formatDistance(double blocks) {
        if (blocks < 1000.0) {
            return String.format("%.0f m", blocks);
        }
        return String.format("%.2f km", blocks / 1000.0);
    }
}
