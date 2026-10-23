# Problèmes connus — Penumbra 0.28.0

## 0.28.0 — La Seconde Présence

- Compilation, validation des ressources et **515 tests dans 99 suites**
  réussis, sans échec, erreur ni test ignoré. Les 14 nouveaux tests portent
  sur la progression de la scène, son placement et sa classification.
- Essais du 23 octobre sur un serveur local isolé avec un seul client,
  Minecraft 26.1.2, Fabric Loader 0.19.3, Fabric API 0.155.2+26.1.2 et Java 25.
  Un mur, un sol plat, des téléportations et des pas de simulation servent
  de dispositif de diagnostic ; ces essais ne mesurent pas la fréquence
  d'apparition naturelle ni les réflexes d'un joueur.
- Le corps commence caché, contourne le mur, marque son arrêt lorsqu'il est
  vu et recule. Un défaut qui empêchait son recul à courte distance a été
  corrigé puis revérifié en jeu. La variante sans attaque repart en laissant
  le joueur à 20 PV, même après son approche.
- Une poursuite dans la variante hostile arme le bond. Sur une trajectoire
  dégagée, l'Hôte atteint le joueur, effectue sa prise et le projette ; la
  position finale passe d'environ (5,5 ; 121 ; 6,5) à (13,6 ; 121 ; 3,8).
  Le relevé final de 17,17 PV inclut les effets du déroulement de la scène
  et de la régénération : ce n'est pas une mesure isolée des dégâts du coup.
- L'immobilité derrière le mur provoque le départ, sans blessure. Une vitre
  seule et un terrain ouvert sont refusés comme cachette. Ouvrir l'inventaire
  pendant une scène l'annule ; le joueur conserve 20 PV.
- Le flash a été essayé en jeu sur la construction précédente du même travail,
  avant la correction du recul : passage en récupération et 20 PV conservés.
  Il n'a pas été réessayé pendant un bond sur le JAR final. Son raccordement
  au recul de l'Hôte est couvert par la revue du code et les tests existants.
- Le serveur et le client de la vérification finale se sont arrêtés normalement.
  Le gel prolongé du monde par la commande de diagnostic peut faire dépasser
  la limite des messages C2S envoyés par le client ; ces messages sont alors
  ignorés jusqu'à ce que l'horloge du serveur avance.
- Le parcours est court et sur un seul niveau. Les plafonds, trous, escaliers
  et terrains encombrés peuvent empêcher cette rencontre. Aucun bloc n'est
  modifié et aucun chunk n'est chargé par son placement.
- Le multijoueur simultané, la latence Internet, les terrains naturels variés,
  une campagne complète et la peur réellement ressentie restent à évaluer.
  Les gardes de confidentialité et d'interruption ont été relues ; cela ne
  remplace pas un essai prolongé avec plusieurs joueurs.
- Installer **0.28.0 sur le serveur et tous les clients**. Le contenu 0.27.0
  (quatre volumes, cassette, recettes et copies) reste inclus.

Voir [le fonctionnement de la rencontre](SECOND_PRESENCE_0280_FR.md) et
[les recherches consultées](HORROR_RESEARCH_0280_FR.md).

## 0.27.0 — les quatre volumes de Warrick

- Compilation, validation des ressources et **501 tests dans 97 suites**
  réussis, sans échec, erreur ni test ignoré.
- Essais ciblés du 23 octobre sur un serveur local isolé, avec des sites,
  déplacements et inventaires préparés pour le diagnostic : récupération des
  quatre livres, lecture de la cassette pendant 300 ticks, recettes illustrées,
  ouverture d'un livre directement à son texte et guidage vers le Sanctuaire.
- Le premier enquêteur récupère un livre dans un coffre vidé et un autre avec
  le Relevé près d'un coffre détruit. Sa connaissance des quatre livres et de
  la cassette persiste après une mort puis un redémarrage du serveur.
