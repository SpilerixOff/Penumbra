package fr.penumbra.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import fr.penumbra.PenumbraMod;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Configuration singleton de Penumbra, sérialisée en Gson dans
 * {@code config/penumbra.json}. Tout JSON invalide est renommé en
 * {@code penumbra.json.broken} puis régénéré avec les valeurs par défaut.
 */
public class PenumbraConfig {

    /** Version du schéma JSON, indépendante de la version du mod. */
    private static final int CURRENT_CONFIG_VERSION = 7;

    /** Écrite dans le JSON pour permettre les migrations futures. */
    private int configVersion = CURRENT_CONFIG_VERSION;

    public float horrorIntensity = 1.0f;        // 0.0–2.0 multiplicateur global
    public float eventFrequency = 1.0f;         // 0.0–4.0
    /** Silence initial avant la première sélection naturelle (secondes). */
    public int eventGracePeriodSeconds = 120;   // 0–900
    /** Intervalle de base entre deux événements, avant fréquence/état (secondes). */
    public int eventMinGapSeconds = 35;         // 5–300
    /** Délai maximal sans apparition 3D réussie (secondes). */
    public int manifestationMaxGapSeconds = 90; // 30–600
    public float jumpscareFrequency = 1.0f;     // 0.0–2.0
    public float jumpscareVolume = 0.8f;        // 0.0–1.0 (sécurité : jamais > 1)
    public boolean cameraShakeEnabled = true;
    public boolean distortionEnabled = true;
    public boolean flashesEnabled = false;      // OFF par défaut (photosensibilité)
    public boolean fogEnabled = true;
    public boolean chasesEnabled = true;
    public boolean worldModificationEnabled = true;
    public boolean sleepEventsEnabled = true;
    public boolean hostEnabled = true;
    /**
     * Alias historique 0.1.0. Sur un ancien fichier sans {@code storyEnabled},
     * sa valeur est migrée vers ce dernier. {@code storyEnabled} est ensuite
     * la clé canonique et les deux valeurs sont resynchronisées à l'écriture.
     */
    @Deprecated
    public boolean storyMode = true;
    public boolean multiplayerContent = true;
    public boolean debugLogging = false;

    // ——— Phase 2 (SPEC2 §1) ———
    public boolean binocularsEnabled = true;
    public boolean binocularsRequireItem = false;   // exiger l'objet Jumelles en main ou inventaire
    public boolean binocularsToggleMode = false;    // false = maintenir C, true = basculer
    public float binocularsZoomLevel = 4.0f;        // borné 2.0–6.0 dans sanitize()
    public boolean binocularsSmoothZoom = true;
    public boolean binocularsBreathingMotion = true;
    public boolean binocularsOverlayEnabled = true;
    public boolean binocularsHostReactionsEnabled = true;

    // ——— Jumelles, cœur 26.1.2 (mixin Camera) ———
    // Rétrocompatible : Gson conserve ces défauts pour toute clé absente d'un JSON 0.2.0.
    public double binocularsMinZoom = 2.0;              // borne basse du zoom molette
    public double binocularsMaxZoom = 8.0;              // borne haute du zoom molette
    public int binocularsTransitionDuration = 300;      // ms, borné 250–400 dans sanitize()
    public double binocularsBreathingStrength = 1.0;    // multiplicateur 0.0–2.0
    public boolean binocularsLensDirt = true;           // salissures de lentille (renderer)
    public boolean binocularsHideHud = false;           // masque AU-DESSUS du HUD vanilla
    public boolean binocularsHideHand = false;          // cacher la main en zoom (mixin GameRenderer)
    public boolean binocularsScrollZoom = true;         // zoom à la molette

    public boolean luresEnabled = true;
    public boolean delayedJumpscaresEnabled = true;
    public boolean binocularsDurabilityEnabled = true;
    public int postChaseCooldownTicks = 7200;       // borné 1200–24000

