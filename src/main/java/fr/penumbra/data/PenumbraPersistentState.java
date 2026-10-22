package fr.penumbra.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.penumbra.PenumbraMod;
import fr.penumbra.director.PlayerHorrorProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * État persistant de Penumbra, sauvegardé dans les données du monde (overworld) :
 * profils d'horreur des joueurs ({@code Map<UUID, PlayerHorrorProfile>}),
 * stade du mode histoire ({@code storyStage}) et miroir de la graine du monde
 * ({@code worldSeedMirror}, pour retrouver le contexte de génération).
 */
public class PenumbraPersistentState extends SavedData {

    /** Nom du fichier de données dans le répertoire du monde. */
    private static final String DATA_NAME = "penumbra";

    /** Codec complet de l'état (clés UUID sérialisées en chaînes). */
    public static final Codec<PenumbraPersistentState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING.xmap(UUID::fromString, UUID::toString), PlayerHorrorProfile.CODEC)
                    .fieldOf("profiles").forGetter(state -> state.profiles),
            Codec.INT.fieldOf("storyStage").forGetter(PenumbraPersistentState::storyStage),
            Codec.LONG.fieldOf("worldSeedMirror").forGetter(state -> state.worldSeedMirror),
            // 0.6.0 : one-shots consommés (bed_shift, animal_gaze, fake_join) —
            // une seule fois par monde, à jamais. Optionnel : rétro-compatible.
            Codec.STRING.listOf().optionalFieldOf("oneShots", List.of())
                    .forGetter(state -> List.copyOf(state.oneShots))
    ).apply(instance, PenumbraPersistentState::new));

    // Persistance 26.1 (vérifié dans les sources 26.1.2) : SavedDataType est un record
    // top-level (Identifier, Supplier, Codec, DataFixTypes) ; DataFixTypes peut être null
    // pour un mod. La (dé)sérialisation est entièrement pilotée par le codec : SavedData
    // n'expose plus de méthode save(...) à surcharger.
    private static final SavedDataType<PenumbraPersistentState> TYPE =
            new SavedDataType<>(PenumbraMod.id(DATA_NAME), PenumbraPersistentState::new, CODEC, null);

    private final Map<UUID, PlayerHorrorProfile> profiles;
    private int storyStage;
    private long worldSeedMirror;
    /** 0.6.0 : ids des one-shots déjà consommés dans ce monde. */
    private final java.util.Set<String> oneShots;

    /** État vierge (nouveau monde). */
    public PenumbraPersistentState() {
        this(new HashMap<>(), 0, 0L, List.of());
    }

    /** Constructeur utilisé par le Codec. */
    private PenumbraPersistentState(Map<UUID, PlayerHorrorProfile> profiles, int storyStage,
                                    long worldSeedMirror, List<String> oneShots) {
        this.profiles = new HashMap<>(profiles);
        this.storyStage = storyStage;
        this.worldSeedMirror = worldSeedMirror;
        this.oneShots = new java.util.HashSet<>();
        for (String id : oneShots) {
            // These server-owned flags are permanent, including personal victories
            // and onboarding. A global cap silently forgets older players on reload.
            if (validOneShotId(id)) {
                this.oneShots.add(id);
            }
        }
    }

    /** 0.6.0 : vrai si le one-shot a déjà eu lieu dans ce monde. */
    public boolean isOneShotUsed(String id) {
        return this.oneShots.contains(id);
    }

    /** 0.6.0 : consomme un one-shot (persisté immédiatement). */
    public void markOneShot(String id) {
        if (validOneShotId(id) && this.oneShots.add(id)) {
            setDirty();
        }
    }

    private static boolean validOneShotId(String id) {
        return id != null && !id.isBlank() && id.length() <= 96;
    }

    /**
     * Repairs victories lost by the old 256-flag load cap. Only the personal
     * victory counter is evidence: stage 6 and old advancement badges are not.
     * The counter is written by the completed duel, independently of this set.
     */
    public boolean hasConfrontationVictory(UUID playerId, int recordedWins) {
        String key = "confrontation_done:" + playerId;
        if (recordedWins > 0) markOneShot(key);
        return isOneShotUsed(key);
    }

    /**
     * 0.19.0 : rend un one-shot à nouveau disponible. Réservé aux commandes de
     * diagnostic — un one-shot consommé en jeu n'est jamais rendu tout seul.
     */
    public void clearOneShot(String id) {
        if (this.oneShots.remove(id)) {
            setDirty();
        }
    }

    /** Récupère l'état persistant du monde, en le créant vierge si nécessaire. */
    public static PenumbraPersistentState get(MinecraftServer server) {
        // 26.1 : SavedDataStorage.computeIfAbsent(SavedDataType) — un seul argument.
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Profil d'horreur d'un joueur ; créé vierge si absent. */
    public PlayerHorrorProfile profile(UUID id) {
        PlayerHorrorProfile profile = this.profiles.get(id);
        if (profile == null) {
            profile = new PlayerHorrorProfile();
            this.profiles.put(id, profile);
            setDirty();
        }
        return profile;
    }

    /**
     * Remplace le profil d'un joueur par un profil vierge
     * (helper additif utilisé par HorrorDirector.reset / /penumbra reset).
     */
    public void resetProfile(UUID id) {
        this.profiles.put(id, new PlayerHorrorProfile());
        setDirty();
    }

    /** Stade actuel du mode histoire. */
    public int storyStage() {
        return this.storyStage;
    }

    /**
     * Fait avancer le stade de l'histoire (monotone : seule une valeur
     * supérieure est retenue) ; la valeur 0 réinitialise le mode histoire
     * (utilisé par /penumbra reset).
     */
    public void advanceStoryStage(int stage) {
        if (stage == 0 || stage > this.storyStage) {
            this.storyStage = stage;
            setDirty();
        }
    }
}
