# Problèmes connus — Penumbra 0.21.1

## 0.21.1 — périmètre de validation

- La photographie de L'Écouteur a été vérifiée en jeu à vitesse normale :
  capture, développement, identification dans l'album et ajout au dossier,
  passé d'une à deux preuves connues. Le rechargement du dossier a été vérifié.
- Un serveur dédié vide a démarré, sauvegardé et arrêté proprement trois fois,
  dont un rechargement du même monde. Le dernier essai inclut la correction
  de visibilité du reflet. Cela ne valide pas les interactions entre
  plusieurs clients, la latence ni une longue partie de survie.
- Le préambule interrompu par un éloignement de plus de 40 blocs a pu être
  relancé sur le même autel. Les autres interruptions ont des tests de
  session ; elles restent à reproduire dans toutes les conditions de jeu.
- Le duel a abouti à une victoire après le flash final, en créatif à 5 puis
  1 tick par seconde. La sauvegarde quittée contient le marqueur de victoire
  et un compteur passé de zéro à un. Après rechargement en jeu avec la
  compilation finale, nouvelle sauvegarde et sortie, le marqueur reste actif
  et le compteur reste à un. Le badge était préexistant : sa première
  attribution n'est pas validée par cet essai. L'équilibrage en survie à
  vitesse normale reste également à éprouver.
- La compilation finale, les tests et la validation des ressources ont
  réussi. Le dernier ajout du message de confirmation des fragments dans le
  chat a été relu et compilé ; aucun nouveau duel n'a été joué après cet ajout.
- **L'accalmie après le rituel est limitée à la session.** Se déconnecter puis
  rejoindre le monde met fin à l'accalmie restante. Le marqueur de victoire
  reste sauvegardé : la reconnexion n'efface pas une confrontation gagnée.
- Les prises de vue tardives d'une ancienne connexion sont désormais ignorées.
  Les conditions de reconnexion pendant une capture et les différences de
  pilotes graphiques restent à éprouver sur plusieurs machines.
- Installer la même version **0.21.1** côté serveur et sur tous les clients.
  Aucun gain de FPS chiffré ni test subjectif complet du mixage sonore n'est
  revendiqué pour cette mise à jour.

## 0.21.0 — limites des essais

- Le duel, l'enquête et la gestion sonore ont des tests automatisés. Les
  longues parties de survie, la latence multijoueur et les mélanges de mods
  restent à éprouver ; les tests de règles ne mesurent pas l'équilibrage réel.
- La confrontation exige un terrain où le reflet peut se tenir. Si aucun
  emplacement chargé avec sol et espace ne convient, elle ne force ni les
  chunks ni le passage dans un mur.
- Le dossier est personnel, même dans la vue des photos publiques. Le lire
  ne met pas le monde en pause. Une ancienne preuve déjà perdue par la limite
  du dossier ne peut pas être inventée à la mise à jour : elle devra être
  photographiée de nouveau si aucun cliché correspondant n'est conservé.
- Depuis la 0.21.0, le client et le serveur doivent utiliser la même version : le paquet
  d'ouverture de l'album transporte désormais aussi le dossier personnel.
- Le faux calme arrête les nappes et le cœur suivis par le client. Il ne coupe
  pas les sons normaux du monde ni les sons positionnels d'une scène déjà lancée.

## 0.20.0 — points à vérifier pendant les essais

- Le nouveau rythme et les fenêtres d'esquive ont des tests automatisés, mais
  l'équilibrage sur une longue partie, les latences multijoueurs et les
  combinaisons de mods demandent encore des retours en jeu.
- La boussole propose le camp, les sites inexplorés ou le site le plus proche.
  Les sites ordinaires doivent déjà avoir été générés dans le monde ; elle ne
  crée pas de structures et ne charge pas de chunks lointains. Le Bûcher est
  l'exception : sa position est calculable avant d'y arriver.
- « Inexploré » signifie non découvert par ce joueur. À l'approche d'un site,
  sa découverte peut donc faire tourner l'aiguille vers le suivant. Le mode
  « site le plus proche » permet de terminer sa fouille ou d'y revenir.

## Le Relevé et le Bûcher 0.18.0 — comportement attendu

- **Un seul Bûcher naturel par monde.** Depuis la 0.19.0, la recherche déplace
  son site vers la terre ferme si le tirage initial tombe en mer. Un monde
  entièrement océanique peut néanmoins rester sans site viable ; le carnet
  reste disponible en créatif.
- **La fumée apparaît avant le camp**, et c'est voulu. Le panache est émis en
  altitude dès qu'un joueur est à moins de 480 blocs ; le camp au sol, lui, ne
  se construit qu'à cent blocs et seulement si le chunk est déjà chargé. Voir de
  la fumée sans rien trouver dessous à mi-chemin est donc normal.
