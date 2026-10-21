package net.fabricmc.loader.api;
import net.fabricmc.api.EnvType;
import java.nio.file.Path;
import java.util.Optional;
public interface FabricLoader {
    static FabricLoader getInstance() { return null; }
    EnvType getEnvironmentType();
    Path getConfigDir();
    Path getGameDir();
    boolean isModLoaded(String id);
    boolean isDevelopmentEnvironment();
    Optional<ModContainer> getModContainer(String id);
}
