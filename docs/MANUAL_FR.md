# Penumbra

> **Quelque chose apprend à être vous.**

Penumbra est un mod d'horreur psychologique pour Minecraft 26.1.2 (Fabric). Il ne se contente pas
d'ajouter un monstre : il observe votre façon de jouer, monte la tension progressivement, puis
manifeste une présence — **l'Hôte** — une silhouette qui vous étudie, imite vos pas et disparaît
dès que vous la regardez.

## Nouveau en 0.23.0 — suivre son histoire

Chaque joueur reçoit **Le Relevé** à sa première arrivée. Le clic droit ouvre
ses propres pistes et ses pages ; le début du carnet indique comment avancer
au chapitre courant. En cas de perte, refabriquez-le avec **un livre et une
poche d'encre**. Les connaissances restent sauvegardées.

Vous pouvez relever les quatre pages en fouillant les coffres d'origine des
sites, même si un autre joueur a déjà pris le butin. La découverte d'une
**Maison décalée** fournit aussi une autre voie que la copie de votre refuge.

Une fois arrivé à Reconnaissance, apportez **la caméra et un Sceau de cendre**
au sanctuaire : le premier rituel éligible mène directement au reflet. Sans
caméra, le jeu vous prévient avant de consommer le Sceau. La victoire conclut
votre enquête ; elle ne termine pas celle des autres joueurs du serveur.

