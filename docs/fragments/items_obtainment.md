# Objets narratifs — obtention

Fragment documentaire d'Agent_Items. Les loot tables elles-mêmes sont
possédées par un autre agent : ce fichier ne fait que **documenter** la
répartition prévue. Tous les objets sont enregistrés dans
`fr.penumbra.content.NarrativeItems` (namespace `penumbra`), non empilables
sauf mention contraire, et donnés en debug via `/penumbra item give <id>`.

| Objet | Id | Structure source (loot) | Rareté | Rôle narratif |
|---|---|---|---|---|
| Lentille fissurée | `penumbra:cracked_lens` | `observation_post` (coffre) | Peu commune | Indice du poste d'observation : le verre s'est brisé en « regardant » quelque chose. Ancre le thème de l'observation qui se retourne. |
| Journal incomplet | `penumbra:incomplete_journal` | `observation_post` (coffre) | Rare | Lore principal : pages arrachées, écriture qui devient celle du joueur (« Quelque chose apprend à être vous »). |
| Fragment d'observation | `penumbra:observation_fragment` | `observation_post` + `ash_sanctum` (coffres) | Commune (stacks de 16) | Notes de terrain de l'observateur disparu ; fragment récurrent qui alimente le lore au compte-gouttes. |
| Fil à plomb noirci | `penumbra:blackened_plumb_bob` | `penumbra_well` (coffre) | Peu commune | Indice du puits : l'outil de mesure est remonté « pointant vers vous » — la verticalité du puits est compromise. |
| Photographie impossible | `penumbra:impossible_photo` | `penumbra_well` (coffre) | Très rare | Révélation tardive : photo datée du futur avec le joueur dessus. Pic narratif de l'arc. |
| Morceau de verre | `penumbra:silhouette_glass` | `ash_sanctum` (coffre) | Peu commune | Relique du sanctuaire de cendre. 15 % des stacks révèlent en infobulle « une silhouette semble se refléter » (marquage persistant par stack via le composant `penumbra:silhouette_state`, tirage unique à la première entrée en inventaire). |

## Notes d'intégration

- Les six objets apparaissent aussi dans l'onglet créatif « Ingrédients »
  (même onglet que `ash_sigil` et `binoculars`).
- Répartition par structure : `observation_post` → journal, fragments,
  lentille ; `ash_sanctum` → verre, fragments ; `penumbra_well` → photo,
  fil à plomb.
- Ton des infobulles : descriptions énigmatiques FR/EN, clés
  `item.penumbra.<id>.desc` (fragments de langue `lang/fragments/items_*.json`,
  fusionnées par l'agent ressources — ne pas éditer `en_us.json` / `fr_fr.json`).