- Le panache utilise la diffusion **forcée** des particules. Un joueur ayant
  réglé ses particules sur « minimales » peut ne pas le voir : c'est une limite
  du client, pas un bug du mod.
- **Le carnet ne se remplit qu'à vue.** Une créature entendue, photographiée ou
  croisée dans le dos ne remplit rien. Il faut qu'elle soit à moins de 34 blocs,
  dans un cône de 70° devant le joueur, et sans obstacle. Le Faux-Semblant fait
  exception : n'ayant pas de type d'entité propre, sa page se remplit au moment
  où il se dissout.
- **Les quatre pages arrachées sont garanties**, une par structure, dans le
  premier pool de leur loot table. Un coffre déjà pillé avant 0.18.0 ne
  contiendra pas la sienne : la table n'est tirée qu'à la première ouverture.
  Il faudra en trouver un autre.
- Une page arrachée s'absorbe **sans posséder le carnet**, et une page déjà
  glissée n'est jamais consommée. Une page dont le composant `penumbra:page_id`
  est absent ou inconnu reste inerte dans l'inventaire au lieu de disparaître.
- **La connaissance est individuelle et vit sur le serveur.** Donner le carnet à
  quelqu'un ne lui transmet aucune page ; il verra son propre relevé. Un client
  modifié ne peut pas déverrouiller de pages : il ne reçoit que ce que le
  serveur lui envoie au moment du clic droit.
- L'écran ne met pas le jeu en pause. Le monde continue de tourner pendant la
  lecture — y compris ce qui s'approche.
- **Une entrée occupe plusieurs feuillets.** Le compteur du pied de page compte
  les feuillets, pas les entrées : « 7 / 43 » est normal. Les feuillets de
  suite d'une même entrée répètent le portrait et le titre, et remplacent le
  sous-titre daté par « (suite) ».
- La pagination est calculée à l'ouverture, avec les largeurs réelles de la
  police active. Changer de police ou d'échelle d'interface repagine donc le
  carnet à la prochaine ouverture — le nombre total de feuillets change, c'est
  attendu.

## L'Écouteur 0.17.0 — comportement attendu

- **L'immobilité totale est un silence total, et c'est voulu.** Un joueur qui ne
  bouge pas, ne frappe pas et n'est pas dans l'eau produit une portée de bruit
  strictement nulle ; une portée nulle n'est jamais audible, même à un bloc de
  la créature. Rester figé pendant qu'elle passe à côté n'est pas un bug : c'est
  la mécanique. La scène s'arrête d'elle-même au bout de 100 secondes.
- **Elle ne regarde jamais le joueur.** Corps et tête sont alignés sur le dernier
  bruit entendu, avec une erreur de ±1,6 bloc. La voir « rater » sa cible de un
  ou deux blocs est le comportement correct.
- **Frapper L'Écouteur ne la blesse pas** : `hurtServer` renvoie toujours faux.
  En revanche le coup est un bruit, et elle repart de la position de son
  agresseur. C'est le pire choix disponible, volontairement.
- **Un bruit derrière un mur reste un bruit.** Elle entend à travers les blocs,
  parce qu'un son porte à travers les blocs. Si le chemin est impraticable, elle
  s'arrête là où elle est et se met à écouter : elle ne traverse rien, ne casse
  rien et ne se téléporte jamais.
- **Le verrouillage se casse en se figeant** (34 ticks de silence complet). Elle
  se remet alors à écouter sur place, éventuellement à un bloc du joueur. Ce
  temps mort est intentionnel.
- **Elle ne meurt pas et ne laisse aucun butin** ; elle ne possède donc pas de
  table de butin. Elle s'éloigne et se dissipe loin du joueur au lieu de
  disparaître devant lui.
- En multijoueur, elle n'écoute les autres joueurs que si `multiplayerContent`
  est actif : sans cela, un tiers ne peut pas attirer la créature sur autrui.
  Les sons de la scène restent audibles par les témoins assez proches.
- **Elle n'est pas sauvegardée.** Un rechargement de monde, une déconnexion du
  propriétaire, un changement de dimension, la mort ou le mode spectateur
  terminent la scène. Une seule scène par joueur, six au total sur le serveur.

## Couche de perception 0.17.0 — comportement attendu

- Le **grain** ne se redessine que tous les trois ticks et son alpha maximal est
  `0x24`. Voir la même répartition de grains pendant un dixième de seconde est
  normal : c'est ce qui évite tout papillotement proche du seuil photosensible.
- Les **lignes de balayage** n'apparaissent qu'à partir de MANIFESTATION. En
  FALSE_CALM, la couche est volontairement coupée : l'image redevient propre.
- La **silhouette rémanente** exige une vraie manifestation Penumbra vivante à
  moins de 40 blocs, à plus de 2 blocs, et dans un angle de 58° à 168° par
  rapport au regard. Sans candidat réel, aucune rémanence n'est jamais inventée.
  Elle est plafonnée à une occurrence toutes les 8 secondes et dure 4 ticks.
