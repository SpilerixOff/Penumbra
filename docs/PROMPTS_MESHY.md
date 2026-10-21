# Textures d'entités haute densité depuis Meshy (0.19.0)

Le principe : Meshy sert de **peintre**, pas de modeleur. Tu génères la créature
en 3D, tu en sors six vues orthographiques, et `tools/bake_entity_texture.py`
projette ces vues sur les faces des boîtes du modèle Minecraft. Le squelette,
l'animation et l'identité du mod restent intacts ; seule la peau change.

Résultat honnête : une silhouette en boîtes portant une texture de très haute
définition. Ce n'est pas un maillage 3D — mais de loin, en mouvement, dans le
noir, la différence ne se voit pas, et le mod garde ses animations.

---

## La contrainte qui décide de tout : les proportions

La projection suppose que **ton rendu a exactement les proportions du modèle**.
Si Meshy produit un humanoïde normal alors que L'Écouteur a des bras qui
descendent aux genoux et une tête surmontée de deux paraboles, la peinture se
retrouvera étirée n'importe comment.

Chaque prompt ci-dessous décrit donc les proportions **en unités de modèle**
(1 unité = 1 pixel Minecraft, 16 unités = 1 bloc). Ne les reformule pas.

Proportions de L'Écouteur, pour référence — hauteur totale 46 unités :

| Partie | Dimensions | Position (Y=0 aux pieds, vers le haut) |
|---|---|---|
| tête | 8 × 6 × 8 | 40 → 46 |
| pavillons d'oreille | 1 × 7 × 6 chacun | 43 → 50, de part et d'autre |
| mâchoire | 6 × 3 × 6 | 38 → 41, pendante |
| torse | 8 × 18 × 4 | 19 → 37 |
| bras (3 segments) | 2 × 22 × 2 | 35 → 13, mains aux genoux |
| jambes (2 segments) | 3 × 19 × 3 | 0 → 19 |

Autrement dit : **très maigre** (torse deux fois plus profond que large en
proportion humaine normale), **bras d'un quart plus longs qu'un humain**, et une
tête large surmontée d'oreilles qui doublent sa largeur.

---

## Protocole de rendu — la partie qui fait rater ou réussir

Six images, dans un dossier, nommées exactement :

```
front.png   back.png   left.png   right.png   top.png   bottom.png
```

Cinq règles, dans l'ordre d'importance :

1. **Caméra orthographique.** Pas de perspective. Dans Meshy, c'est le mode de
   prévisualisation « orthographic » ; si tu ne l'as pas, exporte le `.glb` et
   fais les rendus dans Blender avec une caméra en mode Orthographic. Une vue en
   perspective donne des faces déformées qui ne se raccordent pas.
2. **Fond transparent.** Le script s'en sert pour détecter le sujet. Un fond
   opaque marche aussi (il détecte alors par contraste avec la couleur du coin),
   mais c'est moins fiable.
3. **Aucune ombre portée, aucun sol.** L'ombre serait peinte dans la texture et
   se retrouverait figée sur la créature.
4. **Éclairage plat et frontal**, identique sur les six vues. Le mod fait son
   propre éclairage : toute ombre gravée dans la texture se battra avec lui.
   Concrètement, dans Meshy : désactive l'éclairage d'environnement et ne garde
   qu'une lumière frontale douce.
5. **Cadrage constant.** Le sujet doit occuper la même place dans les six
   images. Le script recadre lui-même sur la boîte englobante détectée, donc un
   petit écart passe — mais si `front.png` est serré et `left.png` est large, les
   faces ne se raccorderont pas aux arêtes.

Taille conseillée : **1024 × 1024** par vue. Au delà c'est inutile, l'atlas final
fait 256 × 256.

Tu n'es pas obligé de fournir les six. `front` et `left` suffisent pour un
premier essai : les faces sans vue gardent la texture actuelle.

---

## Bloc de style commun

À coller au début de chaque prompt Meshy.

> Créature humanoïde d'horreur, style réaliste sobre, peau cireuse gris-beige
> mate comme du vieux papier huilé, marbrures et taches de vieillissement, fines
> coutures de couture visibles là où les membres se rattachent, lambeaux d'un
> tissu turquoise très délavé. Palette entièrement désaturée, aucune couleur
> saturée, aucun métal, aucun élément brillant. Texture mate, sans reflets
> spéculaires. Pose en T, symétrique, bras à l'horizontale.

**Précision importante :** demande une **pose en T** (T-pose). C'est la seule
pose qui permet une projection propre — une pose dynamique peindrait un bras
plié sur la face d'une boîte droite.

---

## Les prompts

### L'Écouteur — `listener`

> \[bloc de style commun]
>
> Humanoïde extrêmement maigre et très haut, proportions non humaines : le torse
> fait deux fois et demie la hauteur de la tête et reste très plat, les bras
> sont un quart plus longs que ceux d'un humain et les mains arrivent au niveau
> des genoux, les jambes sont fines et en deux segments avec le genou orienté
> vers l'arrière.
>
> **Aucun œil.** À leur place, deux fentes horizontales où la peau a été tirée
> puis cousue avec un gros fil pâle, points de suture nettement visibles, la
> couture droite un peu plus basse que la gauche.
>
> De chaque côté du crâne, **deux pavillons d'oreille démesurés**, plats,
> ovales, aussi larges que le visage entier, ouverts vers l'avant comme des
> paraboles, nervurés de plis concentriques et percés d'un canal noir en leur
> centre. Ils dépassent du sommet du crâne.
>
> **Mâchoire décrochée** qui pend ouverte en permanence, quelques dents
> inférieures irrégulières. Mains à trois doigts trop longs, ongles pâles.