    // ——— Phase 3 : contenu, histoire et structures ———
    public boolean storyEnabled = true;             // progression narrative par stades (0–6)
    public boolean structuresEnabled = true;        // structures procédurales rares
    public int structureRarityChunks = 900;         // distance min. moyenne entre structures (chunks)
    public boolean nightEventsEnabled = true;       // événements nocturnes
    public boolean undergroundEventsEnabled = true; // événements souterrains
    public boolean imperfectCopiesEnabled = true;   // copies imparfaites du refuge du joueur
    /** Réservé pour compatibilité ; aucun événement ne le consomme encore. */
    @Deprecated
    public float structureEventChance = 0.5f;

    // ——— Phase 4 : multijoueur, optimisation, contenu 3D ———
    public boolean watcherEnabled = true;           // l'Observateur (personnage qui fixe le joueur)
    public boolean watcherGroupsEnabled = true;     // groupes d'Observateurs (très rare)
    public boolean badgesEnabled = true;            // badges/avancements Penumbra (« Vous me voyez »…)
    public int maxHostEntitiesPerPlayer = 2;        // plafond d'entités d'horreur proches par joueur
    public int watcherStareSeconds = 3;             // durée de regard soutenu avant réaction/badge

    // ——— Phase 5 : contenu additionnel (carte blanche) ———
    public boolean originelEnabled = true;          // l'Originel (notre version de Herobrine)
    public boolean personalEventsEnabled = true;    // événements personnels 0.6.0 (trajet rejoué, coups à la porte…)
    public boolean screamersEnabled = true;         // screamers visuels plein écran (mining_bait…)
    public boolean contrefaitEnabled = true;        // le Contrefait (copie ratée de la légende, 4e présence)
    public boolean metaMessagesEnabled = true;      // messages méta (faux « X a rejoint la partie », solo uniquement)
    public boolean originelTracesEnabled = true;    // petites constructions laissées par l'Originel
    public boolean improvedSoundsEnabled = true;    // variantes et couches audio améliorées

    // ——— 0.11.0 : Identité volée ———
    public boolean absentEnabled = true;                    // apparition naturelle de L'Absent
    public boolean absentSkinCopyEnabled = true;            // copie du skin complet du joueur imité
    public boolean absentMovementImitationEnabled = true;   // démarche humaine puis erreurs graduelles
    public boolean absentCameraCounterplayEnabled = true;   // flash : révélation et étourdissement temporaires

    // ——— 0.12.0 : Directeur adaptatif et refuges contaminés ———
    /** Arcs persistants de 3–7 minutes qui organisent présage, montée, manifestation et retombée. */
    public boolean adaptiveHorrorArcsEnabled = true;
    /** Altérations réversibles et illusions autour des refuges appris. */
    public boolean refugeContaminationEnabled = true;
    /** Séquences coordonnées entre plusieurs créatures. */
    public boolean linkedMonsterEventsEnabled = true;
    /** Screamers réservés au contexte refuge/mimique et au repli visuel du directeur. */
    public boolean contextualScreamersEnabled = true;
    /** Niveau maximal de contamination d'un refuge, 0 désactivant sa progression. */
    public int refugeContaminationMaxLevel = 4;
    /** Délai minimal entre deux contaminations du même joueur. */
    public int refugeContaminationIntervalSeconds = 300;
    /** Nombre maximal d'événements purement audio consécutifs. */
    public int maxConsecutiveAudioEvents = 2;
    /** Hallucinations serveur dont l'audience est un seul joueur. */
    public boolean individualPerceptionsEnabled = true;
    /** Faux compagnon qui copie un profil joueur sans devenir agressif. */
    public boolean falseCompanionsEnabled = true;
    /** Cooldown de référence entre deux perceptions lourdes. */
    public int perceptionIntervalSeconds = 240;
    /** Budget mondial strict de perceptions physiques privées. */
    public int perceptionMaxActive = 3;

    // ——— 0.14.0 : anti-habituation des screamers ———
    /** Mises en scène variées des visages côté client (cadrage, zoom, coupes, son). */
    public boolean screamerVarietyEnabled = true;
    /** Décote serveur des screamers récents et refus sensibilisants. */
    public boolean screamerHabituationEnabled = true;

    // ——— 0.15.0 : le Faux-Semblant ———
    /** Créatures vanilla possédées, au défaut minuscule — ou parfaites. */
    public boolean fauxCreaturesEnabled = true;
    /** Autorise le déguisement zombie (nuit en surface uniquement). */
    public boolean fauxCreatureZombieEnabled = true;

