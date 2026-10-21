# Fabriquer des décors 3D pour Penumbra

Tout ce qu'il faut pour transformer une idée en objet posé dans le monde :
les réglages de Meshy, les prompts prêts à copier, et ce qui se passe ensuite.

Le chemin complet fait quatre étapes :

```
   Meshy  →  téléchargement .zip  →  dossier « maillages »  →  /penumbra prop
   (toi)        (toi)                    (toi)                    (toi, en jeu)
                                    (moi : normalisation)
```

Tu n'as jamais de ligne de commande à taper. Tu déposes le dossier téléchargé,
tu me le dis, je fais la conversion et je te rends les fichiers installés.

---

## 1. La règle qui change tout

**N'écris jamais le mot « horreur » dans un prompt 3D.**

C'est contre-intuitif, mais c'est de loin l'erreur la plus coûteuse. Les
générateurs 3D ont appris « creepy », « scary », « horror » sur des couvertures
de jeux d'arcade et des figurines d'Halloween. Demande un « creepy statue » et
tu obtiens une gargouille avec des dents, en résine brillante, qui a l'air de
sortir d'un magasin de farces et attrapes.

La peur, dans Penumbra, ne vient jamais de l'objet lui-même. Elle vient de son
**emplacement**. Une chaise de cuisine parfaitement ordinaire, seule, au milieu
d'une forêt, à quatre cents blocs de toute habitation, fait un effet que jamais
aucune statue à crocs ne fera. C'est ton mod qui pose la chaise là — le
générateur n'a qu'un seul travail : fabriquer une chaise crédible.

Donc : **décris l'objet, son matériau et son usure. Rien d'autre.** Pas
d'ambiance, pas d'adjectif émotionnel, pas de décor autour.

Trois autres règles, plus techniques :

- **Un seul objet par prompt.** « a chair and a table » produit un bloc unique où
  les deux se touchent, impossible à séparer.
- **Pas de décor.** « in a dark forest » fait fabriquer le sol de la forêt,
  collé sous l'objet.
- **Nomme le matériau tôt.** C'est le mot qui pilote le plus la texture :
  *weathered oak*, *rusted iron*, *grey limestone*, *waxed canvas*, *cast brass*.

---

## 2. Réglages dans Meshy

L'interface bouge d'une version à l'autre — cherche l'option par sa fonction
plutôt que par sa position exacte à l'écran.

| Réglage | Ce qu'il faut | Pourquoi |
|---|---|---|
| Mode | **Text to 3D**, ou **Image to 3D** (voir §4) | |
| Style | **Realistic** | « Sculpture » sort du plâtre lisse sans texture utile |
| Symétrie | **Off** pour tout ce qui est cassé, penché, usé | La symétrie donne un objet neuf, et le neuf ne fait pas peur |
| Topologie | **Triangle** | Le format final n'accepte que des triangles ; autant les avoir tout de suite |
| Nombre de polygones | **le plus bas proposé**, ou ~10 000 | Voir plus bas |
| Texture | **activée**, PBR inutile | Le mod n'utilise que la couleur de base |
| Format d'export | **OBJ** | Le seul que le mod lit. Pas GLB, pas FBX |

**Sur le nombre de polygones.** Le chargeur accepte jusqu'à 200 000 triangles,
mais c'est une limite de sécurité, pas une cible. Un décor de Minecraft est
parfaitement lisible entre 3 000 et 15 000 triangles, et au-delà tu paies de la
mémoire pour des détails que la texture 1024×1024 ne pourra de toute façon pas
montrer. Baisse le curseur dans Meshy — c'est toujours mieux que de faire
alléger le fichier après coup.

**Sur l'export.** Meshy propose souvent de télécharger un `.zip`. Prends-le tel
quel, ne l'ouvre pas pour trier : il contient le `.obj`, un `.mtl` et plusieurs
images, et j'ai besoin de l'ensemble — le `.mtl` est justement ce qui me dit
laquelle des images est la vraie texture, et pas la carte de normales.

---

## 3. Les prompts

Douze objets, écrits pour s'accrocher à ce que le mod raconte déjà. Copie le
bloc anglais tel quel.

### Les traces d'Elias Warrick

Warrick était arpenteur. Le Relevé le dit, mais on ne voit jamais son métier.
Ces trois objets le rendent visible sans une ligne de texte.

**`theodolite` — 1,6 bloc** · le plus fort du lot : un instrument de mesure
abandonné là où il travaillait.

```
antique brass surveyor's theodolite on a wooden tripod, heavily tarnished,
green oxidation, one leg splintered, leather strap hanging loose, standing upright
```

