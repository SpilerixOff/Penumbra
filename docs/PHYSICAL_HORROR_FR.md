# Penumbra 0.25.0 — L'horreur dans le monde

La 0.24.0 prépare des sursauts produits par les créatures et leur comportement.
Le visage plaqué à l'écran, les rémanences de visages et le noir de screamer
ont été retirés. Les anciens réglages ne peuvent pas les réactiver.
Les descriptions suivantes portent sur le code implémenté ; les résultats
d'essais sont suivis dans [les problèmes connus](KNOWN_ISSUES.md).

## Ce que le joueur rencontre

Depuis la 0.25.0, l'Hôte prolonge un contact réussi par une prise physique :
après s'être ramassé, il bondit en arc, plonge avec ses deux mains tendues,
saisit le joueur, le soulève brièvement puis le projette devant lui. La levée
et la projection déplacent réellement le joueur ; tourner la caméra reste
possible. La distance parcourue dépend du terrain et des collisions.
Une esquive pendant la préparation évite la prise ; un flash réussi la rompt
avant le lancer. Un obstacle ou un plafond insuffisant interrompt le mouvement.
Ces étapes concernent la charge de regard de l'Hôte, pas toutes les créatures.

L'Hôte et l'Observateur peuvent s'immobiliser, incliner la tête et préparer leurs
bras avant de bondir. La charge vise la position repérée au début : partir sur
le côté ou gagner un couvert peut éviter le contact. Le corps ne doit pas
traverser un mur. Après sa tentative, la créature recule pendant environ
1,8 seconde avant de se dissiper. Si un obstacle bloque le recul, elle attend
sa disparition sans franchir cet obstacle.

Le Rampant guette sur une paroi ou un plafond de grotte. Une proximité lointaine
ne suffit plus à le faire tomber. Il attend un passage dans sa zone, avertit
brièvement, puis se décroche si la cible y reste. Quitter la zone pendant cet
avertissement annule l'attaque. La lumière et la caméra restent des moyens de
le repousser. Sa poursuite conserve une dernière position réellement observée,
au lieu de suivre les coordonnées d'un joueur dissimulé.

L'Écouteur poursuit les bruits. Un obstacle les atténue et peut provoquer une
recherche ; il ne déclenche pas à lui seul une charge sans visibilité. Le
silence, le couvert et la durée limitée de la recherche restent utiles.

## Des scènes liées aux habitudes

Les anciens déclencheurs de coffre, d'inventaire, de retournement, de torche,
de retour au refuge et de long calme peuvent préparer une présence physique.
Selon la scène, l'Hôte observe, recule, charge, poursuit brièvement ou se retire
avant de revenir depuis une autre position. L'issue n'est pas systématiquement
une attaque.

Ces scènes sont personnelles : l'Hôte mis en scène appartient au joueur ciblé
et n'est pas montré à ses voisins. Les créatures ordinaires restent partagées.
La présence d'autres joueurs, un emplacement insuffisant ou un terrain dangereux
peuvent empêcher une scène. Un retour au refuge cherche un vrai encadrement ou
un angle disponible, sans modifier la maison pour le fabriquer.

Une scène de lecture attend la fermeture d'un coffre, d'un inventaire, du Relevé
ou d'un lecteur de photos. La consultation doit durer environ 1,5 à 30 secondes.
Un menu de réglages, une lecture très longue ou un signal ancien ne réserve pas
un sursaut qui surgirait plusieurs minutes plus tard. La probabilité, les délais
de respiration et l'état du joueur continuent de s'appliquer.

Une courte suspension des nappes et du cœur de Penumbra prépare certains moments.
Les pas, les cris positionnels et les autres sons du monde restent audibles.
Cette suspension ne coupe ni les haut-parleurs ni le volume général de Minecraft.

## Utiliser la caméra pour se défendre

Avec la Caméra VHS en main principale, un clic gauche produit le flash habituel.
Pour repousser un Hôte, un Observateur ou un Rampant :

- la créature doit appartenir au photographe ;
- une partie visible de son corps doit se trouver à 12 blocs au maximum ;
- cette partie doit être à moins de 50° de l'axe du viseur ;
- aucun bloc ne doit intercepter le rayon physique vers cette partie.

Le zoom ne prolonge pas cette portée. Une tête masquée ne suffit pas à annuler
le flash si le torse ou une autre partie échantillonnée du corps reste visible.
Le verre et les autres blocs qui arrêtent le rayon physique font obstacle,
même si une photographie peut montrer le sujet derrière eux.

Un flash réussi interrompt la préparation, le bond, la saisie ou la poursuite concernée,
puis laisse voir le retrait. Il ne supprime pas les effets des attaques déjà
reçues. La recharge entre les clichés reste nécessaire. Les règles propres à
L'Absent et au reflet du duel final restent celles de leurs rencontres : la
caméra ne devient pas un bouton de victoire immédiate.

## Régler son expérience

**K** ouvre six réglages personnels : mouvement réduit, blocage des flashs,
grain, scintillement des lumières, volume des sons soudains et volume des
ambiances. **Appliquer** les enregistre ; **Annuler** conserve les précédents.
Les anciens boutons Visages et Rémanences n'ont plus d'effet à piloter.

Ces choix réduisent la présentation locale. Ils ne changent ni les dégâts,
ni la fréquence décidée par le serveur, ni la progression de l'histoire.
Les sons soudains restent soumis à leur volume ; la courte secousse de contact
respecte le mouvement réduit. L'absence d'images de screamer n'efface pas les
créatures, les surprises sonores ou les autres effets visuels du mod.

## État de validation

Le [protocole 0.24.0](TEST_PROTOCOL.md#129-0240--absence-dimages-et-réglages-personnels)
prévoit des essais de contact, d'esquive, de couvert, de flash, de menus et de
multijoueur. Une compilation réussie ne démontre ni l'efficacité d'un sursaut
sur tous les joueurs, ni la qualité de son animation en conditions réelles.
Les résultats historiques des versions précédentes restent séparés.