    // ——— 0.16.0 : mode streamer ———
    /**
     * Préréglage diffusion : force flashs coupés, messages méta coupés (les
     * faux « X a rejoint la partie » ressemblent à de vrais spectateurs) et
     * plafonne le volume des jumpscares à 0,7. Appliqué dans sanitize().
     */
    public boolean streamerMode = false;

    // ——— 0.17.0 : L'Écouteur ———
    /** La créature aveugle qui ne traque que le bruit. */
    public boolean listenerEnabled = true;
    /** Autorise ses dégâts au contact (le screamer reste actif si faux). */
    public boolean listenerCanHarm = true;

    // ——— 0.17.0 : couche de perception client ———
    /** Grain et lignes de balayage dosés par la tension (client uniquement). */
    public boolean perceptionGrainEnabled = true;
    /**
     * Silhouette rémanente périphérique : deux à quatre images d'une présence
     * réellement proche mais hors champ. Séparée du grain pour permettre le
     * mode mouvement réduit.
     */
    public boolean peripheralAfterimagesEnabled = true;
    /** Force globale de la couche de perception, 0 la coupe entièrement. */
    public float perceptionIntensity = 1.0f;

    // ——— 0.19.0 : objets trouvés ———
    /**
     * Le Directeur pose du mobilier ordinaire — une chaise, une porte isolée,
     * un miroir — sur les chemins du joueur, hors de son champ de vision.
     *
     * <p>Aucun de ces objets n'est effrayant en lui-même ; c'est l'endroit qui
     * travaille. Ils sont sauvegardés avec le monde et restent en place.</p>
     */
    public boolean foundObjectsEnabled = true;

    // ——— 0.19.0 : éclairage dynamique ———
    /**
     * La torche, la lanterne ou la pierre lumineuse tenue en main éclairent
     * réellement le décor, comme les objets tombés au sol.
     *
     * <p>Purement client : rien n'est envoyé au serveur, aucun bloc n'est posé.
     * Le coût est la reconstruction de la géométrie des sections traversées —
     * <b>c'est la seule option du mod qui puisse peser sur les images par
     * seconde</b>, d'où la possibilité de la couper. Sur une machine juste,
     * couper ceci avant tout le reste.</p>
     */
    public boolean dynamicLightsEnabled = true;
    /**
     * La flamme faiblit et bat quand la tension monte, jusqu'à manquer de
     * s'éteindre. C'est ce qui distingue cet éclairage d'un mod générique : le
     * joueur voit son rayon de sécurité se rétracter sans qu'on le lui dise.
     * Sans effet si {@link #dynamicLightsEnabled} est coupé.
     */
    public boolean dynamicLightFlicker = true;

    // ——— 0.18.0 : le Relevé ———
    /** Le carnet de l'enquêteur, ses pages arrachées et son remplissage. */
    public boolean journalEnabled = true;
    /**
     * Le Bûcher et sa colonne de fumée. Le couper laisse le carnet en jeu (il
     * reste obtenable en créatif ou par commande) mais retire la quête.
     */
    public boolean signalPyreEnabled = true;
    /**
     * Rend le Relevé avec la police manuscrite {@code penumbra:ink} au lieu de
     * la police vanilla en italique. <b>Coupée par défaut</b> : le mod ne
     * livre aucun fichier TTF (licences), il faut d'abord déposer sa propre
     * police et sa définition — voir {@code docs/PROMPTS_RELEVE.md}. Activer
     * cette option sans avoir posé les deux fichiers rendrait le carnet
     * illisible, jamais instable.
     */
    public boolean journalHandwrittenFont = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("penumbra.json");
    private static final Path BROKEN_FILE = FILE.resolveSibling("penumbra.json.broken");
    private static final Path TEMP_FILE = FILE.resolveSibling("penumbra.json.tmp");

    private static PenumbraConfig instance;
    /** Clés ajoutées par un outil ou une version future, préservées à la réécriture. */
    private static JsonObject preservedUnknownFields = new JsonObject();