- Un second enquêteur récupère son propre volume I dans le même coffre vide,
  sans hériter de la progression du premier. Un rituel tenté trop tôt indique
  le prochain indice et conserve son Sceau de cendre.
- Une copie de refuge a été construite et inspectée en jeu. Sa restauration
  conserve le coffre dans lequel le joueur a déposé trois diamants. Les blocs
  d'échelle et le toit rehaussé d'un nouveau puits ont aussi été contrôlés ;
  cela ne constitue pas un essai complet de montée au clavier.
- La tentative avec deux clients simultanés a rencontré un plantage natif
  dans `glfw.dll`. Le second client a ensuite été vérifié seul sur le même
  serveur. Ces essais valident deux progressions distinctes, mais ne valident
  pas une session multijoueur simultanée prolongée. Les deux sessions réussies
  et le serveur se sont arrêtés normalement.
- Une campagne naturelle complète, le duel final et la fréquence du placement
  de secours restent à rejouer ou mesurer. Le secours exige de nouveaux chunks
  de terrain admissible ; il ne reconstruit pas les zones déjà générées.
- Les anciens puits ne reçoivent pas automatiquement la nouvelle échelle.
  Les recettes illustrées sont celles fournies avec le mod et ne reflètent
  pas les modifications d'un datapack serveur.
- Installer **0.27.0 sur le serveur et tous les clients** : le contenu et le
  paquet d'ouverture du Relevé ont changé.

Voir [le parcours et les recettes](INVESTIGATION_0270.md).

## 0.26.1 — accélération des bonds

- Compilation, validation des ressources et 456 tests dans 91 suites réussis.
  L'interpolation et l'encodage des charges utilisent désormais des durées
  de 6–9 ticks. Les contrôles de collision existants restent actifs.
- La portée aérienne de l'Absent et du Rampant peut augmenter avec leur
  impulsion horizontale plus forte ; leur hauteur et leur gravité ne changent pas.
- Ce réglage a été vérifié par les tests automatisés et une revue du code.
  Il n'a pas fait l'objet d'une nouvelle session visuelle ou multijoueur.
  Les résultats en jeu ci-dessous sont ceux de la version 0.26.0.

## 0.26.0 — gestes des huit autres créatures

- Compilation, validation des ressources et **456 tests dans 91 suites**
  réussis, sans échec, erreur ni test ignoré. Cela inclut les modèles réellement
  construits en mémoire : poses finies, retour à la pose initiale, articulations
  reliées, raccord avertissement/chute et interruption d'une charge.
- Essais du 22 octobre sur un serveur local isolé, avec un seul client :
  l'Observateur, le Contrefait, l'Originel et l'Absent ont effectué leur geste
  et leur retrait. Les contacts relevés sont respectivement de 4, 5, 6 et 6 PV.
  La Cavaleuse et le Projectionniste n'ont infligé aucun dégât.
- L'Écouteur a effectué son balayage avec un contact de 4 PV. Pour cet essai
  diagnostique, le joueur était dans un bateau et une blessure de 0,1 PV a
  amorcé le bruit : 20 → 19,9 → 15,9 PV. Cela valide cette branche de l'attaque,
  mais ne remplace pas un nouvel essai avec une marche/sprint réellement saisi
  au clavier. Les premières tentatives, sans mouvement reçu, n'ont pas touché.
- Le Rampant a été essayé au sol, puis placé au plafond dans le monde de test.
  Les essais sont commandés et ne mesurent pas la fréquence naturelle des
  apparitions. Le dernier relevé du Rampant ne permet pas d'isoler les dégâts
  d'un seul coup ; aucune valeur de dégâts unique n'en est déduite.
- Six essais supplémentaires ont vérifié le Contrefait et l'Originel : mur
  ajouté après armement, esquive latérale par console et suppression pendant
  la charge. Le joueur conserve 20 PV dans les six cas, puis les entités sont
  retirées proprement. Ces interventions utilisent des pas de simulation ;
  elles ne mesurent pas les réflexes humains ni la latence Internet.