**`borne` — 1,1 bloc** · une borne d'arpentage plantée au milieu de nulle part,
qui numérote un territoire que personne n'a jamais habité.

```
weathered grey limestone survey marker post, rectangular, carved numerals worn
smooth, lichen patches, tilted, chipped top edge
```

**`campement` — 0,5 bloc** · ses affaires, restées sur place.

```
old waxed canvas rucksack lying on its side, worn leather straps, a pair of
cracked leather boots beside it, a dented tin cup, single grouped object
```

### Ce que quelqu'un a construit ici

**`effigie` — 2,2 blocs** · la version en branches. Elle s'ajoute à l'effigie de
pierre de démonstration (`effigy`), qui reste en place : c'est elle qui sert de
test de référence du système.

```
crude human effigy made of bundled dry branches lashed with rough twine,
roughly humanoid, no face, weathered pale wood, standing upright
```

**`cairn` — 1,3 bloc** · un empilement volontaire. Quelqu'un a pris le temps.

```
stacked cairn of flat grey river stones, uneven, moss between the stones,
a few pale animal bones wedged in the gaps, narrow and tall
```

**`croix` — 1,4 bloc** · un marqueur de tombe, sans nom dessus.

```
rough wooden grave marker cross, two split planks nailed crooked, grey weathered
timber, a strip of faded cloth knotted around the joint, leaning
```

### Le mobilier hors contexte

Le cœur de l'affaire. Des objets d'intérieur, parfaitement ordinaires, que le
mod ira poser dehors.

**`chaise` — 1,1 bloc** · pose-la dos au joueur, face à un mur ou à un arbre.

```
plain wooden kitchen chair, worn oak, one back slat missing, scuffed seat,
dull matte finish, standing upright, empty
```

**`porte` — 2,3 blocs** · un chambranle isolé, porte fermée. Rien derrière.

```
single closed interior door in its wooden frame, peeling white paint, brass
handle, no walls attached, freestanding, upright
```

**`miroir` — 2,0 blocs** · celui-là est spécial : le mod l'assombrit à l'affichage.

```
tall standing floor mirror in a carved dark wood frame, glass cracked from one
corner, dusty surface, a grey dust sheet half draped over the top
```

### L'organique

**`nid` — 0,7 bloc** · pas un nid d'oiseau. Trop grand, mauvais matériaux.

```
large woven nest made of dry grass, tangled hair, torn cloth strips and small
bones, roughly circular, matted, hollow center
```

**`souche` — 1,0 bloc** · une souche dont le veinage ressemble un peu trop à un
visage. Le prompt ne dit jamais « visage » : il décrit deux creux et une fente.

```
old tree stump, cut flat on top, thick bark, two deep round hollows in the
trunk and a horizontal split below them, exposed pale grain, roots visible
```

**`poupee` — 1,5 bloc** · une poupée de chiffon à taille d'enfant, assise.

```
large child sized rag doll sitting upright, coarse burlap body, oversized visible
stitching, button eyes, faded striped cloth dress, limp arms, stained fabric
```

---

## 4. La voie plus précise : image vers 3D

La génération à partir de texte te donne ce que le modèle a compris. La
génération à partir d'une **image** te donne ce que tu as choisi. Sur un objet
qui compte — le théodolite, le miroir — ça vaut les cinq minutes de plus.

**Étape 1.** Fabrique l'image dans GPT Image 2, avec ce gabarit :

```
Product photograph of [OBJET], centered, complete object fully visible,
three-quarter view, plain flat neutral grey background, even diffuse studio
lighting, no shadows on the background, no other objects, no text, sharp focus,
photorealistic
```

Remplace `[OBJET]` par la description anglaise du prompt correspondant §3.

