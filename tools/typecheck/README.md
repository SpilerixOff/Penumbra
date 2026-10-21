# Vérification de compilation hors ligne

Compile **tout** `src/main/java` contre le vrai jar Minecraft 26.1.2 sans aucun
accès à Maven, à Gradle ni à Internet.

```
bash tools/typecheck/verify.sh
```

## À quoi ça sert

`./gradlew build` a besoin de télécharger Fabric Loader, Fabric API, Loom et une
distribution Gradle. Quand ce réseau n'est pas disponible, il ne reste aucun
moyen de savoir si le code compile — et une erreur de compilation ne se voit
pas à la relecture. Ce banc comble exactement ce trou.

Il a déjà rattrapé de vraies erreurs, dont celle-ci, qui bloquait entièrement le
build : `SIGNAL_PYRE` avait été ajouté à `StructureType` en 0.18.0 sans être
câblé dans le `switch` exhaustif de `PenumbraStructures.build()`.

## Comment ça marche

Trois obstacles, trois contournements :

1. **Le jar de Loom est illisible par un javac 21.** Il est compilé en version de
   classe 69 (Java 25). Le script réécrit les deux octets de version de chacune
   des 10 682 classes pour les ramener en 65. Lire des signatures ne demande
   rien de plus, et c'est instantané.
2. **Fabric API, Brigadier, Gson, JOML, Mixin et SLF4J sont absents.** Le dossier
   `stubs/` contient 148 classes-doublures écrites à la main : les signatures
   réelles, des corps vides. Elles suffisent au contrôle de types.
3. **Java 25 n'est pas installé.** Le code n'utilise aucune syntaxe postérieure à
   21 ; le script récupère un JDK 21 si nécessaire.

## Ce que ça ne couvre pas

À lire avant de se croire à l'abri :

- **Ce n'est pas un build.** Aucun jar n'est produit. Un jar Fabric chargeable
  exige l'étape de remappage de Loom (mappings officiels → intermediary), qui
  n'est pas réimplémentable à la main.
- **Les doublures peuvent mentir.** Elles reproduisent les signatures Fabric
  telles que je les ai déduites du code et du jar. Si une signature réelle a
  changé, le banc dira « aucune erreur » et Gradle échouera quand même. En cas
  de doute sur une API Fabric, `javap` sur le vrai jar reste l'arbitre.
- **Les mixins ne sont pas validés.** Les annotations compilent, mais rien ne
  vérifie que la méthode ciblée existe : c'est le travail du processeur de
  mixins, au lancement du jeu.
- **Les ressources ne sont pas vérifiées** — langues, sons, textures, modèles.
  C'est le rôle de `validatePenumbraResources` côté Gradle.

## Quand mettre les doublures à jour

Quand une nouvelle API Fabric est utilisée dans le mod. Le symptôme est net :
`cannot find symbol` ou `class file for … not found` sur un paquet
`net.fabricmc.*`. Il suffit d'ajouter le fichier correspondant dans `stubs/`
avec la bonne signature.

Beaucoup de ces doublures sont des interfaces vides : Fabric « injecte » des
interfaces dans des classes vanilla (`FabricItemStack`, `FabricBlockState`,
`FabricResource`…), et javac a seulement besoin qu'elles existent pour résoudre
les supertypes.
