# Changelog

## [0.22.0] — Votre façon de vivre l'horreur

- **K** ouvre les réglages personnels en jeu, sans commande opérateur.
  Trois préréglages et huit réglages permettent de réduire les mouvements,
  masquer les visages plein écran, bloquer les flashs et régler séparément
  le grain, les rémanences, les torches, les sursauts et les nappes sonores.
- Les choix sont enregistrés dans `config/penumbra-client.json`. Appliquer
  les active ; Annuler ou Échap laisse les anciens choix intacts. Ils ne
  modifient pas la difficulté du serveur, les créatures ou le dossier.
- Le mouvement réduit arrête les secousses, la respiration du viseur,
  la dérive de champ de vision et les effets animés supplémentaires. Le
  zoom volontaire de la caméra reste disponible. Masquer un flash visuel
  n'empêche pas la caméra de fonctionner pendant une confrontation.
- Les nappes et le cœur déjà en cours sont arrêtés à l'application des
  réglages, puis reprennent au nouveau volume. Les sons du monde et les
  indices positionnels restent distincts de ce réglage d'ambiance.
- L'accalmie après le rituel est sauvegardée par joueur. Elle dure au plus
  48 000 ticks du monde : le temps s'écoule sur un serveur actif, y compris
  hors connexion, et s'arrête quand le serveur est arrêté. Rejoindre ou
  changer de dimension n'accorde pas une nouvelle période de calme.
- Le badge final récompense désormais une confrontation gagnée. Atteindre
  le stade narratif 6 ne le débloque plus ; les anciens badges sont conservés.

## [0.21.1] — Stabilisation du rituel et des photos

### Un rituel réservé à son participant

- La réservation commence dès l'activation du sanctuaire. Elle interrompt les
  scènes liées et les apparitions temporaires proches appartenant au participant,
  sans supprimer les créatures d'un autre joueur ni les créatures ordinaires.
- Les suites déjà programmées des faux pas, de l'anticlimax et du retour au
  refuge respectent aussi cette réservation, pour préserver l'écoute du rituel.
- Chaque préambule appartient à une tentative précise. S'éloigner de plus de
  40 blocs, changer de dimension, mourir, dormir, passer en spectateur ou se
  déconnecter l'annule ; ses anciens effets différés ne reprennent pas au retour.
- Le sanctuaire n'est validé qu'après le préambule, au démarrage du duel ou à
  la conclusion normale du rituel. Une tentative interrompue reste rejouable
  sur le même autel, sans attribuer une victoire ou une récompense de calme.
- L'apparition de l'Hôte pendant le préambule vérifie l'espace nécessaire à
  toute sa silhouette, le sol et les limites du monde. Si aucun emplacement
  sûr n'est disponible, le préambule se poursuit sans cette apparition.
- Pendant le duel, le reflet refuse un déplacement qui le cacherait derrière
  le pilier ou un mur. Il garde sa dernière position visible pour permettre
  l'observation et le flash, tout en respectant le sol et les collisions.
- Chaque fragment gagné est aussi confirmé dans le chat : le message de
  sortie d'une photo ne peut plus masquer la progression du duel.

### Des captures liées à la bonne session

- Une capture encore en traitement lors d'une déconnexion ne peut plus
  terminer ou annuler une nouvelle prise de vue après reconnexion.
- Les identifiants des prises restent distincts entre les connexions ; les
  anciens retours du déclencheur et de la capture sont ignorés.

## [0.21.0] — Écouter, comprendre, confronter

### Ambiance sonore

- Trois variantes pour chaque nappe de tension, plus de nouveaux pas lourds
  et imités : 13 fichiers produits ou remplacés, dont 10 variantes ajoutées.
- Les nappes durent huit secondes et leurs instances sont suivies : deux au
  maximum pendant le fondu, un seul battement cardiaque. Le faux calme et la
  désactivation du son amélioré arrêtent aussi les pistes déjà lancées.
- Pause, changement de monde, réapparition et déconnexion nettoient ou figent
  correctement la couche sonore et ses minuteurs.
- Les fichiers sont redécodés après encodage pour contrôler pics, durée et
  fondus. Le générateur original et ses graines sont livrés dans les sources.

### Un dossier pour mener l'enquête

- L'album donne accès à un dossier personnel : méthode, observations, pistes
  partielles et déductions. Une piste décrit une action possible sans nommer
  une créature encore inconnue ; une paire complète devient une déduction.
- L'Écouteur devient une catégorie de preuve photographique.
- L'Écouteur effectue trois recherches infructueuses avant de repartir. Un
  chemin bloqué n'est plus compté deux fois, évitant des rencontres trop courtes
  en terrain accidenté.
- La limite de huit preuves par photo ne tronque plus le dossier global :
  toutes les catégories connues persistent, sans notification répétée à chaque
  redécouverte. Le dossier affiché vient du serveur, pas des photos publiques.
- Les vérifications de cadrage précèdent les rayons de visibilité dans le
  détecteur photographique, pour éviter les recherches inutiles.
- À la déconnexion, les textures photographiques et la couche sonore sont
  nettoyées sur le thread client, évitant une erreur OpenGL à la sortie du monde.

### Une confrontation qui se prépare

- Le rituel final convoque une copie dédiée au bon joueur. Elle ne conserve
  plus l'IA de chasse ni la durée de vie d'une apparition ordinaire.
- Trois séquences demandent silence et observation avant une fenêtre de
  flash, avec une respiration entre les réussites. Rater une fenêtre ne
  retire pas les réussites acquises ; le flash visuel reste facultatif.
- Le reflet cherche des emplacements chargés, avec sol et espace suffisants,
  sans déplacer le joueur. Les nouvelles scènes automatiques du directeur
  attendent la fin du duel.
- Le même sanctuaire permet de retenter après un abandon ; le préambule ne
  peut pas être déclenché plusieurs fois en parallèle. Un échec d'apparition
  ne donne pas la récompense de victoire.
- Les victoires déjà sauvegardées et la récompense de calme sont conservées.

### Éclairage

- Les sources proches partagent les mises à jour de terrain : chaque section
  touchée n'est invalidée qu'une fois par tick, même avec plusieurs torches.
- L'instantané de lumière est publié avant de demander les reconstructions.
  Une pause en solo conserve la lumière au lieu de tout éteindre et reconstruire.

## [0.20.0] — Tension, fuite et enquête

### Une peur qui laisse le temps de comprendre

- Le directeur réserve les présages et les retombées aux indices. Les apparitions
  arrivent pendant la montée et la manifestation ; l'escalade automatique vers
  une poursuite ou un scare attend la phase de manifestation.
- Les scènes autonomes respectent aussi une respiration en solo : au moins
  10 secondes après un effet perceptible, 20 après une manifestation.
  Une manifestation en retard ne contourne plus l'intervalle des événements.
- Une santé basse, la faim, le manque d'air, le feu et les dégâts récents
  suspendent les nouvelles scènes de pression ; 10 secondes de récupération
  suivent. Cela n'annule pas une créature déjà engagée.
- Le faux calme sauvegardé est reconnu par l'ambiance client et les règles
  de contagion. Les commandes de diagnostic restent disponibles ; les suites
  internes des scènes liées conservent leur déroulement.

### Des rencontres dont on peut apprendre les règles

- **Hôte :** couper sa ligne de vue pendant 4 secondes permet de s'échapper.
  Il poursuit la dernière position vue, plus la position exacte derrière un
  mur. Revenir dans son regard réinitialise sa mémoire de poursuite.
- Son bond de regard commence par 0,6 seconde d'immobilité et un son ; sa
  destination est fixée au déclenchement. Une esquive, un obstacle ou la
  rupture de vue peut éviter l'impact. Il ne réarme plus ce bond pendant sa
  retraite ou son repos.
- **Écouteur :** sa charge annonce aussi 0,6 seconde avant de pouvoir toucher.
  Un propriétaire silencieux est protégé au contact, même si un autre joueur
  attire la créature. Un mur bloque le coup et 34 ticks de silence cassent
  toujours son verrouillage.
- Sa durée de scène ne se fige plus quand la cible s'éloigne. Sa sortie est
  bornée, et les scènes des autres créatures se nettoient lorsque leur
  propriétaire meurt, dort, quitte ou devient spectateur.
- Les bonds de l'Observateur, du Contrefait et de l'Originel s'interrompent
  lorsque la ligne de vue est rompue.

### Retrouver sa route et lire ses découvertes

- **Boussole :** clic droit pour choisir le camp de Warrick, les sites
  inexplorés ou le site le plus proche. Le choix reste sur l'objet ; la
  découverte reste propre à chaque joueur. Les anciennes boussoles commencent
  par guider vers le camp. Aucun chunk distant n'est chargé pour lire son
  altitude. Les autres sites doivent déjà être générés.
- **Carnet :** papier lisible sous le texte, auteur et titre sur plusieurs
  lignes, dimensions adaptées à l'écran, paragraphes longs répartis sans
  perdre les contre-mesures, indices paginés, flèches corrigées et navigation
  au clavier ou à la molette.
- Les structures construites sont immédiatement envoyées aux clients présents,
  sans devoir recharger le monde. Leur commande respecte maintenant la
  position et la rotation de `/execute`.

Les tests ajoutés couvrent le rythme, la mémoire de poursuite, le contact de
l'Écouteur, les itinéraires de boussole et les bornes de pagination. Cette
version reste à éprouver sur de longues parties et avec plusieurs joueurs.

## [0.19.0] — Maillages

### Les objets trouvés — le mobilier qui n'a rien à faire là

Les douze décors 3D n'étaient posés par rien : `/penumbra prop` était leur unique
point d'entrée. Trente mégaoctets d'assets finis, zéro seconde de jeu. Ils vivent
maintenant tout seuls.

- **Le Directeur pose du mobilier ordinaire sur les chemins du joueur.** Une
  chaise de cuisine, une porte fermée dans son chambranle sans murs, un miroir
  fêlé, une poupée de chiffon, une effigie de branches, une croix, un cairn, un
  nid, une borne, une souche. Aucun n'est effrayant : **c'est l'endroit qui
  travaille.** Une chaise reste une chaise — seule, dos tourné, à quatre cents
  blocs de toute habitation, elle devient une question.
- **Trois règles de pose.** *Jamais dans le champ de vision* — la pose se fait
  derrière le joueur, à 78° minimum de sa visée (le cône vanilla en fait 70,
  donc 43° de marge), avec un test de ligne de vue réel avant chaque tentative :
  un objet qui apparaît est un effet, un objet qu'on découvre est un fait.
  *Ni trop près ni trop loin* — entre dix-sept et trente-trois blocs, assez loin
  pour ne pas être vu naître, assez près pour être croisé. *Un seul par secteur*
  — cinquante-six blocs d'exclusion autour de tout décor existant, parce que
  deux chaises font une décharge quand une seule fait une question.
- L'orientation dépend de l'objet : la **chaise tourne le dos** (quelqu'un
  regardait quelque chose), le miroir et la poupée font face. Chacun reçoit
  jusqu'à 12° de travers — rien de posé à la main n'est parfaitement aligné.
- Rythme : environ un objet toutes les **quinze minutes** à réglage normal, et
  le curseur de fréquence agit réellement — de soixante-six minutes à 25 % à
  quatre minutes à 400 %. C'est le **repos** qui est mis à l'échelle, pas la
  chance : le tirage un-sur-neuf était complètement écrasé par le repos fixe, et
  passer de 50 % à 200 % ne changeait le rythme observé que de dix-huit à seize
  minutes. Le réglage ne servait à rien.
- Les objets sont de vraies entités sauvegardées : ils restent après
  déconnexion, et on repasse devant. Aucun chunk n'est jamais chargé de force.
  `foundObjectsEnabled` coupe le tout.

### Le camp du Bûcher meublé

- **Le théodolite de Warrick**, resté monté sur son trépied face à l'horizon
  qu'il relevait. Le carnet répète qu'il était arpenteur ; on ne voyait jamais
  son métier. Un instrument abandonné dit ce que quinze pages expliquent.
- À côté, son **paquetage** éventré et un **cairn** qu'il a empilé lui-même — la
  seule chose du camp qui n'ait servi à rien.
- Chaque objet cherche son propre sol : le camp est bâti sur du terrain naturel,
  et une pente suffirait sinon à le laisser flotter.

### La Boussole d'arpenteur

- **Nouvel objet**, fabricable dès le premier jour : une **boussole vanilla plus
  un lingot de cuivre**, sans façon. Elle sert justement à trouver ce qu'on ne
  trouve pas autrement.
- Elle **pointe le site connu le plus proche**. Le Bûcher compte toujours — sa
  position se calcule depuis la graine, elle n'a pas besoin d'avoir été visitée
  — les quatre autres structures seulement une fois réellement générées.
- **Pourquoi elle existe.** Le Bûcher est à onze cents ou deux mille six cents
  blocs, et sa fumée ne se voit qu'à portée d'affichage. On pouvait donc jouer
  cent heures sans jamais tomber dessus, et sans jamais trouver Le Relevé, qui
  est pourtant la clé du reste. Il manquait une direction fiable.
- **Aucun rendu sur mesure.** La cible est écrite dans le composant vanilla
  `lodestone_tracker`, drapeau de suivi à *faux* pour que le jeu ne cherche pas
  de pierre-repère à l'arrivée : le moteur fait tout le calcul d'angle et la
  définition d'objet choisit parmi trente-deux images. C'est le mécanisme d'une
  boussole liée à une pierre-repère, détourné vers un lieu quelconque.
- **Trente-deux images en laiton patiné**, cadran bleu-noir, aiguille rouge et
  os, générées par `tools/gen_survey_compass.py`. La convention de rotation a
  été relevée dans les fichiers vanilla eux-mêmes : valeur 0 = aiguille en haut,
  puis 11,25° par cran dans le sens horaire.
- **Elle ne donne jamais un chiffre.** Comme le carnet de Warrick, elle dit
  « à une bonne marche » ou « très loin — Warrick a mis des jours », pas
  « 1843 blocs ». Un instrument d'époque ne connaît pas la télémétrie.
