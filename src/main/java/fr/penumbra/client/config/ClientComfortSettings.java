package fr.penumbra.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import fr.penumbra.PenumbraMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Local restrictions only; never writes the shared gameplay configuration. */
public final class ClientComfortSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientComfortSettings instance;
    private static String loadError;

    public boolean reduceMotion = false;
    public boolean hideFaces = false;
    public boolean blockFlashes = false;
    public boolean grainEnabled = true;
    public boolean afterimagesEnabled = true;
    public boolean lightFlickerEnabled = true;
    public float suddenSoundVolume = 1.0f;
    public float ambientSoundVolume = 1.0f;

    public static synchronized ClientComfortSettings get() {
        if (instance == null) {
            try {
                instance = read(configPath());
            } catch (IOException exception) {
                instance = new ClientComfortSettings();
                loadError = exception.getMessage();
                PenumbraMod.LOGGER.warn("Cannot read penumbra-client.json; using local defaults", exception);
            }
        }
        return instance;
    }

    /** The original unreadable file is preserved until the player explicitly applies settings. */
    public static synchronized String loadError() {
        get();
        return loadError;
    }

    /** Publish only after persistence succeeds, so a failed Apply leaves live effects unchanged. */
    public static synchronized void saveAndApply(ClientComfortSettings edited) throws IOException {
        ClientComfortSettings saved = edited.sanitizedCopy();
        write(configPath(), saved);
        instance = saved;
        loadError = null;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("penumbra-client.json");
    }

    public ClientComfortSettings copy() {
        ClientComfortSettings copy = new ClientComfortSettings();
        copy.reduceMotion = reduceMotion;
        copy.hideFaces = hideFaces;
        copy.blockFlashes = blockFlashes;
        copy.grainEnabled = grainEnabled;
        copy.afterimagesEnabled = afterimagesEnabled;
        copy.lightFlickerEnabled = lightFlickerEnabled;
        copy.suddenSoundVolume = suddenSoundVolume;
        copy.ambientSoundVolume = ambientSoundVolume;
        return copy;
    }

    private ClientComfortSettings sanitizedCopy() {
        ClientComfortSettings copy = copy();
        copy.suddenSoundVolume = volume(copy.suddenSoundVolume);
        copy.ambientSoundVolume = volume(copy.ambientSoundVolume);
        return copy;
    }

    private static float volume(float value) {
        return Float.isFinite(value) ? Math.clamp(value, 0.0f, 1.0f) : 1.0f;
    }

    // Path-based methods keep storage tests independent of a running Fabric client.
    static ClientComfortSettings read(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new ClientComfortSettings();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new JsonParseException("Expected a JSON object");
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonObject defaults = GSON.toJsonTree(new ClientComfortSettings()).getAsJsonObject();
            for (String key : defaults.keySet()) {
                if (!root.has(key)) {
                    continue;
                }
                JsonElement value = root.get(key);
                boolean number = defaults.get(key).getAsJsonPrimitive().isNumber();
                if (!value.isJsonPrimitive()
                        || (number ? !value.getAsJsonPrimitive().isNumber()
                                   : !value.getAsJsonPrimitive().isBoolean())) {
                    throw new JsonParseException("Invalid value for " + key);
                }
            }
            return GSON.fromJson(root, ClientComfortSettings.class).sanitizedCopy();
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IOException("Invalid penumbra-client.json: " + exception.getMessage(), exception);
        }
    }

    static void write(Path file, ClientComfortSettings settings) throws IOException {
        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "penumbra-client-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(settings.sanitizedCopy()) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailure) {
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException fallbackFailure) {
                    fallbackFailure.addSuppressed(atomicFailure);
                    throw fallbackFailure;
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