- La rémanence est dessinée **par-dessus** le voile périphérique, le grain
  **en dessous** : une ombre au bord de l'écran doit rester lisible même quand la
  vision s'est déjà rétrécie.
- `perceptionIntensity = 0` coupe grain, lignes et rémanence sans toucher au
  voile ni à la dérive de FOV, qui restent pilotés par `fogEnabled` et
  `distortionEnabled`.

## Profils d'accessibilité 0.17.0 — comportement attendu

- Un profil ne touche **que** les options qu'il cite. Les réglages fins non
  mentionnés (jumelles, structures, contamination des refuges, perceptions
  individuelles…) conservent exactement la valeur choisie par le joueur.
- `sanitize()` est rejoué après chaque application : le mode streamer continue
  donc de forcer flashs et messages méta à l'arrêt et de plafonner le volume,
  même après `/penumbra profile nightmare`.
- Le profil est écrit sur disque immédiatement. Repasser en `standard` rétablit
  le rythme de référence mais **ne restaure pas** les réglages personnalisés
  écrasés par un profil précédent : il n'y a pas d'historique.

## L'Absent 0.11.0 — comportement attendu

- La peau, les couches de vêtements et la géométrie de bras viennent du profil
  officiel copié. Si la texture n'est pas encore en cache, le skin par défaut
  correspondant à l'UUID peut apparaître brièvement pendant son chargement.
- L'armure et les objets copiés sont une photographie visuelle prise au début de
  la scène. Ils ne donnent aucun pouvoir à L'Absent, ne sont jamais déposés et
  peuvent volontairement différer si le vrai joueur change ensuite d'équipement.
- Les coups de pioche et gestes vers une porte sont des leurres : le son est
  spatial, mais aucun bloc n'est cassé et aucune porte n'est ouverte.
- Le flash ne révèle L'Absent que dans un rayon de 24 blocs, dans le cône avant
  et avec une ligne de vue. Une photo prise de dos ou à travers un mur reste donc
  normale. La silhouette supplémentaire n'apparaît qu'après environ 58 % du
  développement du tirage.
- Le screamer final exige que L'Absent ait terminé sa révélation puis atteint sa
  cible. Les joueurs proches peuvent entendre les sons de la scène, mais ne
  reçoivent ni l'attaque ni l'écran de screamer du propriétaire.

## Enquête photographique 0.10.0 — comportement attendu

- Le clic de la caméra crée d'abord un ticket serveur qui fige le regard et les
  preuves. Les pixels du client doivent revenir avec la même séquence dans les
  **60 ticks** ; un paquet tardif, rejoué ou déjà consommé est ignoré. Cette
  fenêtre protège l'album contre les doubles captures et les preuves choisies
  après le flash.
- Une preuve physique exige une manifestation Penumbra vivante, visible par le
  photographe et dans le cône central, à
  **64 blocs maximum**. Une créature derrière le joueur, masquée par un mur ou
  privée pour un autre joueur ne doit donc pas apparaître dans son dossier. Les
  yeux, le centre et le bas sont testés séparément : une partie visible derrière
  une vitre ou un rebord peut suffire, contrairement à une silhouette totalement cachée.
- Le tirage aléatoire historique du Projectionniste reste distinct : environ
  15 % des captures peuvent recevoir cette anomalie photographique même si
  aucune entité Projectionniste n'était physiquement dans le cadre.
- Les catégories sont intentionnellement masquées pendant les **200 ticks** de
  développement. L'album indique « Analyse » et la visionneuse n'affiche aucune
  identité avant la fin ; le serveur retient aussi les identifiants et n'envoie
  son verdict léger qu'après l'échéance. Ce délai suit le temps du monde, pas
  l'horloge réelle.
- Le dossier du propriétaire est réconcilié à l'ouverture de l'album. Regarder
  seulement un tirage développé peut révéler son texte dans la visionneuse,
  mais l'indice et le compteur du dossier apparaissent lors de la prochaine
  ouverture ou actualisation de l'album.
- Une catégorie ne compte qu'une fois, même si elle figure sur plusieurs
  photos. Les paliers sont exactement 1, 3, 5 et 7 catégories uniques ; le
  maximum affiché reste huit. Supprimer, dépublier ou céder le tirage source
  n'efface pas une preuve déjà acquise par son propriétaire.
- **Important** est un classement privé du propriétaire, pas un vote public.
  Les favoris remontent uniquement dans « Mes photos » ; l'onglet public reste
  ordonné du tirage le plus récent au plus ancien, marqueur Important ou non.
- Une page ignorée par le limiteur est retentée trois fois. Après trois absences
  de réponse, l'album sort de « Chargement » et demande de changer de page pour
  recommencer ; il ne reste plus figé indéfiniment.