- Hors de l'Overworld, la cible est retirée et l'aiguille se fige.

### La torche éclaire enfin — et elle vacille

- **Éclairage dynamique porté à la main.** Torche, torche des âmes, lanterne,
  pierre lumineuse, chandelle : ce qui éclaire une fois posé éclaire aussi tenu
  en main, et tombé au sol. Aucune liste en dur — l'intensité est lue sur le
  bloc que l'objet pose, donc les objets lumineux d'un autre mod fonctionnent
  sans rien ajouter.
- **Un seul point d'accroche.** La lumière de bloc de Minecraft n'est pas
  calculée à l'image : elle est cuite dans les sommets de chaque section au
  moment où sa géométrie est construite — c'est précisément pourquoi Mojang ne
  fait pas d'éclairage dynamique. Mais tout le rendu passe par
  `LevelRenderer.getLightCoords` : le terrain via `ModelBlockRenderer`, les
  entités, les particules. Trente-quatre classes du jar 26.1.2 y mènent. Un
  mixin unique éclaire donc la scène entière, sol, murs, créatures et fumée
  comprises, sans toucher au moteur de lumière ni au serveur.
- **La flamme faiblit quand la tension monte.** C'est la seule raison d'écrire
  ça ici plutôt que d'installer un mod d'éclairage générique : au-dessus de 25
  de tension la torche commence à battre, et plus haut elle manque de s'éteindre.
  Le joueur voit son rayon de sécurité se rétracter sans qu'on lui dise rien.
- **Coût assumé, et isolé.** C'est la seule option du mod qui puisse peser sur
  les images par seconde : déplacer une source oblige à reconstruire la
  géométrie autour d'elle. Trois garde-fous : la reconstruction n'a lieu que
  lorsque la source **change de bloc ou d'intensité**, le vacillement est
  quantifié à l'entier et ne bouge qu'un tick sur cinq, et la lecture sort en
  une comparaison quand aucune source n'existe. `dynamicLightsEnabled` et
  `dynamicLightFlicker` permettent de tout couper.
- Les sources sont publiées dans un **tableau immuable relu à chaque tick** : la
  lecture a lieu depuis les fils de compilation des sections pendant que le tick
  client réécrit la liste, et faire lire une `ArrayList` en cours de
  modification produirait des exceptions aléatoires en pleine génération de
  terrain.

### Corrections

- **Un monde sur deux pouvait n'avoir aucun Relevé.** La position du Bûcher
  venait de la graine et de rien d'autre. Quand elle tombait dans l'océan — ce
  qui est arrivé au tout premier essai en jeu — le code posait le marqueur
  « inutile de réessayer » et ce monde n'avait **ni Bûcher, ni Relevé, ni les
  quinze pages**. Une pièce lancée décidait si la clé du mod existait.
  - Le site **cherche maintenant la terre ferme** : anneaux concentriques du
    plus proche au plus lointain, 378 sondages, jusqu'à 1960 blocs autour du
    tirage. Le premier sol franchement au-dessus de la mer gagne, donc le site
    reste au plus près de sa position d'origine.
  - **Aucun chunk n'est généré** : `ChunkGenerator.getBaseHeight` échantillonne
    le bruit de terrain à n'importe quelle coordonnée — c'est ce qu'utilise
    `/locate`. Le résultat est mis en cache et reste déterministe : deux joueurs
    d'une même carte obtiennent le même site.
  - Le nombre de points **croît avec le rayon**. À nombre fixe, douze sondages
    sur un anneau de deux mille blocs laissent mille blocs entre deux essais —
    de quoi manquer une île entière. L'écart reste ici de l'ordre de cent blocs.
- **`/penumbra pyre tp` tuait le joueur.** La chute accumulée avant la
  téléportation restait comptée à l'arrivée : premier essai réel, premier
  « s'est écrasé au sol ». Vitesse et distance de chute sont remises à zéro.
- **Un seul raté condamnait le Bûcher d'un monde.** Le marqueur « déjà
  construit » était posé dans un `finally`, donc **même quand la construction
  échouait**. Une exception ou un chunk à moitié prêt suffisait à supprimer
  définitivement le camp de ce monde, sans message et sans recours. Il n'est
  plus posé qu'en cas de réussite franche — ou de site tombé en mer, le seul
  échec qu'il soit inutile de réessayer.
- **Le site était muet quand on y arrivait.** La densité du panache baissait
  avec la distance, jusqu'à une particule par pulsation étalée sur une colonne
  de soixante-quatre blocs. L'idée était que le feu du camp prendrait le relais
  de près — sauf que tant que le camp n'est pas bâti, il n'y a aucun feu. On
  arrivait donc pile à l'endroit et on ne voyait rien. Plancher à trois
  particules, quelle que soit la distance.
- **`/penumbra pyre tp`** vous pose sur le site et construit le camp dans la
  foulée. Le chunk est **chargé de force avant de calculer l'altitude** : sans
  ça on se téléporterait dans le vide, le terrain n'existant pas encore. C'est
  un chargement bloquant — inacceptable dans une boucle de jeu, sans importance
  dans une commande d'administration.
- **`/penumbra pyre build`** construit le camp immédiatement et **dit pourquoi**
  quand il échoue : chunk non chargé, site en mer, déjà bâti. **`/penumbra pyre
  reset`** l'oublie pour retester. Et `/penumbra pyre` affiche désormais l'état
  du sol — chunk chargé, altitude, au-dessus de la mer, option active.
- **Le panache du Bûcher était invisible.** Il était émis jusqu'à 480 blocs, la
  limite du mode forcé côté serveur — exact, et parfaitement inutile : le
  brouillard de Minecraft se ferme à peu près à la distance d'affichage du
  client, et tout ce qui est au-delà est peint en couleur de ciel. Un joueur en
  12 chunks recevait bien les particules et ne voyait rien. La portée se cale
  maintenant sur **80 % de la distance de rendu réelle du joueur** (96 blocs au
  minimum, 480 au maximum) et la colonne est **répartie régulièrement sur sa
  hauteur** au lieu d'être tirée au sort, ce qui supprime les trous qui la
  faisaient lire comme des taches éparses.
- **`/penumbra pyre`** : site exact dérivé de la graine, distance, direction,
  portée d'émission pour ta distance d'affichage, et si le camp est déjà bâti.
  « Je ne vois pas la fumée » avait trois causes indiscernables en jeu — trop
  loin, distance de rendu trop courte, site tombé en mer — cette commande les
  sépare.
- **L'Absent ne se copie plus sur le témoin.** C'était l'erreur de fond de la
  scène : dès qu'aucun autre joueur n'était connecté — donc à peu près toujours,
  en solo — il portait le skin et le nom du joueur lui-même. Or on reconnaît son
  propre skin en un dixième de seconde, et le doute, qui est toute la mécanique,
  n'a jamais le temps de s'installer.
  - Nouveau registre `KnownIdentities` : **chaque connexion au monde est
    enregistrée**. L'Absent porte en priorité l'identité d'un autre joueur en
    ligne, sinon celle de quelqu'un qui **est déjà passé sur ce monde** — un nom
    familier attaché à une personne qui n'est pas là.
  - Sur un monde qui n'a jamais vu personne d'autre, il prend celle d'un
    **inconnu tiré de la graine**, donc toujours le même. Un pseudo aléatoire à
    chaque fois se lit comme un bug ; le même étranger qui revient se lit comme
    une présence. L'UUID est synthétique : aucun compte réel n'est usurpé, et le
    client retombe sur un skin par défaut.
  - Le témoin n'est **jamais** retenu, quelle que soit la source. `/penumbra diag`
    affiche le nombre d'identités connues du monde et l'inconnu de la graine.
- **La Cavaleuse était invisible, et pour trois raisons à la fois.**
  - *Elle était rendue en noir sur noir.* Elle n'apparaît que là où la lumière
    de bloc est basse — c'est la condition de la scène — et son renderer ne
    faisait rien de particulier : une créature sombre éclairée au niveau 0 dans
    une grotte noire ne se voit pas. La scène se jouait, le son se jouait, le
    joueur ne voyait rien. Les conditions d'apparition et de visibilité se
    contredisaient. Elle a maintenant un **plancher d'éclairage à 5**, le
    niveau où une silhouette se détache sans qu'on lise ses détails, comme le
    fait vanilla pour les blazes.
  - *La traversée dépendait du pathfinder.* Une scène scriptée de deux secondes
    ne peut pas attendre qu'un chemin soit trouvé : sur un sol de grotte
    irrégulier il échouait souvent, et la Cavaleuse s'évanouissait alors sur
    place au bout de neuf ticks. Elle avance désormais **en ligne droite à
    vitesse constante**, orientée sur son déplacement, en 1,5 à 2,4 secondes
    quelle que soit la longueur du trajet — un balayage de 70° à 96° devant le
    joueur, contre 70° de champ de vision par défaut.
  - *Une torche suffisait à l'interdire.* Le seuil de lumière de bloc au pied du
    joueur passe de 6 à **9** : un couloir balisé reste éligible, une pièce
    franchement éclairée ne l'est toujours pas.
- **`/penumbra diag`** : l'état des dix portes que doit franchir une
  manifestation automatique (grâce, stade de contenu, tension, lumière, heure,
  difficulté, quotas), et la liste de celles qui sont fermées. Utile en
  particulier pour la Cavaleuse, qui exige un stade de contenu ≥ 1 : sur un
  monde neuf en tension calme, elle ne peut pas apparaître, et rien ne le disait.
- `SIGNAL_PYRE` manquait dans le `switch` exhaustif de
  `PenumbraStructures.build()` depuis 0.18.0 : **le projet ne compilait pas**.

### De la vraie 3D dans Minecraft, sans mixin

- Minecraft 26.1 expose `SubmitNodeCollector.submitCustomGeometry`, une porte de
  sortie officielle qui donne accès à un `VertexConsumer` brut. Le mod l'utilisait
  déjà pour les tranches du cadre photo ; elle sert maintenant à afficher des
  **maillages triangulés complets**, importés depuis Blender, Meshy ou Blockbench.
  La géométrie traverse le même chemin que le reste du jeu : elle reçoit donc
  l'éclairage, le brouillard et le tri de transparence sans effort.
- Quatre classes : `ObjLoader` (lecture Wavefront `.obj` et triangulation),
  `ObjMesh` (stockage à plat, aucune allocation au rendu), `MeshLibrary` (cache
  par nom, chargement paresseux) et `MeshRenderer` (soumission au GPU).
- **Objets statiques uniquement**, et c'est un choix. Animer demanderait
  d'implémenter le *skinning* — un morceau de moteur de jeu — pour un résultat
  inférieur à l'animation procédurale de `MonsterAnimationMath`, qui a l'avantage
  décisif de ne jamais boucler à l'identique. Le partage est donc : boîtes
  animées pour ce qui vit, maillages pour ce qui ne bouge pas.

### Ajouter un décor : deux fichiers, aucun code

- `assets/penumbra/meshes/<nom>.obj` et
  `assets/penumbra/textures/entity/prop/<nom>.png`, puis `/penumbra prop <nom>`.
- Les fichiers passent par le gestionnaire de ressources vanilla : **un pack de
  ressources peut ajouter ses propres maillages**, ce qui rend le système
  utilisable par quelqu'un d'autre que nous.
- `MeshPropEntity` : une entité de décor volontairement inerte — pas de gravité,
  pas de collision, pas de points de vie, `tick()` vide. Elle transporte trois
  informations synchronisées (maillage, texture, échelle) et rien d'autre. Elle
  est sauvegardée avec le monde et se retire d'un coup en créatif.

### Tolérances du chargeur

- **Sans `vn`, les normales sont recalculées** par produit vectoriel : c'est le
  défaut le plus fréquent des fichiers trouvés en ligne, et l'éclairage reste
  juste. Sans `vt`, l'objet prend la couleur du premier texel.
- Les faces à plus de trois sommets sont triangulées en éventail ; les index
  négatifs fonctionnent ; une ligne malformée est ignorée sans faire échouer le
  chargement. Un modèle de décor ne doit jamais empêcher le jeu de démarrer.
- Refus au-delà de 200 000 triangles, avec un message explicite.

### Douze décors installés

Générés dans Meshy, normalisés et posés dans le mod. Les trois premiers rendent
visible le métier d'Elias Warrick, que Le Relevé mentionne sans jamais le montrer.

| Maillage | Hauteur | Ce que c'est |
|---|---|---|
| `theodolite` | 1,6 | théodolite d'arpenteur sur trépied, laiton oxydé |
| `borne` | 1,1 | borne d'arpentage gravée, penchée |
| `campement` | 0,5 | sac de toile éventré, bottes, gamelle |
| `effigie` | 2,2 | effigie humaine en branches liées |
| `cairn` | 1,3 | empilement de pierres plates et d'os |
| `croix` | 1,4 | marqueur de tombe en planches, sans nom |
| `chaise` | 1,1 | chaise de cuisine ordinaire |
| `porte` | 2,3 | porte fermée dans son chambranle, sans murs |
| `miroir` | 2,0 | miroir sur pied, glace fêlée, drap |
| `nid` | 0,7 | nid d'herbe, de cheveux et d'os |
| `souche` | 1,0 | souche coupée, deux creux et une fente |
| `poupee` | 1,5 | poupée de chiffon à taille d'enfant |

Environ 10 000 triangles chacun, textures ramenées de 8192² à 1024². Le décor
n'est jamais effrayant en soi — c'est **l'emplacement** qui fait le travail :
une chaise de cuisine à quatre cents blocs de toute habitation fait un effet
qu'aucune statue à crocs ne fera.

### Outils

- `tools/preview_meshes.py` : rasteriseur logiciel qui rend une planche-contact
  de tous les maillages installés, sans lancer Minecraft. Même géométrie, mêmes
  UV, même texture — un objet à l'envers, troué ou mal texturé se voit avant de
  charger un monde.
- `tools/typecheck/` : compilation hors ligne de tout `src/main/java` contre le
  vrai jar 26.1.2, sans réseau. 148 classes-doublures écrites à la main pour
  Fabric, Brigadier, Gson, JOML, Mixin et SLF4J, plus la réécriture des octets
  de version du jar de Loom (69 → 65) pour qu'un javac 21 puisse le lire. C'est
  ce banc qui a trouvé l'oubli de `SIGNAL_PYRE`.
