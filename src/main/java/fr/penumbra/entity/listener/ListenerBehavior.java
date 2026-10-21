package fr.penumbra.entity.listener;

/**
 * Les quatre postures de L'Écouteur, synchronisées vers le client pour piloter
 * l'animation. Aucune ne dépend de la position du joueur : elles ne dépendent
 * que du dernier bruit entendu.
 */
public enum ListenerBehavior {

    /** Il marche vers le dernier bruit entendu, tête orientée vers ce point. */
    SEEKING,

    /** Immobilité de statue, balayage lent de la tête : il attend un son. */
    LISTENING,

    /** Un bruit franc, tout près : il fond dessus, torse cassé en avant. */
    CLOSING,

    /** Il renonce, s'éloigne et se dissipe loin du joueur. */
    LEAVING;

    private static final ListenerBehavior[] VALUES = values();

    /** Conversion défensive depuis l'ordinal synchronisé. */
    public static ListenerBehavior byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : SEEKING;
    }
}