## Album et scènes coopératives 0.9.0 — comportement attendu

- L'album s'ouvre uniquement avec un clic droit lorsque la Caméra VHS est dans
  la main utilisée. Il charge six vignettes à la fois, espacées d'environ
  260 ms ; voir apparaître brièvement les emplacements noirs est normal.
- « Photos publiques » signifie visible depuis l'album de tous les joueurs.
  Une photo privée reste également lisible par une personne qui possède un
  tirage physique ou regarde son cadre proche. Seul le propriétaire peut
  renommer, publier, rendre privée ou supprimer l'archive.
- Supprimer l'archive rend volontairement muets les anciens tirages ou cadres
  qui référencent encore son UUID. Le tirage du propriétaire est retiré de son
  inventaire et le quota est immédiatement libéré.
- `behind_other`, `paired_echo` et `stolen_name` utilisent un compagnon valide
  dans la même dimension, puis revérifient distance et connexion pendant la
  scène. Sans compagnon, leur variante solo s'exécute ; les commandes forcées
  restent hors du verrou automatique de groupe.
- Les trois finales nommées restent soumises à `screamersEnabled`,
  `flashesEnabled`, `jumpscareVolume` et aux cooldowns critiques comme les
  visages historiques.

## Multijoueur 0.8.1 — comportement attendu

- La coordination de **4 secondes** ne concerne que les scènes automatiques
  lourdes de joueurs situés dans la même dimension et à **32 blocs ou moins**.
  Les sons courts et ambiants ne réservent pas ce verrou ; deux groupes
  éloignés, deux dimensions ou un joueur solo évoluent indépendamment.
- La priorité de sélection tourne entre les joueurs connectés. Elle améliore
  l'équité mais ne garantit pas qu'un événement naturel précis apparaisse dans
  un délai fixe : ses conditions, sa chance et son cooldown restent appliqués.
- Les commandes opérateur qui forcent une scène contournent volontairement la
  coordination, les probabilités et certains cooldowns. Elles peuvent donc
  superposer plusieurs manifestations et ne mesurent pas le rythme naturel.
  Un joueur cible peut être ajouté à la fin de ces commandes ; sans lui, la
  cible reste l'auteur de la commande.
- Les entités physiques partagées restent visibles à portée de tracking, mais
  leur cible et leurs dégâts sont strictement attachés au propriétaire de la
  scène. Les visuels de jumpscare ne sont pas envoyés aux témoins.
- Un son positionné est reçu près de son origine ou, pour partager un leurre
  lointain, par un compagnon de la même dimension situé à **24 blocs ou moins**
  du joueur ciblé. Les sons ordinaires en rafale sont limités à un paquet tous
  les 3 ticks par route ; deux paquets prioritaires strictement identiques dans
  le même tick sont dédupliqués.
- Les réservations et caches temporaires sont nettoyés à la déconnexion et à
  l'arrêt du serveur. Les cooldowns de gameplay explicitement conservés par un
  gestionnaire ne doivent pas être confondus avec ces états réseau transitoires.

## Ajouts 0.8.0 — limites connues

- Le Sceau de bannissement (`ash_ward`) recherche à **16 blocs maximum**
  uniquement les entités de type
  `Monster` dont le namespace est exactement `penumbra`. Il ne bannit donc ni
  joueur, animal, cadre photo, monstre vanilla, ni créature Penumbra qui ne
  serait pas classée comme monstre. Une utilisation réussie consomme un sceau
  hors mode Créatif et impose 60 ticks de cooldown ; sans cible valide, la pile
  reste intacte et le cooldown n'est que de 20 ticks.
- L'Originel et le Contrefait sont des scènes privées : le sceau d'un autre
  joueur ne peut ni les révéler par ses particules ni les supprimer. Les autres
  manifestations physiques partagées dans le rayon restent bannissables.
- La Cavaleuse naturelle exige la fin de la grâce, le stade narratif 1 ou plus, le mode
  non Paisible, un sol sec et soit une grotte sombre, soit une nuit sombre
  (lumière de bloc ≤ 6). Sa décision est prise toutes les 5 secondes avec une
  chance de base de 1/32 à pression normale et un cooldown de 2 minutes par
  joueur : le ratio **70 % traversée / 30 % poursuite** ne se juge donc que sur
  un grand échantillon de scènes effectivement créées.
- Une seule Cavaleuse peut appartenir à un joueur et le plafond global est de
  12. La poursuite dure 3 à 5 secondes ; si `chasesEnabled` est faux, une scène
  naturelle tirée en poursuite devient une traversée. La créature ne peut pas
  attaquer : la toucher la fait disparaître immédiatement, sans dégâts, butin
  ni riposte.
