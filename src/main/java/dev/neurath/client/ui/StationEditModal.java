package dev.neurath.client.ui;

import dev.liskov.core.SemanticColor;
import dev.liskov.ui.theme.MubTheme;
import dev.liskov.ui.theme.MubWidgets;
import dev.neurath.client.NeurathClientState;
import dev.neurath.core.LineFolder;
import dev.neurath.core.Station;
import dev.neurath.core.StationSign;
import dev.neurath.core.TransitLine;
import dev.neurath.core.TransitMode;
import dev.neurath.core.TransitNetwork;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Liskov ImGui modals for creating/editing stations, transit lines, and line folders.
 */
public final class StationEditModal {
    private boolean stationOpen;
    private boolean lineOpen;
    private boolean folderOpen;

    private Station editingStation;
    private TransitLine editingLine;
    private LineFolder editingFolder;

    private final ImString nameBuf = new ImString(64);
    private final ImString codeBuf = new ImString(8);
    private final ImString dimBuf = new ImString(64);
    private final ImString notesBuf = new ImString(128);
    private final ImInt xVal = new ImInt();
    private final ImInt yVal = new ImInt(64);
    private final ImInt zVal = new ImInt();
    private boolean stationExtension;
    private final Set<TransitMode> selectedModes = new LinkedHashSet<>();
    private final List<StationSign> selectedSigns = new ArrayList<>();
    private final Set<String> selectedLineIds = new LinkedHashSet<>();

    private final ImString lineNameBuf = new ImString(64);
    private final ImString lineCodeBuf = new ImString(8);
    private int lineModeIdx;
    private String lineFolderId = "";
    private final float[] lineColor = new float[]{0.95f, 0.36f, 0.41f};

    private final ImString folderNameBuf = new ImString(64);
    private final float[] folderColor = new float[]{0.51f, 0.55f, 0.97f};

    public void openNewStation(String dimensionId, int x, int y, int z) {
        this.editingStation = null;
        this.nameBuf.set("New Station");
        this.codeBuf.set("STN");
        this.dimBuf.set(dimensionId == null ? "minecraft:overworld" : dimensionId);
        this.notesBuf.set("");
        this.xVal.set(x);
        this.yVal.set(y);
        this.zVal.set(z);
        this.stationExtension = false;
        this.selectedModes.clear();
        this.selectedModes.add(TransitMode.METRO);
        this.selectedSigns.clear();
        this.selectedSigns.add(StationSign.UBAHN);
        this.selectedLineIds.clear();
        String activeLine = NeurathClientState.get().activeLineId();
        if (activeLine != null) this.selectedLineIds.add(activeLine);
        this.stationOpen = true;
        TransitDiagramWindow.releaseMouseIfGrabbed();
    }

    public void openEditStation(Station s) {
        if (s == null) return;
        this.editingStation = s;
        this.nameBuf.set(s.name != null ? s.name : "");
        this.codeBuf.set(s.code != null ? s.code : "");
        this.dimBuf.set(s.dimensionId != null ? s.dimensionId : "minecraft:overworld");
        this.notesBuf.set(s.notes != null ? s.notes : "");
        this.xVal.set(s.x);
        this.yVal.set(s.y);
        this.zVal.set(s.z);
        this.stationExtension = s.extension;
        this.selectedModes.clear();
        this.selectedModes.addAll(s.effectiveModes());
        this.selectedSigns.clear();
        if (s.signs != null && !s.signs.isEmpty()) {
            this.selectedSigns.addAll(s.signs);
        } else {
            this.selectedSigns.addAll(s.effectiveSigns(NeurathClientState.get().network()));
        }
        this.selectedLineIds.clear();
        for (TransitLine l : NeurathClientState.get().network().linesServing(s.id)) {
            this.selectedLineIds.add(l.id);
        }
        this.stationOpen = true;
        TransitDiagramWindow.releaseMouseIfGrabbed();
    }

    public void openNewLine() {
        openNewLineInFolder("");
    }

    public void openNewLineInFolder(String folderId) {
        this.editingLine = null;
        int nextNum = NeurathClientState.get().network().lines.size() + 1;
        this.lineCodeBuf.set("U" + nextNum);
        this.lineNameBuf.set("Line " + nextNum);
        this.lineModeIdx = 0;
        this.lineFolderId = folderId == null ? "" : folderId;
        int rgb = TransitMode.METRO.defaultRgb();
        lineColor[0] = ((rgb >> 16) & 0xFF) / 255f;
        lineColor[1] = ((rgb >> 8) & 0xFF) / 255f;
        lineColor[2] = (rgb & 0xFF) / 255f;
        this.lineOpen = true;
        TransitDiagramWindow.releaseMouseIfGrabbed();
    }