Le [parcours détaillé de l'histoire](STORY_FLOW_FR.md) explique le début, les
livres, les conditions de chaque chapitre et la fin. Il contient des révélations.

## Nouveau en 0.22.0 — confort personnel et accalmie sauvegardée

En jeu, appuyez sur **K** pour ouvrir les réglages de confort, sans objet ni
permission opérateur. La touche se réassigne dans les contrôles Minecraft.
Elle n'ouvre pas le menu pendant la saisie dans le chat ou un autre écran.
Le menu met le solo en pause ; **le multijoueur continue de tourner**.

| Réglage personnel | Effet local |
|---|---|
| Mouvements réduits | Coupe secousses, dérive du champ de vision, respiration du zoom, animations VHS, grain, rémanences, flashs et vacillement ; le zoom reste utilisable |
| Masquer les visages | Retire les visages soudains plein écran, sans masquer les créatures ni changer leurs dégâts |
| Bloquer les flashs | Retire les éclairs visuels ; photographie et contre-mesures au flash restent utilisables |
| Grain VHS | Autorise ou coupe le grain et les lignes de balayage de l'ambiance |
| Rémanences | Autorise ou coupe les silhouettes périphériques |
| Lumière variable | Autorise ou coupe le vacillement de l'éclairage dynamique de Penumbra |
| Sons soudains | Multiplie le volume des sons de sursaut couverts, de 0 à 100 % du volume de base |
| Ambiances | Multiplie le volume des nappes et du cœur suivis, sans modifier les pas, indices ou sons Minecraft |

Ces choix peuvent **réduire** ce que permet `config/penumbra.json`, jamais
réactiver un effet que cette configuration coupe. Le mode mouvement réduit
prime sur les options concernées sans effacer leurs valeurs mémorisées.
Il ne change ni la fréquence des événements ni les règles du serveur.

Les préréglages **Immersion**, **Mouvement réduit** et **Doux** préparent un
brouillon. Immersion retire les réductions locales supplémentaires ; Doux
masque aussi les visages et règle les sons soudains à 45 %, les ambiances à
60 %. **Appliquer** enregistre dans `config/penumbra-client.json` et prend
effet ; **Annuler** ou Échap abandonne le brouillon. Une erreur d'écriture
laisse les réglages actifs inchangés et affiche un message.

L'accalmie du rituel et de la victoire est désormais sauvegardée : **48 000
ticks de monde**, soit 40 minutes à 20 ticks par seconde. Elle survit à une
reconnexion et à un redémarrage. Elle continue à s'écouler pendant votre
absence si le monde avance, mais pas quand le serveur est arrêté ou le monde
réellement en pause. Toutes les dimensions utilisent la même horloge du monde
principal. Elle ne se recharge donc pas en changeant de dimension.

Le badge **« Il connaît votre nom »** récompense désormais uniquement la
victoire du duel, pas le simple passage au stade 6. Les badges et victoires
déjà sauvegardés restent acquis. Pour les diagnostics, la commande opérateur
`/penumbra tension 90` (ou davantage) annule volontairement l'accalmie restante.

## Corrections en 0.21.1 — un rituel que l'on peut reprendre

- **Dès l'activation de l'autel**, les scènes liées à votre personnage et ses
  apparitions temporaires proches s'arrêtent pour laisser place au rituel.
  Les apparitions appartenant aux autres joueurs et les créatures ordinaires
  restent présentes : choisissez un endroit où vous pouvez vous concentrer.
- **Pendant le préambule**, restez à moins de 40 blocs de l'autel, dans la même
  dimension, vivant et éveillé. Un éloignement, une mort, le sommeil, le mode
  spectateur ou une déconnexion annule cette tentative. Revenir ne relance pas
  ses effets ; vous pouvez recommencer sur le même autel avec un Sceau de cendre.
  Le Sceau consommé au lancement n'est pas rendu en cas d'abandon.
- **La victoire vient du duel terminé.** Une interruption du préambule ou
  l'absence de place sûre pour le double n'accorde ni victoire ni calme final.
  Les victoires déjà enregistrées dans vos sauvegardes restent acquises.
- **Limite historique corrigée en 0.22.0 :** en 0.21.1, une reconnexion
  terminait l'accalmie restante. La 0.22.0 la sauvegarde désormais ; les
  victoires enregistrées restent également acquises.
- **Pour déclencher la caméra**, tenez-la dans la main principale et faites
  un **clic gauche** au signal d'ouverture. **C** règle le zoom ; il ne déclenche
  pas le flash. Les flashs d'écran peuvent rester désactivés.
- **Après une reconnexion**, une ancienne capture encore en traitement ne
  remplace ni n'annule une nouvelle prise de vue.

## Nouveau en 0.21.0 — écouter et comprendre

- **Dossier personnel :** ouvrez l'album avec la Caméra VHS, puis le bouton
  **Dossier**. Il rassemble la méthode de prise de vue, vos observations,
  les pistes encore incomplètes et les liens confirmés entre deux preuves.
  La molette fait défiler un texte long ; les flèches changent de fiche.
- **L'Écouteur** laisse désormais une preuve sur les photos développées où il
  est réellement cadré. Le dossier conserve toutes les catégories reconnues,
  même si une photo individuelle reste limitée à huit preuves.
- **Final du sanctuaire :** au dernier stade de l'histoire, le double ne cède
  plus à trois flashs précipités. Restez silencieux et observez-le jusqu'au
  signal d'ouverture ; déclenchez alors votre caméra. Trois réussites, séparées
  par un repos, dissipent le reflet. Si une ouverture est ratée, vos réussites
  restent acquises pendant cette confrontation. Les flashs d'écran peuvent
  rester désactivés dans les options de confort.
- **Ambiance :** les nappes possèdent plusieurs variations, se fondent entre
  elles et s'arrêtent lors du faux calme. Les pas reçoivent aussi des variantes.
  Le curseur Minecraft « Ambiance » et `improvedSoundsEnabled` restent respectés.
- **Torches :** ouvrir une pause solo conserve leur lumière. Les sources
  proches ne demandent plus plusieurs fois la même mise à jour du terrain.

## Nouveau en 0.20.0 — apprendre à s'en sortir

- **Échapper à l'Hôte :** cachez-vous derrière un vrai obstacle et gardez sa
  ligne de vue coupée pendant quatre secondes. Il cherche à l'endroit où il
  vous a vu pour la dernière fois. Son bond commence par un bref arrêt sonore
  et vise une position fixe : se décaler ou se mettre à couvert peut sauver.
- **Face à l'Écouteur :** immobilisez-vous et cessez de frapper. Il peut venir
  vers un bruit produit par quelqu'un d'autre, mais ne peut pas toucher son
  propriétaire silencieux. Son annonce de charge laisse un court temps de
  réaction ; ses coups ne passent plus à travers les murs.
- **Boussole d'arpenteur :** un clic droit change sa destination. Le camp
  permet de chercher le Relevé ; les sites inexplorés écartent vos découvertes
  déjà enregistrées ; le site le plus proche aide à retrouver une ruine.
  Seuls les sites déjà générés et le camp sont connus de l'aiguille. Quand
  vous découvrez un site, passez au mode « plus proche » pour finir de le fouiller.
- **Le Relevé** adapte ses pages à l'écran. Les flèches gauche/droite et la
  molette le feuillettent ; les textes longs continuent sur le feuillet suivant.
  Ses pages sont lisibles sur un papier uni, avec les bords usés du carnet.
- Le directeur espace mieux les rencontres et laisse récupérer après une
  situation critique. Une créature déjà engagée reste à gérer : le repos du
  directeur ne remplace pas les contre-mesures.

## Nouveau en 0.19.0 — une aiguille, une flamme, et de la 3D

- **Les objets trouvés.** Une chaise de cuisine, seule, dos tourné, à quatre
  cents blocs de toute habitation. Une porte fermée sans murs autour. Un miroir
  fêlé. Aucun de ces objets n'est effrayant — c'est l'endroit qui travaille. Le
  Directeur les pose **derrière vous, hors de votre champ de vision** : un objet
  qui apparaît est un effet, un objet qu'on découvre est un fait.
- **La Boussole d'arpenteur** : une boussole et un lingot de cuivre, et
  l'aiguille pointe le site connu le plus proche — le Bûcher compris, dont la
  position se calcule depuis la graine du monde. Elle ne donne jamais de
  chiffre : « à une bonne marche », « très loin — Warrick a mis des jours ».
- **La torche éclaire dans la main.** Torche, lanterne, pierre lumineuse : ce
  qui éclaire posé éclaire aussi tenu, et tombé au sol. Et **la flamme faiblit
  quand la tension monte** — votre rayon de sécurité se rétracte sans qu'on vous
  dise pourquoi.

## Nouveau en 0.19.0 — de vrais maillages 3D

- Penumbra peut désormais afficher des **modèles 3D triangulés** — importés de
  Blender, Meshy ou Blockbench — à côté des habituelles silhouettes en boîtes.
  Faces obliques, courbes, torsions : tout ce qu'un assemblage de cubes ne sait
  pas faire.
- Aucun mixin : Minecraft 26.1 expose une porte de sortie officielle pour la
  géométrie arbitraire, et le maillage traverse le même chemin de rendu que le
  reste du jeu — il reçoit donc l'éclairage et le brouillard sans effort.
- **Objets statiques uniquement**, par choix. Les créatures gardent l'animation
  procédurale, qui a l'avantage décisif de ne jamais boucler à l'identique :
  boîtes animées pour ce qui vit, maillages pour ce qui ne bouge pas.
- Ajouter un décor demande **deux fichiers et aucune ligne de code**, et un
  simple pack de ressources suffit — le système est utilisable par n'importe qui.
  Voir `docs/MESHES.md` et `docs/PROMPTS_PROPS.md`.

## Nouveau en 0.18.0 — quelqu'un est venu avant vous

- Quelque part très loin, une **colonne de fumée** monte à l'horizon. Elle est
  visible à des centaines de blocs, elle est unique dans votre monde, et elle
  est là depuis le début.
- Au bout du chemin : un camp, une cheminée de pierre qui fume encore, un abri
  vide, et un coffre. Dans le coffre, **Le Relevé** — le carnet de terrain d'un
  arpenteur qui a tenu quatre ans ici, et qui a arrêté d'écrire.
- Le carnet s'ouvre sur une **double page de parchemin** : un croquis à la
  plume, une écriture à l'encre, une page par créature. Ce qu'elle est, ce
  qu'elle fait, et ce qui marche contre elle.
- **Mais il ne raconte que ce que vous avez déjà vu.** Les créatures que vous
  n'avez jamais croisées ne sont que des pages délavées : un titre, une ligne
  survivante, et de l'encre qui a coulé. Croisez la chose, et la page se remplit
  toute seule.
- **Quatre pages manquent.** Il les a arrachées lui-même et laissées ailleurs,
  dans les quatre structures du mod. Ce sont les quatre qui donnent les vraies
  contre-mesures — dont celle qui vous sauvera de L'Écouteur. Sur la page d'en
  face, il a griffonné où il les avait mises.
- Il n'explique jamais ce qui lui est arrivé. La dernière page dit seulement
  qu'il a arrêté d'attendre les secours, et qu'il est descendu voir.

## Nouveau en 0.17.0 — quelque chose écoute

- **L'Écouteur** n'a pas d'yeux : à la place, deux pavillons d'oreille
  démesurés, et une peau recousue là où devraient être les orbites. Elle ne
  traque **que le bruit**. Sa tête est toujours tournée vers le dernier son
  entendu, jamais vers vous — c'est comme cela que vous comprenez la règle.
- **Le contre-jeu est réel et il est simple** : ne bougez plus. L'immobilité
  totale produit un silence total, à n'importe quelle distance, même à un bloc
  de son oreille. Marcher accroupi ne porte qu'à 2,5 blocs. En revanche
  frapper ou miner s'entend à sept ou vingt-six blocs, y compris à l'arrêt.
- Elle se trompe de 1,6 bloc : elle vient vers le bruit, pas vers vous. Casser
  un bloc au loin puis se figer ailleurs est un leurre valide. La frapper est
  le pire choix possible.
- Si un bruit franc éclate à moins de six blocs, elle fond sur vous. Se figer
  pendant deux secondes lui fait perdre le fil : elle se remet alors à écouter,
  parfois à un bloc de votre visage.
- **La couche de perception** traduit votre tension en image : grain et lignes
  de balayage qui se dégradent avec l'état, voile périphérique qui respire au
  rythme du battement cardiaque, et une **silhouette rémanente** au bord de
  l'écran quand une vraie présence se trouve derrière vous. Cette rémanence ne
  mente jamais : si vous vous retournez, la chose est là.
- Trois **profils d'accessibilité** (`/penumbra profile doux|standard|cauchemar`)
  sans retirer aucun des réglages fins.
- Quatre nouveaux événements bâtis sur le son : le monde qui retient son souffle,
  le pas de trop derrière le vôtre, la fausse pioche au loin, et la traversée de
  L'Écouteur qui ne vous cherche pas.

## Nouveau en 0.15.0 — cette vache vous regarde bizarrement

- Le **Faux-Semblant** : une vraie créature du jeu — vache, cochon, mouton,
  poule, zombie la nuit — apparaît près de vous. Presque toujours, un détail
  cloche : elle ne fait aucun bruit, ou elle vous fixe sans jamais détourner la
  tête, ou elle reste figée face à un mur. Et parfois… rien ne cloche. Les
  copies parfaites existent, et elles deviennent plus fréquentes à mesure que
  vous apprenez à repérer les défauts.
- Elle ne se laisse jamais toucher : l'approcher, la frapper, la traire ou la
  photographier au flash la dissout en cendres dans un chuchotement. Fixer une
  copie défectueuse assez longtemps la force à vous rendre votre regard —
  puis elle n'est plus là.
- Aucun animal réel n'est jamais blessé ni modifié : le Faux-Semblant est
  toujours une créature apparue pour vous.

## Nouveau en 0.14.0 — la peur ne s'use plus

- Les screamers ne se ressemblent plus deux fois : six mises en scène (zoom
  rampant, cadrage décentré, double coupe, flash subliminal…) tournent sans
  répétition, et le cri change de hauteur à chaque fois.
- Le Directeur sait maintenant que **le vacarme s'use** : plus vous avez vu de
  visages récemment, plus le suivant est atténué — et parfois remplacé par un
  simple souffle. L'attente déçue ré-arme la peur du prochain vrai visage. Les
  bonds physiques au visage, eux, ne sont jamais annulés.
- L'Hôte a été remodelé : cou étiré, mains aux doigts trop longs, épaules
  désaxées — et une immobilité totale de statue tant qu'il ne fait qu'observer.
  Il ne respire que lorsqu'il chasse.
- Identité visuelle unifiée « copie imparfaite » : peau cireuse cousue, vestige
  de tunique turquoise, asymétries. Sept skins repeints, pack de prompts pour
  régénérer les 14 visages de screamers (`docs/PROMPTS_SCREAMERS.md`).

## Nouveau en 0.13.0 — ce que vous voyez n'est pas forcément partagé

- Le Directeur peut manifester un **faux compagnon strictement individuel** : il
  emprunte le skin, le nom, l'équipement visible et la démarche d'un vrai joueur
  momentanément hors de vue. En solo, il peut prendre votre propre apparence.
- Seul le propriétaire de la perception reçoit l'entité, ses pas, ses gestes et sa
  disparition. Les autres joueurs, même juste à côté, ne la voient pas, ne la
  percutent pas et ne reçoivent aucun son ou effet secondaire de cette hallucination.
- La copie reste subtile : elle marche et simule de petites actions avant de se
  figer lorsqu'elle se sait observée. Un regard prolongé la force à se tourner puis
  à disparaître ; la Caméra VHS accélère la vérification et un flash correctement
  cadré dissipe immédiatement la scène.
- Cette manifestation n'attaque pas, ne modifie aucun bloc et ne crée pas de fausse
  preuve photographique. Elle est nettoyée lors d'une mort, d'une déconnexion ou
  d'un changement de dimension, avec un plafond serveur global configurable.
- `false_companion` rejoint le tirage normal du Directeur à partir du stade narratif
  2 et respecte intensité, arcs, anti-répétition et cooldown persistant. Les commandes
  `/penumbra perception companion|status|clear` permettent de tester son cycle complet.

## L'Hôte et le HorrorDirector

- **Le HorrorDirector** est le système de direction invisible du mod. Il maintient pour chaque
  joueur un profil d'horreur persistant (tension de 0 à 100, historique des événements, temps
  passé sous terre, fréquence des regards en arrière) et fait évoluer l'expérience à travers
  sept états : `CALM`, `UNEASE`, `WATCHED`, `MANIFESTATION`, `HUNT`, `CRISIS` et `FALSE_CALM`.
  La tension croît à chaque événement, décroît lentement avec le temps, et survit aux
  sauvegardes.