Les cinq contraintes qui comptent, et pourquoi : **fond gris uni** (un fond
détaillé se retrouve modélisé en 3D), **objet entier visible** (ce qui est hors
cadre est inventé), **lumière diffuse** (une ombre marquée se retrouve *peinte*
dans la texture, et l'objet aura une ombre fausse en jeu), **aucun autre objet**,
**trois quarts** (une vue strictement de face ne donne aucune information de
profondeur).

**Étape 2.** Charge l'image dans Meshy, mode Image to 3D. Mêmes réglages qu'au §2.

---

## 5. Ce que tu fais du téléchargement

Glisse le dossier ou le `.zip` dans :

```
kimi\workspace\maillages\
```

Un sous-dossier par objet, nommé comme tu veux l'objet en jeu — `theodolite`,
`chaise`, `miroir`. C'est ce nom qui deviendra la commande.

Puis dis-le-moi. Je lance la normalisation et je te donne le résultat.

**Ce que la normalisation corrige**, parce qu'aucun fichier n'arrive utilisable :

1. Le modèle sort à une échelle arbitraire → il est mis à la hauteur en blocs
   que tu veux.
2. Il flotte au-dessus de son origine → son point le plus bas est ramené
   exactement au niveau du sol, sinon la statue lévite.
3. Il est décentré → recentré, sinon il se pose à côté de l'endroit visé.
4. Il contient des quadrilatères et des n-gones → tout est découpé en triangles.
5. Il traîne un `.mtl`, des groupes, des lignes → retirés.
6. Sa texture s'appelle `Model_0_texture_diffuse.png` et fait 2048×2048 → elle
   devient `<nom>.png`, en 1024×1024.
7. Il traîne des sommets que plus aucune face n'utilise → supprimés.

Si tu veux le lancer toi-même un jour, c'est une seule ligne :

```
python3 tools/import_mesh.py maillages/theodolite theodolite --hauteur 1.6
```

Options : `--tout` pour traiter tous les sous-dossiers d'un coup, `--axe z` si
l'objet arrive couché (voir §7), `--reduire 10000` si le fichier est trop lourd.

---

## 6. En jeu

```
/penumbra prop theodolite
/penumbra prop theodolite 150      ← une fois et demie plus grand
```

L'objet se pose deux blocs devant toi, tourné vers toi, et il est sauvegardé
avec le monde. En créatif, un coup dessus le retire.

---

## 7. Quand ça rate

**L'objet est couché sur le côté.** Le fichier était en Z-haut (fréquent chez
Sketchfab, 3ds Max, les modèles de CAO). Dis-le-moi, je relance avec `--axe z`.
Tu le repères dans le rapport d'import : si les dimensions annoncent *0,6 haut ×
2,0 profond* pour une statue, elle est couchée.

**L'objet est tout noir, ou d'une seule couleur.** Le modèle n'a pas de
coordonnées de texture. Ça arrive quand Meshy s'est arrêté à l'étape de
prévisualisation : il faut passer l'objet à l'étape suivante, celle qui ajoute
la texture, avant d'exporter.

**L'objet est violet fluo.** La carte de normales a été prise pour la texture.
Ça ne devrait pas arriver — le `.mtl` est lu en priorité justement pour ça —
mais si le `.zip` est arrivé sans `.mtl`, dis-le-moi et je désignerai la bonne
image à la main.

**L'objet a un socle carré collé sous lui.** Le prompt contenait un mot de
décor, ou l'image de départ avait un sol visible. Reformule sans le décor, ou
recadre l'image sur l'objet seul.

**Il manque un morceau, ou une partie est fondue dans une autre.** C'est la
limite de la génération 3D actuelle sur les objets fins — pieds de trépied,
barreaux de chaise, poignées. Deux solutions : relancer avec une autre variante
(Meshy en propose plusieurs), ou passer par l'image (§4), qui tient beaucoup
mieux les détails fins.

**Le jeu rame quand l'objet est à l'écran.** Regarde le nombre de triangles dans
le rapport d'import. Au-dessus de 40 000, c'est trop pour du décor : réexporte
plus léger depuis Meshy. L'allègement automatique existe (`--reduire`) mais il
est grossier et fait baver les coutures de la texture — c'est un secours, pas
une méthode.

---

## 8. Ce qui ne marchera pas, et pourquoi

**Un personnage animé.** Le système affiche des objets **statiques**. Animer un
maillage demande d'implémenter le *skinning* — chaque sommet influencé par
plusieurs os avec des poids, recalculé à chaque image — soit un morceau de
moteur de jeu. Et ce ne serait pas un progrès : les créatures du mod utilisent
l'animation procédurale de `MonsterAnimationMath`, qui a l'avantage décisif de
**ne jamais boucler à l'identique**. Un maillage figé qui glisse sur le sol
serait un net recul par rapport à une silhouette en boîtes qui respire.

**Un objet en verre ou en fumée.** Le type de rendu est `entityCutout` : un
pixel est opaque ou absent, il n'y a pas de demi-transparence. Une vitre sera
soit un mur plein, soit un trou.

**Deux matériaux sur le même objet.** Un maillage porte une seule texture, parce
que Minecraft dessine une passe de rendu par texture. Ce n'est pas gênant en
pratique : la texture générée par Meshy contient déjà le bois *et* le métal,
peints au bon endroit.

Voir `docs/MESHES.md` pour le fonctionnement interne du système.
