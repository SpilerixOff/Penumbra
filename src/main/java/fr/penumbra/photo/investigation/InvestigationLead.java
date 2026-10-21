package fr.penumbra.photo.investigation;

import fr.penumbra.photo.PhotoEvidence;

/** Piste dérivée d'une seule preuve connue ; elle ne débloque aucun contre-jeu. */
public record InvestigationLead(InvestigationClue clue, PhotoEvidence observed) {

    public InvestigationLead {
        if (clue == null || observed == null || !clue.requiredEvidence().contains(observed)) {
            throw new IllegalArgumentException("A lead requires evidence from its relationship");
        }
    }

    /** Le texte décrit une action à tenter, sans nommer la catégorie encore inconnue. */
    public String translationKey() {
        return "dossier.penumbra.lead." + clue.id() + "." + observed.id();
    }
}