- **L'Hôte** est une entité humanoïde de 2,6 blocs qui n'apparaît jamais injustement en pleine
  vue : elle apparaît hors de votre champ de vision, dans l'obscurité. Tant que vous ne la
  regardez pas, elle vous observe ; si votre regard s'attarde sur elle, elle se fige puis bondit
  en 3D jusqu'à votre visage, frappe et déclenche le screamer seulement à l'arrivée.

## Nouveau en 0.12.0 — le monde se souvient

- Le **Directeur 2.0** construit maintenant des arcs persistants de 3 à 7 minutes :
  présage, montée, manifestation, retombée et faux calme. Il choisit un thème
  contextuel (grotte, refuge, traque, mimique ou photographie), même après une
  reconnexion, sans rejouer une rafale d'événements au chargement d'un ancien monde.
- Une **dette de manifestation** impose une apparition perceptible au plus tard après
  90 secondes. Les sons ne peuvent plus remplir cette dette et deux événements
  purement audio au maximum peuvent se suivre : à intensité élevée, les grottes
  produisent donc de vraies silhouettes et pas seulement des bruits.
- Les refuges se **contaminent en quatre niveaux** au fil des retours : coffre qui
  semble faux, torche rouge impossible, objet décalé, photo abandonnée, cendre et
  fausse pièce. Tout est visuel et temporaire : aucun inventaire ni bloc du joueur
  n'est réordonné, cassé ou remplacé.
- Trois **scènes liées** donnent une mémoire aux monstres : suivre L'Absent peut
  attirer le Rampant ; la Cavaleuse peut annoncer son passage ; rouvrir la Caméra
  après la disparition du Projectionniste peut appeler l'Originel. Le flash, une
  galerie nouvellement éclairée ou la caméra laissée fermée sont de vraies
  contre-mesures serveur.
- Le dossier photo rapproche désormais les preuves par paires et révèle trois
  **déductions de contre-jeu**. Deux nouveaux screamers VHS contextuels (refuge
  contaminé et mimique) complètent la banque visuelle.
- `/penumbra intensity` devient un curseur maître clair : `50` correspond au rythme
  normal et `100` au maximum, en augmentant ensemble la densité des événements,
  leur intensité et les screamers. `/penumbra arc` et `/penumbra linked` permettent
  de tester les nouveaux systèmes sans attendre leur tirage naturel.

## Nouveau en 0.11.0 — L'Absent vole une identité

- **L'Absent copie maintenant réellement un joueur** : son skin exact, ses bras
  larges ou fins, sa main dominante, ses couches de vêtements réellement activées,
  son armure et les objets visibles dans ses mains. Il préfère imiter un compagnon
  momentanément hors de votre vue ; en solo, il apprend à devenir votre propre double.
- Il mémorise une signature légère de la démarche de sa victime — allure,
  rotations, sprint, saut et accroupissement — puis la rejoue avec des erreurs
  d'abord presque imperceptibles. Il peut aussi feindre de miner ou d'ouvrir une
  porte, sans casser de bloc ni modifier l'état de la porte.
- Son comportement est une montée en cinq temps : **imitation, leurre, soupçon,
  révélation, chasse**. Le fixer ne provoque donc plus un screamer arbitraire :
  ses gestes se dérèglent, sa peau se déchire visuellement, il révèle sa vraie
  silhouette, vous poursuit, bondit dans les derniers mètres, puis le screamer
  frappe uniquement au contact réel et jamais à travers un mur.
- Croiser le vrai joueur et sa copie au même endroit trahit immédiatement
  l'anomalie. En multijoueur, le propriétaire reste l'unique cible de l'attaque,
  tandis que les sons physiques demeurent audibles par les témoins assez proches.
- Le **flash de la Caméra VHS** devient un outil de contre-jeu. Bien cadré et sans
  obstacle, il désoriente L'Absent et force sa révélation. La photo développée
  laisse apparaître progressivement une double exposition de sa vraie forme,
  exclusive au tirage où il était réellement présent.
- Le cadrage serveur suit le facteur de zoom au moment exact du déclenchement et
  reste borné. Un accusé de flash précède l'envoi des pixels : si la caméra est
  encore en recharge, elle l'indique au lieu de produire un tirage perdu.
- Le **Sceau de bannissement** laisse désormais sa forme réelle se consumer
  brièvement dans les cendres au lieu de la supprimer instantanément. Quatre
  options de configuration permettent de régler séparément l'entité, le skin,
  la démarche imitée et la réaction au flash.
- La génération des structures est maintenant différée jusqu'à ce que le
  chunk soit réellement publié par le serveur. Cela supprime le watchdog qui
  pouvait figer un serveur dédié pendant 60 secondes ; les grands scans
  d'événements ignorent également les chunks déchargés au lieu de les forcer.

## Nouveau en 0.10.0 — enquête photographique

- Le déclenchement de la **Caméra VHS** est maintenant une transaction en deux
  temps contrôlée par le serveur. `PhotoShutter` fige le regard, le tirage du
  Projectionniste et les preuves au véritable instant du flash ; le tableau de
  pixels `PhotoCapture` correspondant doit arriver dans les **60 ticks**, avec
  le même numéro de séquence, et ne peut être consommé qu'une seule fois.
- Le serveur recherche jusqu'à **64 blocs** les manifestations réellement
  vivantes et cadrées dans le cône central. Trois rayons (yeux, centre et bas
  de la silhouette) limitent les faux négatifs derrière une vitre ou un rebord. Huit
  catégories peuvent être photographiées : **Hôte, Observateur, Originel,
  Contrefait, Projectionniste, Rampant, Absent et Cavaleuse**. Plusieurs
  catégories présentes dans la même image sont gravées dans le même négatif.
- Les preuves restent volontairement secrètes pendant les **200 ticks
  (10 secondes)** du développement : le serveur ne transmet même pas leurs
  identifiants avant l'échéance, puis répond par un verdict léger sans renvoyer
  les pixels. La visionneuse et l'album affichent
  l'analyse en cours, puis seulement la ou les catégories révélées ; une photo
  ordinaire annonce clairement qu'aucune anomalie n'a été trouvée.
- Chaque joueur possède désormais un **dossier d'enquête persistant**. Une
  catégorie n'y compte qu'une fois et les paliers atteints à **1, 3, 5 et
  7 catégories uniques** délivrent quatre indices narratifs. Supprimer ensuite
  le tirage qui a révélé une preuve n'efface pas cette découverte.
- L'album affiche le palier du dossier et sa progression sur les huit
  catégories. Le propriétaire peut marquer un tirage **Important** : ces
  favoris remontent en tête de « Mes photos », alors que « Photos publiques »
  reste strictement chronologique pour tous les lecteurs.
- Une page d'album ignorée par le limiteur réseau est retentée jusqu'à trois
  fois ; l'écran quitte proprement « Chargement » et affiche un diagnostic si
  le serveur ne répond toujours pas.
- Le format persistant reste rétrocompatible : les listes de preuves, le
  marqueur Important et les dossiers absents des anciennes sauvegardes prennent
  des valeurs sûres, sans réécrire les pixels ni les UUID existants.

## Nouveau en 0.9.0 — chambre noire et peur coopérative

- Un **clic droit avec la Caméra VHS** ouvre désormais un véritable album :
  onglets « Mes photos » et « Photos publiques », six vignettes par page,
  chargement progressif des seuls pixels nécessaires, agrandissement, renommage,
  partage public/retour privé et suppression avec double confirmation.
- Le serveur reste l'autorité : seul le propriétaire peut modifier un tirage,
  les titres et paquets sont bornés, les requêtes sont limitées et rendre une
  photo privée révoque immédiatement le cache des anciens lecteurs publics.
  Les albums 0.6.4–0.8.1 sont migrés sans action et restent privés par défaut.
