package dev.neurath.client.jm;

import dev.liskov.core.SemanticColor;
import dev.liskov.ui.host.UiLayer;
import dev.liskov.ui.theme.MubTheme;
import dev.liskov.ui.theme.MubWidgets;
import dev.neurath.client.NeurathClientState;
import dev.neurath.client.ui.TransitDiagramWindow;
import dev.neurath.core.LineFolder;
import dev.neurath.core.Station;
import dev.neurath.core.TransitLine;
import dev.neurath.core.TransitMode;
import dev.neurath.core.TransitNetwork;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import journeymap.api.v2.client.fullscreen.IFullscreen;
import journeymap.api.v2.client.util.UIState;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact, single-bar Liskov overlay on JourneyMap's fullscreen screen (J):
 * <ul>
 *     <li>Renders smooth, anti-aliased, constant-screen-width transit lines (with dotted extensions
 *     and configurable line thickness) directly over JourneyMap fullscreen via {@link IFullscreen}</li>
 *     <li>Provides a sleek top-left tool bar with a compact {@code LAYERS v} popover for toggling
 *     Lines, Stations, Line Thickness, and individual lines/folders without overlapping JourneyMap's toolbar</li>
 * </ul>
 * Never captures keyboard input so ESC, J, and all JourneyMap shortcuts always work.
 */
public final class JmFullscreenToolStripLayer implements UiLayer {
    private static final JmFullscreenToolStripLayer INSTANCE = new JmFullscreenToolStripLayer();

    private volatile IFullscreen fullscreenRef;
    private boolean filterDropdownOpen;
    private final float[] thicknessBuf = new float[]{4.0f};

    private boolean dragActive;
    private float dragStartScreenX, dragStartScreenY;
    private float dragCurScreenX, dragCurScreenY;
    private double dragStartWorldX, dragStartWorldZ;
    private double dragCurWorldX, dragCurWorldZ;

    private JmFullscreenToolStripLayer() {}

    public static JmFullscreenToolStripLayer get() {
        return INSTANCE;
    }

    public void updateFullscreen(IFullscreen fullscreen) {
        this.fullscreenRef = fullscreen;
    }

    public void toggleFilterPopover() {
        this.filterDropdownOpen = !this.filterDropdownOpen;
    }

    public void updateDragBox(boolean active, float sx0, float sy0, float sx1, float sy1,
                              double wx0, double wz0, double wx1, double wz1) {
        this.dragActive = active;
        this.dragStartScreenX = sx0;
        this.dragStartScreenY = sy0;
        this.dragCurScreenX = sx1;
        this.dragCurScreenY = sy1;
        this.dragStartWorldX = wx0;
        this.dragStartWorldZ = wz0;
        this.dragCurWorldX = wx1;
        this.dragCurWorldZ = wz1;
    }

    public void clearDragBox() {
        this.dragActive = false;
    }

    public static boolean isJourneyMapFullscreenOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || mc.gui.screen() == null) return false;
        String cls = mc.gui.screen().getClass().getName();
        return cls.contains("journeymap") && cls.toLowerCase().contains("fullscreen");
    }

    @Override
    public String id() {
        return "neurath.jm_toolstrip";
    }

    @Override
    public int order() {
        return 35;
    }

    @Override
    public boolean visible() {
        boolean open = isJourneyMapFullscreenOpen() && !TransitDiagramWindow.get().isOpen();
        if (!open) {
            filterDropdownOpen = false;
        }
        return open;
    }

    /**
     * Never capture keyboard events on JourneyMap fullscreen so ESC, J, and map hotkeys always pass through.
     */
    @Override
    public boolean wantsKeyboard() {
        return false;
    }

    @Override
    public boolean wantsKey(int key, int scancode, int action, int mods) {
        return false;
    }

    @Override
    public void draw() {
        NeurathClientState state = NeurathClientState.get();
        var vp = ImGui.getMainViewport();

        // 1. Render smooth, anti-aliased, non-stepping transit lines onto the background draw list
        if (state.isShowLinesOverlay()) {
            drawFullscreenTransitLines(state, vp.getPosX(), vp.getPosY(), vp.getSizeX(), vp.getSizeY());
        }

        // 2. Single sleek top-left toolbar (no overlap with JourneyMap's top-center toolbar!)
        int barFlags = ImGuiWindowFlags.NoTitleBar
                | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.AlwaysAutoResize
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoNav;

        float barX = vp.getPosX() + 10f;
        float barY = vp.getPosY() + 10f;
        ImGui.setNextWindowPos(barX, barY, ImGuiCond.Always);
        float barBottomY = barY + 34f;
        if (ImGui.begin("##neurath_jm_strip", barFlags)) {
            MubWidgets.termBadge("Nt", SemanticColor.FOCUS);
            ImGui.sameLine(0f, 4f);
            drawToolBtn("SELECT", NeurathClientState.MapToolMode.SELECT, SemanticColor.FOCUS, state);
            ImGui.sameLine(0f, 3f);
            drawToolBtn("+ STN", NeurathClientState.MapToolMode.PLACE_STATION, SemanticColor.MR, state);
            ImGui.sameLine(0f, 3f);
            drawToolBtn("LINK", NeurathClientState.MapToolMode.CONNECT_LINE, SemanticColor.WARN, state);
            ImGui.sameLine(0f, 3f);
            drawToolBtn("SPEC", NeurathClientState.MapToolMode.SPECTATE_AREA, SemanticColor.PLAYER, state);
            ImGui.sameLine(0f, 6f);
            if (MubWidgets.termButton(filterDropdownOpen ? "LAYERS ^" : "LAYERS v",
                    filterDropdownOpen ? SemanticColor.CTA : SemanticColor.PLAYER)) {
                filterDropdownOpen = !filterDropdownOpen;
            }
            ImGui.sameLine(0f, 3f);
            if (MubWidgets.termButton("DIAGRAM", SemanticColor.CTA)) {
                TransitDiagramWindow.get().setOpen(true);
            }

            if (state.mapToolMode() == NeurathClientState.MapToolMode.CONNECT_LINE) {
                var active = state.activeLine();
                String lineLabel = active != null ? active.code + " " + active.name : "None";
                String hint = state.pendingConnectFromId() == null
                        ? "Click first station on map (Line: " + lineLabel + ")"
                        : "Click destination station to link on " + lineLabel;
                MubTheme.textColored(MubTheme.WARN, hint);
            } else if (state.mapToolMode() == NeurathClientState.MapToolMode.SPECTATE_AREA) {
                MubTheme.textColored(MubTheme.PLAYER, "Click a point or drag a box on the map to spectate that area.");
            } else if (state.mapToolMode() == NeurathClientState.MapToolMode.PLACE_STATION) {
                MubTheme.textColored(MubTheme.OK, "Click anywhere on the map to place a new transit station.");
            }
            barBottomY = ImGui.getWindowPosY() + ImGui.getWindowHeight() + 4f;
        }
        ImGui.end();

        // 3. Compact Layers & Line Thickness popover directly under the bar
        if (filterDropdownOpen) {
            drawLayersPopover(state, barX, barBottomY);
        }

        // 4. Render live drag-selection rectangle for Spectate Area
        if (dragActive) {
            ImDrawList dl = ImGui.getForegroundDrawList();
            float x0 = Math.min(dragStartScreenX, dragCurScreenX);
            float y0 = Math.min(dragStartScreenY, dragCurScreenY);
            float x1 = Math.max(dragStartScreenX, dragCurScreenX);
            float y1 = Math.max(dragStartScreenY, dragCurScreenY);
            int accent = MubTheme.u32(MubTheme.PLAYER);
            dl.addRectFilled(x0, y0, x1, y1, MubTheme.u32(MubTheme.PLAYER, 0.16f));
            dl.addRect(x0, y0, x1, y1, accent, 0f, 0, 2f);

            int spanX = (int) Math.round(Math.abs(dragCurWorldX - dragStartWorldX));
            int spanZ = (int) Math.round(Math.abs(dragCurWorldZ - dragStartWorldZ));
            int estY = Math.min(280, 96 + (int) Math.round(Math.max(spanX, spanZ) * 0.55));
            String tag = String.format("SPECTATE AREA · %dx%dm · CAM Y=%d", spanX, spanZ, estY);
            MubTheme.push(MubTheme.small());
            ImVec2 sz = ImGui.calcTextSize(tag);
            float ty = Math.max(4f, y0 - sz.y - 8f);
            dl.addRectFilled(x0, ty, x0 + sz.x + 10f, ty + sz.y + 4f, MubTheme.u32(MubTheme.PANEL, 0.95f));
            dl.addRect(x0, ty, x0 + sz.x + 10f, ty + sz.y + 4f, accent);
            dl.addText(x0 + 5f, ty + 2f, accent, tag);
            ImGui.popFont();
        }
    }

    /**
     * Draws smooth, anti-aliased, constant-screen-thickness transit lines (with dotted extensions)
     * directly onto JourneyMap's fullscreen viewport using live {@link IFullscreen} center &amp; scale
     * so lines stay locked pixel-for-pixel to the map while panning and zooming.
     */
    private void drawFullscreenTransitLines(NeurathClientState state, float vpX, float vpY, float vpW, float vpH) {
        IFullscreen fs = this.fullscreenRef;
        if (fs == null) return;
        UIState ui = fs.getUiState();
        if (ui == null || !ui.active) return;
        TransitNetwork net = state.network();
        if (net == null || net.lines.isEmpty()) return;

        // Live world center (including active drag offset!) + exact screen pixels-per-block
        double centerX = fs.getCenterBlockX(true);
        double centerZ = fs.getCenterBlockZ(true);
        double pxPerBlockX;
        double pxPerBlockY;
        if (ui.blockSize > 1e-6) {
            double scaleX = (ui.displayBounds != null && ui.displayBounds.width > 1.0) ? (vpW / ui.displayBounds.width) : 1.0;
            double scaleY = (ui.displayBounds != null && ui.displayBounds.height > 1.0) ? (vpH / ui.displayBounds.height) : 1.0;
            pxPerBlockX = ui.blockSize * scaleX;
            pxPerBlockY = ui.blockSize * scaleY;
        } else if (ui.blockBounds != null
                && (ui.blockBounds.maxX - ui.blockBounds.minX) > 1e-3
                && (ui.blockBounds.maxZ - ui.blockBounds.minZ) > 1e-3) {
            pxPerBlockX = vpW / (ui.blockBounds.maxX - ui.blockBounds.minX);
            pxPerBlockY = vpH / (ui.blockBounds.maxZ - ui.blockBounds.minZ);
        } else {
            return;
        }

        double halfW = vpW * 0.5;
        double halfH = vpH * 0.5;
        String activeDim = ui.dimension != null ? ui.dimension.identifier().toString() : "minecraft:overworld";
        float thickness = net.effectiveLineThickness();
        ImDrawList dl = ImGui.getBackgroundDrawList();
        dl.pushClipRect(vpX, vpY, vpX + vpW, vpY + vpH, true);

        for (TransitLine line : net.lines.values()) {
            if (!net.isLineEffectivelyVisible(line)) continue;
            boolean lineDefaultDashed = line.mode != null && line.mode.defaultLineStyle() == TransitMode.LineStyle.DASHED;
            int col = MubTheme.u32(line.rgbColor, 0.94f);
            int outlineCol = MubTheme.u32(0x0B0D12, 0.55f);

            for (TransitLine.LineBranch branch : line.branches) {
                if (branch == null || branch.stationIds == null || branch.stationIds.size() < 2) continue;
                List<Station> run = new ArrayList<>();
                for (String sid : branch.stationIds) {
                    Station st = net.stations.get(sid);
                    if (st == null || !activeDim.equals(st.dimensionId)) {
                        if (run.size() >= 2) {
                            drawScreenBranchRun(dl, run, branch.extension, lineDefaultDashed, col, outlineCol, thickness,
                                    vpX, vpY, halfW, halfH, centerX, centerZ, pxPerBlockX, pxPerBlockY);
                        }
                        run.clear();
                        continue;
                    }
                    run.add(st);
                }
                if (run.size() >= 2) {
                    drawScreenBranchRun(dl, run, branch.extension, lineDefaultDashed, col, outlineCol, thickness,
                            vpX, vpY, halfW, halfH, centerX, centerZ, pxPerBlockX, pxPerBlockY);
                }
            }
        }
        dl.popClipRect();
    }

    private static void drawScreenBranchRun(ImDrawList dl, List<Station> run, boolean branchExt, boolean defaultDashed,
                                            int col, int outlineCol, float thickness,
                                            float vpX, float vpY, double halfW, double halfH,
                                            double centerX, double centerZ, double pxPerBlockX, double pxPerBlockY) {
        int n = run.size();
        for (int i = 0; i < n - 1; i++) {
            Station p0 = run.get(Math.max(0, i - 1));
            Station p1 = run.get(i);
            Station p2 = run.get(i + 1);
            Station p3 = run.get(Math.min(n - 1, i + 2));

            boolean isExtension = branchExt || p1.extension || p2.extension;
            List<double[]> worldPts = NeurathJourneyMapPlugin.sampleCentripetalCatmullRomSpan(p0, p1, p2, p3);
            if (worldPts.size() < 2) continue;

            float[] sx = new float[worldPts.size()];
            float[] sy = new float[worldPts.size()];
            for (int k = 0; k < worldPts.size(); k++) {
                double[] wp = worldPts.get(k);
                sx[k] = vpX + (float) (halfW + (wp[0] - centerX) * pxPerBlockX);
                sy[k] = vpY + (float) (halfH + (wp[1] - centerZ) * pxPerBlockY);
            }

            if (isExtension) {
                drawScreenDottedPolyline(dl, sx, sy, col, outlineCol, thickness);
            } else if (defaultDashed) {
                drawScreenDashedPolyline(dl, sx, sy, col, thickness);
            } else {
                // Subtle dark halo + crisp anti-aliased colored stroke
                for (int k = 0; k < sx.length - 1; k++) {
                    dl.addLine(sx[k], sy[k], sx[k + 1], sy[k + 1], outlineCol, thickness + 2.0f);
                }
                for (int k = 0; k < sx.length - 1; k++) {
                    dl.addLine(sx[k], sy[k], sx[k + 1], sy[k + 1], col, thickness);
                }
            }
        }
    }

    private static void drawScreenDottedPolyline(ImDrawList dl, float[] sx, float[] sy, int col, int outlineCol, float thickness) {
        float radius = Math.max(1.8f, thickness * 0.55f);
        float step = Math.max(8f, thickness * 2.5f);
        float carry = 0f;
        for (int i = 0; i < sx.length - 1; i++) {
            float dx = sx[i + 1] - sx[i];
            float dy = sy[i + 1] - sy[i];
            float segLen = (float) Math.hypot(dx, dy);
            if (segLen < 0.5f) continue;
            float d = carry;
            while (d <= segLen) {
                float t = d / segLen;
                float px = sx[i] + dx * t;
                float py = sy[i] + dy * t;
                dl.addCircleFilled(px, py, radius + 1.0f, outlineCol, 10);
                dl.addCircleFilled(px, py, radius, col, 10);
                d += step;
            }
            carry = d - segLen;
        }
    }

    private static void drawScreenDashedPolyline(ImDrawList dl, float[] sx, float[] sy, int col, float thickness) {
        float dash = Math.max(8f, thickness * 2.5f);
        float gap = Math.max(5f, thickness * 1.5f);
        float period = dash + gap;
        float distAcc = 0f;
        for (int i = 0; i < sx.length - 1; i++) {
            float dx = sx[i + 1] - sx[i];
            float dy = sy[i + 1] - sy[i];
            float segLen = (float) Math.hypot(dx, dy);
            if (segLen < 0.5f) continue;
            float local = 0f;
            while (local < segLen) {
                float phase = (distAcc + local) % period;
                if (phase < dash) {
                    float drawLen = Math.min(dash - phase, segLen - local);
                    float t0 = local / segLen;
                    float t1 = (local + drawLen) / segLen;
                    dl.addLine(sx[i] + dx * t0, sy[i] + dy * t0, sx[i] + dx * t1, sy[i] + dy * t1, col, thickness);
                    local += drawLen;
                } else {
                    local += Math.min(period - phase, segLen - local);
                }
            }
            distAcc += segLen;
        }
    }

    private void drawLayersPopover(NeurathClientState state, float x, float y) {
        TransitNetwork net = state.network();
        float popW = 246f;
        ImGui.setNextWindowPos(x, y, ImGuiCond.Always);
        ImGui.setNextWindowSizeConstraints(popW, 90f, popW, 420f);
        int flags = ImGuiWindowFlags.NoTitleBar
                | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.AlwaysAutoResize
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoNav;

        if (ImGui.begin("##neurath_jm_line_filter", flags)) {
            MubWidgets.sectionHeader("MAP OVERLAYS");
            boolean linesOn = state.isShowLinesOverlay();
            boolean stationsOn = state.isShowStationsOverlay();
            if (MubWidgets.termButton(linesOn ? "LINES: ON" : "LINES: OFF",
                    linesOn ? SemanticColor.MR : SemanticColor.INK_MUTED, 111f, 21f)) {
                state.toggleLinesOverlay();
            }
            ImGui.sameLine(0f, 4f);
            if (MubWidgets.termButton(stationsOn ? "STATIONS: ON" : "STATIONS: OFF",
                    stationsOn ? SemanticColor.FOCUS : SemanticColor.INK_MUTED, 111f, 21f)) {
                state.toggleStationsOverlay();
            }

            // Configurable Line Thickness slider
            thicknessBuf[0] = net.effectiveLineThickness();
            ImGui.setNextItemWidth(-1f);
            if (ImGui.sliderFloat("##jm_line_thick", thicknessBuf, 1.5f, 10.0f, "Line Width: %.1f px")) {
                net.lineThickness = thicknessBuf[0];
            }
            if (ImGui.isItemDeactivatedAfterEdit()) {
                state.save("Update line thickness", true);
            }

            ImGui.separator();
            MubWidgets.sectionHeader("LINE VISIBILITY");
            if (MubWidgets.termButton("SHOW ALL", SemanticColor.MR, 111f, 19f)) {
                state.setShowLinesOverlay(true);
                state.setShowStationsOverlay(true);
                if (net.folders != null) {
                    for (LineFolder f : net.folders) f.visible = true;
                }
                for (TransitLine l : net.lines.values()) l.visible = true;
                state.notifyChanged();
            }
            ImGui.sameLine(0f, 4f);
            if (MubWidgets.termButton("HIDE ALL", SemanticColor.INK_MUTED, 111f, 19f)) {
                for (TransitLine l : net.lines.values()) l.visible = false;
                state.notifyChanged();
            }

            List<LineFolder> folders = net.folders != null ? net.folders : List.of();
            for (LineFolder folder : folders) {
                List<TransitLine> inFolder = linesInFolder(net, folder.id);
                if (MubWidgets.termButton((folder.visible ? "[*] " : "[ ] ") + folder.name + " (" + inFolder.size() + ")##jmf_" + folder.id,
                        folder.visible ? SemanticColor.FOCUS : SemanticColor.INK_MUTED, -1f, 20f)) {
                    folder.visible = !folder.visible;
                    state.notifyChanged();
                }
                for (TransitLine line : inFolder) {
                    drawFilterLineRow(state, net, line, true);
                }
            }

            List<TransitLine> ungrouped = linesInFolder(net, "");
            if (!ungrouped.isEmpty()) {
                if (!folders.isEmpty()) {
                    MubTheme.textColored(MubTheme.GUTTER, "UNGROUPED");
                }
                for (TransitLine line : ungrouped) {
                    drawFilterLineRow(state, net, line, false);
                }
            }
        }
        ImGui.end();
    }

    private static void drawFilterLineRow(NeurathClientState state, TransitNetwork net, TransitLine line, boolean indented) {
        if (indented) ImGui.indent(10f);
        boolean effVis = net.isLineEffectivelyVisible(line);
        ImDrawList dl = ImGui.getWindowDrawList();
        float sx = ImGui.getCursorScreenPosX();
        float sy = ImGui.getCursorScreenPosY();
        dl.addRectFilled(sx, sy + 3f, sx + 8f, sy + 17f, MubTheme.u32(line.rgbColor, effVis ? 1.0f : 0.3f));
        ImGui.dummy(10f, 18f);
        ImGui.sameLine(0f, 4f);
        String tag = (line.visible ? "[*] " : "[ ] ") + line.code + " " + line.name;
        if (MubWidgets.termButton(tag + "##jml_" + line.id,
                effVis ? SemanticColor.MR : SemanticColor.INK_MUTED, -1f, 20f)) {
            line.visible = !line.visible;
            state.save("Toggle line " + line.code, true);
        }
        if (indented) ImGui.unindent(10f);
    }

    private static List<TransitLine> linesInFolder(TransitNetwork net, String folderId) {
        List<TransitLine> out = new ArrayList<>();
        for (TransitLine l : net.lines.values()) {
            String lf = l.folderId == null ? "" : l.folderId;
            if (folderId.isEmpty()) {
                if (lf.isEmpty() || net.findFolder(lf) == null) out.add(l);
            } else if (folderId.equals(lf)) {
                out.add(l);
            }
        }
        return out;
    }

    private static void drawToolBtn(String label, NeurathClientState.MapToolMode mode, SemanticColor tone, NeurathClientState state) {
        boolean active = state.mapToolMode() == mode;
        if (MubWidgets.termButton(label, active ? tone : SemanticColor.INK_MUTED)) {
            state.setMapToolMode(mode);
        }
    }
}