- `tools/import_mesh.py` : installe n'importe quel modèle téléchargé. Il met à
  l'échelle en blocs, **pose l'objet exactement sur Y = 0** (sinon la statue
  lévite), le recentre, triangule, retire matériaux et sommets orphelins,
  convertit la texture en PNG puissance de deux, et repère la vraie carte de
  couleur via le `.mtl` — sans quoi la carte de normales serait prise pour la
  texture et l'objet sortirait violet fluo. Conversion Z-haut → Y-haut et
  allègement par regroupement de sommets en option.
- `tools/gen_effigy_mesh.py` : l'effigie de démonstration, 464 triangles, une
  pierre dressée taillée en silhouette et tournée de 34° sur sa hauteur —
  impossible à obtenir avec des boîtes alignées sur les axes. Exportée sans
  normales, volontairement, pour éprouver le chemin de recalcul.
- `docs/MESHES.md` (fonctionnement interne) et `docs/PROMPTS_PROPS.md` (réglages
  Meshy, douze prompts prêts à copier, et pourquoi il ne faut jamais écrire le
  mot « horreur » dans un prompt 3D).

## [0.18.0] — Le Relevé

### Le Bûcher — une colonne de fumée à l'horizon

- Un **unique feu de signal par monde**, à onze cents ou deux mille six cents
  blocs de l'origine, à une position dérivée de la graine : deux joueurs de la
  même carte le trouveront exactement au même endroit.
- **La fumée existe avant la structure.** Le panache est émis en altitude et
  diffusé en mode forcé : il est donc visible jusqu'à 480 blocs, bien avant que
  le chunk du sol ne soit chargé. Le joueur voit une colonne de fumée à
  l'horizon, marche vers elle, et le camp ne se construit sous ses pieds qu'à
  cent blocs. L'inverse aurait rendu le repère inutile.
- Le camp : une cheminée de pierre de onze blocs qui fume encore, un abri de
  fortune au toit percé, une pile de bûches jamais brûlée, des cendres, des
  toiles, un panneau — « je garde le feu allumé pour qu'on me trouve ». Rien
  n'explique ce qui est arrivé à son propriétaire.
- Aucun chunk n'est jamais chargé de force ; le site tombé en mer est abandonné
  proprement et ne sera pas retenté.

### Le Relevé — le carnet d'Elias Warrick

- Dans le coffre du Bûcher : **Le Relevé**, le carnet de terrain d'un arpenteur
  qui a passé quatre ans ici. Quinze pages, écrites court, numérotées, corrigées.
  Une page par créature : ce qu'elle est, ce qu'elle fait, et **ce qui marche
  contre elle**.
- **Interface à l'encre** : double page de parchemin peinte au texel, croquis à
  la plume, écriture italique brune, réglure, auréoles d'humidité, coins salis.
  On tourne les pages aux flèches, aux touches, ou en cliquant dans la moitié
  gauche ou droite du livre — le geste que tout le monde essaie sur un livre
  ouvert.
- **Le carnet ne raconte que ce que vous avez vu.** Une créature jamais croisée
  laisse une page délavée : le titre, une seule ligne encore lisible, et le
  reste en taches d'encre diluée. La page se remplit quand la créature est
  réellement devant vous, à portée, dans votre champ de vision et sans obstacle
  — la définition exacte de « voir ». Rien ne se débloque en passant à côté les
  yeux fermés.
- **Quatre pages ont été arrachées** et laissées dans les quatre structures du
  mod. Ce sont les quatre qui comptent : le flash de la caméra contre L'Absent,
  la copie des refuges, **l'immobilité contre L'Écouteur**, et la dernière page.
  Sur la page d'en face, l'ancien avait griffonné où il les avait laissées —
  une devinette, jamais des coordonnées.
- Une page arrachée s'absorbe au clic droit **sans posséder le carnet** : si
  vous la trouvez avant le Relevé, la connaissance vous attend. Rien n'est
  jamais perdu, et une page déjà glissée n'est pas consommée par accident.
- La connaissance est **individuelle et serveur** : le client ne demande jamais
  ce qu'il a le droit de lire, c'est le serveur qui le lui envoie au clic. Céder
  le carnet ne transmet donc rien — en multijoueur, chacun a son propre relevé,
  avec ses propres pages délavées.

### Le carnet se feuillette vraiment

- **Les entrées longues sont paginées.** Une mesure des largeurs réelles a
  montré que le frontispice demandait à lui seul plus du double de la hauteur
  disponible : les trois quarts du texte étaient silencieusement tronqués, et
  la ligne coupée était toujours la dernière — c'est-à-dire la contre-mesure.
  L'écran découpe désormais chaque entrée en autant de feuillets que nécessaire
  (43 au total en police normale, 5 au maximum pour une entrée), et les
  feuillets suivants portent la mention « (suite) » à la place du sous-titre.
- L'**interligne s'adapte à la police** : 10 pixels en police vanilla,
  13 lorsque la police manuscrite optionnelle est active — une TTF dépasse
  largement les neuf pixels du rendu par défaut et ses jambages mordraient
  sinon sur la ligne suivante.

### Divers

- Nouveau son `page_turn` (froissement bref, sans résonance), quinze croquis à
  l'encre en atlas 512×512 et deux textures d'objets peintes au texel.
- Schéma de configuration **v7** : `journalEnabled` et `signalPyreEnabled`.
- 146 nouvelles clés de texte, en français et en anglais.

## [0.17.0] — L'Écouteur, la couche de perception et les profils

### L'Écouteur — la première créature qui ne vous voit pas

- **Elle n'a pas d'yeux.** Deux pavillons d'oreille de 7 px remplacent son
  visage, et à la place des orbites la peau a été tirée puis recousue. C'est la
  silhouette la plus haute du mod (2,9 blocs) et la plus fine : de loin, on la
  prend pour un tronc mort.
- **Elle ne traque que le bruit.** Barème complet et documenté
  (`ListenerNoise`) : immobile = 0 bloc, accroupi en mouvement = 2,5, marche =
  9, sprint = 22 ; frapper ou miner ajoute 7 et s'entend donc **même à
  l'arrêt** ; l'eau ajoute 5, une monture 8, plafond 34 blocs. Un bloc cassé
  porte à 26 blocs, un dégât reçu à 30.
- **L'immobilité totale est un silence total.** C'est un contrat, pas une
  approximation : une portée nulle n'est jamais audible, y compris collé à son
  oreille. Le test `ListenerNoiseTest` le vérifie sur toutes les distances de 0
  à 40 blocs. Le joueur peut apprendre à disparaître — c'est tout l'intérêt.
- **Elle se trompe.** Erreur de visée de ±1,6 bloc : elle vient vers le bruit,
  pas vers vous. Sa tête et son corps sont alignés sur le dernier bruit entendu
  et **jamais sur le joueur** : la règle du jeu s'apprend en la regardant, sans
  une ligne de texte.
- **Quatre postures lisibles** : `SEEKING` (marche lente, bras absolument morts
  — ils ne balancent pas d'un degré), `LISTENING` (immobilité minérale, balayage
  de tête de ±32° sur 3,5 s, tics d'oreille), `CLOSING` (torse cassé à 35°,
  tête rejetée en arrière pour tendre les pavillons vers l'avant, mâchoire
  grande ouverte), `LEAVING` (elle renonce, s'éloigne et se dissipe au loin —
  jamais devant vous).
- **Le verrouillage se casse.** Un bruit franc à moins de 6,5 blocs la fait
  fondre sur vous ; se figer pendant 34 ticks lui fait perdre le fil et la
  remet à l'écoute — parfois à un bloc de votre visage. Ce temps mort est le
  vrai sommet de la scène.
- **Contre-jeu réel** : casser un bloc au loin puis se figer ailleurs est un
  leurre parfaitement valide, et `sound_bait` produit le même type de bruit
  qu'elle ira fouiller. La frapper est le pire choix possible : le coup est un
  bruit, et elle sait alors exactement où vous êtes.
- **Garde-fous** : aucune modification de bloc, aucune téléportation, aucun
  chargement forcé de chunk, durée de vie de 100 s, jamais sauvegardée en cours
  de scène, une scène par joueur et six au total, cooldown individuel de six
  minutes, stade narratif ≥ 2. Le contact ne frappe que le propriétaire de la
  scène et respecte `listenerCanHarm`.
- **Quinzième visage de screamer** (`screamer_listener.png`, 1024×1024) et
  quatre sons dédiés : clic d'écholocation, inspiration d'écoute, verrouillage,
  raclement de départ.

### Couche de perception — l'état mental devient une image

- **Grain et lignes de balayage** dosés par la tension (rien en CALM, maximal en
  CRISIS). Les positions du grain ne changent que tous les trois ticks et les
  alphas restent sous `0x24` : c'est une texture d'image usée, pas un
  clignotement. Le faux calme reste volontairement propre — c'est ce qui le rend
  menteur.
- **Silhouette rémanente périphérique** : quand une vraie manifestation Penumbra
  se trouve à moins de 40 blocs **et hors du champ de vision**, une ombre
  humanoïde apparaît quatre ticks du bon côté de l'écran. Elle ne mente jamais :
  si le joueur se retourne, la chose est là. Un indice honnête déguisé en
  hallucination, plafonné à une occurrence toutes les 8 secondes.
- **Pulsation du voile** : en CRISIS, l'assombrissement périphérique respire
  désormais au rythme du battement cardiaque (2 s) au lieu de rester constant.
- Trois options séparées (`perceptionGrainEnabled`,
  `peripheralAfterimagesEnabled`, `perceptionIntensity`) pour permettre un mode
  mouvement réduit sans couper le reste.

### Quatre événements bâtis sur le son

- **`held_breath`** : le monde inspire, puis se tait cinq à sept secondes —
  aucun son de Penumbra. À l'échéance, une seule expiration tombe deux blocs
  derrière la nuque du joueur, recalculée à l'instant même. Pas de cri, pas de
  screamer, pas de tremblement : le joueur se retourne de lui-même.
- **`wrong_footfall`** : pendant huit à onze secondes, chacun de vos pas est
  suivi d'un second, décalé de trois à cinq ticks et posé deux blocs derrière.
  S'arrêter fait taire le double — la même leçon que L'Écouteur.
- **`sound_bait`** : cinq à sept coups de pioche à douze ou vingt-deux blocs,
  puis plus rien. Aucun bloc touché, aucun screamer à l'arrivée : il n'y a
  rien. Le trajet, lui, vous a éloigné de votre refuge.
- **`listener_passage`** : L'Écouteur traverse le champ à trente ou quarante
  blocs, déjà occupé par un bruit qui n'est pas vous, tête tournée ailleurs.
  Une présentation : le joueur comprend la règle avant sa première vraie
  rencontre.

### Profils d'accessibilité (feuille de route « après 0.13.0 »)

- `/penumbra profile <gentle|standard|nightmare>` applique un préréglage et le
  sauvegarde. Les cinquante autres réglages fins et toutes les préférences non
  citées par le profil sont conservés tels quels ; le mode streamer et les
  bornes de `sanitize()` continuent de primer.
- **doux** : intensité 0,55, grâce de 7 minutes, aucun screamer, aucun flash,
  aucun tremblement, aucune distorsion, couche de perception coupée,
  poursuites coupées, L'Écouteur inoffensif au contact.
- **standard** : le rythme de référence, inchangé.
- **cauchemar** : intensité 1,8, fréquence 2,2, grâce de 30 secondes,
  perception à 1,6.

### Refonte visuelle

- Les **huit dernières textures d'objets provisoires** sont repeintes au texel
  près : sceau de cendre ébréché à glyphe incisé, fil à plomb noirci qui ne
  pend pas droit, tirage instantané avec une silhouette de trop, carnet dont il
  manque des cahiers, tesson gravé de bâtons de comptage dont le dernier est
  inachevé, verre cerclé contenant une ombre sans reflet, lentille fendue,
  disque « Mémoire » griffé. Contour sombre, trois valeurs de modelé, une seule
  source de lumière en haut à gauche (`tools/gen_narrative_items.py`).
- `listener.png` : atlas 64×64 calqué texel par texel sur `ListenerModel`,
  occupation vérifiée sans recouvrement (`tools/gen_listener_assets.py`).

### Divers

- Schéma de configuration **v6** : six nouvelles options, migration automatique
  des fichiers v5 avec sauvegarde préalable.
- `/penumbra listener test` force une apparition réelle sans chance ni cooldown.
- `validatePenumbraResources` vérifie désormais aussi `screamer_listener.png`
  (1024×1024) et `entity/listener.png` (64×64).

## [0.16.0] — Les Échos, la Maison d'en face et la Confrontation

### Les Échos (moments personnels)

- **La photo prise pendant votre sommeil** (one-shot, stade ≥ 4) : au réveil,
  une vraie photographie apparaît dans l'inventaire ET dans l'album — votre
  lit, cadré depuis l'embrasure, une silhouette pâle d'un pixel dans le noir.
  Pixels peints par `FauxPhotoPainter` au format exact de l'album.
- **La photo intruse** (one-shot, stade ≥ 5) : un cliché que personne n'a pris
  se glisse dans l'album — votre bâtisse vue de dehors, de nuit, une seule
  fenêtre allumée. Aucun message : elle attend d'être découverte.
- **Le rapport nocturne** : le Journal incomplet écrit désormais le bilan de la
  dernière nuit (« il s'est approché trois fois. Au plus près : quatre
  blocs. ») depuis une vraie veille de distance des Hôtes. Peur rétroactive.
- **L'écho de tes actions** (`action_echo`, stade ≥ 3) : portes, trappes et
  coffres manipulés sont notés puis rejoués — mêmes sons vanilla, même ordre,
  rythme compressé — depuis un point dans votre dos. Mémoire consommée après
  rejeu : jamais deux fois le même écho.
