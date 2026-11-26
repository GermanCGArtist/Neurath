package dev.neurath.core;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OctilinearLayoutEngineTest {

    @Test
    void octilinearSegmentsFollow45Or90DegreeAngles() {
        var p1 = new OctilinearLayoutEngine.Vec2(0f, 0f);
        var p2 = new OctilinearLayoutEngine.Vec2(160f, 64f);
        List<OctilinearLayoutEngine.Vec2> poly = OctilinearLayoutEngine.routeOctilinear(p1, p2);
        assertEquals(3, poly.size());
        // First leg must be 45 degrees (|dx| == |dy|)
        float dx1 = Math.abs(poly.get(1).x() - poly.get(0).x());
        float dy1 = Math.abs(poly.get(1).y() - poly.get(0).y());
        assertEquals(dx1, dy1, 0.01f);
        // Second leg must be orthogonal (dx == 0 or dy == 0)
        float dx2 = Math.abs(poly.get(2).x() - poly.get(1).x());
        float dy2 = Math.abs(poly.get(2).y() - poly.get(1).y());
        assertTrue(dx2 < 0.01f || dy2 < 0.01f);
    }

    @Test
    void pinnedStationsKeepTheirSnappedCoordinates() {
        TransitNetwork net = new TransitNetwork();
        Station a = net.addStation(new Station("a", "AAA", "Alpha", TransitMode.METRO, "minecraft:overworld", 0, 64, 0));
        Station b = net.addStation(new Station("b", "BBB", "Beta", TransitMode.METRO, "minecraft:overworld", 500, 64, 200));
        a.pinAt(256f, 128f);

        OctilinearLayoutEngine engine = new OctilinearLayoutEngine();
        var res = engine.compute(net, true, EnumSet.allOf(TransitMode.class));
        assertEquals(256f, res.stationPositions().get("a").x(), 0.01f);
        assertEquals(128f, res.stationPositions().get("a").y(), 0.01f);
        assertNotNull(res.stationPositions().get("b"));
    }

    @Test
    void sharedCorridorAppliesParallelOffsetsToMultipleLines() {
        TransitNetwork net = new TransitNetwork();
        net.addStation(new Station("a", "AAA", "Alpha", TransitMode.METRO, "minecraft:overworld", 0, 64, 0));
        net.addStation(new Station("b", "BBB", "Beta", TransitMode.METRO, "minecraft:overworld", 320, 64, 0));

        TransitLine l1 = net.addLine(new TransitLine("l1", "M1", "Line 1", TransitMode.METRO, 0xFF0000));
        l1.connectStops("a", "b");
        TransitLine l2 = net.addLine(new TransitLine("l2", "M2", "Line 2", TransitMode.METRO, 0x00FF00));
        l2.connectStops("a", "b");

        OctilinearLayoutEngine engine = new OctilinearLayoutEngine();
        var res = engine.compute(net, true, EnumSet.allOf(TransitMode.class));
        assertEquals(2, res.segments().size());
        float y1 = res.segments().get(0).points().get(0).y();
        float y2 = res.segments().get(1).points().get(0).y();
        assertNotEquals(y1, y2, 0.5f, "Parallel lines on the same corridor must be offset apart");
    }

    @Test
    void extensionStationsProduceDottedSegmentsAndHiddenFoldersOmitSegments() {
        TransitNetwork net = new TransitNetwork();
        Station a = net.addStation(new Station("a", "AAA", "Built", TransitMode.METRO, "minecraft:overworld", 0, 64, 0));
        Station b = net.addStation(new Station("b", "BBB", "Built 2", TransitMode.METRO, "minecraft:overworld", 100, 64, 0));
        Station c = net.addStation(new Station("c", "CCC", "Planned Ext", TransitMode.METRO, "minecraft:overworld", 200, 64, 0));
        c.extension = true;

        LineFolder sys = net.addFolder(new LineFolder("sys", "Metro System", 0x0052A5));
        TransitLine l1 = net.addLine(new TransitLine("l1", "U1", "Line 1", TransitMode.METRO, 0xFF6600));
        l1.folderId = sys.id;
        l1.appendStop(a.id);
        l1.appendStop(b.id);
        l1.appendStop(c.id);

        OctilinearLayoutEngine engine = new OctilinearLayoutEngine();
        var res = engine.compute(net, true, EnumSet.allOf(TransitMode.class));
        assertEquals(2, res.segments().size());
        assertEquals(TransitMode.LineStyle.SOLID, res.segments().get(0).style());
        assertEquals(TransitMode.LineStyle.DOTTED, res.segments().get(1).style());

        sys.visible = false;
        var hiddenRes = engine.compute(net, true, EnumSet.allOf(TransitMode.class));
        assertTrue(hiddenRes.segments().isEmpty());
    }
}
