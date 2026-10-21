# Compiler Penumbra 0.19.0 depuis les sources

## Prérequis

- JDK 25 installé ; `java -version` doit afficher une JVM 25.
- Accès initial aux dépôts Gradle, Mojang et Fabric pour remplir le cache.
- Aucun Gradle système n'est requis : le wrapper 9.6.1 est fourni.

Le projet ne fixe aucun chemin de JDK propre à une machine. Si la détection de
toolchain ne suffit pas, définissez `JAVA_HOME` dans votre shell, sans modifier
le fichier versionné `gradle.properties`.

## Toolchain verrouillée

| Composant | Version |
|---|---|
| Gradle wrapper | 9.6.1 |
| Fabric Loom | 1.17.16 |
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.155.2+26.1.2 |
| Java | 25 |
| JUnit Jupiter | 5.13.4 |

Toutes ces valeurs, y compris `mod_version=0.19.0`, viennent de
`gradle.properties`. `processResources` injecte les mêmes valeurs dans
`fabric.mod.json`, ce qui empêche un nom de JAR et une version interne
différents.

## Commandes

Depuis la racine du projet :

```bash
# Linux / macOS / Git Bash
./gradlew validatePenumbraResources test build

# Windows (cmd / PowerShell)
.\gradlew.bat validatePenumbraResources test build
```

Une fois les dépendances présentes dans le cache, ajoutez `--offline` pour une
validation sans réseau. La tâche `check` lance aussi automatiquement les tests
JUnit et `validatePenumbraResources`.

La livraison est produite dans :

```text
build/libs/penumbra-0.19.0.jar
build/libs/penumbra-0.19.0-sources.jar
```

## Lancements de développement

```bash
./gradlew runClient
./gradlew runServer
```

Le monde, la configuration et les journaux de développement sont placés dans
`run/`. Avant une release, effectuer au minimum un démarrage client, un
démarrage serveur dédié et les tests du protocole `TEST_PROTOCOL.md`.

## Notes

- Minecraft 26.1 est utilisé dans son namespace officiel non obfusqué ; le
  bloc `loom {}` ne déclare donc pas de couche Yarn.
- Les sources et le bytecode ciblent Java 25.
- `validatePenumbraResources` parse tous les JSON, compare les clés FR/EN,
  contrôle manifestes/sous-titres/fichiers OGG et vérifie les textures des
  modèles d'objets. Elle impose aussi les quinze screamers 1024×1024,
  les atlas d'entités (64×64, Écouteur 256×256), l'icône 64×64 du Sceau de bannissement et la
  double exposition 128×72 de L'Absent avec sa transparence.
- Les textures 0.8.0 sont déjà versionnées. Pour les régénérer depuis les
  sources de travail ImageGen, installez Pillow puis exécutez
  `python tools/process_080_assets.py` ; le script utilise une graine fixe et
  réécrit uniquement les cinq sorties décrites dans `docs/ASSETS.md`.
- Les trois finales 0.9.0 se régénèrent avec
  `python tools/process_090_screamers.py`.
- Le warning de dépréciation restant dans l'enregistrement de renderer client
  est non bloquant ; utiliser `-Xlint:deprecation` avant une future migration
  Fabric.