- Le client et le serveur de test se sont arrêtés normalement. Le client de
  développement signale un refus 401 pour la clé de profil et l'autorisation
  Realms de son identité factice ; aucun plantage du mod n'a été observé durant
  cette session.
- Les signatures ont été conçues à partir de pistes scientifiques et des
  mécaniques existantes. Aucune étude de joueurs Penumbra ne démontre encore
  qu'elles effraient davantage ; [sources et limites](MONSTER_ANIMATIONS_FR.md).
- La Cavaleuse et le Projectionniste restent sans dégâts. Les charges du
  Contrefait et de l'Originel peuvent désormais échouer sur un obstacle ou
  une esquive ; cela change l'issue possible de leurs anciennes attaques.
- Les compteurs et états synchronisés ont changé : **même JAR 0.26.0 sur le
  serveur et tous les clients**. L'Hôte conserve sa prise introduite en 0.25.0.
- La validation visuelle avec un second client, la latence Internet, les
  serveurs modifiant les mouvements, les skins/armures variés et une campagne
  complète restent à effectuer. Les tests de modèles ne remplacent pas ces essais.

Voir la section 136 du [protocole de test](TEST_PROTOCOL.md).

## 0.25.0 — saisie physique de l'Hôte

- **404 tests dans 78 suites ont réussi**, sans échec, erreur ni test ignoré,
  avec la compilation et la validation des ressources. Les nouveaux tests
  vérifient notamment les contacts des deux mains, les pieds au sol, les
  transitions de pose, la levée et les limites du jet.
- Essai local à 20 TPS : la victime passe de Y=100 à Y=101,05, puis est
  projetée d'environ 8,68 blocs depuis la prise, avec un seul coup de 6 PV.
  Le témoin connecté reste immobile et à 20 PV. Les angles de la victime
  restent inchangés. La distance du jet dépend du terrain et du mouvement.
- Un plafond bas interrompt la levée sans suffocation ni projection. Un mur
  ajouté pendant la préparation et une esquive latérale de trois blocs
  empêchent le contact. Supprimer l'Hôte pendant une levée mesurée libère
  le joueur au sol, sans dégâts ni jet tardif. Le plafond a été testé à
  20 TPS ; les trois autres interventions ont été préparées à 5 TPS, puis
  leur résultat contrôlé à 20 TPS, avec un déplacement d'esquive par console.
- Deux prises réelles ont été enregistrées, depuis le joueur et en troisième
  personne. Le second client a rencontré
  deux plantages natifs dans `glfw.dll` lors de l'activation de sa fenêtre
  sur ce PC ; le journal ne permet pas d'en attribuer la cause au mod.
  Cela limite la validation visuelle depuis un deuxième client. Aucun essai
  avec latence Internet ou plugin serveur de déplacement n'est revendiqué.
- Le flash pendant la nouvelle saisie, la déconnexion, le changement de
  dimension, deux Hôtes simultanés et la projection contre un mur proche
  restent à vérifier en jeu ; les gardes correspondantes sont présentes
  dans le code et ne constituent pas à elles seules une validation en partie.
- Ces essais utilisent un monde isolé et des placements de diagnostic.
  Ils ne valident ni une campagne complète ni l'équilibrage à long terme.
- Installer **0.25.0 sur le serveur et tous les clients** : le nouvel état
  de saisie synchronisé demande la même version des deux côtés.
- La projection peut entraîner des dégâts de chute sur un terrain en pente
  ou près d'un vide. La saisie ne change pas durablement la gravité du joueur.

Voir la section 135 du [protocole de test](TEST_PROTOCOL.md).

## 0.24.0 — validation ciblée

- **390 tests dans 76 suites ont réussi**, sans échec, erreur ni test ignoré,
  avec `test validatePenumbraResources build` après les corrections de terrain,
  de rendu et de mort pendant une attaque.
- Dans un monde isolé, un vrai clic caméra a fait reculer l’Hôte avant sa
  disparition, sans dégâts au joueur. Les positions ont été relevées côté
  serveur et l’alignement au sol a été contrôlé visuellement.
