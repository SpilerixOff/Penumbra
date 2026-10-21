package fr.penumbra.journal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.penumbra.PenumbraMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce que chaque joueur a réellement lu du Relevé (SavedData du monde, même
 * pattern Codec + {@code SavedDataType} que {@code StructureTracker}).
 *
 * <p>Deux ensembles distincts par joueur, volontairement séparés :</p>
 * <ul>
 *   <li><b>{@code seen}</b> : les créatures aperçues de ses propres yeux. Elles
 *       transforment une page délavée en page lisible. Le carnet ne raconte
 *       jamais ce que le joueur n'a pas vu — c'est la règle qui préserve le
 *       mystère du mod.</li>
 *   <li><b>{@code pages}</b> : les pages arrachées effectivement retrouvées et
 *       reglissées dans le carnet. Ce sont elles qui donnent les contre-mesures,
 *       et elles ne s'obtiennent qu'en fouillant les quatre structures.</li>
 * </ul>
 *
 * <p>La connaissance est <b>individuelle</b> : en multijoueur, un joueur qui a
 * tout trouvé ne remplit pas le carnet des autres. Céder physiquement le carnet
 * ne transmet donc rien — seule l'enquête compte.</p>
 */
public class JournalKnowledge extends SavedData {

    private static final String DATA_NAME = "penumbra_journal";
    /** Nombre maximal de joueurs suivis (éviction FIFO au-delà). */
    private static final int MAX_PLAYERS = 256;

    /** Codec : deux listes d'identifiants par joueur, tout est optionnel. */
    public static final Codec<JournalKnowledge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING.xmap(UUID::fromString, UUID::toString),
                            Codec.STRING.listOf())
                    .optionalFieldOf("seen", Map.of())
                    .forGetter(knowledge -> snapshot(knowledge.seen)),
            Codec.unboundedMap(Codec.STRING.xmap(UUID::fromString, UUID::toString),
                            Codec.STRING.listOf())
                    .optionalFieldOf("pages", Map.of())
                    .forGetter(knowledge -> snapshot(knowledge.pages))
    ).apply(instance, JournalKnowledge::new));

    private static final SavedDataType<JournalKnowledge> TYPE =
            new SavedDataType<>(PenumbraMod.id(DATA_NAME), JournalKnowledge::new, CODEC, null);

    private final Map<UUID, List<JournalEntry>> seen;
    private final Map<UUID, List<JournalEntry>> pages;

    /** Registre vierge (nouveau monde). */
    public JournalKnowledge() {
        this(Map.of(), Map.of());
    }

    /** Constructeur du Codec : les identifiants inconnus sont ignorés en silence. */
    private JournalKnowledge(Map<UUID, List<String>> seen, Map<UUID, List<String>> pages) {
        this.seen = load(seen);
        this.pages = load(pages);
    }

    private static Map<UUID, List<JournalEntry>> load(Map<UUID, List<String>> source) {
        Map<UUID, List<JournalEntry>> loaded = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<String>> player : source.entrySet()) {
            if (loaded.size() >= MAX_PLAYERS) {
                break;
            }
            List<JournalEntry> entries = new ArrayList<>();
            for (String id : player.getValue()) {
                JournalEntry entry = JournalEntry.byId(id);
                // Une page retirée par une version future ne doit pas invalider
                // la sauvegarde entière : on la laisse simplement tomber.
                if (entry != null && !entries.contains(entry)) {
                    entries.add(entry);
                }
            }
            loaded.put(player.getKey(), entries);
        }
        return loaded;
    }

    private static Map<UUID, List<String>> snapshot(Map<UUID, List<JournalEntry>> source) {
        Map<UUID, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<JournalEntry>> player : source.entrySet()) {
            List<String> ids = new ArrayList<>(player.getValue().size());
            for (JournalEntry entry : player.getValue()) {
                ids.add(entry.id());
            }
            out.put(player.getKey(), ids);
        }
        return out;
    }

    /** Récupère le registre du monde, en le créant vierge si nécessaire. */
    public static JournalKnowledge get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Note que ce joueur vient de voir cette créature. Retourne {@code true}
     * uniquement la première fois — l'appelant peut donc s'en servir pour
     * n'annoncer la découverte qu'une seule fois.
     */
    public boolean markSeen(UUID playerId, JournalEntry entry) {
        if (entry.kind() != JournalEntry.Kind.CREATURE) {
            return false;
        }
        return add(this.seen, playerId, entry);
    }

    /** Note qu'une page arrachée vient d'être reglissée dans le carnet. */
    public boolean markPageFound(UUID playerId, JournalEntry entry) {
        if (entry.kind() != JournalEntry.Kind.METHOD) {
            return false;
        }
        return add(this.pages, playerId, entry);
    }

    private boolean add(Map<UUID, List<JournalEntry>> target, UUID playerId, JournalEntry entry) {
        if (playerId == null) {
            return false;
        }
        List<JournalEntry> list = target.computeIfAbsent(playerId, id -> new ArrayList<>());
        if (list.contains(entry)) {
            return false;
        }
        list.add(entry);
        evict(target);
        setDirty();
        return true;
    }

    /** Éviction FIFO du joueur suivi le plus anciennement. */
    private static void evict(Map<UUID, List<JournalEntry>> target) {
        while (target.size() > MAX_PLAYERS) {
            UUID oldest = target.keySet().iterator().next();
            target.remove(oldest);
        }
    }

    /** État de lecture complet du carnet pour ce joueur. */
    public Map<JournalEntry, JournalEntry.State> stateFor(UUID playerId) {
        List<JournalEntry> knownCreatures = this.seen.getOrDefault(playerId, List.of());
        List<JournalEntry> foundPages = this.pages.getOrDefault(playerId, List.of());
        Map<JournalEntry, JournalEntry.State> states = new EnumMap<>(JournalEntry.class);
        for (JournalEntry entry : JournalEntry.ORDER) {
            JournalEntry.State state = switch (entry.kind()) {
                case FRONTISPIECE -> JournalEntry.State.KNOWN;
                case CREATURE -> knownCreatures.contains(entry)
                        ? JournalEntry.State.KNOWN : JournalEntry.State.FADED;
                case METHOD -> foundPages.contains(entry)
                        ? JournalEntry.State.KNOWN : JournalEntry.State.TORN;
            };
            states.put(entry, state);
        }
        return states;
    }

    /** Liste ordonnée des pages entièrement lisibles, pour le paquet d'ouverture. */
    public List<String> readableIds(UUID playerId) {
        List<String> ids = new ArrayList<>();
        for (Map.Entry<JournalEntry, JournalEntry.State> state : stateFor(playerId).entrySet()) {
            if (state.getValue() == JournalEntry.State.KNOWN) {
                ids.add(state.getKey().id());
            }
        }
        return ids;
    }

    /** Nombre de pages révélées, hors frontispice — l'avancement affiché. */
    public int progress(UUID playerId) {
        return this.seen.getOrDefault(playerId, List.of()).size()
                + this.pages.getOrDefault(playerId, List.of()).size();
    }

    /** Vrai si ce joueur possède déjà cette page arrachée. */
    public boolean hasPage(UUID playerId, JournalEntry entry) {
        return this.pages.getOrDefault(playerId, List.of()).contains(entry);
    }

    /** Efface tout ce qu'un joueur a appris (commande de diagnostic). */
    public void reset(ServerPlayer player) {
        this.seen.remove(player.getUUID());
        this.pages.remove(player.getUUID());
        setDirty();
    }
}
