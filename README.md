# Penumbra

> Something is learning to be you.

Penumbra is a psychological horror mod for **Minecraft 26.1.2 on Fabric**. A persistent horror director builds tension from in-game events, then stages sounds, apparitions, pursuit and discoveries around each player.

**Status: 0.24.0, public pre-release.** Maintained by [SpilerixOff](https://github.com/SpilerixOff). **390 automated tests in 76 suites passed**, together with the build and resource validation. Targeted in-game checks used **one client connected to a local server**, diagnostic placements and simulation controls. A complete campaign, simultaneous multiplayer and normal-speed balancing remain to be tested; earlier multiplayer reports do not validate this release. Feedback on longer sessions and different mod combinations is welcome.

**[Download Penumbra 0.24.0](https://github.com/SpilerixOff/Penumbra/releases/tag/v0.24.0)** · **[First session / Premier essai](docs/PLAYTESTING.md)** · **[Send playtest feedback / Donner un retour](https://github.com/SpilerixOff/Penumbra/issues/new?template=playtest_feedback.yml)**

The playable pre-release includes the changes from 0.20.0 through 0.24.0; no compilation is needed. / Cette préversion jouable rassemble les changements de la 0.20.0 à la 0.24.0, sans compilation.

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

## In development: 0.26.1

The other eight creatures now have distinct body animations: a one-arm statue
strike, a ceiling predator unfolding, a sound-led sweep, a harmless ankle feint,
a puppet-like shoulder hook, a deploying projector, a rigid giant's tilt and a
broken human imitation. Attack timing is synchronized with the server; several
models now have connected torsos, shoulders and articulated limbs. Counterfeit
and Originel charges also respect solid obstacles and a committed destination.
See the [design research and creature guide](docs/MONSTER_ANIMATIONS_FR.md).

Version 0.26.1 shortens the Host, Watcher, Counterfeit and Originel approach
from 8–12 to 6–9 ticks and increases the Absent and Crawler's horizontal leap
impulse by 25%. Preparation, damage and post-contact actions retain their timing.

The source build is 0.26.1; the public download above remains 0.24.0 until a
new release is published. See [validation notes](docs/KNOWN_ISSUES.md).

## Also included: 0.25.0

The Host's stare attack now includes a physical leap and dive, a two-handed
grab, a short lift and a forward throw. Dodging the committed leap or using
the VHS-camera flash can interrupt it. Solid obstacles limit the movement.
See [the physical horror guide](docs/PHYSICAL_HORROR_FR.md).

## What is new in 0.24.0

**0.24.0** stages scares with creatures in the
world, with visible preparation, committed charges, dodging and recoil. The
face-image and blackout presentation has been removed, including for legacy
scare packets. The Host, Watcher and Rampant can be repelled by a nearby,
correctly aimed VHS-camera flash. Container and journal closures, refuge returns
and cave ambushes have physical scenes with bounded lifetimes. See the
[physical horror guide](docs/PHYSICAL_HORROR_FR.md) and [validation limits](docs/KNOWN_ISSUES.md).

The personal journal and progression from 0.23.0 remain: each newcomer receives
guidance for their current chapter, original site chests remain readable after
looting, and the first eligible final ritual leads directly to the confrontation.
See the [story walkthrough / parcours de l'histoire](docs/STORY_FLOW_FR.md).

Press **K** in game for personal
comfort settings: four toggles for motion, flashes, grain and light flicker,
plus separate ambient and sudden-sound volume. These settings do not change server
difficulty. Post-ritual calm now survives reconnects and uses elapsed world
ticks; the final badge requires winning the confrontation.
The final ritual also clears the
participant's nearby apparitions and blocks competing scripted scenes from
its prelude onward. Interrupted attempts can be retried, and unfinished photo
captures cannot affect a new session after reconnecting.
It also includes 0.21.0's varied ambient audio, personal investigation dossier,
staged final confrontation and dynamic-light optimizations, plus the pacing,
counterplay and journal improvements from 0.20.0. See [the changelog](CHANGELOG.md).

Post-ritual calm lasts up to 48,000 world ticks, including time spent offline
while the server runs. Stopping the server pauses that clock. It is not renewed
on reconnect. See [known issues](docs/KNOWN_ISSUES.md).

## Build from source

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

The source build produces `build/libs/penumbra-0.26.1.jar`; the `-sources.jar` is for developers. Put the mod JAR and the matching Fabric API in the Fabric instance's `mods/` directory. Multiplayer requires the same Penumbra version and Fabric API on **the server and every client**. Development launch tasks are `runClient` and `runServer`.

For detailed setup and manual checks, see [compilation](docs/COMPILE.md), [installation](docs/INSTALL.md) and the [test protocol](docs/TEST_PROTOCOL.md). Older sections may describe earlier versions; use the current version pins and JAR name above.

## Horror effects and comfort

Penumbra 0.24.0 contains sudden sounds, approaching creatures, camera movement
and visual distortion. It no longer draws full-screen horror faces, peripheral
face images or screamer blackouts. Other visual flashes remain configurable and
disabled by default (`flashesEnabled: false`). These settings are not a guarantee of comfort for
photosensitive players.

Configuration is created at `config/penumbra.json`. With the game stopped, you can disable `screamersEnabled`, `cameraShakeEnabled`, `distortionEnabled` and `fogEnabled`, and lower `jumpscareVolume`, `jumpscareFrequency` and `perceptionIntensity`. The operator command `/penumbra profile gentle` applies a gentler preset; it does not turn off every effect. Client rendering settings and server event settings must be configured in their respective instances.

Since **0.22.0**, **K** opens a personal comfort menu (rebindable in Controls).
In 0.24.0 it exposes six controls: reduced motion, blocked flashes, grain,
light flicker, sudden-sound volume and ambient volume. The obsolete face and
afterimage switches are no longer shown; old saved values cannot restore those images.
Apply saves to `config/penumbra-client.json`; Cancel discards the draft.
Personal options can reduce effects allowed by the base configuration, but
cannot enable effects it has disabled. These are presentation settings;
creature damage, event frequency and progression are unchanged.

For recording or streaming, set `streamerMode` to `true` in `config/penumbra.json` while the relevant game or server is stopped. It disables flashes and meta chat messages and caps jumpscare volume at `0.7`; creature encounters and other startling effects can remain active. See the [playtest guide](docs/PLAYTESTING.md) for setup and a short feedback checklist.

## Contributing and provenance

Bug reports, reproducible in-game checks, translations and focused patches are welcome in English or French. Start with [CONTRIBUTING.md](CONTRIBUTING.md); use [SECURITY.md](SECURITY.md) for a possible vulnerability.

Asset notes and tools live in [docs/ASSETS.md](docs/ASSETS.md), [docs/SOUNDS.md](docs/SOUNDS.md) and `tools/`. Sound documentation identifies several assets as functional placeholders.

The repository carries the [MIT license](LICENSE). See [asset provenance and third-party notices](ASSET_PROVENANCE.md) for resource details. Dependencies retain their own applicable terms. Penumbra is an independent project and is not affiliated with Mojang or Microsoft.

## Français

**Penumbra est un mod d'horreur psychologique pour Minecraft 26.1.2, sous Fabric.** Son directeur d'horreur fait monter la tension, organise les manifestations et conserve une progression propre à chaque joueur. Le projet est maintenu par **SpilerixOff**.

La **0.24.0 est une préversion publique**. **390 tests automatisés dans 76 suites ont réussi**, avec la compilation et la validation des ressources. Les essais ciblés en jeu utilisent **un seul client connecté à un serveur local**, des placements de diagnostic et des pas de simulation. Ils ne valident ni une campagne complète, ni deux clients simultanés, ni l'équilibrage à vitesse normale ; les anciens retours multijoueurs ne valent pas validation de cette version. Les retours sur les parties longues et les différentes combinaisons de mods sont bienvenus.

Pour jouer, téléchargez le JAR de la [préversion 0.24.0](https://github.com/SpilerixOff/Penumbra/releases/tag/v0.24.0), puis suivez le [guide du premier essai](docs/PLAYTESTING.md#français). Pas besoin de compiler. Commencez sur un nouveau monde de test et sauvegardez vos mondes existants. Vous pouvez ensuite [envoyer un retour](https://github.com/SpilerixOff/Penumbra/issues/new?template=playtest_feedback.yml) sur le rythme, les moments marquants ou les problèmes rencontrés.

Dans la **0.24.0**, les sursauts reposent sur les créatures dans le monde, avec préparation visible, charge esquivable et recul. Les visages plaqués à l'écran, rémanences de visages et écrans noirs de screamer sont retirés. La caméra peut interrompre un Hôte, un Observateur ou un Rampant proche et cadré ; les fermetures de coffre ou de carnet et les retours au refuge peuvent ouvrir des scènes physiques. [Voir le guide de l'horreur physique](docs/PHYSICAL_HORROR_FR.md) et les [limites de validation](docs/KNOWN_ISSUES.md).

Le parcours personnel de la 0.23.0 est conservé : chaque arrivant reçoit Le Relevé, les coffres déjà pillés restent lisibles et le premier rituel final éligible conduit directement au duel. [Voir le parcours complet](docs/STORY_FLOW_FR.md). L'accalmie survit aux reconnexions et le badge final exige une victoire.

L'accalmie dure au plus 48 000 ticks du monde : un serveur actif continue de la décompter hors connexion ; un serveur arrêté la met en pause. La reconnexion ne la réinitialise pas. Voir les [problèmes connus](docs/KNOWN_ISSUES.md).

Pour compiler sous Windows : `.\gradlew.bat validatePenumbraResources test build`, avec **Java 25**. Installez `build/libs/penumbra-0.24.0.jar` et Fabric API **0.155.2+26.1.2** dans une instance Minecraft **26.1.2** avec Fabric Loader **0.19.3**. En multijoueur, le mod et Fabric API sont nécessaires sur le serveur et chaque client.

**K** ouvre le menu personnel, réassignable dans les commandes de Minecraft.
Ses six réglages sont : mouvement réduit, flashs bloqués, grain, scintillement
de lumière et les deux volumes Ambiances / Sons soudains. Les anciens boutons
Visages et Rémanences ont été retirés avec ces effets.
**Appliquer** enregistre dans `config/penumbra-client.json` ; **Annuler** abandonne
les changements. Ces choix réduisent les effets autorisés par la configuration
de base et laissent les dégâts, la fréquence des événements et la progression
du serveur inchangés.

Penumbra 0.24.0 contient des sons soudains, des créatures qui approchent, des secousses et des distorsions. Les images de visages ont été retirées. Les autres flashs sont désactivés par défaut. Le profil opérateur `/penumbra profile doux` réduit plusieurs effets sans tout désactiver. Les réglages détaillés se trouvent dans `config/penumbra.json`.

Consultez le [manuel français](docs/MANUAL_FR.md), les [problèmes connus](docs/KNOWN_ISSUES.md) et le [guide de contribution](CONTRIBUTING.md). Les retours en français sont bienvenus. Licence : [MIT](LICENSE).