### L'Hôte — `host`

> \[bloc de style commun]
>
> Humanoïde pâle et haut, presque humain mais faux : la tête est nettement trop
> petite pour le corps, le cou est étiré et trop fin, l'épaule droite pend un
> peu plus bas que la gauche. Bras en trois segments, mains aux doigts trop
> longs qui frôlent les cuisses. Visage aux orbites très creuses et sombres avec
> un minuscule point d'iris pâle, bouche fine trop large qui tombe d'un côté.

### L'Observateur — `watcher`

> \[bloc de style commun], mais **entièrement noir mat** au lieu de la peau
> cireuse.
>
> Silhouette humaine debout, uniformément noire, sans aucun détail de visage ni
> de vêtement — une découpe, pas une personne. Proportions humaines normales,
> légèrement plus large d'épaules. Aucune texture, aucun relief : une masse.

### L'Absent — `absent`

> \[bloc de style commun]
>
> Humanoïde aux proportions **exactement humaines**, vêtu de vêtements simples et
> usés. La peau et le visage sont normaux sur la moitié gauche du corps ; sur la
> moitié droite, la peau se déchire et se décolle par plaques, révélant en
> dessous une matière cireuse grise et sans traits. La déchirure suit une ligne
> verticale nette au milieu du visage et du torse.

### Le Rampant — `rampant`

> \[bloc de style commun]
>
> Humanoïde émacié à quatre membres anormalement longs et fins, articulés comme
> ceux d'une araignée : les coudes et les genoux pointent vers le haut quand le
> corps est à plat. Torse court et creusé, côtes très saillantes. Tête petite,
> tournée à 180 degrés vers l'arrière. Doigts terminés par des griffes plates
> faites pour s'accrocher à la pierre.

### Le Contrefait — `contrefait`

> \[bloc de style commun], mais la peau tire sur un **vert malade** au lieu du
> gris-beige.
>
> Humanoïde manifestement raté : une épaule beaucoup plus basse que l'autre, un
> bras plus court que l'autre, le torse de travers, la tête inclinée en
> permanence. Les coutures sont grossières et mal serrées, certaines ont lâché.
> L'ensemble donne l'impression d'un premier essai que personne n'a jeté.

### Le Projectionniste — `projectionist`

> \[bloc de style commun], plus des éléments d'**appareillage en laiton terni**.
>
> Humanoïde debout dont la tête est remplacée par un boîtier d'appareil de
> projection : un gros objectif circulaire au centre du visage, deux bobines de
> film rondes de part et d'autre du crâne. Le corps reste humanoïde, maigre,
> vêtu d'une longue blouse sombre en lambeaux.

### La Cavaleuse — `scuttler`

> \[bloc de style commun], mais en **chitine noire mate** au lieu de la peau.
>
> Petite créature arachnide très basse et large, corps ovale aplati, huit pattes
> fines et anguleuses. Aucune tête distincte. Environ la taille d'un chat.

### L'Originel — `originel`

> \[bloc de style commun], mais la peau est **momifiée** : brun-ocre desséché,
> craquelée en profondeur.
>
> Humanoïde aux proportions humaines parfaitement régulières — c'est le seul de
> la série qui soit exactement proportionné. Visage entier mais lisse, sans
> aucun trait creusé, comme poli par le temps. Vêtements réduits à des bandes de
> tissu turquoise très ancien.

---

## Le passage en jeu

Depuis la racine du projet :

```
python3 tools/bake_entity_texture.py listener path/to/meshy_listener --preview
```

Le script affiche l'emprise du modèle, les vues trouvées et la taille du sujet
détecté dans chacune, puis écrit directement
`src/main/resources/assets/penumbra/textures/entity/listener.png` en 256 × 256.
L'option `--preview` produit une version sur fond sombre pour juger le résultat.

Vérifie une chose dans la sortie : **la taille du sujet détecté doit être
proche sur les six vues**. Si `front` fait 800 × 950 px et `left` 400 × 950, tes
cadrages ne concordent pas et les arêtes ne se raccorderont pas.

### Pour l'instant, seul L'Écouteur est prêt

Le script contient la table de géométrie de `listener` uniquement, et seul
`ListenerModel` a été passé en haute densité (`TEX_SCALE = 0.25`, atlas 256).
Pour étendre à une autre créature il faut deux choses :

1. ajouter sa table de boîtes dans `MODELS` (positions en coordonnées monde,
   hiérarchie résolue) ;
2. dans son modèle Java, ajouter `CubeDeformation.NONE, TEX_SCALE, TEX_SCALE` à
   chaque `addBox` et passer `LayerDefinition.create(mesh, 256, 256)`.

Dis-le-moi et je le fais pour celles que tu veux.

---

## Comment ça marche, si tu veux comprendre

Vérifié dans le bytecode de `CubeDefinition.bake` en 26.1.2 : la taille de
texture transmise au cube vaut `atlas × texScale`, et `ModelPart.Polygon` divise
ses coordonnées UV par cette valeur. Une échelle de `0.25` avec un atlas de 256
se comporte donc, pour le calcul des UV, comme un atlas de 64 — mais chaque face
occupe quatre fois plus de texels réels.

Conséquence pratique : **les `texOffs` du code Java restent inchangés**, exprimés
dans l'ancien repère 64 × 64. La carte UV documentée dans `docs/ASSETS.md` reste
donc valable telle quelle ; il suffit d'y multiplier chaque coordonnée par
quatre pour trouver la zone correspondante dans le fichier réel.

C'est pour ça que la migration coûte deux lignes par modèle et ne casse rien.
