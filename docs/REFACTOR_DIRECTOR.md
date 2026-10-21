# Plan de refactor — HorrorDirector (2 630 lignes → 6 modules)

## Pourquoi ce document plutôt que le refactor lui-même

Le découpage ci-dessous déplace ~1 800 lignes entre fichiers. C'est un travail
mécanique mais dense en références croisées privées : il ne doit être exécuté
que sur une base **compilée et verte** (`gradlew test`), jamais empilé sur un
changeset non validé. Ce document fige l'analyse pour que l'exécution soit un
simple déroulé, vérifiable étape par étape.

Règle d'or : **aucun changement de comportement**. Chaque étape = déplacement
+ délégation, suivie de `gradlew test` avant l'étape suivante.

## Cartographie actuelle (lignes indicatives)

| Bloc | Lignes | Contenu |
|---|---|---|
| Constantes + jeux d'identifiants | 60–310 | cadences, seuils, `*_EVENT_IDS`, `MINIMUM_EVENT_STAGES` |
| État transitoire par joueur | 312–370 | ~25 maps statiques par UUID |
| Tick + cycle de vie | 395–560 | `onServerTick`, disconnect, server stopped |
| API publique | 570–815 | tension, arcs, grâce, notes de manifestation |
| Poursuites | 816–1180 | start/end chase, fausse retraite, téléport |
| Réactions jumelles (caméra) | 1183–1577 | 7 réactions, armement, unzoom |
| Jumpscares retardés | 1578–1945 | `note*`, armement, tir, déclencheurs internes |
| Sélection d'événements | 1946–2604 | pool pondéré, faux calme, stades, tension |

## Découpage cible (ordre d'extraction)

### Étape 1 — `EventTaxonomy` (risque minimal, ~120 lignes)
Sets d'identifiants (`MANIFESTATION_EVENT_IDS`, `AUDIO_EVENT_IDS`,
`UNDERGROUND_EVENT_IDS`, `MIMIC_EVENT_IDS`, `GROUP_COORDINATED_EVENT_IDS`,
`HOST_*`, `MINIMUM_EVENT_STAGES`) + `usesGroupCoordination`,
`minimumStageForEvent`, `stageUnlockedByTension`. Fonctions pures, constantes
uniquement. HorrorDirector garde des délégations package-private (les tests
existants continuent de passer sans modification).

### Étape 2 — `DelayedScares` (~370 lignes)
Tout le bloc 1578–1945 : `SCARE_IDS`, `SCARE_TYPE_LAST`, `ARMED_SCARES`,
`LAST_SCARE_ANY`, `LOOKBACK_TIMES`, `note*`, `armScare`, `fireScare`,
`executeScare`, `tick*` internes. Dépendances entrantes : `profile()`,
`runLater()`, `isInFalseCalm()` → passer par une interface étroite
`DirectorContext` (3 méthodes) instanciée par HorrorDirector.
État à déplacer AVEC le module, nettoyage disconnect/stop compris.

### Étape 3 — `BinocularReactions` (~400 lignes)
Bloc 1183–1577 + `BINO_*`, `ARMED_BINO_REACTIONS`, `LAST_BINO_*`.
Même technique de contexte étroit. Attention : `approachBetweenOnUnzoom`
touche aussi `vanishInAsh` (poursuites) → dépendance vers Étape 4, extraire
`vanishInAsh`/`teleportBehindPlayer` vers un utilitaire `HostStagecraft`
partagé dès cette étape.

### Étape 4 — `ChaseCoordinator` (~330 lignes)
Bloc 816–1180 : `ACTIVE_CHASES`, `GLOBAL_COOLDOWN_UNTIL`,
`LAST_NATURAL_CHASE_ROLL`, avertissements, fausse retraite.
API publique conservée par délégation (`startChase`, `endChase`,
`canStartChase`, `physicalManifestationAllowed` sont appelés depuis les
entités et les commandes).

### Étape 5 — `FalseCalmCycle` (~150 lignes)
`FALSE_CALM_*`, `notifyFalseCalm`, `isInFalseCalm`, `tryRunFalseCalmBreak`,
entrée/sortie et rupture sobre.

### Étape 6 — `EventSelector` (~450 lignes)
`trySelectEvent`, `CandidatePool`, `buildCandidatePool`, `runCandidatePool`,
`runEvent`, quotas audio, dette de manifestation. Dernière étape : c'est le
cœur, il ne bouge qu'une fois tout le reste stabilisé.

**Résultat final** : HorrorDirector ≈ 600 lignes — tick, cycle de vie, API
publique, câblage des modules. Aucun appelant externe modifié.

## Protocole de vérification (à chaque étape)

1. `gradlew test` — zéro rouge, zéro test modifié.
2. `gradlew runClient` : `/penumbra status`, `/penumbra event <id>`,
   une poursuite forcée, un faux calme (`/penumbra tension 90` puis attendre).
3. `git diff --stat` : uniquement les fichiers attendus.
4. Commit par étape (revert trivial).

## Pièges recensés pendant l'analyse

- `profile(ServerPlayer)` est privé et OMNIPRÉSENT : le contexte étroit doit
  l'exposer en package-private, pas en public.
- `runLater` s'appuie sur `DelayedTaskScheduler` déjà extrait — s'en servir
  comme modèle de style.
- Les maps d'état DOIVENT suivre leur module (sinon fuites au disconnect :
  reproduire le nettoyage de `onPlayerDisconnect`/`onServerStopped` module
  par module — c'est le bug le plus probable de ce refactor).
- `HorrorDirectorPacingTest` et `MultiplayerEventPacingTest` appellent des
  méthodes package-private : les délégations doivent conserver signatures et
  visibilité exactes.