    public void openEditLine(TransitLine line) {
        if (line == null) return;
        this.editingLine = line;
        this.lineCodeBuf.set(line.code != null ? line.code : "");
        this.lineNameBuf.set(line.name != null ? line.name : "");
        this.lineModeIdx = line.mode != null ? line.mode.ordinal() : 0;
        this.lineFolderId = line.folderId != null ? line.folderId : "";
        int rgb = line.rgbColor & 0xFFFFFF;
        lineColor[0] = ((rgb >> 16) & 0xFF) / 255f;
        lineColor[1] = ((rgb >> 8) & 0xFF) / 255f;
        lineColor[2] = (rgb & 0xFF) / 255f;
        this.lineOpen = true;
        TransitDiagramWindow.releaseMouseIfGrabbed();
    }

    public void openNewFolder() {
        this.editingFolder = null;
        int nextNum = (NeurathClientState.get().network().folders != null ? NeurathClientState.get().network().folders.size() : 0) + 1;
        this.folderNameBuf.set("System " + nextNum);
        int rgb = 0x818CF8;
        folderColor[0] = ((rgb >> 16) & 0xFF) / 255f;
        folderColor[1] = ((rgb >> 8) & 0xFF) / 255f;
        folderColor[2] = (rgb & 0xFF) / 255f;
        this.folderOpen = true;
        TransitDiagramWindow.releaseMouseIfGrabbed();
    }

    public void openEditFolder(LineFolder folder) {
        if (folder == null) return;
        this.editingFolder = folder;
        this.folderNameBuf.set(folder.name != null ? folder.name : "");
        int rgb = folder.rgbColor & 0xFFFFFF;
        folderColor[0] = ((rgb >> 16) & 0xFF) / 255f;
        folderColor[1] = ((rgb >> 8) & 0xFF) / 255f;
        folderColor[2] = (rgb & 0xFF) / 255f;
        this.folderOpen = true;
        TransitDiagramWindow.releaseMouseIfGrabbed();
    }

    public void closeAll() {
        boolean wasOpen = isAnyOpen();
        this.stationOpen = false;
        this.lineOpen = false;
        this.folderOpen = false;
        if (wasOpen) {
            dev.liskov.ui.host.LiskovUiHost.regrabMouseIfFree();
        }
    }

    public boolean isAnyOpen() {
        return stationOpen || lineOpen || folderOpen;
    }

    public void draw() {
        boolean wasOpen = isAnyOpen();
        if (stationOpen) drawStationModal();
        if (lineOpen) drawLineModal();
        if (folderOpen) drawFolderModal();
        if (wasOpen && !isAnyOpen()) {
            dev.liskov.ui.host.LiskovUiHost.regrabMouseIfFree();
        }
    }

