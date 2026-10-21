# Penumbra

> Something is learning to be you.

Penumbra is a psychological horror mod for **Minecraft 26.1.2 on Fabric**. A persistent horror director builds tension from in-game events, then stages sounds, apparitions, pursuit and discoveries around each player.

**Status: 0.19.0, in development.** This is an early public project maintained by [SpilerixOff](https://github.com/SpilerixOff). Automated checks and build results do not establish that a complete playthrough or multiplayer session works. In-game validation is still needed; use a separate test world and back up existing saves.

## What is in the source

- A per-player tension system, adaptive pacing and persistent narrative progression.
- Creatures including the Host and the Listener, individual false-companion perceptions, and counterplay through observation, silence and the VHS camera.
- Photographs, albums, investigation clues, structures and a field journal that records discoveries.
- Configurable horror effects, gentler presets, dynamic held-item lighting and resource-pack-driven static mesh props.
- English and French game text. Some documentation remains in French.

These are implemented systems to explore and test, not a promise that every combination is stable. See [known issues](docs/KNOWN_ISSUES.md) and the [French manual](docs/MANUAL_FR.md).

## Build and try it

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

The mod JAR is `build/libs/penumbra-0.19.0.jar`; the `-sources.jar` is for developers. Put the mod JAR and the matching Fabric API in the Fabric instance's `mods/` directory. Multiplayer requires Penumbra and Fabric API on **the server and every client**. Development launch tasks are `runClient` and `runServer`.

For detailed setup and manual checks, see [compilation](docs/COMPILE.md), [installation](docs/INSTALL.md) and the [test protocol](docs/TEST_PROTOCOL.md). Older sections may describe earlier versions; use the current version pins and JAR name above.

## Horror effects and comfort

Penumbra contains sudden sounds, full-screen horror faces, camera movement and visual distortion. Screen flashes are disabled by default (`flashesEnabled: false`); other startling effects are enabled by default. These settings are not a guarantee of comfort for photosensitive players.

Configuration is created at `config/penumbra.json`. With the game stopped, you can disable `screamersEnabled`, `cameraShakeEnabled`, `distortionEnabled` and `fogEnabled`, and lower `jumpscareVolume`, `jumpscareFrequency` and `perceptionIntensity`. The operator command `/penumbra profile gentle` applies a gentler preset; it does not turn off every effect. Client rendering settings and server event settings must be configured in their respective instances.

## Contributing and provenance

Bug reports, reproducible in-game checks, translations and focused patches are welcome in English or French. Start with [CONTRIBUTING.md](CONTRIBUTING.md); use [SECURITY.md](SECURITY.md) for a possible vulnerability.

Penumbra is developed with AI assistance for code, documentation and some artwork. Asset notes and generation scripts live in [docs/ASSETS.md](docs/ASSETS.md), [docs/SOUNDS.md](docs/SOUNDS.md) and `tools/`. Generated output still needs review and in-game testing. Sound documentation identifies several assets as functional placeholders.

The repository carries the [MIT license](LICENSE). See [asset provenance](ASSET_PROVENANCE.md) for Meshy-generated props, original artwork and third-party notices. Dependencies retain their own applicable terms. Penumbra is an independent project and is not affiliated with Mojang or Microsoft.

## Français

**Penumbra est un mod d'horreur psychologique pour Minecraft 26.1.2, sous Fabric.** Son directeur d'horreur fait monter la tension, organise les manifestations et conserve une progression propre à chaque joueur. Le projet est maintenu par **SpilerixOff**, avec une assistance IA pour le code, la documentation et certains visuels.

La version **0.19.0 est en développement**. Les vérifications automatiques ne remplacent pas un essai en jeu, une partie complète ou un test multijoueur. Utilisez un monde de test et sauvegardez vos mondes existants.

Pour compiler sous Windows : `.\gradlew.bat validatePenumbraResources test build`, avec **Java 25**. Installez `build/libs/penumbra-0.19.0.jar` et Fabric API **0.155.2+26.1.2** dans une instance Minecraft **26.1.2** avec Fabric Loader **0.19.3**. En multijoueur, le mod et Fabric API sont nécessaires sur le serveur et chaque client.

Le mod contient des sons soudains, des visages horrifiques plein écran, des secousses et des distorsions. Les flashs sont désactivés par défaut, mais d'autres effets restent actifs. Le profil opérateur `/penumbra profile doux` réduit plusieurs effets sans tout désactiver. Les réglages détaillés se trouvent dans `config/penumbra.json`.

Consultez le [manuel français](docs/MANUAL_FR.md), les [problèmes connus](docs/KNOWN_ISSUES.md) et le [guide de contribution](CONTRIBUTING.md). Les retours en français sont bienvenus. Licence : [MIT](LICENSE).