- **La fausse pluie** (`false_rain`, stade ≥ 3, nuit) : il pleut — pour vous
  seul. Paquet météo individuel, le monde reste sec, retour honnête à l'état
  réel. (Remplace l'idée de fausse aube : 26.1 a déplacé le temps dans
  WorldClock, l'horloge n'est plus falsifiable proprement par joueur.)
- **Le disque « Mémoire »** : trouvable dans la Maison décalée. Une berceuse à
  boîte à musique qui se désaccorde lentement, quelque chose de chuchoté à
  l'envers dessous, et une fin qui s'arrête au milieu d'une phrase (78 s,
  `tools/generate_disc.py`, chanson jukebox data-driven).

### La Maison d'en face

- **L'occupant** (one-shot, stade ≥ 5) : si une copie imparfaite de votre
  refuge est encore debout, l'Hôte s'y tient désormais — DORMANT, dos à vous,
  immobile comme une statue. Le regarder le fera disparaître. Comme toujours.
- **Les villageois savent** (one-shot) : au moins trois villageois se figent et
  fixent simultanément le même point — six blocs derrière vous. Aucun son.
  Trois secondes.
- **Le faux départ** (one-shot, multijoueur, stade ≥ 4) : « X a quitté la
  partie » — alors que X est à côté de vous. Seule la victime voit le message ;
  X « revient » 30–60 s plus tard. Gated par `metaMessagesEnabled`.

### La Confrontation (fin d'histoire jouable)

