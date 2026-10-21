# Feuille de route de Penumbra

## État actuel — 0.22.0, livraison locale

La version publique reste 0.19.0. Les versions 0.20.0, 0.21.0 et 0.21.1
sont préparées localement. La 0.22.0 ajoute le confort personnel et corrige la
persistance de l'accalmie. Sa compilation, la validation des ressources et
305 tests dans 59 suites ont réussi. Les essais en jeu restent limités aux
cas ci-dessous ; ils ne valident pas une longue survie, deux clients simultanés
ou la latence Internet.

| Version | Évolution locale |
|---|---|
| 0.19.0 | Première préversion publique et sélection terrestre du Bûcher |
| 0.20.0 | Respiration du directeur, contre-jeu de l'Hôte et de l'Écouteur, boussole et lecture du Relevé |
| 0.21.0 | Dossier d'enquête, nappes sonores suivies et variées, duel en trois étapes, éclairage regroupé |
| 0.21.1 | Isolation et reprise du rituel, reflet gardé visible pendant son déplacement, confirmation durable des fragments, captures liées à la bonne connexion |
| 0.22.0 | Menu personnel K, huit réglages et trois préréglages locaux ; accalmie sauvegardée ; badge de confrontation réservé à la victoire — validation ciblée, limites documentées |

## 0.22.0 — implémentation et validation partielle

- Écran de confort sans permission opérateur, brouillon Appliquer/Annuler,
  préréglages et fichier distinct `penumbra-client.json`. Les limites de la
  configuration de base restent prioritaires ; aucune règle serveur ne change.
- Réductions visuelles applicables immédiatement et volumes séparés des sons
  soudains et des nappes/cœur. Le mouvement réduit coupe les effets concernés
  sans effacer les choix individuels.
- Accalmie de 48 000 ticks sauvegardée, avec une horloge commune aux dimensions :
  elle continue hors présence du joueur si le monde avance, mais pas pendant
  l'arrêt du serveur. Une tension forcée à 90 ou plus l'annule pour le diagnostic.
- Badge de confrontation accordé à la victoire, sans retirer les anciennes
  attributions. Les sections 120 à 122 du protocole couvrent les contrôles attendus.

Déjà vérifié : K sans permission opérateur sur un client connecté au serveur
dédié local ; Annuler Doux conserve Immersion sans créer de fichier ; Appliquer
Doux écrit les préférences et les conserve à la réouverture. Les configurations
de base du client et du serveur gardent leurs empreintes. Le menu français a
été capturé en fenêtre 960×540. Le stade 6 n'accorde pas le badge à un joueur
qui ne l'avait pas. Un vrai rituel normal a accordé une accalmie sauvegardée.
Après arrêt puis redémarrage du serveur et du client A, Doux reste actif ;
l'accalmie passe de 47 403 à 46 931 ticks pour 472 ticks de monde écoulés,
sans réarmement ni attribution du badge.

## Étapes accomplies pour la 0.21.1

- Le préambule et le duel suspendent les autres scènes du participant ; le
  nettoyage reste limité à ses propres apparitions. Les tentatives sont liées
  à la session, avec annulation définitive des effets différés après abandon.
- Le cycle photographie → développement → album → dossier a été vérifié avec
  L'Écouteur à 20 ticks par seconde. Le dossier est passé d'une à deux preuves
  connues et son rechargement a été contrôlé.
- Un éloignement pendant le préambule a interrompu la tentative ; le même
  autel a accepté une nouvelle tentative au retour.
- Le duel a été joué jusqu'à la victoire après le flash final. La sauvegarde
  contient le marqueur de victoire et le compteur est passé de zéro à un.
  Après rechargement, nouvelle sauvegarde et sortie avec la compilation
  finale, le marqueur reste actif et le compteur reste à un.
  Cet essai en créatif, à 5 puis 1 tick par seconde, valide le chemin
  fonctionnel, pas son équilibrage à vitesse normale. Le badge était déjà
  présent : cet essai ne prouve pas sa première attribution.
- Trois essais de serveur dédié vide ont couvert démarrage, sauvegarde et
  arrêt propre, dont un rechargement du même monde. Le dernier inclut la
  correction de visibilité du reflet ; aucun client n'y était connecté.
- La compilation finale, les tests et la validation des ressources ont
  réussi avec le message de confirmation des fragments dans le chat.
  Cet ajout a été relu et compilé, sans rejouer un nouveau duel après son ajout.
- La limite de session de l'accalmie constatée alors est corrigée dans la
  0.22.0 ; sa nouvelle persistance a été vérifiée après redémarrage du serveur.

## Prochaines vérifications

- Terminer les contrôles 0.22.0 : menu anglais et petites dimensions GUI,
  effets immédiats et première attribution du badge.
  Diagnostiquer le crash natif GLFW du client B, reproduit avant connexion
  lors de deux lancements, puis vérifier l'isolation entre deux clients
  simultanés. Ce contrôle n'a pas pu être réalisé.