- `/penumbra scuttler cross` et `/penumbra scuttler chase` ignorent chance,
  cooldown et conditions naturelles pour faciliter les tests. Elles exigent
  néanmoins un joueur vivant non spectateur, le mode non Paisible, un budget
  d'entités disponible et un vrai sol sans collision ; leur succès ne mesure
  pas la fréquence naturelle.
- Le Rampant ne s'accroche qu'à des blocs solides réellement présents. Une
  paroi discontinue, la perte du support, une lumière de bloc supérieure à 7
  ou une recherche trop longue provoque une sortie bornée vers le sol ou la
  fuite. Les transitions mur/plafond ne téléportent pas la créature et ne
  modifient aucun bloc, mais une géométrie très irrégulière peut écourter la
  séquence d'escalade.
- Les douze screamers plein écran forment un sac mélangé complet, sans
  répétition immédiate même entre deux cycles. L'intrusion visuelle plus légère
  `apparition` conserve volontairement son sous-ensemble historique 4 à 6 ;
  elle ne choisit pas les variantes 7 à 12. `screamersEnabled: false` masque les
  douze images, et les cooldowns ainsi que le plafond de volume restent actifs.
- Rampant et Cavaleuse sont des entités serveur physiques : les joueurs à
  portée de tracking peuvent les voir et entendre leurs sons spatiaux proches,
  même si la scène et sa cible restent rattachées à un seul joueur.

## Ajouts 0.7.0 — limites connues

- Les photos du Projectionniste sont tirées aléatoirement côté serveur à
  **15 %**. Cette proportion est une moyenne, pas la garantie d'obtenir une
  photo hantée toutes les sept captures. La silhouette ne commence à remonter
  que pendant le développement et la manifestation exige ensuite une photo
  complètement développée, tenue en main et observée 1,5 seconde dans la
  visionneuse.
- Le Projectionniste ne force aucun chunk et n'apparaît que si un sol chargé,
  sans collision et visible existe derrière le joueur. Si le placement échoue,
  la photo reste valide et aucune manifestation ni cooldown de 90 secondes
  n'est consommé. `/penumbra projectionist test` applique les mêmes contraintes
  de placement.
- Le Rampant naturel est limité à l'Overworld, sans accès au ciel, au stade de
  contenu 2 ou plus et dans une grotte sombre disposant d'un vrai plafond ou d'une
  paroi ainsi que d'un sol de réception. `/penumbra rampant test` ignore la
  grâce, le stade, la chance et le cooldown, mais pas la géométrie ni le mode
  Paisible ; son succès ne valide donc pas à lui seul le rythme naturel.
- L'Absent a lui aussi besoin d'un emplacement chargé et sans collision. Près
  d'un refuge, son repère est un lit, une porte en bois ou un coffre ; sinon il
  cherche un sol plus distant. En multijoueur, son faux nom est volontairement
  celui d'un autre joueur connecté, ce qui peut permettre à un groupe attentif
  de démasquer le leurre.
- Projectionniste, Rampant et Absent sont des entités serveur réelles, donc
  visibles par les joueurs à portée de tracking. Leur propriétaire reste la
  seule cible de la scène : dialogues, effet d'obscurité, attaque et image de
  screamer ne sont pas recopiés sur les témoins. Les souffles, décrochages,
  pas, voix et cris restent volontairement des sons spatiaux de proximité.
- Les cooldowns naturels du Rampant (**6 min**) et de L'Absent (**8 min**)
  survivent à une reconnexion tant que la session serveur continue, mais pas à
  un arrêt complet du serveur. Le cooldown photographique du Projectionniste
  (**90 s**) est réinitialisé à la déconnexion. Les trois commandes de test
  contournent ou ne consomment pas ces cooldowns.
- Le champ `projectionist` est optionnel et vaut `false` lorsqu'il est absent :
  les objets et albums 0.6.4 restent compatibles, mais une ancienne photo ne
  peut pas devenir hantée rétroactivement. C'est le comportement attendu.

## Ajouts 0.6.4 — limites connues

- La photo capture le centre du framebuffer déjà rendu, en 128×72 : elle est
  volontairement petite, nette en pixel-art et légère sur le réseau, mais ne
  remplace pas une capture d’écran haute résolution.
- Le flash plein écran dépend de `flashesEnabled` et reste désactivé par défaut
  pour l’accessibilité. La particule de flash et le déclic spatial existent
  néanmoins dans le monde pour les joueurs proches.
- L’album est plafonné à 128 photos par joueur et 2 048 par monde. La 129e/2 049e
  capture est refusée proprement. Depuis la 0.9.0, le clic droit avec la caméra
  ouvre l'écran de gestion et la suppression définitive libère une place.
- Un joueur ne peut demander au serveur que les pixels d’une photo qu’il
  possède ou d’un cadre effectivement suivi à moins de 160 blocs. Une demande
  refusée est retentée toutes les 3 secondes lorsque le joueur se rapproche.
