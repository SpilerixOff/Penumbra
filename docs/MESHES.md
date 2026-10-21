# Maillages 3D dans Penumbra (0.19.0)

Un système complet pour afficher de vrais maillages triangulés — importés depuis
Blender, Meshy, Blockbench ou n'importe quel logiciel 3D — à l'intérieur de
Minecraft. **Objets statiques uniquement** : pas d'animation squelettique, et
c'est un choix, pas une limite provisoire (voir « Pourquoi pas d'animation »).

## Comment ça marche

Minecraft 26.1 expose `SubmitNodeCollector.submitCustomGeometry(PoseStack,
RenderType, CustomGeometryRenderer)` — une porte de sortie officielle qui donne
accès à un `VertexConsumer` brut. Aucun mixin n'est nécessaire, et la géométrie
traverse exactement le même chemin que le reste du jeu : elle reçoit donc
l'éclairage, le brouillard et le tri de transparence sans effort.

Le chemin complet, en quatre classes :

| Classe | Rôle |
|---|---|
| `ObjLoader` | lit un fichier Wavefront `.obj` et le triangule |
| `ObjMesh` | le maillage en mémoire, à plat, sans allocation au rendu |
| `MeshLibrary` | cache par nom, chargement paresseux depuis les ressources |
| `MeshRenderer` | soumission au GPU via `submitCustomGeometry` |

Et un support pour les poser dans le monde :

| Classe | Rôle |
|---|---|
| `MeshPropEntity` | entité de décor inerte, porte un nom de maillage |
| `MeshPropRenderer` | assemble le tout |

## Ajouter un maillage

Deux fichiers, aucun code :

```
assets/penumbra/meshes/<nom>.obj
assets/penumbra/textures/entity/prop/<nom>.png
```

Puis en jeu : `/penumbra prop <nom> [échelle en %]`

L'accessoire se pose deux blocs devant toi, tourné vers toi, et se sauvegarde
avec le monde. Un joueur en créatif le retire d'un coup.

**Un pack de ressources peut ajouter ses propres maillages** : les fichiers
passent par le gestionnaire de ressources vanilla. C'est ce qui rend le système
utilisable par quelqu'un d'autre que nous.

## Ce que le chargeur accepte

Lu : `v`, `vt`, `vn`, `f`. Les faces à plus de trois sommets sont triangulées en
éventail. Les index négatifs (comptés depuis la fin) fonctionnent.

Ignoré : matériaux, groupes, lignes, surfaces paramétriques. Un maillage porte
**une seule texture**, parce que Minecraft dessine une passe par texture :
plusieurs matériaux signifieraient découper le maillage en autant de
soumissions, pour un gain nul sur du décor.

Tolérances utiles :

- **Sans `vt`**, l'objet reçoit des UV nuls et prend la couleur du premier texel.
- **Sans `vn`**, les normales sont recalculées par produit vectoriel. C'est le
  défaut le plus fréquent des fichiers trouvés en ligne, et il est corrigé
  automatiquement — l'éclairage reste juste.
- Une ligne malformée est ignorée sans faire échouer le chargement. Un modèle de
  décor ne doit jamais empêcher le jeu de démarrer.
- Au-delà de 200 000 triangles, le fichier est refusé.

## Conventions à respecter dans ton logiciel 3D

1. **1 unité = 1 bloc.** Un cube d'un mètre dans Blender fera un bloc en jeu.
2. **Pose l'objet sur le plan Y = 0.** Le renderer ne recentre rien : l'origine
   du fichier est le point d'ancrage au sol.
3. **+Y vers le haut**, comme Blender et Minecraft. Rien à convertir.
4. **Une seule texture**, en PNG, avec un canal alpha si tu veux des découpes
   (le type de rendu est `entityCutout` : un pixel est opaque ou absent, pas de
   semi-transparence).
5. **Triangule avant d'exporter** si tu utilises des n-gones concaves — la
   triangulation en éventail du chargeur suppose des polygones convexes.

L'inversion de l'axe V est faite par le chargeur : n'inverse rien toi-même,
sinon tes textures seront à l'envers.

## Un détail qui surprend à la lecture du code

Les types de rendu d'entité de Minecraft travaillent en **quads**, quatre
sommets par face. `MeshRenderer` émet donc chaque triangle avec son troisième
sommet **répété** — un quad dégénéré. Le GPU le traite comme un triangle d'aire
nulle, invisible et gratuit. C'est la méthode standard, mais elle déroute quand
on la découvre.

## Pourquoi pas d'animation

Charger un maillage et l'envoyer au GPU, c'est trois cents lignes. L'animer, non.
Il faudrait implémenter le *skinning* : chaque sommet influencé par plusieurs os
avec des poids, recalculé à chaque image. C'est un morceau de moteur de jeu.

Et surtout, ce ne serait pas un progrès pour Penumbra : les créatures du mod
utilisent l'animation procédurale de `MonsterAnimationMath` (respiration
organique, tics, balayage de tête), qui a l'avantage décisif de **ne jamais
boucler à l'identique**. Un maillage figé qui glisse sur le sol serait un net
recul par rapport à une silhouette en boîtes qui respire.

Le partage est donc : **boîtes animées pour ce qui vit, maillages pour ce qui ne
bouge pas** — statues, machines, épaves, objets narratifs.

## Le maillage de démonstration

`tools/gen_effigy_mesh.py` produit `effigy.obj` : une pierre dressée de deux
blocs, taillée en silhouette (socle, fût fuselé, épaules, cou étranglé, tête),
tournée de 34° sur sa hauteur, en 464 triangles. Elle est faite pour prouver le
pipeline d'un coup d'œil : faces obliques et torsion continue sont impossibles
avec des boîtes alignées sur les axes.

Le fichier est exporté **sans normales**, volontairement — ça teste le chemin de
recalcul du chargeur.
