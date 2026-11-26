# Neurath

Octilinear & geographic transit network planner, smooth JourneyMap vector overlay, and Spectate mode for **Minecraft 26.3** (Fabric, Java 25), built on [Liskov](https://github.com/GermanCGArtist/Liskov).

## Features

- **Transit Diagram Window (`N`):** 3-pane Liskov ImGui workspace with folder-organized Lines & ordered Stops outliner, interactive Octilinear (45°/90°) and Geographic canvas, A* Route Finder, Network Statistics dashboard, and Station Inspector.
- **JourneyMap Integration (`J`):**
  - Pixel-locked, anti-aliased vector transit lines rendered directly over JourneyMap's fullscreen map (including live drag offset and exact `blockSize` scaling, dotted planned extensions, dashed ferry/air modes, and configurable line thickness).
  - Compact top-left tool strip (`SELECT`, `+ STN`, `LINK`, `SPEC`, `LAYERS`, `DIAGRAM`) and right-click map context menu.
  - Non-degenerate minimap line ribbons and crisp `U`/mode station sign pictograms.
- **Spectate Mode (`V`):** Freely inspect stations or map areas with a saved return anchor, previous gamemode restoration, and a compact top-center HUD pill.
- **Liskov Drives, VCS & Server Sync:** Stores `.ntransit` networks per world/server in Liskov Drives with automatic VCS snapshots and live `liskov:sync` reloads.

## Requirements

- **Minecraft:** `26.3`
- **Fabric Loader:** `>=0.20.1` and **Fabric API**
- **Java:** `25+`
- **[Liskov](https://github.com/GermanCGArtist/Liskov):** `0.2.3+mc26.3`
- **Recommended:** JourneyMap `26.x` (for fullscreen/minimap overlay integration)

## Controls & Shortcuts

| Key | Scope | Action |
|---|---|---|
| `N` | Global | Toggle Transit Diagram window |
| `V` | Global | Toggle Spectate mode / return to saved anchor |
| `Ctrl+P` | Global | Open unified Liskov Command Palette (`Neurath` commands included) |
| `F8` | Global | Open Liskov File Explorer |
| `Ctrl+F` | Diagram | Focus station search box |
| `F` or `0` | Diagram | Fit view to transit network |
| `G` | Diagram | Toggle Octilinear 45°/90° vs. Geographic view |
| `L` | Diagram | Toggle station labels |
| `=` / `-` | Diagram | Zoom in / out (mouse wheel zooms to cursor) |
| `Esc` | Diagram / Modal | Close active modal → clear search → close diagram and return to gameplay |

## Building from Source

```bash
# Windows (JDK 25)
build.bat test build

# Linux / macOS (JDK 25)
./gradlew test build
```

Output JAR: `build/libs/neurath-<version>.jar`

## Related Suite Mods

- **[Liskov](https://github.com/GermanCGArtist/Liskov)** — Shared ImGui UI host, Drives filesystem, `.liskov-vcs/`, and `liskov:sync`
- **[Manhattan](https://github.com/GermanCGArtist/Manhattan)** — Automation scripting, Midtown path engine, and fleet client
- **[Manhattan Fleet Server](https://github.com/GermanCGArtist/manhattan-fleet-server)** — Server-side multi-bot fleet coordinator
- **[Vitruvius](https://github.com/GermanCGArtist/Vitruvius)** — Blender-style 3D schematic scene editor (`F6`)
