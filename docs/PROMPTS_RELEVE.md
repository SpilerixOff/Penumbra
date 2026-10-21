# Le Relevé — croquis à l'IA et écriture manuscrite (0.18.0)

Deux chantiers indépendants, dans cet ordre de rentabilité :

1. **Les quinze croquis** — c'est ce qui change le plus l'écran. Les dessins
   actuels sont procéduraux : lisibles, mais naïfs.
2. **L'écriture manuscrite** — plus discret, mais c'est ce qui fait basculer
   l'objet de « interface de mod » à « carnet ».

---

# Partie A — Les quinze croquis

## Ce que le carnet attend exactement

| Contrainte | Valeur | Pourquoi |
|---|---|---|
| Format à générer | **carré**, 1024×1024 conseillé | recadré puis réduit en 128×128 |
| Fond | **blanc ou papier très clair** | il devient transparent à l'import |
| Trait | **noir ou gris foncé** | il devient l'encre sépia du carnet |
| Couleur | **aucune** | l'atlas est monochrome, la teinte est appliquée à l'import |
| Sujet | **centré, marge d'environ 10 %** | la case est carrée et serrée |

Ne demande **jamais** un fond transparent au modèle : il rend mal les PNG à
alpha. Demande du noir sur blanc, l'importateur fait la conversion — c'est
justement son travail.

## Bloc de style commun

À coller **au début de chaque prompt**. C'est lui qui garantit que les quinze
croquis semblent tenus par la même main.

> Croquis à la plume et à l'encre noire sur papier blanc, dessin de carnet de
> terrain du XIXᵉ siècle, trait unique nerveux et légèrement tremblé, ombres
> rendues uniquement par des hachures parallèles, aucune couleur, aucun aplat,
> aucun dégradé, aucun cadre, aucun texte, aucune annotation, aucune signature,
> fond blanc uni et vide. Sujet centré, isolé, occupant environ 80 % de l'image.
> Style volontairement imparfait, comme dessiné vite et de mémoire par un
> observateur épuisé — pas une illustration professionnelle.

**Interdictions à répéter en fin de prompt** (les modèles y reviennent tout
seuls) :

> Interdit : couleur, ombre portée, fond texturé, cadre, bordure, texte, lettres,
> chiffres, flèches, échelle, rendu 3D, photoréalisme, aplats noirs pleins.

## Les quinze prompts

Chaque numéro correspond à sa case dans l'atlas — nomme les fichiers `01.png` à
`15.png` et l'importateur les placera au bon endroit.

### 01 → `frontispiece` — Le Bûcher
> Une haute cheminée de pierre étroite, maçonnée à la main, vue de face, avec
> trois volutes de fumée qui s'échappent du sommet et s'écartent en montant.
> Une ligne d'horizon vide sous la base. Rien d'autre dans l'image.

### 02 → `host` — l'Hôte
> Une silhouette humanoïde debout, anormalement haute et maigre, la tête
> nettement trop petite pour le corps, les bras si longs que les mains
> descendent au niveau des genoux. Vue de face, immobile, sans visage dessiné.

### 03 → `watcher` — l'Observateur
> Une silhouette humaine entièrement remplie de hachures très serrées, presque
> noire, debout de face, jambes légèrement écartées, sans aucun détail de
> visage ni de vêtement. Une forme découpée, pas une personne.

### 04 → `scuttler` — la Cavaleuse
> Une créature basse et large à huit pattes fines et anguleuses, vue de
> trois-quarts, en pleine course vers la gauche, corps ovale aplati. Quelques
> traits de vitesse horizontaux derrière elle.

### 05 → `contrefait` — le Contrefait
> Une silhouette humanoïde mal proportionnée : une épaule beaucoup plus basse
> que l'autre, un bras trop court, un torse de travers, la tête penchée. Debout,
> instable, comme un mannequin mal monté.

### 06 → `rampant` — le Rampant
> Vue en contre-plongée d'une créature humanoïde plaquée à plat contre un
> plafond de pierre, membres écartés en X, tête pendant vers le bas et tournée
> vers l'observateur. Le plafond est suggéré par une ligne horizontale hachurée
> en haut de l'image.

### 07 → `projectionist` — le Projectionniste
> Une silhouette debout dont la tête est un boîtier d'appareil de projection :
> un objectif circulaire au centre, deux bobines de film rondes de part et
> d'autre. Deux traits fins divergents partent de l'objectif vers la droite.

### 08 → `faux_creature` — le Faux-Semblant
> Une tête de vache vue de face, dessinée avec soin, à ceci près qu'un œil est
> normal et que l'autre est beaucoup trop grand, parfaitement rond et fixe.
> Rien dans le reste du dessin ne signale l'anomalie.