- Le Rampant est resté au plafond après 40 ticks hors de sa zone, a averti
  pendant 12 ticks au passage du joueur sous lui, puis est tombé et a touché.
- Une charge de l’Hôte armée par le regard a été esquivée en décalant le joueur
  de trois blocs pendant sa préparation. Le corps a reculé puis disparu ; la
  santé est restée exactement identique.
- La suppression d’un Hôte pendant sa préparation ne provoque plus de coup
  posthume : après `/kill` et 40 ticks, aucun acteur ne reste et la santé est
  inchangée. La pose finale du Rampant au plafond et les six réglages du menu K
  ont aussi été contrôlés visuellement.
- Ces essais utilisent **un seul client**, des placements par console et des
  pas de simulation ; le recul au flash a été observé à 5 TPS. Ils ne valident
  ni une campagne complète ni l’équilibrage d’une partie à plusieurs. Les
  essais interrompus par gel réseau, timeout ou mob vanilla ne sont pas comptés.
- Les nouvelles scènes sont personnelles : leur Hôte, leurs sons privés et
  leurs particules sont réservés à leur propriétaire. Les créatures ordinaires
  restent des entités partagées. Les autres joueurs proches peuvent empêcher
  ou annuler une scène ; jouer groupé ne garantit donc pas de la voir.
- Un terrain étroit, un plafond trop bas, des liquides ou un emplacement
  dangereux peuvent empêcher la scène. Le refus n'est pas remplacé par une
  image à l'écran. Les scènes de refuge exigent un encadrement ou un angle
  utilisable ; un champ vide ne suffit pas.
- Une fermeture de lecteur doit suivre une occupation d'environ 1,5 à 30 s.
  Une consultation très brève, une longue lecture, un passage par les réglages,
  un signal périmé ou une autre dimension ne doivent pas libérer un ancien piège.
  Le directeur et la probabilité de déclenchement s'appliquent encore : fermer
  un coffre ne promet pas une apparition à chaque essai.
- Le flash physique a une portée de 12 blocs et un demi-angle de 50°, sans
  extension due au zoom. Il ne repousse que les Hôtes, Observateurs et Rampants
  du photographe, avec un rayon dégagé vers une partie du corps. Les règles
  propres au reflet de la confrontation et à L'Absent restent distinctes.
- Les trajectoires et le recul doivent encore être contrôlés en jeu sur les
  dalles, escaliers, bords de falaise et avec une latence réseau réelle.
  Si le recul est bloqué, la créature peut rester en pose de retrait jusqu'à
  la fin de sa durée plutôt que franchir l'obstacle.
- La brève coupure sonore vise les nappes et battements suivis par Penumbra ;
  elle ne coupe ni le système audio, ni tous les sons Minecraft, ni ceux d'un
  autre mod. Les six réglages personnels de confort ne changent pas la difficulté.
- Le même JAR **0.24.0** est nécessaire côté serveur et chaque client pour
  ces paquets et états synchronisés. La présence de noms de paquets ou d'assets
  hérités ne constitue pas une garantie de compatibilité avec un client ancien.

