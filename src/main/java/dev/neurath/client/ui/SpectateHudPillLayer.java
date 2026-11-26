package dev.neurath.client.ui;

import dev.liskov.core.SemanticColor;
import dev.liskov.ui.host.UiLayer;
import dev.liskov.ui.theme.MubTheme;
import dev.neurath.client.spectate.ClientSpectateController;
import dev.neurath.net.SpectateAnchor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.Locale;

/**
 * Compact top-center Liskov HUD pill shown while Spectate mode is active, reminding the player
 * of their saved return anchor, previous gamemode, and return shortcut.
 */
public final class SpectateHudPillLayer implements UiLayer {
    private static final SpectateHudPillLayer INSTANCE = new SpectateHudPillLayer();

    private SpectateHudPillLayer() {}

    public static SpectateHudPillLayer get() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "neurath.spectate_hud";
    }

    @Override
    public int order() {
        return 15;
    }

    @Override
    public boolean visible() {
        return ClientSpectateController.get().isSpectating();
    }

    @Override
    public boolean wantsMouse() {
        return false;
    }

    @Override
    public boolean wantsKeyboard() {
        return false;
    }

    @Override
    public void draw() {
        ClientSpectateController ctrl = ClientSpectateController.get();
        if (!ctrl.isSpectating()) return;

        SpectateAnchor anchor = ctrl.anchor();
        String anchorText = anchor != null
                ? anchor.formattedCoords() + " (" + anchor.shortDimensionName() + " · "
                  + anchor.previousGameMode().toUpperCase(Locale.ROOT) + ")"
                : "SAVED";
        String target = ctrl.targetLabel();
        String line = "SPECTATING  |  ANCHOR: " + anchorText
                + (target != null && !target.isBlank() ? "  |  " + target : "")
                + "  |  [V] RETURN";

        var vp = ImGui.getMainViewport();
        ImDrawList dl = ImGui.getForegroundDrawList();
        MubTheme.push(MubTheme.small());
        ImVec2 sz = ImGui.calcTextSize(line);
        float padX = 12f;
        float padY = 6f;
        float w = sz.x + padX * 2f + 4f;
        float h = sz.y + padY * 2f;
        float x = vp.getPosX() + (vp.getSizeX() - w) * 0.5f;
        float y = vp.getPosY() + 12f;

        int accent = MubTheme.u32(MubTheme.colorFor(SemanticColor.PLAYER));
        dl.addRectFilled(x, y, x + w, y + h, MubTheme.u32(MubTheme.PANEL, 0.94f));
        dl.addRect(x, y, x + w, y + h, accent, 0f, 0, 1f);
        dl.addRectFilled(x, y, x + 3f, y + h, accent);
        dl.addText(x + padX + 2f, y + padY, MubTheme.u32(MubTheme.FG), line);
        ImGui.popFont();
    }
}