    private void drawStationModal() {
        ImGui.setNextWindowSize(420f, 560f);
        String title = editingStation == null ? "NEURATH // NEW STATION##stn_modal" : "NEURATH // EDIT STATION##stn_modal";
        if (ImGui.begin(title, ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize)) {
            MubWidgets.sectionHeader("STATION IDENTITY");
            ImGui.inputText("Name", nameBuf);
            ImGui.inputText("Code (3-4)", codeBuf);
            if (MubWidgets.termToggle("stn_ext_tog", "Extension / Planned Station (Dotted Lines)", stationExtension,
                    stationExtension ? SemanticColor.WARN : SemanticColor.INK_MUTED)) {
                stationExtension = !stationExtension;
            }

            ImGui.spacing();
            MubWidgets.sectionHeader("STATION SIGNS (MULTI-SELECT)");
            int sCol = 0;
            for (StationSign sign : StationSign.values()) {
                boolean active = selectedSigns.contains(sign);
                if (sCol > 0 && sCol % 5 != 0) ImGui.sameLine(0f, 4f);
                if (MubWidgets.termButton(sign.glyph() + " " + sign.name().substring(0, Math.min(3, sign.name().length())) + "##ms_" + sign.name(),
                        active ? sign.defaultMode().semanticColor() : SemanticColor.INK_MUTED, 74f, 22f)) {
                    if (active) selectedSigns.remove(sign);
                    else {
                        selectedSigns.add(sign);
                        selectedModes.add(sign.defaultMode());
                    }
                }
                sCol++;
            }

            ImGui.spacing();
            MubWidgets.sectionHeader("TRANSIT MODES (MULTI-SELECT)");
            int mCol = 0;
            for (TransitMode mode : TransitMode.values()) {
                boolean active = selectedModes.contains(mode);
                if (mCol > 0 && mCol % 4 != 0) ImGui.sameLine(0f, 4f);
                if (MubWidgets.termButton(mode.monogram() + " " + mode.name() + "##mm_" + mode.name(),
                        active ? mode.semanticColor() : SemanticColor.INK_MUTED, 92f, 20f)) {
                    if (active && selectedModes.size() > 1) selectedModes.remove(mode);
                    else selectedModes.add(mode);
                }
                mCol++;
            }

            ImGui.spacing();
            MubWidgets.sectionHeader("WORLD LOCATION");
            ImGui.inputText("Dimension", dimBuf);
            ImGui.inputInt("X", xVal);
            ImGui.inputInt("Y", yVal);
            ImGui.inputInt("Z", zVal);
            ImGui.inputText("Notes", notesBuf);

            ImGui.spacing();
            MubWidgets.sectionHeader("ASSIGNED LINES");
            NeurathClientState st = NeurathClientState.get();
            for (TransitLine l : st.network().lines.values()) {
                boolean assigned = selectedLineIds.contains(l.id);
                if (MubWidgets.termToggle("mod_ln_" + l.id, l.code + " " + l.name, assigned, SemanticColor.FOCUS)) {
                    if (assigned) selectedLineIds.remove(l.id);
                    else selectedLineIds.add(l.id);
                }
            }

            ImGui.separator();
            if (MubWidgets.termButton("SAVE STATION", SemanticColor.CTA, 140f, 24f)) {
                TransitMode primaryMode = selectedModes.isEmpty() ? TransitMode.METRO : selectedModes.iterator().next();
                Station target;
                if (editingStation == null) {
                    target = new Station(
                            null,
                            codeBuf.get(),
                            nameBuf.get(),
                            primaryMode,
                            dimBuf.get(),
                            xVal.get(), yVal.get(), zVal.get()
                    );
                    target.notes = notesBuf.get();
                    st.network().addStation(target);
                    st.setSelectedStationId(target.id);
                } else {
                    target = editingStation;
                    target.name = nameBuf.get().trim();
                    target.code = Station.sanitizeCode(codeBuf.get(), target.name);
                    target.mode = primaryMode;
                    target.dimensionId = dimBuf.get().trim();
                    target.x = xVal.get();
                    target.y = yVal.get();
                    target.z = zVal.get();
                    target.notes = notesBuf.get();
                }
                target.extension = stationExtension;
                target.modes = new LinkedHashSet<>(selectedModes.isEmpty() ? List.of(primaryMode) : selectedModes);
                target.signs = new ArrayList<>(selectedSigns);
                for (TransitLine l : st.network().lines.values()) {
                    boolean want = selectedLineIds.contains(l.id);
                    boolean has = l.servesStation(target.id);
                    if (want && !has) l.appendStop(target.id);
                    else if (!want && has) l.removeStation(target.id);
                }
                st.save((editingStation == null ? "Add" : "Edit") + " station " + target.name, true);
                MubWidgets.ToastHost.push((editingStation == null ? "Added " : "Updated ") + target.name + " (" + target.code + ")", SemanticColor.MR);
                stationOpen = false;
            }
            ImGui.sameLine();
            if (MubWidgets.termButton("CANCEL", SemanticColor.INK_MUTED, 90f, 24f)) {
                stationOpen = false;
            }
        }
        ImGui.end();
    }

