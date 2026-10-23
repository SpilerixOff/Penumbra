# Penumbra 0.28.0 — La Seconde Présence

Cette préversion publique réunit les nouveautés développées depuis la 0.24.0.
Téléchargez **`penumbra-0.28.0.jar`** dans les fichiers de la
[version GitHub](https://github.com/SpilerixOff/Penumbra/releases/tag/v0.28.0).
Les archives du code source et le JAR `-sources` ne servent pas à jouer.

## Ce qui change

- **La Seconde Présence** : des pas derrière un obstacle, un véritable
  contournement, un arrêt au moment où vous remarquez l'Hôte, puis un recul.
  Le poursuivre peut provoquer son bond préparé ; certaines rencontres
  finissent simplement par son départ. Immobilité derrière un obstacle
  opaque, flash de la caméra et talisman permettent de réagir.
- **La prise de l'Hôte** : bond, plongée, saisie à deux mains, soulèvement
  et projection. Esquive, obstacle et flash peuvent interrompre son attaque.
- **Huit autres animations distinctes** : l'Observateur, le Rampant,
  l'Écouteur, la Cavaleuse, le Contrefait, le Projectionniste, l'Originel et
  l'Absent ont chacun un geste propre. Les bonds concernés sont plus rapides,
  avec les préparations et récupérations conservées. La Cavaleuse et le
  Projectionniste restent sans dégâts.
- **Une enquête en quatre livres** : les volumes de Warrick relient le poste
  d'observation, la maison décalée, le sanctuaire et le puits. Une cassette
  endommagée, lisible avec la caméra dans l'inventaire, prépare la finale.
  Les indices perdus déjà découverts peuvent être récupérés avec Le Relevé.
- **Des crafts visibles dans Le Relevé** : grilles illustrées, ingrédients,
  résultats et plans qui se débloquent au fil de l'enquête. La boussole a un
  mode Enquête pour retrouver la prochaine piste.
- **Des copies de refuge plus reconnaissables** : proportions, matériaux,
  orientation de l'entrée et mobilier choisi. Elles reproduisent une pièce
  bornée avec une anomalie, sans dupliquer le contenu des coffres.

Les sursauts restent physiques : les visages plein écran et les écrans noirs
de screamer retirés en 0.24.0 ne reviennent pas. **K** ouvre les six réglages
personnels de confort et de volume.

## Installation et mise à jour

| Composant | Version utilisée |
| --- | --- |
| Minecraft Java Edition | 26.1.2 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.155.2+26.1.2 |
| Java | 25 |

Fermez le jeu et le serveur, sauvegardez le monde, puis remplacez l'ancien
JAR Penumbra dans `mods/`. Gardez une seule version et installez **le même
JAR 0.28.0 sur le serveur et chaque client**, avec Fabric API.
Conservez les configurations, sauvegardes et photos.
Voir [l'installation complète](https://github.com/SpilerixOff/Penumbra/blob/v0.28.0/docs/INSTALL.md) et le [premier essai](https://github.com/SpilerixOff/Penumbra/blob/v0.28.0/docs/PLAYTESTING.md).

## Vérifications et limites

**515 tests dans 99 suites**, la compilation et la validation des ressources
ont réussi. Les essais ciblés de la Seconde Présence ont vérifié le placement,
le contournement, le recul, le bond avec prise et projection, le départ au
calme et l'annulation à l'ouverture d'un inventaire. Ils utilisent un seul
client sur un serveur local, un terrain préparé et des commandes de diagnostic.
Le flash a été essayé avant la dernière correction du recul ; il n'a pas été
réessayé pendant un bond sur le JAR final.

Une campagne naturelle complète, le multijoueur simultané, la latence Internet
et l'équilibrage sur des terrains variés restent à tester. La Seconde Présence
exige un trajet court sur un même niveau ; elle peut être refusée dans les
escaliers, sous un plafond bas ou près d'un trou. Les recettes illustrées
représentent celles du mod, pas les changements d'un datapack serveur.
Les anciens puits ne reçoivent pas automatiquement la nouvelle échelle.

Les observations et leurs limites sont détaillées dans les
[problèmes connus](https://github.com/SpilerixOff/Penumbra/blob/v0.28.0/docs/KNOWN_ISSUES.md). Les guides de
[la rencontre](https://github.com/SpilerixOff/Penumbra/blob/v0.28.0/docs/SECOND_PRESENCE_0280_FR.md), de
[l'enquête](https://github.com/SpilerixOff/Penumbra/blob/v0.28.0/docs/INVESTIGATION_0270.md) et les
[recherches sur l'horreur](https://github.com/SpilerixOff/Penumbra/blob/v0.28.0/docs/HORROR_RESEARCH_0280_FR.md) expliquent les choix.
