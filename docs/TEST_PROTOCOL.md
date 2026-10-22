# Protocole de test — Penumbra 0.1.0 à 0.23.0

Chaque étape indique le résultat attendu. Toute commande `/penumbra` requiert le niveau
d'opérateur 2. Avant de tester les événements, monter la tension si besoin :
`/penumbra tension 80`.

Les sections 120 à 122 couvrent la 0.22.0 ; leur validation en jeu est partielle,
avec les résultats observés consignés avant la section 120.
Les sections 123 à 128 couvrent la 0.23.0 ; le bilan qui les précède distingue
les contrôles confirmés des cas restant à jouer.
Le menu de confort **K** ne requiert aucune permission opérateur. Pendant
les tests d'accalmie, ne pas forcer une tension de 90 ou plus : cette commande
de diagnostic annule volontairement la récompense de calme.

## 1. Démarrage sans crash

1. Lancez le client (ou `./gradlew runClient` en dev) avec Fabric Loader 0.19.3,
   Fabric API 0.155.2+26.1.2 et `penumbra-0.23.0.jar`.
2. **Attendu** : le jeu atteint l'écran titre sans crash ; les journaux contiennent une ligne
   `[Penumbra]` d'initialisation, sans stack trace.

## 2. Présence dans la liste des mods

1. Ouvrez l'écran **Mods** depuis le menu principal.
2. **Attendu** : « Penumbra » version `0.22.0` apparaît, avec icône et description.

## 3. Commande de statut

1. Créez un monde (créatif, triche activée), rejoignez-le.
2. Exécutez `/penumbra status`.
3. **Attendu** : message affichant l'état de tension (`CALM` au départ, tension 0), le dernier
   événement (aucun) et le stade narratif (0).

## 4. Forçage des 8 événements

Après `/penumbra tension 80`, forcez chaque événement et vérifiez son effet :

| Commande | Vérification attendue |
|---|---|
| `/penumbra event footsteps` | 4 à 7 bruits de pas espacés, derrière vous, volume modéré |
| `/penumbra event breath` | Souffle bref juste derrière la tête, volume faible |
| `/penumbra event fake_creeper` | Sifflement de creeper latéral ; parfois jumpscare associé |
| `/penumbra event torch_sputter` | Près d'une torche posée : particules de cendre + crachotement ; la torche peut s'éteindre |
| `/penumbra event door_anomaly` | Près d'une porte en bois fermée : elle s'ouvre avec un grincement |
| `/penumbra event distant_sighting` | L'Hôte apparaît au loin (24–40 blocs, zone sombre) + murmure |
| `/penumbra event silence` | Nappe grave oppressante ~12 s |
| `/penumbra event sleep_interrupt` | Allongé dans un lit la nuit : réveil forcé + murmure + jumpscare léger |

**Attendu global** : `/penumbra status` reflète la montée de tension et le dernier événement ;
aucun événement ne casse de bloc de façon permanente.

## 5. Apparition de l'Hôte et vanish-when-seen

1. `/penumbra spawnhost` en zone sombre.
2. **Attendu** : l'Hôte apparaît à distance, hors de votre champ de vision direct, et reste
   immobile à vous observer (comportement `OBSERVING`).
3. Tournez la caméra vers lui et maintenez-le dans le viseur quelques secondes.
4. **Attendu** : soit il disparaît dans des particules avec un murmure (~70 %), soit il se
   téléporte derrière vous et passe en filature (~30 %).

## 6. Persistance de la tension (save / quit / reload)

1. `/penumbra tension 60`, vérifiez avec `/penumbra status` (état `MANIFESTATION`).
2. Sauvegardez et quittez le monde, relancez le jeu, rechargez le monde.
3. `/penumbra status`.
4. **Attendu** : la tension (~60, à la décroissance près) et l'historique sont restaurés —
   le profil survit au rechargement.

## 7. Serveur local à 2 joueurs — sons spatiaux de proximité

1. Lancez `./gradlew runServer` (ou un serveur Fabric local) avec le mod, connectez deux
   clients.
2. Joueur A : `/penumbra tension 80`; placez d'abord B à plus de 64 blocs.
3. Forcez un événement sur le joueur A (`/penumbra event breath` exécuté par A).
4. **Attendu** : B ne reçoit et n'entend rien. Rapprochez ensuite B à 5 blocs et
   recommencez : A et B entendent le souffle, avec une direction et un volume cohérents.
   Un joueur dans une autre dimension n'entend jamais le son. La tension et la cible de
   l'événement restent propres à A (`/penumbra status` diffère entre A et B).

## 8. Restauration des torches

1. Posez une torche, `/penumbra event torch_sputter` jusqu'à déclencher l'extinction
   (probabilité 40 % quand `worldModificationEnabled` est actif).
2. Attendez ~60 secondes (1200 ticks).
3. **Attendu** : la torche réapparaît à sa position d'origine, dans son état d'origine ;
   si le bloc a été remplacé entre-temps par autre chose, la restauration n'écrase rien.

## Critère de sortie Phase 1

Les 8 sections passent sans crash, sans corruption de monde et sans modification permanente
non voulue.

---

# Phase 2 (0.2.0) — Jumelles, poursuite, leurres, scares retardés

Prérequis : la Phase 1 (sections 1 à 8) passe. Sauf mention contraire, la configuration
est celle par défaut. Pour les tests liés à la tension : `/penumbra tension 80`.

## 9. Caméra VHS — activation et relâchement de C

1. En solo, première personne, **maintenez C**.
2. **Attendu** : le FOV se réduit progressivement jusqu'au facteur configuré (défaut ×4),
   le viseur VHS 4:3 apparaît, un clic doux (`binoculars_on`) se fait entendre,
   une légère dérive de visée (respiration) est perceptible.