    private void drawLineModal() {
        ImGui.setNextWindowSize(360f, 280f);
        String title = editingLine == null ? "NEURATH // NEW TRANSIT LINE##line_modal" : "NEURATH // EDIT TRANSIT LINE##line_modal";
        if (ImGui.begin(title, ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize)) {
            NeurathClientState st = NeurathClientState.get();
            TransitNetwork net = st.network();

            MubWidgets.sectionHeader("LINE DEFINITION");
            ImGui.inputText("Code", lineCodeBuf);
            ImGui.inputText("Name", lineNameBuf);

            TransitMode[] modes = TransitMode.values();
            if (ImGui.beginCombo("Mode", modes[lineModeIdx].displayName())) {
                for (int i = 0; i < modes.length; i++) {
                    if (ImGui.selectable(modes[i].monogram() + " - " + modes[i].displayName(), i == lineModeIdx)) {
                        lineModeIdx = i;
                        if (editingLine == null) {
                            int rgb = modes[i].defaultRgb();
                            lineColor[0] = ((rgb >> 16) & 0xFF) / 255f;
                            lineColor[1] = ((rgb >> 8) & 0xFF) / 255f;
                            lineColor[2] = (rgb & 0xFF) / 255f;
                        }
                    }
                }
                ImGui.endCombo();
            }

            // Folder assignment combo
            LineFolder curFolder = net.findFolder(lineFolderId);
            String folderPreview = curFolder != null ? curFolder.name : "(Ungrouped)";
            if (ImGui.beginCombo("Folder", folderPreview)) {
                if (ImGui.selectable("(Ungrouped)", curFolder == null)) {
                    lineFolderId = "";
                }
                if (net.folders != null) {
                    for (LineFolder f : net.folders) {
                        if (ImGui.selectable(f.name + "##lf_" + f.id, f.id.equals(lineFolderId))) {
                            lineFolderId = f.id;
                        }
                    }
                }
                ImGui.endCombo();
            }

            ImGui.colorEdit3("Color", lineColor);

            ImGui.separator();
            int rgb = (Math.round(lineColor[0] * 255f) << 16)
                    | (Math.round(lineColor[1] * 255f) << 8)
                    | Math.round(lineColor[2] * 255f);

            if (MubWidgets.termButton(editingLine == null ? "CREATE LINE" : "SAVE LINE", SemanticColor.CTA, 120f, 24f)) {
                if (editingLine == null) {
                    TransitLine line = new TransitLine(null, lineCodeBuf.get(), lineNameBuf.get(), modes[lineModeIdx], rgb);
                    line.folderId = lineFolderId;
                    net.addLine(line);
                    st.setActiveLineId(line.id);
                    st.save("Add line " + line.code, true);
                    MubWidgets.ToastHost.push("Created transit line " + line.name, SemanticColor.FOCUS);
                } else {
                    String c = lineCodeBuf.get().trim().toUpperCase();
                    editingLine.code = c.isEmpty() ? editingLine.code : c;
                    String n = lineNameBuf.get().trim();
                    editingLine.name = n.isEmpty() ? editingLine.code : n;
                    editingLine.mode = modes[lineModeIdx];
                    editingLine.rgbColor = rgb & 0xFFFFFF;
                    editingLine.folderId = lineFolderId;
                    st.save("Edit line " + editingLine.code, true);
                    MubWidgets.ToastHost.push("Updated transit line " + editingLine.name, SemanticColor.FOCUS);
                }
                lineOpen = false;
            }
            ImGui.sameLine();
            if (MubWidgets.termButton("CANCEL", SemanticColor.INK_MUTED, 80f, 24f)) {
                lineOpen = false;
            }
            if (editingLine != null) {
                ImGui.sameLine();
                if (MubWidgets.termButton("DELETE", SemanticColor.DANGER, 80f, 24f)) {
                    String deletedName = editingLine.code + " " + editingLine.name;
                    net.removeLine(editingLine.id);
                    st.save("Delete line " + deletedName, true);
                    MubWidgets.ToastHost.push("Deleted line " + deletedName, SemanticColor.DANGER);
                    lineOpen = false;
                }
            }
        }
        ImGui.end();
    }

    private void drawFolderModal() {
        ImGui.setNextWindowSize(340f, 200f);
        String title = editingFolder == null ? "NEURATH // NEW FOLDER##folder_modal" : "NEURATH // EDIT FOLDER##folder_modal";
        if (ImGui.begin(title, ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize)) {
            NeurathClientState st = NeurathClientState.get();
            TransitNetwork net = st.network();

            MubWidgets.sectionHeader("SYSTEM FOLDER");
            ImGui.inputText("Folder Name", folderNameBuf);
            ImGui.colorEdit3("Accent", folderColor);
            MubTheme.textColored(MubTheme.GUTTER, "Group lines by transit system (e.g. U-Bahn, S-Bahn, Ice).");

            ImGui.separator();
            int rgb = (Math.round(folderColor[0] * 255f) << 16)
                    | (Math.round(folderColor[1] * 255f) << 8)
                    | Math.round(folderColor[2] * 255f);

            if (MubWidgets.termButton(editingFolder == null ? "CREATE" : "SAVE", SemanticColor.CTA, 100f, 24f)) {
                String nm = folderNameBuf.get().trim();
                if (nm.isEmpty()) nm = "System";
                if (editingFolder == null) {
                    net.addFolder(new LineFolder(null, nm, rgb));
                    st.save("Add folder " + nm, true);
                    MubWidgets.ToastHost.push("Created folder " + nm, SemanticColor.FOCUS);
                } else {
                    editingFolder.name = nm;
                    editingFolder.rgbColor = rgb & 0xFFFFFF;
                    st.save("Edit folder " + nm, true);
                }
                folderOpen = false;
            }
            ImGui.sameLine();
            if (MubWidgets.termButton("CANCEL", SemanticColor.INK_MUTED, 80f, 24f)) {
                folderOpen = false;
            }
            if (editingFolder != null) {
                ImGui.sameLine();
                if (MubWidgets.termButton("DELETE", SemanticColor.DANGER, 80f, 24f)) {
                    String fn = editingFolder.name;
                    net.removeFolder(editingFolder.id);
                    st.save("Delete folder " + fn, true);
                    MubWidgets.ToastHost.push("Deleted folder " + fn + " (lines moved to Ungrouped)", SemanticColor.WARN);
                    folderOpen = false;
                }
            }
        }
        ImGui.end();
    }
}
