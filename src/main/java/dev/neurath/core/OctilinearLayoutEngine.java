package dev.neurath.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hybrid octilinear (45°/90°) + geographic transit diagram layout engine.
 * Projects unpinned stations from world (x, z) onto a snapped schematic grid while preserving
 * user-pinned coordinates, then routes segments along 45°/90° octilinear paths with parallel
 * perpendicular offsets whenever multiple lines share the same corridor.
 */
public final class OctilinearLayoutEngine {
    public static final float GRID_STEP = 32f;
    public static final float LINE_SPACING = 6.5f;

    public record Vec2(float x, float y) {}

    public record RoutedSegment(
            String lineId,
            String fromStationId,
            String toStationId,
            int rgbColor,
            TransitMode.LineStyle style,
            List<Vec2> points
    ) {}

    public record LayoutResult(
            Map<String, Vec2> stationPositions,
            List<RoutedSegment> segments
    ) {}

    public LayoutResult compute(TransitNetwork network, boolean octilinear, Set<TransitMode> visibleModes) {
        Map<String, Vec2> positions = new LinkedHashMap<>();
        if (network == null || network.stations.isEmpty()) {
            return new LayoutResult(positions, List.of());
        }

        // Compute world bounds for normalization
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Station s : network.stations.values()) {
            if (s.x < minX) minX = s.x;
            if (s.x > maxX) maxX = s.x;
            if (s.z < minZ) minZ = s.z;
            if (s.z > maxZ) maxZ = s.z;
        }
        double spanX = Math.max(1.0, maxX - minX);
        double spanZ = Math.max(1.0, maxZ - minZ);
        double scale = 560.0 / Math.max(spanX, spanZ);

        // Place stations
        Set<Long> occupiedCells = new HashSet<>();
        List<Station> ordered = new ArrayList<>(network.stations.values());
        // Reserve pinned stations first so auto-placed stations avoid colliding with them
        ordered.sort(Comparator.comparing((Station s) -> !s.diagramPinned).thenComparing(s -> s.id));

        for (Station s : ordered) {
            float sx;
            float sy;
            if (octilinear && s.diagramPinned) {
                sx = snap(s.diagramX);
                sy = snap(s.diagramY);
            } else {
                float rawX = (float) ((s.x - minX) * scale) + 96f;
                float rawY = (float) ((s.z - minZ) * scale) + 96f;
                if (octilinear) {
                    int gx = Math.round(rawX / GRID_STEP);
                    int gy = Math.round(rawY / GRID_STEP);
                    long key = packCell(gx, gy);
                    if (occupiedCells.contains(key)) {
                        int[] free = findNearestFreeCell(gx, gy, occupiedCells);
                        gx = free[0];
                        gy = free[1];
                    }
                    occupiedCells.add(packCell(gx, gy));
                    sx = gx * GRID_STEP;
                    sy = gy * GRID_STEP;
                } else {
                    sx = rawX;
                    sy = rawY;
                }
            }
            if (octilinear && s.diagramPinned) {
                occupiedCells.add(packCell(Math.round(sx / GRID_STEP), Math.round(sy / GRID_STEP)));
            }
            positions.put(s.id, new Vec2(sx, sy));
        }

        // Group undirected corridor segments to assign parallel line offsets
        Map<String, List<String>> corridorLines = new HashMap<>();
        for (TransitLine line : network.lines.values()) {
            if (!network.isLineEffectivelyVisible(line)) continue;
            if (visibleModes != null && !visibleModes.contains(line.mode)) continue;
            for (TransitLine.LineBranch branch : line.branches) {
                for (int i = 0; i < branch.stationIds.size() - 1; i++) {
                    String a = branch.stationIds.get(i);
                    String b = branch.stationIds.get(i + 1);
                    if (!positions.containsKey(a) || !positions.containsKey(b)) continue;
                    String key = corridorKey(a, b);
                    List<String> list = corridorLines.computeIfAbsent(key, k -> new ArrayList<>());
                    if (!list.contains(line.id)) list.add(line.id);
                }
            }
        }

