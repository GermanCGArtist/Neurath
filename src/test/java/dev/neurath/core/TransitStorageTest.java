package dev.neurath.core;

import dev.liskov.core.vcs.VcsCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TransitStorageTest {

    @Test
    void roundTripsNetworkAndCreatesVcsSnapshot(@TempDir Path tempDir) throws Exception {
        TransitStorage storage = new TransitStorage(tempDir.resolve("neurath"), tempDir.resolve(".liskov-vcs"));
        Path file = storage.fileForContext("test_world");

        TransitNetwork net = new TransitNetwork();
        net.networkName = "Vienna Ring";
        Station s1 = net.addStation(new Station("s1", "CTR", "Central", TransitMode.METRO, "minecraft:overworld", 10, 64, -20));
        s1.pinAt(128f, 192f);
        Station s2 = net.addStation(new Station("s2", "HBR", "Harbor", TransitMode.FERRY, "minecraft:overworld", 210, 63, 140));
        TransitLine l1 = net.addLine(new TransitLine("l1", "U1", "U1 Metro", TransitMode.METRO, 0xE20A16));
        l1.connectStops("s1", "s2");

        VcsCommit commit = storage.save(file, net, "uuid-1", "Julian", "Initial network", false);
        assertNotNull(commit);
        assertTrue(commit.milestone);

        TransitNetwork loaded = storage.load(file);
        assertEquals("Vienna Ring", loaded.networkName);
        assertEquals(2, loaded.stations.size());
        assertEquals(1, loaded.lines.size());
        assertTrue(loaded.stations.get("s1").diagramPinned);
        assertEquals(128f, loaded.stations.get("s1").diagramX, 0.01f);
        assertTrue(loaded.lines.get("l1").servesStation("s2"));
    }
}
