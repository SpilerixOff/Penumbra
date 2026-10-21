package fr.penumbra.event.night;

import fr.penumbra.event.HorrorEvent;
import java.util.List;

/**
 * Provider des événements nocturnes (Phase 3, contrat Agent_NightEvents).
 * <p>
 * Les 8 événements sont des singletons sans état, instanciés une seule fois
 * (aucune allocation par tick). Intégration attendue (hors de ce fichier) :
 * l'intégrateur ajoute {@link #create()} au pool d'événements du
 * {@code HorrorDirector} et branche {@code /penumbra event night <id>} sur
 * {@link #byId(String)}. Ordre contractuel des ids : window_silhouette,
 * roof_steps, torch_chain, animal_stare, dawn_door, hill_host,
 * false_refuge_light, shadow_crossing.
 * </p>
 */
public final class NightEvents {

    /** Liste immuable des événements nocturnes (singletons partagés). */
    private static final List<HorrorEvent> ALL = List.of(
            new WindowSilhouetteEvent(),
            new RoofStepsEvent(),
            new TorchChainEvent(),
            new AnimalStareEvent(),
            new DawnDoorEvent(),
            new HillHostEvent(),
            new FalseRefugeLightEvent(),
            new ShadowCrossingEvent(),
            // 0.16.0 : le ciel qui ment — pluie perçue par un seul joueur.
            new FalseRainEvent()
    );

    private NightEvents() {
    }

    /**
     * Retourne la liste immuable des événements nocturnes (instances partagées,
     * sans état) à ajouter au pool du directeur.
     */
    public static List<HorrorEvent> create() {
        return ALL;
    }

    /**
     * Retourne l'événement nocturne portant cet id, ou {@code null} si inconnu
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
