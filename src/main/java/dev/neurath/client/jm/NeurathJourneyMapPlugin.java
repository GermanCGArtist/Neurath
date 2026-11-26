package dev.neurath.client.jm;

import dev.liskov.core.SemanticColor;
import dev.liskov.ui.theme.MubWidgets;
import dev.neurath.NeurathMod;
import dev.neurath.client.NeurathClientState;
import dev.neurath.client.spectate.ClientSpectateController;
import dev.neurath.client.ui.TransitDiagramWindow;
import dev.neurath.core.Station;
import dev.neurath.core.StationSign;
import dev.neurath.core.TransitLine;
import dev.neurath.core.TransitMode;
import dev.neurath.core.TransitNetwork;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.IOverlayListener;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.event.FullscreenMapEvent;
import journeymap.api.v2.client.fullscreen.ModPopupMenu;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.common.Context;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * JourneyMap API v2 client plugin for Neurath:
 * <ul>
 *     <li>Adds native toolbar buttons for Transit Diagram and Map Layers</li>
 *     <li>Feeds live fullscreen {@link UIState} to {@link JmFullscreenToolStripLayer} for crisp,
 *     anti-aliased, configurable-thickness, non-stepping fullscreen transit lines</li>
 *     <li>Renders non-degenerate minimap line overlays and crisp {@code U}/mode sign pictograms</li>
 * </ul>
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public final class NeurathJourneyMapPlugin implements IClientPlugin {
    private static final Logger LOG = LoggerFactory.getLogger("neurath-jm");
    private static final Identifier DIAGRAM_ICON = Identifier.fromNamespaceAndPath(NeurathMod.MOD_ID, "textures/gui/diagram_icon.png");

    private IClientAPI jmApi;
    private boolean spectateDragging;
    private BlockPos dragStartPos;
    private double dragStartScreenX;
    private double dragStartScreenY;

    @Override
    public String getModId() {
        return NeurathMod.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI jmClientApi) {
        this.jmApi = jmClientApi;
        NeurathClientState.get().addChangeListener(this::refreshOverlays);

        // 1. Add native JourneyMap toolbar buttons (laid out by JourneyMap with zero overlap)
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(NeurathMod.MOD_ID, event -> {
            event.getThemeButtonDisplay().addThemeButton(
                    "Neurath Transit Diagram",
                    DIAGRAM_ICON,
                    btn -> TransitDiagramWindow.get().toggle()
            );
            event.getThemeButtonDisplay().addThemeButton(
                    "Neurath Map Layers",
                    DIAGRAM_ICON,
                    btn -> JmFullscreenToolStripLayer.get().toggleFilterPopover()
            );
        });

        // 2. Capture live JourneyMap IFullscreen reference every frame for smooth vector line rendering
        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe(NeurathMod.MOD_ID, event -> {
            if (event.getFullscreen() != null) {
                JmFullscreenToolStripLayer.get().updateFullscreen(event.getFullscreen());
            }
        });

        // 3. Add right-click context menu entries on JourneyMap fullscreen
        FullscreenEventRegistry.FULLSCREEN_POPUP_MENU_EVENT.subscribe(NeurathMod.MOD_ID, event -> {
            var menu = event.getPopupMenu().createSubItemList("Neurath Transit");
            menu.addMenuItem("+ Place Transit Station Here", blockPos -> openStationModalAt(blockPos));
            menu.addMenuItem("Spectate This Location", blockPos -> {
                if (blockPos == null) return;
                String dim = currentDimensionId();
                ClientSpectateController.get().spectatePoint(
                        dim, blockPos.getX() + 0.5, Math.max(80, blockPos.getY() + 36), blockPos.getZ() + 0.5,
                        0f, 55f, String.format("Map (%d, %d)", blockPos.getX(), blockPos.getZ()));
                closeJourneyMapScreen();
            });
            menu.addMenuItem("Toggle Lines Overlay", blockPos -> NeurathClientState.get().toggleLinesOverlay());
            menu.addMenuItem("Toggle Stations Overlay", blockPos -> NeurathClientState.get().toggleStationsOverlay());
            menu.addMenuItem("Open Transit Diagram", blockPos -> TransitDiagramWindow.get().setOpen(true));
        });

        // 4. Handle left-click on fullscreen map according to active Liskov tool strip mode
        FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT.subscribe(NeurathMod.MOD_ID, event -> {
            if (event.getButton() != 0) return;
            if (TransitDiagramWindow.get().isOpen()) return;

            NeurathClientState state = NeurathClientState.get();
            BlockPos pos = event.getLocation();
            if (pos == null) return;

            if (event.getStage() == FullscreenMapEvent.Stage.PRE) {
                switch (state.mapToolMode()) {
                    case PLACE_STATION -> {
                        event.cancel();
                        openStationModalAt(pos);
                    }
                    case CONNECT_LINE -> {
                        event.cancel();
                        handleConnectClick(pos, event.getLevel());
                    }
                    case SPECTATE_AREA -> {
                        event.cancel();
                        spectateDragging = true;
                        dragStartPos = pos;
                        dragStartScreenX = event.getMouseX();
                        dragStartScreenY = event.getMouseY();
                        JmFullscreenToolStripLayer.get().updateDragBox(
                                true,
                                (float) dragStartScreenX, (float) dragStartScreenY,
                                (float) dragStartScreenX, (float) dragStartScreenY,
                                pos.getX(), pos.getZ(), pos.getX(), pos.getZ());
                    }
                    case SELECT -> {}
                }
            } else if (event.getStage() == FullscreenMapEvent.Stage.POST && spectateDragging) {
                spectateDragging = false;
                JmFullscreenToolStripLayer.get().clearDragBox();
                BlockPos start = dragStartPos != null ? dragStartPos : pos;
                dragStartPos = null;
                String dim = event.getLevel() != null ? event.getLevel().identifier().toString() : currentDimensionId();
                int surfaceY = Math.max(start.getY(), pos.getY());
                ClientSpectateController.get().spectateArea(
                        dim,
                        Math.min(start.getX(), pos.getX()),
                        Math.min(start.getZ(), pos.getZ()),
                        Math.max(start.getX(), pos.getX()),
                        Math.max(start.getZ(), pos.getZ()),
                        surfaceY > 0 ? surfaceY : 72
                );
                closeJourneyMapScreen();
            }
        });

        // 5. Handle drag updates for Spectate Area bounding box
        FullscreenEventRegistry.FULLSCREEN_MAP_DRAG_EVENT.subscribe(NeurathMod.MOD_ID, event -> {
            if (!spectateDragging || event.getButton() != 0) return;
            event.cancel();
            BlockPos cur = event.getLocation();
            if (cur == null || dragStartPos == null) return;
            JmFullscreenToolStripLayer.get().updateDragBox(
                    true,
                    (float) dragStartScreenX, (float) dragStartScreenY,
                    (float) event.getMouseX(), (float) event.getMouseY(),
                    dragStartPos.getX(), dragStartPos.getZ(),
                    cur.getX(), cur.getZ()
            );
        });

        refreshOverlays();
        LOG.info("[Neurath] JourneyMap plugin initialized.");
    }

    private void handleConnectClick(BlockPos pos, ResourceKey<Level> levelKey) {
        NeurathClientState state = NeurathClientState.get();
        String dim = levelKey != null ? levelKey.identifier().toString() : currentDimensionId();
        Station hit = state.network().findNearestStation(dim, pos.getX(), pos.getZ(), 24.0);
        if (hit == null) {
            MubWidgets.ToastHost.push("No station within 24 blocks of click", SemanticColor.WARN);
            return;
        }
        String fromId = state.pendingConnectFromId();
        if (fromId == null) {
            state.setPendingConnectFromId(hit.id);
            state.setSelectedStationId(hit.id);
            MubWidgets.ToastHost.push("Connect from " + hit.name + " -> click next station", SemanticColor.WARN);
            return;
        }
        if (fromId.equals(hit.id)) {
            state.setPendingConnectFromId(null);
            return;
        }
        TransitLine line = state.activeLine();
        if (line != null) {
            line.connectStops(fromId, hit.id);
            state.setPendingConnectFromId(hit.id);
            state.setSelectedStationId(hit.id);
            state.save("Connect station to " + hit.code + " on " + line.code, true);
            MubWidgets.ToastHost.push("Connected -> " + hit.name + " on " + line.code, SemanticColor.MR);
        }
    }

    private void openStationModalAt(BlockPos pos) {
        if (pos == null) return;
        int y = pos.getY() > -64 ? pos.getY() : 64;
        TransitDiagramWindow.get().editModal().openNewStation(currentDimensionId(), pos.getX(), y, pos.getZ());
    }

    private static void closeJourneyMapScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null && mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
    }

    private static String currentDimensionId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            return mc.level.dimension().identifier().toString();
        }
        return "minecraft:overworld";
    }

    /**
     * Rebuilds JourneyMap overlays:
     * <ul>
     *     <li>Minimap transit lines as non-degenerate {@link PolygonOverlay} ribbons (fullscreen lines
     *     are rendered with smooth anti-aliased vector strokes in {@link JmFullscreenToolStripLayer})</li>
     *     <li>Station {@code U}/mode sign pictograms as {@link MarkerOverlay}s on both Fullscreen and Minimap</li>
     * </ul>
     */
    public void refreshOverlays() {
        if (jmApi == null) return;
        jmApi.removeAll(NeurathMod.MOD_ID);
        NeurathClientState state = NeurathClientState.get();
        TransitNetwork net = state.network();
        if (net == null) return;

        // 1. Minimap line ribbons (Fullscreen uses anti-aliased ImDrawList vector rendering)
        if (state.isShowLinesOverlay()) {
            double halfWidth = Math.max(1.2, net.effectiveLineThickness() * 0.45);
            for (TransitLine line : net.lines.values()) {
                if (!net.isLineEffectivelyVisible(line)) continue;
                for (TransitLine.LineBranch branch : line.branches) {
                    if (branch == null || branch.stationIds == null || branch.stationIds.size() < 2) continue;
                    List<Station> run = new ArrayList<>();
                    String runDim = null;
                    for (String sid : branch.stationIds) {
                        Station st = net.stations.get(sid);
                        if (st == null) continue;
                        if (runDim != null && !runDim.equals(st.dimensionId)) {
                            showMinimapBranchRun(line, branch, runDim, run, halfWidth);
                            run.clear();
                        }
                        runDim = st.dimensionId;
                        run.add(st);
                    }
                    if (run.size() >= 2 && runDim != null) {
                        showMinimapBranchRun(line, branch, runDim, run, halfWidth);
                    }
                }
            }
        }

        // 2. Crisp U / mode sign pictograms for stations
        if (state.isShowStationsOverlay()) {
            for (Station s : net.stations.values()) {
                showStationMarker(net, s);
            }
        }
    }

    private void showStationMarker(TransitNetwork net, Station s) {
        ResourceKey<Level> dimKey = resolveDimKey(s.dimensionId);
        List<StationSign> signs = s.effectiveSigns(net);
        MapImage icon = StationSignIconGenerator.iconFor(signs, s.extension);

        MarkerOverlay marker = new MarkerOverlay(NeurathMod.MOD_ID, new BlockPos(s.x, s.y, s.z), icon);
        marker.setDimension(dimKey);
        marker.setOverlayGroupName("Neurath Stations");
        marker.setDisplayOrder(20);

        StringBuilder linesInfo = new StringBuilder();
        for (TransitLine l : net.linesServing(s.id)) {
            if (!linesInfo.isEmpty()) linesInfo.append(", ");
            linesInfo.append(l.code);
        }
        String extTag = s.extension ? " [EXT]" : "";
        String title = s.name + extTag + (linesInfo.isEmpty() ? "" : " · " + linesInfo);
        marker.setTitle(title);
        marker.setLabel(s.name + extTag);
        marker.setTextProperties(new TextProperties()
                .setColor(s.extension ? 0xF5B731 : 0xEDF1F8)
                .setBackgroundColor(0x0C0E14)
                .setBackgroundOpacity(0.84f)
                .setFontShadow(false)
                .setScale(1.0f)
                .setMinZoom(1)
                .setOffsetY(12));

        marker.setOverlayListener(new IOverlayListener() {
            @Override
            public void onOverlayMenuPopup(UIState mapState, Point2D.Double mapPosition, BlockPos blockPosition, ModPopupMenu modPopupMenu) {
                var sub = modPopupMenu.createSubItemList("Station: " + s.name);
                sub.addMenuItem("Edit Station", bp -> TransitDiagramWindow.get().editModal().openEditStation(s));
                sub.addMenuItem(s.extension ? "Mark as Built (Solid Line)" : "Mark as Extension (Dotted Line)", bp -> {
                    s.extension = !s.extension;
                    NeurathClientState.get().save("Toggle extension on " + s.code, true);
                });
                sub.addMenuItem("Spectate Station", bp -> {
                    ClientSpectateController.get().spectatePoint(
                            s.dimensionId, s.x + 0.5, s.y + 32.0, s.z + 0.5, 0f, 55f, s.name + " (" + s.code + ")");
                    closeJourneyMapScreen();
                });
            }
        });

        try {
            jmApi.show(marker);
        } catch (Exception e) {
            LOG.debug("[Neurath] Skipped station marker overlay: {}", e.getMessage());
        }
    }

    private void showMinimapBranchRun(TransitLine line, TransitLine.LineBranch branch, String dimensionId, List<Station> run, double halfWidth) {
        if (run.size() < 2) return;
        ResourceKey<Level> dimKey = resolveDimKey(dimensionId);
        List<double[]> fullPts = new ArrayList<>();
        int n = run.size();
        for (int i = 0; i < n - 1; i++) {
            Station p0 = run.get(Math.max(0, i - 1));
            Station p1 = run.get(i);
            Station p2 = run.get(i + 1);
            Station p3 = run.get(Math.min(n - 1, i + 2));
            List<double[]> span = sampleCentripetalCatmullRomSpan(p0, p1, p2, p3);
            if (!fullPts.isEmpty() && !span.isEmpty()) {
                fullPts.addAll(span.subList(1, span.size()));
            } else {
                fullPts.addAll(span);
            }
        }
        MapPolygon ribbon = buildNonDegenerateRibbon(fullPts, halfWidth);
        if (ribbon == null) return;
        ShapeProperties shape = new ShapeProperties()
                .setStrokeColor(line.rgbColor)
                .setStrokeOpacity(0.92f)
                .setStrokeWidth(1.5f)
                .setFillColor(line.rgbColor)
                .setFillOpacity(0.82f);
        PolygonOverlay overlay = new PolygonOverlay(NeurathMod.MOD_ID, dimKey, shape, ribbon);
        overlay.setActiveUIs(Context.UI.Minimap);
        overlay.setOverlayGroupName("Neurath Lines");
        overlay.setTitle(line.code + " " + line.name);
        overlay.setDisplayOrder(10);
        try {
            jmApi.show(overlay);
        } catch (Exception e) {
            LOG.debug("[Neurath] Skipped minimap line overlay: {}", e.getMessage());
        }
    }

    /**
     * Samples a single span (p1 -> p2) using centripetal Catmull-Rom (alpha = 0.5), which prevents
     * cusps, self-intersections, and overshoot on unevenly spaced stations.
     */
    public static List<double[]> sampleCentripetalCatmullRomSpan(Station p0, Station p1, Station p2, Station p3) {
        double dist = Math.hypot(p2.x - p1.x, p2.z - p1.z);
        int steps = Math.max(6, Math.min(36, (int) Math.ceil(dist / 8.0)));

        double x0 = p0.x + 0.5, z0 = p0.z + 0.5;
        double x1 = p1.x + 0.5, z1 = p1.z + 0.5;
        double x2 = p2.x + 0.5, z2 = p2.z + 0.5;
        double x3 = p3.x + 0.5, z3 = p3.z + 0.5;

        if (p0 == p1) {
            x0 = x1 - (x2 - x1);
            z0 = z1 - (z2 - z1);
        }
        if (p2 == p3) {
            x3 = x2 + (x2 - x1);
            z3 = z2 + (z2 - z1);
        }

        double t0 = 0.0;
        double t1 = t0 + knotDelta(x0, z0, x1, z1);
        double t2 = t1 + knotDelta(x1, z1, x2, z2);
        double t3 = t2 + knotDelta(x2, z2, x3, z3);

        List<double[]> out = new ArrayList<>(steps + 1);
        for (int s = 0; s <= steps; s++) {
            double t = t1 + (t2 - t1) * ((double) s / steps);
            double[] a1 = lerp(x0, z0, x1, z1, t0, t1, t);
            double[] a2 = lerp(x1, z1, x2, z2, t1, t2, t);
            double[] a3 = lerp(x2, z2, x3, z3, t2, t3, t);
            double[] b1 = lerp(a1[0], a1[1], a2[0], a2[1], t0, t2, t);
            double[] b2 = lerp(a2[0], a2[1], a3[0], a3[1], t1, t3, t);
            out.add(lerp(b1[0], b1[1], b2[0], b2[1], t1, t2, t));
        }
        return out;
    }

    private static double knotDelta(double ax, double az, double bx, double bz) {
        double d = Math.hypot(bx - ax, bz - az);
        return Math.max(1e-3, Math.sqrt(d));
    }

    private static double[] lerp(double ax, double az, double bx, double bz, double ta, double tb, double t) {
        double denom = tb - ta;
        if (Math.abs(denom) < 1e-6) return new double[]{ax, az};
        double w1 = (tb - t) / denom;
        double w2 = (t - ta) / denom;
        return new double[]{w1 * ax + w2 * bx, w1 * az + w2 * bz};
    }

    private static MapPolygon buildNonDegenerateRibbon(List<double[]> centerline, double halfWidth) {
        if (centerline == null || centerline.size() < 2) return null;
        int m = centerline.size();
        List<BlockPos> leftSide = new ArrayList<>(m);
        List<BlockPos> rightSide = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            double[] prev = centerline.get(Math.max(0, i - 1));
            double[] cur = centerline.get(i);
            double[] next = centerline.get(Math.min(m - 1, i + 1));
            double dx = next[0] - prev[0];
            double dz = next[1] - prev[1];
            double len = Math.max(0.001, Math.hypot(dx, dz));
            double nx = (-dz / len) * halfWidth;
            double nz = (dx / len) * halfWidth;
            leftSide.add(new BlockPos((int) Math.round(cur[0] + nx), 64, (int) Math.round(cur[1] + nz)));
            rightSide.add(new BlockPos((int) Math.round(cur[0] - nx), 64, (int) Math.round(cur[1] - nz)));
        }
        List<BlockPos> poly = new ArrayList<>(m * 2);
        poly.addAll(leftSide);
        for (int i = m - 1; i >= 0; i--) {
            poly.add(rightSide.get(i));
        }
        return poly.size() >= 3 ? new MapPolygon(poly) : null;
    }

    private static ResourceKey<Level> resolveDimKey(String dimensionId) {
        Identifier id = Identifier.tryParse(dimensionId == null ? "minecraft:overworld" : dimensionId);
        if (id == null) id = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        return ResourceKey.create(Registries.DIMENSION, id);
    }
}
