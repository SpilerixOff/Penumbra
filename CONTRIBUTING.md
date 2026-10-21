# Contributing / Contribuer

Penumbra is an early project maintained by SpilerixOff. English and French reports and contributions are welcome. Response times are not guaranteed.

## Report a problem

Check [known issues](docs/KNOWN_ISSUES.md) and existing issues first. Include the mod version or commit, Minecraft/Fabric/Java versions, other installed mods, exact reproduction steps and expected versus actual behavior. Say whether the problem occurs in single-player, on a dedicated server, or both.

Attach only the relevant log excerpt. Remove usernames, local paths, server addresses, tokens and other private information before sharing. For security-sensitive reports, follow [SECURITY.md](SECURITY.md).

## Make a change

1. For a large change, open an issue describing the player problem and proposed behavior before implementing it.
2. Fork the repository, create a focused branch and use JDK 25. Dependency versions are in `gradle.properties`.
3. Keep English and French translation keys aligned. Preserve effect controls, per-player state and multiplayer ownership boundaries when changing gameplay.
4. Run `./gradlew validatePenumbraResources test build` (Windows: `.\gradlew.bat validatePenumbraResources test build`). Add a targeted regression test when a behavior or defect can usefully be tested automatically.
5. For gameplay or rendering changes, use a disposable test world and the relevant steps in [TEST_PROTOCOL.md](docs/TEST_PROTOCOL.md). Report what you actually tried and what remains untested; a successful build is not a gameplay test.
6. Open a pull request explaining the problem, resulting behavior and validation. Screenshots or short videos help with visual changes.

Only contribute code and assets you are entitled to share. Identify third-party sources and their licenses. This repository uses the [MIT license](LICENSE); flag any contribution requiring different terms before inclusion.

AI-assisted contributions are welcome. Explain significant generated code or assets and their provenance in the pull request, review them yourself, and do not claim checks you did not perform. Avoid unrelated rewrites and generated binary/build output.

## Français

Les rapports et contributions en français sont bienvenus. Précisez les versions, les autres mods, le mode solo/serveur, les étapes exactes et le résultat attendu. Retirez les informations personnelles des journaux. Pour une vulnérabilité, consultez [SECURITY.md](SECURITY.md).

Discutez d'abord les changements importants dans une issue. Travaillez avec Java 25 et les versions de `gradle.properties`, puis lancez `.\gradlew.bat validatePenumbraResources test build`. Gardez les traductions FR/EN cohérentes et les limites entre joueurs intactes. Vérifiez les changements de gameplay dans un monde de test et indiquez clairement ce qui n'a pas été testé.

Une pull request doit expliquer le problème, le comportement obtenu et les vérifications effectuées. Déclarez les sources et licences des contenus externes, ainsi que l'assistance IA significative. N'ajoutez pas de résultats de build ni de modifications sans rapport avec le changement.
