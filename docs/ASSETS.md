# ASSETS.md — Penumbra — Inventaire et specs des assets visuels

## §Prompts — remplacement des placeholders par génération IA (0.6.0)

Prompts prêts à coller (GPT Image ou équivalent). Livrer en PNG ; les
dimensions finales sont réappliquées à l'intégration (redimensionnement
NEAREST + vérification UV par l'intégrateur — envoyer l'image telle quelle).

1. **`textures/gui/screamer_host.png`** (cible 256×256 ou 512×512) — LE plus
   important, et le plus adapté à l'IA (aucune contrainte UV) :
   > Extreme close-up horror face filling the entire frame, a humanoid figure
   > made of charred ash and burnt charcoal, cracked skin with faint dying
   > ember glow in the fissures, two asymmetric burning orange eyes (right eye
   > slightly lower and larger), a gaping pitch-black elongated mouth frozen
   > mid-scream, ash particles drifting, very dark desaturated palette, black
   > vignette edges fading to pure black, no text, no watermark, digital
   > horror art, subtle film grain. Square image.
   Variante 2 (alterner les screamers tue l'habituation) : même prompt +
   « the face is tilted 15 degrees, eyes looking slightly off-camera as if it
   sees something behind the viewer ».
2. **`icon.png`** (cible 128×128) :
   > Minimalist dark mod icon, a tall thin charred silhouette with two tiny
   > ember-orange eyes standing in penumbra half-shadow, deep black-purple
   > background (#141216), faint ash motes, flat vector-like style, no text.
3. **Textures d'entités** (host/watcher/originel, 64×64 UV) — **FAIT en
   0.6.0** : les planches de référence IA v3 (Hôte), v4 (Observateur) et
   v5 (Originel) ont été fournies par l'utilisateur et repeintes en textures
   UV par `gen_entity_textures_v2.py` (palettes v3 relevées sur les
   planches : Hôte charbon `#0e0c10` veiné de braise `#e8813c`, visage de
   fumée `#3a3740`–`#55515c`, yeux `#ffac4e` ; Observateur noir uniforme
   `#060608`, yeux `#9aa0aa` ; Originel peau `#b5b1a9`, chemise sarcelle
   `#3e4a4e` à col V, avant-bras nus, jean marine `#252838`).
   Prompt d'origine, si besoin de régénérer une planche :
   > Character reference sheet, front view and back view side by side, of a
   > 2.6-blocks-tall Minecraft-style blocky humanoid made of charred ash,
   > near-black body with charcoal mottling, thin dying-ember cracks on the
   > torso, slightly lighter ash-grey face mask, two asymmetric ember-orange
   > pixel eyes, arms hanging dead straight, pixel art, flat colors, dark
   > background, no text.
   (Adapter : Observateur = « pitch-black silhouette, two faint pale grey
   pixel eyes, 2.4 blocks tall » ; Originel = « pale washed-out skin, dark
   desaturated shirt and trousers, exactly player-proportioned, NO face at
   all, blank head ».)
   **Contrefait** (0.6.0) — prompt dédié :
   > Character reference sheet on a plain dark grey background, front view
   > and back view side by side. A Minecraft player-proportioned blocky
   > humanoid, exactly like a classic player model: cubic head, blocky arms
   > and legs, head slightly tilted to one side. Warm tan skin with a few
   > greyish corrupted patches, a worn desaturated teal shirt, blue-purple
   > trousers where the two legs are two slightly DIFFERENT shades. Two
   > glowing pure-white eyes that are MISMATCHED: the left eye is normal,
   > the right eye is one pixel higher and smaller. One arm is entirely
   > shirt-colored by mistake, as if the copy was colored wrong. Dark brown
   > hair with a missing bald patch on the BACK of the head. Flat pixel-art
   > shading, crisp edges, uncanny almost-correct look, no text, no watermark.
4. **Objets 16×16** (ash_sigil, cracked_lens, incomplete_journal,
   observation_fragment, impossible_photo, silhouette_glass,
   blackened_plumb_bob) :
   > 16x16 pixel art item icon for a dark psychological horror Minecraft mod,
   > [OBJET : a wax seal stamped in cold ash / a cracked brass lens / a
   > half-burnt leather journal / a torn observation note / an impossible
   > photograph with a silhouette / a shard of glass reflecting a figure that
   > is not there / a blackened plumb bob on a string], muted desaturated palette with one ember-orange accent,
   > dark outline, transparent background, no text.

---

> Phase 1 : toutes les textures sont des **placeholders générés procéduralement**
> par `tools/gen_textures.py` (PIL, seed fixe, résultats reproductibles).
>
> **0.5.0** : les textures d'ENTITÉS sont régénérées par
> `tools/gen_entity_textures_v2.py` (host, watcher, originel) — l'Hôte passe du
> gris pâle au **calciné** (base `#141216`, marbrures `#24212a`, fissures de
> braise `#d97b3f`/`#6e4126`, masque de tête `#2e2b30`, yeux braise
> asymétriques), l'Observateur garde `#08080a` avec yeux asymétriques,
> l'Originel est blafard (`#c6beb2`) et **sans visage**. La palette « pâle »
> ci-dessous reste valable pour les OBJETS et l'icône (gen_textures.py).
> Ce document donne, pour chaque asset, ses specs actuelles et le **prompt de
> génération IA** licite pour produire la version finale (création originale,
> sans imitation d'artiste nommé ni contenu sous licence).

## Palette imposée (SPEC §4.12)

| Rôle | Couleur |
|---|---|
| Corps de l'Hôte (pâle) | `#c9c4bb` |
| Membres de l'Hôte (assombris) | `#8f8a82` |
| Yeux / accents braise | `#d97b3f` |
| Fond icône | `#141216` |
| Boîtier de la caméra VHS | `#2b2b30` |

Direction artistique : horreur sobre et crédible, tons désaturés, pas de gore,
pas de jump-scare visuel gratuit. L'horreur vient de l'anormal, pas du sang.

## Ajout 0.11.0 — double exposition de L'Absent

| Champ | Valeur |
|---|---|
| Fichier | `assets/penumbra/textures/entity/absent_photo_truth.png` |
| Dimensions | 128×72 px |
| Format | PNG RGBA, fond transparent |
| État | Illustration originale générée puis détourée pour la 0.11.0 |
| Usage | Apparition progressive uniquement sur une photo où L'Absent a été cadré |

La silhouette est volontairement lisible à petite taille, asymétrique et sans
texte. La transparence est contrôlée par `validatePenumbraResources` pour éviter
qu'un rectangle opaque recouvre la photographie.

---

## 1. `assets/penumbra/icon.png` — Icône du mod

| Champ | Valeur |
|---|---|
| Dimensions | 128×128 px |
| Format | PNG RGBA |
| État | **Final candidate 0.6.0** (illustration originale générée) |
| Description | Silhouette calcinée aux yeux braise sur fond noir-violet |

**Ancien prompt monogramme (archivé, non retenu en 0.6.0) :**
> Minimalist game mod icon, flat design, 128×128. A single elegant capital
> letter "P" centered on a near-black warm charcoal background (#141216). The
> letter is rendered in a pale bone-ash color (#c9c4bb) with a very subtle
> weathered stone texture. Faint, thin crescent of dim ember orange (#d97b3f)
> partially eclipsed behind the letter, suggesting a penumbra. No text besides
> the letter, no borders, no gradients, sober horror aesthetic, restrained and
> professional. Original artwork.

## 2. `assets/penumbra/textures/entity/host.png` — Texture de l'Hôte

| Champ | Valeur |
|---|---|
| Dimensions | 64×64 px |
| Format | PNG RGBA (fond transparent hors UV) |
| État | **Final candidate 0.6.0** (UV repeint depuis la planche IA v3) |
| Modèle 3D | `HostEntityModel` (§4.9) : head 8×8×10, body 8×20×4, arms 3×16×3, legs 3×14×3 |

**UV map historique** (structure conservée par le générateur v2) —
pour une boîte `(w,h,d)` à l'offset `(u,v)`, emprise = largeur `2(d+w)`,
hauteur `d+h`, face avant = `(u+d, v+d) → (u+d+w, v+d+h)` :

| Partie | Offset | Boîte (w×h×d) | Couleur |
|---|---|---|---|
| head | (0, 0) | 8×8×10 | `#c9c4bb`, yeux braise en face avant (12–15, 14–15) |
| body | (0, 20) | 8×20×4 | `#c9c4bb` |
| arm_r | (40, 0) | 3×16×3 | `#8f8a82` |
| arm_l | (40, 20) | 3×16×3 | `#8f8a82` |
| leg_r | (0, 46) | 3×14×3 | `#8f8a82` |
| leg_l | (14, 46) | 3×14×3 | `#8f8a82` |

**Ancien prompt pâle (archivé ; la palette calcinée v3 est utilisée) :**
> Minecraft-style entity texture, 64×64 pixel art, flat unshaded boxes layout.
> A tall, emaciated humanoid silhouette: corpse-pale skin (#c9c4bb) like cold
> ash over bone, visibly darker thin limbs (#8f8a82) as if charred. On the
> head's front face, two small ember-orange dots (#d97b3f) as eyes — the only
> warm color. Subtle mottled noise and faint ash-dust speckling across the
> body. Uncanny through stillness and proportions, NOT gore, no wounds, no
> blood. Keep the exact UV box layout described above. Original pixel art.

## 3. `assets/penumbra/textures/item/ash_sigil.png` — Sceau de cendre

| Champ | Valeur |
|---|---|
| Dimensions | 16×16 px |
| Format | PNG RGBA (fond transparent) |
| État | **Placeholder** (anneau + rune braise) |
| Description | Petit talisman : anneau de cendre `#c9c4bb`, rune intérieure `#d97b3f` |

**Prompt de génération IA (final) :**
> 16×16 Minecraft item sprite, pixel art, transparent background. A small
> circular sigil stamped in pale ash-gray (#c9c4bb), like a wax seal made of
> compacted cinders, slightly chipped on one edge. Inside the ring, a single
> abstract rune glowing dim ember-orange (#d97b3f). Readable at tiny size, high
> contrast silhouette, sober dark-fantasy mood, no text. Original pixel art.

## 4. `assets/penumbra/textures/particle/ash_mote.png` — Particule de cendre

| Champ | Valeur |
|---|---|
| Dimensions | 8×8 px |
| Format | PNG RGBA (dégradé radial vers transparent) |
| État | **Placeholder acceptable en Phase 1** (proche du rendu final) |
| Description | Motte de cendre douce : cœur gris clair, alpha décroissant en radial |

**Prompt de génération IA (final) :**
> 8×8 particle sprite, pixel art, transparent background. One soft round mote
> of gray ash, brightest at center and fading smoothly to fully transparent at
> the edges, slightly warm pale gray. No hard outline, no shape detail — it
> will be tinted and animated in-game. Original pixel art.

## 5. `assets/penumbra/textures/item/binoculars.png` — Caméra VHS

| Champ | Valeur |
|---|---|
| Dimensions | 64×64 px |
| Format | PNG RGBA (fond transparent) |
| État | **Final candidate 0.6.1** (illustration originale générée avec l’outil intégré `imagegen`, détourage chroma puis réduction pixel-art) |
| Description | Caméscope VHS noir rayé, objectif gris-bleu, poignée supérieure, dragonne et témoin REC rouge. L’identifiant historique `binoculars` est conservé pour ne pas casser les mondes. |
| Modèles associés | `assets/penumbra/models/item/binoculars.json` pour l’inventaire et `binoculars_in_hand.json` pour le modèle 3D tenu en main |
| Atlas 3D | `textures/item/binoculars_model.png`, 16×16 px RGBA, dérivé de la même direction artistique |

**Prompt de génération IA (final) :**
> Minecraft item texture sprite source, handheld 1990s VHS camcorder, dark
> horror-game tool, scratched charcoal-black plastic, silver-gray lens with a
> faint cold-blue reflection, tiny red recording LED, top handle and wrist
> strap, hand-authored pixel-art look designed to remain readable after
> reduction to 64×64, isolated on a flat #00ff00 chroma-key background, no
> text, no logo, no watermark, no shadow outside the object.

---

## 6. `assets/penumbra/textures/gui/screamer_host*.png` — screamers 0.6.0 à 0.8.0

| Champ | Valeur |
|---|---|
| Fichiers | `screamer_host.png` à `screamer_host_9.png` |
| Dimensions | 512×512 px RGB/RGBA, validées automatiquement au build |
| État | **Final candidates** : deux variantes 0.6.0 + quatre créations 0.6.2 + trois créations 0.8.0 |
| Usage | Screamer plein écran, mais aussi intrusion brève du type `apparition`; jamais affiché si `screamersEnabled` est faux |
| Sélection | Sac mélangé : cycle complet avant réutilisation et aucun doublon immédiat |

Ces images sont volontairement beaucoup plus réalistes que les textures de
monde. Elles doivent rester signalées comme contenu sensible dans le README et
la page de distribution. Une variante pixel-art moins intense reste souhaitable
pour un futur profil d'accessibilité.

Les quatre ajouts 0.6.2 ont été générés avec l'outil intégré `imagegen`, en
mode génération avec les deux images 0.6.0 comme références de continuité,
puis recadrés et réduits en PNG 512×512 :

1. **Hôte asymétrique** — visage de cendre collé à l'objectif, œil braise
   immense, second œil minuscule et déplacé, bouche verticale impossible.
2. **Observateur** — tête parfaitement noire et sans visage, deux points blancs
   mal alignés, doigts à peine visibles sur les bords, esthétique found footage.
3. **Originel** — visage pâle surexposé, orbites seulement suggérées, bouche
   réduite à une fente et tête inclinée anormalement.
4. **Contrefait** — copie presque humaine à géométrie cubique, yeux carrés
   décalés, moitié de sourire mal raccordée et artefacts VHS.

Contraintes communes des prompts : très gros plan carré destiné à un jeu
d'horreur, noir presque total, composition lisible plein écran, création
originale, aucun sang ni gore, aucun texte, logo ou filigrane.

Les variantes 7 à 9 ajoutées en 0.8.0 sont détaillées avec leurs sources et
prompts résumés dans la section 10.

## 7. `assets/penumbra/textures/item/hammer.png` — Marteau de mineur 4×4

| Champ | Valeur |
|---|---|
| Dimensions | 64×64 px |
| Format | PNG RGBA (fond transparent) |
| État | **Final candidate 0.6.1** (illustration originale générée avec l’outil intégré `imagegen`, détourage chroma puis réduction pixel-art) |
| Description | Lourde tête rectangulaire en fer noirci, arêtes argentées usées, petit accent braise et manche sombre cerclé de cuir. |
| Modèle associé | `assets/penumbra/models/item/hammer.json` (`minecraft:item/handheld`) |

**Prompt de génération utilisé :**
> Minecraft item texture sprite source, heavy 4×4 mining hammer, rectangular
> forged iron head, charcoal steel with worn silver edges, one tiny ember-red
> accent, dark wooden handle wrapped in black leather, hand-authored pixel-art
> look designed to remain readable after reduction to 64×64, isolated on a
> flat #00ff00 chroma-key background, no text, no logo, no watermark, no
> shadow outside the object.

### Variantes 0.6.3

| Objet | Texture | Traitement |
|---|---|---|
| Pierre | `textures/item/stone_hammer.png` | Tête en pierre grise taillée, facettes ébréchées |
| Or | `textures/item/golden_hammer.png` | Or chaud, arêtes plus sombres et usées |
| Diamant | `textures/item/diamond_hammer.png` | Cristal cyan anguleux, reflets pâles opaques |

Les trois variantes ont été créées en mode **édition** avec `hammer.png` comme
référence afin de conserver exactement silhouette, orientation diagonale,
manche, marges et éclairage. Seul le matériau de la tête change. Contraintes
communes : pixel-art Minecraft, un seul objet, fond chroma `#00ff00`, aucun
texte/logo/filigrane/ombre et aucune couleur verte dans l'objet. Le fond a été
détouré avec matte doux et suppression de débordement vert, puis les images ont
été réduites en 64×64 RGBA avec rééchantillonnage nearest-neighbor.

Prompts de matériau :

- pierre : « rough dark gray cobblestone, chipped blocky facets, simple wood
  binding, stone tier » ;
- or : « rich Minecraft-like warm gold, worn darker edges, not plastic » ;
- diamant : « cyan-blue diamond/crystal, angular facets, pale highlights, no
  transparency ».

## 8. Photographie instantanée et cadre mural — 0.6.4

Les deux concepts ont été créés avec l’outil intégré **ImageGen**, en mode
`stylized-concept`. Il s’agit de créations originales sans artiste, marque ou
œuvre sous licence cités. Les sorties haute définition ont été détourées puis
réduites avec une palette et des arêtes pixel-art compatibles avec la Caméra
VHS 0.6.1.

### Tirage instantané

| Champ | Valeur |
|---|---|
| Source ImageGen détourée | `tools/image_sources/instant_photo_rgba.png` |
| Sprites finaux | `assets/penumbra/textures/item/camera_photo_black.png`, `camera_photo_faint.png`, `camera_photo_sepia.png`, `camera_photo.png` |
| Dimensions finales | 64×64 px RGBA |
| Usage | Quatre états d’objet pendant le développement noir → couleur sur 10 s |

**Prompt résumé :** tirage instantané physique de jeu, papier ivoire vieilli à
marge inférieure large, photographie 4:3 d’une grotte/forêt Minecraft inquiétante
avec bruit VHS cyan, vert froid et ambre, usure et traces discrètes, lisible à
64×64, centré sur fond chroma vert uniforme, sans texte, logo, marque, filigrane
ni ombre extérieure.

Les variantes noire, faible et sépia sont dérivées de la même silhouette et du
même papier pour éviter tout saut visuel pendant le développement. La vraie
capture dynamique n’est pas encodée dans le sprite : elle est rendue depuis
l’album persistant dans la visionneuse et sur le cadre.

### Petit cadre photo

| Champ | Valeur |
|---|---|
| Source ImageGen détourée | `tools/image_sources/photo_frame_rgba.png` |
| Sprite d’objet | `assets/penumbra/textures/item/photo_frame.png` |
| Texture du renderer | `assets/penumbra/textures/item/photo_frame.png` (les variantes `textures/block/` restent réutilisables par un futur modèle bloc) |
| Dimensions finales | 64×64 px RGBA pour l’objet ; textures de cadre adaptées au rendu mural |
| Usage | Entité murale compacte avec ouverture 16:9 pour la photo dynamique |

**Prompt résumé :** petit cadre mural analogique vu de face, bois sombre noirci,
coins et attaches en fer oxydé, proportions fines, ouverture rectangulaire nette,
un très léger accent braise cohérent avec Penumbra, pixel-art Minecraft lisible,
fond chroma vert uniforme, sans texte, logo, filigrane ni décor extérieur.

Le détourage chroma, la réduction et les déclinaisons de matériau sont des
traitements d’intégration ; la source ImageGen reste conservée dans
`tools/image_sources/` pour audit visuel et régénération déterministe par
`tools/process_camera_assets.py`.

## 9. Projectionniste, Rampant et Absent — 0.7.0

Les trois planches de concept sont des créations originales réalisées avec
l'outil intégré **ImageGen**, en mode `stylized-concept`, sans artiste, marque,
personnage tiers ni œuvre sous licence cités. Chaque sortie est conservée sous
deux formes : le rendu sur fond chroma vert pour l'audit de génération, puis la
version RGBA détourée utilisée comme référence d'intégration.

| Monstre | Source ImageGen chroma | Source détourée | Atlas final |
|---|---|---|---|
| Projectionniste | `tools/image_sources/projectionist_concept_chroma.png` | `tools/image_sources/projectionist_concept_rgba.png` | `src/main/resources/assets/penumbra/textures/entity/projectionist.png` |
| Rampant | `tools/image_sources/rampant_concept_chroma.png` | `tools/image_sources/rampant_concept_rgba.png` | `src/main/resources/assets/penumbra/textures/entity/rampant.png` |
| Absent | `tools/image_sources/absent_concept_chroma.png` | `tools/image_sources/absent_concept_rgba.png` | `src/main/resources/assets/penumbra/textures/entity/absent.png` |

Les trois atlas d'entité sont des PNG RGBA **64×64** calés sur les UV des
modèles Java. Le Projectionniste possède en plus l'overlay transparent
**128×72**
`src/main/resources/assets/penumbra/textures/entity/projectionist_photo.png`,
superposé à la vraie capture et révélé progressivement avec son développement.

`tools/gen_trio_monster_assets.py` régénère de façon déterministe les trois
atlas `entity/*.png` et `projectionist_photo.png`. Les palettes, silhouettes et
détails des concepts détourés servent de références ; le script lit directement
`projectionist_concept_rgba.png` pour composer l'overlay photographique, puis
peint les atlas dans les zones UV déclarées. Commande depuis la racine :

```bash
python tools/gen_trio_monster_assets.py
```

Contraintes communes : horreur stylisée sans gore, aucun texte, logo ou
filigrane, silhouettes lisibles à la résolution Minecraft et transparence hors
UV vérifiée pour le Projectionniste et L'Absent.

## 10. Sceau de bannissement, Cavaleuse et screamers 7 à 9 — 0.8.0

Tous les concepts de cette section sont des créations originales réalisées
avec l'**ImageGen intégré**, cas d'usage `stylized-concept`. Les sources de
travail du workspace sont conservées dans `tools/image_sources/` pour audit et
régénération ; aucun artiste, personnage tiers, marque ou œuvre sous licence
n'a servi de référence nommée.

| Asset | Sources du workspace | Sortie intégrée | Dimensions |
|---|---|---|---|
| Sceau de bannissement | `tools/image_sources/ash_ward_concept_chroma.png`, `tools/image_sources/ash_ward_concept_rgba.png` | `src/main/resources/assets/penumbra/textures/item/ash_ward.png` | 64×64 RGBA |
| Cavaleuse | `tools/image_sources/scuttler_concept_chroma.png`, `tools/image_sources/scuttler_concept_rgba.png` | `src/main/resources/assets/penumbra/textures/entity/scuttler.png` | atlas UV 64×64 RGBA |
| Screamer 7 — Projectionniste | `tools/image_sources/screamer_v7_projectionist.png` | `src/main/resources/assets/penumbra/textures/gui/screamer_host_7.png` | 512×512 RGB |
| Screamer 8 — Rampant | `tools/image_sources/screamer_v8_rampant.png` | `src/main/resources/assets/penumbra/textures/gui/screamer_host_8.png` | 512×512 RGB |
| Screamer 9 — Absent | `tools/image_sources/screamer_v9_absent.png` | `src/main/resources/assets/penumbra/textures/gui/screamer_host_9.png` | 512×512 RGB |

**Prompts résumés :**

- **Sceau de bannissement** — carte/parchemin pixel-art vieilli, spirale noire,
  charbon et
  cachet de cire rouge, isolé sur fond chroma vert, sans texte, logo,
  filigrane ni ombre extérieure.
- **Cavaleuse** — minuscule araignée cubique brun charbon, longues pattes fines
  et anguleuses, deux yeux ponctuels volontairement dépareillés, isolée sur
  fond chroma vert, sans gore, texte, logo ni filigrane.
- **Screamer 7** — très gros plan carré du visage-caméra du Projectionniste,
  objectif surexposé et bandes de film, sépia, scanlines et glitches VHS, sans
  texte ni filigrane.
- **Screamer 8** — très gros plan carré du Rampant, crâne minéral et arachnéen
  jaillissant d'une grotte noire, membres en étoile, gris/bleu froid et flou de
  mouvement, sans gore, texte ni filigrane.
- **Screamer 9** — très gros plan carré de L'Absent, visage de joueur presque
  humain dédoublé, yeux et sourires discordants, fond noir et glitches VHS,
  sans gore, texte ni filigrane.

Le script déterministe `tools/process_080_assets.py` utilise la graine
`0x0800CAFE`. Il détoure et réduit la source RGBA du Sceau de bannissement,
peint l'atlas UV de
la Cavaleuse d'après la palette et la planche de référence, puis recadre et
réduit les trois sources de screamer. Depuis la racine du projet :

```bash
python tools/process_080_assets.py
```

Le script réécrit uniquement les cinq sorties du tableau. La validation Gradle
contrôle les formats 64×64 du Sceau de bannissement et de la Cavaleuse, ainsi
que les neuf
screamers carrés 512×512.

## 11. Finales Rampant, Absent et Chœur — 0.9.0

Ces trois images carrées ont été créées avec l'outil **ImageGen intégré**, en
génération bitmap directe, puis réduites en 512×512 RGB avec Lanczos. Les
sources pleine résolution sont conservées afin que l'intégration soit
reproductible et auditable.

| Finale | Source ImageGen | Sortie intégrée |
|---|---|---|
| Rampant | `tools/image_sources/screamer_v10_rampant.png` | `src/main/resources/assets/penumbra/textures/gui/screamer_rampant.png` |
| Absent | `tools/image_sources/screamer_v11_absent.png` | `src/main/resources/assets/penumbra/textures/gui/screamer_absent.png` |
| Chœur | `tools/image_sources/screamer_v12_chorus.png` | `src/main/resources/assets/penumbra/textures/gui/screamer_chorus.png` |

**Prompts résumés :**

- **Rampant** — humanoïde cubique aux membres allongés rampant droit vers la
  caméra dans une grotte noire, visage minéral fissuré, lumière bleu-gris et
  liseré rouge, mouvement frontal, scanlines et déchirures VHS ; sans gore,
  texte, logo, interface ni filigrane.
- **Absent** — silhouette cubique trop proche dont le visage est un vide
  rectangulaire impossible, deux reflets lointains, parois aspirées vers lui,
  palette bleu-noir, double exposition et perte de tracking VHS ; sans gore,
  texte, logo, interface ni filigrane.
- **Chœur** — trois visages cubiques discordants envahissent les bords pendant
  qu'une quatrième silhouette reste au fond d'un tunnel, flash central,
  fisheye, séparation rouge/cyan et bruit de bande ; sans gore, texte, logo,
  interface ni filigrane.

Régénération depuis la racine, avec Pillow installé :

```bash
python tools/process_090_screamers.py
```

Le build contrôle l'existence, la lisibilité et les dimensions exactes des
trois sorties. Elles sont raccordées aux types `rampant_screamer`,
`absent_screamer` et `chorus_screamer`, et rejoignent aussi la rotation
générale des visages.

## Overlay de la caméra VHS

L’overlay 4:3 est dessiné **procéduralement** par le HUD client : cadre de
caméscope, REC, timecode, batterie, niveau de zoom, repères de mise au point,
scanlines et bruit VHS discret. Aucune texture d’overlay n’est requise ; les
animations gênantes sont neutralisées lorsque `flashesEnabled` est désactivé.

## §0.17.0 — L'Écouteur et les huit derniers placeholders

### `textures/entity/listener.png` — atlas 64×64

Peint par `tools/gen_listener_assets.py` (seed `1717`, sortie identique à chaque
exécution). Chaque `texOffs` correspond exactement à un `addBox` de
`ListenerModel.createBodyLayer()`. Empreinte d'une boîte `(w, h, d)` :
largeur `2d + 2w`, hauteur `d + h`. **Aucune zone ne se recouvre** :

| Pièce | texOffs | Boîte (w×h×d) | Zone occupée (x, y) |
|---|---|---|---|
| `head` | (0, 0) | 8×6×8 | 0–32, 0–14 |
| `right_ear` | (32, 0) | 1×7×6 | 32–46, 0–13 |
| `left_ear` | (46, 0) | 1×7×6 | 46–60, 0–13 |
| `jaw` | (0, 14) | 6×3×6 | 0–24, 14–23 |
| `neck` | (24, 14) | 3×3×3 | 24–36, 14–20 |
| `right_hand` | (36, 14) | 2×4×2 | 36–44, 14–20 |
| `left_hand` | (44, 14) | 2×4×2 | 44–52, 14–20 |
| `body` | (0, 24) | 8×18×4 | 0–24, 24–46 |
| `right_arm` | (24, 24) | 2×9×2 | 24–32, 24–35 |
| `right_forearm` | (32, 24) | 2×9×2 | 32–40, 24–35 |
| `left_arm` | (40, 24) | 2×9×2 | 40–48, 24–35 |
| `left_forearm` | (48, 24) | 2×9×2 | 48–56, 24–35 |
| `right_leg` (cuisse) | (0, 46) | 3×9×3 | 0–12, 46–58 |
| `right_leg/shin` | (12, 46) | 2×10×2 | 12–20, 46–58 |
| `left_leg` (cuisse) | (20, 46) | 3×9×3 | 20–32, 46–58 |
| `left_leg/shin` | (32, 46) | 2×10×2 | 32–40, 46–58 |

Hauteur totale du modèle : 9 (cuisse) + 10 (tibia) + 18 (torse) + 3 (cou) +
6 (tête) = **46 px = 2,875 blocs**, pour une hitbox de 2,9 — la plus haute
silhouette du mod. Palette dérivée de `generate_skins.py` mais volontairement
plus grise et plus froide : cette chose n'a jamais essayé d'imiter une couleur
de peau, seulement une silhouette.

Détails de lecture, dans l'ordre d'importance :

1. **Aucun œil.** Deux coutures horizontales sur la face avant de la tête, la
   droite un texel plus bas que la gauche, avec leurs points de suture.
2. **Pavillons** : face interne en cartilage presque translucide, trois nervures
   concentriques, un canal auditif noir en bas ; face externe plus sombre et
   veinée. Le bord (`top`) est le texel le plus clair de l'atlas.
3. **Mâchoire décrochée** : la face `top` du `jaw` est l'intérieur de la bouche,
   noire, cerclée de gencive. Deux dents inférieures dépassent sur la face avant.
4. **Torse** : col et bande de tunique turquoise déchirée en diagonale, côtes
   saillantes en dessous, couture de sternum verticale sur toute la hauteur.
5. **Membres** : une couture sombre à chaque articulation, mains à trois doigts
   trop longs terminés par un ongle pâle.

### `textures/gui/screamer_listener.png` — 1024×1024

Quinzième visage de la banque, et le premier **entièrement procédural** (les
quatorze autres viennent de planches IA repeintes). Construit en flottant par
composition d'ellipses perturbées angulairement (`blob()`), puis grainé :

- crâne décentré à 45,5 % de la largeur, contour irrégulier (cinq harmoniques),
  mâchoire resserrée de 34 % — jamais un œuf ;
- source de lumière unique par le bas : le front reste noir, la mâchoire brûle ;
- deux pavillons de tailles et d'inclinaisons **différentes** qui sortent du
  cadre, trois nervures concentriques chacun, canal auditif noir ;
- deux orbites noires cousues, avec le fil et ses neuf nœuds ;
- bouche béante asymétrique à lèvre éclairée, gorge à peine visible, cinq dents
  inférieures irrégulières ;
- marbrures de peau par bruit fractal à quatre octaves, grain argentique
  σ = 0,036, vignettage, courbe en S puis gain 1,30.

Le rendu reste lisible après le recadrage « cover » de `JumpscareManager` sur un
écran 21/9 : les éléments porteurs (orbites, bouche) sont dans les 60 % centraux.

### Les huit derniers placeholders d'objets — 16×16

Repeints par `tools/gen_narrative_items.py`. Grammaire commune : contour
`(18, 16, 18)`, trois valeurs de modelé, une seule source de lumière en haut à
gauche, et **un détail qui cloche par objet** :

| Fichier | Objet | Le détail qui cloche |
|---|---|---|
| `ash_sigil.png` | Sceau de cendre | Un éclat manquant en bas à droite, et de la cendre qui s'échappe encore |
| `blackened_plumb_bob.png` | Fil à plomb noirci | Le cordon ne pend pas droit |
| `impossible_photo.png` | Photo impossible | Une silhouette de trois texels au milieu du cadre |
| `incomplete_journal.png` | Journal incomplet | Trois cahiers arrachés, deux pages qui dépassent de travers |
| `observation_fragment.png` | Fragment d'observation | Quatre bâtons barrés, puis un cinquième qui s'arrête à mi-hauteur |
| `silhouette_glass.png` | Verre à silhouette | L'ombre est dans le verre, mais rien ne la projette |
| `cracked_lens.png` | Lentille fissurée | La fêlure traverse toute la surface utile |
| `memoire_disc.png` | Disque « Mémoire » | Une griffure : il a été joué trop souvent |

## §0.18.0 — Le Relevé

Tout est produit par `tools/gen_journal_assets.py` (seeds fixes, sortie
identique à chaque exécution).

### `textures/gui/journal_spread.png` — 512×288

Double page de parchemin. Construction, dans l'ordre : fond uni, 3 400 fibres
horizontales à alpha 26, vingt-deux auréoles d'humidité peintes sur un calque
séparé **flouté à 26 px** puis composé (une tache franche donnerait un fond
sale, pas du vieux papier), réglure du seul feuillet de droite, pliure centrale
en deux dégradés floutés, salissure des coins, et enfin un contour mangé
irrégulièrement de 0 à 3 px. Le feuillet de gauche reste vierge : c'est là que
l'écran colle les croquis.

### `textures/gui/journal_portraits.png` — 512×512

Atlas 4×4 de quinze croquis de 128×128, la seizième case restant vide. Style
commun : trait tremblé (`shaky()` applique ±1 px à chaque sommet), hachures
diagonales pour l'ombre, encre sépia `#2C2218`, fond transparent — c'est le
parchemin qui transparaît. Chaque sujet est réduit à ce que quelqu'un
dessinerait de mémoire, vite : l'Hôte à ses bras trop longs, l'Observateur à sa
masse noire, L'Écouteur à ses deux pavillons et ses orbites cousues, L'Absent à
deux silhouettes identiques dont une plus pâle, la dernière page à une margelle
et une corde qui s'arrête net.

L'ordre des cases suit exactement `JournalEntry.portraitIndex()` (0 à 14) ;
l'écran calcule ses UV en divisant par 4, il n'y a aucune table de correspondance
à maintenir.

### Objets 16×16

| Fichier | Sujet | Détail |
|---|---|---|
| `survey_journal.png` | Le Relevé | Cuir gonflé qui ne ferme plus, pages qui débordent, cordon dénoué, un tirage photographique glissé entre deux feuillets |
| `torn_page.png` | Page arrachée | Bord droit dentelé irrégulier, trois lignes d'écriture dont une coupée par la déchirure |

### Son

`page_turn.ogg` (0,36 s, -10 dBFS, `tools/gen_sounds_018.py`) : trois bouffées
de bruit filtré très courtes, la troisième plus aiguë — la page retombe sur la
tranche. Aucune réverbération : un carnet, ça ne sonne pas.
