package dev.neurath.client.ui;

import dev.liskov.core.SemanticColor;
import dev.liskov.ui.host.UiLayer;
import dev.liskov.ui.theme.MubTheme;
import dev.liskov.ui.theme.MubWidgets;
import dev.neurath.client.NeurathClientState;
import dev.neurath.client.spectate.ClientSpectateController;
import dev.neurath.core.LineFolder;
import dev.neurath.core.OctilinearLayoutEngine;
import dev.neurath.core.RouteFinder;
import dev.neurath.core.Station;
import dev.neurath.core.StationSign;
import dev.neurath.core.TransferLink;
import dev.neurath.core.TransitLine;
import dev.neurath.core.TransitMode;
import dev.neurath.core.TransitNetwork;
import dev.neurath.core.TransitStats;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Main Liskov ImGui Transit Diagram Window ("under the J menu") implementing the 3-pane layout:
 * <ul>
 *     <li>Left sidebar: Station quick-jump search, Folder-organized Lines outliner, ordered Stops
 *     outliner (with extension toggles), Transit Mode filters, and A* Route Finder</li>
 *     <li>Center canvas: Dot-grid backdrop, zoom-to-cursor pan/zoom, non-scaling lines and names,
 *     dotted extensions, U/mode pictogram station nodes (no redundant squares), and collision-avoiding labels</li>
 *     <li>Right sidebar: Station Inspector with Extension toggle, Signs, Modes, Line assignments,
 *     Spectate Station, Pin/Unpin, Edit, and Delete</li>
 * </ul>
 */
public final class TransitDiagramWindow implements UiLayer {
    private static final TransitDiagramWindow INSTANCE = new TransitDiagramWindow();

    // Constant screen-space dimensions (do NOT scale with map zoom!)
    private static final float BADGE_SIZE_PX = 16f;
    private static final float BADGE_GAP_PX = 2f;

    private final ImBoolean open = new ImBoolean(false);
    private final OctilinearLayoutEngine layoutEngine = new OctilinearLayoutEngine();
    private final RouteFinder routeFinder = new RouteFinder();
    private final StationEditModal editModal = new StationEditModal();
    private final Set<TransitMode> visibleModes = EnumSet.allOf(TransitMode.class);
    private final ImString stationSearchBuf = new ImString(48);
    private final float[] thicknessBuf = new float[]{4.0f};

    private boolean octilinearMode = true;
    private boolean showLabels = true;
    private boolean statsView = false;
    private boolean fitViewPending = false;
    private boolean focusSearchNextFrame = false;
    private String focusStationId = null;

    private float panX = 40f;
    private float panY = 30f;
    private float zoom = 1.0f;
    private String draggingStationId;
    private boolean panningCanvas;

    private String routeFromId;
    private String routeToId;
    private RouteFinder.RouteResult activeRoute;

    private TransitDiagramWindow() {}

    public static TransitDiagramWindow get() {
        return INSTANCE;
    }

    public StationEditModal editModal() {
        return editModal;
    }

    public boolean isOpen() {
        return open.get();
    }

    public void setOpen(boolean value) {
        open.set(value);
        if (value) {
            releaseMouseIfGrabbed();
        } else {
            dev.liskov.ui.host.LiskovUiHost.regrabMouseIfFree();
        }
    }

    public void toggle() {
        setOpen(!open.get());
    }