- Au stade final, le rituel du Sanctuaire **convoque** au lieu de libérer :
  votre propre copie (l'Absent portant votre skin) reflète chacun de vos
  déplacements par symétrie autour du cercle de cendre. Intouchable en mêlée —
  la seule arme est celle de tout le mod : **trois flashs de la Caméra VHS**
  correctement cadrés. Son murmure descend d'un ton à chaque flash.
- Victoire : dissolution en cendres, badge `confrontation`, longue accalmie
  réelle (40 min) et l'épilogue honnête du mod : il réapprendra. Fuite, mort ou
  déconnexion : la copie s'efface sans un mot, le rituel peut être retenté.
- Géométrie du reflet pure et testée (`ConfrontationMathTest`) : la copie se
  tient toujours exactement aussi loin du centre que vous, et ne peut jamais
  vous atteindre.

### Divers

- **Mode streamer** (`streamerMode`) : force flashs coupés, messages méta
  coupés (les faux joueurs ressemblent à de vrais spectateurs) et plafonne le
  volume des jumpscares à 0,7.
- `CopyTracker.sitesFor` expose les sites de copies debout ; tests `EchoesTest`
  (photos peintes, veille nocturne, écho d'actions).

## [0.15.0] — Le Faux-Semblant

### Nouvelle présence : le Faux-Semblant

- **Aucune entité custom** : le serveur fait apparaître une <em>vraie</em>
  créature vanilla — vache, cochon, mouton, poule, zombie la nuit — dans le dos
  du joueur, et la pilote (`FauxMobManager`). Elle est authentique pour tous
  les joueurs ; c'est le doute qui est l'arme.
- **Trois défauts minuscules** : muette (`SILENT`), fixée en permanence sur le
  joueur (`STARE`), ou figée face à un point sans intérêt (`FROZEN`). Et des
  **copies parfaites** (`NONE`), indiscernables, dont la part croît avec le
  nombre de rencontres vécues (15 % → 45 %) : à force d'apprendre les défauts,
  c'est l'absence de défaut qui effraie.
- **Jamais touchable** : approche à moins de 2,5 blocs, coup, interaction
  (traite, tonte…) ou flash de la Caméra VHS → dissolution en cendres avec
  chuchotement individuel et gain de tension. Un regard soutenu (~4,5 s) force
  une copie défectueuse à rendre le regard, puis à s'effacer. Les parfaites,
  elles, ne réagissent jamais au regard : c'est leur définition.
- **Règles dures héritées du mod** : aucun NoAI (immobilité par lenteur vanilla
  temporisée : un crash ne fige jamais un animal définitivement) ; expiration
  silencieuse uniquement hors du regard ; l'événement `faux_creature` est
  annulé sans effet si aucun sol honnête n'existe. Fort en FALSE_CALM — un
  cochon paisible est exactement ce qu'un faux calme promet.
- Options : `fauxCreaturesEnabled`, `fauxCreatureZombieEnabled`. Commandes :
  `/penumbra fauxmob test|perfect|silent|stare|frozen|status`.
- Tests : `FauxMobRulesTest` (portes zombie, croissance des parfaits,
  cohérence des seuils, timeout jamais observé).

### Dialogues de l'Absent réécrits (3 → 28 répliques)

- Les leurres écrits dans le tchat passent de 3 répliques figées à **24
  répliques en trois paliers**, tirées au hasard sans répétition d'ambiance :
  le premier message ressemble à un vrai joueur qui écrit vite (« viens voir
  un truc », « t'es où ? viens »), le deuxième insiste un peu trop
  (« pourquoi tu t'arrêtes ? », « laisse la torche. viens comme ça. »), le
  troisième n'est plus tout à fait humain (« viens viens viens viens »,
  « j'ai presque ton visage. viens le voir. »).
- **4 répliques de révélation** au lieu d'une (« merci d'être venu. »,
  « la prochaine fois, c'est moi qui viendrai. »).
- Traductions FR/EN/CH alignées ; clés `absent.lure.<palier>.<n>` et
  `absent.reveal.<n>`.

### Preuve photographique du Faux-Semblant

- Nouvelle catégorie de preuve `FAUX_SEMBLANT` (10ᵉ du dossier) : cadrer une
  créature possédée à la Caméra VHS avant sa dissolution inscrit sur le
  négatif « l'animal qui n'en était pas un ». Le détecteur s'exécute avant la
  dissolution du flash : la photo devient la seule vérification fiable d'un
  animal suspect — et la preuve qu'il n'a jamais existé.

### Sons refaits (29 fichiers, 100 % de l'audio), avec variantes anti-répétition

- **19 fichiers resynthétisés** (`tools/generate_sounds.py`, déterministe,
  mono 44,1 kHz pour l'atténuation spatiale) : sound design en couches —
  cri à trois étages (voix inharmonique, déchirure formantée, impact sourd),
  chuchotements articulés en syllabes, souffles voilés inspiration/expiration,
  stingers en grappe dissonante avec résonance métallique, départ de poursuite
  avec montée et pulsation, cœur lub-dub bouclable, trois nappes d'ambiance de
  ~22 s bouclables par fondu croisé, sous-basse du « silence ».
- **Variantes par événement** tirées au hasard par vanilla : 3 cris, 3
  chuchotements, 2 souffles, 2 stingers. Combinées au jitter de pitch client
  (0.14.0), deux screamers consécutifs ne sonnent plus jamais pareil.
- Les nappes longues et le drone passent en `stream: true`. Anciens fichiers
  sauvegardés dans `build/backup_sounds/`.

### Visages de screamers définitifs

- Les 14 visages plein écran générés selon `docs/PROMPTS_SCREAMERS.md` sont
  installés (1024×1024, bords vignettés au noir) : 14 plans de la même
  créature, du face-à-face au « presque rien », dont l'Absent en masque de
  peau pixelisé et le « miroir raté » mi-humain mi-copie.

## [0.14.0] — L'usure de la peur

### Anti-habituation des screamers

- **Six mises en scène** pour les visages plein écran (`ScreamerPresentation`,
  client) : plein cadre classique, zoom rampant, cadrage décentré, double coupe
  avec trou noir de 2 ticks, flash subliminal à alpha réduit (chuchotement au
  lieu du cri), et « trop près » qui recule. Rotation sans répétition ; le pitch
  du cri varie à chaque déclenchement. Les finales de contact ne tombent jamais
  sur un style affaiblissant (`finalizeForContact`).
- **Décote serveur** (`ScreamerHabituation`) : chaque visage montré dans les
  5 dernières minutes réduit l'intensité du suivant (plancher 55 %). Passé deux
  visages récents, une part croissante (≤ 35 %) des screamers non physiques est
  convertie en **refus sensibilisant** — un souffle à la place du cri promis —
  et un plancher d'espacement de 25 s s'applique. Les finales de contact
  (`lunge`, `rampant`, `absent`) ne sont jamais refusées, seulement décotées.
  Mémoire de session uniquement, purgée à la déconnexion et à l'arrêt.
- Nouvelles options : `screamerVarietyEnabled`, `screamerHabituationEnabled`
  (activées par défaut ; les options d'accessibilité existantes priment toujours).
- Anti-doublon : un visage partagé entre la rotation générale et les apparitions
  ne peut plus être remontré immédiatement.

### Identité visuelle et modèles

- **L'Hôte remodelé** : cou étiré, mains articulées aux doigts trop longs
  (déploiement complet pendant le bond), épaules désaxées, silhouette portée à
  2,75 blocs. **Immobilité de statue** en DORMANT/OBSERVING — il ne respire ni
  ne tressaille tant qu'il observe ; seule sa tête suit le joueur. Son spasme
  utilise `twitchPulse` : instant pseudo-aléatoire à chaque cycle, plus jamais
  prévisible.
- **Skins régénérés** (`tools/generate_skins.py`, déterministe) : identité
  commune « copie imparfaite » — peau cireuse cousue, vestige de tunique
  turquoise, asymétries délibérées. Sept textures d'entités repeintes au texel
  près (l'Absent, qui vole les skins, est inchangé).
- **Pack de prompts screamers** (`docs/PROMPTS_SCREAMERS.md`) : 14 plans du même
  visage — du face-à-face au « presque rien » — à générer, puis
  `tools/import_screamers.py` recadre, vignette au noir et installe.

### Tests

- `ScreamerPresentationTest`, `ScreamerHabituationTest` et 3 cas `twitchPulse`
  ajoutés (bornes, monotonie de la décote, refus interdits sur contact, fenêtre
  glissante, horloge qui recule).

## [0.13.0] — Perceptions individuelles

### Faux compagnon privé

- Le nouvel événement `false_companion` manifeste une copie humanoïde destinée à
  un seul joueur. Elle reprend le profil, le skin, les couches de vêtements,
  l'équipement visible et la démarche d'un compagnon momentanément hors de vue ;
  en solo, le propriétaire lui-même sert de repli.
- La visibilité est filtrée avant l'ajout de l'entité au monde : aucun paquet de
  suivi n'est envoyé aux autres joueurs. Pas, gestes, sons, particules et
  disparition sont eux aussi adressés uniquement au propriétaire de la perception.
- Ce mode reste hermétique au comportement offensif de L'Absent : aucune collision,
  attaque, poursuite, modification du monde, preuve photo ou effet de zone partagé.
  Une déconnexion, une mort ou un changement de dimension nettoie la scène.

### Vérification et direction

- Regarder la copie la rend d'abord consciente de l'observation, puis la force à se
  tourner et à disparaître. Le zoom de la Caméra VHS accélère cette vérification ;
  un flash cadré dissipe immédiatement la manifestation sans l'inscrire comme preuve.
- Le faux compagnon rejoint le pool du HorrorDirector au stade narratif 2. Il
  bénéficie des arcs, de l'intensité, de l'anti-répétition, du quota de manifestations
  et d'un cooldown persistant, sans ajouter une seconde boucle aléatoire concurrente.
- `/penumbra perception companion|status|clear` couvre le forçage, le diagnostic et
  le nettoyage manuel. Les quatre nouvelles options permettent d'activer séparément
  les perceptions, les faux compagnons, leur intervalle et leur plafond serveur.

### Configuration et compatibilité

- Le schéma de configuration passe en version 5 avec
  `individualPerceptionsEnabled`, `falseCompanionsEnabled`,
  `perceptionIntervalSeconds` (60–1800, défaut 240) et
  `perceptionMaxActive` (1–8, défaut 3). La migration conserve automatiquement une
  sauvegarde `penumbra.json.pre-v5.bak` et complète les anciens fichiers sans perdre
  leurs réglages.
- Les textes de perception et de commande sont disponibles avec des ensembles de
  clés identiques en anglais, français et français suisse.

## [0.12.0] — Le monde se souvient

### Directeur adaptatif persistant

- Le directeur orchestre des arcs individuels de 3 à 7 minutes en six phases :
  présage, montée, manifestation, retombée, faux calme, puis rupture. Leur
  thème (grotte, refuge, traque, mimique ou photographie), leurs échéances et
  leurs conséquences longues sont sauvegardés dans le profil du joueur.
- Les anciens profils sont migrés paresseusement en ancrant l'horloge au moment
  du chargement : aucune rafale ni dette instantanée n'est créée sur les mondes
  0.11. Les reculs de `/time` et restaurations de sauvegarde rebasent les délais.
- La dette de manifestation est maintenant stricte : après 90 secondes sans
  apparition, seuls une entité physique ou un repli visuel fiable peuvent la
  solder. L'anti-répétition garde un second passage pondéré et le plafond audio
  empêche plus de deux scènes sonores consécutives.

### Refuges, scènes liées et enquête

- Chaque refuge appris peut atteindre quatre niveaux de contamination persistante.
  Les faux coffres, torches rouges, objets décalés, photos, traces de cendre et
  pièces impossibles utilisent uniquement des affichages temporaires dans des
  chunks déjà chargés : aucun bloc ni inventaire réel n'est modifié.
- Trois chaînes bornées relient L'Absent au Rampant, la Cavaleuse au Rampant et
  le Projectionniste à l'Originel. Elles réservent leur propriétaire et leurs
  témoins proches, s'annulent à la déconnexion et ne forcent jamais un chunk.
- Les actions du joueur comptent : flasher L'Absent, augmenter réellement la
  lumière de la galerie ou ne pas rouvrir la Caméra peuvent briser une chaîne.
  Le dossier photographique déduit ces contre-mesures lorsque les deux preuves
  concernées ont été développées.

### Screamers, commandes et configuration

- Deux visuels VHS 512×512 dédiés au refuge contaminé et à la mimique portent
  la banque à quatorze images validées au build. Le propriétaire reçoit l'effet
  d'écran tandis que le son spatial reste audible aux joueurs assez proches.
- `/penumbra intensity <0-100>` est un curseur maître (50 normal, 100 maximum).
  Les commandes `/penumbra arc status|start|clear` et `/penumbra linked <type>`
  permettent de diagnostiquer les nouveaux systèmes sur une cible explicite.
- Le schéma de configuration passe en version 4 avec sept options d'activation,
  plafond et tempo ; la migration conserve automatiquement une sauvegarde v3.

## [0.11.0] — L'Absent, identité volée

### Imitation complète et comportement

- L'Absent copie désormais le profil complet d'un vrai joueur : texture de peau
  exacte, modèle de bras large ou fin, main dominante, réglages de veste/chapeau/
  manches/pantalon, nom, objets tenus et armure. L'équipement copié est purement
  visuel, ne tombe jamais et disparaît dès que l'imitation se brise.
- Le serveur choisit en priorité un compagnon hors du regard du témoin, avec un
  repli sur le propriétaire en solo. L'Absent apprend une signature compacte de
  sa vitesse, de ses rotations, de ses sauts et de ses accroupissements afin de
  reproduire une démarche reconnaissable sans envoyer chaque mouvement brut sur
  le réseau.
- Sa scène suit maintenant cinq états : imitation, leurre, soupçon, révélation et
  chasse. Il peut regarder un bloc et simuler un coup de pioche ou tendre le bras
  vers une porte, avec le son correspondant, mais ne modifie jamais le monde.
  Rencontrer simultanément le joueur copié accélère fortement la révélation.
- Le regard prolongé ne déclenche plus un screamer instantané : l'erreur de copie
  augmente, le visage et les gestes se désynchronisent, la vraie forme apparaît,
  puis L'Absent poursuit sa cible, bondit dans les derniers mètres et ne lance le
  screamer qu'après un vrai contact de hitbox avec ligne de vue. Les murs bloquent
  donc aussi bien l'attaque que le screamer.

### Flash, preuve et bannissement

- Un flash cadré à moins de **24 blocs**, dans un cône de **70°** et sans obstacle,
  force L'Absent à montrer sa vraie forme, le désoriente brièvement et produit un
  retour sonore spatial que les joueurs proches peuvent entendre.
- Le cône de preuve suit désormais le zoom optique réellement capturé. Le serveur
  accuse réception du déclenchement avant que les pixels soient envoyés : sous
  faible TPS, un flash encore en recharge affiche un diagnostic au lieu de perdre
  silencieusement la photographie.
- Lorsqu'une photo contient L'Absent, une double exposition exclusive se révèle
  progressivement pendant le développement. Les identifiants de preuve restent
  néanmoins secrets jusqu'au verdict du serveur, comme pour les autres anomalies.
- Le Sceau de bannissement ne supprime plus instantanément L'Absent : seul le
  propriétaire de la scène peut l'utiliser et la vraie silhouette demeure visible
  pendant une courte dissolution de cendre avant de disparaître.
- Quatre options permettent de couper séparément L'Absent, la copie de skin,
  l'imitation de démarche et la réaction au flash. La configuration passe au
  schéma 3 avec migration automatique des anciens fichiers.

### Technique et validation

- Correction d'un interblocage serveur réel pendant la génération de chunks :
  le callback `CHUNK_GENERATE` ne lit ni ne modifie plus le monde avant que le
  chunk `FULL` soit publié. Les structures sont mises dans une file bornée,
  dédupliquée, puis construites en fin de tick uniquement via `getChunkNow` ;
  une structure optionnelle ne peut donc plus déclencher le watchdog.
- Les scans d'une copie de refuge, d'une galerie décalée ou d'un faux filon
  refusent maintenant les zones déchargées au lieu de forcer leur chargement
  synchrone. Le registre des structures ignore aussi les doublons d'ancre.
- Les phases, erreurs d'imitation, signatures de démarche et règles du flash sont
  testées par des fonctions déterministes. La suite automatisée couvre désormais
  **118 tests répartis dans 27 suites**.
- Une texture photographique 128×72 dédiée à la vraie forme de L'Absent est
  validée à la compilation, dimensions et transparence comprises.

## [0.10.0] — Dossier d'enquête photographique

### Flash et détection serveur

- La capture est scindée en un ticket `PhotoShutter` et un envoi de pixels
  `PhotoCapture`. Au clic, le serveur fige immédiatement la séquence, l'instant,
  le tirage aléatoire du Projectionniste et les preuves cadrées ; les pixels ne
  sont acceptés qu'avec la même séquence, dans une fenêtre de **60 ticks**.
- Un ticket est consommé atomiquement avant l'archivage : un rejeu, une réponse
  tardive ou une seconde réponse portant la même séquence ne peut jamais créer
  un doublon. Le cooldown, l'état du joueur, la caméra tenue et les quotas
  continuent d'être validés par le serveur.
- Le nouveau détecteur recherche les manifestations Penumbra vivantes jusqu'à
  **64 blocs**, dans le cône central de **36° de demi-angle**, avec les règles
  de visibilité propres au photographe. Il teste les yeux, le centre et le bas
  de la hitbox par rayons visuels pour accepter une partie réellement visible
  derrière une vitre ou un rebord. Il reconnaît
  l'Hôte, l'Observateur, l'Originel, le Contrefait, le Projectionniste, le
  Rampant, L'Absent et la Cavaleuse.
- Une photo peut conserver plusieurs catégories uniques. Elles sont ordonnées
  par importance, centrage puis distance, et le tirage photographique aléatoire
  du Projectionniste peut compléter les manifestations réellement cadrées.

### Développement et dossier

- Les preuves sont gravées dans l'album au flash mais leurs identifiants restent
  retenus côté serveur pendant les **200 ticks** de développement, même face à
  un client modifié. Un verdict réseau léger les livre ensuite sans renvoyer les
  pixels. L'album affiche « Analyse » avec sa
  progression et la visionneuse ne révèle les catégories qu'une fois la photo
  entièrement développée.
- Un dossier persistant distinct est tenu pour chaque propriétaire. Il compte
  les **huit catégories uniques**, sans recompter les doublons, et délivre les
  indices I à IV aux seuils exacts de **1, 3, 5 et 7 découvertes**.
- L'ouverture de l'album réconcilie les tirages déjà développés avec le dossier.
  Une découverte reste acquise après suppression de sa photo source et après
  redémarrage du serveur.

### Album et compatibilité

- L'en-tête de l'album affiche le palier du dossier, le nombre de catégories
  découvertes sur huit et la page courante. Chaque vignette passe de l'analyse
  en cours à « aucune anomalie », au nom de la catégorie ou au nombre
  d'anomalies détectées.
- Le propriétaire peut marquer ou démarquer une photo **Important**. Les
  favoris sont regroupés en tête uniquement dans « Mes photos », puis triés du
  plus récent au plus ancien ; l'onglet public demeure purement chronologique.
- Les pages refusées silencieusement par un limiteur ou perdues sont retentées
  au plus trois fois à 1,5 seconde d'intervalle, puis l'album sort de l'état de
  chargement avec un message explicite.
- `evidence`, `important` et `dossiers` sont optionnels dans le codec persistant.
  Les albums 0.6.4–0.9.0 restent lisibles, les anciens tirages ordinaires ne
  reçoivent aucune preuve inventée et un ancien marqueur `projectionist`
  conserve sa signification.
- La couverture automatisée inclut les limites de cadrage, les priorités, les
  seuils du dossier, les preuves multiples, la migration des anciens codecs et
  les deux ordres de tri de l'album.

## [0.9.0] — Chambre noire et peur coopérative

### Album photo

- Un clic droit avec la Caméra VHS ouvre une galerie complète, divisée entre
  les tirages du joueur et les photos rendues publiques. Elle affiche six
  vignettes par page, charge les pixels à la demande et conserve un cache LRU
  borné à 128 textures.
- Les propriétaires peuvent agrandir et renommer un tirage, le publier ou le
  rendre privé, puis le supprimer après une seconde confirmation. Une
  suppression libère immédiatement le quota et invalide la texture chez tous
  les clients ; un retour en privé révoque les lecteurs qui ne possèdent aucun
  tirage physique ni cadre autorisé.
- Les métadonnées `title` et `public` sont optionnelles dans le codec : tous les
  albums plus anciens restent valides, sans publication rétroactive.
- Les listes n'envoient jamais les pixels. Requêtes d'image, pages et mutations
  possèdent chacune leur limiteur serveur ; tailles, actions, titres,
  propriétaire et présence de la caméra sont revalidés côté serveur.
- Correction de la visionneuse grise : l'appel 26.1.2 utilise maintenant des
  coordonnées `x0/y0/x1/y1` et des UV `u0/u1/v0/v1` corrects. L'état
  « terminée » n'est plus affiché tant que la texture n'a pas été reçue.

### Scènes multijoueur

- `behind_other` anime une traversée d'Hôte derrière le compagnon. L'entité est
  liée au témoin avant son ajout au monde, invisible pour les autres clients,
  non sauvegardée et supprimée si le duo devient invalide.
- `paired_echo` envoie deux moitiés sonores synchronisées et différentes au
  témoin et à son compagnon, puis revalide connexion, dimension et distance
  avant le second battement.
- `stolen_name` donne à L'Absent le nom exact d'un joueur proche tout en gardant
  le témoin comme unique propriétaire et cible. Les trois scènes conservent un
  repli solo et participent à la coordination de groupe automatique.

### Graphismes et animations

- Nouvelles courbes d'animation bornées : respiration organique, proximité,
  anticipation, projection et contraction d'impact. Elles pilotent les modèles
  du Rampant, de L'Absent, du Projectionniste, de l'Observateur, de l'Originel
  et du Contrefait via leurs render states réels.
- Le Rampant articule désormais ses segments de pattes et ses épines selon la
  course, l'accroche, la montée, la chute et le bond ; son renderer interpole
  aussi l'orientation paroi/plafond et l'ombre de chute.
- Trois screamers VHS 512×512 supplémentaires : Rampant, Absent et Chœur. La
  rotation générale compte douze images, tandis que les impacts du Rampant et
  de L'Absent sélectionnent leur propre finale. Les sources ImageGen et le
  script de réduction sont conservés dans `tools/image_sources/` et `tools/`.

### Validation

- 97 tests unitaires couvrent notamment album, quotas, codec rétrocompatible,
  titres/visibilité, rate limits, scènes de duo, ciblage et courbes d'animation.
- Validation Gradle des trois nouveaux PNG et synchronisation stricte des clés
  `fr_fr`, `fr_ch` et `en_us`.

## [0.8.1] — Coordination multijoueur

### Modifié

- Les scènes automatiques les plus lourdes utilisent maintenant une
  coordination locale : dans un rayon de **32 blocs**, une seule scène peut
  commencer pendant **4 secondes**. L'ordre de sélection tourne entre les UUID
  connectés afin qu'un même joueur ne monopolise pas les manifestations.
- La coordination reste locale à une dimension et à un groupe. Les joueurs à
  plus de 32 blocs et ceux présents dans une autre dimension continuent leurs
  scènes indépendamment ; le comportement solo reste inchangé.
- Les gestionnaires naturels de l'Originel, du Contrefait, du Rampant, de
  L'Absent et de la Cavaleuse partagent la même réservation de scène et
  échelonnent leurs sondages. Les commandes opérateur de forçage contournent
  volontairement ce délai pour rester utilisables comme outils de test.
- Les commandes de statut, tension, événements, apparitions, jumpscares,
  poursuite et jumelles acceptent désormais un joueur cible optionnel en dernier
  argument. Elles fonctionnent donc pour un ami ou depuis la console, y compris
  si la cible se trouve dans une autre dimension.
- Le ciblage des créatures est strictement lié au propriétaire de la scène.
  Les manifestations physiques partagées ne peuvent pas acquérir un autre
  joueur comme cible ; les visuels de jumpscare restent individuels.
- Le partage compagnon des sons positionnés est limité aux joueurs de la même
  dimension situés à **24 blocs ou moins** de la cible. Le filtre distingue la
  dimension, la source et l'auditeur, limite les sons ordinaires à un paquet
  tous les 3 ticks et supprime aussi les doublons prioritaires identiques d'un
  même tick sans leur retirer leur voie d'urgence.
- Les paramètres réseau de son et de jumpscare sont validés et bornés avant
  envoi et à la réception. Les états temporaires de routage, de scène et de
  cooldown client sont purgés à la déconnexion ou à l'arrêt du serveur.
- L'Observateur et les autres manifestations courtes ne sont plus sauvegardés
  à mi-scène ; ils disparaissent proprement si leur propriétaire part. Le Sceau
  de bannissement ne peut plus révéler ni supprimer l'Originel ou le Contrefait
  privé d'un autre joueur.
- Les créations d'entités vérifient leur ajout réel au niveau avant de consommer
  une scène ou un cooldown. La visionneuse d'une photo hantée réessaie aussi de
  façon bornée si sa première requête n'a pas pu aboutir.

### Photos et compatibilité

- Le quota persistant de l'album passe de **512 à 2 048 photos par monde** ; le
  plafond individuel reste **128 photos par joueur**. Chaque joueur conserve sa
  propre part même lorsque plusieurs photographes remplissent le même album.
- Aucune migration manuelle n'est nécessaire : mondes, configurations, photos
  et cadres des versions précédentes restent compatibles.

## [0.8.0] — Les surfaces répondent

### Ajouté

- **Sceau de bannissement consommable** (`penumbra:ash_ward`) : recette sans forme
  papier + charbon de bois + redstone, pour un résultat de deux objets. À
  l'utilisation, il bannit les entités `Monster` du namespace `penumbra`
  situées dans un rayon de 16 blocs, sans affecter joueurs, animaux, cadres
  photo ni monstres vanilla. Il n'est consommé qu'en cas de bannissement
  réussi. L'ancien Sceau de cendre (`penumbra:ash_sigil`) du rituel reste un
  objet distinct.
- **La Cavaleuse** (`penumbra:scuttler`), petite araignée sans attaque, dégâts,
  butin ni riposte. Une scène naturelle traverse le champ de vision dans 70 %
  des cas ; dans les 30 % restants, elle suit les pieds du joueur pendant
  3 à 5 secondes. Commandes opérateur : `/penumbra scuttler cross` et
  `/penumbra scuttler chase`.
- Trois screamers 512×512 supplémentaires portent le sac mélangé à neuf
  variantes : Projectionniste (7), Rampant (8) et Absent (9). Le cycle complet
  et l'absence de répétition immédiate sont conservés.

### Modifié

- Le Rampant rejoint maintenant une paroi réelle, l'escalade et peut passer au
  plafond sans téléportation ni modification de bloc. Après une attaque, il
  cherche de nouveau à se percher ; une lumière forte, la perte du support ou
  une tentative bornée le font redescendre ou fuir proprement.
- Son modèle et son renderer 3D disposent de poses articulées dédiées à la
  course, la fuite, l'accrochage, l'escalade, la chute et le bond, avec une
  orientation cohérente sur les murs et plafonds.
- Le sac de screamers reste soumis à `screamersEnabled`, aux cooldowns et au
  plafond de volume existants. La petite intrusion `apparition` conserve son
  groupe spécialisé de variantes 4 à 6.

### Assets et validation

- Les concepts du Sceau de bannissement, de la Cavaleuse et des screamers 7 à 9
  ont été créés
  avec l'ImageGen intégré, cas d'usage `stylized-concept`. Les sources de
  travail restent dans `tools/image_sources/` et leur provenance ainsi que les
  prompts résumés sont consignés dans `docs/ASSETS.md`.
- `tools/process_080_assets.py` produit de façon déterministe l'icône RGBA
  64×64 du Sceau de bannissement, l'atlas UV 64×64 de la Cavaleuse et les trois
  screamers
  carrés 512×512. La validation Gradle contrôle ces sorties et les neuf images
  plein écran.

## [0.7.0] — Trois présences

### Ajouté

- **Le Projectionniste** (`penumbra:projectionist`) marque environ 15 % des
  nouvelles captures, sur un tirage décidé exclusivement par le serveur. Sa
  silhouette apparaît progressivement dans l'image pendant le développement.
  Une photo entièrement développée, tenue en main et observée 1,5 seconde dans
  la visionneuse peut provoquer une manifestation derrière le joueur : regard
  fixe de 4 à 6 secondes, courte avancée puis dissolution sans dégâts. Le
  cooldown est de 90 secondes par joueur.
- **Le Rampant** (`penumbra:rampant`) se fixe aux plafonds et parois réels des
  grottes sombres. Il se décroche, chasse au ras du sol, bondit entre 2,4 et
  8 blocs et déclenche un screamer au premier impact. Une lumière forte
  interrompt la chasse et le force à chercher l'ombre ou à disparaître. Les
  apparitions naturelles sont espacées d'au moins 6 minutes par joueur.
- **L'Absent** (`penumbra:absent`) emprunte le nom d'un joueur connecté, ou une
  variante inquiétante du nom de sa cible en solo. Il observe, envoie des
  messages leurres, se retire, puis révèle sa posture et charge si le joueur
  le suit. Sa rencontre naturelle possède un cooldown de 8 minutes par joueur.
- Commandes opérateur dédiées : `/penumbra projectionist test`,
  `/penumbra rampant test` et `/penumbra absent test`.

### Compatibilité, multijoueur et assets

- Les anciennes photos restent lisibles : le marqueur `projectionist` est
  optionnel dans les objets et l'album persistant, avec `false` par défaut.
  Une photo 0.6.4 ne gagne donc jamais rétroactivement une apparition.
- Les trois monstres sont des entités physiques visibles par les témoins à
  portée, mais conservent un propriétaire et une cible uniques. Dialogues,
  obscurité, dégâts et visuels de screamer restent ciblés ; souffles, pas,
  voix et cris sont des sons de proximité audibles par les joueurs voisins.
- Trois concepts originaux ont été créés avec ImageGen en mode
  `stylized-concept`, puis détourés et convertis en atlas UV 64×64. Le script
  déterministe `tools/gen_trio_monster_assets.py` produit les textures
  `projectionist.png`, `rampant.png`, `absent.png` et l'overlay photo
  `projectionist_photo.png`.

## [0.6.4] — La chambre noire

### Ajouté

- Système photo complet pour la Caméra VHS : lorsqu’elle est tenue, un clic
  gauche capture réellement le centre de l’image en 128×72, avec ou sans le
  viseur C, et remet une photographie instantanée au joueur.
- Développement chimique visible sur 10 secondes : tirage d’abord noir, puis
  révélation faible, sépia et enfin couleur. La transformation continue dans
  l’inventaire et le rendu détaillé progresse par paliers fins.
- Visionneuse plein écran au clic gauche sur une photographie tenue, avec cadre
  papier, scanlines et état de chargement si les pixels doivent être redemandés
  au serveur.
- Petit cadre photo mural original. Il peut être posé vide puis garni d’un
  tirage, ou créé directement en faisant un clic droit sur un mur avec la photo.
  Recette simple : un cuir au centre et quatre bâtons en croix. Son rendu possède
  une vraie épaisseur, reste plaqué au mur et présente toujours sa face vers la pièce.
- La vraie capture apparaît désormais directement sur le tirage dans l’inventaire,
  dans les deux mains et au sol ; son développement animé n’est plus une simple
  image générique.
- Album persistant propre au monde : les objets et cadres ne transportent qu’un
  identifiant léger, les pixels restent sauvegardés côté serveur et survivent au
  redémarrage.

### Multijoueur et garde-fous

- Le flash de prise de vue est partagé jusqu’à 64 blocs et son intensité baisse
  avec la distance ; le déclic reste un son spatial, sans paquet envoyé aux
  joueurs trop éloignés.
- Les captures sont acceptées seulement si le joueur est vivant et tient la
  caméra. Un cooldown de 40 ticks empêche le spam réseau.
- Taille réseau fixe de 27 648 octets par photo, chargement à la demande et
  accès borné aux photos possédées ou exposées à proximité.
- Quotas persistants : 128 photos maximum par joueur et 512 par monde. Si
  l’inventaire est plein, le tirage est déposé au sol sans perdre la capture.
- Nouvelles textures originales ImageGen pour le tirage et le cadre, déclinées
  en sprites pixel-art cohérents avec la Caméra VHS.
- Traduction française complète pour la locale suisse `fr_ch` : dialogues,
  journal, panneaux, sous-titres, entités, objets et messages ne retombent plus
  sur l'anglais. Le build impose sa synchronisation avec `fr_fr`.

## [0.6.3] — Le regard répond

### Ajouté

- Marteaux 4×4 en pierre, fer, or et diamant, chacun avec texture, modèle,
  recette, niveau d'outil, vitesse, réparations et durabilité propres. L'ancien
  `penumbra:hammer` reste le marteau en fer pour préserver les sauvegardes.
- Totem hanté invocateur de l'Originel : colonne or/sable des âmes/citrouille
  sculptée, croix de pierre noire polie et quatre torches de redstone, activée
  par le Sceau de cendre.
- Apparition caméra dédiée de l'Originel après 3 secondes de zoom continu,
  visible à 10–28 blocs et limitée à une scène toutes les 90 secondes.
- Animations client synchronisées de bond pour l'Hôte et le Contrefait.

### Modifié

- Durabilités des marteaux : pierre 1 536, or 1 024, fer 4 096, diamant 12 288.
- L'Originel naturel revient toutes les 4–7 minutes à fréquence normale et se
  place à 28–52 blocs au lieu de rester presque invisible à l'horizon.
- Hôte, Observateur, Originel et Contrefait frappent maintenant à la fin de leur
  bond 3D, puis déclenchent le screamer puissant. Celui-ci remplace correctement
  une intrusion visuelle critique récente au lieu d'être avalé par son cooldown.
- Le son des attaques et screamers reste spatial : cible en local, joueurs
  proches seulement via le réseau, aucun paquet envoyé aux joueurs lointains.

## [0.6.2] — Visages dans le noir

### Ajouté et amélioré
- Quatre nouvelles images de screamer 512×512 portent le total à six : Hôte
  asymétrique, Observateur sans visage, Originel pâle et Contrefait presque humain.
- Les variantes passent dans un sac mélangé : chaque image est vue une fois avant
  répétition, sans doublon immédiat, et le rendu carré conserve ses proportions
  sur les écrans 16:9 et ultralarges.
- Le jumpscare `apparition` possède désormais une brève intrusion visuelle plus
  discrète que le screamer complet, toujours coupée par `screamersEnabled` et
  prioritaire sur les petits sons déjà en cooldown.
- Nouvelle apparition souterraine `cave_face`, fiable même dans les galeries trop
  basses pour accueillir l'entité Hôte.
- L'Originel apparaît plus régulièrement et dispose maintenant d'une scène de
  grotte : il se place devant sa cible, pose une torche de redstone temporaire,
  la fixe plusieurs secondes puis disparaît si elle détourne le regard.
- Un regard trop soutenu provoque désormais une vraie réaction animée de
  l'Originel ou de l'Observateur : posture tendue, bras levés, bond 3D vers la
  caméra puis screamer ciblé, sans dégâts pour le joueur.
- `mining_bait` reconnaît le marteau, se déclenche plus naturellement et ne remet
  plus à zéro la garantie 3D tant qu'un Hôte n'est pas réellement apparu.
- Les callbacks de scares désarmés sont annulés correctement et la commande
  `/penumbra jumpscare screamer` permet de tester le rendu complet.
- Les nouvelles commandes `/penumbra originel test` et
  `/penumbra jumpscarefrequency <0-200>` rendent les scènes et leur fréquence
  testables sans confondre intensité visuelle et cadence.
- Le build vérifie maintenant l'existence, le décodage et les dimensions des six
  images plein écran.

## [0.6.1] — Caméra VHS, marteau et son spatial

### Ajouté et modifié
- Les anciennes jumelles deviennent visuellement une caméra VHS : nouvelle texture
  64×64, modèle cubique tenu en main et viseur 4:3 avec REC, timecode, batterie,
  facteur de zoom, scanlines, grain analogique et dropouts accessibles.
- Ajout du `hammer`, marteau de mineur niveau fer (1024 durabilité) qui casse un plan
  4×4 selon le regard via la casse vanilla. Il respecte protections, chunks chargés,
  blocs incassables, Fortune, Silk Touch, drops et XP. Recette : 5 fer + 2 bâtons.
- Les sons positionnés sont maintenant entendus par tous les joueurs assez proches
  dans la même dimension, avec atténuation vanilla et aucune diffusion aux lointains.

## [0.6.0] — Phase 6 (peur personnalisée : le mod utilise sa mémoire du joueur)

### Correctif apparitions et ciblage
- La tension à 100 débloque désormais immédiatement les manifestations, contourne
  la grâce restante et provoque une nouvelle sélection sous cinq secondes.
- La grâce par défaut passe à 2 minutes, l'intervalle entre événements à 35 secondes
  et une apparition physique devient prioritaire après 2 minutes sans manifestation.
- En grotte, `tunnel_silhouette` utilise un parcours borné dans les galeries réellement
  connectées au joueur au lieu de chercher une ligne droite presque impossible dans la roche.
- Les Hôtes peuvent poursuivre dès les hauts niveaux de tension ; à CRISIS, une silhouette
  de tunnel valide déclenche systématiquement la poursuite.
- Les commandes, cibles, disparitions et plafonds de monstres sont filtrés par
  propriétaire : un joueur ne vole plus l'événement ou le monstre d'un autre.
- `eventFrequency` accepte maintenant 0–400 %, avec `/penumbra frequency`,
  `/penumbra intensity` et `/penumbra reload` pour appliquer les réglages en jeu.

### Ajouté
- **5 événements personnels** (`personalEventsEnabled`, pool du directeur) :
  - `echoed_path` — des pas rejouent le PROPRE trajet du joueur (ruban de
    positions du profil, 1 échantillon/s sur 90 s), ~40 s derrière lui ;
  - `absent_home` — la porte de SON refuge grince au loin (son positionné au
    refuge réel, volume 3.0 → portée étendue) ; le retour paniqué est
    l'événement ;
  - `door_knocks` — trois coups sourds, la nuit, sur la vraie porte du refuge
    (scan borné, annulé sans porte) ;
  - `binoculars_reveal` — un Hôte invisible à l'œil nu, visible UNIQUEMENT
    aux jumelles (invisibilité vanilla pilotée par l'état de zoom C2S) ; au
    dézoom après révélation : cendre, murmure, +8 tension ;
  - `behind_other` — multijoueur : le joueur voit l'Hôte debout derrière un
    AUTRE joueur (spawn dans le dos de l'autre, dissolution après 4 s).
- **Moments personnels** (`PersonalMoments`) : au réveil, 18 % de chance qu'un
  coffre à ≤ 8 blocs soit RETRIÉ (rien de volé, rien de perdu — l'ordre
  seulement ; ≤ 1/jour de jeu) ; **one-shots par monde** persistés
  (`oneShots` dans l'état monde) : `bed_shift` (un mouvement est entendu sous
  le lit sans déplacer le bloc ni le point de réapparition), `animal_gaze`
  (tous les animaux du rayon fixent le
  joueur simultanément 3 s), `fake_join` (`metaMessagesEnabled`, solo
  uniquement : faux « X a rejoint la partie » vanilla avec le pseudo du
  joueur à deux lettres échangées, départ 60–120 s plus tard — clés vanilla,
  rendues dans la langue du client, français inclus).
- **Journal incomplet lisible** (`JournalItem.use`, signature 26.1.2 vérifiée
  au bytecode) : au clic droit, des lignes générées depuis les VRAIES
  statistiques du profil (nuits, regards arrière, minutes sous terre,
  événements remarqués), en gris italique — clés `penumbra.journal.*`,
  **françaises** dans fr_fr.json. Le narrateur, c'était l'Hôte.
- **Coupure de musique** : à l'entrée en HUNT/CRISIS, la musique vanilla
  s'arrête net (`MusicManager.stopPlaying()`, vérifié au bytecode ; gate
  `improvedSoundsEnabled`, une coupure par transition).
- **Peur contagieuse** (multijoueur, `multiplayerContent`) : un joueur en
  CRISIS fait entendre sa nappe haute, très faible (0.15), aux joueurs à
  ≤ 24 blocs — on ne sait pas ce qu'il vit, on sait que ça se passe.

- **`mining_bait` — LE screamer au retournement** (`screamersEnabled`) : sous
  terre, pioche en main — trois coups de pioche retentissent DERRIÈRE le
  joueur (± une voix « HÉ » à haute tension). Il se retourne : rien. Dès que
  son regard revient à son cap initial, l'Hôte se tient à 3 blocs face à lui,
  hurlement (`screamer_shriek`) + **visage plein écran** (nouveau type de
  jumpscare `"screamer"`, appel `blit` vérifié par désassemblage de
  `Gui.extractTextureOverlay` — même pattern que la vignette vanilla), gain de
  tension centralisé par le directeur, dissolution en cendre après 25 ticks.
  S'il ne se retourne jamais :
  rien — le doute seul a travaillé. Cooldown 10 min, compte comme événement
  agressif (exclu du cooldown post-poursuite) et comme avertissement de
  poursuite. `screamersEnabled=false` → repli sonore « sting », aucune image.
  Texture placeholder procédurale `textures/gui/screamer_host.png` (256×256),
  remplaçable par un visuel IA (voir ASSETS.md §Prompts).

### Modifié
- **Anti-habituation** : le poids de chaque événement est divisé par
  `1 + 0,15 × vécus` (compteur persisté par id, borné à 50, codec profil
  rétro-compatible) — les événements jamais vus deviennent relativement plus
  probables, la peur reste neuve sur la durée.
- **Le Contrefait** (`contrefaitEnabled`, entité `penumbra:contrefait`) —
  QUATRIÈME présence du mod : une copie RATÉE de la légende. Proportions de
  joueur, yeux blancs DÉPAREILLÉS (le droit 1 px trop haut et plus petit),
  un bras entier couleur chemise (mal colorié), les deux jambes de teintes
  différentes, cheveux troués à l'arrière. Il a une ombre (contrairement à
  l'Originel) : la copie est presque réussie — c'est le « presque » qui la
  trahit. Comportement d'ange pleureur : il n'AVANCE que quand personne ne
  le regarde (LOS + cône ±60°, évalué 1/2 ticks), se fige en pleine
  enjambée quand on se retourne (démarche cassée : jambes presque en phase,
  bras morts, tête penchée en permanence), et à moins de 10 blocs, à
  l'instant où plus personne ne regarde : disparu — murmure + 40 % de
  chance de laisser un totem raté là où il se tenait. Jamais d'attaque,
  invulnérable, durée de vie 2 min, apparitions ~1/600 par joueur/10 s à
  30–50 blocs FACE au joueur, hors économie de tension (comme l'Originel).
- **Traces « légende ratée »** (portées par le Contrefait,
  `worldModificationEnabled`) : totem raté en 3 variantes (feu oublié /
  pilier tordu / torche au lieu du feu) ou panneau avec une faute (« JE TE
  VOIT », « VA TEN », « IL ARIVE », « DERIERE TOI » —
  `penumbra.sign.originel_1..4`, fautes volontaires, versions anglaises
  équivalentes). Les anciens tunnels et arbres effeuillés ont été retirés :
  une trace permanente ne supprime plus aucun bloc existant. ≤ 1 trace/jour
  de jeu/joueur. L'Originel, lui, garde son cairn minéral (pierres profondes +
  torche des âmes) : deux présences, deux signatures.
- **Textures d'entités v3** : repeintes d'après les planches de référence IA
  fournies par l'utilisateur — l'Hôte gagne des veines de braise pleine
  longueur sur les membres (avant et arrière) et un visage de fumée grise aux
  yeux incandescents ; l'Observateur devient un noir uniforme dont seuls les
  deux yeux pâles existent ; l'Originel prend sa tenue définitive (chemise
  sarcelle à col V, avant-bras nus, jean marine), toujours sans visage.

### Corrigé et sécurisé
- Le directeur ne comptabilise plus un événement annulé comme réussi et isole
  les exceptions d'un événement afin qu'elles ne fassent pas tomber le tick
  serveur. La tension décroît assez lentement pour construire une vraie courbe,
  et les manifestations sont déverrouillées progressivement par stade narratif.
- Les caches réseau, poursuites, moments personnels, restaurations et entités
  sont purgés à la déconnexion et à l'arrêt. Les paquets C2S sont validés et
  limités ; les quotas d'Hôtes/Observateurs sont désormais propres à chaque
  joueur et respectés membre par membre dans les groupes.
- Les Hôtes, Observateurs, Originels et Contrefaits temporaires ont un
  propriétaire, une durée de vie sauvegardée et un nettoyage terminal. Les
  lumières temporaires survivent à un redémarrage via le registre persistant de
  restauration.
- Les copies imparfaites ne suppriment plus les porte-armures du joueur et ne
  perdent plus silencieusement leurs données quand une zone est déchargée. Les
  faux objets de copie sont inoffensifs et les traces permanentes ne retirent
  aucun bloc existant.
- La rareté des structures utilise maintenant des cellules de chunks cohérentes
  (30 × 30 par défaut au lieu d'une zone accidentellement gigantesque). La
  sauvegarde atomique de `penumbra.json` possède un repli compatible Windows.
- Le scheduler différé est isolé par serveur et sûr face aux ajouts pendant un
  tick. Le build valide les JSON, langues, sons, textures et modèles ; 9 tests
  unitaires couvrent notamment le rythme, les structures et le scheduler.

## [0.5.0] — Phase 5 (couche ambiante, l'Originel, finale du rituel, refonte visuelle)

### Ajouté
- **Couche ambiante client** pilotée par l'état de tension, synchronisé par le
  nouveau payload S2C `tension_sync` (envoi sur changement d'état + toutes les
  100 ticks ; cache client `ClientTensionState`, périmé après 15 s sans synchro,
  purgé à la déconnexion) :
  - **nappe sonore** (`improvedSoundsEnabled`) : 3 nappes de 8 s à fondus
    intégrés (`ambient_bed_low/mid/high`, -20/-17/-14 dBFS) rejouées toutes les
    ~5 s en crossfade par recouvrement — WATCHED → CRISIS ; **FALSE_CALM =
    silence total** (le faux calme n'est crédible que si plus rien ne vibre) ;
    battement cardiaque subliminal en CRISIS (volume 0.12, toutes les 2 s) ;
  - **voile périphérique** (`fogEnabled`, `TensionVeilRenderer`) : trois bandes
    d'assombrissement des bords d'écran (alpha max ≈ 0x30 en CRISIS), intensité
    lissée à 5 %/tick — même API `HudElementRegistry` que le flash de jumpscare ;
  - **dérive de FOV** (`distortionEnabled`) : oscillation ±0,4 % (HUNT) à
    ±0,8 % (CRISIS), période ~28 s, composée dans `CameraFovMixin` par-dessus le
    zoom des jumelles (mêmes garanties de restauration exacte).
- **L'Originel** (`originelEnabled`, entité `penumbra:originel`, hors économie de
  tension) : silhouette aux proportions EXACTES de joueur (0,6 × 1,95), peau
  blafarde, **sans visage** — qui apparaît ultra-rarement (~1/2 h de jeu actif)
  en pleine vue à 60–90 blocs, ciel visible, et ne fait JAMAIS face au joueur
  (rotation lente dos tourné). S'approcher à moins de 32 blocs : il n'est plus
  là — aucune particule, aucun son. Aucune ombre au sol (renderer 0.0f).
  Bourdon `originel_hum` envoyé au seul joueur visé. **Traces**
  (`originelTracesEnabled` + `worldModificationEnabled`) : cairn de pierres
  profondes + torche des âmes posé sur le chemin du joueur (≤ 1/jour de jeu
  par joueur, jamais restauré — la preuve du passage).
- **Finale du rituel du Sanctuaire** : la séquence remplace le simple drone —
  dissolution de toutes les entités d'horreur à ≤ 64 blocs puis silence, quatre
  murmures convergents, **unique face-à-face scripté avec l'Hôte** (8 blocs,
  figé, il regarde), libération `ritual_release`, tension à zéro et **accalmie
  de 2 jours de jeu** (`HorrorDirector.applyPostRitualCalm`). Épilogue :
  « La cendre retombe. Pour un temps. » — après l'accalmie, il réapprend.
- **Coffre « presque juste » des copies imparfaites** : jusqu'à 3 imitations
  d'objets de la barre d'action du joueur (exemplaire unique, sans enchantement
  ni durabilité), renommées avec UNE erreur d'un caractère (lettre doublée,
  lettres échangées ou point final) — `CopyBuilder.almostRightName`.
- **6 nouveaux sons** générés par `tools/gen_sounds.py` (23 au total) et
  **variantes de murmure** : `PenumbraSounds.whisperVariant` tire
  `host_whisper_2` une fois sur deux si `improvedSoundsEnabled` (la familiarité
  tue la peur) — branché sur la dissolution en cendre et `sound_behind`.

### Modifié
- **Refonte visuelle des personnages** (`tools/gen_entity_textures_v2.py`) :
  l'Hôte quitte le gris pâle du placeholder pour un corps **calciné** aligné sur
  son lore de cendre (noir-cendre marbré, fissures de braise, masque de tête en
  cendre claire, yeux braise **asymétriques** — l'œil droit un pixel plus bas) ;
  l'Observateur gagne des yeux asymétriques et un visage réduit ; nouvelle
  texture `originel.png`.
- **Animations réécrites** : l'Hôte marche en **boitillement asymétrique**
  (phases décalées de 2,8 rad, amplitudes inégales) avec les **bras morts**
  (aucun swing en marche), immobilité réelle au repos, tête en SUR-rotation
  (×1,12) avec dérive lente et salves de tremblements (8 ticks / ~9 s).
  L'Observateur gagne l'« inclinaison volée » : la tête s'incline si lentement
  qu'on ne la voit pas bouger (0 → 0,11 rad sur ~10 s) puis se redresse en
  2 ticks — on ne perçoit que la correction, du coin de l'œil.
- **`multiplayerContent` enfin consommé** (recommandation de l'audit Phase 4) :
  quand l'option est fausse et qu'un autre joueur est à ≤ 64 blocs, aucune
  manifestation physique (événements invoquant Hôte/Observateur, poursuites,
  apparitions de l'Originel) — les sons et anomalies individualisés restent.
- **Période de grâce persistée** (`graceStartTick` dans le profil, codec v2
  rétro-compatible) : un redémarrage serveur ne réoffre plus 7,5 min de calme.

### Corrigé
- **Zoom des jumelles sans effet (touche C)** : l'ancien mixin divisait
  `Camera.fov`/`hudFov` au `RETURN` de `update(DeltaTracker)` — or le
  désassemblage du bytecode 26.1.2 montre que `update` construit la matrice de
  projection EN INTERNE (`calculateFov → PUTFIELD fov → … → GETFIELD fov →
  setupPerspective`) : la division arrivait après que la projection ait été
  figée, le monde ne zoomait jamais (seule la main, via `hudFov` copié plus
  tard par `extractRenderState`, était affectée). `CameraFovMixin` injecte
  désormais au `RETURN` de `calculateFov(F)F` — la valeur est divisée à la
  source, projection et frustum de culling compris. `calculateHudFov` n'est
  volontairement plus touché : la main garde son FOV vanilla (comportement
  longue-vue ; `binocularsHideHand` reste disponible).
- **Fuite mémoire lente** : les maps transitoires du directeur (et de
  l'Originel) sont purgées à la déconnexion du joueur
  (`ServerPlayConnectionEvents.DISCONNECT` → `HorrorDirector.onPlayerDisconnect`).

## [0.4.0] — Phase 4 (Observateur, badges, protections multijoueur)

### Ajouté
- **L'Observateur** (`penumbra:watcher`, `watcherEnabled`) : entité humanoïde
  immobile de 0,6 × 2,4 blocs (yeux à 2,1, tracking 48 blocs) qui apparaît dans
  le cône de regard du joueur et le fixe. Registre dédié `WatcherEntities`
  (même pattern `EntityType.Builder` + `build(ResourceKey)` que l'Hôte),
  renderer et modèle client (`WatcherClient`, `WatcherRenderer`, `WatcherModel`,
  texture `assets/penumbra/textures/entity/watcher.png`).
- **2 événements de l'Observateur** ajoutés au pool du directeur (31 au total) :
  `watcher_stare` (apparition à distance dans le cône de vue ; soutenir le
  regard `watcherStareSeconds` secondes déclenche la réaction et le badge
  `you_see_me`) et `watcher_group` (`watcherGroupsEnabled`, très rare : arc
  d'Observateurs via `spawnInGazeCone` centré sur un yaw arbitraire). Les deux
  respectent le plafond de population partagé (`WatcherEntity.populationCapReached`).
- **Badges / avancements** (`badgesEnabled`, module `fr.penumbra.badge.PenumbraBadges`) :
  7 avancements (`root`, `you_see_me`, `first_anomaly`, `first_structure`,
  `survivor`, `sanctum_ritual`, `confrontation`) à critère
  `minecraft:impossible` octroyés par `grant(ServerPlayer, String)` — toast et
  son vanilla automatiques, `has` pour l'anti-doublon, warn unique par id
  introuvable, sortie silencieuse si `badgesEnabled=false`. Câblage des 5
  octrois intégrateur : `first_anomaly` (premier événement, `HorrorDirector`),
  `first_structure` (découverte de structure, `PenumbraStructures`), `survivor`
  (`HorrorDirector.endChase`, joueur vivant), `sanctum_ritual` (rituel du
  sanctuaire), `confrontation` (`StoryManager.setStage` au stade 6).
- **Protections multijoueur** (déjà intégrées par le module dédié — voir
  `docs/fragments/multiplayer_audit.md`) : `HorrorDirector.canSpawnHorrorEntityNear`
  (plafond `maxHostEntitiesPerPlayer` sur les 6 événements invoquant un Hôte et
  `canStartChase`), anti-spam S2C `sendSound` (1 paquet non prioritaire /
  3 ticks / joueur, table bornée à 128 entrées, TTL 200 ticks), voie
  `sendSoundPriority` jamais étranglée (stinger de poursuite, sons de scare),
  rate-limit C2S `PenumbraC2S.allowC2S` (10 paquets/s/joueur, fenêtre 20 ticks,
  warn 1×/min), durabilité jumelles facturée sur front montant du zoom,
  purge des fenêtres de rate-limit à la déconnexion.
- **Commande** `/penumbra watcher test` : fait apparaître un Observateur à
  15–30 blocs dans le cône de regard (±120°) du joueur, feedback traduit.
- **5 nouvelles options de configuration** : `watcherEnabled`,
  `watcherGroupsEnabled`, `badgesEnabled`, `maxHostEntitiesPerPlayer` (1–4,
  défaut 2), `watcherStareSeconds` (1–10, défaut 3).
- **Traductions** : clés Observateur, badges (titres/descriptions EN/FR des 7
  avancements) et retours de commande fusionnées dans `en_us.json` /
  `fr_fr.json` (114 clés chacun, aucun doublon ; dossier `lang/fragments/`
  supprimé après fusion).

### Modifié
- `PenumbraMod.onInitialize` : enregistrement de `WatcherEntities` et
  `PenumbraBadges` parmi les registres (avant les événements).
- `PenumbraClient.onInitializeClient` : enregistrement de `WatcherClient`
  avec le renderer de l'Hôte.
- `HorrorEvents.ALL` : ajout de `WatcherEvents.create()` au pool de sélection
  (29 → 31 événements), même pattern que `NightEvents`/`UndergroundEvents`.
- Version du mod : `0.4.0` (`build.gradle`, `fabric.mod.json`).
- Protocole de test étendu à la Phase 4 ([docs/TEST_PROTOCOL.md](docs/TEST_PROTOCOL.md)).

### Corrigé (intégration)
- `PenumbraBadges.find` : `ServerPlayer.getServer()` n'existe pas en 26.1.2 —
  remplacé par `player.level().getServer()` (`ServerPlayer.level()` retourne
  `ServerLevel`, vérifié javap sur `minecraft-merged.jar` 26.1.2). Seule
  erreur de compilation des modules livrés.

### Préservé
- Aucune régression Phase 1–3 : commandes `/penumbra event night|underground`
  inchangées, mondes antérieurs chargés sans migration, avancements absents
  d'un datapack tolérés (warn unique, aucune exception).

## [0.3.0] — Phase 3 (histoire, structures, objets narratifs, événements nocturnes et souterrains, copies)

### Ajouté
- **Progression narrative (StoryManager)** : stade d'histoire par joueur (0–6 :
  `normalcy`, `traces`, `imitation`, `familiarity`, `substitution`, `recognition`,
  `confrontation`) persisté dans les données du monde (codec à champs optionnels,
  rétrocompatible) avec compteurs de découvertes contractuels (`first_anomaly`,
  `mimic_event`, `refuge_host_seen`, `bed_used`, `copy_built`, `host_observed`,
  `sanctum_ritual`, `structure_<type>`). Règles automatiques de montée de stade et
  message narratif discret à chaque progression. Câblage : premier événement
  d'horreur (`first_anomaly`), événements d'imitation `footsteps` /
  `fake_silhouette` / `lure_door` (`mimic_event`), apparitions de l'Hôte en
  sighting (`host_observed`), poste d'observation choisi près d'un refuge
  (`refuge_host_seen`, via `ObservePlayerGoal`), réveil au matin (`bed_used`,
  via `noteSlept`), copie construite (`copy_built`), rituel du sanctuaire
  (`sanctum_ritual`), approche de structure (`structure_<type>`).
- **4 structures procédurales** (`observation_post`, `shifted_house`,
  `penumbra_well`, `ash_sanctum`) avec variantes : génération déterministe par
  hash de graine sur les **nouveaux chunks uniquement** (1 structure par cellule
  de `structureRarityChunks` chunks, validation de site : biome non aquatique,
  pente ≤ 4, altitude ≤ 180, aucun liquide), suivi persistant (StructureTracker),
  découverte à 24 blocs (tick throttlé 1/100), tables de butin dédiées, pancartes
  narratives. **Rituel du sanctuaire** : clic droit à l'`ash_sigil` sur le pilier
  central (consommé) → `sanctum_ritual`.
- **6 objets narratifs** (indices de structures) : `cracked_lens`,
  `incomplete_journal`, `observation_fragment`, `blackened_plumb_bob`,
  `impossible_photo`, `silhouette_glass` — ce dernier porte un composant de
  données persistant (`silhouette_state`) : 15 % des stacks révèlent une
  silhouette (infobulle alternative synchronisée réseau).
- **8 événements nocturnes** : `window_silhouette`, `roof_steps`, `torch_chain`,
  `animal_stare`, `dawn_door`, `hill_host`, `false_refuge_light`,
  `shadow_crossing` (config `nightEventsEnabled`).
- **8 événements souterrains** : `wall_knocks`, `false_vein`, `foreign_torch`,
  `deadend_breath`, `tunnel_silhouette`, `pickaxe_echo`, `shifted_gallery`,
  `impossible_room` (config `undergroundEventsEnabled`, joueur sous terre
  requis). **Restauration garantie** : `UndergroundRestorationTracker` (SavedData,
  cap 256 entrées, éviction FIFO) mémorise chaque bloc posé et le restaure à
  échéance uniquement s'il est resté intact — tick de maintenance throttlé
  1/200 — jamais d'écrasement d'une modification du joueur. Mémoire de minage de
  `pickaxe_echo` via `PlayerBlockBreakEvents.AFTER`.
- **Copies imparfaites du refuge** (`imperfectCopiesEnabled`) : le directeur
  tente une copie toutes les 12000 ticks par joueur (probabilité 15 %,
  `CopyManager.maybeBuildCopy`) — garde-fous : stade ≥ 3, cooldown 72000 ticks
  persisté, aucun autre joueur à moins de 30 blocs, site validé (blocs naturels,
  distance). Copies suivies (CopyTracker) et restaurables.
- **Commandes Phase 3** : `/penumbra story get|set <0-6>`,
  `/penumbra structure locate|spawn <type>`, `/penumbra item give <id>` (8 objets),
  `/penumbra copy build|restore`, `/penumbra event night <id>` et
  `/penumbra event underground <id>` (mêmes mécanismes de forçage que
  `/penumbra event`), `/penumbra binoculars landmark` (structure la plus proche,
  direction cardinale + distance énigmatique), `/penumbra debug restoration`
  (restauration complète souterraine + copies du niveau courant).
- **7 nouvelles options de configuration** : `storyEnabled`, `structuresEnabled`,
  `structureRarityChunks`, `structureEventChance`, `nightEventsEnabled`,
  `undergroundEventsEnabled`, `imperfectCopiesEnabled` (34 au total).
- **Traductions** : ~60 nouvelles clés EN/FR (stades narratifs, structures,
  directions cardinales, objets et descriptions, pancartes inachevées, retours
  de commandes) fusionnées dans `en_us.json` / `fr_fr.json` (97 clés chacun,
  aucun doublon).

### Modifié
- `HorrorEvents.ALL` : pool de sélection construit de façon mutable puis figé
  (`List.copyOf`) — 13 événements Phase 1/2 + 16 événements Phase 3 (29 au total),
  sans allocation par tick.
- `PenumbraCommands` : l'arbre `/penumbra` est construit dans un builder local
  pour permettre le greffage des sous-arbres des modules (`story`, `structure`,
  `item`, `copy`).
- Version du mod : `0.3.0` (`build.gradle`, `fabric.mod.json`).
- Protocole de test étendu à la Phase 3 ([docs/TEST_PROTOCOL.md](docs/TEST_PROTOCOL.md)).

### Corrigé (intégration)
- `StoryCommands` : parenthèse fermante en trop dans `attach` (erreur de
  compilation livrée).
- Module structures : import `net.minecraft.world.level.Heightmap` →
  `net.minecraft.world.level.levelgen.Heightmap` (vérifié javap 26.1.2).

### Préservé
- Aucune régression Phase 1/2 : les mondes antérieurs se chargent sans migration
  (codecs à champs optionnels), les structures ne touchent que des chunks neufs,
  toute modification de bloc Phase 3 est restaurable.

## [0.2.0] — Phase 2 (jumelles, poursuite, leurres et mémoire)

### Ajouté
- **Jumelles** : nouvel objet craftable (`binoculars`, recette verre/fer/cuir, 256 points
  de durabilité). Touche **C** par défaut (modifiable, catégorie « Penumbra »), modes
  maintenir ou basculer, zoom optique ×2 à ×6 avec transition douce, overlay procédural
  (deux disques + vignette, sans texture), respiration de visée subtile, sons d'activation
  feutrés. FOV toujours restauré à sa valeur exacte (mort, dimension, déconnexion, écran) ;
  aucune écriture dans `options.txt`. Aucun effet en troisième personne.
- **Réseau C2S** : paquets `binoculars_state` et `inventory_state` pour synchroniser le
  zoom et l'ouverture d'inventaire avec le serveur (individualisé par joueur).
- **Poursuite de l'Hôte** : nouvel état d'IA `CHASING` — mise en scène d'ouverture
  (arrêt face au joueur + stinger `chase_stinger`), accélération de 1,15 à 1,45,
  télégraphe d'attaque de 20 ticks, abandon au-delà de 48 blocs ou après 1200 ticks.
  Anti-frustration : avertissement contextuel préalable obligatoire, puis cooldown global
  post-poursuite (`postChaseCooldownTicks`, défaut 7200) sans chase, scare ni apparition ;
  fausses retraites possibles (l'Hôte se cache à moins de 24 blocs).
- **5 nouveaux événements** : `lure_door` (grincement lointain, sans modification de
  blocs), `fake_silhouette` (Hôte `HIDING` qui s'évanouit dès qu'on le fixe),
  `distant_light` (lueur posée à 24–40 blocs puis retirée — restauration garantie),
  `anticlimax` (heartbeat + nappe, aucun autre effet, favorisé en `FALSE_CALM`),
  `binoculars_sighting` (Hôte à 60–90 blocs dans le cône de regard, visible aux jumelles).
- **7 réactions de l'Hôte aux jumelles** : `freeze`, `head_turn`, `vanish_on_unzoom`,
  `approach_between`, `sound_behind`, `mimic_posture`, `reappear_checked` — pondérées par
  l'état, cooldown 2400 ticks, anti-répétition, jamais d'attaque ni de téléportation au
  contact.
- **Jumpscares retardés** : 6 déclencheurs (`chest_scare`, `refuge_scare`, `torch_scare`,
  `lookback_scare`, `inventory_scare`, `quiet_scare`) armés par les actions du joueur avec
  un délai de 100 à 400 ticks, probabilité pondérée par la tension, cooldown 3000 ticks
  par type et anti-doublon (800 ticks minimum entre deux scares).
- **Mémoire comportementale** : le profil d'horreur apprend jusqu'à 4 points de refuge,
  16 cellules fréquentées, les usages du lit et les regards en arrière ; l'Hôte privilégie
  ces zones pour ses apparitions. Codec v2 à champs optionnels : **les mondes Phase 1 se
  chargent sans migration**.
- **12 nouvelles options de configuration** (`config/penumbra.json`) : activation,
  objet requis, mode bascule, niveau de zoom (borné 2.0–6.0), transitions, respiration,
  overlay, réactions, leurres, scares retardés, durabilité, cooldown post-poursuite
  (borné 1200–24000). Les configurations Phase 1 restent valides.
- **7 nouveaux sons** : `binoculars_on`, `binoculars_off`, `lure_door`, `distant_hum`,
  `chase_stinger`, `footstep_mimic_2`, `breath_behind_2` (+ sous-titres EN/FR).
- **Nouvelles commandes** : `/penumbra chase`, `/penumbra scare <id>`,
  `/penumbra binoculars test|host_sighting|reaction <type>|reset`.

### Modifié
- Version du mod : `0.2.0` (`build.gradle`, `fabric.mod.json`).
- Protocole de test étendu à la Phase 2 ([docs/TEST_PROTOCOL.md](docs/TEST_PROTOCOL.md)).

### Préservé
- Aucun mixin, aucune régression Phase 1 : toutes les modifications du monde restent
  restaurables, la séparation client/serveur est stricte, aucun flash lumineux ajouté.

## [0.1.0] — Phase 1 (version initiale)

### Ajouté
- **HorrorDirector** : système de tension par joueur (0–100), 7 états (`CALM` → `CRISIS`,
  `FALSE_CALM`), période de grâce de 7,5 minutes, décroissance naturelle de la tension,
  persistance dans le monde (profils, stade narratif).
- **L'Hôte** : entité humanoïde (2,6 blocs, 40 PV) avec comportements `DORMANT`, `OBSERVING`,
  `STALKING`, `RETREATING`… Apparition hors champ de vision dans l'obscurité uniquement,
  disparition (70 %) ou téléportation derrière le joueur (30 %) quand elle est regardée.
- **8 événements d'horreur** : `footsteps` (pas mimétiques), `breath` (souffle dans la nuque),
  `fake_creeper`, `torch_sputter` (torches crachotantes, extinction restaurable),
  `door_anomaly`, `distant_sighting` (apparition distante), `silence` (nappe de 12 s),
  `sleep_interrupt`.
- **Réseau individualisé** : paquets S2C `horror_sound` et `jumpscare` ; chaque joueur
  entend ses propres sons directionnels en multijoueur.
- **JumpscareManager client** : cooldown anti-empoisonnement de 400 ticks, secousse de caméra
  amortie sans mixin, types `sting`, `fake_creeper`, `apparition`.
- **Configuration** : 15 options dans `config/penumbra.json` (fréquences, volumes,
  accessibilité, multijoueur) ; `flashesEnabled` désactivé par défaut (photosensibilité).
- **Commandes** : `/penumbra status|tension|event|spawnhost|jumpscare|reset`.
- **Ressources** : Sceau de cendre (`ash_sigil`, butin rare de l'Hôte), particule `ash_mote`,
  10 événements sonores, traductions française et anglaise.
