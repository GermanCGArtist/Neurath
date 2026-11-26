package dev.neurath.client.jm;

import com.mojang.blaze3d.platform.NativeImage;
import dev.neurath.core.StationSign;
import journeymap.api.v2.client.model.MapImage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates and caches crisp {@link MapImage} station pictograms (e.g. blue square {@code U},
 * green circle {@code S}, multi-sign combos like {@code [U](S)}) backed by {@link NativeImage}
 * for JourneyMap {@code MarkerOverlay}s. Icons render at constant screen-pixel size across zoom levels.
 */
public final class StationSignIconGenerator {
    private static final int BADGE_PX = 24;
    private static final int GAP_PX = 2;
    private static final double DISPLAY_BADGE_PX = 16.0;
    private static final double DISPLAY_GAP_PX = 1.5;

    private static final Map<String, MapImage> CACHE = new ConcurrentHashMap<>();

    private StationSignIconGenerator() {}

    public static MapImage iconFor(List<StationSign> signs, boolean extension) {
        List<StationSign> eff = (signs == null || signs.isEmpty()) ? List.of(StationSign.UBAHN) : signs;
        StringBuilder key = new StringBuilder();
        for (StationSign s : eff) {
            if (!key.isEmpty()) key.append('+');
            key.append(s.name());
        }
        if (extension) key.append(":ext");

        return CACHE.computeIfAbsent(key.toString(), k -> buildMapImage(eff, extension));
    }

    private static MapImage buildMapImage(List<StationSign> signs, boolean extension) {
        int count = signs.size();
        int width = count * BADGE_PX + (count - 1) * GAP_PX;
        int height = BADGE_PX;
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, width, height, true);

        // Transparent background
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setPixel(x, y, 0x00000000);
            }
        }

        for (int i = 0; i < count; i++) {
            int ox = i * (BADGE_PX + GAP_PX);
            drawBadge(img, ox, 0, signs.get(i), extension);
        }

        MapImage mapImage = new MapImage(img);
        double dispW = count * DISPLAY_BADGE_PX + (count - 1) * DISPLAY_GAP_PX;
        mapImage.setDisplayWidth(dispW);
        mapImage.setDisplayHeight(DISPLAY_BADGE_PX);
        mapImage.centerAnchors();
        mapImage.setBlur(false);
        return mapImage;
    }

    private static void drawBadge(NativeImage img, int ox, int oy, StationSign sign, boolean extension) {
        int bgAlpha = extension ? 0xD8 : 0xFF;
        int bgArgb = (bgAlpha << 24) | (sign.bgRgb() & 0xFFFFFF);
        int fgArgb = 0xFF000000 | (sign.fgRgb() & 0xFFFFFF);
        int outerBorder = 0xFF0B0D12;
        int innerBorder = extension ? 0xFFF5B731 : 0xFFE8EEF8;

        boolean circle = sign.shape() == StationSign.Shape.CIRCLE;
        double cx = (BADGE_PX - 1) * 0.5;
        double cy = (BADGE_PX - 1) * 0.5;
        double outerR = BADGE_PX * 0.5 - 0.3;
        double innerR = outerR - 1.3;
        double fillR = innerR - 1.1;

        for (int y = 0; y < BADGE_PX; y++) {
            for (int x = 0; x < BADGE_PX; x++) {
                int color = 0;
                if (circle) {
                    double dist = Math.hypot(x - cx, y - cy);
                    if (dist <= fillR) {
                        color = bgArgb;
                    } else if (dist <= innerR) {
                        boolean dashOn = !extension || (((x + y) / 3) % 2 == 0);
                        color = dashOn ? innerBorder : bgArgb;
                    } else if (dist <= outerR) {
                        color = outerBorder;
                    }
                } else {
                    boolean isOuter = (x == 0 || y == 0 || x == BADGE_PX - 1 || y == BADGE_PX - 1);
                    boolean isInner = (x == 1 || y == 1 || x == BADGE_PX - 2 || y == BADGE_PX - 2);
                    // Slightly chamfered corners for crisp sign look
                    boolean corner = (x == 0 || x == BADGE_PX - 1) && (y == 0 || y == BADGE_PX - 1);
                    if (corner) {
                        color = 0;
                    } else if (isOuter) {
                        color = outerBorder;
                    } else if (isInner) {
                        boolean dashOn = !extension || (((x + y) / 3) % 2 == 0);
                        color = dashOn ? innerBorder : bgArgb;
                    } else {
                        color = bgArgb;
                    }
                }
                if (color != 0) {
                    img.setPixel(ox + x, oy + y, color);
                }
            }
        }

        // Draw crisp 5x7 glyph scaled 2x (10x14) centered in the 24x24 badge
        char ch = sign.glyph().isEmpty() ? 'U' : Character.toUpperCase(sign.glyph().charAt(0));
        String[] bitmap = glyphBitmap(ch);
        int scale = 2;
        int gw = bitmap[0].length() * scale;
        int gh = bitmap.length * scale;
        int gx0 = ox + (BADGE_PX - gw) / 2;
        int gy0 = oy + (BADGE_PX - gh) / 2;

        for (int row = 0; row < bitmap.length; row++) {
            String line = bitmap[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != '#') continue;
                for (int sy = 0; sy < scale; sy++) {
                    for (int sx = 0; sx < scale; sx++) {
                        img.setPixel(gx0 + col * scale + sx, gy0 + row * scale + sy, fgArgb);
                    }
                }
            }
        }
    }

    private static String[] glyphBitmap(char c) {
        return switch (c) {
            case 'U' -> new String[]{
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#",
                    ".###."
            };
            case 'S' -> new String[]{
                    ".###.",
                    "#...#",
                    "#....",
                    ".###.",
                    "....#",
                    "#...#",
                    ".###."
            };
            case 'M' -> new String[]{
                    "#...#",
                    "##.##",
                    "#.#.#",
                    "#...#",
                    "#...#",
                    "#...#",
                    "#...#"
            };
            case 'R' -> new String[]{
                    "####.",
                    "#...#",
                    "#...#",
                    "####.",
                    "#.#..",
                    "#..#.",
                    "#...#"
            };
            case 'T' -> new String[]{
                    "#####",
                    "..#..",
                    "..#..",
                    "..#..",
                    "..#..",
                    "..#..",
                    "..#.."
            };
            case 'B' -> new String[]{
                    "####.",
                    "#...#",
                    "#...#",
                    "####.",
                    "#...#",
                    "#...#",
                    "####."
            };
            case 'I' -> new String[]{
                    ".###.",
                    "..#..",
                    "..#..",
                    "..#..",
                    "..#..",
                    "..#..",
                    ".###."
            };
            case 'N' -> new String[]{
                    "#...#",
                    "##..#",
                    "#.#.#",
                    "#..##",
                    "#...#",
                    "#...#",
                    "#...#"
            };
            case 'F' -> new String[]{
                    "#####",
                    "#....",
                    "#....",
                    "####.",
                    "#....",
                    "#....",
                    "#...."
            };
            case 'A' -> new String[]{
                    ".###.",
                    "#...#",
                    "#...#",
                    "#####",
                    "#...#",
                    "#...#",
                    "#...#"
            };
            default -> new String[]{
                    ".###.",
                    "#...#",
                    "....#",
                    "..##.",
                    "..#..",
                    ".....",
                    "..#.."
            };
        };
    }
}
