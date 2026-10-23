# First session / Premier essai

Penumbra is looking for feedback from its first public players: what felt unsettling, what felt repetitive, and what was difficult to understand. A short solo or multiplayer session is useful; finishing the mod is not required.

For **0.28.0**, **515 automated tests in 99 suites passed**, together with the build and resource validation. Targeted checks on **23 October 2026** used one client on a local server, prepared terrain and simulation controls. A complete campaign, simultaneous multiplayer, Internet latency and natural pacing remain to be evaluated. Earlier solo and multiplayer reports do not validate this release. See [known issues](KNOWN_ISSUES.md).

## English

### Install

1. Download `penumbra-0.28.0.jar` from [the 0.28.0 pre-release](https://github.com/SpilerixOff/Penumbra/releases/tag/v0.28.0). Use the attached playable JAR, not `-sources.jar` or GitHub's source archive.
2. Prepare a Minecraft **Java Edition 26.1.2** instance with **Java 25**, **Fabric Loader 0.19.3** and **Fabric API 0.155.2+26.1.2**.
3. Put Penumbra and Fabric API in the instance's `mods/` folder. A first session with only these mods makes feedback easier to interpret.
4. Create a new test world. Back up an existing save before using it with the mod.
5. For multiplayer, use the same release and dependencies on the server and every client. Tell everyone that the session includes horror effects before starting.

### Choose your comfort settings

The mod includes sudden sounds, approaching creatures, camera movement and distortion. Full-screen horror faces and screamer blackouts were removed in 0.24.0. Other visual flashes are off by default; other startling effects remain active. You can stop the session whenever you want.

- With operator permission, `/penumbra profile gentle` applies a gentler preset. In a solo test world, commands must be enabled. This preset disables several effects and pursuits and slows the pacing; it does not remove all horror.
- Press **K** for your personal comfort menu: reduced motion, blocked flashes, grain, light flicker, sudden-sound volume and ambient volume. **Apply** saves to `config/penumbra-client.json`; **Cancel** leaves previous choices intact. These options do not change creature damage, event frequency or progression.
- For the base configuration, launch once to create `config/penumbra.json`, then stop the game or server before editing it. Options include `screamersEnabled`, `cameraShakeEnabled`, `distortionEnabled`, `fogEnabled`, `jumpscareVolume` and `perceptionIntensity`.
- For recording, `streamerMode: true` disables flashes and meta chat messages and caps jumpscare volume at `0.7`. It does **not** disable every screamer or other startling effect.

On a server, the operator profile changes server settings; each player chooses personal presentation settings with **K**. These controls do not guarantee comfort for photosensitive players.

### Try a short session

Play normally for about **20–30 minutes**, or as long as you are comfortable. Explore, make a small shelter and pay attention to how the atmosphere changes. The director deliberately leaves quiet periods, especially with the gentle preset; an uneventful session is useful feedback too. Avoid forcing events during your first session so you can judge the natural pacing.

Read the Survey journal provided on joining. Its Investigation tab points to the next missing Warrick volume; four volumes lead through the observation post, shifted house, ash sanctum and Penumbra well. The cassette and illustrated crafting plans help prepare the return to the sanctum. This route takes longer than a short playtest: report an unclear clue or an inaccessible site without needing to finish it.

If Second Presence occurs, note whether the footsteps helped you locate it, whether its pause and retreat were readable, and whether your response made sense. It needs a suitable opaque corner and short clear route, so it will not appear in every place or session. Some variants simply withdraw. The camera, your ward and stillness behind opaque cover provide responses; seeing it alone does not trigger its attack.

In multiplayer, compare what each person noticed: some perceptions are intentionally individual. Note unexpected disconnects or broken interactions separately from differences in what players saw or heard. If practical, save, leave and return once to check that your session resumes as expected.

### Tell us what happened

Use the [short playtest feedback form](https://github.com/SpilerixOff/Penumbra/issues/new?template=playtest_feedback.yml). Include the release, solo/multiplayer setup, approximate session length and your clearest observation. Explain a bug with the steps, expected result and actual result when possible.

GitHub issues are public. Do not include your real identity, server address, account credentials, a full save or unedited logs. Screenshots are optional; hide player names, chat and identifying details first. Use [SECURITY.md](../SECURITY.md) for a suspected vulnerability.

## Français

### Installer

1. Téléchargez `penumbra-0.28.0.jar` sur la [préversion 0.28.0](https://github.com/SpilerixOff/Penumbra/releases/tag/v0.28.0). Prenez le fichier jouable, pas `-sources.jar` ni l'archive du code source.
2. Préparez une instance **Minecraft Java Edition 26.1.2**, avec **Java 25**, **Fabric Loader 0.19.3** et **Fabric API 0.155.2+26.1.2**.
3. Placez Penumbra et Fabric API dans son dossier `mods/`. Pour un premier essai, ces deux mods seuls facilitent l'interprétation des problèmes.
4. Créez un nouveau monde de test. Sauvegardez tout monde existant avant d'y essayer le mod.
5. En multijoueur, installez la même version du mod et les mêmes dépendances sur le serveur et chaque client. Prévenez les participants des effets d'horreur avant de commencer.

### Régler le confort

Le mod contient des sons soudains, des créatures qui approchent, des mouvements de caméra et des distorsions. Les visages plein écran et écrans noirs de screamer ont été retirés en 0.24.0. Les autres flashs sont désactivés par défaut, mais d'autres effets restent actifs. Vous pouvez interrompre l'essai à tout moment.

- La commande opérateur `/penumbra profile doux` applique un profil plus calme. En solo, activez les commandes dans le monde de test. Ce profil désactive plusieurs effets et les poursuites, et ralentit le rythme ; il ne supprime pas toute l'horreur.
- **K** ouvre le menu personnel : mouvement réduit, flashs bloqués, grain, scintillement, volume des sons soudains et volume des ambiances. **Appliquer** enregistre dans `config/penumbra-client.json` ; **Annuler** conserve les choix précédents. Ces réglages ne changent ni les dégâts, ni la fréquence des événements, ni la progression.
- Pour régler la configuration de base, lancez une première fois le jeu pour créer `config/penumbra.json`, puis fermez le jeu ou le serveur avant de modifier le fichier. Vous pouvez notamment régler `screamersEnabled`, `cameraShakeEnabled`, `distortionEnabled`, `fogEnabled`, `jumpscareVolume` et `perceptionIntensity`.
- Pour filmer ou diffuser, `streamerMode: true` désactive les flashs et les faux messages méta dans le chat, et plafonne le volume des jumpscares à `0.7`. Ce mode ne coupe **pas** tous les screamers ni tous les effets soudains.

Sur un serveur, la commande opérateur règle le serveur ; chaque joueur choisit ses réglages personnels de présentation avec **K**. Ces réglages ne garantissent pas le confort des personnes photosensibles.

### Faire une courte partie

Jouez normalement pendant **20 à 30 minutes**, ou moins selon votre confort. Explorez, construisez un petit refuge et observez l'évolution de l'ambiance. Les périodes calmes sont volontaires, surtout avec le profil doux : un essai où il ne se passe presque rien mérite aussi un retour. Évitez de forcer les événements pendant cette première partie pour pouvoir juger leur rythme naturel.

Lisez Le Relevé reçu à l'arrivée. L'onglet Enquête indique le prochain volume de Warrick manquant : quatre livres relient le poste d'observation, la maison décalée, le sanctuaire de cendre et le puits de Penumbra. La cassette et les recettes illustrées préparent le retour au sanctuaire. Ce parcours dépasse un court essai : vous pouvez signaler un indice peu clair ou un lieu inaccessible sans finir l'histoire.

Si la Seconde Présence se produit, notez si les pas vous aident à la situer, si son arrêt et son recul sont compréhensibles et si votre réaction a un résultat lisible. Il lui faut un angle opaque et un court trajet dégagé : elle n'apparaît pas partout ni à chaque session. Certaines variantes se retirent simplement. Caméra, talisman et immobilité à couvert permettent de réagir ; la voir ne déclenche pas à elle seule son attaque.

Pour cette **0.28.0**, **515 tests automatisés dans 99 suites**, la compilation et la validation des ressources ont réussi. Les essais ciblés du **23 octobre 2026** utilisent un seul client sur un serveur local, un terrain préparé et des pas de simulation. Une campagne complète, plusieurs clients simultanés, la latence Internet et le rythme naturel restent à évaluer. Les anciens retours solo/multi ne valident pas cette version. Voir les [problèmes connus](KNOWN_ISSUES.md).

À plusieurs, comparez ce que chacun a remarqué : certaines perceptions sont volontairement individuelles. Distinguez ces différences des déconnexions ou interactions cassées. Si possible, sauvegardez, quittez et revenez une fois pour vérifier que la partie reprend comme prévu.

### Envoyer un retour

Le [formulaire de retour](https://github.com/SpilerixOff/Penumbra/issues/new?template=playtest_feedback.yml) demande la version, le contexte solo/multi, la durée approximative et votre observation principale. Pour un bug, décrivez si possible les étapes, ce que vous attendiez et ce qui s'est produit.

Les tickets GitHub sont publics. N'ajoutez ni identité réelle, ni adresse de serveur, ni identifiants de connexion, ni sauvegarde complète, ni logs bruts. Les captures sont facultatives : masquez les pseudos, le chat et les détails identifiants. Pour une faille potentielle, consultez [SECURITY.md](../SECURITY.md).