- Rejouer le duel en survie à 20 ticks par seconde, vérifier l'attribution
  initiale du badge et les autres causes d'interruption en conditions de jeu.
- Éprouver les captures lors d'une reconnexion et les scènes de plusieurs
  joueurs simultanés, avec latence et différentes combinaisons de mods.

Ensuite : parties de survie longues, essais avec plusieurs clients et
évaluation du confort directement en jeu. Les nouveaux objectifs de terrain
viendront après vérification de la progression actuelle.

## Historique jusqu'à 0.18.0

Les incréments historiques sont intégrés aux sources :

| Version | Livré |
|---|---|
| 0.1 | Directeur d'horreur, tension persistante, Hôte, 8 événements, réseau et configuration |
| 0.2 | Jumelles, poursuites, leurres, jumpscares retardés et mémoire comportementale |
| 0.3 | Progression en 7 stades, 4 structures, objets narratifs, nuits/souterrains et copies |
| 0.4 | Observateur, badges et protections multijoueur |
| 0.5 | Couche ambiante, Originel, finale du rituel et refonte visuelle/sonore |
| 0.6 | Événements personnels, journal dynamique, screamers, Contrefait et anti-habituation |
| 0.6.1 | Caméra VHS, marteau de mineur 4×4 et diffusion spatiale des sons en multijoueur |
| 0.6.2 | Six screamers, apparitions de grotte fiables et réactions animées au regard |
| 0.6.3 | Quatre marteaux, Originel caméra/totem et bonds réellement offensifs |
| 0.6.4 | Photos réelles, développement progressif, visionneuse, flash partagé et cadres muraux persistants |
| 0.7.0 | Projectionniste photographique, Rampant des grottes et Absent aux faux noms |
| 0.8.0 | Escalade réelle du Rampant, Sceau de bannissement, Cavaleuse inoffensive et neuf screamers |
| 0.8.1 | Coordination locale des scènes, ciblage strict, commandes multijoueur et album mondial étendu |
| 0.9.0 | Album gérable, scènes coopératives asymétriques, animations 3D enrichies et douze screamers |
| 0.10.0 | Preuves photographiques serveur, développement secret, dossier personnel et favoris |
| 0.11.0 | L'Absent copie le skin, l'équipement et la démarche ; révélation au flash et double exposition |
| 0.12.0 | Arcs adaptatifs persistants, contamination des refuges, scènes liées et déductions photographiques |
| 0.13.0 | Perceptions individuelles et faux compagnon entièrement privé, vérifiable au regard et à la caméra |
| 0.14.0 | Anti-habituation des screamers, remodelage de l'Hôte, identité visuelle unifiée |
| 0.15.0 | Le Faux-Semblant : créature vanilla possédée, au défaut minuscule — ou parfaite |
| 0.16.0 | Les Échos, la Maison d'en face, la Confrontation, le disque « Mémoire » |
| 0.17.0 | L'Écouteur (traque au bruit, contre-jeu réel), couche de perception, profils d'accessibilité |
| 0.18.0 | Le Bûcher, Le Relevé et ses quatre pages arrachées : le bestiaire devient une enquête |

La 0.13.0 sépare pour la première fois une manifestation physique de sa perception
réseau : le faux compagnon copie une identité crédible mais n'existe que pour son
propriétaire. Regard soutenu, zoom et flash permettent de vérifier puis dissiper
l'anomalie sans attaque ni fausse preuve. Le Directeur conserve l'unique horloge de
tirage et applique ses arcs, cooldowns et quotas au nouveau contenu.

## Priorité immédiate — stabilisation de la 0.18.0

- Vérifier sur plusieurs graines que le site du Bûcher tombe sur un sol valide,
  et mesurer la proportion de graines où il tombe en mer.
- Confirmer que le panache reste visible à 480 blocs sur les réglages de
  particules « toutes » et « diminuées », et invisible au-delà.
- Campagne de lecture : ouvrir le carnet à zéro page, à quatre pages, et
  complet ; vérifier qu'aucune ligne ne déborde du feuillet dans les deux
  langues et à toutes les échelles d'interface.
- Vérifier par capture réseau qu'un client ne reçoit jamais l'identifiant d'une
  page qu'il n'a pas gagnée.
- Relire les quinze entrées à voix haute : le carnet doit sonner comme une seule
  personne, sur quatre ans, qui se fatigue.

## Priorité immédiate — stabilisation de la 0.17.0

- Campagne dédiée à L'Écouteur : vérifier sur de grands échantillons que
  l'immobilité totale reste inaudible à toutes les distances, que le
  verrouillage se casse bien après 34 ticks de silence, et qu'aucune scène ne
  survit à un rechargement de monde.
