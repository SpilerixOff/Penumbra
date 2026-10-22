package fr.penumbra.photo.investigation;

import fr.penumbra.director.sequence.LinkedSceneType;
import fr.penumbra.photo.PhotoEvidence;
import java.util.Collection;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Pont pur entre le dossier photographique existant et les scenes liees.
 * Les indices sont derives : aucune nouvelle donnee de sauvegarde n'est
 * necessaire et une ancienne sauvegarde les obtient des son premier chargement.
 */
public final class InvestigationRules {

    private InvestigationRules() {
    }

    /** Les relations inconnues restent cachées ; une paire complète remplace sa piste. */
    public static List<InvestigationLead> partialLeads(
            Collection<PhotoEvidence> discoveredEvidence) {
        EnumSet<PhotoEvidence> discovered = normalize(discoveredEvidence);
        List<InvestigationLead> leads = new ArrayList<>();
        for (InvestigationClue clue : InvestigationClue.values()) {
            if (discovered.containsAll(clue.requiredEvidence())) {
                continue;
            }
            for (PhotoEvidence observed : discovered) {
                if (clue.requiredEvidence().contains(observed)) {
                    leads.add(new InvestigationLead(clue, observed));
                }
            }
        }
        return List.copyOf(leads);
    }

    public static List<InvestigationClue> unlockedClues(
            Collection<PhotoEvidence> discoveredEvidence) {
        EnumSet<PhotoEvidence> discovered = normalize(discoveredEvidence);
        return java.util.Arrays.stream(InvestigationClue.values())
                .filter(clue -> discovered.containsAll(clue.requiredEvidence()))
                .toList();
    }

    public static List<InvestigationClue> newlyUnlocked(
            Collection<PhotoEvidence> before, Collection<PhotoEvidence> after) {
        EnumSet<InvestigationClue> previous = enumClues(unlockedClues(before));
        return unlockedClues(after).stream()
                .filter(clue -> !previous.contains(clue))
                .toList();
    }

    public static boolean knowsCounterplay(Collection<PhotoEvidence> discoveredEvidence,
                                           LinkedSceneType sceneType) {
        if (sceneType == null) {
            return false;
        }
        return unlockedClues(discoveredEvidence).stream()
                .anyMatch(clue -> clue.sceneType() == sceneType);
    }

    public static Counterplay counterplayFor(LinkedSceneType sceneType) {
        if (sceneType == null) {
            return null;
        }
        for (InvestigationClue clue : InvestigationClue.values()) {
            if (clue.sceneType() == sceneType) {
                return clue.counterplay();
            }
        }
        return null;
    }

    private static EnumSet<PhotoEvidence> normalize(
            Collection<PhotoEvidence> evidence) {
        EnumSet<PhotoEvidence> normalized = EnumSet.noneOf(PhotoEvidence.class);
        if (evidence != null) {
            for (PhotoEvidence value : evidence) {
                if (value != null && value.discoverable()) {
                    normalized.add(value);
                }
            }
        }
        return normalized;
    }

    private static EnumSet<InvestigationClue> enumClues(
            Collection<InvestigationClue> clues) {
        EnumSet<InvestigationClue> values = EnumSet.noneOf(InvestigationClue.class);
        values.addAll(clues);
        return values;
    }
}