Le [guide de l'horreur physique](PHYSICAL_HORROR_FR.md) décrit les règles de jeu ;
les contrôles à exécuter se trouvent en fin de [protocole](TEST_PROTOCOL.md).

## 0.23.0 — résultats et limites de validation

- **347 tests dans 68 suites ont réussi**, sans échec, avec la compilation
  finale et la validation des ressources. Les tests couvrent notamment les
  transitions, l'isolation des lecteurs, les paquets et les rechargements NBT.
  Ils ne valident pas à eux seuls une campagne complète.
- En jeu, l'arrivée d'un joueur en survie **sans permission opérateur** a
  confirmé la remise d'un seul Relevé. Le suivi personnel a été ouvert aux
  étapes 0 puis 5 ; cela ne signifie pas que tout le parcours intermédiaire
  a été joué naturellement.
- Au stade 5, le clic rituel sans caméra a été refusé avec le message attendu
  et le Sceau de cendre conservé. Avec la caméra dans l'inventaire, **un vrai
  clic et une seule offrande ont lancé le préambule puis la confrontation** :
  reflet et indications de duel visibles, stade 6, compteur `sanctum_ritual=1`
  et découverte `structure_ash_sanctum=1`. Le badge du rituel est présent,
  celui de la victoire finale reste absent. Le début de la rencontre a été
  capturé le 22 octobre 2026 à 09:57:40 ; aucun duel complet n'est déclaré.
- Un éloignement a ensuite interrompu le duel et fait disparaître son acteur,
  sans badge final. Dans le même essai, le coffre original du sanctuaire a été
  vidé et sa table de butin retirée. Un vrai clic à main vide a ouvert le coffre
  toujours vide et accordé la page « L'immobilité » (message à 09:59:29, capture
  à 09:59:37), sans régénérer de butin. Ce contrôle avec un seul client ne
  constitue pas encore une validation entre deux joueurs simultanés.
- **La persistance a été vérifiée après une vraie sauvegarde forcée, un arrêt
  propre et un redémarrage.** Après la sauvegarde de 10:00:08, la lecture NBT
  confirme le stade 6, `sanctum_ritual=1`, la page `method_stillness`, le marqueur
  de remise du carnet et l'absence de victoire. Le serveur s'est arrêté à
  10:00:39 avec le code 0, a redémarré à 10:01:16, puis le joueur s'est reconnecté
  à 10:01:42 : un seul carnet, caméra conservée, aucun sceau et coffre toujours
  vide. Le Relevé rouvert affiche « La dernière rencontre » avec les consignes
  de reprise et 2/14 notes (la page et une observation de créature accumulée),
  capturé à 10:02:47. Le duel interrompu n'a pas été transformé en victoire.
- Les remises différées avec inventaire plein en survie/créatif, tous les
  états du guide, la découverte coopérative et les autres interruptions
  demandent encore leurs contrôles en jeu. Aucun duel complet
  de la 0.23.0, aucune campagne de survie entière ni validation de latence
  Internet ou de deux clients simultanés n'est revendiqué ici.
- La récupération personnelle d'une page exige un coffre encore présent à
  la position exacte d'une structure enregistrée. Un coffre détruit ou déplacé
  et une structure sortie du registre ne sont pas réparés automatiquement.
  Cette règle ne régénère ni les objets partagés ni les Sceaux de cendre.
- Les plafonds de 256 des connaissances du Relevé et des marqueurs persistants
  ont été retirés. Une victoire dont un ancien marqueur a été perdu peut être
  reconnue à partir de son compteur personnel conservé ; un simple stade 6
  ne suffit pas. La mise à jour n'invente pas des pages déjà perdues dans une
  ancienne sauvegarde. Les limites distinctes du registre des structures restent.
- Le guide décrit les mécanismes activés : couper les événements ou la
  génération des structures peut toujours empêcher une progression naturelle.
  Les réglages de confort locaux ne coupent pas ces mécanismes.
- Le paquet d'ouverture du Relevé transporte maintenant le suivi personnel :
  installer la même version **0.23.0** côté serveur et sur tous les clients.
  La préversion publique demeure 0.19.0 ; la 0.23.0 est préparée localement.

## 0.22.0 — résultats historiques et limites encore pertinentes

- **305 tests dans 59 suites ont réussi**, sans échec ; compilation et
  validation des ressources réussies. Cela ne remplace pas les contrôles en jeu.
- Sur un client A connecté à un serveur dédié local **sans permission
  opérateur**, K ouvre le menu. Choisir Doux puis Annuler conserve les valeurs
  Immersion à la réouverture et ne crée pas de fichier. Appliquer Doux conserve
  ensuite les valeurs à la réouverture et écrit le bon fichier personnel.
  Les empreintes des configurations de base de A et du serveur restent identiques.
  Après arrêt et redémarrage du serveur et de A, K conserve aussi Doux, sans OP.