- Le **fond gris de la visionneuse est corrigé**. Minecraft 26.1.2 attendait les
  coordonnées finales et l'ordre UV `u0/u1/v0/v1`; l'ancien appel réduisait
  toute la capture à un seul texel tout en affichant « photo terminée ».
- Trois scènes coopératives dédiées rejoignent le directeur : `behind_other`
  fait traverser un Hôte que seul le témoin voit derrière son compagnon,
  `paired_echo` donne à deux joueurs des moitiés sonores différentes d'une même
  scène, et `stolen_name` fait porter à L'Absent le vrai nom d'un voisin. Elles
  ont un repli solo et s'annulent proprement en cas de distance, dimension ou
  déconnexion invalide.
- Les modèles 3D du Rampant, de L'Absent, du Projectionniste, de l'Observateur,
  de l'Originel et du Contrefait ont des poses plus organiques : anticipation,
  impact, respiration, proximité, articulations secondaires et transitions de
  paroi/plafond. Les courbes sont bornées et couvertes par des tests unitaires.
- Trois nouvelles images VHS portent la rotation à **douze screamers**. Le
  Rampant et L'Absent déclenchent maintenant leur finale visuelle propre après
  avoir réellement atteint le joueur ; le Chœur est aussi testable par commande.

## Nouveau en 0.8.1 — l'horreur joue en groupe

- Les **scènes lourdes automatiques** sont coordonnées entre les joueurs situés
  dans un rayon de **32 blocs** : une seule commence pendant une fenêtre de
  **4 secondes**, et l'ordre de priorité tourne équitablement entre les joueurs.
  Deux groupes éloignés, ou placés dans des dimensions différentes, continuent
  toutefois leur rythme indépendamment.
- Chaque monstre conserve strictement son propriétaire et sa cible. Une entité
  physique peut rester visible par les témoins proches, mais elle ne se retourne
  jamais contre eux et son screamer visuel reste réservé au joueur visé.
- Les sons positionnés restent audibles près de leur origine. En plus, un
  compagnon situé à **24 blocs ou moins** du joueur ciblé partage la scène, sans
  la diffuser au reste du niveau. Le routage distingue la dimension, la source
  et l'auditeur ; il supprime les doublons prioritaires et limite les rafales
  ordinaires.
- Les caches temporaires de scène, de son et de jumpscare sont nettoyés à la
  déconnexion ou à l'arrêt du serveur, afin qu'une reconnexion ne récupère pas
  une cible ou un effet client périmé.
- L'album accepte désormais **2 048 photos par monde**, tout en conservant un
  quota indépendant de **128 photos par joueur** : un joueur ne peut plus
  épuiser la part d'un autre.
- Les commandes opérateur qui forcent une scène restent volontairement hors de
  cette coordination, pour permettre de tester immédiatement chaque événement.
  Elles acceptent aussi un joueur à la fin, par exemple
  `/penumbra event cave_face PlayerName` ou `/penumbra rampant test PlayerName` ;
  sans ce dernier argument, elles continuent de viser celui qui écrit la commande.

## Nouveau en 0.8.0 — les surfaces répondent

- **Le Rampant grimpe désormais réellement** : pendant sa chasse, il rejoint une
  paroi sans téléportation, l'escalade, passe au plafond lorsque le support le
  permet et peut chercher à s'y raccrocher après une attaque. Son modèle 3D
  articule séparément les membres et adapte sa pose à la course, au bond, à la
  chute, à la paroi et au plafond. Il ne pose ni ne casse aucun bloc.
- Le nouveau **Sceau de bannissement** consommable (`penumbra:ash_ward`) se fabrique
  sans forme avec **papier + charbon de bois + redstone** et donne **2 sceaux**.
  Un clic droit bannit uniquement les monstres du namespace `penumbra` dans un
  rayon de **16 blocs** : joueurs, animaux et monstres vanilla sont épargnés.
  En multijoueur, l'Originel ou le Contrefait privé d'un voisin est aussi
  protégé : seul son propriétaire peut dissiper cette apparition invisible.
  L'ancien **Sceau de cendre** (`penumbra:ash_sigil`), utilisé par le rituel
  narratif, reste distinct et compatible avec les sauvegardes.
- **La Cavaleuse** (`penumbra:scuttler`) est une minuscule araignée entièrement
  inoffensive. Dans **70 %** des scènes elle traverse rapidement le champ de
  vision ; dans les **30 %** restantes elle suit les chaussures du joueur
  pendant **3 à 5 secondes**, sans attaque, dégâts ni butin.
- Le sac mélangé compte maintenant **neuf screamers**. Les variantes 7, 8 et 9
  évoquent respectivement le Projectionniste, le Rampant et L'Absent ; chaque
  image passe une fois par cycle avant répétition et aucun doublon immédiat
  n'est possible. `screamersEnabled: false` continue de tous les masquer.
- Les deux scènes de la Cavaleuse peuvent être forcées avec
  `/penumbra scuttler cross` et `/penumbra scuttler chase`.

## Nouveau en 0.7.0 — trois nouvelles présences

- **Le Projectionniste** contamine environ **15 % des nouvelles photos**. Sa
  silhouette remonte progressivement pendant le développement du tirage ; une
  fois la photo entièrement révélée, l'observer **1,5 seconde** dans la
  visionneuse peut le manifester derrière son propriétaire. Il fixe le joueur
  **4 à 6 secondes**, avance brièvement puis se dissout, sans infliger de
  dégâts. Une nouvelle manifestation photographique ne peut pas se produire
  avant **90 secondes**.
- **Le Rampant** apparaît naturellement dans les grottes sombres, accroché à un
  vrai plafond ou à une paroi. Il se décroche, poursuit sa cible au ras du sol,
  bondit et déclenche un screamer au premier impact. Une lumière forte le met
  en fuite ; s'il ne retrouve pas l'obscurité, il disparaît. Son cooldown
  naturel est de **6 minutes** par joueur.
- **L'Absent** porte un faux nom : celui d'un autre joueur connecté en
  multijoueur, ou une altération du vôtre en solo. Il envoie des messages
  leurres, se retire pour vous pousser à le suivre, puis révèle sa vraie pose
  et charge. Son cooldown naturel est de **8 minutes** par joueur.
- Les trois scènes peuvent être contrôlées avec
  `/penumbra projectionist test`, `/penumbra rampant test` et
  `/penumbra absent test`. Les photos et albums créés en 0.6.4 restent
  compatibles et sont considérés comme des tirages ordinaires.

## Nouveau en 0.6.4 — la chambre noire

- Tenez la **Caméra VHS** puis faites un **clic gauche**, avec ou sans le viseur
  **C** : le centre de l’image est réellement photographié et un flash visible
  par les joueurs proches accompagne le déclic spatial.
- Le tirage reçu est d’abord noir et développe progressivement les vraies couleurs
  de la capture pendant **10 secondes**, y compris dans l’inventaire et en main.
  Tenez-le et faites un **clic gauche** pour l’ouvrir en grand dans la visionneuse.
- Faites un **clic droit sur un mur avec la photo** pour l’exposer directement,
  ou posez d’abord un **Petit cadre photo** puis insérez-y le tirage. Le cadre se
  fabrique avec un cuir au centre et quatre bâtons en croix.
- Les images sont sauvegardées dans l’album du monde et restent visibles après
  une reconnexion ou un redémarrage serveur. Depuis la 0.8.1, l’album est borné
  à 128 captures par joueur et 2 048 par monde.

## Nouveau en 0.6.3 — le regard répond

- Quatre marteaux 4×4 possèdent maintenant une texture et une progression propres :
  pierre (1 536), or (1 024, très rapide), fer (4 096) et diamant (12 288 de
  durabilité). Les quatre recettes utilisent 5 unités du matériau et 2 bâtons.
- L'Originel (Herobrine) apparaît naturellement toutes les 4–7 minutes à 100 %,
  à une distance visible de 28–52 blocs. Maintenir la caméra VHS cadrée pendant
  environ 3 secondes provoque aussi une scène personnelle à 10–28 blocs, avec
  un cooldown de 90 secondes.
- Un **totem hanté** permet de l'invoquer sans commande : bloc d'or, sable des
  âmes et citrouille sculptée en colonne ; quatre pierres noires polies autour
  de la base, chacune surmontée d'une torche de redstone. Touchez la citrouille
  avec le Sceau de cendre.
- Hôte, Observateur, Originel et Contrefait suivent désormais tous le même ordre :
  regard soutenu → bond 3D de 8–12 ticks → vraie attaque → screamer puissant.
  Le visuel reste personnel et le hurlement est entendu par les joueurs proches.

## Nouveau en 0.6.2 — visages dans le noir

- **Six screamers différents** au lieu de deux, avec Hôte, Observateur, Originel
  et Contrefait. Un sac mélangé empêche de revoir immédiatement la même image.