- Mesurer le coût du sondage de bruit avec 1, 4 et 20 joueurs : la boucle est
  amortie sur trois ticks et bornée à 34 blocs, mais le chiffre reste à relever.
- Vérifier que le leurre `sound_bait` détourne réellement une scène en cours et
  que la créature ne traverse jamais un mur pour l'atteindre.
- Relire la couche de perception avec un profil photosensible : confirmer
  qu'aucun effet ne descend sous 3 Hz de variation perceptible.
- Remplacer les six sons procéduraux 0.17.0 par un sound design final, en
  conservant les durées documentées dans `docs/SOUNDS.md`.
- Faire relire `screamer_listener.png` à côté des quatorze visages IA : la
  banque doit rester homogène en contraste et en grain.

## Priorité immédiate — stabilisation de la 0.13.0 (reporté)

- Smoke tests client/serveur reproductibles et couverture JUnit des invariants
  de configuration, persistance, sélection d'événements, paquets et tickets de
  capture à usage unique.
- Validation automatique des JSON, langues, sons, modèles, recettes, butins et
  avancements dans `check`.
- Campagne photographique avec les huit catégories, plusieurs monstres dans un
  même cadre, obstacles, limite de 64 blocs et scènes privées multijoueur.
- Vérification sur sauvegardes 0.6.4–0.9.0 : anciennes photos, Projectionniste,
  favoris absents et création/réconciliation du dossier sans perte de pixels.
- Campagne en grottes variées pour les transitions sol → paroi → plafond du
  Rampant et sa nouvelle finale visuelle, sans téléportation ni bloc modifié.
- Vérification statistique de la Cavaleuse sur de grands échantillons (70 % de
  traversées, 30 % de poursuites de 3 à 5 s) et du filtre du Sceau de
  bannissement à 16 blocs.
- Correction du warning d'API Fabric dépréciée côté renderer.
- Campagne de tests Windows/Linux, solo/LAN/serveur dédié et sauvegardes issues
  des versions 0.1 à 0.9.0, notamment les anciens albums photo et `ash_sigil`.
- Mesure du budget CPU du directeur et des scans avec 1, 4 et 20 joueurs.
- Campagne dédiée au faux compagnon avec skins larges/fins, couches externes,
  armures, compagnon déconnecté, repli solo et flash derrière des vitres.
- Vérification par capture réseau qu'un témoin proche ne reçoit ni paquet d'entité,
  ni son, ni particule ou animation de la perception d'un autre joueur.

## Accessibilité et configuration

- ~~Profils prédéfinis « doux », « standard » et « cauchemar », sans retirer
  les réglages fins.~~ **Livré en 0.17.0** (`/penumbra profile`).
- ~~Mode mouvement réduit et contrôle séparé de la brusquerie visuelle.~~
  **Partiellement livré en 0.17.0** : `perceptionGrainEnabled`,
  `peripheralAfterimagesEnabled` et `perceptionIntensity` sont indépendants du
  voile et de la dérive de FOV.
- Interface locale de confort : livrée en 0.22.0, avec validation ciblée et
  limites documentées dans les problèmes connus.
  L'interface complète des règles serveur et des autres options reste à étudier.
- Mode mouvement réduit, variante sans visage photoréaliste, contrôle séparé
  des sons brusques et rappel de contenu au premier lancement.
- Sous-titres testés en contexte, pas seulement présents dans les fichiers de
  langue.

## Après 0.13.0 — multijoueur individualisé

- Étendre prudemment le filtre de perception validé par le faux compagnon à une
  variante de l'Hôte ou de l'Observateur, avec un budget distinct et des tests de
  fuite réseau avant toute généralisation.
- Évaluer des scènes où deux témoins reçoivent des versions contradictoires d'un
  même événement sans créer deux entités physiques concurrentes.
- Clarifier tension individuelle, tension partagée et contenu coopératif via
  des options distinctes.
- Étudier des preuves coopératives où deux angles complémentaires sont requis,
  sans fusionner les dossiers personnels ni révéler une catégorie à distance.

## 0.13.x — direction et contenu final

- Remplacer les sons procéduraux et sprites encore provisoires par un sound
  design et un pixel-art finaux, avec provenance documentée.
- Compléter les animations déjà synchronisées par un sound design final et des
  transitions encore plus fines lors des changements de surface.
- Équilibrer les courbes de tension, l'anti-habituation et les temps morts sur
  des télémétries anonymes de sessions de test, jamais sur des données envoyées
  automatiquement.
- Étendre les indices du dossier vers des objectifs de terrain uniquement après
  validation de la lisibilité des quatre paliers actuels.

## 1.0 — publication

- Zéro crash connu, protocole complet validé et migration de sauvegarde testée.
- Documentation FR/EN, changelog, licences/provenance des assets et sommes de
  contrôle des JAR.
- Publication sur les plateformes de mods seulement après validation du couple
  exact Minecraft/Fabric annoncé dans les métadonnées.