### 09 → `absent` — L'Absent
> Deux silhouettes humaines identiques debout côte à côte, dans exactement la
> même pose. Celle de gauche est dessinée d'un trait ferme et appuyé, celle de
> droite d'un trait beaucoup plus léger, presque effacé.

### 10 → `listener` — L'Écouteur
> Une tête et un buste vus de face. Aucun œil : à leur place, deux coutures
> horizontales fermées par un gros fil, avec les points de suture visibles. De
> chaque côté du crâne, un immense pavillon d'oreille plat et ovale, plus large
> que le visage, nervuré de plis concentriques et percé d'un trou noir en son
> centre. La mâchoire pend, ouverte.

### 11 → `originel` — l'Originel
> Une silhouette humaine debout, de face, parfaitement centrée, dessinée
> uniquement par son contour extérieur — l'intérieur reste entièrement vide et
> blanc. Aucun visage, aucun détail, aucune hachure. Un cercle très fin l'entoure.

### 12 → `method_camera` — Le flash
> Un appareil photographique ancien à soufflet, vu de trois-quarts, avec un gros
> objectif rond et un flash à réflecteur au-dessus. Une douzaine de traits
> droits rayonnent du flash dans toutes les directions.

### 13 → `method_refuge` — Les refuges
> Deux petites maisons identiques côte à côte, dessinées de face de façon très
> simple. Celle de gauche a deux fenêtres, celle de droite en a trois. Rien
> d'autre ne les distingue.

### 14 → `method_stillness` — L'immobilité
> Une silhouette humaine debout, parfaitement immobile, bras le long du corps.
> Trois arcs de cercle concentriques passent de part et d'autre d'elle, à sa
> gauche et à sa droite, sans jamais la toucher — ils la contournent.

### 15 → `last_page` — La dernière page
> La margelle circulaire d'un puits de pierre, vue en légère plongée. Une corde
> descend dans l'ouverture et le trait s'interrompt net dans le noir. L'intérieur
> du puits est rempli de hachures très serrées.

## Import

```
python3 tools/import_journal_portraits.py <dossier>
```

