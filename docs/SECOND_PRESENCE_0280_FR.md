# Penumbra 0.28.0 — La Seconde Présence

Cette rencontre donne à l'Hôte une nouvelle manière de rôder autour du joueur.
Elle peut être choisie par le directeur après les premières étapes de l'histoire,
si le terrain offre une cachette et un chemin praticable. Elle n'est pas garantie
à chaque sortie et ne remplace pas les quatre volumes de Warrick.

## Ce que l'on peut vivre

Un pas vient de derrière un mur ou un angle de terrain. Le corps est réellement
là, masqué par des blocs opaques. Après une attente, il marche vers le bord de
l'obstacle. Les pas viennent de sa position dans le monde.

Si vous le remarquez, il hésite, puis recule. Le suivre ou avancer franchement
vers lui peut provoquer son bond préparé, avec la prise et le jet déjà présents
dans Penumbra. Certains épisodes se terminent par un simple retrait, même si
vous vous approchez. Le regarder ne suffit pas à déclencher cette attaque.

Vous disposez de plusieurs réactions :

- Rompre la vue derrière un obstacle opaque et rester immobile, au sol, sans
  frapper ni sprinter pendant cinq secondes fait commencer son départ.
- Un flash de caméra bien cadré le repousse, y compris pendant son bond.
- Le talisman de bannissement du propriétaire peut interrompre la rencontre.
- S'éloigner suffisamment met fin à la scène. Elle dure au maximum 38 secondes
  à 20 ticks par seconde ; un serveur ralenti allonge sa durée réelle.

La fiche de l'Hôte dans le Relevé comporte un nouvel indice sur cette filature.
Ouvrir un écran, commencer la cassette, mourir, dormir ou changer de dimension
interrompt cette rencontre. Elle ne reprend pas après reconnexion.

## Terrain et rythme

La créature emprunte un court chemin continu, sur un même niveau, puis le
reprend pour reculer. Elle ne modifie aucun bloc et ne se téléporte pas après
son apparition. Les sols dangereux, les trous, les collisions et les chunks
non chargés invalident le placement ou arrêtent le déplacement. Une vitre ou
des feuilles ne suffisent pas à cacher l'apparition initiale.

Un terrain entièrement ouvert peut donc empêcher cette scène. Il ne s'agit
pas d'une IA de poursuite capable de parcourir n'importe quel bâtiment.

Le directeur espace les rencontres. Le délai propre à Seconde Présence est
de six minutes avant de pouvoir être de nouveau sélectionnée ; les autres
conditions de rythme et de terrain continuent à s'appliquer. Les manifestations
automatiques du directeur attendent sa fin, puis leur délai de répit habituel.
Une lecture de cassette ou la finale a priorité sur cette rencontre.

Le corps et ses sons de scène sont personnels. Si un autre joueur s'approche
trop près, la scène s'arrête. Cela évite qu'une créature invisible pour lui
produise un contact gênant. La même version est requise sur serveur et clients.

## Réglages et diagnostic

`secondPresenceEnabled: false` désactive cette rencontre dans `penumbra.json`.
`hostEnabled` la désactive également. Avec `chasesEnabled: false`, elle peut
encore se montrer et repartir, sans libérer son bond. Les volumes personnels
et les options de présentation déjà disponibles restent applicables.

Commandes opérateur : `/penumbra presence test [joueur]`,
`/penumbra presence status [joueur]`, `/penumbra presence clear [joueur]`.
Le test contourne la sélection aléatoire et le délai propre à cette scène,
mais conserve les vérifications de placement et de compatibilité. Il peut
donc échouer dans une plaine dégagée ou pendant une autre rencontre.

Les [recherches et leurs limites](HORROR_RESEARCH_0280_FR.md) expliquent le choix
de conception. Les [résultats de validation](KNOWN_ISSUES.md) distinguent tests
automatisés, essais en jeu et vérifications restant à faire. Aucune mesure ne
démontre encore que cette rencontre effraie davantage les joueurs de Penumbra.
