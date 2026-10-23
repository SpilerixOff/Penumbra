# Penumbra 0.27.0 — Les quatre volumes de Warrick

Cette version organise l'enquête autour de **quatre livres, une cassette et
un retour au Sanctuaire de cendre**. Le Relevé conserve la progression propre
à chaque joueur. Les manifestations et la vie au refuge continuent entre les
voyages ; les livres donnent des objectifs concrets pour avancer.

Ce document décrit les sources de développement 0.27.0. La dernière préversion
publiée reste la [0.24.0](https://github.com/SpilerixOff/Penumbra/releases/tag/v0.24.0).
Les résultats des vérifications sont consignés dans [KNOWN_ISSUES.md](KNOWN_ISSUES.md) ;
ce parcours ne constitue pas un compte rendu de campagne entièrement testée.

## Depuis la première connexion

Avec le journal et l'histoire activés, le nouveau joueur reçoit **Le Relevé**.
Une place libre est nécessaire à sa remise. Le clic droit ouvre le carnet ;
**Enquête** indique le nombre de volumes retrouvés et la prochaine destination.
**Fabrication** montre les plans disponibles, leurs ingrédients et le résultat.

Fabriquer une **Boussole d'arpenteur** aide à suivre les lieux de l'enquête.
Son mode **Enquête** vise un site connu du type correspondant au premier
volume manquant. Le clic droit fait défiler les modes ; les modes Camp,
Inexploré et Proche restent disponibles. L'aiguille ne donne pas de coordonnées
et ne crée pas de terrain distant à elle seule.

| Étape | Destination et découverte | Ce que le joueur apprend ou prépare |
|---|---|---|
| 1 | Coffre du **poste d'observation** : *Volume I — Le flash*. | Le rôle de la caméra VHS, ses commandes, son plan et la piste de la maison. |
| 2 | Coffre de la **maison décalée** : *Volume II — La maison qui ment*. | Les imitations de refuge, le talisman de bannissement et la piste du sanctuaire. |
| 3 | Coffre du **Sanctuaire de cendre** : *Volume III — Le serment de cendre*. | Le plan du Sceau de cendre et la nécessité de descendre au puits avant le rituel. |
| 4 | Coffre **au fond du puits de Penumbra** : *Volume IV — La dernière descente*, avec la cassette. | La lecture de la bande, la récupération des archives et le retour au sanctuaire. |
| 5 | Lecture de la **cassette endommagée de Warrick**. | La nature des copies et la méthode de la confrontation. |
| 6 | Retour au **pilier central du Sanctuaire de cendre**. | Utiliser le sceau, puis affronter son double avec la caméra. |

Les quatre livres peuvent être trouvés dans un autre ordre. Un volume trouvé
tôt reste acquis ; Enquête et la boussole reviennent au premier manquant.
Faire un clic droit avec un livre l'ouvre directement à son texte dans le
Relevé. Le livre n'est pas consommé : un joueur peut le prêter à un autre,
qui enregistre alors sa propre découverte en le lisant.

La maison décalée contenant le deuxième volume est une structure d'enquête.
Les copies personnelles du refuge sont des manifestations supplémentaires :
il n'est pas nécessaire d'attendre qu'une copie personnelle apparaisse pour
récupérer le volume II.

## La cassette

Après la découverte du volume IV, garder la **cassette et la caméra VHS dans
l'inventaire**, puis prendre la cassette en main et l'utiliser avec le clic
droit. La lecture dure **300 ticks, soit quinze secondes à 20 TPS**. Elle se
poursuit si l'on change d'objet tenu, tant que cassette et caméra restent dans
l'inventaire.

La présentation comprend des sons de mécanisme et **cinq fragments de texte
dans le chat**. Elle ne contient pas de narration vocale enregistrée. À la fin,
la bande est marquée comme lue et sa transcription complète reste accessible
dans **Enquête**, y compris après reconnexion.

Mourir, passer en spectateur, changer de dimension, se déconnecter ou retirer
la caméra ou la cassette interrompt la lecture. Il faut alors relancer la
bande ; une lecture partielle ne valide pas l'étape. Un serveur qui ralentit
allonge la durée réelle, car la lecture suit les ticks du monde.

## Fabrication

L'onglet **Fabrication** affiche une grille 3×3 et son résultat. Survoler une
case donne le nom de l'objet. Les noms et quantités figurent également à droite ;
les instructions longues continuent sur les pages suivantes.

| Objet | Ingrédients | Disposition | Plan affiché |
|---|---|---|---|
| Boussole d'arpenteur | 1 boussole + 1 lingot de cuivre | Libre | Dès le début |
| Le Relevé | 1 livre + 1 poche d'encre | Libre | Dès le début |
| Caméra VHS | 4 vitres + 2 lingots de fer + 1 cuir | Schéma ci-dessous, sur établi | Après le volume I |
| Talisman de bannissement ×2 | 1 papier + 1 charbon de bois + 1 poudre de redstone | Libre | Après le volume II |
| Sceau de cendre | 4 charbons de bois + 1 éclat d'améthyste | Croix ci-dessous, sur établi | Après le volume III |

Caméra : `V` = vitre, `F` = fer, `C` = cuir, `·` = case vide.

```text
V · V
F C F
V · V
```

Sceau : `B` = charbon de bois, `A` = éclat d'améthyste.

```text
· B ·
B A B
· B ·
```

Le **talisman de bannissement** repousse des manifestations proches. Le
**Sceau de cendre** déclenche le rituel : ce sont deux objets différents.
Les livres révèlent les plans dans le carnet ; ils ne verrouillent pas
techniquement les recettes de l'établi. Les plans du carnet lisent les JSON
fournis avec Penumbra ; un datapack serveur qui remplace une recette n'est pas
reflété automatiquement dans ces illustrations.

## Archives perdues et serveur déjà exploré

- **Coffre déjà vidé :** utiliser le coffre d'origine suffit à retrouver
  personnellement son archive. Le contenu partagé du coffre reste inchangé.
- **Coffre détruit :** utiliser Le Relevé à moins de quatre blocs de son
  ancien emplacement récupère l'archive. Pour le puits, il faut descendre à
  l'emplacement réel du coffre ; rester à la surface ne suffit pas.
- **Livre ou cassette perdus :** s'accroupir et utiliser Le Relevé restitue
  les volumes déjà découverts et, après le volume IV, la cassette.
- **Inventaire plein :** la découverte reste conservée. Libérer des places
  et refaire la récupération ; chaque volume et la cassette occupent leur
  propre emplacement. Les objets déjà présents ne sont pas ajoutés en double.
- **Carnet perdu :** refaire un Relevé avec un livre et une poche d'encre.
  La progression appartient au joueur, pas à l'objet.

Il n'est pas nécessaire de porter les quatre livres pour le rituel : ce sont
leurs découvertes et la lecture complète de la cassette qui comptent. En
multijoueur, chacun suit sa progression ; terminer l'histoire ne termine pas
celle des autres.

## Si la boussole ne trouve aucune piste

Quand aucun site du type recherché n'est enregistré, le système peut préparer
ce site pendant l'exploration de **nouveaux chunks de l'Overworld**. L'histoire,
le journal et les structures doivent être activés. Le terrain doit être
naturel, suffisamment plat et dégagé, sans eau ni bloc protégé dans le volume
à construire, à distance des joueurs et des autres structures.

Continuer vers des terres nouvelles et ouvertes est donc utile. Tourner en
rond dans une zone déjà générée ne fournit pas de candidats. Un océan, une
forêt serrée, un relief accidenté ou une zone construite peut faire refuser
les emplacements. Il n'existe pas de distance ou de délai garantis avant
l'apparition d'un site ; la boussole ne téléporte pas le joueur. Si un site
approprié est déjà enregistré, la boussole le vise au lieu d'en créer un autre.

## La fin et les anciennes sauvegardes

Pour une nouvelle enquête, réunir les **quatre découvertes**, finir la lecture
de la **cassette**, puis revenir au sanctuaire avec **une caméra VHS et un Sceau
de cendre**. Utiliser le sceau sur le pilier central. Si un indice ou la caméra
manque, le rituel l'indique et ne consomme pas le sceau pour cette tentative.

Après le prélude, garder la caméra en main, rester immobile et cadrer le double.
Photographier avec le clic gauche pendant ses ouvertures. **Trois flashes
réussis** permettent de gagner. Mourir ou interrompre la tentative ne supprime
pas les livres ni la bande déjà validés ; un nouveau sceau permet de revenir.
La victoire conserve l'épilogue, le badge et l'accalmie du système existant.

Les quatre anciens identifiants de pages sont conservés : `method_camera`,
`method_refuge`, `method_stillness` et `last_page`. Les anciennes pages déjà
retrouvées comptent comme leurs volumes correspondants ; les anciennes pages
arrachées encore en inventaire restent utilisables.

Un joueur **déjà au stade 5 ou 6 avant la première initialisation de cette
enquête** conserve son accès à la finale. Il n'est pas obligé de refaire le
parcours des livres pour débloquer un objectif précédemment atteint. La
première initialisation enregistre cette exception ; atteindre le stade 5
après cela ne contourne pas les nouveaux prérequis. Les sauvegardes ayant
déjà enregistré une victoire la conservent.

Installer la même version **0.27.0** sur le serveur et tous les clients : le
paquet du journal contient désormais la progression des volumes, la cassette
et le livre à ouvrir.

## Les copies de maison

La copie personnelle reprend **une pièce représentative**, avec une limite
de neuf blocs par côté et de 63 blocs au sol. Elle adapte les proportions du
refuge, les matériaux de mur et de sol reconnus, le côté de l'entrée et quelques
meubles : lit, table, four ou coffre selon l'espace disponible. Une fenêtre
condamnée avec un mauvais matériau apporte une anomalie visible ; une autre
ouverture reste vitrée. Une lampe au plafond et un passage dégagé gardent la
pièce lisible et accessible.

Ce système ne reproduit pas fidèlement tous les étages, les façades complexes,
les machines ou chaque bloc d'une grande maison. Le contenu des coffres n'est
jamais dupliqué. Les imitations d'objets déposées dans le coffre de la copie
sont du papier portant un nom presque juste, pas de véritables équipements.

La construction nécessite un refuge déjà appris, l'activation des copies et
un emplacement admissible. Elle respecte les délais et l'isolement prévus
par le directeur ; elle peut être reportée faute de terrain. Les contrôles
vérifient l'emprise entière, l'accès de la porte, les liquides et les meubles
proches avant de construire. La maison d'origine reste intacte. Une restitution
ultérieure des blocs de la copie évite d'écraser un bloc dont l'état a été
modifié entre-temps par le joueur.

## Accès au puits

Les nouveaux puits possèdent une échelle avec support maçonné, les deux blocs d’eau au fond et un auvent relevé pour sortir debout. Les puits déjà sauvegardés ne sont pas reconstruits par la mise à jour.
