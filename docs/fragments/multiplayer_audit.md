# Audit multijoueur — Penumbra 0.9.0

Périmètre : coordination des scènes automatiques, scènes asymétriques de duo,
équité entre joueurs,
ciblage des créatures, diffusion des sons, nettoyage des états transitoires et
quotas de l'album photo. Les commandes opérateur restent des outils de forçage
et sont explicitement séparées du rythme naturel.

## 1. Coordination des scènes automatiques

| Règle | Comportement 0.9.0 |
|---|---|
| Groupe local | Les scènes lourdes se coordonnent entre joueurs de la même dimension situés à 32 blocs ou moins. |
| Fenêtre | Une réservation locale bloque une autre scène lourde pendant 80 ticks, soit 4 secondes. |
| Équité | L'ordre des joueurs est trié puis décalé à chaque cycle ; la première tentative ne revient donc pas toujours au même UUID. |
| Indépendance | Les joueurs seuls, les groupes éloignés de plus de 32 blocs et les dimensions différentes conservent des rythmes indépendants. |
| Gestionnaires naturels | Le directeur, l'Originel, le Contrefait, le Rampant, L'Absent et la Cavaleuse consultent la même réservation ; leurs sondages sont échelonnés. |
| Forçage opérateur | `/penumbra event` et les commandes de test des créatures contournent la réservation, les chances naturelles et, selon la commande, les cooldowns. Un argument final `[joueur]` permet de viser un autre joueur, même depuis la console. |

Les sons brefs et les couches ambiantes ne sont pas considérés comme une scène
lourde. Ils restent donc disponibles pendant la fenêtre de quatre secondes et
ne peuvent pas affamer les apparitions 3D.

## 2. Propriété et ciblage

- Chaque créature créée pour une scène conserve l'UUID de son propriétaire.
  Ses goals de poursuite, de regard, de bond et d'attaque filtrent sur cet UUID.
- Un témoin peut voir une entité physique partagée à portée de tracking, mais
  il ne devient ni sa cible de navigation, ni sa victime de dégâts, ni le
  destinataire de son screamer plein écran.
- L'Originel, le Contrefait et l'Hôte de `behind_other` utilisent en plus un
  suivi réseau réservé à leur propriétaire. Les autres manifestations
  physiques restent partagées lorsque le groupe doit constater la présence.
- Les scènes courtes (Observateur, Projectionniste, Rampant, Absent et
  Contrefait) ne sont pas sauvegardées à mi-action. Un départ, changement de
  dimension, unload ou redémarrage ne peut donc pas ressusciter une attaque.
- Le Sceau de bannissement d'un témoin ne peut pas toucher l'Originel ou le
  Contrefait privé d'un autre joueur ; les manifestations réellement partagées
  restent dissipables normalement.
- `multiplayerContent` contrôle les scènes qui exploitent réellement un autre
  joueur (`behind_other`, `paired_echo`, `stolen_name` et contagion de crise).
  Le désactiver ne supprime pas
  les manifestations ordinaires correctement ciblées.

## 3. Sons positionnés et anti-doublons

Un auditeur reçoit un son lorsqu'il se trouve dans la portée acoustique de sa
position, ou lorsqu'il est un compagnon situé à 24 blocs ou moins du joueur
source. Dans tous les cas, il doit être dans la même dimension. La position
acoustique conserve la direction du leurre et l'atténuation reste gérée côté
client.

`SoundDeliveryGate` identifie chaque route par dimension, source et auditeur :

- les sons ordinaires sont limités à un paquet tous les 3 ticks par route ;
- la voie prioritaire reste disponible pour les stingers et screamers, mais une
  signature strictement identique n'est envoyée qu'une fois dans le même tick,
  même si un autre son prioritaire s'intercale ;
- les paramètres non finis ou invalides sont refusés, puis volume, hauteur et
  intensité de jumpscare sont bornés avant sérialisation et à la réception ;
- la table est bornée et les routes d'un joueur sont supprimées à sa
  déconnexion ; l'arrêt du serveur efface l'ensemble de l'état statique.

Le visuel du jumpscare reste toujours envoyé uniquement à sa cible. Seul son
cri positionné peut être entendu par les compagnons proches.

## 4. Déconnexion et changement de session

La déconnexion purge les fenêtres C2S, les routes de sons et les états
transitoires attachés au joueur. Côté client, le gestionnaire de jumpscare est
réinitialisé afin qu'un cooldown ou un visuel de l'ancienne session ne déborde
pas sur la suivante. L'arrêt serveur supprime aussi les réservations locales de
scène. Les données persistantes voulues — profil de tension, histoire et album
photo — ne sont pas concernées par ce nettoyage.

## 5. Album photo partagé

L'album est plafonné à 2 048 captures par monde et à 128 captures par joueur.
Le contrôle individuel est appliqué par propriétaire : plusieurs photographes
peuvent donc remplir le quota mondial sans qu'un joueur dépasse sa part ou
consomme celle d'un autre. Les anciennes photos conservent leur identifiant et
leur format ; le passage depuis la limite historique de 512 ne demande aucune
migration.

La 0.9.0 ajoute des pages de six métadonnées et un chargement séparé des pixels.
Seul le propriétaire peut renommer, publier, rendre privée ou supprimer une
archive. Trois limiteurs distincts bornent pages, images et mutations. Une
publication retirée invalide immédiatement le cache des lecteurs qui ne
possèdent ni tirage ni cadre autorisé ; une suppression est diffusée à tous.
Les anciens champs absents deviennent titre vide et visibilité privée.

## 6. Scènes asymétriques de duo

- `behind_other` fixe propriétaire et visibilité privée avant l'ajout de
  l'Hôte, anime sa traversée et ne donne au compagnon que des pas ciblés.
- `paired_echo` envoie deux pistes différentes au même tick et revalide le duo
  avant sa seconde impulsion.
- `stolen_name` copie le vrai nom du compagnon sans jamais transférer la cible
  de L'Absent.
- Déconnexion, mort, mode spectateur, changement de dimension ou éloignement
  annulent la continuation sans rechercher un remplaçant. Les variantes solo
  restent disponibles et les commandes forcées contournent seulement le rythme.

## 7. Couverture de validation

Les tests unitaires couvrent notamment :

- l'indépendance des dimensions, des sources et des auditeurs ;
- la déduplication prioritaire, y compris l'ordre A, B, A dans un même tick ;
- le retour de l'horloge, la capacité bornée et le nettoyage de déconnexion ;
- la limite compagnon de 24 blocs ;
- la rotation équitable et la réservation locale des scènes ;
- le quota individuel de 128 photos et la capacité mondiale de 2 048.
- la sélection du compagnon, le repli solo et les faux noms déterministes ;
- codec d'album ancien, pages privées/publiques, mutations propriétaires et
  limites réseau indépendantes.

## 8. Historique des protections Phase 4

La Phase 4 avait introduit le plafond d'Hôtes/Observateurs par joueur, le
rate-limit C2S à 10 paquets par seconde, le coût de durabilité de la caméra sur
front montant et un premier throttle sonore non prioritaire. Ces protections
restent actives. La 0.8.1 les complète par une clé de routage réellement
multijoueur, la déduplication prioritaire, la coordination locale et le
nettoyage complet des changements de session.
La 0.9.0 complète cet audit avec les scènes de duo, l'album autoritaire et la
révocation des caches privés.
