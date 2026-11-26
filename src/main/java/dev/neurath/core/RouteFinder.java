package dev.neurath.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * A* shortest-path route finder across Neurath transit lines and cross-dimension transfer links.
 */
public final class RouteFinder {
    private static final double TRANSFER_PENALTY = 250.0;
    private static final double PORTAL_LINK_COST = 80.0;

    public record RouteStep(
            String fromStationId,
            String toStationId,
            String lineId,
            boolean isTransferLink,
            double segmentCost
    ) {}

    public record RouteResult(
            List<String> stationIds,
            List<RouteStep> steps,
            int transfers,
            double totalCost
    ) {}

    private record Edge(String to, String lineId, boolean transferLink, double cost) {}

    private record StateKey(String stationId, String currentLineId) {}

    private record Node(StateKey key, double g, double f, Node parent, RouteStep via) {}

    public RouteResult findRoute(TransitNetwork network, String fromStationId, String toStationId) {
        if (network == null || fromStationId == null || toStationId == null) return null;
        if (!network.stations.containsKey(fromStationId) || !network.stations.containsKey(toStationId)) return null;
        if (fromStationId.equals(toStationId)) {
            return new RouteResult(List.of(fromStationId), List.of(), 0, 0.0);
        }

        Map<String, List<Edge>> graph = buildGraph(network);
        Station goal = network.stations.get(toStationId);

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        Map<StateKey, Double> bestG = new HashMap<>();

        StateKey startKey = new StateKey(fromStationId, null);
        open.add(new Node(startKey, 0.0, heuristic(network.stations.get(fromStationId), goal), null, null));
        bestG.put(startKey, 0.0);

        Node goalNode = null;
        while (!open.isEmpty()) {
            Node cur = open.poll();
            if (cur.key.stationId.equals(toStationId)) {
                goalNode = cur;
                break;
            }
            if (cur.g > bestG.getOrDefault(cur.key, Double.POSITIVE_INFINITY) + 1e-6) {
                continue;
            }

            for (Edge e : graph.getOrDefault(cur.key.stationId, List.of())) {
                double transferPenalty = 0.0;
                if (cur.key.currentLineId != null && e.lineId != null && !Objects.equals(cur.key.currentLineId, e.lineId)) {
                    transferPenalty = TRANSFER_PENALTY;
                }
                double nextG = cur.g + e.cost + transferPenalty;
                String nextLine = e.transferLink ? null : e.lineId;
                StateKey nextKey = new StateKey(e.to, nextLine);
                if (nextG < bestG.getOrDefault(nextKey, Double.POSITIVE_INFINITY)) {
                    bestG.put(nextKey, nextG);
                    double h = heuristic(network.stations.get(e.to), goal);
                    RouteStep step = new RouteStep(cur.key.stationId, e.to, e.lineId, e.transferLink, e.cost);
                    open.add(new Node(nextKey, nextG, nextG + h, cur, step));
                }
            }
        }

        if (goalNode == null) return null;

        List<RouteStep> steps = new ArrayList<>();
        List<String> stationIds = new ArrayList<>();
        Node p = goalNode;
        while (p != null) {
            stationIds.add(p.key.stationId);
            if (p.via != null) steps.add(p.via);
            p = p.parent;
        }
        Collections.reverse(stationIds);
        Collections.reverse(steps);

        int transfers = 0;
        String lastLine = null;
        for (RouteStep step : steps) {
            if (step.isTransferLink) {
                transfers++;
                lastLine = null;
            } else {
                if (lastLine != null && !Objects.equals(lastLine, step.lineId)) {
                    transfers++;
                }
                lastLine = step.lineId;
            }
        }

        return new RouteResult(stationIds, steps, transfers, goalNode.g);
    }

    private static Map<String, List<Edge>> buildGraph(TransitNetwork network) {
        Map<String, List<Edge>> graph = new HashMap<>();
        for (TransitLine line : network.lines.values()) {
            if (!line.visible) continue;
            double speedFactor = switch (line.mode) {
                case NETHER -> 0.15;
                case ICE -> 0.35;
                case AIR -> 0.45;
                case RAIL -> 0.65;
                case METRO -> 0.75;
                case TRAM, FERRY -> 1.0;
            };
            for (TransitLine.LineBranch branch : line.branches) {
                for (int i = 0; i < branch.stationIds.size() - 1; i++) {
                    String aId = branch.stationIds.get(i);
                    String bId = branch.stationIds.get(i + 1);
                    Station a = network.stations.get(aId);
                    Station b = network.stations.get(bId);
                    if (a == null || b == null) continue;
                    double dist = Math.max(16.0, Math.hypot(a.x - b.x, a.z - b.z)) * speedFactor;
                    graph.computeIfAbsent(aId, k -> new ArrayList<>()).add(new Edge(bId, line.id, false, dist));
                    graph.computeIfAbsent(bId, k -> new ArrayList<>()).add(new Edge(aId, line.id, false, dist));
                }
            }
        }
        for (TransferLink link : network.transfers) {
            if (!network.stations.containsKey(link.fromStationId) || !network.stations.containsKey(link.toStationId)) {
                continue;
            }
            graph.computeIfAbsent(link.fromStationId, k -> new ArrayList<>())
                    .add(new Edge(link.toStationId, null, true, PORTAL_LINK_COST));
            graph.computeIfAbsent(link.toStationId, k -> new ArrayList<>())
                    .add(new Edge(link.fromStationId, null, true, PORTAL_LINK_COST));
        }
        return graph;
    }

    private static double heuristic(Station a, Station b) {
        if (a == null || b == null) return 0.0;
        if (!Objects.equals(a.dimensionId, b.dimensionId)) return 0.0;
        return Math.hypot(a.x - b.x, a.z - b.z) * 0.12;
    }
}