- Les **apparitions** peuvent maintenant produire une intrusion visuelle courte,
  moins agressive qu'un screamer complet, sans jamais ignorer
  `screamersEnabled`, `flashesEnabled` ou le plafond de volume.
- Le nouvel événement souterrain `cave_face` fonctionne même dans les petites
  galeries. `mining_bait` accepte aussi le marteau et ne compte plus un simple
  bruit comme une apparition réussie.
- **L'Originel (Herobrine)** revient plus souvent, peut se matérialiser devant
  un joueur dans une grotte et y poser une torche de redstone temporaire. Il
  soutient le regard plusieurs secondes avant de disparaître — sauf si on le
  fixe trop longtemps.
- L'Originel et l'Observateur possèdent une réaction 3D au regard : inclinaison,
  bras levés puis bond contrôlé jusqu'au visage, terminé par un screamer ciblé.
  Depuis la 0.6.3, la frappe a lieu à l'arrivée et seul le son atteint les joueurs proches.
- Le rendu conserve le ratio carré des images sur écran large et ultralarge.

## Nouveau en 0.6.1 — caméra VHS et marteau 4×4

- Les jumelles deviennent une **caméra VHS** tenue en main : texture haute
  définition pixel-art, modèle 3D, viseur 4:3, REC, timecode, batterie,
  scanlines et grain analogique respectueux de l'option `flashesEnabled`.
- Le **marteau de mineur** casse un plan 4×4 et conserve toutes les règles
  vanilla importantes. Il se fabrique simplement avec 5 fers et 2 bâtons.
- Les sons d'événement et de jumpscare sont maintenant **spatiaux** : tous
  les joueurs proches les entendent, aucun joueur lointain ne reçoit le paquet.

## Nouveau en 0.6.0 — il se souvient de vous

- **Des pas rejouent VOTRE trajet**, quarante secondes derrière vous. **La porte
  de VOTRE base** grince pendant que vous êtes loin. **Trois coups** sur votre
  vraie porte, la nuit. Un Hôte **visible uniquement aux jumelles**. Et en
  multijoueur : lui, debout, **derrière votre coéquipier**.
- Au réveil, il arrive qu'un coffre ait été **retrié** — rien ne manque, c'est
  bien le problème. Une fois par monde : quelque chose remue sous le lit sans
  déplacer votre point de réapparition, tous les animaux vous fixent en même
  temps, quelqu'un portant presque votre pseudo rejoint la partie.
- Le **Journal incomplet se lit** désormais (clic droit) : il parle de vous,
  avec vos vraies statistiques. En français.
- **Anti-habituation** : plus vous avez vécu un événement, moins il revient —
  la peur reste neuve. La musique vanilla se coupe net quand la traque commence.

## Nouveau en 0.5.0 — la peur qui ne se voit pas

- **Couche ambiante** : nappes sonores subliminales pilotées par votre état de
  tension (le faux calme, lui, est un silence TOTAL), battement cardiaque en
  crise, **voile périphérique** qui resserre la vision (`fogEnabled`) et
  **dérive de FOV** imperceptible (`distortionEnabled`) — le monde respire à
  peine faux, sans jamais rien annoncer.
- **L'Originel** (`originelEnabled`) : une silhouette aux proportions de joueur,
  très loin, qui ne fait JAMAIS face — et qui n'est plus là quand on approche.
  Aucune ombre. Aucun son, sauf un bourdon que vous êtes seul à entendre. Il
  laisse parfois des cairns sur votre chemin (`originelTracesEnabled`).
- **Finale du rituel** : au Sanctuaire de cendre, le Sceau déclenche désormais
  une vraie séquence — silence, murmures convergents, l'unique face-à-face du
  mod, libération. Deux jours de jeu de calme, soit 48 000 ticks ; depuis la
  0.22.0, le temps restant survit à une reconnexion. Puis il réapprend.
- **Personnages refondus** : l'Hôte est désormais calciné (fissures de braise,
  yeux asymétriques), il marche en boitillant, bras morts, et sa tête tourne
  toujours un peu trop loin ; l'Observateur s'incline si lentement qu'on ne
  voit que la correction.
- Les copies imparfaites contiennent maintenant des **imitations de VOS objets**,
  renommées à une lettre près.

## Fonctionnalités — Phase 1 (0.1.0)

- **8 événements d'horreur dirigés** : pas mimétiques derrière vous, souffle dans la nuque,
  faux creeper, torches qui crachotent et s'éteignent, portes qui s'ouvrent seules,
  apparitions distantes de l'Hôte, silence oppressant, interruption de sommeil.
- **Tension persistante par joueur** : sauvegardée avec le monde, restaurée au rechargement.
- **Audio spatial multijoueur** : les sons d'événement sont envoyés uniquement aux
  joueurs assez proches dans la même dimension, puis atténués par leur distance ;
  les joueurs lointains ne reçoivent aucun paquet.
- **Configuration complète** (15 options, fichier `config/penumbra.json`) couvrant fréquence,
  intensité, accessibilité et contenu multijoueur.
- **Commandes d'administration** `/penumbra` pour tester et piloter l'expérience.
- **Modifications du monde sécurisées** : les effets temporaires sont suivis et
  restaurés sans écraser les changements du joueur ; les mixins restent limités
  aux effets visuels côté client.

## Fonctionnalités — Phase 2 (0.2.0)

