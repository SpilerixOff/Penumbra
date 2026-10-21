# Penumbra

> Something is learning to be you.

Penumbra is a psychological horror mod for **Minecraft 26.1.2 on Fabric**. A persistent horror director builds tension from in-game events, then stages sounds, apparitions, pursuit and discoveries around each player.

**Status: 0.19.0, early public testing.** Maintained by [SpilerixOff](https://github.com/SpilerixOff). On **21 October 2026**, the maintainer confirmed having tested solo and multiplayer; **208 automated tests passed** during release preparation. Feedback on longer sessions and different mod combinations is welcome.

**[Download Penumbra 0.19.0](https://github.com/SpilerixOff/Penumbra/releases/tag/v0.19.0)** · **[First session / Premier essai](docs/PLAYTESTING.md)** · **[Send playtest feedback / Donner un retour](https://github.com/SpilerixOff/Penumbra/issues/new?template=playtest_feedback.yml)**

The first playable pre-release is available for download; no compilation is needed. / La première préversion jouable est disponible au téléchargement, sans compilation.

## Play Penumbra

1. On the [Releases page](https://github.com/SpilerixOff/Penumbra/releases), choose the latest playtest build and download its **mod JAR**. The source archive and `-sources.jar` are for developers.
2. Use **Minecraft Java Edition 26.1.2**, **Java 25**, **Fabric Loader 0.19.3** and **Fabric API 0.155.2+26.1.2**. Put the Penumbra JAR and matching Fabric API JAR in that instance's `mods/` folder.
3. Start with a **new test world** and back up any existing saves before trying the mod there. For multiplayer, install the same Penumbra release and dependencies on **the server and every client**.
4. Read the [comfort settings](#horror-effects-and-comfort), then follow the short [playtest guide](docs/PLAYTESTING.md). Reports about pacing, memorable moments and confusing interactions are as useful as bug reports. English and French are welcome.

## What is in the source

- A per-player tension system, adaptive pacing and persistent narrative progression.
- Creatures including the Host and the Listener, individual false-companion perceptions, and counterplay through observation, silence and the VHS camera.
- Photographs, albums, investigation clues, structures and a field journal that records discoveries.
- Configurable horror effects, gentler presets, dynamic held-item lighting and resource-pack-driven static mesh props.
- English and French game text. Some documentation remains in French.

These are implemented systems to explore and test, not a promise that every combination is stable. See [known issues](docs/KNOWN_ISSUES.md) and the [French manual](docs/MANUAL_FR.md).

## Build from source

This development branch prepares **0.22.0**: press **K** in game for personal
comfort settings, including reduced motion, hidden full-screen faces and
separate ambient and sudden-sound volume. These settings do not change server
difficulty. Post-ritual calm now survives reconnects and uses elapsed world
ticks; the final badge requires winning the confrontation.
The final ritual also clears the
participant's nearby apparitions and blocks competing scripted scenes from
its prelude onward. Interrupted attempts can be retried, and unfinished photo
captures cannot affect a new session after reconnecting.
It also includes 0.21.0's varied ambient audio, personal investigation dossier,
staged final confrontation and dynamic-light optimizations, plus the pacing,
counterplay and journal improvements from 0.20.0. The public download above
remains **0.19.0** until a new release is published. See [the changelog](CHANGELOG.md).

Post-ritual calm lasts up to 48,000 world ticks, including time spent offline
while the server runs. Stopping the server pauses that clock. It is not renewed
on reconnect. See [known issues](docs/KNOWN_ISSUES.md).

The version pins in [gradle.properties](gradle.properties) are the source of truth:

| Component | Target |
| --- | --- |
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.155.2+26.1.2 |
| JDK | 25 |

From the repository root, with JDK 25 available:

```sh
# Linux / macOS
./gradlew validatePenumbraResources test build
```

```powershell
# Windows PowerShell
.\gradlew.bat validatePenumbraResources test build
```

The mod JAR is `build/libs/penumbra-0.22.0.jar`; the `-sources.jar` is for developers. Put the mod JAR and the matching Fabric API in the Fabric instance's `mods/` directory. Multiplayer requires Penumbra and Fabric API on **the server and every client**. Development launch tasks are `runClient` and `runServer`.

For detailed setup and manual checks, see [compilation](docs/COMPILE.md), [installation](docs/INSTALL.md) and the [test protocol](docs/TEST_PROTOCOL.md). Older sections may describe earlier versions; use the current version pins and JAR name above.

## Horror effects and comfort

Penumbra contains sudden sounds, full-screen horror faces, camera movement and visual distortion. Screen flashes are disabled by default (`flashesEnabled: false`); other startling effects are enabled by default. These settings are not a guarantee of comfort for photosensitive players.

Configuration is created at `config/penumbra.json`. With the game stopped, you can disable `screamersEnabled`, `cameraShakeEnabled`, `distortionEnabled` and `fogEnabled`, and lower `jumpscareVolume`, `jumpscareFrequency` and `perceptionIntensity`. The operator command `/penumbra profile gentle` applies a gentler preset; it does not turn off every effect. Client rendering settings and server event settings must be configured in their respective instances.

In **0.22.0**, **K** opens a personal comfort menu (rebindable in Controls).
Apply saves to `config/penumbra-client.json`; Cancel discards the draft.
Personal options can reduce effects allowed by the base configuration, but
cannot enable effects it has disabled. These are presentation settings;
creature damage, event frequency and progression are unchanged.

For recording or streaming, set `streamerMode` to `true` in that file while the relevant game or server is stopped. It disables flashes and meta chat messages and caps jumpscare volume at `0.7`; horror faces and other startling effects can remain active. See the [playtest guide](docs/PLAYTESTING.md) for setup and a short feedback checklist.

## Contributing and provenance

Bug reports, reproducible in-game checks, translations and focused patches are welcome in English or French. Start with [CONTRIBUTING.md](CONTRIBUTING.md); use [SECURITY.md](SECURITY.md) for a possible vulnerability.

Asset notes and tools live in [docs/ASSETS.md](docs/ASSETS.md), [docs/SOUNDS.md](docs/SOUNDS.md) and `tools/`. Sound documentation identifies several assets as functional placeholders.

The repository carries the [MIT license](LICENSE). See [asset provenance and third-party notices](ASSET_PROVENANCE.md) for resource details. Dependencies retain their own applicable terms. Penumbra is an independent project and is not affiliated with Mojang or Microsoft.

## Français

**Penumbra est un mod d'horreur psychologique pour Minecraft 26.1.2, sous Fabric.** Son directeur d'horreur fait monter la tension, organise les manifestations et conserve une progression propre à chaque joueur. Le projet est maintenu par **SpilerixOff**.

La version **0.19.0 est ouverte aux premiers essais publics**. Le **21 octobre 2026**, le mainteneur a confirmé l'avoir testée en solo et en multijoueur ; **208 tests automatisés ont réussi** pendant la préparation de la publication. Les retours sur les parties longues et les différentes combinaisons de mods sont bienvenus.

Pour jouer, téléchargez le JAR du mod sur la [page Releases](https://github.com/SpilerixOff/Penumbra/releases), puis suivez le [guide du premier essai](docs/PLAYTESTING.md#français). Pas besoin de compiler. Commencez sur un nouveau monde de test et sauvegardez vos mondes existants. Vous pouvez ensuite [envoyer un retour](https://github.com/SpilerixOff/Penumbra/issues/new?template=playtest_feedback.yml) sur le rythme, les moments marquants ou les problèmes rencontrés.

Cette branche de développement prépare la **0.22.0** : **K** ouvre des réglages personnels de mouvement, d'image et de son, l'accalmie après le rituel survit aux reconnexions et le badge final exige une victoire. Elle comprend aussi le rituel reprenable et les photos fiabilisées de la 0.21.1, les ambiances variées et le dossier de la 0.21.0, ainsi que le rythme, le contre-jeu et le carnet de la 0.20.0. Le téléchargement public ci-dessus reste la **0.19.0** jusqu'à la prochaine publication.

L'accalmie dure au plus 48 000 ticks du monde : un serveur actif continue de la décompter hors connexion ; un serveur arrêté la met en pause. La reconnexion ne la réinitialise pas. Voir les [problèmes connus](docs/KNOWN_ISSUES.md).

Pour compiler sous Windows : `.\gradlew.bat validatePenumbraResources test build`, avec **Java 25**. Installez `build/libs/penumbra-0.22.0.jar` et Fabric API **0.155.2+26.1.2** dans une instance Minecraft **26.1.2** avec Fabric Loader **0.19.3**. En multijoueur, le mod et Fabric API sont nécessaires sur le serveur et chaque client.

**K** ouvre le menu personnel, réassignable dans les commandes de Minecraft.
**Appliquer** enregistre dans `config/penumbra-client.json` ; **Annuler** abandonne
les changements. Ces choix réduisent les effets autorisés par la configuration
de base et laissent les dégâts, la fréquence des événements et la progression
du serveur inchangés.

Le mod contient des sons soudains, des visages horrifiques plein écran, des secousses et des distorsions. Les flashs sont désactivés par défaut, mais d'autres effets restent actifs. Le profil opérateur `/penumbra profile doux` réduit plusieurs effets sans tout désactiver. Les réglages détaillés se trouvent dans `config/penumbra.json`.

Consultez le [manuel français](docs/MANUAL_FR.md), les [problèmes connus](docs/KNOWN_ISSUES.md) et le [guide de contribution](CONTRIBUTING.md). Les retours en français sont bienvenus. Licence : [MIT](LICENSE).
