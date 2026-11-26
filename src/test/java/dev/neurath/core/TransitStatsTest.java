package dev.neurath.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransitStatsTest {

    @Test
    void computesSystemCategoryAndLineStatisticsWithExtensions() {
        TransitNetwork net = new TransitNetwork();
        LineFolder ubahn = net.addFolder(new LineFolder("f_u", "U-Bahn", 0x0052A5));

        Station a = net.addStation(new Station("a", "AAA", "Start", TransitMode.METRO, "minecraft:overworld", 0, 64, 0));
        Station b = net.addStation(new Station("b", "BBB", "Mid", TransitMode.METRO, "minecraft:overworld", 300, 64, 400)); // 500m from a
        Station c = net.addStation(new Station("c", "CCC", "Planned", TransitMode.METRO, "minecraft:overworld", 300, 64, 600)); // 200m from b
        c.extension = true;

        TransitLine u1 = net.addLine(new TransitLine("u1", "U1", "U1 Main", TransitMode.METRO, 0xFF6600));
        u1.folderId = ubahn.id;
        u1.appendStop(a.id);
        u1.appendStop(b.id);
        u1.appendStop(c.id);

        TransitLine s1 = net.addLine(new TransitLine("s1", "S1", "S1 Express", TransitMode.RAIL, 0x008D4F));
        s1.appendStop(a.id);
        s1.appendStop(b.id); // 500m built, ungrouped

        TransitStats.NetworkSummary summary = TransitStats.compute(net);
        assertEquals(3, summary.totalStations());
        assertEquals(2, summary.builtStations());
        assertEquals(1, summary.extensionStations());
        assertEquals(2, summary.interchangeStations());
        assertEquals(2, summary.totalLines());
        assertEquals(1, summary.totalFolders());

        assertEquals(1000.0, summary.builtLength(), 0.01);
        assertEquals(200.0, summary.extensionLength(), 0.01);
        assertEquals(1200.0, summary.totalLength(), 0.01);
        assertEquals("u1", summary.longestLine().lineId());
        assertEquals(2, summary.folderStats().size()); // U-Bahn + Ungrouped
        assertEquals(2, summary.modeStats().size());   // METRO + RAIL
        assertEquals("1.20 km", TransitStats.formatDistance(summary.totalLength()));
    }
}
