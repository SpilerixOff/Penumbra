package fr.penumbra.refuge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.penumbra.PenumbraMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistance bornée de la contamination et de ses entités visuelles.
 *
 * <p>Les entités temporaires sont suivies par UUID et par un jeton unique.
 * Leur nettoyage ne retire donc jamais une entité du joueur ou d'un autre
 * mod. Une entrée expirée dans un chunk déchargé reste enregistrée : le
 * gestionnaire attend le retour naturel du chunk au lieu de le charger.</p>
 */
public final class RefugeContaminationState extends SavedData {

    public static final int MAX_REFUGES = 256;
    public static final int MAX_TRACKED_VISUALS = 256;
    public static final String VISUAL_TAG_PREFIX = "penumbra.refuge_visual.";

    private static final Codec<RefugeContaminationModel.State> MODEL_STATE_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("stage", 0)
                            .forGetter(state -> state.stage().level()),
                    Codec.LONG.optionalFieldOf("last_event", 0L)
                            .forGetter(RefugeContaminationModel.State::lastEventTick),
                    Codec.LONG.optionalFieldOf("next_eligible", 0L)
                            .forGetter(RefugeContaminationModel.State::nextEligibleTick),
                    Codec.INT.optionalFieldOf("successful_events", 0)
                            .forGetter(RefugeContaminationModel.State::successfulEvents),
                    Codec.INT.optionalFieldOf("events_at_stage", 0)
                            .forGetter(RefugeContaminationModel.State::eventsAtStage)
            ).apply(instance, (stage, lastEvent, nextEligible, successfulEvents, eventsAtStage) ->
                    new RefugeContaminationModel.State(
                            RefugeContaminationModel.Stage.byLevel(stage),
                            lastEvent, nextEligible, successfulEvents, eventsAtStage)));

    private static final Codec<RefugeContaminationModel.Audience> AUDIENCE_CODEC =
            Codec.STRING.xmap(RefugeContaminationModel.Audience::byId,
                    RefugeContaminationModel.Audience::id);

    private static final Codec<RefugeContaminationModel.Effect> EFFECT_CODEC =
            Codec.STRING.xmap(RefugeContaminationState::effectById,
                    effect -> effect.name().toLowerCase(java.util.Locale.ROOT));

    /** Progression d'un refuge appartenant à un joueur. */
    public record RefugeEntry(UUID owner, ResourceKey<Level> dimension, BlockPos anchor,
                              RefugeContaminationModel.State state) {

        private static final Codec<RefugeEntry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        UUIDUtil.CODEC.fieldOf("owner").forGetter(RefugeEntry::owner),
                        Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(RefugeEntry::dimension),
                        BlockPos.CODEC.fieldOf("anchor").forGetter(RefugeEntry::anchor),
                        MODEL_STATE_CODEC.optionalFieldOf("state", RefugeContaminationModel.State.initial())
                                .forGetter(RefugeEntry::state)
                ).apply(instance, RefugeEntry::new));

        public RefugeEntry {
            if (owner == null || dimension == null || anchor == null) {
                throw new IllegalArgumentException("Invalid refuge contamination entry");
            }
            anchor = anchor.immutable();
            state = state == null ? RefugeContaminationModel.State.initial() : state;
        }
    }

    /** Entité visuelle temporaire, nettoyée par UUID + jeton exacts. */
    public record VisualEntry(UUID entityId, UUID owner, ResourceKey<Level> dimension,
                              BlockPos position, String token,
                              RefugeContaminationModel.Effect effect,
                              RefugeContaminationModel.Audience audience,
                              long expiresAt) {

        private static final Codec<VisualEntry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        UUIDUtil.CODEC.fieldOf("entity").forGetter(VisualEntry::entityId),
                        UUIDUtil.CODEC.fieldOf("owner").forGetter(VisualEntry::owner),
                        Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(VisualEntry::dimension),
                        BlockPos.CODEC.fieldOf("position").forGetter(VisualEntry::position),
                        Codec.STRING.fieldOf("token").forGetter(VisualEntry::token),
                        EFFECT_CODEC.fieldOf("effect").forGetter(VisualEntry::effect),
                        AUDIENCE_CODEC.optionalFieldOf("audience", RefugeContaminationModel.Audience.LOCAL_SHARED)
                                .forGetter(VisualEntry::audience),
                        Codec.LONG.fieldOf("expires_at").forGetter(VisualEntry::expiresAt)
                ).apply(instance, VisualEntry::new));

        public VisualEntry {
            if (entityId == null || owner == null || dimension == null || position == null) {
                throw new IllegalArgumentException("Invalid refuge visual entry");
            }
            position = position.immutable();
            token = sanitizeToken(token, entityId);
            effect = effect == null ? RefugeContaminationModel.Effect.WHISPER : effect;
            audience = audience == null
                    ? RefugeContaminationModel.Audience.LOCAL_SHARED : audience;
            expiresAt = Math.max(0L, expiresAt);
        }
    }

    public static final Codec<RefugeContaminationState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    RefugeEntry.CODEC.listOf().optionalFieldOf("refuges", List.of())
                            .forGetter(state -> List.copyOf(state.refuges)),
                    VisualEntry.CODEC.listOf().optionalFieldOf("visuals", List.of())
                            .forGetter(state -> List.copyOf(state.visuals))
            ).apply(instance, RefugeContaminationState::new));

    private static final SavedDataType<RefugeContaminationState> TYPE =
            new SavedDataType<>(PenumbraMod.id("refuge_contamination"),
                    RefugeContaminationState::new, CODEC, null);

    private final List<RefugeEntry> refuges;
    private final List<VisualEntry> visuals;

    public RefugeContaminationState() {
        this(List.of(), List.of());
    }

    RefugeContaminationState(List<RefugeEntry> loadedRefuges,
                              List<VisualEntry> loadedVisuals) {
        this.refuges = new ArrayList<>();
        for (RefugeEntry entry : loadedRefuges) {
            if (this.refuges.size() >= MAX_REFUGES) {
                break;
            }
            if (findIndex(entry.owner(), entry.dimension(), entry.anchor()) < 0) {
                this.refuges.add(entry);
            }
        }
        this.visuals = new ArrayList<>();
        for (VisualEntry entry : loadedVisuals) {
            if (this.visuals.size() >= MAX_TRACKED_VISUALS) {
                break;
            }
            if (this.visuals.stream().noneMatch(existing ->
                    existing.entityId().equals(entry.entityId()))) {
                this.visuals.add(entry);
            }
        }
    }

    public static RefugeContaminationState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<RefugeContaminationModel.State> find(UUID owner,
                                                         ResourceKey<Level> dimension,
                                                         BlockPos anchor) {
        int index = findIndex(owner, dimension, anchor);
        return index < 0 ? Optional.empty() : Optional.of(this.refuges.get(index).state());
    }

    /** Insère ou remplace la progression exacte d'un refuge. */
    public boolean put(UUID owner, ResourceKey<Level> dimension, BlockPos anchor,
                       RefugeContaminationModel.State state) {
        if (owner == null || dimension == null || anchor == null || state == null) {
            return false;
        }
        int index = findIndex(owner, dimension, anchor);
        RefugeEntry replacement = new RefugeEntry(owner, dimension, anchor, state);
        if (index >= 0) {
            if (this.refuges.get(index).equals(replacement)) {
                return true;
            }
            this.refuges.set(index, replacement);
            setDirty();
            return true;
        }
        if (this.refuges.size() >= MAX_REFUGES) {
            return false;
        }
        this.refuges.add(replacement);
        setDirty();
        return true;
    }

    /** Enregistre une entité seulement si le budget global et l'UUID le permettent. */
    public boolean addVisual(VisualEntry entry) {
        if (entry == null || this.visuals.size() >= MAX_TRACKED_VISUALS
                || this.visuals.stream().anyMatch(existing ->
                existing.entityId().equals(entry.entityId()))) {
            return false;
        }
        this.visuals.add(entry);
        setDirty();
        return true;
    }

    public List<VisualEntry> visuals() {
        return List.copyOf(this.visuals);
    }

    public boolean removeVisual(UUID entityId) {
        boolean removed = this.visuals.removeIf(entry -> entry.entityId().equals(entityId));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public int activeVisualCount(UUID owner) {
        int count = 0;
        for (VisualEntry entry : this.visuals) {
            if (entry.owner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    int refugeCount() {
        return this.refuges.size();
    }

    int visualCount() {
        return this.visuals.size();
    }

    private int findIndex(UUID owner, ResourceKey<Level> dimension, BlockPos anchor) {
        if (owner == null || dimension == null || anchor == null) {
            return -1;
        }
        for (int index = 0; index < this.refuges.size(); index++) {
            RefugeEntry entry = this.refuges.get(index);
            if (entry.owner().equals(owner) && entry.dimension().equals(dimension)
                    && entry.anchor().equals(anchor)) {
                return index;
            }
        }
        return -1;
    }

    public static String tokenFor(UUID entityId) {
        return VISUAL_TAG_PREFIX + entityId;
    }

    private static String sanitizeToken(String token, UUID entityId) {
        String expected = tokenFor(entityId);
        return token != null && token.equals(expected) ? token : expected;
    }

    private static RefugeContaminationModel.Effect effectById(String id) {
        if (id != null) {
            for (RefugeContaminationModel.Effect effect : RefugeContaminationModel.Effect.values()) {
                if (effect.name().equalsIgnoreCase(id)) {
                    return effect;
                }
            }
        }
        return RefugeContaminationModel.Effect.WHISPER;
    }
}