3. **Relâchez C**.
4. **Attendu** : retour progressif au FOV exact d'origine, disparition de l'overlay,
   clic de sortie (`binoculars_off`). Aucune valeur dans `options.txt` n'est modifiée
   (vérifiez que votre sensibilité/FOV d'origine sont intacts après le test).

## 10. Caméra VHS — modification de la touche

1. *Options → Contrôles → catégorie « Penumbra »* : réassignez « Caméra VHS » à **V**.
2. **Attendu** : le zoom s'active avec V, plus avec C. Si une autre action (mod ou vanilla)
   utilise la même touche, le conflit s'affiche en rouge dans le menu vanilla et les deux
   actions suivent le comportement standard de Minecraft.
3. Réassignez à C pour la suite.

## 11. Jumelles — modes maintenir / basculer

1. `binocularsToggleMode: false` (défaut) : le zoom ne dure que tant que C est maintenu.
2. Passez `binocularsToggleMode: true`, redémarrez (ou rechargez la config).
3. **Attendu** : une pression sur C active le zoom ; une seconde pression le désactive.
   Le FOV est restauré dans les deux modes.

## 12. Jumelles — restauration du FOV sur événements

Activez le zoom puis, tour à tour :

| Événement pendant le zoom | Attendu |
|---|---|
| Ouvrir un écran (inventaire, pause, chat) | Zoom interrompu, FOV restauré à la valeur exacte |
| Mourir | Au respawn, FOV normal, aucun zoom résiduel |
| Changer de dimension (portail Nether) | FOV restauré pendant et après la traversée |
| Se déconnecter / recharger le monde | FOV normal à la reconnexion |
| Passer en 3e personne (F5) | Aucun effet de zoom visuel ; retour en 1re personne : comportement cohérent (rezoom si touche maintenue, sinon rien) |

**Attendu global** : dans tous les cas, le FOV d'origine est restauré au tick près et
`options.txt` n'est jamais réécrit par le mod.

## 13. Jumelles — inventaire pendant le zoom

1. Zoomez, ouvrez l'inventaire, refermez-le.
2. **Attendu** : l'ouverture interrompt le zoom proprement ; le serveur est informé de
   l'état d'inventaire (observable via `debugLogging` et le déclencheur
   `inventory_scare`). Aucun blocage de touche après fermeture.

## 14. Jumelles — avec et sans objet

1. `binocularsRequireItem: false` (défaut) : le zoom fonctionne les mains vides.
2. Passez `binocularsRequireItem: true`.
3. **Attendu** : sans l'objet Jumelles dans l'inventaire, la touche C ne fait rien ;
   craftez les jumelles (verre/fer/cuir), le zoom fonctionne.
4. `binocularsDurabilityEnabled: true` : chaque activation retire 1 point de durabilité
   (256 au total) ; l'objet se brise au terme de sa vie.

## 15. Jumelles — 1re / 3e personne

1. En 1re personne : zoom normal (section 9).
2. En 3e personne : **aucun** zoom ni overlay ne s'applique.
3. Maintenez C et repassez en 1re personne : le zoom s'applique alors (mode maintenir).

## 16. Caméra VHS — résolutions et formats d'écran

Testez l'overlay en plein écran et fenêtré, en 16:9, 21:9 et 4:3, avec plusieurs
échelles d'interface (GUI scale 1 à 4) :

- **Attendu** : le viseur arrondi reste centré en 4:3, la vignette couvre les
  marges et REC/timecode/batterie/zoom restent lisibles sans texture manquante.

## 17. Caméra VHS — multijoueur

1. Serveur local à 2 joueurs (voir section 7). Joueur A zoome, joueur B observe A.
2. **Attendu** : seul A voit l'overlay et le zoom ; B ne subit aucun effet visuel,
   mais entend le petit clic d'allumage s'il se trouve à moins de 16 blocs.
   L'état de zoom d'A n'influence pas les événements de B.

## 18. Jumelles — performances

1. Zoomez en continu pendant 5 minutes dans une zone chargée (village + forêt).
2. **Attendu** : aucune chute de FPS notable, aucune allocation visible au profileur,
   aucun spam de paquets (un paquet C2S par changement d'état seulement).

## 19. Jumelles — réactions de l'Hôte

1. `binocularsHostReactionsEnabled: true`, tension ≥ WATCHED (`/penumbra tension 60`).
2. Forcez une apparition : `/penumbra binoculars host_sighting` **pendant le zoom**.
3. **Attendu** : l'Hôte apparaît à 60–90 blocs dans votre cône de regard, comportement
   `OBSERVING`. Sans zoom, la commande échoue avec un message explicite.
4. Testez chaque réaction : `/penumbra binoculars reaction freeze`, `head_turn`,
   `vanish_on_unzoom`, `approach_between`, `sound_behind`, `mimic_posture`,
   `reappear_checked`.
5. **Attendu** : la réaction forcée s'exécute sur l'Hôte le plus proche (un Hôte est
   invoqué si nécessaire) ; un type invalide renvoie l'erreur `reaction_unknown`.
   Jamais d'attaque immédiate ni de téléportation au contact.
6. `/penumbra binoculars reset` : l'état jumelles du joueur est réinitialisé côté serveur.

## 20. Poursuite complète

1. `/penumbra tension 80` puis `/penumbra chase`.
2. **Attendu** : stinger `chase_stinger`, l'Hôte s'arrête ~0,75 s face à vous
   (mise en scène), puis poursuit avec accélération progressive (1,15 → 1,45).
3. L'attaque de mêlée n'arrive qu'après un **télégraphe visible de 20 ticks**
   (pas rapprochés + ralentissement) — jamais d'attaque instantanée.
4. Fuyez au-delà de 48 blocs pendant 5 s, ou survivez 60 s : la poursuite s'arrête
   (`endChase`). Parfois (~40 %), l'Hôte fait semblant de battre en retraite et se
   cache à moins de 24 blocs (`HIDING`).
5. **Attendu post-poursuite** : pendant `postChaseCooldownTicks` (défaut 6 min), aucune
   nouvelle poursuite, aucun scare, aucun `distant_sighting` automatique.

## 21. Leurres

`/penumbra tension 80`, puis forcez chaque événement :

| Commande | Vérification attendue |
|---|---|
| `/penumbra event lure_door` | Grincement lointain latéral (4–10 blocs), **aucun bloc modifié** |
| `/penumbra event fake_silhouette` | Silhouette `HIDING` à 28–40 blocs ; la fixer 4 ticks la fait disparaître (cendre) |
| `/penumbra event distant_light` | Lueur posée à 24–40 blocs + nappe grave ; la source lumineuse est **retirée après 60 s** — rien ne subsiste |
| `/penumbra event anticlimax` | Heartbeat 6 s + nappe de silence, **aucun autre effet** |
| `/penumbra event binoculars_sighting` | Nécessite un zoom actif (voir section 19) |

## 22. Jumpscares retardés

1. `delayedJumpscaresEnabled: true`, tension ≥ UNEASE (`/penumbra tension 40`).
2. Réalisez les actions déclencheuses : ouvrir un coffre, regagner votre refuge après une
   longue absence, laisser une torche s'éteindre (`torch_sputter`), regarder derrière vous
   5 fois rapidement, ouvrir l'inventaire, rester 5 min sans événement.
3. **Attendu** : les scares surviennent **5 à 20 s après** l'action (jamais sur le coup),
   individualisés par joueur, avec au moins 40 s entre deux scares du même type et 800
   ticks minimum entre deux scares quelconques.
4. Test forcé : `/penumbra scare chest_scare` (et chacun des 5 autres ids).
   **Attendu** : déclenchement immédiat du jumpscare retardé correspondant ; un id
   inconnu est refusé avec la liste des ids valides.

## 23. Mémoire comportementale

1. Restez immobile de nuit (> 30 s) au même endroit à plusieurs reprises (≥ 3 visites),
   dormez dans un lit, regardez souvent derrière vous.
2. **Attendu** : après apprentissage, les apparitions de l'Hôte (observation, réaction
   `reappear_checked`) privilégient les abords de votre refuge et de vos zones fréquentées.
3. Sauvegardez/rechargez : la mémoire persiste (et les mondes créés en 0.1.0 se chargent
   sans erreur — codec v2 à champs optionnels).

## 24. Commandes de débogage Phase 2

| Commande | Attendu |
|---|---|
| `/penumbra chase` | Poursuite démarrée (Hôte existant < 60 blocs réutilisé, sinon invoqué à 16–24 blocs) ; message `chase_started` |
| `/penumbra scare <id>` | Scare retardé forcé ; message `scare_triggered` ; id inconnu refusé |
| `/penumbra binoculars test` | Zoom forcé de 3 s (solo / hôte LAN uniquement ; échec explicite sur serveur dédié) |
| `/penumbra binoculars host_sighting` | Apparition jumelles forcée (zoom actif requis) ; échec explicite sinon |
| `/penumbra binoculars reaction <type>` | Réaction forcée sur l'Hôte le plus proche ; type inconnu → erreur `reaction_unknown` |
| `/penumbra binoculars reset` | État jumelles serveur réinitialisé ; message `reset_done` |

Les suggestions Brigadier doivent proposer tous les ids (`scare`, `reaction`, événements).

## Critère de sortie Phase 2

Les sections 1 à 24 passent sans crash, sans FOV bloqué, sans modification permanente du
monde, avec restauration vérifiée de toute torche/lumière temporaire, et aucun effet
transféré d'un joueur à l'autre en multijoueur.

---

# Jumelles 0.3.0 — nouvel overlay (refonte renderer + manager)

Prérequis : les critères de sortie des Phases 1 et 2 passent. Sauf mention contraire,
configuration par défaut. L'overlay 0.3.0 est procédural (aucune texture) : deux
lentilles elliptiques séparées d'un espace noir central, masque noir opaque à 100 %
hors des lentilles, liseré discret de 2 px, vignette interne très subtile.

## 25. Jumelles 0.3.0 — zoom ×4 sur cible lointaine

1. En solo, placez-vous face à un arbre à 80 blocs ou plus, en 1re personne.
2. Maintenez **C**.
3. **Attendu** : l'arbre est clairement grossi (×4 par défaut), net à l'intérieur des
   deux lentilles ; la visée centrale est parfaitement dégagée (aucun ornement au
   centre : pas de croix, pas de cadre). Les deux lentilles sont bien elliptiques,
   centrées, et **ne se croisent jamais** — l'espace noir central reste visible quelle
   que soit la résolution.

## 26. Jumelles 0.3.0 — entrée / sortie fluides (~300 ms)

1. Activez puis relâchez le zoom une dizaine de fois, à rythmes variés.
2. **Attendu** : le masque noir fond progressivement (opacité proportionnelle à la
   progression) et les lentilles grandissent en douceur (échelle 0,88 → 1,0) en
   ~300 ms ; la sortie est symétrique. Aucun flash, aucune coupure, aucun residu
   d'overlay à l'écran une fois la progression revenue à 0.

## 27. Jumelles 0.3.0 — résolutions, ratios et GUI scale

Testez l'overlay dans chaque configuration, zoom actif :

| Configuration | Attendu |
|---|---|
| 1920×1080 plein écran | Lentilles ≈ 76 % de la hauteur, ≈ 70 % de la largeur au total, centrées |
| Fenêtré (ex. 1280×720) | Géométrie identique en proportions, aucun débordement |
| 21:9 (ultra-large) | Lentilles centrées, bandes noires latérales opaques, gap central visible |
| 4:3 | Lentilles toujours séparées, jamais croisées, masque opaque partout ailleurs |
| GUI scale 1, 2, 3, 4 | Mêmes proportions relatives ; liseré de 2 px GUI visible ; aucun artefact |

**Attendu global** : dans toutes les configurations, l'écran hors lentilles est noir
opaque (pas de voile gris translucide type 84 %), le HUD vanilla n'est pas recouvert
d'un assombrissement parasite, et les lentilles ne se chevauchent jamais.

## 28. Jumelles 0.3.0 — maintien / bascule et scroll

1. `binocularsToggleMode: false` : le zoom dure tant que C est maintenu ; le relâcher
   déclenche la sortie fluide (section 26).
2. `binocularsToggleMode: true` : une pression active, une seconde désactive ;
   l'overlay suit sans désynchronisation.
3. Pendant la visée, utilisez la **molette (scroll)**.
4. **Attendu** : le niveau de zoom s'ajuste progressivement dans la plage configurée
   (2–6) ; l'overlay reste stable (géométrie inchangée, pas de saut de lentilles).

## 29. Jumelles 0.3.0 — inventaire pendant le zoom

1. Zoomez, ouvrez l'inventaire, refermez-le.
2. **Attendu** : l'ouverture interrompt proprement zoom et overlay ; le serveur est
   informé de l'état d'inventaire ; à la fermeture, comportement cohérent avec le mode
   actif (reprise si touche maintenue, sinon rien) ; FOV restauré à la valeur exacte.

## 30. Jumelles 0.3.0 — mort, respawn et changement de dimension

| Événement pendant le zoom | Attendu |
|---|---|
| Mourir | Overlay et zoom disparaissent immédiatement ; au respawn, FOV normal, aucun résidu |
| Traverser un portail (Nether/End) | FOV restauré pendant et après la traversée ; aucun zoom résiduel dans la nouvelle dimension |

## 31. Jumelles 0.3.0 — 1re / 3e personne

1. En 1re personne : zoom et overlay normaux (sections 25–26).
2. Passez en 3e personne (F5) avec C maintenu.
3. **Attendu** : aucun zoom ni overlay en 3e personne ; au retour en 1re personne,
   comportement cohérent avec le mode actif. Le FOV d'origine est toujours restauré.

## 32. Jumelles 0.3.0 — restauration exacte du FOV

1. Notez votre FOV dans *Options → Vidéo* (ex. 75, ou une valeur personnalisée).
2. Zoomez, relâchez, répétez avec les sections 29–31.
3. **Attendu** : le FOV revient à la valeur EXACTE d'origine dans tous les cas ;
   `options.txt` n'est jamais réécrit par le mod (restauration en mémoire uniquement).

## 33. Jumelles 0.3.0 — hideHud / hideHand

1. Activez le zoom puis appuyez sur **F1** (masquer le HUD).
2. **Attendu** : l'overlay des jumelles disparaît avec le reste du HUD ; il réapparaît
   au ré-affichage du HUD si le zoom est toujours actif.
3. Pendant la visée, la main et l'objet tenu restent masqués (hideHand) : aucun
   modèle de main ne chevauche les lentilles.

## 34. Jumelles 0.3.0 — salété des lentilles (lensDirt)

1. `binocularsLensDirt: true`, zoomez.
2. **Attendu** : 5 à 7 micro-taches par lentille (2–4 px, très discrètes, noir ou brun
   sombre), toutes à l'intérieur des lentilles, aucune près du centre de visée ;
   positions parfaitement stables d'une image à l'autre (aucun scintillement) et
   identiques d'une activation à l'autre (déterminisme).
3. `binocularsLensDirt: false` : lentilles parfaitement propres.

## 35. Jumelles 0.3.0 — journal et stabilité

1. Enchaînez les sections 25 à 34 puis laissez le zoom actif 5 minutes.
2. **Attendu** : `logs/latest.log` ne contient aucune erreur ni avertissement Penumbra
   (aucune stack trace, aucun spam) ; aucune fuite de FPS notable pendant le maintien.

## Critère de sortie 0.3.0

Les sections 25 à 35 passent sans crash : géométrie des lentilles conforme et jamais
croisée sur toutes les résolutions testées, animations d'entrée/sortie fluides, FOV
restauré exactement dans tous les contextes, taches déterministes, et journal propre.

---

# Phase 3 (0.3.0) — Histoire, structures, objets narratifs, événements nocturnes et souterrains, copies

Prérequis : les critères de sortie des Phases 1 et 2 passent. Configuration par
défaut (`storyEnabled`, `structuresEnabled`, `nightEventsEnabled`,
`undergroundEventsEnabled`, `imperfectCopiesEnabled` actifs). Toutes les commandes
ci-dessous requièrent le niveau d'opérateur 2.

## 36. Structures — locate et spawn des 4 types

1. `/penumbra structure spawn observation_post` en plaine dégagée.
2. **Attendu** : la structure est construite ~8 blocs devant vous sur la surface ;
   message `penumbra.structure.spawned` avec les coordonnées.
3. Répétez pour `shifted_house`, `penumbra_well`, `ash_sanctum` (spawn ×4).
4. **Attendu** : chaque type apparaît avec ses matériaux caractéristiques (cendre
   en `black_wool`/`coal_block`/`soul_soil pour le sanctuaire) ; aucune construction
   voisine n'est endommagée ; des pancartes inachevées sont présentes.
5. Éloignez-vous de quelques centaines de blocs puis
   `/penumbra structure locate <type>` pour chacun des 4 types (locate ×4).
6. **Attendu** : coordonnées et distance de la structure la plus proche, cohérentes
   avec les spawns précédents ; un type inconnu renvoie `penumbra.structure.unknown`.
7. Approchez à moins de 24 blocs d'une structure non encore découverte.
8. **Attendu** : message discret « Vous n'êtes pas le premier à vous tenir ici. »
   (une seule fois par structure) et découverte comptabilisée
   (`/penumbra story get` montre `structure_<type>: 1`).
9. `/penumbra binoculars landmark` à proximité d'une structure connue.
10. **Attendu** : phrase énigmatique indiquant direction cardinale et distance
    arrondie ; sans structure connue, la phrase « rien » est renvoyée.

## 37. Progression narrative — story get / set

1. `/penumbra story get` en début de partie.
2. **Attendu** : stade 0 (`normalcy`), aucun compteur de découverte.
3. `/penumbra story set 3` puis `/penumbra story get`.
4. **Attendu** : stade 3 (`familiarity`) persisté et affiché ; à la montée, une
   ligne narrative discrète (italique gris sombre) apparaît dans le chat.
5. `/penumbra story set 0` : la descente de stade reste muette (pas de message
   narratif), le get confirme 0.
6. Progression naturelle : `/penumbra tension 80` puis forcez
   `/penumbra event footsteps` (×1), `fake_silhouette`, `lure_door`.
7. **Attendu** : `/penumbra story get` montre `first_anomaly: 1` (stade ≥ 1) puis
   `mimic_event: 3` → passage automatique au stade 2 (`imitation`) avec message.

## 38. Objets narratifs — item give ×6

1. Tour à tour : `/penumbra item give cracked_lens`, `incomplete_journal`,
   `observation_fragment`, `blackened_plumb_bob`, `impossible_photo`,
   `silhouette_glass` (puis `ash_sigil` et `binoculars`).
2. **Attendu** : chaque objet est remis à l'inventaire (ou déposé aux pieds si
   plein) avec message `penumbra.item.given` ; noms et descriptions traduits dans
   l'infobulle ; les 6 objets sont aussi visibles dans l'onglet Ingrédients
   (mode créatif).
3. `/penumbra item give foo` : refus explicite avec la liste des ids valides.
4. Empilez plusieurs `silhouette_glass` : ~15 % affichent la description
   alternative « silhouette » (composant persistant, visible en infobulle).

## 39. Événements nocturnes — event night ×8

1. La nuit (ou `/time set night`), `/penumbra tension 80`, forcez tour à tour :
   `/penumbra event night window_silhouette`, `roof_steps`, `torch_chain`,
   `animal_stare`, `dawn_door`, `hill_host`, `false_refuge_light`,
   `shadow_crossing`.
2. **Attendu** : chaque événement produit sa mise en scène près du joueur
   (conditions de `canRun` réunies : nuit, extérieur proche pour certains) ;
   message `event_triggered` ; un id hors du pool nocturne (ex.
   `/penumbra event night footsteps`) est refusé (`event_unknown`).
   Note : `hill_host` conserve son IA (pas de statue figée — limite connue) ;
   `dawn_door` ne restaure pas la porte (limite connue, précédent DoorAnomaly).

## 40. Événements souterrains — event underground ×8 et restauration

1. Descendez sous terre (galerie en Y < 60), `/penumbra tension 80`, forcez :
   `/penumbra event underground wall_knocks`, `false_vein`, `foreign_torch`,
   `deadend_breath`, `tunnel_silhouette`, `pickaxe_echo`, `shifted_gallery`,
   `impossible_room`.
2. **Attendu** : chaque événement s'exécute (joueur sous terre et éveillé requis ;
   sinon refus silencieux via `canRun` → `event_unknown` après forçage) ; les
   blocs posés (filon, torche étrangère, galerie décalée, salle) sont bien
   matérialisés. En surface, les ids souterrains ne font rien.
3. `/penumbra debug restoration` dans le même niveau.
4. **Attendu** : message de confirmation ; tous les blocs encore intacts posés
   par les événements sont restaurés dans leur état d'origine ; un bloc miné ou
   remplacé par vous entre-temps n'est PAS recréé (jamais d'écrasement).
5. Sans commande : les modifications à échéance (filon illusoire, galerie) se
   restaurent d'elles-mêmes à terme ; la torche étrangère est permanente jusqu'à
   `debug restoration`.

## 41. Copies imparfaites — build, restore et garde-fous

1. Construisez un petit refuge (quelques meubles, lit, coffres) et dormez-y une
   nuit pour l'enregistrer.
2. `/penumbra story set 3` (stade minimal requis) puis `/penumbra copy build`.
3. **Attendu** : une copie imparfaite du refuge apparaît à distance raisonnable —
   similaire mais avec des détails faux et des pancartes inachevées ; message
   `penumbra.copy.built` ; `/penumbra story get` montre `copy_built: 1`
   (stade 4 à la prochaine évaluation).
4. `/penumbra copy restore` : la copie est démontée (`penumbra.copy.restored`
   avec le nombre), le terrain d'origine rétabli.
5. Sans copie construite : `copy restore` renvoie `penumbra.copy.none` ; avec un
   second joueur à moins de 30 blocs, `copy build` est refusé (isolement requis).

## 42. Persistance du stade narratif (save / quit / reload)

1. `/penumbra story set 4`, notez les compteurs via `/penumbra story get`.
2. Sauvegardez et quittez le monde, relancez le jeu, rechargez.
3. `/penumbra story get`.
4. **Attendu** : stade 4 et compteurs restaurés à l'identique ; un monde créé
   avant la 0.3.0 se charge sans erreur (stade 0, compteurs vides — codec à
   champs optionnels, aucune migration).

## 43. Découvertes narratives passives

1. Dormez deux nuits complètes dans un lit (réveil au matin, pas d'interruption).
2. **Attendu** : `bed_used: 2` au `/penumbra story get`.
3. Provoquez des observations de l'Hôte (`/penumbra event distant_sighting`,
   `fake_silhouette`, `binoculars_sighting` au zoom — ×4 au total).
4. **Attendu** : `host_observed: 4` ; au stade 4 préalable, passage au stade 5.
5. Appliquez un `ash_sigil` (clic droit) sur le pilier central d'un sanctuaire
   de cendre (`/penumbra structure spawn ash_sanctum`).
6. **Attendu** : le sceau est consommé, son grave joué, message « La cendre
   accepte. », `sanctum_ritual: 1` → stade 6 si le stade 5 était atteint.

## Critère de sortie Phase 3

Les sections 36 à 43 passent sans crash : les 4 structures se construisent et se
localisent, le stade narratif progresse et persiste au rechargement, les 6 objets
se donnent avec traductions, les 16 événements Phase 3 se forcent dans leurs
conditions, la restauration souterraine et des copies n'écrase aucune modification
du joueur, et aucune construction existante n'est détruite par la génération.

---

# Phase 4 (0.4.0) — Observateur, badges, protections multijoueur

Prérequis : les Phases 1 à 3 passent. Configuration par défaut. Pour les tests
liés à la tension : `/penumbra tension 80`.

## 44. Observateur — apparition et regard soutenu (badge you_see_me)

1. En extérieur dégagé, `/penumbra tension 80` puis `/penumbra watcher test`.
2. **Attendu** : un Observateur (silhouette humanoïde de ~2,4 blocs, texture
   dédiée) apparaît à 15–30 blocs dans votre cône de regard, orienté vers vous ;
   message `watcher_spawned`. En terrain sans emplacement valide, la commande
   répond `watcher_spawn_failed` sans effet.
3. Maintenez l'Observateur au centre de votre écran pendant
   `watcherStareSeconds` secondes (défaut 3).
4. **Attendu** : la réaction de `watcher_stare` se déclenche et l'avancement
   **« Vous me voyez »** (`you_see_me`, frame défi) s'affiche en toast avec le
   son vanilla d'avancement ; il apparaît dans l'onglet Penumbra des avancements.
5. Alternative organique : `/penumbra event watcher_stare` (l'id est dans le
   pool général) — mêmes attendus.

## 45. Observateur — groupe (watcher_group)

1. `/penumbra tension 80` puis `/penumbra event watcher_group`.
2. **Attendu** : un arc de plusieurs Observateurs se matérialise face à vous
   (cône centré sur votre regard) ; événement très rare en sélection naturelle
   et soumis à `watcherGroupsEnabled`.
3. **Attendu (plafond)** : si Hôtes + Observateurs proches atteignent déjà
   `maxHostEntitiesPerPlayer`, l'événement refuse d'apparaître (refus silencieux
   de `canRun`). Un groupe validé vérifie encore le quota avant chaque membre et
   ne doit jamais le dépasser.

## 46. Badges — octroi des 5 badges intégrateur

1. **first_anomaly** : sur un joueur frais (`/penumbra reset`), forcez un
   événement (`/penumbra event breath`). **Attendu** : avancement
   **« Première fissure »** toasté ; il ne se re-toaste jamais une seconde fois.
2. **first_structure** : `/penumbra structure spawn observation_post` puis
   approchez à moins de 24 blocs. **Attendu** : message de découverte +
   avancement **« Traces de pas »**.
3. **survivor** : `/penumbra chase`, survivez jusqu'à l'abandon de la poursuite
   (éloignez-vous au-delà de 48 blocs ou attendez 60 s). **Attendu** : à la fin
   de poursuite, joueur vivant, avancement **« Encore là »**.
4. **sanctum_ritual** : `/penumbra structure spawn ash_sanctum`,
   `/penumbra item give ash_sigil`, clic droit du sceau sur le pilier central.
   **Attendu** : rituel consommé + avancement **« Cendre et serment »**.
5. **confrontation** : sur un joueur sans ce badge, `/penumbra story set 6`.
   **Attendu depuis 0.22.0** : aucun octroi au simple passage au stade final.
   Gagner ensuite le duel (section 117) : **« Il connaît votre nom »** est
   accordé une seule fois si les badges sont activés. Les anciennes
   attributions restent conservées ; ne pas les utiliser comme preuve de ce test.
6. Passez `badgesEnabled: false`, recommencez le test 1 sur un autre joueur.
   **Attendu** : aucun toast, aucune erreur dans les journaux (sortie silencieuse).

## 47. Multijoueur — throttle des sons S2C

1. Serveur local à 2 joueurs (cf. section 7). Joueur A : `/penumbra tension 80`.
2. Forcez en rafale plusieurs événements sonores sur A
   (`/penumbra event breath`, `footsteps`, `silence`… à quelques ticks
   d'intervalle).
3. **Attendu** : A entend les sons sans saturation ; au plus 1 paquet sonore
   non prioritaire par 3 ticks et par joueur est émis (les excès sont droppés
   silencieusement — trace visibles avec `debugLogging: true`). Le joueur B,
   à distance, n'entend rien.
4. `/penumbra chase` : **attendu** : le stinger de poursuite passe
   systématiquement (voie prioritaire `sendSoundPriority`, jamais étranglée),
   même immédiatement après la rafale.

## 48. Multijoueur — plafond d'entités par joueur

1. `maxHostEntitiesPerPlayer: 2` (défaut). `/penumbra tension 80`.
2. `/penumbra spawnhost` ×2 à proximité, puis forcez des événements invoquant
   un Hôte (`/penumbra event distant_sighting`, `hill_host`…) et des
   apparitions d'Observateur.
3. **Attendu** : tant que 2 entités d'horreur vivent dans le rayon, les
   événements à apparition refusent de se déclencher (décision du directeur,
   refus silencieux de `canRun` — les forçages admin ne créent pas d'entité
   supplémentaire via ces événements) ; dès qu'une entité disparaît, les
   apparitions redeviennent possibles. Les commandes d'invocation directes
   (`spawnhost`, `watcher test`, `chase`) restent des forçages admin assumés.

## Critère de sortie Phase 4

Les sections 44 à 48 passent sans crash : l'Observateur apparaît dans le cône
de regard et réagit au regard soutenu, le groupe se matérialise, les 5 badges
s'octroient exactement une fois chacun (et jamais quand `badgesEnabled` est
faux), les sons restent spatiaux et bornés sous rafale avec la voie prioritaire
préservée, et le plafond d'entités bloque les apparitions automatiques en
multijoueur.

---

# Préflight de release 0.8.0

## 49. Build, métadonnées et ressources

1. Exécutez `gradlew.bat clean check build --offline` (ou `./gradlew` sous
   Unix) après avoir rempli une fois le cache Gradle.
2. **Attendu** : tests JUnit et `validatePenumbraResources` passent ; aucun JSON,
   son, sous-titre ou lien modèle/texture n'est signalé.
3. Ouvrez `build/libs/penumbra-0.8.0.jar` comme une archive et contrôlez
   `fabric.mod.json`.
4. **Attendu** : version interne `0.8.0`, Minecraft `~26.1.2`, Loader
   `>=0.19.3`, Fabric API `>=0.155.2+26.1.2`, Java `>=25`, licence présente.
5. Contrôlez dans le JAR les textures
   `textures/entity/projectionist.png`, `projectionist_photo.png`,
   `rampant.png`, `absent.png`, `scuttler.png`, `textures/item/ash_ward.png`
   ainsi que `textures/gui/screamer_host_7.png` à `screamer_host_9.png`.
6. **Attendu** : tous les PNG sont présents et lisibles ; les atlas d'entité
   et l'icône font 64×64, l'overlay photo 128×72 et les trois screamers 512×512.
   Aucun modèle ni renderer ne signale de ressource manquante au lancement.

## 50. Migration d'une ancienne configuration

1. Copiez un `penumbra.json` 0.1–0.4 ne contenant que les 15 clés historiques ;
   mettez `storyMode: false` et omettez `storyEnabled`.
2. Lancez puis arrêtez proprement le jeu.
3. **Attendu** : `configVersion: 2` et les 57 réglages sont présents,
   `storyEnabled` vaut `false`, et `penumbra.json.pre-v1.bak` contient
   l'original.
4. Ajoutez une clé inconnue, relancez puis provoquez une réécriture de
   migration sur une copie du fichier.
5. **Attendu** : la clé inconnue est conservée. Un JSON cassé est déplacé vers
   `penumbra.json.broken[.<horodatage>]` et les défauts sont recréés.

---

# Phase 5 (0.5.0) — ambiance, Originel et finale

## 51. Couche ambiante et options de confort

1. Avec un casque à volume modéré, fixez successivement la tension à 20, 50,
   80 puis 100 et restez immobile au moins 10 secondes à chaque palier.
2. **Attendu** : les nappes passent progressivement de discrètes à tendues,
   sans clic de boucle ; en faux calme, elles s'interrompent.
3. Désactivez `fogEnabled` puis `distortionEnabled` et redémarrez le client.
4. **Attendu** : le voile périphérique puis la dérive de FOV disparaissent
   indépendamment, sans modifier le FOV sauvegardé par Minecraft.

## 52. Originel et ressources client

1. En monde de test, utilisez `/summon penumbra:originel ~ ~ ~10` pour valider
   l'enregistrement, le modèle et la texture.
2. **Attendu** : silhouette aux proportions de joueur, sans visage ni texture
   manquante ; aucune erreur renderer dans `latest.log`.
3. Supprimez l'entité de test, activez `originelEnabled` et jouez en extérieur
   sur une session longue. Contrôlez aussi `originelTracesEnabled: false` sur une
   nouvelle session.
4. **Attendu** : les apparitions naturelles restent rares et l'option de traces
   empêche les nouvelles constructions de l'Originel.

## 53. Finale du sanctuaire

1. Atteignez le stade 5, construisez un sanctuaire et appliquez le Sceau de
   cendre comme en section 43.
2. **Attendu** : silence, murmures convergents, confrontation, son
   `ritual_release`, épilogue et calme temporaire ; aucun segment sonore ne
   reste bloqué après la séquence.

---

# Phase 6 (0.6.0) — peur personnelle, screamers et Contrefait

## 54. `mining_bait` et coupure des screamers

1. Sous terre, tension élevée, forcez `/penumbra event mining_bait`.
2. **Attendu** : la voix-leurre précède l'une des deux variantes de visage et
   le son `screamer_shriek`; l'image couvre correctement les formats 16:9,
   16:10 et 21:9 sans texture rose/noire.
3. Passez `screamersEnabled: false`, redémarrez et recommencez.
4. **Attendu** : aucun visage ni hurlement de screamer n'est déclenché.

## 55. Événements personnels forçables

Dans leurs conditions respectives, forcez :

- `/penumbra event echoed_path` après au moins une minute de déplacement ;
- `/penumbra event absent_home` à 48–110 blocs du refuge ;
- `/penumbra event door_knocks` la nuit près d'une vraie porte ;
- `/penumbra event binoculars_reveal`, puis zoomez et dézoomez ;
- `/penumbra event behind_other` avec un second joueur à 8–24 blocs.

**Attendu** : chacun exploite la position ou le contexte réel du joueur ; le son
est audible par les joueurs proches seulement et aucune erreur ne survient si les préconditions
ne sont pas réunies. `personalEventsEnabled: false` les bloque tous.

## 56. Journal dynamique et moments uniques

1. Accumulez quelques nuits, regards arrière, minutes souterraines et
   événements, puis utilisez le Journal incomplet.
2. **Attendu** : les quatre statistiques affichées correspondent au profil du
   joueur et les textes sont traduits en FR puis EN selon la langue du client.
3. Sur une copie de monde, observez les moments `chest_reorder`, `bed_shift` et
   `fake_join` pendant une session longue.
4. **Attendu** : chaque moment unique ne se répète pas abusivement ; avec
   `metaMessagesEnabled: false`, aucun faux message de connexion n'apparaît.
   `bed_shift` joue son mouvement sous le lit sans déplacer le bloc ni modifier
   le point de réapparition.

## 57. Contrefait et anti-habituation

1. Utilisez `/summon penumbra:contrefait ~ ~ ~8` pour valider modèle, texture,
   yeux asymétriques et animations, puis supprimez l'entité de test.
2. Jouez une session naturelle avec `contrefaitEnabled: true`, puis une avec
   l'option fausse.
3. **Attendu** : aucun spawn géré par le manager quand l'option est fausse.
4. Forcez plusieurs fois les mêmes événements puis laissez le directeur choisir
   naturellement.
5. **Attendu** : les événements sur-exposés perdent du poids et la variété
   remonte sans bloquer totalement le directeur.

## 58. Smoke test final client et serveur

1. Démarrez `runClient`, chargez un monde, testez jumelles, un son, un badge,
   une structure, un screamer et les trois nouvelles entités.
2. Démarrez séparément `runServer`, connectez deux clients puis arrêtez-le
   proprement.
3. **Attendu** : les deux journaux chargent `penumbra 0.8.1`, aucune ressource
   Penumbra n'échoue au parsing, aucun mixin client n'est appliqué sur le serveur
   et aucune stack trace Penumbra n'apparaît.

## 59. Caméra VHS — modèle, viseur et accessibilité

1. Donnez-vous `/penumbra item give binoculars`, observez l'icône dans
   l'inventaire puis l'objet en première et troisième personne.
2. Maintenez C et testez les GUI scales 1–4, les ratios 4:3, 16:9 et 21:9.
3. **Attendu** : icône de caméra nette, modèle cubique tenu en main, viseur 4:3
   sans débordement, REC/timecode/batterie/zoom lisibles et scanlines discrètes.
4. Avec `flashesEnabled: false`, le REC et le grain restent fixes et aucun
   dropout animé n'apparaît.

## 60. Marteau de mineur 4×4

1. Craftez le marteau avec 5 lingots de fer et 2 bâtons, ou utilisez
   `/penumbra item give hammer`.
2. Cassez de la pierre face à un mur, au sol et au plafond.
3. **Attendu** : exactement un plan 4×4 compatible est miné, orienté selon le
   regard. Bedrock, coffres, blocs non-pioche, chunks déchargés et zones
   protégées restent intacts ; Fortune, Silk Touch, drops et XP restent vanilla.
4. Vérifiez la durabilité : chaque bloc réellement cassé consomme une utilisation
   (avant réduction éventuelle par Unbreaking) et la zone s'arrête si l'outil casse.

## 61. Audio spatial complet

1. Placez deux joueurs à 5 blocs, forcez un souffle, une poursuite et un screamer.
2. **Attendu** : les deux entendent chaque son à sa position ; seul le joueur
   ciblé reçoit l'overlay/jumpscare et l'attaque.
3. Recommencez à plus de 64 blocs puis dans une autre dimension.
4. **Attendu** : le second joueur ne reçoit aucun de ces sons.

## 62. Screamers et apparition de grotte 0.6.2

1. Exécutez six fois `/penumbra jumpscare screamer`, en attendant le bref
   cooldown critique entre les commandes.
2. **Attendu** : les six images apparaissent une fois chacune avant le cycle
   suivant, sans répétition immédiate et sans déformation en 4:3, 16:9 ou 21:9.
3. Forcez `/penumbra event underground cave_face` dans une galerie basse.
4. **Attendu** : une intrusion visuelle courte et semi-opaque apparaît. Avec
   `screamersEnabled: false`, aucun visage plein écran n'est rendu.
5. Tenez une pioche puis le marteau et forcez `mining_bait`; regardez derrière
   puis revenez vers le front de taille.
6. **Attendu** : les deux outils sont acceptés ; seul un Hôte réellement apparu
   rembourse la garantie 3D. Une image seule ne repousse jamais cette garantie.

## 63. Originel et réactions 3D au regard

1. Avec `originelEnabled: true`, utilisez `/penumbra originel test` en extérieur,
   puis dans une galerie de deux blocs de haut.
2. **Attendu** : en grotte, l'Originel apparaît devant le joueur, pose une
   torche de redstone temporaire, fixe sa cible plusieurs secondes puis disparaît.
3. Soutenez son regard, puis celui d'un Observateur, pendant la durée configurée.
4. **Attendu** : le monstre se penche, lève les bras et effectue une lunge 3D
   vers le visage, frappe, puis seulement déclenche le screamer. Le visuel ne
   cible pas les autres joueurs ; un voisin proche entend seulement le son.
5. Changez de dimension ou déconnectez-vous pendant une scène.
6. **Attendu** : la scène est annulée proprement et la torche temporaire est
   restaurée uniquement si personne ne l'a modifiée.

## 64. Marteaux multiples, caméra et totem hanté 0.6.3

1. Fabriquez les quatre marteaux avec 5 pavés/lingots d'or/lingots de fer/diamants
   et 2 bâtons ; contrôlez les quatre textures et leur niveau de minage.
2. Maintenez la caméra VHS active 3 secondes en regardant une zone dégagée.
3. **Attendu** : l'Originel apparaît dans le cadre à 10–28 blocs, vous fixe puis
   peut bondir si vous soutenez son regard ; aucun second déclenchement caméra
   n'arrive pendant 90 secondes.
4. Construisez le totem décrit dans le README et utilisez le Sceau de cendre sur
   sa citrouille sculptée.
5. **Attendu** : particules d'âme, message du rituel et Originel visible près du
   totem. Un rituel incomplet ne consomme pas l'interaction.

## 65. Photo — capture et développement en solo

1. Donnez-vous la Caméra VHS, tenez-la en main et cliquez gauche sans activer C.
2. **Attendu** : une capture est créée et le clic ne casse pas de bloc avec la caméra.
3. Activez ensuite le viseur avec C, cadrez une scène très reconnaissable puis
   cliquez gauche après le délai. Réessayez immédiatement, puis après au moins 2 secondes.
4. **Attendu** : la première, la deuxième et la dernière tentative donnent chacune un seul
   tirage ; la tentative pendant le cooldown n’en donne aucun. Le cadrage 16:9
   correspond au centre du viseur et aucune erreur n’apparaît dans le journal.
5. Observez le tirage dans l’inventaire pendant 10 secondes.
6. **Attendu** : noir pendant environ 2,5 s, révélation faible, sépia, puis
   couleur complète à 10 s, sans changer l’UUID logique de la photo (les quatre
   identifiants d’objet ne servent qu’à son icône de développement).

## 66. Photo — visionneuse et inventaire plein

1. Tenez le tirage et cliquez gauche à chaque phase de développement.
2. **Attendu** : une visionneuse plein écran s’ouvre, respecte la phase courante
   et se ferme avec Échap ; aucun coup ou bloc cassé n’est produit.
3. Remplissez tous les emplacements de l’inventaire, reprenez une photo puis
   vérifiez le sol devant le joueur.
4. **Attendu** : le tirage est déposé au sol, un message prévient le joueur et
   la capture reste consultable après l’avoir ramassée.

## 67. Photo — cadre mural et recette

1. Fabriquez le Petit cadre photo avec un cuir au centre et quatre bâtons en
   croix (` S ` / `SLS` / ` S `).
2. Posez-le sur les quatre orientations d’un mur, insérez une photo par clic
   droit et observez-le de face et de biais.
3. **Attendu** : cadre petit, centré, solidement accroché ; la vraie image suit
   son développement et son orientation sans scintillement ni texture absente.
4. Avec une autre photo en main, cliquez droit directement sur un mur.
5. **Attendu** : un cadre garni est créé et un seul tirage est consommé. Cassez
   ensuite les deux cadres : cadre et photo sont récupérables, sans duplication.

## 68. Photo — flash et son en multijoueur

1. Connectez trois joueurs dans la même dimension : A avec la caméra, B à
   5 blocs et C à plus de 64 blocs. A prend une photo, viseur actif ou non.
2. **Attendu** : A et B voient le flash si `flashesEnabled: true`, ainsi que la
   particule dans le monde, et entendent le déclic avec atténuation spatiale.
   C ne reçoit ni flash ni son.
3. Passez `flashesEnabled: false` sur B et recommencez.
4. **Attendu** : B ne subit aucun voile blanc plein écran ; la particule monde
   et le déclic spatial restent présents. Seul A reçoit le tirage.

## 69. Photo — partage, persistance et autorisations

1. A prend une photo, la remet à B puis se déconnecte. B l’ouvre, la place dans
   un cadre et redémarre complètement le serveur.
2. **Attendu** : B peut encore consulter le tirage reçu et le cadre affiche la
   même image après redémarrage ; aucune donnée pixel n’est stockée dans le
   paquet d’objet, seulement sa référence persistante.
3. C s’approche à moins de 160 blocs du cadre, attend son chargement, puis
   s’éloigne et se reconnecte à grande distance.
4. **Attendu** : le serveur sert l’image exposée à proximité mais refuse une
   demande arbitraire pour une photo que C ne possède pas et qui n’est pas
   exposée près de lui.

## 70. Photo — limites et robustesse serveur

1. Automatisez au besoin des captures espacées de 40 ticks jusqu’à atteindre
   128 photos pour un joueur ; tentez-en une 129e. Sur une copie de test,
   atteignez aussi 2 048 photos mondiales avec plusieurs joueurs.
2. **Attendu** : les captures surnuméraires sont refusées avec le message
   d’album plein, sans objet vide, désynchronisation ni croissance du SavedData.
3. Essayez de capturer mort, en spectateur, caméra hors main, viseur inactif et
   sous rafale de clics.
4. **Attendu** : aucun paquet invalide n’aboutit, aucun crash serveur, aucune
   capture fantôme et aucun dépassement du débit C2S borné.

## Critère historique de sortie 0.6.4

Toutes les sections 1 à 70 pertinentes au type de partie passent, le JAR et
sa métadonnée annoncent tous deux 0.6.4, une ancienne configuration migre sans
perdre ses choix, les interrupteurs d'accessibilité coupent effectivement leurs
effets, et les journaux client/serveur sont propres.

---

# Release 0.7.0 — Projectionniste, Rampant et Absent

Les commandes de cette section demandent le niveau opérateur 2. Les commandes
`test` forcent une scène mais ne remplacent pas les essais du rythme naturel :
elles ignorent la chance et tout ou partie des cooldowns, tout en conservant les
contraintes de placement. Le Rampant et L'Absent refusent le mode Paisible ; un
Projectionniste forcé s'y dissout dès son premier tick.

## 71. Projectionniste — photo hantée et manifestation

1. Dans une copie de monde dont l'album n'est pas plein, prenez des photos
   reconnaissables espacées d'au moins 40 ticks. Observez chaque tirage pendant
   ses 10 secondes de développement. Répétez jusqu'à obtenir une silhouette de
   Projectionniste ; le tirage serveur est de 15 %, donc le nombre de captures
   nécessaire n'est pas déterministe.
2. **Attendu** : la capture reste d'abord noire, puis la silhouette remonte
   progressivement avec l'image au lieu d'apparaître en une frame. Environ
   15 % des nouvelles photos sont marquées sur un grand échantillon, sans
   exiger exactement ce ratio sur une courte série.
3. Pendant une phase intermédiaire, ouvrez le tirage par clic gauche,
   conservez la visionneuse ouverte plus de 1,5 seconde puis fermez-la avant
   que la couleur soit complète.
4. **Attendu** : aucune manifestation n'est demandée. Attendez la couleur
   complète, gardez la photo dans la main principale, rouvrez-la et observez-la
   sans interruption pendant au moins **30 ticks (1,5 s)**.
5. **Attendu** : un effet d'obscurité cible le propriétaire et le
   Projectionniste apparaît sur un sol chargé derrière lui, accompagné d'un
   souffle spatial. Il le fixe **80 à 120 ticks (4–6 s)**, avance brièvement
   puis disparaît en cendre, sans dégâts ni bloc modifié.
6. Après sa disparition, rouvrez immédiatement une photo hantée développée et
   observez-la 1,5 seconde, puis recommencez après 90 secondes.
7. **Attendu** : aucune seconde manifestation avant la fin du cooldown ; elle
   redevient possible ensuite si un placement valide existe.
8. Dans une zone dégagée avec un sol chargé derrière vous, exécutez
   `/penumbra projectionist test`, puis recommencez au-dessus d'une zone sans
   emplacement valide.
9. **Attendu** : la commande réussit et reproduit la manifestation dans le
   premier cas ; elle échoue explicitement dans le second, sans forcer de chunk
   ni créer d'entité orpheline.

## 72. Rampant — plafond, bond, screamer et lumière

1. Construisez ou trouvez une grotte de l'Overworld sans vue du ciel, sous le
   niveau de la mer + 12, avec lumière de bloc ≤ 5, un plafond ou une paroi
   solide à 10–22 blocs et un sol sec au plus 8 blocs plus bas.
2. Exécutez `/penumbra rampant test`. Au besoin, essayez une seconde salle
   haute avec paroi afin de couvrir les deux orientations.
3. **Attendu** : un seul Rampant propre au joueur apparaît réellement collé au
   plafond ou à la paroi, avec le modèle orienté contre son support. En plein
   air, dans l'eau, en Paisible ou sans surface valide, la commande échoue
   proprement.
4. Passez sous lui ou attendez au plus 75 ticks après son affût initial.
5. **Attendu** : il se décroche avec un son positionné, tombe sur le sol,
   poursuit uniquement son propriétaire au ras du sol puis bondit lorsqu'il
   est à 2,4–8 blocs avec ligne de vue.
6. Laissez le premier bond atteindre sa cible.
7. **Attendu** : les dégâts sont appliqués à l'impact, un seul screamer initial
   est envoyé à la cible et la créature reprend sa chasse sans rester bloquée
   en phase de bond.
8. Pendant la chasse, posez assez de torches pour dépasser une lumière de bloc
   de 7 autour du Rampant et maintenez cet éclairage 5 secondes.
9. **Attendu** : il cesse de charger, fuit vers une case plus sombre et
   disparaît si la lumière demeure ; si une zone ≤ 5 est trouvée avant le
   délai, il peut reprendre la chasse. Aucun bloc n'est cassé ou posé.
10. Pour le rythme naturel, jouez au stade de contenu ≥ 2 après la période de
    grâce, avec `undergroundEventsEnabled`, `chasesEnabled`, `eventFrequency`
    et `horrorIntensity` actifs. Après une apparition naturelle réussie,
    surveillez la même session pendant 6 minutes.
11. **Attendu** : aucune seconde apparition naturelle pour ce joueur pendant
    les **6 minutes** de cooldown. La commande `test` n'entame pas ce délai et
    ne doit pas servir à mesurer cette règle.

## 73. Absent — faux nom, leurres, retrait et charge

1. En solo, placez un lit, une porte en bois ou un coffre à moins de 10 blocs,
   laissez un sol libre autour puis exécutez `/penumbra absent test`.
2. **Attendu** : une silhouette humanoïde apparaît près du refuge ou, si ce
   placement échoue, à 18–34 blocs. Son nom visible est une altération du nom
   du joueur, jamais un joueur supplémentaire dans TAB.
3. Restez à distance pendant l'observation, lisez les messages gris en
   italique, puis avancez dans la direction où la silhouette se retire.
4. **Attendu** : les messages utilisent le faux nom, la créature s'éloigne et
   détecte une poursuite réelle. Après au moins 45 ticks de leurre, se
   rapprocher à 8,5 blocs ou la suivre assez longtemps déclenche la révélation.
5. **Attendu** : sa pose se brise, une dernière réplique est envoyée, puis
   L'Absent charge. Un premier coup porté avant la révélation force cette phase
   sans effacer la scène ni infliger de dégâts à la créature.
6. Laissez la charge atteindre la cible.
7. **Attendu** : L'Absent frappe son propriétaire, lui seul reçoit l'image de
   screamer si elle est activée, puis la créature disparaît en cendre. Sans
   poursuite, elle continue de s'éloigner et disparaît au plus tard à sa durée
   de vie bornée de 75 secondes. Aucun bloc, coffre ou inventaire n'est modifié.
8. Pour le rythme naturel, jouez au stade de contenu ≥ 2 après la période de
   grâce, avec fréquence et intensité non nulles. Après une rencontre naturelle
   réussie, surveillez la même session pendant 8 minutes.
9. **Attendu** : aucune seconde rencontre naturelle pour ce joueur pendant les
   **8 minutes** de cooldown. `/penumbra absent test` ignore ce délai sans le
   consommer.

## 74. Multijoueur — propriété des scènes et sons de proximité

1. Connectez A, B et C au même serveur avec le JAR 0.7.0 sur chaque client :
   B reste à environ 5 blocs de A et se rapproche à 5–10 blocs du Rampant une
   fois celui-ci fixé ; C reste à plus de 128 blocs ou dans une autre
   dimension. Donnez le niveau opérateur uniquement à A.
2. A force successivement `/penumbra projectionist test`,
   `/penumbra rampant test` dans une grotte valide et
   `/penumbra absent test`, en laissant chaque scène se terminer.
3. **Attendu** : les entités physiques sont visibles par B lorsqu'elles sont à
   portée de tracking, mais restent liées à A. Le Projectionniste ne blesse
   personne ; le Rampant et L'Absent ne poursuivent ni B ni C.
4. **Attendu audio** : B entend une seule fois et dans la bonne direction le
   souffle du Projectionniste, le décrochage/pas/impact du Rampant et les
   voix/cris de L'Absent. Le volume suit la distance. C ne reçoit aucun son
   hors de portée et aucun joueur d'une autre dimension ne l'entend.
5. **Attendu ciblage** : l'obscurité et les dialogues ne visent que A ; dégâts
   et images plein écran de screamer ne sont jamais recopiés sur B ou C. Avec
   `screamersEnabled: false`, les visages disparaissent sans couper les sons
   de scène ordinaires.
6. Pour L'Absent, recommencez avec B connecté et non spectateur.
7. **Attendu** : le faux nom choisi pour la scène de A est celui d'un véritable
   autre joueur connecté, tandis que la cible, le suivi et le cooldown restent
   ceux de A. `/penumbra status` de B et C ne change pas.
8. Déconnectez A au milieu de chaque scène et contrôlez les entités puis les
   journaux serveur.
9. **Attendu** : les manifestations perdent leur propriétaire et se suppriment
   sans attaque résiduelle, son dupliqué, erreur réseau ni stack trace.

## 75. Compatibilité des photos et albums 0.6.4

1. Avec la 0.6.4 sur une copie de test, créez plusieurs photos : une en main,
   une dans l'inventaire et une dans un cadre. Arrêtez proprement le serveur et
   sauvegardez le monde avant d'installer uniquement `penumbra-0.7.0.jar`.
2. Chargez le monde en 0.7.0, ouvrez chaque tirage, observez le cadre, échangez
   une photo entre deux joueurs puis redémarrez encore le serveur.
3. **Attendu** : UUID, pixels, propriétaire, quota et développement restent
   lisibles ; les images en main, dans la visionneuse et dans les cadres sont
   identiques avant et après redémarrage.
4. Gardez une ancienne photo développée dans la main principale et observez-la
   plus de 1,5 seconde dans la visionneuse.
5. **Attendu** : aucune silhouette du Projectionniste n'est ajoutée et aucune
   manifestation ne se produit. Le champ `projectionist` absent des données
   0.6.4 est interprété comme `false`, sans migration destructive.
6. Prenez ensuite de nouvelles photos 0.7.0 et partagez-en une hantée avec un
   second joueur autorisé à la consulter.
7. **Attendu** : nouvelles et anciennes références coexistent dans le même
   album ; seul un nouveau tirage explicitement marqué peut révéler puis
   manifester le Projectionniste pour la personne qui le tient.

## Critère historique de sortie 0.7.0

Les sections 1 à 75 pertinentes passent sans crash ni corruption. Le JAR et
`fabric.mod.json` annoncent 0.7.0, les trois commandes de test fonctionnent
dans leurs environnements valides, les cooldowns naturels sont respectés, les
sons restent de proximité et les anciennes photos 0.6.4 restent consultables
sans devenir hantées rétroactivement.

---

# Release 0.8.0 — Rampant, Sceau de bannissement, Cavaleuse et neuf screamers

Utilisez `penumbra-0.8.0.jar` sur le serveur et tous les clients. Les commandes
de cette section demandent le niveau opérateur 2. Les commandes de la
Cavaleuse imposent un mode de scène, mais contournent sa chance, son cooldown
et ses conditions naturelles ; elles ne servent donc pas à mesurer son ratio
70/30.

## 76. Rampant — escalade réelle et animations 3D

1. Préparez une grotte sombre avec un sol sec, une paroi pleine à moins de
   5 blocs de la zone de chasse et un plafond raccordé à cette paroi. Ajoutez
   ailleurs une paroi discontinue et une zone pouvant être éclairée au-delà de
   7. Exécutez `/penumbra rampant test`.
2. **Attendu** : le Rampant apparaît sur un support réel, se décroche puis
   chasse au sol comme en 0.7.0. Lorsqu'il décide de se percher, il rejoint le
   pied de la paroi par sa navigation normale : aucun saut de position,
   passage à travers un bloc ou bloc posé/cassé ne doit être observé.
3. Laissez-le atteindre la paroi raccordée au plafond et observez-le depuis la
   troisième personne ou avec un second client proche.
4. **Attendu** : il monte verticalement, s'aligne sur la face solide puis passe
   au plafond si le support continue. Le corps et les membres sont orientés
   contre la surface, l'ombre disparaît pendant l'accrochage et les segments de
   pattes s'articulent au lieu de faire glisser un modèle figé.
5. Observez successivement la course, la fuite, l'escalade, la chute et un bond.
6. **Attendu** : chaque phase possède une pose 3D lisible ; oscillation du
   corps, tête, colonne et démarche diagonale restent cohérentes avec le
   déplacement, sans rotation brutale à la transition mur/plafond.
7. Laissez un bond atteindre la cible puis gardez une paroi valide à proximité.
8. **Attendu** : après l'attaque, le Rampant peut prioriser une nouvelle montée
   et se raccrocher au lieu de rester indéfiniment au sol.
9. Retirez manuellement un bloc de support sous lui, testez la paroi
   discontinue, puis éclairez sa position avec une lumière de bloc > 7.
10. **Attendu** : la perte de support provoque une libération/chute propre ; une
    escalade impossible s'arrête dans le délai borné au lieu de boucler ; la
    lumière forte déclenche la fuite. Aucun crash, entité figée, changement de
    bloc produit par le Rampant ni ombre flottante ne subsiste.

## 77. Sceau de bannissement — recette, rayon et filtre

1. Dans n'importe quelle grille de craft, placez un papier, un charbon de bois
   et une redstone dans plusieurs arrangements.
2. **Attendu** : la recette est sans forme et fournit exactement **2**
   `penumbra:ash_ward`. Le Sceau de cendre historique
   (`penumbra:ash_sigil`) reste distinct, disponible dans une ancienne
   sauvegarde et utilisable sur le totem hanté.
3. Placez autour du joueur plusieurs monstres Penumbra : un à environ
   15,5 blocs, un à 16,5 blocs et un en diagonale à `(12, 12)` blocs, donc à
   environ 17 blocs malgré deux coordonnées inférieures à 16. Placez aussi à
   moins de 16 blocs un zombie vanilla, un animal, un cadre photo et un second
   joueur.
4. En Survie, tenez `ash_ward` et faites un clic droit.
5. **Attendu** : seuls les monstres du namespace `penumbra` situés à une vraie
   distance de **16 blocs ou moins** disparaissent. La cible axiale à 16,5
   blocs et la cible diagonale à environ 17 blocs restent présentes, tout comme
   le zombie, l'animal, le cadre et le joueur. Le message donne le bon nombre
   de présences, les particules et le son de rituel sont joués, un objet est
   consommé et le cooldown dure 60 ticks.
6. Éloignez ou supprimez tous les monstres Penumbra puis réutilisez le Sceau de
   bannissement.
7. **Attendu** : le message indique que rien n'est assez proche, aucun autre
   type d'entité ne disparaît, la pile n'est pas consommée et le cooldown dure
   20 ticks.
8. Répétez une utilisation réussie en Créatif.
9. **Attendu** : les mêmes cibles Penumbra sont bannies, mais la pile ne diminue
   pas. Le maximum de pile reste 16 et `/penumbra item give ash_ward` fournit
   bien le nouvel objet, pas `ash_sigil`.

## 78. Cavaleuse — traversée, poursuite et rythme naturel

1. En mode non Paisible, sur un terrain sec et dégagé, exécutez
   `/penumbra scuttler cross` en regardant droit devant vous.
2. **Attendu** : une très petite araignée apparaît sur un sol réel à environ
   4,8 à 8,6 blocs devant le joueur, traverse rapidement son champ de vision
   de côté et disparaît en au plus 48 ticks, sans attaque ni bloc modifié.
3. Exécutez `/penumbra scuttler chase` et chronométrez la scène.
4. **Attendu** : la Cavaleuse part de 6,5 à 12 blocs derrière, suit et contourne
   les chaussures pendant **60 à 100 ticks (3 à 5 secondes)** puis disparaît.
   Le contact n'inflige jamais de dégâts et elle ne sélectionne aucune cible
   d'attaque.
5. Recommencez puis frappez la Cavaleuse une fois avant la fin de sa scène.
6. **Attendu** : elle se dissipe immédiatement, sans subir une séquence de
   combat, lâcher de butin, infliger de dégâts ou riposter.
7. Essayez les deux commandes au-dessus de l'eau, sans sol valide, en
   spectateur puis en Paisible.
8. **Attendu** : la commande échoue explicitement et proprement, sans entité
   orpheline. Avec un placement valide, il ne peut exister qu'une Cavaleuse
   propriétaire par joueur et jamais plus de 12 sur le serveur.
9. Exécutez les tests JUnit `ScuttlerBehaviorTest`, puis jouez après la grâce au
   stade narratif ≥ 1 dans une grotte ou une nuit de lumière de bloc ≤ 6.
10. **Attendu** : les tests fixent exactement 70 choix de traversée et 30 de
    poursuite sur la table déterministe, ainsi que la durée de 3 à 5 s, les
    bornes du cooldown et le dénominateur de base 1/32. En jeu, la décision
    naturelle est amortie à une tentative toutes les 5 s et aucune seconde
    scène ne survient pour le même joueur avant les 2 minutes de cooldown.
11. Passez `chasesEnabled` à `false`, rechargez la configuration et attendez une
    scène naturelle ; utilisez les commandes séparément seulement pour vérifier
    que les deux animations restent testables.
12. **Attendu** : tout choix naturel de poursuite retombe sur une traversée ; la
    créature reste inoffensive dans les deux modes.

## 79. Screamers 7 à 9 et sac mélangé

1. Exécutez neuf fois `/penumbra jumpscare screamer`, en attendant au moins le
   cooldown critique de 100 ticks entre les commandes et en notant l'image à
   chaque passage.
2. **Attendu** : les neuf variantes apparaissent exactement une fois avant le
   cycle suivant. Les nouvelles images sont reconnaissables : visage-caméra du
   Projectionniste en 7, Rampant arachnéen jaillissant de la grotte en 8 et
   visage dédoublé de L'Absent en 9.
3. Déclenchez une dixième image et comparez-la à la neuvième, puis recommencez
   un cycle complet.
4. **Attendu** : la première image du nouveau cycle n'est jamais la dernière du
   cycle précédent ; chaque nouveau cycle reste une permutation complète.
   `ShuffledIndexBagTest` couvre également ces deux invariants.
5. Répétez sur des fenêtres 4:3, 16:9 et 21:9, puis forcez plusieurs
   `/penumbra jumpscare apparition`.
6. **Attendu** : aucun visage n'est étiré ; le carré couvre l'écran avec un
   rognage symétrique. `apparition` continue d'utiliser seulement les variantes
   4 à 6 et ne consomme pas le sac complet de neuf.
7. Passez `screamersEnabled` à `false` et forcez les deux types de jumpscare.
8. **Attendu** : aucune image plein écran n'est rendue. Les cooldowns, le
   plafond `jumpscareVolume` et les sons ordinaires de scène conservent leur
   comportement d'accessibilité documenté.

## 80. Multijoueur — scènes physiques et ciblage

1. Connectez A, B et C au même serveur : B reste à 5–10 blocs de A, C à plus de
   128 blocs ou dans une autre dimension. A force successivement les deux
   scènes de la Cavaleuse et une escalade du Rampant.
2. **Attendu** : B voit les entités physiques et entend leurs sons spatiaux
   proches ; C ne reçoit aucun son hors de portée. Les créatures restent liées
   à A, la Cavaleuse ne blesse personne et le Rampant ne change pas de cible.
3. Faites apparaître dans le rayon de A des monstres Penumbra appartenant à A
   et B, plus un zombie vanilla, puis utilisez `ash_ward` avec A.
4. **Attendu** : les monstres Penumbra partagés à 16 blocs ou moins sont bannis,
   tandis que l'Originel et le Contrefait privés appartenant à B restent
   présents et invisibles pour A. B, le zombie et toute entité hors rayon sont
   aussi épargnés. Le son du rituel reste spatial ; aucun visage de screamer
   n'est envoyé aux témoins.
5. Déconnectez A ou changez-le de dimension au milieu d'une traversée, d'une
   poursuite et d'une escalade.
6. **Attendu** : les scènes perdant leur propriétaire se terminent sans attaque
   résiduelle, entité orpheline, paquet invalide ni stack trace serveur.

## 81. Compatibilité des sauvegardes 0.7.0

1. Sur une copie de monde 0.7.0, conservez photos et cadres 0.6.4, un exemplaire
   de `ash_sigil` et la progression/cooldowns des trois monstres 0.7. Arrêtez le
   serveur puis remplacez son unique ancien JAR par `penumbra-0.8.0.jar`.
2. **Attendu** : le monde charge sans conversion manuelle ni entrée de registre
   manquante. Photos, albums, cadres, Projectionniste, Rampant et Absent gardent
   le comportement validé dans les sections 71 à 75.
3. Utilisez l'ancien `ash_sigil` sur un totem hanté, puis fabriquez et utilisez
   séparément `ash_ward`.
4. **Attendu** : le rituel historique fonctionne toujours avec `ash_sigil` ;
   seul `ash_ward` applique le bannissement local. Aucun objet existant n'a été
   renommé, remplacé ou transformé silencieusement.

## 82. Sources et reproduction des assets 0.8.0

1. Vérifiez les sept sources de travail listées dans `docs/ASSETS.md` sous
   `tools/image_sources/` : deux variantes du Sceau de bannissement, deux de la
   Cavaleuse et
   les trois concepts de screamer.
2. Dans une copie propre avec Pillow disponible, calculez les empreintes des
   cinq sorties puis exécutez `python tools/process_080_assets.py` et recalculez
   les empreintes.
3. **Attendu** : le script ne réécrit que `ash_ward.png`, `scuttler.png` et
   `screamer_host_7.png` à `_9.png` ; les sorties sont identiques à celles
   versionnées, aux dimensions 64×64, 64×64 et 512×512 attendues.
4. Exécutez enfin `gradlew.bat validatePenumbraResources test --offline`.
5. **Attendu** : les neuf screamers carrés, les nouveaux atlas/icônes, la
   recette sans forme et les tests `RampantBehaviorTest`, `AshWardItemTest`,
   `ScuttlerBehaviorTest` et `ShuffledIndexBagTest` passent sans erreur.

## Critère de sortie 0.8.0

Les sections 1 à 82 pertinentes passent sans crash ni corruption. Le JAR et
`fabric.mod.json` annoncent 0.8.0 ; le Rampant grimpe les surfaces réelles avec
ses poses 3D, le Sceau de bannissement respecte un vrai rayon de 16 blocs et le
namespace
Penumbra, la Cavaleuse reste inoffensive avec son partage 70/30, les neuf
screamers tournent sans répétition immédiate et une sauvegarde 0.7.0 reste
lisible sans renommer `ash_sigil`.

# Release 0.8.1 — coordination multijoueur

Utilisez `penumbra-0.8.1.jar` sur le serveur et sur chaque client. Pour les
tests de rythme naturel, réglez temporairement `eventGracePeriodSeconds: 0`,
`eventFrequency: 4.0`, `horrorIntensity: 2.0` et `debugLogging: true`, puis
redémarrez le serveur. N'utilisez pas de commande de forçage dans les sections
83 et 84 : ces commandes contournent volontairement la coordination.

## 83. Scènes lourdes — fenêtre locale et équité

1. Connectez A et B dans la même dimension, à moins de 32 blocs, avec une
   tension élevée et des conditions compatibles avec les événements naturels.
2. Relevez pendant plusieurs cycles l'heure serveur et le propriétaire de
   chaque apparition 3D naturelle dans le journal de débogage.
3. **Attendu** : deux scènes lourdes du groupe ne commencent jamais à moins de
   80 ticks (4 secondes) l'une de l'autre. Les sons courts peuvent continuer
   entre-temps.
4. Poursuivez suffisamment longtemps pour observer plusieurs sélections.
5. **Attendu** : la première tentative alterne entre les joueurs ; A ne
   monopolise pas systématiquement les apparitions parce qu'il s'est connecté
   avant B.

## 84. Joueurs éloignés et dimensions indépendantes

1. Gardez A et B côte à côte, puis placez C à plus de 32 blocs dans la même
   dimension. Dans un second essai, envoyez C dans une autre dimension.
2. Observez uniquement les scènes automatiques lourdes.
3. **Attendu** : A et B partagent toujours une seule réservation locale. C peut
   démarrer une scène pendant leur fenêtre de 4 secondes dans les deux essais ;
   sa réservation ne bloque pas leur groupe et réciproquement.
4. Recommencez en solo.
5. **Attendu** : aucun délai supplémentaire n'est ajouté au rythme solo.

## 85. Sons compagnons — limite de 24 blocs et déduplication

1. Placez B à 23 blocs de A et C à 25 blocs de A, tous dans la même dimension,
   assez loin du point sonore choisi pour que seule la règle compagnon puisse
   les inclure. Forcez sur A un événement sonore positionné.
2. **Attendu** : A et B entendent le son avec sa direction ; C ne reçoit pas le
   paquet. Un joueur placé dans une autre dimension ne l'entend jamais.
3. Rapprochez C du point sonore sans le rapprocher d'A et recommencez.
4. **Attendu** : C peut alors entendre le son par sa portée acoustique normale,
   indépendamment du rayon compagnon.
5. Déclenchez dans le même tick deux envois prioritaires strictement identiques,
   puis une rafale de sons ordinaires pour une même source et un même auditeur.
6. **Attendu** : un seul doublon prioritaire est joué et les sons ordinaires
   sont espacés d'au moins 3 ticks ; une autre source conserve sa propre route.

## 86. Ciblage strict des créatures

1. A force successivement un Hôte, un Originel, un Contrefait, un Rampant,
   L'Absent et une Cavaleuse ; B reste à proximité et C dans une autre
   dimension.
2. B croise le regard des entités et se place volontairement plus près qu'A.
3. **Attendu** : aucune créature ne remplace A par B ou C comme cible. Les
   dégâts, dialogues privés et images de screamer restent destinés à A.
4. **Attendu visuel** : les entités physiques partagées peuvent être visibles
   par B ; l'Originel et le Contrefait restent suivis uniquement par A. Les sons
   positionnés peuvent être entendus par B selon les règles de la section 85.

## 87. Nettoyage à la déconnexion

1. Déclenchez pour A une scène, un son et un jumpscare, puis déconnectez A
   pendant l'effet. Reconnectez-le immédiatement, puis redémarrez aussi le
   serveur pour un second essai.
2. **Attendu** : aucun ancien visuel, son mis en attente, cible de créature ou
   verrou de scène transitoire n'est rejoué dans la nouvelle session. Aucun
   avertissement réseau ni stack trace n'apparaît.
3. **Attendu** : le profil de tension, l'histoire et les photos persistent,
   puisqu'ils ne font pas partie des caches transitoires.

## 88. Album — quotas mondial et individuel

1. Sur une copie de test automatisée ou sauvegardée, faites atteindre à A son
   quota de 128 photos puis tentez une 129e capture.
2. **Attendu** : la 129e est refusée proprement, mais B peut encore prendre des
   photos : A n'a pas consommé son quota individuel.
3. Avec plusieurs joueurs, atteignez 2 048 captures valides dans le monde puis
   tentez une 2 049e capture avec un joueur encore sous son quota individuel.
4. **Attendu** : la 2 049e est refusée par le plafond mondial. Les tirages déjà
   sauvegardés restent consultables après reconnexion et redémarrage.

## 89. Commandes forcées non bridées

1. Depuis A ou la console, utilisez le joueur cible optionnel, par exemple
   `/penumbra event distant_sighting A` puis
   `/penumbra rampant test B`. Vérifiez aussi `/penumbra status B` et
   `/penumbra tension 100 B`.
2. **Attendu** : chaque effet et chaque profil concernent le joueur nommé, pas
   l'auteur de la commande. Cela reste vrai si B change de dimension.
3. Placez A et B à moins de 32 blocs et forcez immédiatement une scène lourde
   différente pour chacun, sans attendre 4 secondes.
4. **Attendu** : les deux commandes s'exécutent. Elles conservent le ciblage
   strict et les protections réseau, mais ignorent le rythme automatique afin
   de permettre le débogage.

## 90. Préflight et compatibilité 0.8.1

1. Exécutez `gradlew.bat clean build --offline` avec un JDK 25 et ouvrez
   `build/libs/penumbra-0.8.1.jar` comme une archive.
2. **Attendu** : le nom du JAR et `fabric.mod.json` annoncent `0.8.1`; les tests
   de coordination, de sons, de ciblage et de quotas passent sans erreur.
3. Chargez une copie d'un monde 0.8.0 contenant des photos et cadres.
4. **Attendu** : aucune migration manuelle ni entrée de registre manquante ; les
   anciennes photos restent lisibles et de nouvelles captures peuvent dépasser
   l'ancienne limite mondiale de 512 sans dépasser 2 048.

## Critère de sortie 0.8.1

Les sections 1 à 90 pertinentes passent sans crash ni corruption. Le JAR et
`fabric.mod.json` annoncent 0.8.1 ; les scènes lourdes proches respectent la
fenêtre équitable de 4 secondes, les groupes éloignés et dimensions restent
indépendants, le rayon compagnon sonore est de 24 blocs, les monstres ne
changent jamais de propriétaire, les états transitoires sont nettoyés et
l'album accepte 2 048 photos mondiales sans dépasser 128 par joueur.

# Release 0.9.0 — album, scènes asymétriques et animations

## 91. Régression visionneuse — vraie image, aucun texel gris

1. Prenez une photo très contrastée, attendez dix secondes, tenez le tirage et
   faites un clic gauche sur plusieurs ratios et échelles d'interface.
2. **Attendu** : la capture 16:9 entière remplit le cadre sans étirement, texel
   gris uniforme ni recadrage décalé. Tant que les pixels n'arrivent pas, le
   texte reste « chargement » et n'annonce jamais prématurément la fin.
3. Fermez avec clic droit ou Échap.
4. **Attendu** : retour au jeu, ou à l'album si la photo venait de la galerie.

## 92. Album complet et chargement borné

1. Caméra en main, faites un clic droit. Naviguez entre « Mes photos » et
   « Photos publiques », les flèches, la molette et six vignettes par page.
2. **Attendu** : aucune page ne contient plus de six métadonnées ; les images
   arrivent progressivement sans burst, et un double clic ouvre la sélection.
3. Renommez une photo avec espaces, accents, caractères de contrôle et plus de
   32 caractères.
4. **Attendu** : le serveur supprime contrôles/formatage, borne à 32 caractères
   et conserve le nom après redémarrage.

## 93. Visibilité, révocation et suppression

1. A publie une photo ; B la voit dans l'onglet public et l'ouvre. A la rend
   ensuite privée sans donner le tirage à B.
2. **Attendu** : la texture de B est révoquée, sa visionneuse se ferme et la
   photo disparaît de la page publique. Si B possède réellement un tirage, son
   accès direct reste autorisé.
3. A clique une fois sur Supprimer, attend plus de trois secondes, puis clique
   de nouveau ; recommence ensuite deux clics en moins de trois secondes.
4. **Attendu** : le premier essai expire sans suppression ; le second efface
   l'archive, invalide tous les caches et libère immédiatement une place du
   quota. Un UUID arbitraire envoyé par B est refusé.

## 94. Scènes coopératives dédiées

1. Placez A et B dans la même dimension à moins de 28 blocs. Forcez
   `/penumbra event behind_other A`, puis `paired_echo` et `stolen_name`.
2. **Attendu** : A voit la traversée privée derrière B ; B n'en reçoit que les
   pas. Pour `paired_echo`, chacun entend une moitié différente aux mêmes
   instants. L'Absent porte exactement le nom de B mais poursuit seulement A.
3. Déconnectez B ou changez-le de dimension entre les deux battements.
4. **Attendu** : aucune nouvelle cible n'est choisie et la suite est annulée.
   Sans B, chaque événement utilise sa variante solo sans échouer.

## 95. Animations et finales nommées

1. Forcez Observateur, Originel, Contrefait, Projectionniste, Rampant et Absent.
2. **Attendu** : respiration et micro-mouvements restent subtils au repos ; les
   charges lisent anticipation, projection et impact sans rotation non finie.
   Le Rampant articule ses pattes sur sol, mur, plafond, chute et bond.
3. Forcez `/penumbra jumpscare rampant_screamer A`, `absent_screamer A` puis
   `chorus_screamer A` après les cooldowns nécessaires.
4. **Attendu** : chaque commande affiche sa texture propre en conservant ratio,
   options d'accessibilité, volume local et cri spatial pour les voisins.

## 96. Préflight 0.9.0

1. Exécutez `gradlew.bat clean build --offline` avec le JDK 25 puis démarrez
   `runServer` et `runClient`.
2. **Attendu** : 97 tests passent, les ressources et trois langues sont
   validées, aucun crash/stack trace n'apparaît et
   `build/libs/penumbra-0.9.0.jar` annonce la même version en interne.
3. Chargez une sauvegarde 0.8.1 avec photos et cadres.
4. **Attendu** : titres vides, visibilité privée, pixels et UUID inchangés ;
   aucune migration manuelle n'est demandée.

## Critère de sortie 0.9.0

Les sections 91 à 96 s'ajoutent aux régressions précédentes : aucune photo
grise, album totalement gérable et autoritaire, scènes de duo sans changement
de cible, douze screamers valides, animations finies et JAR 0.9.0 démarrant sur
client comme serveur dédié.

# Release 0.10.0 — preuves et dossier photographiques

Les tests 97 à 102 s'ajoutent à toutes les régressions précédentes. Les essais
réseau qui injectent un paquet invalide doivent être réalisés sur une copie du
monde, avec le client de développement ou une fixture automatisée ; aucun client
modifié ne doit rejoindre un serveur public pour ce protocole.

## 97. Flash — ticket serveur et consommation atomique

1. Caméra en main, prenez une photo normalement et relevez le nombre de tirages
   avant/après ainsi que l'UUID reçu.
2. **Attendu** : `PhotoShutter` crée le flash et fige la séquence côté serveur ;
   le `PhotoCapture` de même séquence produit exactement un tirage et un UUID.
3. Dans une fixture réseau, rejouez le même `PhotoCapture`, envoyez une mauvaise
   séquence, puis retardez un autre envoi de plus de **60 ticks**. Testez aussi
   un tableau RGB de taille invalide avant de renvoyer le bon paquet.
4. **Attendu** : aucun de ces paquets ne crée de photo. Le ticket valide est à
   usage unique et une charge invalide le consomme sans permettre de second essai.
5. Avec un point d'arrêt ou une latence contrôlée, cadrez un monstre au flash,
   déplacez ensuite le regard et le monstre avant l'envoi des pixels.
6. **Attendu** : la preuve correspond à l'instant de `PhotoShutter`, jamais à
   la position plus tardive de la caméra ou de l'entité.

## 98. Détection des huit catégories et preuves multiples

1. À l'aide des commandes de test existantes ou d'une fixture serveur, placez
   successivement au centre du cadre, à moins de 64 blocs : Hôte, Observateur,
   Originel, Contrefait, Projectionniste, Rampant, Absent et Cavaleuse.
2. Pour chaque entité, prenez une photo avec une ligne de vue dégagée, attendez
   son développement complet puis ouvrez-la.
3. **Attendu** : la catégorie française exacte est révélée. Une apparition
   privée ne peut être prouvée que par le joueur auquel le serveur l'autorise.
4. Recommencez avec l'entité derrière un mur, derrière le photographe, au bord
   puis hors du cône central de 36°, et enfin à plus de 64 blocs.
5. **Attendu** : ces cas ne gravent aucune preuve physique ; les cas exactement
   sur les limites de cône et de distance restent acceptés s'ils sont visibles.
   Une vitre transparente et un petit rebord cachant seulement les pieds restent
   acceptés grâce aux trois rayons ; un mur cachant les trois points reste refusé.
6. Cadrez plusieurs catégories et deux monstres d'une même catégorie dans une
   seule image.
7. **Attendu** : toutes les catégories uniques admissibles sont conservées,
   ordonnées par priorité/cadrage/distance ; le doublon ne compte qu'une fois.
   Une image peut donc porter jusqu'aux huit catégories connues.

## 99. Secret pendant le développement

1. Photographiez une anomalie puis ouvrez immédiatement l'album et la
   visionneuse, sans attendre dix secondes.
2. **Attendu** : l'album affiche « Analyse » et son pourcentage ; la visionneuse
   affiche le développement, sans nom, nombre ni identité de preuve.
   Avec une fixture ou un client instrumenté, inspectez aussi `PhotoData` : le
   serveur transmet `evidenceRevealed=false` et une liste vide avant 200 ticks.
3. Contrôlez à 199 ticks puis à **200 ticks** après le flash.
4. **Attendu** : rien n'est révélé à 199 ; à 200, la catégorie unique, les deux
   premières catégories d'une photo multiple, ou « aucune anomalie » apparaît.
   Le verdict `PhotoAnalysis` est léger et ne retransmet pas les pixels. Le
   changement dépend du temps du monde et reste stable après reconnexion.

## 100. Dossier persistant et quatre paliers

1. Développez puis révélez une première catégorie dans « Mes photos ».
2. **Attendu** : le dossier passe à **1/8, palier I**, annonce la nouvelle preuve
   et délivre l'indice I. Ouvrir plusieurs fois la même photo ne recompte rien.
3. Ajoutez des catégories uniques jusqu'aux comptes 2, 3, 4, 5, 6, 7 puis 8.
4. **Attendu** : les nouveaux paliers apparaissent exactement à **1, 3, 5 et
   7** catégories ; les comptes 2, 4, 6 et 8 ne créent pas de palier
   supplémentaire. Chaque catégorie n'est annoncée qu'une fois.
5. Supprimez la photo ayant livré la première preuve, redémarrez complètement
   le serveur puis rouvrez l'album.
6. **Attendu** : progression, catégories et palier restent acquis. Un autre
   joueur qui consulte la photo publique ne gagne pas la preuve dans son propre
   dossier : seule l'archive de son propriétaire est réconciliée.

## 101. Marqueur Important et ordres de l'album

1. Dans « Mes photos », créez au moins quatre tirages d'âges différents et
   marquez Important le plus ancien puis un tirage intermédiaire.
2. **Attendu** : les deux favoris remontent en tête, le plus récent des deux en
   premier ; toutes les photos normales suivent, elles aussi de la plus récente
   à la plus ancienne. Le badge `!` et le bouton « Normal » reflètent l'état.
3. Publiez ces tirages et consultez « Photos publiques » depuis deux joueurs.
4. **Attendu** : l'ordre public reste strictement chronologique et identique
   pour tous ; Important ne donne aucune priorité publique. Seul le propriétaire
   peut modifier le marqueur et l'état survit au redémarrage.

## 102. Migration, préflight et smoke tests 0.10.0

1. Chargez une copie 0.9.0 contenant titres, photos privées/publiques, cadres,
   tirages ordinaires et au moins un ancien tirage `projectionist` développé.
2. **Attendu** : pixels, UUID, titres, visibilité et cadres sont inchangés. Les
   champs `evidence`, `important` et `dossiers` absents utilisent leurs valeurs
   sûres ; aucune preuve physique n'est inventée sur un ancien tirage ordinaire.
   L'ancien Projectionniste conserve en revanche sa catégorie historique.
3. Exécutez `gradlew.bat clean build --offline` avec le JDK 25, puis démarrez
   `runServer` et `runClient` avec le même JAR.
4. **Attendu** : tous les tests JUnit et `validatePenumbraResources` passent,
   les trois langues restent synchronisées, aucun crash/stack trace Penumbra
   n'apparaît et `build/libs/penumbra-0.10.0.jar` annonce `0.10.0` en interne.
5. Ouvrez/changer très vite les onglets et pages plus de quatre fois en une
   seconde, ou faites perdre artificiellement une réponse `photo_album_page`.
6. **Attendu** : la demande est retentée au plus trois fois à 1,5 seconde ; soit
   la page arrive, soit « Chargement » disparaît au profit d'un message explicite.

## Critère de sortie 0.10.0

Les sections 97 à 102 passent en plus du socle 0.9.0 : flash atomique et borné,
huit catégories cadrées côté serveur, preuves multiples persistantes et secrètes
pendant 200 ticks, dossier personnel stable aux seuils 1/3/5/7, favoris limités
à l'ordre personnel, migration sans perte et JAR 0.10.0 fonctionnel sur client
comme sur serveur dédié.

# Release 0.11.0 — identité volée de L'Absent

## 103. Copie du joueur et démarche

1. Rejoignez à deux avec des skins et géométries de bras différents, équipez le
   second joueur d'une armure et d'objets visibles, puis exécutez
   `/penumbra absent test` depuis le premier joueur lorsque le second est hors de
   son champ de vision.
2. **Attendu** : L'Absent reprend le profil, les couches extérieures, le type de
   bras et l'équipement du second joueur. Rien ne tombe si la scène disparaît.
3. Faites marcher, sprinter, tourner, sauter et s'accroupir le joueur copié avant
   une nouvelle scène.
4. **Attendu** : la copie rejoue une démarche apparentée avec de petites erreurs
   croissantes, sans téléportation ni paquet de mouvement envoyé à chaque tick.

## 104. Soupçon, vraie rencontre et contact

1. Fixez brièvement la copie, détournez le regard, puis observez-la longtemps.
2. **Attendu** : elle passe du leurre au soupçon, se désynchronise et révèle sa
   vraie forme avant de poursuivre ; aucun screamer n'arrive pendant le regard.
3. Faites entrer le vrai joueur copié dans la même zone.
4. **Attendu** : la présence simultanée accélère la révélation. L'Absent ne
   frappe que le propriétaire de la scène et le screamer survient au contact.
5. Laissez la copie feindre de miner ou d'utiliser une porte.
6. **Attendu** : geste et son local sont visibles/audibles, mais le bloc et la
   porte restent strictement inchangés.

## 105. Flash, photographie et cendres

1. Photographiez L'Absent de face à moins de 24 blocs, puis recommencez derrière
   un mur, hors du cône et au-delà de 24 blocs.
2. **Attendu** : seul le premier flash le désoriente et force sa vraie forme. Le
   son du flash/râle est audible par les témoins proches et s'atténue avec la
   distance.
3. Ouvrez le premier tirage pendant son développement.
4. **Attendu** : la photo part du noir puis révèle progressivement la capture ;
   après environ 58 %, une double exposition propre à L'Absent apparaît. Les
   autres photos ne reçoivent jamais cette silhouette.
5. Utilisez le Sceau de bannissement avec le propriétaire puis avec un témoin.
6. **Attendu** : seul le propriétaire peut réussir ; la vraie forme reste visible
   durant une courte dissolution de cendre avant la disparition.

## 106. Build et smoke tests 0.11.0

1. Exécutez `gradlew.bat clean build --offline` avec le JDK 25, puis démarrez
   `runServer` et `runClient`.
2. **Attendu** : les 118 tests et la validation des ressources passent, le JAR
   `build/libs/penumbra-0.11.0.jar` annonce `0.11.0`, le serveur atteint `Done`
   et le client atteint le menu sans stack trace Penumbra.

## 107. Régression watchdog de génération

1. Sur un serveur dédié, créez un monde neuf avec les structures activées et
   chargez plusieurs chunks candidats. Pour reproduire exactement l'ancien
   blocage, utilisez la graine `-8397116075636203411` et chargez le chunk
   **[13, 5]** (centre bloc **[216, 88]**).
2. **Attendu** : le serveur reste réactif immédiatement, `list` répond, la
   structure est construite seulement après publication du chunk et aucun tick
   n'attend sa propre future `FULL`.
3. Restez connecté plus de 60 secondes, puis déclenchez une copie de refuge, une
   galerie décalée et un faux filon près de la limite des chunks chargés.
4. **Attendu** : aucun watchdog ni chargement forcé ; les scènes dont la zone
   complète n'est pas disponible échouent proprement et pourront être retentées.

## Critère de sortie 0.11.0

Les sections 103 à 107 passent en plus du socle 0.10.0 : copie exacte et sûre,
démarche bornée, révélation graduelle, attaque réservée au propriétaire, flash
avec distance/cône/visibilité, preuve photographique exclusive, dissolution de
cendre, génération sans watchdog et JAR 0.11.0 chargeable sur client comme sur
serveur dédié.

# Release 0.19.0 — perceptions individuelles

## 108. Faux compagnon strictement privé

1. Rejoignez un serveur avec deux joueurs côte à côte, puis exécutez
   `/penumbra perception companion <joueur-a>` depuis un opérateur.
2. **Attendu** : le joueur A voit une copie portant le skin, le nom, les couches
   et l'équipement visible d'un joueur ; le joueur B ne reçoit aucune entité,
   particule, animation ni son provenant de cette perception.
3. Faites traverser au joueur B la position de la copie et photographiez-la avec
   les deux clients.
4. **Attendu** : aucune collision n'affecte B. Seul A peut cadrer la copie, et
   son tirage ne reçoit jamais la preuve photographique de L'Absent.

## 109. Regard, caméra et disparition

1. Sans caméra, laissez le faux compagnon marcher, regardez-le brièvement, puis
   détournez les yeux.
2. **Attendu** : la scène ne disparaît pas au premier regard et le compteur de
   vérification redescend progressivement.
3. Fixez ensuite la copie. Recommencez sur une autre scène avec le zoom de la
   Caméra VHS actif, puis avec un flash cadré.
4. **Attendu** : elle s'arrête, se tourne et se dissipe après un regard soutenu ;
   le zoom accélère ce cycle et le flash valide provoque une disparition immédiate
   avec un retour adressé uniquement au propriétaire.

## 110. Cycle de vie, plafond et commandes

1. Utilisez `/penumbra perception status [joueur]`, puis changez de dimension,
   mourez ou déconnectez-vous pendant une scène. Testez enfin
   `/penumbra perception clear [joueur]`.
2. **Attendu** : le statut indique l'identité copiée et le temps restant ; chaque
   sortie invalide nettoie l'entité et le quota, et `clear` est idempotente.
3. Réglez `perceptionMaxActive: 1`, créez une scène, puis tentez-en une seconde.
   Désactivez séparément `individualPerceptionsEnabled` et
   `falseCompanionsEnabled` et rechargez la configuration.
4. **Attendu** : le plafond global et les deux interrupteurs sont respectés sans
   empêcher les manifestations Penumbra ordinaires.

## 111. Tirage naturel et build 0.19.0

1. Au stade narratif 2 ou plus, laissez `false_companion` être choisi naturellement
   par le Directeur avec une intensité élevée.
2. **Attendu** : l'événement respecte le cooldown, l'anti-répétition, les arcs et
   le plafond des manifestations ; aucune seconde boucle aléatoire indépendante
   ne crée de doublon.
3. Exécutez `gradlew.bat clean build --offline`, puis démarrez `runServer` et
   `runClient` avec le JDK 25.
4. **Attendu** : tests et validation des ressources passent, les trois ensembles
   de clés de langue sont identiques, `build/libs/penumbra-0.19.0.jar` annonce
   `0.19.0` et le serveur dédié atteint `Done` sans stack trace Penumbra.

## Critère de sortie 0.19.0

Les sections 108 à 111 passent : copie crédible mais inoffensive, visibilité et
effets réellement privés, vérification graduelle au regard/caméra, nettoyage sur
tous les départs du propriétaire, quotas et migration v5, intégration au Directeur
et JAR 0.19.0 chargeable sur client comme sur serveur dédié.

## 112. Variété de présentation des screamers (0.14.0)

1. `/penumbra jumpscare screamer` plusieurs fois de suite (attendre le cooldown
   client entre chaque).
2. **Attendu** : les mises en scène diffèrent (plein cadre, zoom lent, cadrage
   décentré, double coupe avec trou noir, flash discret plus court, « trop
   près » qui recule) et le cri n'a pas toujours la même hauteur. Avec
   `screamerVarietyEnabled=false`, retour au plein cadre fixe historique.
3. Déclencher une finale de contact (bond de l'Hôte). **Attendu** : jamais de
   version subliminale ni de trou noir pendant le contact.

## 113. Décote et refus sensibilisants (0.14.0)

1. Enchaîner 4 à 5 `mining_bait`/screamers non physiques en moins de 5 minutes
   (via événements ou commandes).
2. **Attendu** : l'impact décroît d'un visage à l'autre ; à partir du 3ᵉ, une
   partie des screamers est remplacée par un souffle discret (refus) ; jamais
   deux visages non physiques à moins de ~25 s d'écart. Les bonds physiques
   restent toujours accompagnés d'un visage.
3. `screamerHabituationEnabled=false` restaure le comportement 0.19.0.
4. Se déconnecter/reconnecter : l'historique d'habituation repart à neuf.

## 114. Statue et spasmes de l'Hôte (0.14.0)

1. `/penumbra spawnhost` puis observer l'Hôte en DORMANT/OBSERVING de loin.
2. **Attendu** : aucune respiration, aucun balancement, aucun spasme — seule la
   tête pivote pour suivre le joueur. Le cou, les mains et l'asymétrie des
   épaules sont visibles.
3. Le déclencher en poursuite. **Attendu** : la respiration reprend, les doigts
   se crispent, et le spasme survient à des instants imprévisibles (pas au même
   moment de chaque cycle).

## 115. Faux-Semblant : défauts et révélations (0.19.0)

1. `/penumbra fauxmob silent` puis chercher l'animal apparu derrière soi.
2. **Attendu** : animal vanilla normal en tout point, mais strictement muet.
3. `/penumbra fauxmob stare` : l'animal vous fixe en permanence, immobile,
   sans NoAI (vérifier `/data get entity` → `NoAI` absent).
4. `/penumbra fauxmob frozen` : figé face à un point sans intérêt.
5. Fixer un défectueux ~5 s : il rend le regard, puis se dissout (cendres +
   chuchotement). S'approcher à moins de 2,5 blocs, frapper, traire ou
   flasher : dissolution immédiate ; le coup ajoute un sting léger.
6. `/penumbra fauxmob perfect` : aucun défaut, aucune réaction au regard ;
   seuls contact, coup, interaction ou flash le trahissent.
7. La nuit en surface : `zombie` possible — il ne poursuit, n'attaque et ne
   brûle jamais pendant la scène ; l'approcher le dissout avant tout contact.
8. Laisser expirer (2 min) en lui tournant le dos : disparition sans témoin,
   sans effet ni tension. Ne jamais observer de disparition en le regardant.
9. Multijoueur : la créature est visible par tous ; seule la perception du
   propriétaire déclenche regard/dissolution ; sons individuels.
10. Crash serveur en pleine scène : l'animal restant redevient un animal
    vanilla normal (éventuellement muet — seul résidu possible, documenté).

## 116. Variantes sonores (0.19.0)

1. `/penumbra jumpscare screamer` plusieurs fois : le cri doit changer de
   timbre (3 fichiers) en plus de sa hauteur (jitter client).
2. Déclencher `breath` et des chuchotements : deux textures de souffle, trois
   débits de chuchotement.
3. Vérifier qu'aucun son positionné n'est en stéréo (l'atténuation spatiale
   exige du mono) : tous les fichiers générés sont mono.
4. Laisser tourner une nappe d'ambiance complète (8 s depuis la 0.21.0) : la boucle ne doit
   présenter aucun clic au raccord (fondu croisé intégré).
5. Niveaux à l'oreille : cri nettement au-dessus du chuchotement, nappes en
   retrait. Ajustement éventuel dans `tools/generate_sounds.py` (constantes
   `normalize`) puis régénération.

# Stabilisation 0.21.1

Les procédures ci-dessous décrivent les résultats attendus. Leur présence
ne signifie pas que chaque cas a été exécuté ; le rapport de livraison
précise les contrôles réellement passés.

## 117. Rituel, interruptions et victoire

1. Dans un monde d'essai, préparer un sanctuaire, une Caméra VHS et un Sceau
   de cendre. Passer au stade 6 avec `/penumbra story set 6`.
2. Faire apparaître un Absent avec `/penumbra absent test`, puis activer le
   pilier avec le sceau. Attendu : l'ancien acteur du joueur est retiré et
   le préambule peut aboutir à une copie dédiée sur un terrain valide.
3. Caméra en main, rester au sol, immobile, en regardant la copie. Au signal
   d'ouverture, faire un **clic gauche**. **C ne déclenche pas le flash** :
   c'est le viseur. Obtenir trois réussites séparées par les temps de repos.
4. Attendu : les réussites apparaissent une à une ; trois réussites donnent
   la victoire, le badge et l'accalmie. Une fenêtre manquée ne remet pas les
   réussites à zéro. Recharger le monde conserve la victoire.
5. Sur une tentative non gagnée, quitter le rayon de 40 blocs pendant le
   préambule, puis revenir. Attendu : les anciens messages/apparitions ne
   reprennent pas ; le même sanctuaire accepte une nouvelle tentative.
6. Refaire l'interruption par mort/réapparition, changement de dimension et
   déconnexion/reconnexion. Une nouvelle instance du joueur ne doit jamais
   reprendre les tâches de sa session précédente.
7. Avec deux joueurs, lancer une scène pour B et le rituel de A. Attendu :
   le nettoyage de A conserve les acteurs de B et ne valide pas ses flashs.
8. Garder le pilier central entre la position miroir théorique et le joueur.
   Attendu : la copie s'arrête avant de se masquer derrière le pilier. Se
   décaler peut lui permettre de reprendre un mouvement visible ; elle ne
   traverse ni les murs ni les sols pour retrouver son point miroir.

Un rythme réduit par `/tick rate 5` peut servir au diagnostic fonctionnel des
fenêtres. Rétablir `/tick rate 20` ensuite ; ce test ne valide pas la difficulté
ou le rythme ressenti à vitesse normale.

## 118. Preuve de l'Écouteur et dossier personnel

1. Dans un monde d'essai, faire apparaître L'Écouteur sur un terrain dégagé.
   Le cadrer au centre avec la Caméra VHS et faire un clic gauche.
2. Attendre 200 ticks de développement. Ouvrir l'album : le cliché doit
   reconnaître L'Écouteur et le dossier doit gagner une catégorie distincte.
3. Refaire un cliché identique : aucun second déblocage de la même catégorie.
4. En multijoueur, montrer la photo publique à B : son dossier ne doit pas
   hériter de la catégorie de A. Recharger la sauvegarde conserve les preuves
   personnelles, y compris après suppression volontaire du tirage.
5. Quitter pendant une capture puis reprendre une autre session : un ancien
   retour de capture ne doit ni envoyer ses pixels ni annuler une nouvelle photo.

## 119. Serveur dédié et arrêt propre

1. Avec Java 25 et les dépendances du projet, démarrer un serveur dédié dans
   un dossier d'essai distinct, lié seulement à `127.0.0.1`.
2. Attendre `Done`, puis envoyer `list`, `save-all flush`, et `stop`, en
   attendant la réponse de chaque commande.
3. Attendu : le mod charge sans erreur de classe client, la console répond,
   tous les mondes sont enregistrés et le processus se termine normalement.
4. Relancer avec le même dossier pour vérifier le rechargement de ce monde.

Un serveur vide ne valide pas une partie à plusieurs. Les tests de latence,
de confidentialité des perceptions et de synchronisation sonore demandent
encore des clients connectés.

# Confort et persistance 0.22.0 — validation partielle

Les procédures suivantes décrivent les résultats attendus ; seuls les
contrôles listés ici ont été observés. La compilation, les ressources et
**305 tests dans 59 suites** ont réussi, sans échec.

| Contrôle observé | Résultat |
|---|---|
| Client A sans OP, connecté au serveur dédié local | K ouvre le menu |
| Doux puis Annuler, réouverture | Valeurs Immersion conservées, aucun fichier personnel créé |
| Doux puis Appliquer, réouverture | Valeurs Doux conservées, fichier personnel correct |
| Configurations de base client A et serveur | Empreintes inchangées après ces manipulations |
| Interface française | Vérifiée et capturée en fenêtre 960×540 |
| Passage au stade 6, badge initialement absent | Aucun octroi, sans révocation préalable |
| Rituel normal déclenché par un vrai clic | Accalmie à 47 668 ticks après sauvegarde forcée, puis 47 403 après arrêt propre ; badge de confrontation toujours absent |
| Redémarrage du serveur et du client A, reconnexion sans OP | Doux conservé ; horloge 10 104→10 576, accalmie 47 403→46 931 : exactement 472 ticks consommés, sans réarmement ; badge toujours absent |

Le serveur a signalé un retard de 2 056 ms (41 ticks) à la connexion ; ce
contrôle ne démontre pas un maintien constant à 20 TPS. Le client B a échoué
deux fois avant connexion, y compris après une relance contrôlée, avec une
violation d'accès native dans GLFW (`glfw.dll+0x10fa1`, `pollEvents` ; dernier
rapport `hs_err_pid20052.log`). Aucune frame Penumbra n'apparaît dans la trace
observée ; l'origine reste indéterminée. Aucun second joueur n'a rejoint :
**l'essai simultané à deux clients n'est pas validé**.
L'interface anglaise, les petites dimensions GUI, le duel complet en 0.22.0,
la survie longue et la latence Internet ne sont pas déclarés validés.

## 120. Menu personnel, application et limites

1. Sans permission opérateur, ouvrir **K** en jeu. Vérifier les huit réglages,
   les infobulles, les trois préréglages, puis le clavier et la souris en
   français et en anglais à 320×180 GUI et à une taille supérieure.
2. Réassigner K dans les contrôles. Le chat et les écrans ouverts doivent
   consommer leurs saisies sans ouvrir le menu, même après leur fermeture.
3. Modifier plusieurs valeurs et choisir Doux, puis Annuler ou Échap.
   Attendu : aucun changement actif ni fichier écrit. Refaire puis Appliquer :
   effet immédiat et valeurs conservées à la réouverture et après redémarrage.
   `penumbra-client.json` change ; `penumbra.json` reste inchangé.
4. Tester un fichier client incomplet, invalide et une écriture impossible.
   Attendu : défauts neutres pour les clés absentes, erreur lisible pour les
   échecs, pas de publication des changements si la sauvegarde échoue.
5. Couper un effet dans la configuration de base : aucun réglage local ni
   préréglage Immersion ne doit le réactiver. Activer mouvement réduit avec
   grain/rémanences/lumière variable autorisés individuellement : ces effets
   restent coupés, mais leurs choix restent mémorisés à la désactivation du mode.
6. Vérifier secousses, FOV et respiration du zoom, animations VHS et flashs.
   Photos et contre-mesures du duel restent utilisables avec les flashs bloqués.
   Masquer les visages ne masque pas les créatures ni ne change leurs dégâts.
7. Tester chaque volume à 0 %, 50 % et 100 %, y compris une nappe déjà lancée.
   Attendu : aucune amplification de base ; ambiances limitées aux nappes/cœur,
   sons soudains limités aux sons de sursaut couverts. Pas, indices et sons
   Minecraft restent indépendants. Aucun son de démonstration n'est joué par le menu.
8. Vérifier la vraie pause solo, puis le même menu sur un serveur à deux
   clients : le serveur continue, les choix de A ne modifient pas ceux de B
   ni le comportement des créatures ou la fréquence des événements.

## 121. Accalmie persistante et horloge commune

1. Obtenir réellement l'accalmie d'un rituel ou d'une victoire sur un monde
   d'essai. Attendu : durée initiale de 48 000 ticks, puis consommation selon
   les ticks du monde principal, pas le temps mural.
2. Quitter puis rejoindre avant expiration. Le temps restant doit être
   conservé, sans repartir à 48 000 ; aucun badge ou victoire n'est réattribué.
3. Arrêter le serveur, attendre, puis relancer : le temps hors ligne avec le
   serveur arrêté n'est pas décompté. Sur un serveur qui continue à avancer
   pendant l'absence du joueur, les ticks écoulés sont au contraire décomptés.
4. Changer de dimension : la même horloge est utilisée, sans prolongation ni
   expiration artificielle. Vérifier aussi l'expiration normale et la reprise
   des événements. Les sons vanilla ne sont pas censés être supprimés.
5. Sur une copie d'essai, `/penumbra tension 90` doit annuler le calme restant,
   y compris après reconnexion. Réserver cette commande à la fin du contrôle.

## 122. Badge de victoire et anciennes sauvegardes

1. Sur un joueur sans badge `confrontation`, atteindre le stade 6 : aucun
   octroi. Interrompre un préambule ou un duel : toujours aucun octroi.
2. Gagner le duel avec les badges activés : attribution du badge, marqueur de
   victoire et compteur enregistrés. Recharger conserve ces données sans
   seconde attribution. Refaire avec les badges coupés sur un autre joueur.
3. Charger une sauvegarde où le badge a déjà été obtenu selon l'ancienne règle :
   il reste acquis. Ce cas ne valide pas l'attribution initiale de la nouvelle règle.

# Parcours de l'histoire 0.23.0 — validation ciblée

La compilation finale, la validation des ressources et les tests ont réussi :
**347 tests dans 68 suites, sans échec**. Les observations en jeu confirmées
le 22 octobre 2026 se limitent aux cas suivants ; les sections 123 à 128
restent un protocole, et ne sont pas déclarées intégralement exécutées.

| Contrôle observé | Résultat |
|---|---|
| Arrivée en survie sans OP | Un Relevé remis ; suivi personnel ouvert aux étapes 0 puis 5 |
| Rituel au stade 5, sans caméra | Refus lisible, sceau conservé |
| Un vrai clic avec caméra dans l'inventaire et une seule offrande | Préambule puis reflet et indications du duel ; stade 6, `sanctum_ritual=1`, `structure_ash_sanctum=1` ; badge du rituel présent et badge final absent (capture 09:57:40) |
| Abandon du duel par éloignement | Acteur disparu, aucune victoire finale accordée |
| Coffre original du sanctuaire préparé vide, table de butin retirée, clic à main vide | Coffre ouvert toujours vide et page « L'immobilité » accordée, sans régénération du butin (message 09:59:29, capture 09:59:37) |
| Sauvegarde forcée, arrêt propre, redémarrage et reconnexion | Lecture NBT après sauvegarde : stade 6, rituel 1, `method_stillness`, remise du carnet et aucune victoire ; après reconnexion : un carnet, caméra, aucun sceau, coffre vide, guide « La dernière rencontre » et 2/14 notes (page et observation de créature) |

La sauvegarde a été forcée à 10:00:08, l'arrêt propre s'est terminé avec le code
0 à 10:00:39, le redémarrage a eu lieu à 10:01:16 et la reconnexion à 10:01:42.
Le guide de reprise a été capturé à 10:02:47. Ces contrôles avec un seul
client ne valident ni deux joueurs
simultanés, ni le déroulement naturel de tous les chapitres, ni un duel complet.
Les inventaires pleins, les autres états du guide et les autres cas ci-dessous
doivent encore être distingués des preuves automatisées disponibles.

## 123. Remise personnelle du Relevé et inventaire plein

1. Connecter un joueur encore inconnu de ce monde, avec `journalEnabled=true`,
   sans permission opérateur. Attendu : un Relevé dans l'inventaire, un message
   expliquant clic droit, enquête personnelle, refabrication et K ; aucun écran
   ne s'ouvre automatiquement. Répéter en survie et en créatif.
2. Préparer un autre joueur d'essai dont l'inventaire est plein avant sa première
   remise, sans Relevé. Attendu : message demandant une place, aucun livre jeté
   au sol, aucun objet remplacé. Libérer une place sans quitter le serveur :
   le Relevé doit arriver au prochain contrôle, dans environ 40 ticks.
3. Répéter le cas plein en créatif : la remise ne doit pas être considérée
   comme acquise tant que le livre n'est pas réellement dans l'inventaire.
   Ne pas confondre un retour positif d'insertion avec une livraison visible.
4. Quitter alors que la remise est encore en attente, puis rejoindre et libérer
   une place. Le droit au livre doit être conservé. Après une remise réussie,
   plusieurs reconnexions et un redémarrage du serveur ne donnent ni double
   livre ni nouveau message de bienvenue. Tester aussi un joueur qui possédait
   déjà un Relevé avant la mise à jour : son exemplaire suffit.
5. Après livraison, perdre volontairement le livre dans ce monde d'essai :
   une reconnexion ne doit pas le remplacer. Combiner un livre et une poche
   d'encre pour en refaire un ; vérifier la recette dans le livre de recettes
   après obtention d'un livre. Les découvertes personnelles restent présentes.
6. Sur une configuration sans journal, aucune remise ni invitation à libérer
   une place. Réactiver le journal puis rejoindre avec un joueur qui ne l'a
   jamais reçu : sa première remise doit rester possible.

## 124. Suivi personnel, étapes et fin de l'histoire

1. Ouvrir le Relevé d'un nouveau joueur : les premiers feuillets sont
   « Votre enquête », avant le frontispice de Warrick. Ils indiquent refuge,
   repos, exploration et boussole, sans annoncer les objectifs des étapes
   suivantes. Vérifier français et anglais, petite et grande interface,
   pagination au clavier/souris et retour au début avec Début.
2. Dans le monde d'essai, utiliser `/penumbra story set <0–6>` pour contrôler
   chaque état d'affichage. Fermer puis rouvrir le livre après chaque changement :
   il reçoit un nouvel instantané du serveur, sans prétendre évoluer en direct.

| Étape du joueur | Indication attendue |
|---|---|
| 0 | Refuge avec lit, pauses nocturnes, anomalie ou poste d'observation |
| 1 | Continuer ses activités et repérer plusieurs manifestations d'imitation |
| 2 | Deux pauses immobiles distinctes d'environ 10 s au même endroit, nuit/pénombre ; repos sur 2 et visiteur au refuge distingués |
| 3 | Maison décalée déjà visitée par d'autres toujours utile ; copie personnelle comme autre voie si autorisée |
| 4 | Rencontres avec l'Hôte et observations personnelles sur 4 |
| 5 | Caméra VHS et Sceau de cendre au sanctuaire ; distinction avec le Sceau de bannissement |
| 6, sans victoire | Reprise possible, caméra en main principale, immobilité/cadrage, clic gauche à l'ouverture, trois brèches |
| Victoire acquise | Épilogue, poursuite libre du jeu et accalmie temporaire ; aucun retour artificiel au début |

3. Pour l'étape 2, réaliser les pauses et les repos plutôt que seulement forcer
   le stade. Vérifier séparément les compteurs de lits et la présence de l'Hôte
   au refuge. Pour l'étape 4, comparer les observations affichées aux données
   serveur. Le suivi ne doit pas inventer les découvertes d'un autre joueur.
4. Régler `storyEnabled=false` : message explicite d'histoire désactivée,
   sans objectif impossible. Avec `imperfectCopiesEnabled=false`, l'étape 3
   doit orienter vers la Maison décalée sans promettre une copie personnelle.
5. Le simple passage au stade 6 ne doit jamais afficher la victoire. Gagner
   réellement le duel, ou charger une sauvegarde d'essai d'une victoire déjà
   vérifiée : l'épilogue reste affiché après rechargement. Il explique que
   l'ambiance peut revenir après environ 40 minutes au rythme du monde.
6. Prêter le même exemplaire du Relevé à un autre joueur : ses propres pages,
   compteurs et objectifs apparaissent. Reprendre le livre ne modifie aucun
   des deux historiques. Les notes de caméra n'annoncent ni pellicule à acheter
   ni charges limitées : clic gauche, puis environ deux secondes entre prises.

## 125. Pages personnelles dans les coffres déjà pillés

1. Avec A, vider les coffres naturels d'un poste d'observation, d'une Maison
   décalée, d'un Sanctuaire et d'un puits ; conserver leurs positions exactes.
   Tester les variantes existantes, notamment les deux profondeurs du puits.
2. Avec B, arrivé plus tard et sans ces pages, faire un vrai clic droit sur
   chacun des coffres restés en place. Attendu : message de page retrouvée et
   page personnelle lisible, même si le coffre est vide ; aucun remplissage du
   coffre, aucun objet retiré à A et aucun changement de son Relevé.
3. Réouvrir avec B : pas de seconde attribution ni progression supplémentaire.
   Vérifier aussi B sans Relevé en main, puis avec un exemplaire refabriqué :
   la connaissance appartient au joueur et reste consultable ensuite.
4. Ouvrir un coffre ordinaire proche, un coffre déplacé ou un coffre aux mêmes
   coordonnées dans une autre dimension : aucune page ne doit être accordée.
   Une structure doit encore être connue du registre et le coffre à sa place
   exacte. La destruction du coffre n'entraîne pas de récupération à distance.
5. La page physique du butin continue de fonctionner. Si cette page est déjà
   connue du lecteur, son utilisation ne consomme pas un duplicata. Recharger
   le serveur conserve les quatre connaissances de B sans modifier celles de A.

## 126. Maison décalée, coopération et copie personnelle

1. Avec deux joueurs à l'étape 3, faire découvrir la même Maison décalée à A
   puis à B. Chaque découverte personnelle doit permettre sa progression ;
   le passage de A ne valide ni n'épuise celui de B. Refaire avec les copies
   personnelles désactivées et avec une maison dont le coffre est déjà vide.
2. Faire découvrir la maison à B avant l'étape 3. Une fois ce stade atteint,
   la découverte déjà enregistrée reste valable lors de l'évaluation suivante ;
   B n'a pas à trouver une deuxième maison pour satisfaire cette condition.
3. Avec les copies activées, vérifier également la voie de la copie personnelle.
   Les conditions de terrain et de protection des constructions restent actives :
   l'alternative Maison décalée ne doit pas les contourner.
4. Contrôler le calendrier de copie sur un joueur éligible : première tentative
   après environ deux minutes, nouvelle tentative après environ une minute en
   cas de refus, délai de trois jours conservé après une construction réussie.
   Déconnecter/reconnecter entre les tentatives ne doit pas repousser sans fin
   la première possibilité. Noter les ticks et la condition d'éligibilité.

## 127. Premier rituel final avec une seule offrande

1. En survie dans un monde d'essai, placer le joueur au stade 5 et lui donner
   exactement un Sceau de cendre, sans Caméra VHS dans son inventaire. Cliquer
   le pilier d'un sanctuaire : message demandant la caméra, sceau conservé,
   aucun préambule lancé ni autel consommé pour ce joueur.
2. Ajouter une Caméra VHS à l'inventaire et recommencer. Attendu : une seule
   offrande est consommée ; le même préambule valide le stade 6 puis ouvre
   directement le duel. Aucun deuxième sceau ni second clic rituel ne doit être
   nécessaire, et l'accalmie normale ne doit pas annoncer une fausse fin.
3. Prendre la caméra en main principale et tenter les trois ouvertures. Réserver
   le marqueur de victoire et le badge de confrontation à une victoire réelle.
   Le badge propre au rituel ne doit pas être confondu avec celui du duel.
4. Tester un sanctuaire utilisé plus tôt dans l'histoire par ce même joueur :
   au stade 5, une nouvelle offrande avec caméra peut déclencher la finale.
   Un rituel ancien suivi d'un événement sans lien avec l'autel ne doit pas,
   à lui seul, faire passer du stade 5 au stade 6.
5. Interrompre avant la victoire, puis revenir avec une nouvelle offrande :
   reprise possible au même autel. Tester également un placement impossible
   de la copie : message d'échec, aucun faux succès ni accalmie de récompense,
   et possibilité de retenter lorsque le terrain est valide.
6. En créatif, refaire les contrôles fonctionnels en notant que le sceau n'est
   pas consommé par conception. Ce cas ne valide pas le coût en survie ; les
   contrôles de rythme à faible TPS ne valident pas l'équilibrage à 20 TPS.

## 128. Historique au-delà des anciennes limites de sauvegarde

1. Exécuter les tests ciblés `JournalKnowledgeTest` : 300 identités distinctes
   gardent pages et observations après trois cycles NBT ; les anciens formats
   chargent toutes les identités, ignorent les identifiants inconnus et retirent
   les doublons sans effacer les premières entrées.
2. Exécuter `PenumbraPersistentStateTest` : plus de 256 marqueurs, dont victoire
   et remise du Relevé pour 400 identités, survivent aux rechargements. Vérifier
   aussi une ancienne liste dont le marqueur de victoire se trouve après la
   256e entrée ; ne pas remplacer cette vérification par une liste courte.
3. Sur une copie de sauvegarde, contrôler qu'un compteur personnel de victoire
   réellement enregistré peut restaurer un ancien marqueur perdu. Un simple
   stade 6, un autre joueur vainqueur ou un compteur nul ne doit pas suffire.
4. Compléter par démarrage, sauvegarde et arrêt propre d'un serveur utilisant
   un jeu de données d'essai volumineux, puis comparer les données rechargées.
   Les tests de codec ne constituent pas à eux seuls un essai serveur de charge.
   Ce contrôle ne supprime pas les limites distinctes du registre des structures.
