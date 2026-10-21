package fr.penumbra.director.sequence;

/**
 * Portee visuelle d'une manifestation liee.
 *
 * <p>La portee ne change jamais la cible logique de la creature : le
 * proprietaire du bail reste l'unique cible des dialogues, attaques et
 * screamers. Elle dit seulement quels clients peuvent recevoir l'entite.</p>
 */
public enum AudienceScope {
    /** Seul le proprietaire recoit la manifestation. */
    PRIVATE,
    /** Le proprietaire et les temoins captures au debut de la scene. */
    LOCAL,
    /** Tout observateur valide dans le rayon borne de la scene. */
    SHARED
}
