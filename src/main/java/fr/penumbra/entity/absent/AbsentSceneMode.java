package fr.penumbra.entity.absent;

/** Variante serveur synchronisée de L'Absent. */
public enum AbsentSceneMode {
    NORMAL,
    FALSE_COMPANION;

    public static AbsentSceneMode byId(int id) {
        return id == FALSE_COMPANION.ordinal() ? FALSE_COMPANION : NORMAL;
    }
}
