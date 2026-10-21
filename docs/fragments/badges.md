# Badges Penumbra

Avancements du mod (`penumbra:<id>`), octroyés exclusivement par code via
`fr.penumbra.badge.PenumbraBadges.grant(ServerPlayer, String)`. Chaque avancement
n'a qu'un critère `minecraft:impossible` : le jeu ne le déclenche jamais seul ;
l'octroi programmatique joue automatiquement le toast et le son vanilla d'avancement.

Interrupteur global : champ `badgesEnabled` de `config/penumbra.json`
(désactivé → `grant` sort silencieusement).

## Les 7 badges

| id | Titre FR | Titre EN | Déclencheur (qui appelle `grant`) | Conditions |
|---|---|---|---|---|
| `root` | Penumbra | Penumbra | Automatique (onglet) | Racine de l'onglet Penumbra ; s'affiche dès qu'un badge enfant est octroyé. Jamais octroyé directement. |
| `you_see_me` | Vous me voyez | You See Me | Agent Watcher | Le joueur soutient le regard de l'Observateur pendant `watcherStareSeconds` secondes (config). Frame `challenge`, toast affiché. |
| `first_anomaly` | Première fissure | First Crack | Intégrateur | Première anomalie détectée par le joueur (événement d'horreur constaté). |
| `first_structure` | Traces de pas | Footprints | Intégrateur | Découverte de la première structure Penumbra. Enfant de `first_anomaly`. |
| `survivor` | Encore là | Still Here | Intégrateur | Survivre à une poursuite de l'Hôte (fin de poursuite, joueur vivant). |
| `sanctum_ritual` | Cendre et serment | Ash and Oath | Intégrateur | Accomplir le rituel du sanctuaire. |
| `confrontation` | Il connaît votre nom | It Knows Your Name | Intégrateur | Atteindre le stade 6 de l'histoire. Enfant de `sanctum_ritual`. Frame `challenge`. |

## Hiérarchie de l'onglet

```
root
├── first_anomaly
│   └── first_structure
├── you_see_me          (challenge)
├── survivor
└── sanctum_ritual
    └── confrontation   (challenge)
```

## Règles d'appel

- Toujours appeler `PenumbraBadges.grant(player, "<id>")` côté serveur, jamais côté client.
- `grant` est idempotent côté vanilla (un avancement déjà complété ne re-toaste pas) ;
  utiliser `PenumbraBadges.has(player, "<id>")` en amont si l'appelant veut éviter
  de déclencher ses propres effets deux fois.
- Icônes : objets du mod existants (`ash_sigil`, `binoculars`, `impossible_photo`,
  `silhouette_glass`). Traductions : fragments `assets/penumbra/lang/fragments/badges_*.json`.