- La datation du développement utilise le temps du monde : si le monde ne
  tourne pas, le tirage ne développe pas hors ligne.

## Ajouts 0.6.2 — limites connues

- Les six images de screamer sont volontairement intenses. Le réglage
  `screamersEnabled: false` supprime entièrement les visages plein écran ;
  `flashesEnabled` reste désactivé par défaut.
- Les apparitions de l’Originel et du Contrefait sont envoyées uniquement à leur
  propriétaire. L’Observateur reste une entité physique visible à portée, mais
  son saut visuel cible uniquement son propriétaire ; les personnes proches
  peuvent entendre le son spatial sans devenir sa cible.
- La torche de redstone d’une scène de l’Originel est temporaire et n’est
  restaurée que si aucun joueur ne l’a modifiée entre-temps.

## Ajouts 0.6.1 — limites connues

- Le marteau choisit son plan 4×4 d'après l'axe dominant du regard. Près d'une
  diagonale parfaite, un petit changement d'angle peut donc faire basculer le
  plan entre horizontal et vertical ; c'est volontaire pour garder une zone
  déterministe.
- Le viseur VHS est procédural et validé automatiquement côté ressources, mais
  son cadrage doit encore être contrôlé manuellement sur les combinaisons de
  résolution, ratio et GUI scale prévues dans le protocole de test.
- Les sons d'événements sont partagés entre joueurs proches dans la même
  dimension, avec atténuation positionnelle et rayon compagnon de 24 blocs
  autour de la cible. Les images de jumpscare et les dégâts restent personnels
  au joueur ciblé.

## Phase 6 — limites connues (0.6.0)

- **Validation runtime à renouveler pour chaque release** : le serveur
  dédié 26.1.2 a été démarré jusqu'à `Done`, puis arrêté proprement le
  18 juillet 2026. Le contrôle visuel `runClient` (rendus, HUD et commandes)
  reste à refaire après chaque changement graphique.
- **`binoculars_reveal` et invisibilité vanilla** : la révélation repose sur
  `Entity.setInvisible`, propriété GLOBALE — en multijoueur, un autre joueur
  proche verrait aussi l'Hôte pendant la fenêtre de révélation (cohérent avec
  la limitation « visible par tous » documentée en Phase 4).
- **`echoed_path` et le ruban de trajet** : le ruban (90 échantillons, 1/s)
  est transitoire — vidé au redémarrage serveur et à la reconnexion ;
  l'événement redevient éligible après ~1 minute de jeu.
- **`fake_join` et logs** : le faux message de connexion est un message chat
  pur — il n'apparaît PAS dans les logs serveur ni dans la liste des joueurs
  (TAB) ; un joueur attentif peut le démasquer ainsi. Assumé : le doute est
  l'effet recherché. Option dédiée `metaMessagesEnabled` pour tout couper.

## Phase 5 — limites connues (0.5.0)

- **Note historique 0.5.0** : sa validation syntaxique partielle est désormais
  remplacée par le build global 0.6.2 et la tâche
  `validatePenumbraResources`. Les limitations fonctionnelles ci-dessous
  restent toutefois valables.
- **Nappe ambiante par segments** : la nappe est jouée en segments de 8 s à
  fondus intégrés toutes les ~5 s (crossfade par recouvrement) via
  `playLocalSound` — pas de `SoundInstance` tickable (aucune API nouvelle).
  Conséquence : un changement d'état se fait entendre au prochain segment
  (≤ 5 s de latence), jamais en fondu continu.
- **`fogEnabled` = voile périphérique** : l'option est implémentée comme un
  assombrissement des bords de l'écran (HUD), pas comme un brouillard de
  distance monde — il n'existe pas de hook de fog vérifié en 26.1.2/Fabric
  0.155.2. La sémantique (« la vision se resserre ») est documentée dans le
  README.
- **Animations sans état de comportement** : les modèles ne reçoivent pas le
  `HostBehavior` synchronisé (il faudrait un `RenderState` personnalisé via
  `extractRenderState`, non vérifié dans les sources) ; la refonte 0.5.0 est
  donc indépendante du comportement (boitillement, bras morts, sur-rotation,
  tremblements périodiques). Raffinement candidat pour une phase ultérieure.
- **Traces de l'Originel jamais restaurées** : c'est un choix de design (la
  preuve du passage), documenté aussi dans le CHANGELOG. `worldModificationEnabled`
  et `originelTracesEnabled` les désactivent.

## Compilation vérifiée

Le projet compile contre Minecraft 26.1.2 (namespace officiel non obfusqué) avec
Fabric Loom **1.17.16**, Gradle **9.6.1** et un JDK **25**. La validation de
ressources est branchée sur `check`. Un avertissement de dépréciation subsiste
dans l'enregistrement de génération des structures ; il est non bloquant.

