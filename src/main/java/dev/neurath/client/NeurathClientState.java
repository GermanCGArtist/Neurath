package dev.neurath.client;

import dev.liskov.core.SemanticColor;
import dev.liskov.core.vcs.VcsCommit;
import dev.liskov.net.LiskovSyncClient;
import dev.liskov.ui.theme.MubWidgets;
import dev.neurath.core.Station;
import dev.neurath.core.TransitLine;
import dev.neurath.core.TransitMode;
import dev.neurath.core.TransitNetwork;
import dev.neurath.core.TransitStorage;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side state holder for the active {@link TransitNetwork}, persistence via
 * {@link TransitStorage}, and JourneyMap/UI change listeners.
 */
public final class NeurathClientState {
    private static final Logger LOG = LoggerFactory.getLogger("neurath-client");
    private static final NeurathClientState INSTANCE = new NeurathClientState();

    public enum MapToolMode {
        SELECT,
        PLACE_STATION,
        CONNECT_LINE,
        SPECTATE_AREA
    }

    private TransitStorage storage;
    private Path activeFile;
    private TransitNetwork network = new TransitNetwork();
    private String activeLineId;
    private String selectedStationId;
    private String pendingConnectFromId;
    private MapToolMode mapToolMode = MapToolMode.SELECT;
    private boolean showLinesOverlay = true;
    private boolean showStationsOverlay = true;
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private NeurathClientState() {}

    public static NeurathClientState get() {
        return INSTANCE;
    }

    public void initStorage() {
        try {
            this.storage = TransitStorage.createWithLiskovFs();
            loadForContext("default");
        } catch (IOException e) {
            LOG.error("[Neurath] Failed to initialize TransitStorage", e);
        }
    }

    public void loadForContext(String contextKey) {
        if (storage == null) return;
        this.activeFile = storage.fileForContext(contextKey);
        try {
            this.network = storage.load(activeFile);
            ensureDefaultLineIfEmpty();
            notifyChanged();
        } catch (IOException e) {
            LOG.warn("[Neurath] Failed loading transit network {}", activeFile, e);
            this.network = new TransitNetwork();
        }
    }

    public void save(String commitMessage, boolean autoSave) {
        if (storage == null || activeFile == null) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            String uuid = (mc != null && mc.player != null) ? mc.player.getUUID().toString() : "local";
            String name = (mc != null && mc.player != null) ? mc.player.getGameProfile().name() : "Player";
            VcsCommit commit = storage.save(activeFile, network, uuid, name, commitMessage, autoSave);
            // Also publish to Liskov Server Drive if connected
            String serverId = LiskovSyncClient.get().activeServerId();
            if (serverId != null && !serverId.isBlank()) {
                try {
                    LiskovSyncClient.get().publishPath(
                            "shared",
                            activeFile,
                            "neurath/" + activeFile.getFileName().toString(),
                            TransitStorage.FILE_TYPE_ID,
                            uuid,
                            name,
                            commitMessage,
                            autoSave
                    );
                } catch (Exception syncErr) {
                    LOG.debug("[Neurath] Optional server sync skipped: {}", syncErr.getMessage());
                }
            }
            if (!autoSave && commit != null) {
                MubWidgets.ToastHost.push("Saved VCS snapshot: " + commitMessage, SemanticColor.BOT);
            }
        } catch (IOException e) {
            LOG.error("[Neurath] Failed saving transit network", e);
            MubWidgets.ToastHost.push("Save failed: " + e.getMessage(), SemanticColor.DANGER);
        }
        notifyChanged();
    }

    private void ensureDefaultLineIfEmpty() {
        if (network.lines.isEmpty()) {
            TransitLine m1 = new TransitLine(null, "M1", "M1 Central Metro", TransitMode.METRO, TransitMode.METRO.defaultRgb());
            network.addLine(m1);
            activeLineId = m1.id;
        } else if (activeLineId == null || !network.lines.containsKey(activeLineId)) {
            activeLineId = network.lines.keySet().iterator().next();
        }
    }

    public TransitNetwork network() {
        return network;
    }

    public Path activeFile() {
        return activeFile;
    }

    public String activeLineId() {
        ensureDefaultLineIfEmpty();
        return activeLineId;
    }

    public void setActiveLineId(String id) {
        if (id != null && network.lines.containsKey(id)) {
            this.activeLineId = id;
            notifyChanged();
        }
    }

    public TransitLine activeLine() {
        return network.lines.get(activeLineId());
    }

    public String selectedStationId() {
        return selectedStationId;
    }

    public void setSelectedStationId(String id) {
        this.selectedStationId = id;
        notifyChanged();
    }

    public Station selectedStation() {
        return selectedStationId == null ? null : network.stations.get(selectedStationId);
    }

    public String pendingConnectFromId() {
        return pendingConnectFromId;
    }

    public void setPendingConnectFromId(String id) {
        this.pendingConnectFromId = id;
    }

    public MapToolMode mapToolMode() {
        return mapToolMode;
    }

    public void setMapToolMode(MapToolMode mode) {
        this.mapToolMode = mode == null ? MapToolMode.SELECT : mode;
        if (this.mapToolMode != MapToolMode.CONNECT_LINE) {
            this.pendingConnectFromId = null;
        }
        notifyChanged();
    }

    public boolean isShowLinesOverlay() {
        return showLinesOverlay;
    }

    public void setShowLinesOverlay(boolean show) {
        if (this.showLinesOverlay != show) {
            this.showLinesOverlay = show;
            notifyChanged();
        }
    }

    public void toggleLinesOverlay() {
        setShowLinesOverlay(!this.showLinesOverlay);
    }

    public boolean isShowStationsOverlay() {
        return showStationsOverlay;
    }

    public void setShowStationsOverlay(boolean show) {
        if (this.showStationsOverlay != show) {
            this.showStationsOverlay = show;
            notifyChanged();
        }
    }

    public void toggleStationsOverlay() {
        setShowStationsOverlay(!this.showStationsOverlay);
    }

    public void addChangeListener(Runnable r) {
        if (r != null) changeListeners.add(r);
    }

    public void notifyChanged() {
        for (Runnable r : changeListeners) {
            try {
                r.run();
            } catch (Exception ignored) {}
        }
    }
}