    /** Retourne l'instance active ; charge depuis le disque si nécessaire. */
    public static synchronized PenumbraConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    /**
     * Charge la configuration. Crée le fichier avec les défauts s'il est absent ;
     * s'il est illisible ou invalide, le renomme en {@code .broken} puis régénère
     * les défauts.
     */
    public static synchronized void load() {
        if (Files.isRegularFile(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) {
                    throw new JsonParseException("penumbra.json doit contenir un objet JSON");
                }

                JsonObject original = parsed.getAsJsonObject();
                JsonObject normalized = original.deepCopy();
                JsonObject defaults = GSON.toJsonTree(new PenumbraConfig()).getAsJsonObject();
                int sourceVersion = readConfigVersion(original);
                boolean futureVersion = sourceVersion > CURRENT_CONFIG_VERSION;
                boolean migrated = false;
                int missingKeys = 0;

                // Compatibilité 0.1–0.4 : storyMode était la seule clé exposée.
                if (!normalized.has("storyEnabled") && normalized.has("storyMode")) {
                    normalized.add("storyEnabled", normalized.get("storyMode").deepCopy());
                    migrated = true;
                }

                // Complète toutes les options ajoutées depuis la création du fichier.
                for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
                    if (!normalized.has(entry.getKey())) {
                        normalized.add(entry.getKey(), entry.getValue().deepCopy());
                        missingKeys++;
                    }
                }
                normalized.addProperty("configVersion", Math.max(sourceVersion, CURRENT_CONFIG_VERSION));
                preservedUnknownFields = collectUnknownFields(original, defaults);

                PenumbraConfig loaded = GSON.fromJson(normalized, PenumbraConfig.class);
                if (loaded == null) {
                    throw new JsonParseException("penumbra.json ne contient aucun objet");
                }
                JsonObject beforeSanitize = GSON.toJsonTree(loaded).getAsJsonObject();
                loaded.sanitize();
                loaded.storyMode = loaded.storyEnabled;
                JsonObject afterSanitize = GSON.toJsonTree(loaded).getAsJsonObject();
                boolean sanitized = !beforeSanitize.equals(afterSanitize);
                instance = loaded;

                if (futureVersion) {
                    PenumbraMod.LOGGER.warn("penumbra.json utilise le schéma futur v{} (supporté : v{}). "
                            + "Les clés inconnues sont conservées et le fichier n'est pas réécrit.",
                            sourceVersion, CURRENT_CONFIG_VERSION);
                } else if (sourceVersion < CURRENT_CONFIG_VERSION || missingKeys > 0 || migrated || sanitized) {
                    backupBeforeMigration(sourceVersion);
                    save();
                    PenumbraMod.LOGGER.info("Configuration Penumbra migrée vers le schéma v{} "
                            + "({} clé(s) ajoutée(s)).", CURRENT_CONFIG_VERSION, missingKeys);
                }
                return;
            } catch (IOException | JsonParseException | IllegalStateException e) {
                PenumbraMod.LOGGER.warn("penumbra.json illisible ou invalide ({}). "
                        + "Sauvegarde en penumbra.json.broken puis retour aux défauts.", e.toString());
                backupBrokenFile();
            }
        }
        preservedUnknownFields = new JsonObject();
        instance = new PenumbraConfig();
        save();
    }

    /** Écrit la configuration courante sur disque (crée le dossier config au besoin). */
    public static synchronized void save() {
        if (instance == null) {
            instance = new PenumbraConfig();
        }
        instance.configVersion = Math.max(instance.configVersion, CURRENT_CONFIG_VERSION);
        instance.sanitize();
        instance.storyMode = instance.storyEnabled;
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject output = GSON.toJsonTree(instance).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : preservedUnknownFields.entrySet()) {
                if (!output.has(entry.getKey())) {
                    output.add(entry.getKey(), entry.getValue().deepCopy());
                }
            }
            try (Writer writer = Files.newBufferedWriter(TEMP_FILE, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                GSON.toJson(output, writer);
            }
            moveAtomically(TEMP_FILE, FILE);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(TEMP_FILE);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            PenumbraMod.LOGGER.error("Impossible d'écrire penumbra.json", e);
        }
    }

    /** Lit la version de schéma ; l'absence correspond aux fichiers historiques v0. */
    private static int readConfigVersion(JsonObject root) {
        if (!root.has("configVersion")) {
            return 0;
        }
        try {
            return Math.max(0, root.get("configVersion").getAsInt());
        } catch (RuntimeException e) {
            throw new JsonParseException("configVersion doit être un entier positif", e);
        }
    }

    /** Conserve les extensions inconnues afin qu'une migration ne les efface pas. */
    private static JsonObject collectUnknownFields(JsonObject root, JsonObject defaults) {
        JsonObject unknown = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!defaults.has(entry.getKey())) {
                unknown.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        return unknown;
    }

    /** Sauvegarde le fichier d'origine avant sa première migration de schéma. */
    private static void backupBeforeMigration(int sourceVersion) {
        Path backup = FILE.resolveSibling("penumbra.json.pre-v" + CURRENT_CONFIG_VERSION + ".bak");
        try {
            Files.copy(FILE, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            PenumbraMod.LOGGER.warn("Impossible de sauvegarder penumbra.json v{} avant migration",
                    sourceVersion, e);
        }
    }

    /**
     * Remplace le fichier d'un seul coup ; repli portable dès que le déplacement
     * atomique échoue. Sous Windows, remplacer une cible existante peut lever
     * {@link java.nio.file.AccessDeniedException} au lieu de
     * {@link AtomicMoveNotSupportedException}, même sur le même volume.
     */
    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailure) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallbackFailure) {
                fallbackFailure.addSuppressed(atomicFailure);
                throw fallbackFailure;
            }
        }
    }

    /** Renomme le fichier courant en penumbra.json.broken (écrasé s'il existait déjà). */
    private static void backupBrokenFile() {
        try {
            Path target = Files.exists(BROKEN_FILE)
                    ? FILE.resolveSibling("penumbra.json.broken." + System.currentTimeMillis())
                    : BROKEN_FILE;
            Files.move(FILE, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            PenumbraMod.LOGGER.warn("Impossible de renommer penumbra.json en penumbra.json.broken", e);
        }
    }

    /** Borne les valeurs numériques aux plages documentées du SPEC §4.3 et SPEC2 §1. */
    private void sanitize() {
        horrorIntensity = clamp(horrorIntensity, 0.0f, 2.0f, 1.0f);
        eventFrequency = clamp(eventFrequency, 0.0f, 4.0f, 1.0f);
        eventGracePeriodSeconds = clamp(eventGracePeriodSeconds, 0, 900);
        eventMinGapSeconds = clamp(eventMinGapSeconds, 5, 300);
        manifestationMaxGapSeconds = clamp(manifestationMaxGapSeconds, 30, 600);
        jumpscareFrequency = clamp(jumpscareFrequency, 0.0f, 2.0f, 1.0f);
        jumpscareVolume = clamp(jumpscareVolume, 0.0f, 1.0f, 0.8f); // sécurité : jamais > 1
        // Phase 2 (SPEC2 §1)
        binocularsZoomLevel = clamp(binocularsZoomLevel, 2.0f, 6.0f, 4.0f);
        // Jumelles 26.1.2
        binocularsTransitionDuration = clamp(binocularsTransitionDuration, 250, 400);
        binocularsMinZoom = clamp(binocularsMinZoom, 1.5, 10.0, 2.0);
        binocularsMaxZoom = clamp(binocularsMaxZoom, 2.0, 16.0, 8.0);
        if (binocularsMinZoom > binocularsMaxZoom) {
            binocularsMinZoom = 2.0;
            binocularsMaxZoom = 8.0;
        }
        binocularsBreathingStrength = clamp(binocularsBreathingStrength, 0.0, 2.0, 1.0);
        postChaseCooldownTicks = clamp(postChaseCooldownTicks, 1200, 24000);
        // Phase 4
        maxHostEntitiesPerPlayer = clamp(maxHostEntitiesPerPlayer, 1, 4);
        watcherStareSeconds = clamp(watcherStareSeconds, 1, 10);
        // Phase 3
        structureRarityChunks = clamp(structureRarityChunks, 200, 5000);
        structureEventChance = clamp(structureEventChance, 0.0f, 1.0f, 0.5f);
        // 0.12.0
        refugeContaminationMaxLevel = clamp(refugeContaminationMaxLevel, 0, 4);
        refugeContaminationIntervalSeconds = clamp(refugeContaminationIntervalSeconds, 120, 1800);
        maxConsecutiveAudioEvents = clamp(maxConsecutiveAudioEvents, 1, 4);
        // 0.13.0
        perceptionIntervalSeconds = clamp(perceptionIntervalSeconds, 60, 1800);
        perceptionMaxActive = clamp(perceptionMaxActive, 1, 8);
        // 0.17.0
        perceptionIntensity = clamp(perceptionIntensity, 0.0f, 2.0f, 1.0f);
        // 0.16.0 : le mode streamer prime sur les réglages individuels.
        if (streamerMode) {
            flashesEnabled = false;
            metaMessagesEnabled = false;
            jumpscareVolume = Math.min(jumpscareVolume, 0.7f);
        }
    }

    /**
     * Applique un profil d'accessibilité (0.17.0, feuille de route « après
     * 0.13.0 »). Les trois profils ne touchent qu'un sous-ensemble volontairement
     * restreint d'options — intensité, fréquences, brusquerie visuelle et
     * créatures les plus agressives — et laissent intacts les cinquante autres
     * réglages fins ainsi que les préférences déjà posées par le joueur sur les
     * options qu'ils ne citent pas.
     *
     * @param profile {@code gentle}, {@code standard} ou {@code nightmare}
     * @return {@code false} si le nom du profil est inconnu
     */
    public boolean applyProfile(String profile) {
        switch (profile == null ? "" : profile.toLowerCase(java.util.Locale.ROOT)) {
            case "gentle", "doux" -> {
                horrorIntensity = 0.55f;
                eventFrequency = 0.6f;
                eventGracePeriodSeconds = 420;
                eventMinGapSeconds = 75;
                jumpscareFrequency = 0.35f;
                jumpscareVolume = 0.5f;
                screamersEnabled = false;
                flashesEnabled = false;
                cameraShakeEnabled = false;
                distortionEnabled = false;
                perceptionGrainEnabled = false;
                peripheralAfterimagesEnabled = false;
                perceptionIntensity = 0.4f;
                listenerCanHarm = false;
                chasesEnabled = false;
            }
            case "standard", "normal" -> {
                horrorIntensity = 1.0f;
                eventFrequency = 1.0f;
                eventGracePeriodSeconds = 120;
                eventMinGapSeconds = 35;
                jumpscareFrequency = 1.0f;
                jumpscareVolume = 0.8f;
                screamersEnabled = true;
                cameraShakeEnabled = true;
                distortionEnabled = true;
                perceptionGrainEnabled = true;
                peripheralAfterimagesEnabled = true;
                perceptionIntensity = 1.0f;
                listenerEnabled = true;
                listenerCanHarm = true;
                chasesEnabled = true;
            }
            case "nightmare", "cauchemar" -> {
                horrorIntensity = 1.8f;
                eventFrequency = 2.2f;
                eventGracePeriodSeconds = 30;
                eventMinGapSeconds = 15;
                jumpscareFrequency = 1.6f;
                jumpscareVolume = 1.0f;
                screamersEnabled = true;
                cameraShakeEnabled = true;
                distortionEnabled = true;
                perceptionGrainEnabled = true;
                peripheralAfterimagesEnabled = true;
                perceptionIntensity = 1.6f;
                listenerEnabled = true;
                listenerCanHarm = true;
                chasesEnabled = true;
            }
            default -> {
                return false;
            }
        }
        // Le profil ne contourne jamais les bornes ni le mode streamer :
        // flashesEnabled reste coupé et le volume plafonné si celui-ci est actif.
        sanitize();
        return true;
    }

    private static float clamp(float value, float min, float max, float defaultValue) {
        if (Float.isNaN(value)) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** Bornage flottant double précision (zoom molette, intensité de respiration). */
    private static double clamp(double value, double min, double max, double defaultValue) {
        if (Double.isNaN(value)) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** Bornage entier simple (pas de NaN possible côté int). */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
