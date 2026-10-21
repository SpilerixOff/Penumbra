# First session / Premier essai

Penumbra is looking for feedback from its first public players: what felt unsettling, what felt repetitive, and what was difficult to understand. A short solo or multiplayer session is useful; finishing the mod is not required.

On **21 October 2026**, the maintainer confirmed having tested solo and multiplayer. **208 automated tests passed** during release preparation. This is an early release; feedback on different machines, worlds and modpacks is welcome.

## English

### Install

1. Download the mod JAR from [GitHub Releases](https://github.com/SpilerixOff/Penumbra/releases). Use the attached playable JAR, not `-sources.jar` or GitHub's source archive; note the release you selected.
2. Prepare a Minecraft **Java Edition 26.1.2** instance with **Java 25**, **Fabric Loader 0.19.3** and **Fabric API 0.155.2+26.1.2**.
3. Put Penumbra and Fabric API in the instance's `mods/` folder. A first session with only these mods makes feedback easier to interpret.
4. Create a new test world. Back up an existing save before using it with the mod.
5. For multiplayer, use the same release and dependencies on the server and every client. Tell everyone that the session includes horror effects before starting.

### Choose your comfort settings

The mod includes sudden sounds, full-screen horror faces, camera movement and distortion. Flashes are off by default; other startling effects are on. You can stop the session whenever you want.

- With operator permission, `/penumbra profile gentle` applies a gentler preset. In a solo test world, commands must be enabled. This preset disables several effects and pursuits and slows the pacing; it does not remove all horror.
- For individual settings, launch once to create `config/penumbra.json`, then stop the game or server before editing it. Options include `screamersEnabled`, `cameraShakeEnabled`, `distortionEnabled`, `fogEnabled`, `jumpscareVolume` and `perceptionIntensity`.
- For recording, `streamerMode: true` disables flashes and meta chat messages and caps jumpscare volume at `0.7`. It does **not** disable every screamer or other startling effect.

On a server, the operator profile changes server settings; it does not configure each player's client. Set rendering preferences in each client's configuration as well. These controls do not guarantee comfort for photosensitive players.

### Try a short session

Play normally for about **20–30 minutes**, or as long as you are comfortable. Explore, make a small shelter and pay attention to how the atmosphere changes. The director deliberately leaves quiet periods, especially with the gentle preset; an uneventful session is useful feedback too. Avoid forcing events during your first session so you can judge the natural pacing.

In multiplayer, compare what each person noticed: some perceptions are intentionally individual. Note unexpected disconnects or broken interactions separately from differences in what players saw or heard. If practical, save, leave and return once to check that your session resumes as expected.

### Tell us what happened

Use the [short playtest feedback form](https://github.com/SpilerixOff/Penumbra/issues/new?template=playtest_feedback.yml). Include the release, solo/multiplayer setup, approximate session length and your clearest observation. Explain a bug with the steps, expected result and actual result when possible.

GitHub issues are public. Do not include your real identity, server address, account credentials, a full save or unedited logs. Screenshots are optional; hide player names, chat and identifying details first. Use [SECURITY.md](../SECURITY.md) for a suspected vulnerability.

## Français

### Installer

1. Téléchargez le JAR jouable sur [GitHub Releases](https://github.com/SpilerixOff/Penumbra/releases). Prenez le fichier du mod, pas `-sources.jar` ni l'archive du code source, et notez la version choisie.
2. Préparez une instance **Minecraft Java Edition 26.1.2**, avec **Java 25**, **Fabric Loader 0.19.3** et **Fabric API 0.155.2+26.1.2**.
3. Placez Penumbra et Fabric API dans son dossier `mods/`. Pour un premier essai, ces deux mods seuls facilitent l'interprétation des problèmes.
4. Créez un nouveau monde de test. Sauvegardez tout monde existant avant d'y essayer le mod.
5. En multijoueur, installez la même version du mod et les mêmes dépendances sur le serveur et chaque client. Prévenez les participants des effets d'horreur avant de commencer.

### Régler le confort

Le mod contient des sons soudains, des visages horrifiques plein écran, des mouvements de caméra et des distorsions. Les flashs sont désactivés par défaut, mais d'autres effets restent actifs. Vous pouvez interrompre l'essai à tout moment.

- La commande opérateur `/penumbra profile doux` applique un profil plus calme. En solo, activez les commandes dans le monde de test. Ce profil désactive plusieurs effets et les poursuites, et ralentit le rythme ; il ne supprime pas toute l'horreur.
- Pour affiner les réglages, lancez une première fois le jeu pour créer `config/penumbra.json`, puis fermez le jeu ou le serveur avant de modifier le fichier. Vous pouvez notamment régler `screamersEnabled`, `cameraShakeEnabled`, `distortionEnabled`, `fogEnabled`, `jumpscareVolume` et `perceptionIntensity`.
- Pour filmer ou diffuser, `streamerMode: true` désactive les flashs et les faux messages méta dans le chat, et plafonne le volume des jumpscares à `0.7`. Ce mode ne coupe **pas** tous les screamers ni tous les effets soudains.

Sur un serveur, la commande opérateur règle le serveur, pas chaque client. Ajustez aussi les préférences de rendu dans la configuration de chaque joueur. Ces réglages ne garantissent pas le confort des personnes photosensibles.

### Faire une courte partie

Jouez normalement pendant **20 à 30 minutes**, ou moins selon votre confort. Explorez, construisez un petit refuge et observez l'évolution de l'ambiance. Les périodes calmes sont volontaires, surtout avec le profil doux : un essai où il ne se passe presque rien mérite aussi un retour. Évitez de forcer les événements pendant cette première partie pour pouvoir juger leur rythme naturel.

À plusieurs, comparez ce que chacun a remarqué : certaines perceptions sont volontairement individuelles. Distinguez ces différences des déconnexions ou interactions cassées. Si possible, sauvegardez, quittez et revenez une fois pour vérifier que la partie reprend comme prévu.

### Envoyer un retour

Le [formulaire de retour](https://github.com/SpilerixOff/Penumbra/issues/new?template=playtest_feedback.yml) demande la version, le contexte solo/multi, la durée approximative et votre observation principale. Pour un bug, décrivez si possible les étapes, ce que vous attendiez et ce qui s'est produit.

Les tickets GitHub sont publics. N'ajoutez ni identité réelle, ni adresse de serveur, ni identifiants de connexion, ni sauvegarde complète, ni logs bruts. Les captures sont facultatives : masquez les pseudos, le chat et les détails identifiants. Pour une faille potentielle, consultez [SECURITY.md](../SECURITY.md).