        // Route each line segment
        List<RoutedSegment> segments = new ArrayList<>();
        for (TransitLine line : network.lines.values()) {
            if (!network.isLineEffectivelyVisible(line)) continue;
            if (visibleModes != null && !visibleModes.contains(line.mode)) continue;
            for (TransitLine.LineBranch branch : line.branches) {
                for (int i = 0; i < branch.stationIds.size() - 1; i++) {
                    String a = branch.stationIds.get(i);
                    String b = branch.stationIds.get(i + 1);
                    Vec2 p1 = positions.get(a);
                    Vec2 p2 = positions.get(b);
                    if (p1 == null || p2 == null) continue;

                    Station sa = network.stations.get(a);
                    Station sb = network.stations.get(b);
                    boolean isExtension = branch.extension
                            || (sa != null && sa.extension)
                            || (sb != null && sb.extension);
                    TransitMode.LineStyle segStyle = isExtension
                            ? TransitMode.LineStyle.DOTTED
                            : line.mode.defaultLineStyle();

                    List<String> shared = corridorLines.getOrDefault(corridorKey(a, b), List.of(line.id));
                    int idx = Math.max(0, shared.indexOf(line.id));
                    float offset = (idx - (shared.size() - 1) * 0.5f) * LINE_SPACING;
                    // Canonical perpendicular direction so reverse-ordered lines offset consistently
                    Vec2 ca = a.compareTo(b) <= 0 ? p1 : p2;
                    Vec2 cb = a.compareTo(b) <= 0 ? p2 : p1;
                    float dx = cb.x - ca.x;
                    float dy = cb.y - ca.y;
                    float len = (float) Math.hypot(dx, dy);
                    float nx = len > 0.001f ? -dy / len : 0f;
                    float ny = len > 0.001f ? dx / len : 0f;

                    Vec2 o1 = new Vec2(p1.x + nx * offset, p1.y + ny * offset);
                    Vec2 o2 = new Vec2(p2.x + nx * offset, p2.y + ny * offset);
                    List<Vec2> poly = octilinear ? routeOctilinear(o1, o2) : List.of(o1, o2);
                    segments.add(new RoutedSegment(
                            line.id, a, b, line.rgbColor, segStyle, poly
                    ));
                }
            }
        }

        return new LayoutResult(positions, segments);
    }

    /**
     * Produces a 45°/90° octilinear polyline between two points:
     * straight if already axis-aligned or 45°, otherwise a 45° diagonal bend followed by an orthogonal leg.
     */
    public static List<Vec2> routeOctilinear(Vec2 p1, Vec2 p2) {
        float dx = p2.x - p1.x;
        float dy = p2.y - p1.y;
        float adx = Math.abs(dx);
        float ady = Math.abs(dy);
        if (adx < 1f || ady < 1f || Math.abs(adx - ady) < 1f) {
            return List.of(p1, p2);
        }
        float diag = Math.min(adx, ady);
        float mx = p1.x + Math.signum(dx) * diag;
        float my = p1.y + Math.signum(dy) * diag;
        return List.of(p1, new Vec2(mx, my), p2);
    }

    public static float snap(float v) {
        return Math.round(v / GRID_STEP) * GRID_STEP;
    }

    private static String corridorKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private static long packCell(int gx, int gy) {
        return (((long) gx) << 32) ^ (gy & 0xFFFFFFFFL);
    }

    private static int[] findNearestFreeCell(int gx, int gy, Set<Long> occupied) {
        for (int r = 1; r <= 32; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    int cx = gx + dx;
                    int cy = gy + dy;
                    if (!occupied.contains(packCell(cx, cy))) {
                        return new int[]{cx, cy};
                    }
                }
            }
        }
        return new int[]{gx + 1, gy};
    }
}
