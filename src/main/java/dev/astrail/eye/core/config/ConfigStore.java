package dev.astrail.eye.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.astrail.eye.api.module.ModuleState;
import dev.astrail.eye.api.setting.Setting;
import dev.astrail.eye.core.module.ModuleManager;
import java.io.IOException;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Single-module configuration store: enabled flag and per-setting JSON values. */
public final class ConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("AstrailEye/Config");
    private static final int SCHEMA_VERSION = 1;
    private static final String FILE_NAME = "config.json";
    private static final int SHUTDOWN_FLUSH_TIMEOUT_SECONDS = 2;

    private final Path file;
    private final ModuleManager modules;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AstrailEye-ConfigWriter");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean writeScheduled = new AtomicBoolean();
    private volatile boolean dirty;
    private volatile String pendingSave;

    public ConfigStore(Path configDirectory, ModuleManager modules) {
        this.file = configDirectory.resolve("astrail-eye").resolve(FILE_NAME);
        this.modules = modules;
    }

    public void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("schemaVersion") && root.get("schemaVersion").getAsInt() > SCHEMA_VERSION) {
                quarantine(new IllegalStateException(
                    "Config schema " + root.get("schemaVersion").getAsInt()
                        + " is newer than supported " + SCHEMA_VERSION
                ));
                return;
            }
            JsonObject moduleJson = root.getAsJsonObject("module");
            if (moduleJson == null) {
                return;
            }
            JsonObject settingsJson = moduleJson.getAsJsonObject("settings");
            if (settingsJson == null) {
                return;
            }
            modules.all().forEach(module -> {
                for (Setting<?> setting : module.settings()) {
                    JsonElement value = settingsJson.get(setting.id());
                    if (value != null) {
                        try {
                            setting.fromJson(value);
                        } catch (RuntimeException error) {
                            LOGGER.warn("Ignoring invalid value for setting {}", setting.id(), error);
                        }
                    }
                }
                if (!module.metadata().persistEnabled()) {
                    return;
                }
                JsonElement enabled = moduleJson.get("enabled");
                if (enabled != null && enabled.getAsBoolean()) {
                    try {
                        module.enable();
                    } catch (RuntimeException error) {
                        LOGGER.warn("Failed to enable module {} from config", module.metadata().id(), error);
                    }
                }
            });
        } catch (IOException | RuntimeException error) {
            quarantine(error);
        }
    }

    public void markDirty() {
        dirty = true;
    }

    /** Writes at most once per flush window; callers decide the cadence. */
    public void flushIfDirty() {
        if (!dirty) {
            return;
        }
        if (save()) {
            dirty = false;
        }
    }

    /**
     * Snapshots the current settings on the caller thread, then hands the JSON to a
     * single writer thread so the render thread never blocks on fsync or the move.
     * A save arriving while one is pending replaces the queued snapshot, so the
     * last save wins and the temp-file path is only ever used by one thread.
     */
    public boolean save() {
        String snapshot;
        try {
            snapshot = serialize();
        } catch (RuntimeException error) {
            LOGGER.error("Failed to serialize config", error);
            return false;
        }
        pendingSave = snapshot;
        if (!writeScheduled.compareAndSet(false, true)) {
            return true;
        }
        try {
            writer.execute(this::drainAndWrite);
            return true;
        } catch (RejectedExecutionException error) {
            writeScheduled.set(false);
            return writeFile(snapshot);
        }
    }

    /** Final synchronous save for client shutdown; waits for queued writes to drain first. */
    public boolean saveAndFlush() {
        String snapshot;
        try {
            snapshot = serialize();
        } catch (RuntimeException error) {
            LOGGER.error("Failed to serialize config", error);
            return false;
        }
        pendingSave = snapshot;
        shutdownWriter();
        return writeFile(snapshot);
    }

    private void drainAndWrite() {
        while (true) {
            String snapshot = pendingSave;
            if (snapshot != null) {
                pendingSave = null;
                writeFile(snapshot);
                continue;
            }
            writeScheduled.set(false);
            if (pendingSave == null) {
                return;
            }
        }
    }

    private void shutdownWriter() {
        writer.shutdown();
        try {
            writer.awaitTermination(SHUTDOWN_FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    private String serialize() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonObject moduleJson = new JsonObject();
        modules.all().forEach(module -> {
            moduleJson.addProperty(
                "enabled",
                module.metadata().persistEnabled() && module.state() == ModuleState.ENABLED
            );
            JsonObject settingsJson = new JsonObject();
            for (Setting<?> setting : module.settings()) {
                settingsJson.add(setting.id(), setting.toJson());
            }
            moduleJson.add("settings", settingsJson);
        });
        root.add("module", moduleJson);
        return gson.toJson(root);
    }

    private boolean writeFile(String content) {
        try {
            writeAtomically(content);
            return true;
        } catch (IOException error) {
            LOGGER.error("Failed to save config", error);
            return false;
        }
    }

    private void writeAtomically(String content) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(
                temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
        )) {
            channel.write(StandardCharsets.UTF_8.encode(content));
            channel.force(true);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void quarantine(Throwable cause) {
        LOGGER.error("Config at {} could not be loaded; quarantining it", file, cause);
        try {
            Path corrupt = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
            Files.move(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException moveError) {
            LOGGER.error("Failed to quarantine corrupt config", moveError);
        }
    }
}
