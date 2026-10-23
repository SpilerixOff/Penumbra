package fr.penumbra.journal;

import fr.penumbra.structure.StructureType;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Les quinze pages du Relevé (0.18.0) — le carnet de terrain d'un enquêteur mort.
 *
 * <p>Trois familles :</p>
 * <ul>
 *   <li><b>Frontispice</b> : toujours lisible. C'est la seule page que l'ancien
 *       propriétaire ait écrite en sachant qu'elle serait lue.</li>
 *   <li><b>Créatures</b> : présentes dans le carnet mais <em>délavées</em> tant
 *       que le joueur n'a pas vu la créature de ses yeux. Il ne reste alors que
 *       le titre et une seule ligne — assez pour savoir qu'il existe quelque
 *       chose, pas assez pour savoir quoi.</li>
 *   <li><b>Méthodes</b> : quatre pages physiquement <em>arrachées</em> du carnet.
 *       Il n'en reste qu'un talon déchiré. Chacune se trouve dans le coffre
 *       d'une des quatre structures de Penumbra, et c'est là que se joue la
 *       vraie enquête : ce sont elles qui donnent les contre-mesures.</li>
 * </ul>
 *
 * <p>Les clés de traduction sont dérivées de l'identifiant :
 * {@code journal.penumbra.<id>.title}, {@code .subtitle}, {@code .faded} et
 * {@code .line1} … {@code .lineN}. Le nombre de lignes est déclaré ici pour que
 * l'écran n'ait jamais à sonder l'existence d'une clé.</p>
 */
public enum JournalEntry {

    /** Page de garde : un nom, une date, et pourquoi ce carnet est là. */
    FRONTISPIECE("frontispiece", Kind.FRONTISPIECE, 0, 6, null),

    // ——— Créatures, dans l'ordre où l'ancien les a rencontrées ———
    HOST("host", Kind.CREATURE, 1, 5, null),
    WATCHER("watcher", Kind.CREATURE, 2, 4, null),
    SCUTTLER("scuttler", Kind.CREATURE, 3, 4, null),
    CONTREFAIT("contrefait", Kind.CREATURE, 4, 4, null),
    RAMPANT("rampant", Kind.CREATURE, 5, 5, null),
    PROJECTIONIST("projectionist", Kind.CREATURE, 6, 5, null),
    FAUX_CREATURE("faux_creature", Kind.CREATURE, 7, 5, null),
    ABSENT("absent", Kind.CREATURE, 8, 6, null),
    LISTENER("listener", Kind.CREATURE, 9, 6, null),
    ORIGINEL("originel", Kind.CREATURE, 10, 5, null),

    // ——— Pages arrachées, une par structure ———
    METHOD_CAMERA("method_camera", Kind.METHOD, 11, 8, StructureType.OBSERVATION_POST),
    METHOD_REFUGE("method_refuge", Kind.METHOD, 12, 8, StructureType.SHIFTED_HOUSE),
    METHOD_STILLNESS("method_stillness", Kind.METHOD, 13, 8, StructureType.ASH_SANCTUM),
    LAST_PAGE("last_page", Kind.METHOD, 14, 8, StructureType.PENUMBRA_WELL);

    /** Famille d'une page : elle décide de son état par défaut. */
    public enum Kind {
        /** Toujours entièrement lisible. */
        FRONTISPIECE,
        /** Délavée jusqu'à ce que la créature soit vue. */
        CREATURE,
        /** Arrachée : absente du carnet jusqu'à ce que la page soit retrouvée. */
        METHOD
    }

    /** État de lecture d'une page pour un joueur donné. */
    public enum State {
        /** Page arrachée : il n'en reste qu'un talon. */
        TORN,
        /** Page présente mais délavée : titre et une ligne. */
        FADED,
        /** Page complète. */
        KNOWN
    }

    private static final JournalEntry[] VALUES = values();
    /** Ordre de feuilletage, garanti stable et sans trou. */
    public static final List<JournalEntry> ORDER = List.of(VALUES);

    private final String id;
    private final Kind kind;
    private final int portraitIndex;
    private final int lineCount;
    @Nullable
    private final StructureType heldBy;

    JournalEntry(String id, Kind kind, int portraitIndex, int lineCount,
                 @Nullable StructureType heldBy) {
        this.id = id;
        this.kind = kind;
        this.portraitIndex = portraitIndex;
        this.lineCount = lineCount;
        this.heldBy = heldBy;
    }

    /** Identifiant court, persisté et utilisé dans les commandes. */
    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    /** Case de l'atlas {@code textures/gui/journal_portraits.png} (grille 4×4). */
    public int portraitIndex() {
        return portraitIndex;
    }

    /** Nombre de lignes {@code .lineN} déclarées pour cette page. */
    public int lineCount() {
        return lineCount;
    }

    /** Structure dont le coffre contient cette page arrachée, le cas échéant. */
    public Optional<StructureType> heldBy() {
        return Optional.ofNullable(heldBy);
    }

    public String titleKey() {
        return "journal.penumbra." + id + ".title";
    }

    public String subtitleKey() {
        return "journal.penumbra." + id + ".subtitle";
    }

    /** L'unique ligne encore lisible d'une page délavée. */
    public String fadedKey() {
        return "journal.penumbra." + id + ".faded";
    }

    public String lineKey(int index) {
        return "journal.penumbra." + id + ".line" + (index + 1);
    }

    /**
     * Indice griffonné sur la page d'en face, et qui a donc survécu à
     * l'arrachage : une devinette sur l'endroit où la page se trouve, jamais des
     * coordonnées. Seules les pages {@link Kind#METHOD} en déclarent un.
     */
    public String hintKey() {
        return "journal.penumbra." + id + ".hint";
    }

    /**
     * État par défaut d'une page pour un joueur qui n'a rien découvert : le
     * frontispice est lisible, les créatures sont délavées, les méthodes sont
     * arrachées.
     */
    public State defaultState() {
        return switch (kind) {
            case FRONTISPIECE -> State.KNOWN;
            case CREATURE -> State.FADED;
            case METHOD -> State.TORN;
        };
    }

    /** Recherche défensive par identifiant (persistance, commandes, paquets). */
    @Nullable
    public static JournalEntry byId(String id) {
        for (JournalEntry entry : VALUES) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }

    /** Page arrachée logée dans cette structure, s'il y en a une. */
    public static Optional<JournalEntry> pageFor(StructureType type) {
        for (JournalEntry entry : VALUES) {
            if (entry.heldBy == type) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** Nombre de pages qu'un joueur peut réellement révéler (hors frontispice). */
    public static int discoverableCount() {
        int total = 0;
        for (JournalEntry entry : VALUES) {
            if (entry.kind != Kind.FRONTISPIECE) {
                total++;
            }
        }
        return total;
    }
}
