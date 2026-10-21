package fr.penumbra.event.watcher;

import fr.penumbra.event.HorrorEvent;
import java.util.List;

/**
 * Provider des événements de l'Observateur (Phase 4, contrat Agent_Watcher).
 * <p>
 * Les 2 événements sont des singletons sans état, instanciés une seule fois (aucune
 * allocation par tick). Intégration attendue (hors de ce fichier) : l'intégrateur
 * ajoute {@link #create()} au pool d'événements du {@code HorrorDirector} et branche
 * la commande de déclenchement sur {@link #byId(String)}. Ordre contractuel des ids :
 * watcher_stare, watcher_group.
 * </p>
 */
public final class WatcherEvents {

    /** Liste immuable des événements de l'Observateur (singletons partagés). */
    private static final List<HorrorEvent> ALL = List.of(
            new WatcherStareEvent(),
            new WatcherGroupEvent()
    );

    private WatcherEvents() {
    }

    /**
     * Retourne la liste immuable des événements de l'Observateur (instances partagées,
     * sans état) à ajouter au pool du directeur.
     */
    public static List<HorrorEvent> create() {
        return ALL;
    }

    /**
     * Retourne l'événement portant cet id, ou {@code null} si inconnu
     * (même convention que {@code HorrorEvents.byId}).
     */
    public static HorrorEvent byId(String id) {
        for (HorrorEvent event : ALL) {
            if (event.id().equals(id)) {
                return event;
            }
        }
        return null;
    }
}
