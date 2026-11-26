package dev.neurath.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouteFinderTest {

    @Test
    void findsShortestPathWithCrossDimensionTransfer() {
        TransitNetwork net = new TransitNetwork();
        net.addStation(new Station("west", "WST", "Westgate", TransitMode.METRO, "minecraft:overworld", -200, 64, 0));
        net.addStation(new Station("ctr", "CTR", "Central", TransitMode.METRO, "minecraft:overworld", 0, 64, 0));
        net.addStation(new Station("nhub", "NHB", "Nether Hub", TransitMode.NETHER, "minecraft:the_nether", 0, 120, 0));
        net.addStation(new Station("far", "FAR", "Far Outpost", TransitMode.NETHER, "minecraft:the_nether", 400, 120, 0));

        TransitLine m1 = net.addLine(new TransitLine("m1", "M1", "Central Metro", TransitMode.METRO, 0xF45B69));
        m1.connectStops("west", "ctr");

        TransitLine n1 = net.addLine(new TransitLine("n1", "N1", "Nether Spine", TransitMode.NETHER, 0xC084FC));
        n1.connectStops("nhub", "far");

        net.addTransfer("ctr", "nhub", "Central Portal");

        RouteFinder finder = new RouteFinder();
        RouteFinder.RouteResult route = finder.findRoute(net, "west", "far");
        assertNotNull(route);
        assertEquals(4, route.stationIds().size());
        assertEquals("west", route.stationIds().get(0));
        assertEquals("far", route.stationIds().get(3));
        assertEquals(1, route.transfers());
    }

    @Test
    void returnsNullWhenDisconnected() {
        TransitNetwork net = new TransitNetwork();
        net.addStation(new Station("a", "A", "A", TransitMode.METRO, "minecraft:overworld", 0, 64, 0));
        net.addStation(new Station("b", "B", "B", TransitMode.METRO, "minecraft:overworld", 100, 64, 0));
        assertNull(new RouteFinder().findRoute(net, "a", "b"));
    }
}
