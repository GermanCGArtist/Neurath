package dev.neurath.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransitLineTest {

    @Test
    void reordersAndReversesStopsCleanly() {
        TransitLine line = new TransitLine("l1", "M1", "Central", TransitMode.METRO, 0xF45B69);
        line.appendStop("a");
        line.appendStop("b");
        line.appendStop("c");

        assertTrue(line.moveStopUp(0, 2));
        assertEquals(List.of("a", "c", "b"), line.mainBranch().stationIds);

        assertTrue(line.moveStopDown(0, 0));
        assertEquals(List.of("c", "a", "b"), line.mainBranch().stationIds);

        line.reverseBranch(0);
        assertEquals(List.of("b", "a", "c"), line.mainBranch().stationIds);

        assertTrue(line.removeStopAt(0, 1));
        assertEquals(List.of("b", "c"), line.mainBranch().stationIds);
    }

    @Test
    void multiModeStationCombinesExplicitAndCallingLineSigns() {
        TransitNetwork net = new TransitNetwork();
        Station hbf = net.addStation(new Station("hbf", "HBF", "Hauptbahnhof", TransitMode.METRO, "minecraft:overworld", 0, 64, 0));
        // Default sign for METRO is UBAHN (U)
        assertEquals(List.of(StationSign.UBAHN), hbf.effectiveSigns(net));

        // When an S-Bahn (RAIL) line calls at the same station, S sign appears next to U automatically
        TransitLine s1 = net.addLine(new TransitLine("s1", "S1", "S1 Airport", TransitMode.RAIL, 0x008D4F));
        s1.appendStop("hbf");
        assertEquals(List.of(StationSign.UBAHN, StationSign.SBAHN), hbf.effectiveSigns(net));
        assertEquals("[U][S]", hbf.formattedSignPrefix(net));

        // Explicit custom sign toggle + multi-mode toggle
        hbf.toggleSign(StationSign.TRAM);
        hbf.toggleMode(TransitMode.RAIL);
        assertTrue(hbf.effectiveModes().contains(TransitMode.METRO));
        assertTrue(hbf.effectiveModes().contains(TransitMode.RAIL));
        assertEquals("[U][T][S]", hbf.formattedSignPrefix(net));
    }

    @Test
    void folderVisibilityGatesChildLinesAndRemainsSerializable() {
        TransitNetwork net = new TransitNetwork();
        LineFolder ubahn = net.addFolder(new LineFolder("f1", "U-Bahn", 0x0052A5));
        TransitLine u1 = net.addLine(new TransitLine("u1", "U1", "U1 Main", TransitMode.METRO, 0xFF6600));
        u1.folderId = ubahn.id;

        assertTrue(net.isLineEffectivelyVisible(u1));
        ubahn.visible = false;
        assertFalse(net.isLineEffectivelyVisible(u1));

        String json = TransitStorage.toJson(net);
        TransitNetwork reloaded = TransitStorage.fromJson(json);
        assertEquals(1, reloaded.folders.size());
        assertFalse(reloaded.isLineEffectivelyVisible(reloaded.lines.get("u1")));

        reloaded.removeFolder(ubahn.id);
        assertEquals("", reloaded.lines.get("u1").folderId);
        assertTrue(reloaded.isLineEffectivelyVisible(reloaded.lines.get("u1")));
    }
}
