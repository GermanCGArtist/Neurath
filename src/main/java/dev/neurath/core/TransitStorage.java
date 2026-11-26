package dev.neurath.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.liskov.core.LiskovDrives;
import dev.liskov.core.LiskovFileType;
import dev.liskov.core.LiskovFs;
import dev.liskov.core.LiskovNamespace;
import dev.liskov.core.LiskovPaths;
import dev.liskov.core.SemanticColor;
import dev.liskov.core.vcs.VcsCommit;
import dev.liskov.core.vcs.VcsStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Persists {@link TransitNetwork} documents as {@code .ntransit} JSON files under Liskov Drives
 * ({@code liskov/local/neurath/}) with atomic writes and automatic {@code .liskov-vcs/} snapshots.
 */
public final class TransitStorage {
    public static final String NAMESPACE_ID = "neurath";
    public static final String FILE_TYPE_ID = "ntransit";
    public static final LiskovFileType NTRANSIT_TYPE = new LiskovFileType(
            FILE_TYPE_ID,
            ".ntransit",
            List.of(),
            "Nt",
            "Neurath Transit",
            SemanticColor.FOCUS,
            LiskovFileType.PreviewKind.TEXT_EDITABLE
    );

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path rootDir;
    private final VcsStore vcsStore;

    public TransitStorage(Path rootDir, Path vcsRootDir) throws IOException {
        this.rootDir = rootDir;
        Files.createDirectories(rootDir);
        this.vcsStore = vcsRootDir != null ? new VcsStore(vcsRootDir) : null;
    }

    /**
     * Registers Neurath's namespace and {@code .ntransit} file type with LiskovFs.
     */
    public static TransitStorage createWithLiskovFs() throws IOException {
        LiskovFs fs = LiskovFs.get();
        Path nsDir = fs.localRoot().resolve(NAMESPACE_ID);
        Files.createDirectories(nsDir);
        if (fs.namespace(NAMESPACE_ID) == null) {
            fs.registerNamespace(new LiskovNamespace(
                    NAMESPACE_ID,
                    "Neurath",
                    nsDir,
                    SemanticColor.FOCUS,
                    "Nt",
                    List.of(".ntransit")
            ));
        }
        fs.fileTypes().register(NTRANSIT_TYPE);
        return new TransitStorage(nsDir, fs.vcsRootDir());
    }

    public Path fileForContext(String worldOrServerKey) {
        String safe = LiskovPaths.safeName(worldOrServerKey == null || worldOrServerKey.isBlank() ? "default" : worldOrServerKey);
        return LiskovPaths.resolveInside(rootDir, safe + ".ntransit");
    }

    public TransitNetwork load(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return new TransitNetwork();
        }
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        TransitNetwork parsed = GSON.fromJson(raw, TransitNetwork.class);
        return parsed != null ? parsed : new TransitNetwork();
    }

    public VcsCommit save(Path file, TransitNetwork network, String authorUuid, String authorName, String message, boolean autoSave) throws IOException {
        String json = toJson(network);
        LiskovPaths.writeAtomicUtf8(file, json);
        if (vcsStore == null) return null;
        String rel = rootDir.relativize(file).toString().replace('\\', '/');
        String repoId = VcsStore.repoIdFor(NAMESPACE_ID + "/" + rel);
        return vcsStore.snapshot(
                LiskovDrives.LOCAL_DRIVE_ID,
                repoId,
                authorUuid == null ? "local" : authorUuid,
                authorName == null ? "Player" : authorName,
                (message == null || message.isBlank()) ? "Update transit network" : message,
                !autoSave,
                autoSave,
                Map.of(file.getFileName().toString(), json.getBytes(StandardCharsets.UTF_8))
        );
    }

    public static String toJson(TransitNetwork network) {
        return GSON.toJson(network == null ? new TransitNetwork() : network);
    }

    public static TransitNetwork fromJson(String json) {
        if (json == null || json.isBlank()) return new TransitNetwork();
        TransitNetwork parsed = GSON.fromJson(json, TransitNetwork.class);
        return parsed != null ? parsed : new TransitNetwork();
    }

    public Path rootDir() {
        return rootDir;
    }
}