    public static void releaseMouseIfGrabbed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.mouseHandler != null && mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }
    }

    @Override
    public String id() {
        return "neurath.diagram";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public boolean visible() {
        return open.get() || editModal.isAnyOpen();
    }

    @Override
    public boolean blocksMouseGrab() {
        return open.get() || editModal.isAnyOpen();
    }

    @Override
    public boolean wantsKey(int key, int scancode, int action, int mods) {
        if (!visible()) return false;
        // Always allow standard Minecraft/OS function shortcuts (F1, F2 screenshot, F3 debug, F11 fullscreen)
        if (key == 290 || key == 291 || key == 292 || key == 300) {
            return false;
        }
        // Capture ESC (both press and release) so closing Neurath doesn't also open Minecraft's pause menu
        if (key == 256 /* GLFW_KEY_ESCAPE */) {
            return true;
        }
        boolean ctrl = (mods & (dev.liskov.ui.host.InputMap.GLFW_MOD_CONTROL | dev.liskov.ui.host.InputMap.GLFW_MOD_SUPER)) != 0;
        if (ctrl && key == 70 /* F */) {
            return true;
        }
        if (dev.liskov.ui.host.LiskovUiHost.imguiWantsTextInput()) {
            return true;
        }
        // Single-key diagram canvas shortcuts when diagram is open and not typing
        if (open.get() && !editModal.isAnyOpen()) {
            return key == 70 /* F */ || key == 71 /* G */ || key == 76 /* L */
                    || key == 48 /* 0 */ || key == 61 /* = */ || key == 45 /* - */;
        }
        return false;
    }

    @Override
    public void onKey(int key, int scancode, int action, int mods) {
        if (action != 1 /* GLFW_PRESS */) return;
        boolean ctrl = (mods & (dev.liskov.ui.host.InputMap.GLFW_MOD_CONTROL | dev.liskov.ui.host.InputMap.GLFW_MOD_SUPER)) != 0;
        if (key == 256 /* GLFW_KEY_ESCAPE */) {
            if (editModal.isAnyOpen()) {
                editModal.closeAll();
            } else if (!stationSearchBuf.get().isEmpty() && !dev.liskov.ui.host.LiskovUiHost.imguiWantsTextInput()) {
                stationSearchBuf.set("");
            } else if (open.get()) {
                setOpen(false);
            }
            return;
        }
        if (ctrl && key == 70 /* Ctrl+F */ && open.get() && !editModal.isAnyOpen()) {
            focusSearchNextFrame = true;
            return;
        }
        if (!open.get() || editModal.isAnyOpen() || dev.liskov.ui.host.LiskovUiHost.imguiWantsTextInput()) return;
        switch (key) {
            case 70 /* F */, 48 /* 0 */ -> fitViewPending = true;
            case 71 /* G */ -> { statsView = false; octilinearMode = !octilinearMode; }
            case 76 /* L */ -> showLabels = !showLabels;
            case 61 /* = */ -> zoom = Math.min(4.0f, zoom * 1.2f);
            case 45 /* - */ -> zoom = Math.max(0.2f, zoom / 1.2f);
            default -> {}
        }
    }

    @Override
    public void draw() {
        boolean wasOpen = open.get();
        if (visible()) {
            releaseMouseIfGrabbed();
        }
        if (open.get()) {
            drawMainWindow();
        }
        editModal.draw();
        if (wasOpen && !open.get()) {
            dev.liskov.ui.host.LiskovUiHost.regrabMouseIfFree();
        }
    }

    private void drawMainWindow() {
        var vp = ImGui.getMainViewport();
        ImGui.setNextWindowPos(vp.getPosX() + 48f, vp.getPosY() + 36f, imgui.flag.ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(
                Math.min(1220f, vp.getSizeX() - 96f),
                Math.min(740f, vp.getSizeY() - 72f),
                imgui.flag.ImGuiCond.FirstUseEver
        );

        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollbar;
        if (ImGui.begin("NEURATH // TRANSIT DIAGRAM##neurath_diagram", open, flags)) {
            drawTopToolbar();
            ImGui.separator();

            float availW = ImGui.getContentRegionAvailX();
            float availH = ImGui.getContentRegionAvailY();
            float leftW = 268f;
            float rightW = 256f;
            float centerW = Math.max(260f, availW - leftW - rightW - 14f);

            // Left Pane: Outliner (Folders, Lines, Stops), Modes & A* Route Finder
            if (ImGui.beginChild("##neurath_left", leftW, availH, true)) {
                drawLeftSidebar();
            }
            ImGui.endChild();

            ImGui.sameLine();

            // Center Pane: Octilinear / Geographic Canvas OR Network Statistics Dashboard
            int centerFlags = statsView ? 0 : (ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse);
            if (ImGui.beginChild("##neurath_canvas", centerW, availH, true, centerFlags)) {
                if (statsView) {
                    drawStatsDashboard();
                } else {
                    drawDiagramCanvas();
                }
            }
            ImGui.endChild();

            ImGui.sameLine();

            // Right Pane: Station Inspector
            if (ImGui.beginChild("##neurath_right", rightW, availH, true)) {
                drawInspectorSidebar();
            }
            ImGui.endChild();
        }
        ImGui.end();
    }

    private void drawTopToolbar() {
        NeurathClientState state = NeurathClientState.get();
        MubWidgets.termBadge("Nt", SemanticColor.FOCUS);
        ImGui.sameLine();
        if (MubWidgets.termButton("OCTILINEAR 45/90", (!statsView && octilinearMode) ? SemanticColor.FOCUS : SemanticColor.INK_MUTED)) {
            statsView = false;
            octilinearMode = true;
        }
        ImGui.sameLine();
        if (MubWidgets.termButton("GEOGRAPHIC", (!statsView && !octilinearMode) ? SemanticColor.FOCUS : SemanticColor.INK_MUTED)) {
            statsView = false;
            octilinearMode = false;
        }
        ImGui.sameLine();
        if (MubWidgets.termButton("STATS", statsView ? SemanticColor.CTA : SemanticColor.PLAYER)) {
            statsView = !statsView;
        }
        ImGui.sameLine();
        if (MubWidgets.termButton("+ STATION HERE", SemanticColor.MR)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                String dim = mc.level != null ? mc.level.dimension().identifier().toString() : "minecraft:overworld";
                editModal.openNewStation(dim, (int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()));
            } else {
                editModal.openNewStation("minecraft:overworld", 0, 64, 0);
            }
        }
        ImGui.sameLine();
        if (MubWidgets.termButton("FIT VIEW", SemanticColor.PLAYER)) {
            fitViewPending = true;
        }
        ImGui.sameLine();
        if (MubWidgets.termButton(showLabels ? "LABELS: ON" : "LABELS: OFF",
                showLabels ? SemanticColor.FOCUS : SemanticColor.INK_MUTED)) {
            showLabels = !showLabels;
        }
        ImGui.sameLine();
        thicknessBuf[0] = state.network().effectiveLineThickness();
        ImGui.setNextItemWidth(104f);
        if (ImGui.sliderFloat("##diag_line_thick", thicknessBuf, 1.5f, 10.0f, "W: %.1fpx")) {
            state.network().lineThickness = thicknessBuf[0];
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            state.save("Update line thickness", true);
        }
        ImGui.sameLine();
        if (MubWidgets.termButton("RESET PINS", SemanticColor.WARN)) {
            state.network().resetAllPins();
            state.save("Reset diagram pins", true);
            MubWidgets.ToastHost.push("Reset all manual station pins", SemanticColor.WARN);
        }
        ImGui.sameLine();
        if (MubWidgets.termButton("VCS SNAPSHOT", SemanticColor.BOT)) {
            state.save("Manual milestone snapshot", false);
        }
        ImGui.sameLine();
        MubTheme.textColored(MubTheme.GUTTER, String.format("  %.0f%% · Dbl-click to edit", zoom * 100f));
    }

    private void drawLeftSidebar() {
        NeurathClientState state = NeurathClientState.get();
        TransitNetwork net = state.network();
        float availW = ImGui.getContentRegionAvailX();

        // Quick station search / jump
        if (focusSearchNextFrame) {
            ImGui.setKeyboardFocusHere();
            focusSearchNextFrame = false;
        }
        ImGui.setNextItemWidth(-1f);
        ImGui.inputTextWithHint("##stn_search", "Search stations... (Ctrl+F)", stationSearchBuf);
        String query = stationSearchBuf.get().trim().toLowerCase(Locale.ROOT);
        if (!query.isEmpty()) {
            int hits = 0;
            for (Station s : net.stations.values()) {
                if ((s.name != null && s.name.toLowerCase(Locale.ROOT).contains(query))
                        || (s.code != null && s.code.toLowerCase(Locale.ROOT).contains(query))) {
                    boolean sel = s.id.equals(state.selectedStationId());
                    if (MubWidgets.termButton(s.code + " " + s.name + "##srch_" + s.id,
                            sel ? SemanticColor.FOCUS : SemanticColor.INK_MUTED, -1f, 20f)) {
                        state.setSelectedStationId(s.id);
                        focusStationId = s.id;
                    }
                    if (++hits >= 8) break;
                }
            }
            if (hits == 0) {
                MubTheme.textColored(MubTheme.GUTTER, "No matching stations.");
            }
            ImGui.separator();
        }

        // LINES OUTLINER WITH FOLDERS
        MubWidgets.sectionHeader("LINES (" + net.lines.size() + ")");
        float halfW = (availW - 4f) * 0.5f;
        if (MubWidgets.termButton("+ LINE", SemanticColor.FOCUS, halfW, 22f)) {
            editModal.openNewLine();
        }
        ImGui.sameLine(0f, 4f);
        if (MubWidgets.termButton("+ FOLDER", SemanticColor.PLAYER, halfW, 22f)) {
            editModal.openNewFolder();
        }
        ImGui.spacing();

        List<LineFolder> folders = net.folders != null ? new ArrayList<>(net.folders) : List.of();
        for (LineFolder folder : folders) {
            List<TransitLine> inFolder = linesInFolder(net, folder.id);
            drawFolderHeader(state, folder, inFolder.size());
            if (!folder.collapsed) {
                for (TransitLine line : inFolder) {
                    drawLineOutlinerRow(state, net, line, true);
                }
            }
        }

        List<TransitLine> ungrouped = linesInFolder(net, "");
        if (!ungrouped.isEmpty()) {
            if (!folders.isEmpty()) {
                ImGui.spacing();
                MubTheme.textColored(MubTheme.GUTTER, "UNGROUPED (" + ungrouped.size() + ")");
            }
            for (TransitLine line : ungrouped) {
                drawLineOutlinerRow(state, net, line, false);
            }
        }

        // ORDERED STOPS OUTLINER FOR ACTIVE LINE
        TransitLine activeLine = state.activeLine();
        if (activeLine != null && activeLine.branches != null && !activeLine.branches.isEmpty()) {
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            MubWidgets.sectionHeader("STOPS: " + activeLine.code + " (" + activeLine.uniqueStopCount() + ")");

            for (int bIdx = 0; bIdx < activeLine.branches.size(); bIdx++) {
                TransitLine.LineBranch branch = activeLine.branches.get(bIdx);
                if (branch == null || branch.stationIds == null) continue;
                List<String> stops = branch.stationIds;

                if (activeLine.branches.size() > 1) {
                    MubTheme.textColored(MubTheme.DIM, branch.name + " (" + stops.size() + ")");
                }

                if (stops.isEmpty()) {
                    MubTheme.textColored(MubTheme.GUTTER, "No stops assigned to " + activeLine.code + " yet.");
                    continue;
                }

                float bAvail = ImGui.getContentRegionAvailX();
                float revW = (bAvail - 4f) * 0.52f;
                float extW = bAvail - revW - 4f;
                if (MubWidgets.termButton("REVERSE##rev_" + activeLine.id + "_" + bIdx, SemanticColor.INK_MUTED, revW, 19f)) {
                    activeLine.reverseBranch(bIdx);
                    state.save("Reverse " + activeLine.code + " order", true);
                }
                ImGui.sameLine(0f, 4f);
                if (MubWidgets.termButton(branch.extension ? "BRANCH: EXT" : "BRANCH: SOLID",
                        branch.extension ? SemanticColor.WARN : SemanticColor.INK_MUTED, extW, 19f)) {
                    branch.extension = !branch.extension;
                    state.save("Toggle branch extension on " + activeLine.code, true);
                }

                float rowAvail = ImGui.getContentRegionAvailX();
                float ctrlW = 26f + 2f + 20f + 2f + 20f + 2f + 20f; // EXT + ^ + v + x
                float nameBtnW = Math.max(60f, rowAvail - ctrlW - 4f);

                for (int i = 0; i < stops.size(); i++) {
                    String sid = stops.get(i);
                    Station st = net.stations.get(sid);
                    String rawName = st != null ? (i + 1) + ". " + st.name : (i + 1) + ". ?";
                    String label = truncateText(rawName, 16);
                    boolean isSel = sid.equals(state.selectedStationId());
                    boolean isExt = st != null && st.extension;

                    if (MubWidgets.termButton(label + "##ord_" + bIdx + "_" + i,
                            isSel ? SemanticColor.FOCUS : (isExt ? SemanticColor.WARN : SemanticColor.INK_MUTED), nameBtnW, 20f)) {
                        state.setSelectedStationId(sid);
                        focusStationId = sid;
                    }
                    ImGui.sameLine(0f, 2f);
                    if (MubWidgets.termButton("..##ext_" + bIdx + "_" + i,
                            isExt ? SemanticColor.WARN : SemanticColor.INK_MUTED, 26f, 20f)) {
                        if (st != null) {
                            st.extension = !st.extension;
                            state.save("Toggle extension on " + st.code, true);
                        }
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(isExt ? "Extension station (dotted line) — click to mark built" : "Click to mark as extension (dotted line)");
                    }
                    ImGui.sameLine(0f, 2f);
                    if (MubWidgets.termButton("^##up_" + bIdx + "_" + i, i > 0 ? SemanticColor.PLAYER : SemanticColor.INK_MUTED, 20f, 20f)) {
                        if (activeLine.moveStopUp(bIdx, i)) {
                            state.save("Move stop up on " + activeLine.code, true);
                            break;
                        }
                    }
                    ImGui.sameLine(0f, 2f);
                    if (MubWidgets.termButton("v##dn_" + bIdx + "_" + i, i < stops.size() - 1 ? SemanticColor.PLAYER : SemanticColor.INK_MUTED, 20f, 20f)) {
                        if (activeLine.moveStopDown(bIdx, i)) {
                            state.save("Move stop down on " + activeLine.code, true);
                            break;
                        }
                    }
                    ImGui.sameLine(0f, 2f);
                    if (MubWidgets.termButton("x##rm_" + bIdx + "_" + i, SemanticColor.DANGER, 20f, 20f)) {
                        if (activeLine.removeStopAt(bIdx, i)) {
                            state.save("Remove stop from " + activeLine.code, true);
                            break;
                        }
                    }
                }
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        MubWidgets.sectionHeader("TRANSIT MODES");
        float modeBtnW = (ImGui.getContentRegionAvailX() - 4f) * 0.5f;
        int col = 0;
        for (TransitMode mode : TransitMode.values()) {
            boolean on = visibleModes.contains(mode);
            if (col > 0 && col % 2 != 0) ImGui.sameLine(0f, 4f);
            if (MubWidgets.termButton(mode.monogram() + " " + mode.name(), on ? mode.semanticColor() : SemanticColor.INK_MUTED, modeBtnW, 20f)) {
                if (on && visibleModes.size() > 1) visibleModes.remove(mode);
                else visibleModes.add(mode);
            }
            col++;
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        MubWidgets.sectionHeader("A* ROUTE FINDER");
        List<Station> stationList = new ArrayList<>(net.stations.values());
        if (stationList.size() >= 2) {
            if (routeFromId == null || !net.stations.containsKey(routeFromId)) routeFromId = stationList.get(0).id;
            if (routeToId == null || !net.stations.containsKey(routeToId)) routeToId = stationList.get(stationList.size() - 1).id;

            Station fromS = net.stations.get(routeFromId);
            Station toS = net.stations.get(routeToId);
            if (ImGui.beginCombo("From##rf", fromS != null ? fromS.code + " - " + fromS.name : "")) {
                for (Station s : stationList) {
                    if (ImGui.selectable(s.code + " - " + s.name + "##rf_" + s.id, s.id.equals(routeFromId))) {
                        routeFromId = s.id;
                    }
                }
                ImGui.endCombo();
            }
            if (ImGui.beginCombo("To##rt", toS != null ? toS.code + " - " + toS.name : "")) {
                for (Station s : stationList) {
                    if (ImGui.selectable(s.code + " - " + s.name + "##rt_" + s.id, s.id.equals(routeToId))) {
                        routeToId = s.id;
                    }
                }
                ImGui.endCombo();
            }
            float rfAvail = ImGui.getContentRegionAvailX();
            if (MubWidgets.termButton("FIND ROUTE", SemanticColor.CTA, rfAvail - 58f, 22f)) {
                activeRoute = routeFinder.findRoute(net, routeFromId, routeToId);
                if (activeRoute == null) {
                    MubWidgets.ToastHost.push("No connected route found", SemanticColor.WARN);
                }
            }
            ImGui.sameLine(0f, 4f);
            if (MubWidgets.termButton("CLR", SemanticColor.INK_MUTED, 54f, 22f)) {
                activeRoute = null;
            }

            if (activeRoute != null) {
                ImGui.spacing();
                MubTheme.textColored(MubTheme.OK,
                        (activeRoute.stationIds().size() - 1) + " STOPS · " + activeRoute.transfers() + " TRANSFER(S)");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < activeRoute.stationIds().size(); i++) {
                    Station s = net.stations.get(activeRoute.stationIds().get(i));
                    if (i > 0) sb.append(" -> ");
                    sb.append(s != null ? s.code : "?");
                }
                ImGui.textWrapped(sb.toString());
            }
        } else {
            MubTheme.textColored(MubTheme.GUTTER, "Add at least 2 stations to route.");
        }
    }

    private void drawFolderHeader(NeurathClientState state, LineFolder folder, int count) {
        float avail = ImGui.getContentRegionAvailX();
        float btnW = Math.max(60f, avail - 72f);

        if (MubWidgets.termButton((folder.collapsed ? ">" : "v") + "##fcol_" + folder.id, SemanticColor.INK_MUTED, 20f, 20f)) {
            folder.collapsed = !folder.collapsed;
        }
        ImGui.sameLine(0f, 2f);
        if (MubWidgets.termButton((folder.visible ? "*" : " ") + "##fvis_" + folder.id,
                folder.visible ? SemanticColor.MR : SemanticColor.INK_MUTED, 20f, 20f)) {
            folder.visible = !folder.visible;
            state.save("Toggle folder " + folder.name, true);
        }
        ImGui.sameLine(0f, 2f);
        if (MubWidgets.termButton(truncateText(folder.name, 16) + " (" + count + ")##fnm_" + folder.id,
                folder.visible ? SemanticColor.PLAYER : SemanticColor.INK_MUTED, btnW, 20f)) {
            folder.collapsed = !folder.collapsed;
        }
        ImGui.sameLine(0f, 2f);
        if (MubWidgets.termButton("E##fed_" + folder.id, SemanticColor.INK_MUTED, 22f, 20f)) {
            editModal.openEditFolder(folder);
        }
    }

    private void drawLineOutlinerRow(NeurathClientState state, TransitNetwork net, TransitLine line, boolean indented) {
        if (indented) ImGui.indent(10f);
        boolean isActive = line.id.equals(state.activeLineId());
        boolean effVis = net.isLineEffectivelyVisible(line);

        // Color swatch
        ImDrawList dl = ImGui.getWindowDrawList();
        float sx = ImGui.getCursorScreenPosX();
        float sy = ImGui.getCursorScreenPosY();
        dl.addRectFilled(sx, sy + 2f, sx + 6f, sy + 18f, MubTheme.u32(line.rgbColor, effVis ? 1.0f : 0.3f));
        ImGui.dummy(8f, 20f);
        ImGui.sameLine(0f, 2f);

        // Independent visibility toggle button (does NOT change active line)
        if (MubWidgets.termButton((line.visible ? "*" : " ") + "##lvis_" + line.id,
                effVis ? SemanticColor.MR : SemanticColor.INK_MUTED, 20f, 20f)) {
            line.visible = !line.visible;
            state.save("Toggle line " + line.code, true);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(line.visible ? "Hide line" : "Show line");
        }
        ImGui.sameLine(0f, 2f);

        // Active line selector button (does NOT toggle visibility!)
        float nameW = Math.max(60f, ImGui.getContentRegionAvailX() - 26f);
        String label = truncateText(line.code + " " + line.name, 18);
        if (MubWidgets.termButton(label + "##lsel_" + line.id,
                isActive ? SemanticColor.FOCUS : SemanticColor.INK_MUTED, nameW, 20f)) {
            state.setActiveLineId(line.id);
        }
        ImGui.sameLine(0f, 2f);

        // Edit line button (rename, recolor, folder, delete)
        if (MubWidgets.termButton("E##led_" + line.id, SemanticColor.INK_MUTED, 22f, 20f)) {
            editModal.openEditLine(line);
        }
        if (indented) ImGui.unindent(10f);
    }

    private void drawDiagramCanvas() {
        NeurathClientState state = NeurathClientState.get();
        TransitNetwork net = state.network();

        float cx0 = ImGui.getCursorScreenPosX();
        float cy0 = ImGui.getCursorScreenPosY();
        float cw = ImGui.getContentRegionAvailX();
        float ch = ImGui.getContentRegionAvailY();
        ImDrawList dl = ImGui.getWindowDrawList();

        MubWidgets.paintDotGridBackdrop(dl, cx0, cy0, cw, ch, zoom);
        dl.pushClipRect(cx0, cy0, cx0 + cw, cy0 + ch, true);

        ImGui.invisibleButton("##canvas_hit", Math.max(1f, cw), Math.max(1f, ch));
        boolean hovered = ImGui.isItemHovered();
        float mx = ImGui.getIO().getMousePosX();
        float my = ImGui.getIO().getMousePosY();

        OctilinearLayoutEngine.LayoutResult layout = layoutEngine.compute(net, octilinearMode, visibleModes);

        // Apply one-shot FIT VIEW or focus-station centering
        if (fitViewPending && !layout.stationPositions().isEmpty()) {
            fitViewToStations(layout, cw, ch);
            fitViewPending = false;
        } else if (focusStationId != null) {
            OctilinearLayoutEngine.Vec2 targetPos = layout.stationPositions().get(focusStationId);
            if (targetPos != null) {
                panX = cw * 0.5f - targetPos.x() * zoom;
                panY = ch * 0.5f - targetPos.y() * zoom;
            }
            focusStationId = null;
        }

        // Zoom toward mouse cursor + pan (right/middle drag, or left-drag on empty canvas)
        if (hovered) {
            float wheel = ImGui.getIO().getMouseWheel();
            if (Math.abs(wheel) > 0.01f) {
                float oldZoom = zoom;
                float newZoom = Math.max(0.25f, Math.min(3.5f, zoom * (wheel > 0 ? 1.14f : 1f / 1.14f)));
                float logicalX = (mx - cx0 - panX) / oldZoom;
                float logicalY = (my - cy0 - panY) / oldZoom;
                zoom = newZoom;
                panX = (mx - cx0) - logicalX * zoom;
                panY = (my - cy0) - logicalY * zoom;
            }
            if (ImGui.isMouseDragging(ImGuiMouseButton.Right) || ImGui.isMouseDragging(ImGuiMouseButton.Middle)) {
                ImVec2 delta = ImGui.getIO().getMouseDelta();
                panX += delta.x;
                panY += delta.y;
            }
        }

        // Draw Transfer Links (dashed purple, constant screen thickness)
        for (TransferLink link : net.transfers) {
            OctilinearLayoutEngine.Vec2 a = layout.stationPositions().get(link.fromStationId);
            OctilinearLayoutEngine.Vec2 b = layout.stationPositions().get(link.toStationId);
            if (a == null || b == null) continue;
            drawDashedLine(dl, toScreenX(cx0, a.x()), toScreenY(cy0, a.y()),
                    toScreenX(cx0, b.x()), toScreenY(cy0, b.y()),
                    MubTheme.u32(MubTheme.BOT, 0.85f), 2.4f);
        }

        // Draw Line Segments (CONSTANT configurable screen-pixel thickness — does NOT scale with zoom!)
        float baseThickness = net.effectiveLineThickness();
        for (OctilinearLayoutEngine.RoutedSegment seg : layout.segments()) {
            boolean inRoute = isSegmentInRoute(seg);
            float alpha = (activeRoute != null && !inRoute) ? 0.22f : 0.95f;
            float thickness = inRoute ? baseThickness + 1.8f : baseThickness;
            int col = MubTheme.u32(seg.rgbColor(), alpha);
            List<OctilinearLayoutEngine.Vec2> pts = seg.points();
            for (int i = 0; i < pts.size() - 1; i++) {
                float x1 = toScreenX(cx0, pts.get(i).x());
                float y1 = toScreenY(cy0, pts.get(i).y());
                float x2 = toScreenX(cx0, pts.get(i + 1).x());
                float y2 = toScreenY(cy0, pts.get(i + 1).y());
                if (inRoute) {
                    dl.addLine(x1, y1, x2, y2, MubTheme.u32(MubTheme.FG, 0.24f), thickness + 5f);
                }
                if (seg.style() == TransitMode.LineStyle.DOTTED) {
                    drawDottedLine(dl, x1, y1, x2, y2, col, thickness);
                } else if (seg.style() == TransitMode.LineStyle.DASHED) {
                    drawDashedLine(dl, x1, y1, x2, y2, col, thickness);
                } else if (seg.style() == TransitMode.LineStyle.DOUBLE) {
                    dl.addLine(x1, y1, x2, y2, col, thickness + 1.5f);
                    dl.addLine(x1, y1, x2, y2, MubTheme.u32(MubTheme.BG, alpha), Math.max(1.2f, thickness - 2.2f));
                } else {
                    dl.addLine(x1, y1, x2, y2, col, thickness);
                }
            }
        }

        // Handle station click / double-click / drag-to-pin / empty-canvas pan
        boolean mouseDown = ImGui.isMouseDown(ImGuiMouseButton.Left);
        boolean mouseClicked = hovered && ImGui.isMouseClicked(ImGuiMouseButton.Left);
        boolean mouseDoubleClicked = hovered && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left);

        if (!mouseDown) {
            if (draggingStationId != null) {
                state.save("Pin station layout", true);
                draggingStationId = null;
            }
            panningCanvas = false;
        }

        // First pass: render station pictograms directly on the line (NO redundant black/white square!)
        // and collect their screen bounding boxes for collision-free label placement.
        record PlacedStation(Station station, float sx, float sy, float halfW, float halfH) {}
        List<PlacedStation> placedStations = new ArrayList<>();
        List<float[]> occupiedRects = new ArrayList<>();
        boolean clickedAnyStation = false;

        for (Map.Entry<String, OctilinearLayoutEngine.Vec2> e : layout.stationPositions().entrySet()) {
            Station s = net.stations.get(e.getKey());
            if (s == null) continue;
            float sx = toScreenX(cx0, e.getValue().x());
            float sy = toScreenY(cy0, e.getValue().y());

            List<StationSign> signs = s.effectiveSigns(net);
            if (signs.isEmpty()) signs = List.of(StationSign.UBAHN);
            float groupW = signs.size() * BADGE_SIZE_PX + (signs.size() - 1) * BADGE_GAP_PX;
            float halfW = groupW * 0.5f;
            float halfH = BADGE_SIZE_PX * 0.5f;

            boolean hit = Math.abs(mx - sx) <= (halfW + 6f) && Math.abs(my - sy) <= (halfH + 6f);
            if (mouseClicked && hit) {
                clickedAnyStation = true;
                state.setSelectedStationId(s.id);
                if (octilinearMode) {
                    draggingStationId = s.id;
                }
            }
            if (mouseDoubleClicked && hit) {
                editModal.openEditStation(s);
            }
            if (s.id.equals(draggingStationId) && mouseDown && octilinearMode) {
                float logicalX = (mx - cx0 - panX) / zoom;
                float logicalY = (my - cy0 - panY) / zoom;
                s.pinAt(OctilinearLayoutEngine.snap(logicalX), OctilinearLayoutEngine.snap(logicalY));
            }

            boolean selected = s.id.equals(state.selectedStationId());

            // Subtle dark halo behind pictogram(s) so lines meet cleanly
            float pad = 2f;
            dl.addRectFilled(sx - halfW - pad, sy - halfH - pad, sx + halfW + pad, sy + halfH + pad,
                    MubTheme.u32(MubTheme.BG, 0.94f), 2f);
            if (selected) {
                dl.addRect(sx - halfW - 4f, sy - halfH - 4f, sx + halfW + 4f, sy + halfH + 4f,
                        MubTheme.u32(MubTheme.ACCENT), 2f, 0, 1.8f);
            } else if (s.extension) {
                dl.addRect(sx - halfW - 2.5f, sy - halfH - 2.5f, sx + halfW + 2.5f, sy + halfH + 2.5f,
                        MubTheme.u32(MubTheme.WARN, 0.9f), 2f, 0, 1.4f);
            }

            float bx = sx - halfW;
            float by = sy - halfH;
            for (StationSign sign : signs) {
                drawSignBadge(dl, bx, by, BADGE_SIZE_PX, sign, s.extension);
                bx += BADGE_SIZE_PX + BADGE_GAP_PX;
            }
            if (s.diagramPinned) {
                dl.addCircleFilled(sx + halfW + 3f, sy - halfH - 2f, 2.5f, MubTheme.u32(MubTheme.WARN));
            }

            placedStations.add(new PlacedStation(s, sx, sy, halfW + pad, halfH + pad));
            occupiedRects.add(new float[]{sx - halfW - 3f, sy - halfH - 3f, sx + halfW + 3f, sy + halfH + 3f});
        }

        // Left-drag on empty canvas pans smoothly
        if (mouseClicked && !clickedAnyStation) {
            panningCanvas = true;
        }
        if (panningCanvas && mouseDown && draggingStationId == null) {
            ImVec2 delta = ImGui.getIO().getMouseDelta();
            panX += delta.x;
            panY += delta.y;
        }

        // Second pass: collision-avoiding station name labels (constant screen font size!)
        if (showLabels) {
            MubTheme.push(MubTheme.small());
            for (PlacedStation ps : placedStations) {
                String labelText = ps.station().extension ? ps.station().name + " [EXT]" : ps.station().name;
                ImVec2 ts = ImGui.calcTextSize(labelText);
                float padX = 4f;
                float padY = 2f;
                float lw = ts.x + padX * 2f;
                float lh = ts.y + padY * 2f;
                float gap = 5f;

                float[][] candidates = new float[][]{
                        {ps.sx() + ps.halfW() + gap, ps.sy() - lh * 0.5f},                 // Right
                        {ps.sx() - lw * 0.5f, ps.sy() + ps.halfH() + gap},                 // Below
                        {ps.sx() - lw * 0.5f, ps.sy() - ps.halfH() - gap - lh},            // Above
                        {ps.sx() - ps.halfW() - gap - lw, ps.sy() - lh * 0.5f},            // Left
                        {ps.sx() + ps.halfW() + 3f, ps.sy() + ps.halfH() + 3f},            // Bottom-Right
                        {ps.sx() + ps.halfW() + 3f, ps.sy() - ps.halfH() - 3f - lh},       // Top-Right
                        {ps.sx() - ps.halfW() - 3f - lw, ps.sy() + ps.halfH() + 3f},       // Bottom-Left
                        {ps.sx() - ps.halfW() - 3f - lw, ps.sy() - ps.halfH() - 3f - lh}   // Top-Left
                };

                float bestX = candidates[0][0];
                float bestY = candidates[0][1];
                float bestOverlap = Float.POSITIVE_INFINITY;
                for (float[] c : candidates) {
                    float ov = computeOverlapArea(c[0], c[1], c[0] + lw, c[1] + lh, occupiedRects);
                    if (ov < bestOverlap) {
                        bestOverlap = ov;
                        bestX = c[0];
                        bestY = c[1];
                        if (ov <= 0.01f) break;
                    }
                }

                occupiedRects.add(new float[]{bestX - 2f, bestY - 1f, bestX + lw + 2f, bestY + lh + 1f});
                dl.addRectFilled(bestX, bestY, bestX + lw, bestY + lh, MubTheme.u32(MubTheme.PANEL, 0.84f), 2f);
                int textCol = ps.station().extension ? MubTheme.u32(MubTheme.WARN) : MubTheme.u32(MubTheme.FG);
                dl.addText(bestX + padX, bestY + padY, textCol, labelText);
            }
            ImGui.popFont();
        }

        if (net.stations.isEmpty()) {
            MubTheme.push(MubTheme.ui());
            String empty = "No stations yet. Place stations on JourneyMap (J) or click [+ STATION HERE].";
            ImVec2 sz = ImGui.calcTextSize(empty);
            dl.addText(cx0 + (cw - sz.x) * 0.5f, cy0 + (ch - sz.y) * 0.5f, MubTheme.u32(MubTheme.GUTTER), empty);
            ImGui.popFont();
        }

        dl.popClipRect();
    }

    private void drawStatsDashboard() {
        NeurathClientState state = NeurathClientState.get();
        TransitNetwork net = state.network();
        TransitStats.NetworkSummary stats = TransitStats.compute(net);
        float availW = ImGui.getContentRegionAvailX();
        ImDrawList dl = ImGui.getWindowDrawList();

        MubWidgets.sectionHeader("NETWORK OVERVIEW");
        float cardGap = 6f;
        float cardW = Math.max(100f, (availW - cardGap * 3f) * 0.25f);
        drawStatCard("STATIONS", stats.totalStations() + "",
                stats.builtStations() + " built · " + stats.extensionStations() + " ext", SemanticColor.FOCUS, cardW);
        ImGui.sameLine(0f, cardGap);
        drawStatCard("LINES / SYSTEMS", stats.totalLines() + " / " + stats.totalFolders(),
                stats.visibleLines() + " visible · " + stats.interchangeStations() + " interchanges", SemanticColor.PLAYER, cardW);
        ImGui.sameLine(0f, cardGap);
        drawStatCard("TOTAL LENGTH", TransitStats.formatDistance(stats.totalLength()),
                "Built: " + TransitStats.formatDistance(stats.builtLength()), SemanticColor.MR, cardW);
        ImGui.sameLine(0f, cardGap);
        drawStatCard("EXTENSIONS & SPACING", TransitStats.formatDistance(stats.extensionLength()),
                "Avg stop: " + TransitStats.formatDistance(stats.avgStopSpacing()), SemanticColor.WARN, cardW);

        if (stats.longestLine() != null && stats.longestLine().totalLength() > 0) {
            ImGui.spacing();
            MubTheme.textColored(MubTheme.DIM, "Longest Line: "
                    + stats.longestLine().code() + " " + stats.longestLine().name()
                    + " (" + TransitStats.formatDistance(stats.longestLine().totalLength())
                    + ", " + stats.longestLine().totalStops() + " stops)");
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // SYSTEMS / FOLDERS BREAKDOWN
        MubWidgets.sectionHeader("SYSTEMS / FOLDERS (" + stats.folderStats().size() + ")");
        if (stats.folderStats().isEmpty()) {
            MubTheme.textColored(MubTheme.GUTTER, "No systems or lines yet.");
        } else {
            for (TransitStats.FolderStat fs : stats.folderStats()) {
                drawBreakdownBarRow(dl, fs.rgbColor(), fs.name(),
                        fs.lineCount() + "L · " + fs.uniqueStationCount() + "S",
                        TransitStats.formatDistance(fs.totalLength())
                                + (fs.extensionLength() > 0 ? " (+" + TransitStats.formatDistance(fs.extensionLength()) + " ext)" : ""),
                        fs.share(), availW);
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // CATEGORIES / TRANSIT MODES BREAKDOWN
        MubWidgets.sectionHeader("CATEGORIES / TRANSIT MODES (" + stats.modeStats().size() + ")");
        if (stats.modeStats().isEmpty()) {
            MubTheme.textColored(MubTheme.GUTTER, "No transit modes in use yet.");
        } else {
            for (TransitStats.ModeStat ms : stats.modeStats()) {
                drawBreakdownBarRow(dl, ms.mode().defaultRgb(),
                        ms.mode().monogram() + " " + ms.mode().displayName(),
                        ms.lineCount() + "L · " + ms.uniqueStationCount() + "S",
                        TransitStats.formatDistance(ms.totalLength())
                                + (ms.extensionLength() > 0 ? " (+" + TransitStats.formatDistance(ms.extensionLength()) + " ext)" : ""),
                        ms.share(), availW);
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // PER-LINE DETAILED BREAKDOWN
        MubWidgets.sectionHeader("LINES BREAKDOWN (" + stats.lineStats().size() + ")");
        if (stats.lineStats().isEmpty()) {
            MubTheme.textColored(MubTheme.GUTTER, "No lines defined yet.");
        } else {
            MubTheme.push(MubTheme.small());
            MubTheme.textColored(MubTheme.GUTTER, String.format("%-18s %-12s %-5s %6s %9s %9s %8s",
                    "LINE", "SYSTEM", "MODE", "STOPS", "BUILT", "EXT", "AVG"));
            ImGui.popFont();
            ImGui.separator();

            for (TransitStats.LineStat ls : stats.lineStats()) {
                boolean active = ls.lineId().equals(state.activeLineId());
                float sx = ImGui.getCursorScreenPosX();
                float sy = ImGui.getCursorScreenPosY();
                dl.addRectFilled(sx, sy + 2f, sx + 5f, sy + 18f, MubTheme.u32(ls.rgbColor(), ls.visible() ? 1.0f : 0.35f));
                ImGui.dummy(7f, 20f);
                ImGui.sameLine(0f, 3f);

                String stopsTxt = ls.extensionStops() > 0
                        ? ls.builtStops() + "+" + ls.extensionStops()
                        : String.valueOf(ls.totalStops());
                String rowTxt = String.format("%-17s %-12s %-5s %6s %9s %9s %8s",
                        truncateText(ls.code() + " " + ls.name(), 17),
                        truncateText(ls.folderName(), 12),
                        ls.mode().monogram(),
                        stopsTxt,
                        TransitStats.formatDistance(ls.builtLength()),
                        ls.extensionLength() > 0 ? TransitStats.formatDistance(ls.extensionLength()) : "-",
                        ls.avgSpacing() > 0 ? TransitStats.formatDistance(ls.avgSpacing()) : "-");

                MubTheme.push(MubTheme.monoS());
                if (MubWidgets.termButton(rowTxt + "##stat_l_" + ls.lineId(),
                        active ? SemanticColor.FOCUS : SemanticColor.INK_MUTED, -1f, 20f)) {
                    state.setActiveLineId(ls.lineId());
                }
                ImGui.popFont();
            }
        }
    }

    private static void drawStatCard(String title, String value, String subtitle, SemanticColor tone, float width) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        float h = 56f;
        int accent = MubTheme.u32(MubTheme.colorFor(tone));
        dl.addRectFilled(x, y, x + width, y + h, MubTheme.u32(MubTheme.PANEL2, 0.92f), 3f);
        dl.addRect(x, y, x + width, y + h, MubTheme.u32(MubTheme.LINE), 3f);
        dl.addRectFilled(x, y, x + 3f, y + h, accent);

        MubTheme.push(MubTheme.small());
        dl.addText(x + 9f, y + 5f, MubTheme.u32(MubTheme.GUTTER), title);
        ImGui.popFont();

        MubTheme.push(MubTheme.uiBold());
        dl.addText(x + 9f, y + 19f, accent, value);
        ImGui.popFont();

        MubTheme.push(MubTheme.small());
        dl.addText(x + 9f, y + 38f, MubTheme.u32(MubTheme.DIM), subtitle);
        ImGui.popFont();

        ImGui.dummy(width, h);
    }

    private static void drawBreakdownBarRow(ImDrawList dl, int rgb, String label, String counts, String lengthText, double share, float availW) {
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        float rowH = 26f;
        int col = MubTheme.u32(rgb);

        dl.addRectFilled(x, y + 3f, x + 6f, y + rowH - 3f, col);

        MubTheme.push(MubTheme.small());
        dl.addText(x + 12f, y + 2f, MubTheme.u32(MubTheme.FG), label + "   " + counts);
        String rightText = lengthText + String.format("  (%.0f%%)", share * 100.0);
        ImVec2 rsz = ImGui.calcTextSize(rightText);
        dl.addText(x + availW - rsz.x - 4f, y + 2f, MubTheme.u32(MubTheme.DIM), rightText);
        ImGui.popFont();

        // Visual percentage bar
        float barX0 = x + 12f;
        float barX1 = x + availW - 4f;
        float barY0 = y + 17f;
        float barY1 = y + 22f;
        dl.addRectFilled(barX0, barY0, barX1, barY1, MubTheme.u32(MubTheme.TRACK), 2f);
        float fillW = (float) Math.max(share > 0 ? 4.0 : 0.0, (barX1 - barX0) * Math.min(1.0, share));
        if (fillW > 0f) {
            dl.addRectFilled(barX0, barY0, barX0 + fillW, barY1, col, 2f);
        }
        ImGui.dummy(availW, rowH + 2f);
    }

    private void fitViewToStations(OctilinearLayoutEngine.LayoutResult layout, float cw, float ch) {
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (OctilinearLayoutEngine.Vec2 p : layout.stationPositions().values()) {
            if (p.x() < minX) minX = p.x();
            if (p.x() > maxX) maxX = p.x();
            if (p.y() < minY) minY = p.y();
            if (p.y() > maxY) maxY = p.y();
        }
        float spanX = Math.max(64f, maxX - minX);
        float spanY = Math.max(64f, maxY - minY);
        float margin = 90f;
        float zx = Math.max(0.25f, (cw - margin * 2f) / spanX);
        float zy = Math.max(0.25f, (ch - margin * 2f) / spanY);
        zoom = Math.max(0.25f, Math.min(2.2f, Math.min(zx, zy)));
        float midX = (minX + maxX) * 0.5f;
        float midY = (minY + maxY) * 0.5f;
        panX = cw * 0.5f - midX * zoom;
        panY = ch * 0.5f - midY * zoom;
    }

    private static float computeOverlapArea(float ax0, float ay0, float ax1, float ay1, List<float[]> rects) {
        float total = 0f;
        for (float[] r : rects) {
            float ox = Math.min(ax1, r[2]) - Math.max(ax0, r[0]);
            if (ox <= 0f) continue;
            float oy = Math.min(ay1, r[3]) - Math.max(ay0, r[1]);
            if (oy <= 0f) continue;
            total += ox * oy;
        }
        return total;
    }

    private void drawInspectorSidebar() {
        NeurathClientState state = NeurathClientState.get();
        TransitNetwork net = state.network();
        Station s = state.selectedStation();

        MubWidgets.sectionHeader("STATION INSPECTOR");
        if (s == null) {
            MubTheme.textColored(MubTheme.GUTTER, "Click any station on the diagram to inspect or spectate.");
            return;
        }

        MubTheme.push(MubTheme.uiBold());
        MubTheme.textColored(MubTheme.FG, s.name);
        ImGui.popFont();
        MubWidgets.termBadge(s.code, SemanticColor.FOCUS);
        if (s.diagramPinned) {
            ImGui.sameLine();
            MubWidgets.termBadge("PINNED", SemanticColor.WARN);
        }
        if (s.extension) {
            ImGui.sameLine();
            MubWidgets.termBadge("EXT", SemanticColor.WARN);
        }

        // Render active station signs inline (e.g. [U] (S) side-by-side)
        List<StationSign> effSigns = s.effectiveSigns(net);
        if (!effSigns.isEmpty()) {
            ImGui.spacing();
            float px = ImGui.getCursorScreenPosX();
            float py = ImGui.getCursorScreenPosY();
            ImDrawList dl = ImGui.getWindowDrawList();
            for (int i = 0; i < effSigns.size(); i++) {
                drawSignBadge(dl, px + i * 20f, py, 17f, effSigns.get(i), s.extension);
            }
            ImGui.dummy(effSigns.size() * 20f, 19f);
        }

        ImGui.spacing();
        MubTheme.textColored(MubTheme.DIM, "DIM: " + s.shortDimensionName());
        MubTheme.textColored(MubTheme.FG, String.format("WORLD: X %d · Y %d · Z %d", s.x, s.y, s.z));

        ImGui.spacing();
        if (MubWidgets.termToggle("insp_ext_" + s.id, "Extension / Planned (Dotted Line)", s.extension,
                s.extension ? SemanticColor.WARN : SemanticColor.INK_MUTED)) {
            s.extension = !s.extension;
            state.save("Toggle extension on " + s.code, true);
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        MubWidgets.sectionHeader("STATION SIGNS");
        int sc = 0;
        for (StationSign sign : StationSign.values()) {
            boolean on = s.signs != null && s.signs.contains(sign);
            if (sc > 0 && sc % 5 != 0) ImGui.sameLine(0f, 3f);
            if (MubWidgets.termButton(sign.glyph() + "##isg_" + sign.name(),
                    on ? sign.defaultMode().semanticColor() : SemanticColor.INK_MUTED, 42f, 20f)) {
                s.toggleSign(sign);
                if (s.signs != null && s.signs.contains(sign)) {
                    if (s.modes == null) s.modes = new java.util.LinkedHashSet<>();
                    s.modes.add(sign.defaultMode());
                }
                state.save("Toggle sign " + sign.glyph() + " on " + s.code, true);
            }
            sc++;
        }

        ImGui.spacing();
        MubWidgets.sectionHeader("TRANSIT MODES");
        int mc2 = 0;
        Set<TransitMode> curModes = s.effectiveModes();
        for (TransitMode mode : TransitMode.values()) {
            boolean on = curModes.contains(mode);
            if (mc2 > 0 && mc2 % 3 != 0) ImGui.sameLine(0f, 3f);
            if (MubWidgets.termButton(mode.monogram() + "##imd_" + mode.name(),
                    on ? mode.semanticColor() : SemanticColor.INK_MUTED, 70f, 19f)) {
                s.toggleMode(mode);
                state.save("Toggle mode " + mode.name() + " on " + s.code, true);
            }
            mc2++;
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        MubWidgets.sectionHeader("LINE ASSIGNMENTS");
        for (TransitLine l : net.lines.values()) {
            boolean assigned = l.servesStation(s.id);
            if (MubWidgets.termToggle("insp_assign_" + l.id, l.code + " " + l.name, assigned,
                    assigned ? SemanticColor.FOCUS : SemanticColor.INK_MUTED)) {
                if (assigned) {
                    l.removeStation(s.id);
                    state.save("Unassign " + s.code + " from " + l.code, true);
                } else {
                    l.appendStop(s.id);
                    state.setActiveLineId(l.id);
                    state.save("Assign " + s.code + " to " + l.code, true);
                }
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        if (MubWidgets.termButton("SPECTATE STATION", SemanticColor.CTA, -1f, 26f)) {
            ClientSpectateController.get().spectatePoint(
                    s.dimensionId, s.x + 0.5, s.y + 32.0, s.z + 0.5, 0f, 55f, s.name + " (" + s.code + ")");
            open.set(false);
        }
        float halfBtn = (ImGui.getContentRegionAvailX() - 4f) * 0.5f;
        if (MubWidgets.termButton(s.diagramPinned ? "UNPIN NODE" : "PIN NODE", SemanticColor.WARN, halfBtn, 22f)) {
            if (s.diagramPinned) s.unpin();
            else s.pinAt(s.diagramX, s.diagramY);
            state.save("Toggle pin " + s.code, true);
        }
        ImGui.sameLine(0f, 4f);
        if (MubWidgets.termButton("EDIT", SemanticColor.INK_MUTED, halfBtn, 22f)) {
            editModal.openEditStation(s);
        }
        if (MubWidgets.termButton("DELETE STATION", SemanticColor.DANGER, -1f, 22f)) {
            net.removeStation(s.id);
            state.setSelectedStationId(null);
            state.save("Delete station " + s.name, true);
        }
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

    private static String truncateText(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(1, maxChars - 1)) + "..";
    }

    private boolean isSegmentInRoute(OctilinearLayoutEngine.RoutedSegment seg) {
        if (activeRoute == null) return false;
        for (RouteFinder.RouteStep step : activeRoute.steps()) {
            if (!step.isTransferLink()
                    && seg.lineId().equals(step.lineId())
                    && ((seg.fromStationId().equals(step.fromStationId()) && seg.toStationId().equals(step.toStationId()))
                    || (seg.fromStationId().equals(step.toStationId()) && seg.toStationId().equals(step.fromStationId())))) {
                return true;
            }
        }
        return false;
    }

    private float toScreenX(float originX, float logicalX) {
        return originX + panX + logicalX * zoom;
    }

    private float toScreenY(float originY, float logicalY) {
        return originY + panY + logicalY * zoom;
    }

    private static void drawSignBadge(ImDrawList dl, float x, float y, float size, StationSign sign, boolean extension) {
        int bg = MubTheme.u32(sign.bgRgb(), extension ? 0.78f : 1.0f);
        int fg = MubTheme.u32(sign.fgRgb());
        int rim = extension ? MubTheme.u32(MubTheme.WARN) : MubTheme.u32(MubTheme.FG, 0.85f);
        if (sign.shape() == StationSign.Shape.CIRCLE) {
            float r = size * 0.5f;
            dl.addCircleFilled(x + r, y + r, r, bg, 18);
            dl.addCircle(x + r, y + r, r, rim, 18, 1.2f);
        } else {
            dl.addRectFilled(x, y, x + size, y + size, bg, 2f);
            dl.addRect(x, y, x + size, y + size, rim, 2f, 0, 1.2f);
        }
        MubTheme.push(MubTheme.small());
        ImVec2 ts = ImGui.calcTextSize(sign.glyph());
        dl.addText(x + (size - ts.x) * 0.5f, y + (size - ts.y) * 0.5f, fg, sign.glyph());
        ImGui.popFont();
    }

    private static void drawDashedLine(ImDrawList dl, float x1, float y1, float x2, float y2, int col, float thickness) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) return;
        float dash = 9f;
        float gap = 5f;
        float step = dash + gap;
        for (float d = 0f; d < len; d += step) {
            float t0 = d / len;
            float t1 = Math.min(1f, (d + dash) / len);
            dl.addLine(x1 + dx * t0, y1 + dy * t0, x1 + dx * t1, y1 + dy * t1, col, thickness);
        }
    }

    private static void drawDottedLine(ImDrawList dl, float x1, float y1, float x2, float y2, int col, float thickness) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) return;
        float radius = Math.max(1.8f, thickness * 0.55f);
        float step = Math.max(8f, thickness * 2.4f);
        for (float d = 0f; d <= len; d += step) {
            float t = d / len;
            dl.addCircleFilled(x1 + dx * t, y1 + dy * t, radius, col, 10);
        }
    }
}