Le script recadre au carré, convertit **luminance → alpha** (le blanc du papier
devient transparent, le noir devient l'encre), applique la teinte sépia du
carnet, réduit en 128×128 et colle dans la bonne case. L'ancien atlas est
sauvegardé dans `build/backup_portraits/`. Les cases sans fichier gardent leur
croquis actuel : tu peux donc remplacer les quinze un par un.

Deux réglages, si le rendu ne te plaît pas :

- **Traits qui disparaissent, hachures avalées** → baisse le seuil bas :
  `--low 0.18`
- **Fond gris sale, halo autour des traits** (typique d'un JPEG) → monte-le :
  `--low 0.38`
- **Trait mou, pas assez noir** → baisse le seuil haut : `--high 0.70`

Pour juger le résultat, regarde toujours l'atlas **sur un fond `#CEBE9E`** :
c'est la couleur exacte du parchemin derrière lui. Sur fond blanc ou damier,
tout paraît trop clair.

## Critères d'acceptation

- le fond est **totalement** transparent après import (aucun voile gris) ;
- le sujet se lit en une demi-seconde à 96 px de côté — c'est la taille réelle à
  l'écran, teste-la ;
- les quinze croquis semblent tenus par la **même main** : même épaisseur de
  trait, même densité de hachures ;
- rien ne ressemble à une illustration finie. Si c'est beau, c'est raté.

---

# Partie B — L'écriture manuscrite

Le carnet rend aujourd'hui son texte avec la police vanilla en italique. Le code
est déjà prêt pour une police manuscrite : tout le texte de l'écran passe par
une seule méthode (`ink()` dans `SurveyJournalScreen`), et il suffit d'activer
l'option pour basculer.

## Ce que Minecraft 26.1.2 accepte

Vérifié dans le jar : le type de fournisseur `ttf` existe toujours, avec les
champs `file`, `size`, `oversample`, `shift` et `skip`. Côté code,
`Style.withFont(FontDescription.Resource)` permet d'attacher une police à un
texte sans toucher au renderer — c'est ce que fait `ink()`.

## Choisir la police — la contrainte n'est pas le style, c'est la taille

Le texte du carnet est rendu à environ **9 pixels de haut**. À cette taille :

- une **cursive** (lettres attachées) devient illisible, quelle que soit sa
  beauté. Écarte-les toutes ;
- il te faut une **écriture manuscrite scriptée**, lettres détachées, avec des
  jambages courts et des contreformes larges.

Trois pistes, par ordre de pertinence pour un carnet d'arpenteur — **vérifie la
licence sur la page de chaque police avant de l'embarquer** :

| Police | Pourquoi elle convient | Le risque |
|---|---|---|
| **Architects Daughter** | Lettrage technique tracé à la main : c'est littéralement l'écriture d'un métier de relevé. Lettres détachées, très lisible en petit. | Un peu ronde, presque sympathique |
| **Kalam** | Plume à encre, épaisseur variable, détachée. Le plus proche de l'idée « carnet de terrain ». | Jambages un peu longs, à compenser par `shift` |
| **Special Elite** | Machine à écrire usée, encre irrégulière. Excellent rendu d'époque. | Ce n'est pas manuscrit — change le personnage d'Elias |

Évite : Caveat, Shadows Into Light, Homemade Apple, Dancing Script — trop
cursives ou trop enjouées pour quelqu'un qui écrit ça depuis quatre ans.

## Installation — sans recompiler le mod

La méthode la plus propre : un **pack de ressources**. Rien à rebuild, et tu
peux le retirer en un clic.

1. Crée `.minecraft/resourcepacks/Penumbra Encre/` avec dedans :

```
Penumbra Encre/
├── pack.mcmeta
└── assets/
    └── penumbra/
        └── font/
            ├── ink.json
            └── ink.ttf        ← ta police, renommée
```

2. `pack.mcmeta` :

```json
{
  "pack": {
    "pack_format": 88,
    "description": "Écriture manuscrite du Relevé"
  }
}
```

> `pack_format` doit correspondre à ta version. Si le pack apparaît en rouge
> dans la liste, corrige ce nombre — Minecraft affiche celui qu'il attend.

3. `assets/penumbra/font/ink.json` — le fichier est fourni prêt à copier dans
   `docs/templates/ink.font.json` :

```json
{
  "providers": [
    {
      "type": "ttf",
      "file": "penumbra:ink.ttf",
      "size": 11.0,
      "oversample": 2.0,
      "shift": [0.0, 0.0],
      "skip": ""
    }
  ]
}
```

4. Active le pack en jeu, puis mets dans `config/penumbra.json` :

```json
"journalHandwrittenFont": true
```

5. `/penumbra reload` recharge la configuration à chaud. Le pack de ressources,
   lui, demande `F3+T`.

## Régler la police

Les trois nombres du JSON font tout le travail :

- **`size`** — la hauteur de rendu. Commence à `11`. En dessous de `9` la
  plupart des manuscrites deviennent floues ; au dessus de `13` le texte ne
  tiendra plus sur le feuillet de droite (il déborde au lieu de revenir à la
  ligne, `Font.split` ne connaît pas la marge du parchemin).
- **`oversample`** — la finesse. `2.0` est le bon compromis ; `4.0` donne un
  trait plus net mais quadruple la taille de l'atlas de glyphes en mémoire.
- **`shift`** — `[horizontal, vertical]` en pixels. Une police à longs jambages
  paraîtra trop haute dans les lignes : `[0.0, 1.0]` la fait descendre d'un
  pixel et rétablit l'alignement sur la réglure.

Procède dans cet ordre : `size` d'abord jusqu'à ce que la ligne la plus longue
du frontispice tienne, `shift` ensuite pour l'asseoir sur la réglure,
`oversample` en dernier.

## Points de vigilance

- **Si le JSON existe mais que le TTF est absent**, Minecraft loggue une erreur
  de chargement de ressource et le texte du carnet devient illisible. Ce n'est
  pas instable, mais vérifie le log au premier lancement.
- **N'active `journalHandwrittenFont` qu'une fois les deux fichiers en place.**
  Tant que l'option est à `false`, le carnet utilise l'italique vanilla et rien
  ne peut casser.
- **Les accents comptent.** Le texte français utilise `é è ê à ç ô û ù ï «  »
  … —`. Vérifie que ta police les possède **tous** : une police anglophone
  incomplète affichera des carrés au milieu des phrases d'Elias. Teste sur la
  page `last_page`, c'est la plus chargée en typographie.
- **Licence.** Si tu redistribues le mod avec la police embarquée, la plupart
  des licences libres (OFL notamment) exigent que tu joignes le fichier de
  licence. La solution du pack de ressources séparé évite complètement la
  question.

## Aller plus loin, si tu y prends goût

Deux polices valent mieux qu'une : garde `ink` pour le corps du texte, et
déclare une seconde police plus grasse pour les titres. Il suffira d'ajouter un
second `FontDescription.Resource` dans `SurveyJournalScreen` et de l'appliquer
dans `drawLeftPage` — le titre est déjà isolé sur sa propre ligne de code.