## Phase 4 — limites connues (acceptées, non corrigées)

- **Apparitions visibles par tous en multijoueur** : les entités d'horreur (Hôte,
  Observateur) invoquées pour un joueur sont des entités réelles du monde,
  **visibles par tous** les joueurs à portée de tracking. L'individualisation est
  narrative (ciblage et tension), tandis que les sons sont volontairement spatiaux
  et audibles par les joueurs proches. C'est un **choix de design
  assumé**, documenté dans [fragments/multiplayer_audit.md](fragments/multiplayer_audit.md)
  (§3) — des apparitions strictement privées exigeraient des entités client-side,
  chantier renvoyé à une phase ultérieure.
- **`multiplayerContent`** contrôle uniquement les scènes qui utilisent
  réellement plusieurs joueurs (`behind_other`, contagion de crise). Les
  manifestations physiques ordinaires restent permises et conservent leur cible.

## Phase 3 — limites rapportées (acceptées, non corrigées)

Limites connues des modules Phase 3, consignées à l'intégration :

- **`hill_host` sans `setNoAi`** : l'Hôte posé sur la colline n'est pas figé par
  `setNoAi(true)` — choix assumé pour éviter le défaut de la « statue permanente »
  si l'événement était interrompu ; l'Hôte conserve son IA normale (il peut donc
  s'écarter ou disparaître selon les règles habituelles).
- **`dawn_door` non restaurée** : la porte déplacée/ouverte à l'aube n'est pas
  remise dans son état d'origine (précédent `door_anomaly` de Phase 1, même
  philosophie de modification légère assumée).
- **`minecraft:ash` inexistant** : il n'existe pas de bloc `minecraft:ash` en
  26.1.2 — la cendre des structures et événements est matérialisée par
  `black_wool`, `coal_block` et `soul_soil` (substituts documentés).
- **Puits de pénombre** : l'eau du fond du puits est immobile (pas de mécanique
  de courant ni de source renouvelée).
- **Refuge « le plus fréquent »** : la notion de refuge principal utilisée par les
  copies imparfaites et les points d'observation est un **proxy** — le refuge le
  plus **récemment promu**, pas un comptage exact des fréquentations.
- **`impossible_room` et `observation_fragment`** : l'objet `observation_fragment`
  est résolu par lookup dans le registre à l'exécution ; si l'objet est absent du
  registre (datapack ou intégration incomplète), le butin est simplement ignoré —
  aucune erreur, aucun remplacement.

## Phase 3 — points d'attention

- **Génération de structures** : uniquement sur `ServerChunkEvents.CHUNK_GENERATE`
  (chunks neufs). Les mondes créés avant la 0.3.0 ne verront des structures que
  dans les chunks jamais visités — aucune rétro-génération, aucune destruction de
  constructions existantes. Depuis la 0.11.0, ce callback ne touche jamais le
  `ServerLevel` tant que son `LevelChunk` n'est pas publié : une file bornée est
  drainée en fin de tick avec `getChunkNow`, ce qui évite l'auto-interblocage du
  futur `FULL` et le crash watchdog observé sur serveur dédié.
- **Zones déchargées** : les copies de refuge, galeries décalées et faux filons
  sont simplement reportés si toute leur zone de scan n'est pas chargée. Ils ne
  créent ni ticket de chunk ni génération synchrone depuis le tick serveur.
- **Restauration souterraine** : une restauration n'a lieu que si le bloc est
  encore exactement celui posé par l'événement (comparaison d'instances
  canoniques) ; toute modification du joueur inhibe la restauration de ce bloc.
  `/penumbra debug restoration` force la restauration de tout ce qui est intact
  dans le niveau courant (souterrains + copies).
- **Progression narrative** : les compteurs de découvertes s'incrémentent même
  quand `storyEnabled` est faux (comptabilité brute), mais les règles de montée
  de stade et `StoryManager.getStage` restent inactifs (stade lu = 0).
  `/penumbra story set` persiste toujours la valeur demandée.

## Choix d'API 26.1 confirmés

Points notables retenus après vérification dans les sources 26.1.2 :

- `ResourceLocation` est renommé `net.minecraft.resources.Identifier`.
- Persistance : `SavedDataType` (record top-level : `Identifier`, `Supplier`, `Codec`,
  `DataFixTypes` nullable) + `SavedDataStorage.computeIfAbsent(SavedDataType)` ; la
  (dé)sérialisation est pilotée par le codec, sans méthode `save(...)` à surcharger.
- La particule de cendre étend `SingleQuadParticle` et `ParticleProvider.createParticle`
  reçoit un `RandomSource` final.
- Fabric : `PayloadTypeRegistry.clientboundPlay()`, `CreativeModeTabEvents`
  (`fabric-creative-tab-api-v1`), `ParticleProviderRegistry`, `ModelLayerRegistry`,
  `HudElementRegistry`.