- **La Caméra VHS** (id historique `binoculars`) : un objet craftable, modèle 3D tenu
  en main et touche **C** par défaut — viseur 4:3, REC, timecode, batterie, scanlines,
  bruit analogique accessible et zoom optique progressif. Observer
  l'Hôte aux jumelles peut provoquer **7 réactions** (immobilisation, tour de tête lent,
  disparition au dézoom, rapprochement entre deux regards, son derrière vous, imitation de
  votre posture, réapparition dans une zone déjà vérifiée). Voir la section
  [Caméra VHS](#caméra-vhs).
- **Marteaux de mineur 4×4** : pierre, fer, or et diamant, avec respectivement
  1 536, 4 096, 1 024 et 12 288 de durabilité. Ils cassent un plan de 16 blocs
  perpendiculaire au regard en conservant protections, Fortune, Silk Touch,
  drops et XP vanilla. Recette commune : 5 matériaux et 2 bâtons.
- **La poursuite** : l'Hôte peut désormais vous **chasser** (état `CHASING`), avec mise en
  scène sonore, accélération progressive, télégraphe d'attaque de 20 ticks et règles
  anti-frustration : avertissement contextuel préalable obligatoire, abandon au-delà de
  48 blocs ou après 60 secondes, puis **cooldown global post-poursuite**
  (`postChaseCooldownTicks`, 6 minutes par défaut) sans aucun événement agressif.
  Fausses retraites possibles : l'Hôte se cache parfois à moins de 24 blocs.
- **Les leurres** : nouveaux événements `lure_door` (grincement lointain), `fake_silhouette`
  (silhouette qui s'évanouit dès qu'on la fixe), `distant_light` (lueur posée au loin puis
  retirée — toujours restaurée), `anticlimax` (montée cardiaque… puis rien) et
  `binoculars_sighting` (apparition à 60–90 blocs visible aux jumelles).
- **Jumpscares retardés** : certaines de vos actions (ouvrir un coffre, regagner votre
  refuge, éteindre une torche, regarder derrière vous, ouvrir l'inventaire, rester trop
  longtemps dans le silence) arment des déclencheurs à retardement (5 à 20 secondes plus
  tard), avec anti-doublon et cooldowns par type.
- **Mémoire comportementale** : l'Hôte apprend vos habitudes — jusqu'à 4 points de refuge,
  vos zones les plus fréquentées, vos usages du lit, vos regards en arrière — et exploite
  cette mémoire pour choisir où apparaître. Tout est persisté avec le monde (format
  compatible avec les sauvegardes Phase 1).
- **12 nouvelles options de configuration** (27 au total), 7 nouveaux sons, et des
  commandes de débogage dédiées (`chase`, `scare`, `binoculars …`).

## Fonctionnalités — Phase 3 (0.3.0)

- **Progression narrative en 7 stades** (« Quelque chose apprend à être vous ») :
  chaque joueur avance du stade 0 (*normalcy*) au stade 6 (*confrontation*) en
  accumulant des **découvertes** persistées avec le monde — première anomalie,
  événements d'imitation (×3), Hôte vu près du refuge, nuits passées au lit (×2),
  copie du refuge découverte, observations de l'Hôte (×4), rituel du sanctuaire.
  Chaque montée de stade est annoncée par une ligne narrative discrète (italique
  gris sombre, jamais explicative). Désactivable via `storyEnabled`.
- **4 structures procédurales rares** : *poste d'observation*, *maison décalée*,
  *puits de pénombre* et *sanctuaire de cendre*. Génération déterministe sur les
  **nouveaux chunks uniquement** (une structure par cellule de
  `structureRarityChunks` chunks, défaut 900, site validé : biome, pente, altitude)
  — **jamais de destruction de constructions existantes**. S'approcher à moins de
  24 blocs révèle la structure (découverte narrative + message discret) ; les
  coffres utilisent des tables de butin dédiées ; des pancartes y laissent des
  messages inachevés. Le *sanctuaire de cendre* s'active en appliquant un
  **Sceau de cendre** sur son pilier central (rituel, stade 5→6).
- **6 objets narratifs** (indices de structures, onglet Ingrédients) : lentille
  fissurée, journal incomplet, fragment d'observation, fil à plomb noirci,
  photographie impossible et morceau de verre — 15 % des morceaux de verre
  révèlent une silhouette dans leur reflet (composant persistant).
- **16 nouveaux événements d'horreur** intégrés à la sélection pondérée du
  directeur (29 au total) :
  - **8 nocturnes** (`nightEventsEnabled`) : silhouette à la fenêtre, pas sur le
    toit, chaîne de torches qui s'éteignent, animaux qui vous fixent, porte de
    l'aube, Hôte sur la colline, fausse lumière de refuge, ombre qui traverse ;
  - **8 souterrains** (`undergroundEventsEnabled`) : coups dans les murs, filon
    illusoire, torche étrangère, souffle au fond d'une galerie, silhouette de
    tunnel, écho de pioche, galerie décalée, salle impossible. Toute modification
    de bloc est enregistrée et **restaurée** automatiquement à échéance (ou via
    `/penumbra debug restoration`) sans jamais écraser une modification du joueur.
- **Copies imparfaites du refuge** (`imperfectCopiesEnabled`, stade ≥ 3) : l'Hôte
  reconstruit votre refuge de mémoire, presque à l'identique — détails faux,
  pancartes inachevées. Depuis la 0.23.0, la première tentative est planifiée
  après deux minutes, avec un nouvel essai au plus une fois par minute si le
  site est refusé. Ce délai est sauvegardé. Après construction : cooldown de
  trois jours de jeu, jamais à moins de 30 blocs d'un autre joueur. Une Maison
  décalée découverte permet aussi d'avancer. Les copies sont restaurables via
  `/penumbra copy restore` ou `/penumbra debug restoration`.
- **Nouvelles commandes** : `/penumbra story get|set`, `/penumbra structure
  locate|spawn <type>`, `/penumbra item give <id>`, `/penumbra copy build|restore`,
  `/penumbra event night|underground <id>`, `/penumbra binoculars landmark`,
  `/penumbra debug restoration`.
- **7 nouvelles options de configuration** (34 au total) et traductions EN/FR
  complètes de tout le contenu Phase 3.

## Fonctionnalités — Phase 4 (0.4.0)

- **L'Observateur** (*the Watcher*, `watcherEnabled`) : une nouvelle entité
  humanoïde de 2,4 blocs, immobile, qui apparaît dans votre champ de vision et
  vous **fixe** — elle ne poursuit pas, elle regarde. Deux événements intégrés à
  la sélection pondérée du directeur (31 au total) :
  - `watcher_stare` : un Observateur apparaît à distance dans votre cône de
    regard ; soutenir son regard pendant `watcherStareSeconds` secondes
    (défaut 3) provoque sa réaction… et octroie le badge *Vous me voyez* ;
  - `watcher_group` (`watcherGroupsEnabled`, très rare) : un arc d'Observateurs
    se matérialise face à vous.
  L'Observateur partage avec l'Hôte le **plafond d'entités** par joueur et
  refuse d'apparaître quand il est atteint.
- **Les badges** (`badgesEnabled`) : 7 avancements Penumbra octroyés
  exclusivement par code (critère `minecraft:impossible`, toast et son vanilla
  automatiques) — *Première fissure* (première anomalie), *Traces de pas*
  (première structure), *Encore là* (poursuite survécue), *Cendre et serment*
  (rituel du sanctuaire), *Il connaît votre nom* (victoire du duel depuis
  la 0.22.0, défi ; les anciennes attributions sont conservées), *Vous me
  voyez* (regard soutenu de l'Observateur, défi), sous la racine *Penumbra*.
- **Protections multijoueur** (audit complet : `docs/fragments/multiplayer_audit.md`) :
  - **plafond d'entités d'horreur** par joueur (`maxHostEntitiesPerPlayer`,
    1–4, défaut 2) appliqué aux décisions du directeur (apparitions d'Hôtes,
    poursuites) et partagé avec l'Observateur ;
  - **anti-spam des sons S2C** : 1 paquet non prioritaire / 3 ticks / joueur,
    voie prioritaire dédiée (`sendSoundPriority`) jamais étranglée pour les
    stingers de poursuite et de jumpscare ;
  - **rate-limit global C2S** : 10 paquets/s/joueur sur les receivers
    jumelles/inventaire, warn borné, purge à la déconnexion ;
  - **durabilité jumelles facturée sur front montant** du zoom uniquement
    (un client modifié ne peut plus la vider en re-spammant).
- **Nouvelle commande** : `/penumbra watcher test` (Observateur dans le cône
  de regard).
- **5 nouvelles options de configuration** (39 au total) : `watcherEnabled`,
  `watcherGroupsEnabled`, `badgesEnabled`, `maxHostEntitiesPerPlayer`,
  `watcherStareSeconds`.

## Caméra VHS

La **Caméra VHS** (id compatible `binoculars`) se crafte avec du verre, du fer et du cuir (recette
début de partie : panneaux de verre en colonnes, lingots de fer et cuir au centre).
Une fois en main ou dans l'inventaire, elle s'active avec la touche **C**.

- **Touche modifiable** : *Options → Contrôles → catégorie « Penumbra » → « Caméra VHS »*.
  Par défaut **C** n'est utilisée par aucune action vanilla ; si un autre mod s'en sert,
  Minecraft affiche le conflit en rouge dans l'écran des contrôles — réassignez simplement
  l'une des deux touches, le comportement reste celui de vanilla.
- **Deux modes** : par défaut il faut **maintenir** la touche pour zoomer
  (`binocularsToggleMode: false`) ; en mode **basculer** (`true`), une pression active,
  une autre désactive.
- **Prendre une photo** : tenez la caméra en main et faites un **clic gauche**,
  avec ou sans le viseur **C**. Une capture peut être prise toutes les 2 secondes.
  Le flash d’écran respecte `flashesEnabled` ; sa particule et le déclic spatial
  restent perceptibles dans le monde par les joueurs proches.
- **Ouvrir l'album** : faites un **clic droit avec la caméra**. La galerie
  sépare vos tirages des photos publiques ; sélectionnez une vignette pour la
  regarder, la renommer, la partager/rendre privée, la marquer **Important** ou
  la supprimer. Son en-tête suit aussi le palier et les catégories uniques de
  votre dossier photographique.
- **Développer et regarder** : le tirage passe du noir à la couleur en
  **10 secondes**, même rangé dans l’inventaire. Tenez-le puis faites un
  **clic gauche** pour afficher la vraie capture en plein écran. Les anomalies
  détectées par le serveur restent masquées jusqu'à la fin de ces dix secondes.
- **Exposer** : faites un **clic droit sur un mur avec le tirage** pour créer un
  petit cadre déjà garni. Vous pouvez aussi fabriquer le cadre séparément avec
  un cuir au centre et quatre bâtons en croix (` S ` / `SLS` / ` S `), le poser
  sur un mur puis y insérer une photo par clic droit.
- **Objet requis (optionnel)** : par défaut la caméra fonctionne sans objet
  (`binocularsRequireItem: false`). Passez l'option à `true` pour exiger la Caméra VHS
  en main ou dans l'inventaire. Avec `binocularsDurabilityEnabled`, chaque activation
  consomme 1 point de durabilité (256 au total).
- **Confort** : zoom progressif réglable (`binocularsZoomLevel`, 2.0–6.0, défaut 4.0),
  transition douce désactivable (`binocularsSmoothZoom`), respiration de visée subtile
  désactivable (`binocularsBreathingMotion`), overlay désactivable
  (`binocularsOverlayEnabled`). L’activation seule ne produit aucun flash et garde
  un volume très faible ; le flash de prise de vue respecte `flashesEnabled`.
- **Originel dans le viseur** : après environ 3 secondes de zoom continu, une
  apparition personnelle est recherchée dans le cadre à 10–28 blocs. Elle ne
  peut revenir par la caméra qu'après 90 secondes.
- **Garanties** : le champ de vision est **toujours restauré** à sa valeur exacte (aucune
  écriture dans `options.txt`), y compris en cas de mort, de changement de dimension, de
  déconnexion ou d'ouverture d'écran. En **troisième personne**, le zoom n'a aucun effet
  tant que vous ne repassez pas en première personne.

## ⚠️ Avertissement et confort

Ce mod contient des **sons soudains et forts** (stingers, souffles, jumpscares) et des effets
visuels potentiellement gênants (secousses de caméra, voile périphérique et visages horrifiques
plein écran). **Personnes photosensibles** : l'option `flashesEnabled` est **désactivée par
défaut** ; ne l'activez qu'en connaissance de cause. Pour une expérience plus douce, passez
`screamersEnabled`, `cameraShakeEnabled`, `distortionEnabled` et `fogEnabled` à `false`, puis
réduisez `jumpscareVolume` et `jumpscareFrequency`. Jouez à volume modéré.

## Installation (joueur)

1. Minecraft **26.1.2**
2. Fabric Loader **0.19.3**
3. Fabric API **0.155.2+26.1.2**
4. Déposez `penumbra-0.13.0.jar` dans le dossier `mods/`.

En multijoueur, installez Penumbra et Fabric API sur le serveur **et sur chaque client**.

Détails : [docs/INSTALL.md](INSTALL.md) — Compilation depuis les sources :
[docs/COMPILE.md](COMPILE.md).

## Configuration

Fichier `config/penumbra.json` (créé au premier démarrage avec les valeurs par défaut).
La configuration versionnée complète automatiquement les anciens fichiers ; lors de la migration
vers le schéma v5, l'original est conservé dans `penumbra.json.pre-v5.bak`. Les 72 réglages
utilisateur sont :

| Option | Type | Défaut | Plage | Description |
|---|---|---|---|---|
| `horrorIntensity` | float | `1.0` | 0.0–2.0 | Multiplicateur global de l'intensité horrifique |
| `eventFrequency` | float | `1.0` | 0.0–4.0 | Fréquence des événements (0 = aucun événement automatique, 4 = 400 %) |
| `eventGracePeriodSeconds` | int | `120` | 0–900 | Délai sans événement automatique après la première connexion |
| `eventMinGapSeconds` | int | `35` | 5–300 | Intervalle de base entre deux sélections automatiques |
| `manifestationMaxGapSeconds` | int | `90` | 30–600 | Délai maximal avant d'imposer une apparition perceptible réelle |
| `jumpscareFrequency` | float | `1.0` | 0.0–2.0 | Fréquence des jumpscares (0 = désactivés) |
| `jumpscareVolume` | float | `0.8` | 0.0–1.0 | Volume des stingers (sécurité : jamais au-delà de 1.0) |
| `cameraShakeEnabled` | bool | `true` | — | Secousses de caméra lors des jumpscares |
| `distortionEnabled` | bool | `true` | — | Effets de distorsion audiovisuelle |
| `flashesEnabled` | bool | `false` | — | Flashs d'écran (**photosensibilité : laisser OFF**) |
| `fogEnabled` | bool | `true` | — | Voile périphérique qui resserre la vision |
| `chasesEnabled` | bool | `true` | — | Autorise les phases de poursuite, y compris celle de la Cavaleuse |
| `worldModificationEnabled` | bool | `true` | — | Autorise les manifestations qui modifient des blocs |
| `sleepEventsEnabled` | bool | `true` | — | Événements pendant le sommeil |
| `hostEnabled` | bool | `true` | — | Active l'entité Hôte et ses apparitions |
| `storyMode` | bool | `true` | — | Alias historique migré vers `storyEnabled` ; ne plus modifier directement |
| `multiplayerContent` | bool | `true` | — | Active les scènes qui impliquent réellement plusieurs joueurs (`behind_other`, contagion de crise) |
| `debugLogging` | bool | `false` | — | Journalisation détaillée (débogage) |
| `binocularsEnabled` | bool | `true` | — | Active la Caméra VHS (touche C) |
| `binocularsRequireItem` | bool | `false` | — | Exige la Caméra VHS en main ou inventaire |
| `binocularsToggleMode` | bool | `false` | — | `false` = maintenir C, `true` = basculer |
| `binocularsZoomLevel` | float | `4.0` | 2.0–6.0 | Facteur de zoom optique de la caméra |
| `binocularsSmoothZoom` | bool | `true` | — | Transition de FOV progressive (ease in/out) |
| `binocularsBreathingMotion` | bool | `true` | — | Dérive de visée subtile pendant le zoom |
| `binocularsOverlayEnabled` | bool | `true` | — | Viseur VHS 4:3 (REC, timecode, scanlines et vignette) |
| `binocularsHostReactionsEnabled` | bool | `true` | — | Réactions de l'Hôte quand il est filmé avec la caméra |
| `binocularsMinZoom` | double | `2.0` | 1.5–10.0 | Borne basse du zoom à la molette |
| `binocularsMaxZoom` | double | `8.0` | 2.0–16.0 | Borne haute du zoom à la molette |
| `binocularsTransitionDuration` | int | `300` | 250–400 | Durée de transition du zoom en millisecondes |
| `binocularsBreathingStrength` | double | `1.0` | 0.0–2.0 | Intensité du mouvement respiratoire |
| `binocularsLensDirt` | bool | `true` | — | Grain et défauts discrets du viseur VHS |
| `binocularsHideHud` | bool | `false` | — | Place l'overlay au-dessus du HUD vanilla pendant le zoom |
| `binocularsHideHand` | bool | `false` | — | Masque la main et l'objet tenu pendant le zoom |
| `binocularsScrollZoom` | bool | `true` | — | Autorise le réglage du zoom à la molette |
| `luresEnabled` | bool | `true` | — | Active les événements leurres |
| `delayedJumpscaresEnabled` | bool | `true` | — | Active les jumpscares retardés |
| `binocularsDurabilityEnabled` | bool | `true` | — | Consomme 1 durabilité par activation (si objet requis) |
| `postChaseCooldownTicks` | int | `7200` | 1200–24000 | Cooldown global anti-agression après une poursuite |
| `storyEnabled` | bool | `true` | — | Progression narrative en 7 stades (découvertes) |
| `structuresEnabled` | bool | `true` | — | Structures procédurales rares (nouveaux chunks) |
| `structureRarityChunks` | int | `900` | 200–5000 | Taille de cellule d'espacement des structures (chunks) |
| `structureEventChance` | float | `0.5` | 0.0–1.0 | Réservé pour compatibilité ; sans effet en 0.6.0 |
| `nightEventsEnabled` | bool | `true` | — | Événements nocturnes (Phase 3) |
| `undergroundEventsEnabled` | bool | `true` | — | Événements souterrains (Phase 3) |
| `imperfectCopiesEnabled` | bool | `true` | — | Copies imparfaites du refuge (stade ≥ 3) |
| `watcherEnabled` | bool | `true` | — | Active l'Observateur et `watcher_stare` (Phase 4) |
| `watcherGroupsEnabled` | bool | `true` | — | Groupes d'Observateurs `watcher_group`, très rares (Phase 4) |
| `badgesEnabled` | bool | `true` | — | Badges/avancements Penumbra (Phase 4) |
| `maxHostEntitiesPerPlayer` | int | `2` | 1–4 | Plafond d'entités d'horreur proches par joueur (Phase 4) |
| `watcherStareSeconds` | int | `3` | 1–10 | Regard soutenu avant réaction/badge de l'Observateur (Phase 4) |
| `originelEnabled` | bool | `true` | — | Active l'Originel : apparitions extérieures et scènes de grotte face au joueur |
| `personalEventsEnabled` | bool | `true` | — | Active les événements personnels de la 0.6.0 |
| `screamersEnabled` | bool | `true` | — | Autorise les neuf visages horrifiques plein écran et leurs sons |
| `contrefaitEnabled` | bool | `true` | — | Active le Contrefait |
| `metaMessagesEnabled` | bool | `true` | — | Autorise les faux messages méta en solo |
| `originelTracesEnabled` | bool | `true` | — | Autorise les petites traces construites par l'Originel |
| `improvedSoundsEnabled` | bool | `true` | — | Active variantes et couches audio enrichies |
| `absentEnabled` | bool | `true` | — | Active les apparitions naturelles de L'Absent |
| `absentSkinCopyEnabled` | bool | `true` | — | Autorise L'Absent à copier le skin complet de l'identité volée |
| `absentMovementImitationEnabled` | bool | `true` | — | Active l'apprentissage et la dégradation de la démarche imitée |
| `absentCameraCounterplayEnabled` | bool | `true` | — | Le flash photographique révèle et étourdit temporairement L'Absent |
| `adaptiveHorrorArcsEnabled` | bool | `true` | — | Active les arcs persistants et contextuels du Directeur 2.0 |
| `refugeContaminationEnabled` | bool | `true` | — | Active la contamination visuelle progressive des refuges |
| `linkedMonsterEventsEnabled` | bool | `true` | — | Active les scènes à deux manifestations et leurs contre-mesures |
| `contextualScreamersEnabled` | bool | `true` | — | Active les screamers dédiés au refuge et à la mimique |
| `refugeContaminationMaxLevel` | int | `4` | 0–4 | Niveau maximal de contamination (0 coupe la progression) |
| `refugeContaminationIntervalSeconds` | int | `300` | 120–1800 | Délai minimal entre deux contaminations d'un joueur |
| `maxConsecutiveAudioEvents` | int | `2` | 1–4 | Nombre maximal de scènes purement sonores consécutives |
| `individualPerceptionsEnabled` | bool | `true` | — | Active les manifestations que seul leur propriétaire peut percevoir |
| `falseCompanionsEnabled` | bool | `true` | — | Autorise les faux compagnons individuels qui copient l'apparence d'un joueur |
| `perceptionIntervalSeconds` | int | `240` | 60–1800 | Cooldown de base du faux compagnon dans le tirage du Directeur |
| `perceptionMaxActive` | int | `3` | 1–8 | Plafond global de perceptions individuelles actives sur le serveur |
| `listenerEnabled` | bool | `true` | — | Active L'Écouteur, la créature qui ne traque que le bruit |
| `listenerCanHarm` | bool | `true` | — | Autorise ses dégâts au contact (le screamer reste actif si faux) |
| `perceptionGrainEnabled` | bool | `true` | — | Grain et lignes de balayage dosés par la tension (client) |
| `peripheralAfterimagesEnabled` | bool | `true` | — | Silhouette rémanente périphérique d'une présence réellement hors champ |
| `perceptionIntensity` | float | `1.0` | 0.0–2.0 | Force globale de la couche de perception, `0` la coupe entièrement |
| `journalEnabled` | bool | `true` | — | Le Relevé, ses pages arrachées et son remplissage automatique |
| `signalPyreEnabled` | bool | `true` | — | Le Bûcher et sa colonne de fumée (le carnet reste obtenable sans lui) |

## Commandes

Toutes les commandes `/penumbra` requièrent le niveau d'opérateur 2 :

| Commande | Effet |
|---|---|
| `/penumbra status` | Affiche état de tension, dernier événement et stade narratif |
| `/penumbra tension <0-100>` | Fixe la tension du joueur |
| `/penumbra intensity <0-100>` | Curseur maître : 50 = normal, 100 = maximum (intensité, événements et screamers) |
| `/penumbra frequency <0-400>` | Règle la fréquence des événements en pourcentage et sauvegarde la configuration |
| `/penumbra jumpscarefrequency <0-200>` | Règle séparément la fréquence des jumpscares et sauvegarde la configuration |
| `/penumbra reload` | Recharge immédiatement `config/penumbra.json` |
| `/penumbra event <id>` | Force un événement du directeur, notamment les scènes coopératives `behind_other`, `paired_echo` et `stolen_name` |
| `/penumbra spawnhost` | Fait apparaître l'Hôte à proximité |
| `/penumbra originel test` | Force une apparition personnelle de l'Originel adaptée à la surface ou à la grotte |
| `/penumbra projectionist test` | Manifeste directement un Projectionniste derrière le joueur si le placement est valide |
| `/penumbra rampant test` | Cherche un plafond ou une paroi de grotte sombre et y fixe un Rampant |
| `/penumbra absent test` | Force une scène de L'Absent près d'un refuge ou à distance |
| `/penumbra listener test` | Fait apparaître L'Écouteur à 22–34 blocs, hors du champ de vision (il arrive toujours en écoute) |
| `/penumbra profile <gentle\|standard\|nightmare>` | Applique un profil d'accessibilité et le sauvegarde, sans toucher aux réglages non cités |
| `/penumbra scuttler cross` | Force une Cavaleuse à traverser le champ de vision sur un sol valide |
| `/penumbra scuttler chase` | Force une Cavaleuse à poursuivre le joueur pendant 3 à 5 secondes sur un sol valide |
| `/penumbra jumpscare <type>` | Déclenche un visuel connu, dont `rampant_screamer`, `absent_screamer`, `chorus_screamer`, `refuge_screamer`, `mimic_screamer` et `listener_screamer` |
| `/penumbra reset` | Réinitialise le profil d'horreur du joueur |
| `/penumbra arc status [joueur]` | Affiche thème, phase, temps restant et pulsations de l'arc persistant |
| `/penumbra arc start <cave\|refuge\|stalker\|mimic\|photographic> [joueur]` | Démarre immédiatement un arc thématique |
| `/penumbra arc clear [joueur]` | Efface l'arc sans supprimer tension, histoire ni dossier photo |
| `/penumbra linked <false_guide\|skitter_omen\|negative_witness> [joueur]` | Force une scène liée avec les vrais garde-fous de placement |
| `/penumbra perception companion [joueur]` | Manifeste un faux compagnon privé pour tester la perception individuelle |
| `/penumbra perception status [joueur]` | Affiche l'identité copiée et le temps restant de la perception active |
| `/penumbra perception clear [joueur]` | Dissipe immédiatement la perception individuelle active |
| `/penumbra chase` | Démarre une poursuite (réutilise un Hôte proche ou en invoque un) |
| `/penumbra scare <id>` | Force un jumpscare retardé (`chest_scare`, `refuge_scare`, `torch_scare`, `lookback_scare`, `inventory_scare`, `quiet_scare`) |
| `/penumbra binoculars test` | Force un zoom de 3 s (solo / hôte LAN uniquement) |
| `/penumbra binoculars host_sighting` | Force l'événement `binoculars_sighting` (zoom actif requis) |
| `/penumbra binoculars reaction <type>` | Force une réaction de l'Hôte (`freeze`, `head_turn`, `vanish_on_unzoom`, `approach_between`, `sound_behind`, `mimic_posture`, `reappear_checked`) |
| `/penumbra binoculars reset` | Réinitialise l'état jumelles du joueur côté serveur |
| `/penumbra story get` | Affiche stade narratif et compteurs de découvertes |
| `/penumbra story set <0-6>` | Fixe le stade narratif du joueur |
| `/penumbra structure locate <type>` | Coordonnées et distance de la structure la plus proche (`observation_post`, `shifted_house`, `penumbra_well`, `ash_sanctum`, `signal_pyre`) |
| `/penumbra structure spawn <type>` | Construit la structure devant le joueur et l'enregistre |
| `/penumbra item give <id>` | Donne un objet (`cracked_lens`, `incomplete_journal`, `observation_fragment`, `blackened_plumb_bob`, `impossible_photo`, `silhouette_glass`, `ash_sigil`, `ash_ward`, `binoculars`, `stone_hammer`, `hammer`, `golden_hammer`, `diamond_hammer`). Le Relevé et les pages arrachées s'obtiennent dans l'onglet créatif « Ingrédients » |
| `/penumbra copy build` | Force une copie imparfaite du refuge (garde-fous de site respectés) |
| `/penumbra copy restore` | Restaure toutes les copies du niveau courant |
| `/penumbra event <id>` avec `held_breath`, `wrong_footfall`, `sound_bait`, `listener_passage` | Force un des quatre événements sonores 0.17.0 |
| `/penumbra event night <id>` | Force un événement nocturne (`window_silhouette`, `roof_steps`, `torch_chain`, `animal_stare`, `dawn_door`, `hill_host`, `false_refuge_light`, `shadow_crossing`) |
| `/penumbra event underground <id>` | Force un événement souterrain (`wall_knocks`, `false_vein`, `foreign_torch`, `deadend_breath`, `tunnel_silhouette`, `cave_face`, `pickaxe_echo`, `shifted_gallery`, `impossible_room`) |
| `/penumbra binoculars landmark` | Décrit la structure la plus proche (direction + distance énigmatique) |
| `/penumbra debug restoration` | Restaure blocs souterrains modifiés et copies imparfaites du niveau |
| `/penumbra watcher test` | Fait apparaître un Observateur à 15–30 blocs dans votre cône de regard (Phase 4) |
| `/penumbra prop <maillage> [échelle]` | Pose un décor 3D devant vous (`effigy` de série ; échelle en pourcentage, 100 par défaut) |
| `/penumbra pyre` | Site du Bûcher, distance, direction, portée du panache et état du sol |
| `/penumbra pyre tp` | Vous téléporte sur le site (charge le chunk) et construit le camp dans la foulée |
| `/penumbra pyre build` | Construit le camp maintenant, ou dit précisément pourquoi il ne peut pas |
| `/penumbra pyre reset` | Oublie la construction du Bûcher pour pouvoir la retester |
| `/penumbra diag [joueur]` | État des conditions d'apparition et liste de celles qui bloquent |

## Licence

MIT — © 2026 Penumbra Team. Voir [LICENSE](../LICENSE).
