package fr.penumbra.config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PenumbraConfigTest {

    @BeforeAll
    static void provideFabricLoaderWithAUnitTestGameDirectory() throws Exception {
        Path gameDirectory = Path.of("build", "unit-test-game").toAbsolutePath();
        Files.createDirectories(gameDirectory);
        Class<?> loaderType = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl");
        Field instanceField = loaderType.getDeclaredField("INSTANCE");
        Object loader = instanceField.get(null);
        Method setGameDirectory = loaderType.getDeclaredMethod("setGameDir", Path.class);
        setGameDirectory.setAccessible(true);
        setGameDirectory.invoke(loader, gameDirectory);
    }

    /**
     * Les perceptions individuelles sont arrivées avec le schéma 5 et doivent
     * rester actives par défaut, avec les mêmes valeurs prudentes.
     *
     * <p>Ce test épinglait {@code CURRENT_CONFIG_VERSION == 5}. Le schéma est
     * passé à 7 depuis, et l'égalité stricte n'a plus rien vérifié d'utile :
     * elle a simplement transformé chaque montée de schéma en échec de build.
     * Le sujet réel du test, ce sont les <b>valeurs par défaut</b> ; la version
     * n'est qu'un plancher — ces réglages ne doivent pas disparaître d'un
     * schéma ultérieur, ni réapparaître sur un plus ancien.</p>
     */
    @Test
    void individualPerceptionsStayOnByDefaultSinceSchemaFive()
            throws ReflectiveOperationException {
        PenumbraConfig config = new PenumbraConfig();

        Field schema = PenumbraConfig.class.getDeclaredField("CURRENT_CONFIG_VERSION");
        schema.setAccessible(true);
        int version = schema.getInt(null);
        assertTrue(version >= 5,
                "les perceptions individuelles datent du schéma 5, or le schéma courant est "
                        + version);
        assertTrue(config.individualPerceptionsEnabled);
        assertTrue(config.falseCompanionsEnabled);
        assertEquals(240, config.perceptionIntervalSeconds);
        assertEquals(3, config.perceptionMaxActive);
    }

    @Test
    void perceptionSettingsAreSanitizedToDocumentedServerBounds()
            throws ReflectiveOperationException {
        PenumbraConfig config = new PenumbraConfig();
        Method sanitize = PenumbraConfig.class.getDeclaredMethod("sanitize");
        sanitize.setAccessible(true);

        config.perceptionIntervalSeconds = Integer.MIN_VALUE;
        config.perceptionMaxActive = Integer.MAX_VALUE;
        sanitize.invoke(config);
        assertEquals(60, config.perceptionIntervalSeconds);
        assertEquals(8, config.perceptionMaxActive);

        config.perceptionIntervalSeconds = Integer.MAX_VALUE;
        config.perceptionMaxActive = Integer.MIN_VALUE;
        sanitize.invoke(config);
        assertEquals(1_800, config.perceptionIntervalSeconds);
        assertEquals(1, config.perceptionMaxActive);
    }
}