- **Phase 2** : il n'existe pas de `Level.getEntitiesOfClass` en 26.1 — la recherche
  d'entités par classe passe par
  `Level.getEntities(EntityTypeTest.forClass(X.class), AABB, Predicate)`.
- **Phase 2** : l'ordonnancement d'une tâche sur le thread client se fait via
  `Minecraft.getInstance().execute(Runnable)` (hérité de `BlockableEventLoop`).

## Phase 2 — points d'attention

- **Zoom jumelles et FOV** (corrigé en 0.5.0) : le mixin injecte au `RETURN` de
  `Camera.calculateFov(F)F` et divise la valeur de retour — la projection est
  donc construite directement avec le FOV zoomé (l'ancienne injection au
  `RETURN` de `update` arrivait APRÈS `setupPerspective` et n'avait aucun effet
  sur le monde : bytecode vérifié au désassemblage du jar 26.1.2). Rien n'est
  jamais persisté : le mod **n'écrit jamais `options.txt`**. En cas de
  fermeture brutale du jeu pendant un zoom (crash, kill du processus), le FOV
  affiché au redémarrage reste celui sauvegardé par le jeu, donc non affecté
  par le mod. La main garde son FOV vanilla pendant le zoom
  (`calculateHudFov` non modifié ; `binocularsHideHand` pour la masquer).
- **Touche C** : aucune action vanilla ne l'utilise par défaut, mais un autre mod peut la
  revendiquer. Minecraft affiche alors le conflit en rouge dans *Options → Contrôles* ;
  réassignez l'une des deux touches (voir README, section Jumelles).
- **Troisième personne** : le zoom n'a volontairement aucun effet en vue à la troisième
  personne ; il se (ré)applique au retour en première personne si la touche est maintenue.
- **`/penumbra binoculars test`** : le zoom forcé ne fonctionne que depuis le client
  intégré (solo ou hôte LAN) — aucun paquet réseau dédié n'existe pour ce test. Sur
  serveur dédié ou pour un invité LAN, la commande échoue proprement avec un message
  explicite ; les autres commandes jumelles (`host_sighting`, `reaction`, `reset`)
  fonctionnent partout.
- **Poursuite et anti-frustration** : après `endChase`, aucun événement agressif (chase,
  scare, `distant_sighting`) ne se déclenche pendant `postChaseCooldownTicks`. Les
  commandes de débogage (`/penumbra chase`, `/penumbra scare`) contournent ces garde-fous
  par conception.
- **Mémoire comportementale** : le codec v2 utilise des champs optionnels avec défauts ;
  les sauvegardes créées en 0.1.0 se chargent sans migration ni perte.
- **Leurres et restauration** : `distant_light` retire systématiquement la source posée
  après 1200 ticks (même pattern de capture d'état que les torches de Phase 1) ; si le
  bloc a été remplacé entre-temps, la restauration n'écrase rien.

## Sons placeholder procéduraux

Les **25 fichiers OGG** fournis sont des sons procéduraux générés par script
(souffles filtrés, clusters dissonants, nappes graves). Ils sont valides, mono
44,1 kHz et remplissent leur rôle, mais mériteraient un sound design final.
`validatePenumbraResources` refuse maintenant un fichier absent ou non déclaré.

## Structures en Phase 3

La Phase 3 (0.3.0) introduit les **4 structures procédurales** (poste d'observation,
maison décalée, puits de pénombre, sanctuaire de cendre) générées rarement sur les
chunks neufs de l'overworld. Le mod n'ajoute ni biome ni dimension personnalisée
(voir [ROADMAP.md](ROADMAP.md)).

## Divers

- Les seuls mixins du mod sont **client-only** et dédiés aux jumelles (`CameraFovMixin`,
  `GameRendererHandMixin`, `MouseHandlerScrollMixin`) ; aucune injection côté serveur.
- Le mod n'a pas de dépendance optionnelle (Mod Menu non requis) : la configuration se
  fait par édition de `config/penumbra.json` (72 options en 0.13.0, plus
  `configVersion`, schéma v5). `storyMode` est un alias historique de
  `storyEnabled` et `structureEventChance` reste réservé pour compatibilité.

## 0.15.0

- **Faux-Semblant orphelin après un crash** : si le serveur s'arrête brutalement
  en pleine scène, la créature vanilla pilotée reste dans le monde et redevient
  un animal normal. Seul résidu possible : un animal muet (défaut `SILENT`,
  `Silent:1b` en NBT). Aucun bloc ni inventaire n'est affecté. Un arrêt propre
  purge l'état sans laisser de créature pilotée. Correctif candidat : marquage
  NBT + balayage au démarrage pour supprimer les orphelins d'un crash.