- L'interface française a été vérifiée et capturée en fenêtre 960×540.
  L'interface anglaise et les plus petites dimensions GUI restent à vérifier.
- Le passage au stade 6 par commande serveur n'a pas accordé le badge à un
  joueur qui ne le possédait pas, sans révocation préalable. Un rituel normal
  déclenché par un vrai clic a ensuite accordé l'accalmie : 47 668 ticks après
  sauvegarde forcée, 47 403 après arrêt propre. Le badge de confrontation
  restait absent. Après redémarrage et reconnexion, l'horloge est passée de
  10 104 à 10 576 ticks et l'accalmie de 47 403 à 46 931 : exactement 472 ticks
  consommés, sans réarmement. Le badge reste absent.
- Un avertissement serveur de retard de 2 056 ms (41 ticks) a été relevé à
  la connexion. Ces contrôles ne prouvent pas un maintien constant à 20 TPS.
- Dans cet environnement d'essai, le client B a échoué deux fois avant
  connexion, y compris après une relance contrôlée : violation d'accès native
  dans GLFW (`glfw.dll+0x10fa1`, `pollEvents` ; dernier rapport
  `hs_err_pid20052.log`). La trace observée ne contient aucune frame Penumbra ;
  l'origine n'est pas établie. **Aucun second joueur n'a rejoint le serveur :
  l'essai à deux clients simultanés n'est pas validé.**
- Aucun duel complet de la 0.22.0, équilibrage en longue survie ou essai de
  latence Internet n'est revendiqué. La première attribution du badge après
  victoire reste à vérifier en jeu.
- Le menu ne change que la présentation locale. Il ne réduit ni les dégâts
  ni la fréquence des scènes. Il met le solo en pause, mais pas le multijoueur.
  Le mode mouvement réduit prime sur les options visuelles qu'il coupe ; les
  valeurs individuelles restent mémorisées. La configuration de base conserve
  ses interdictions, même avec le préréglage Immersion.
- Les deux volumes ne constituent pas un volume général du mod : Ambiances
  couvre les nappes et le cœur suivis, Sons soudains les sons de sursaut
  couverts. Les pas, indices et sons Minecraft gardent leurs réglages propres.
- **La limite d'accalmie de session de la 0.21.1 est corrigée.** Les 48 000
  ticks sont sauvegardés et décomptés avec l'horloge du monde principal, commune
  aux dimensions. Ils s'écoulent pendant l'absence du joueur si le serveur
  avance, mais pas quand il est arrêté. Forcer une tension de 90 ou plus annule
  volontairement ce calme pour les diagnostics.
- Le badge de confrontation demande désormais la victoire. Les badges déjà
  obtenus restent acquis ; une sauvegarde où il existait déjà ne permet pas
  de valider sa première attribution selon la nouvelle règle.
- Ces essais utilisaient la même version **0.22.0** côté serveur et clients ;
  pour la version actuelle, suivre l'exigence de compatibilité indiquée plus haut.

## 0.21.1 — résultats historiques

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
- **Limite historique corrigée en 0.22.0 :** en 0.21.1, la reconnexion
  terminait l'accalmie restante, tandis que le marqueur de victoire persistait.
- Les prises de vue tardives d'une ancienne connexion sont désormais ignorées.
  Les conditions de reconnexion pendant une capture et les différences de
  pilotes graphiques restent à éprouver sur plusieurs machines.
- Aucun gain de FPS chiffré ni test subjectif complet du mixage sonore n'est
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
  entièrement océanique peut néanmoins rester sans site viable. Depuis la
  0.23.0, la remise personnelle et la recette du Relevé permettent de le lire
  sans dépendre de la présence ou du butin de ce camp.
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
  Depuis la 0.23.0, l'ouverture du coffre à sa position enregistrée permet
  néanmoins à chaque lecteur de retrouver sa page sans régénérer le butin.
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
