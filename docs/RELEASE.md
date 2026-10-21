# Checklist de release — Penumbra

## 1. Geler et valider

- [ ] `gradlew test` — 100 % vert (≈ 195 tests).
- [ ] `gradlew runClient` — création d'un monde, `/penumbra status` répond.
- [ ] Protocoles récents : §112–116 (variété screamers, habituation, statue de
      l'Hôte, Faux-Semblant, variantes sonores) + un passage libre de 20 min.
- [ ] Multijoueur : `gradlew runServer` + 2 clients, vérifier perceptions
      individuelles et sons par joueur.
- [ ] `CHANGELOG.md` à jour, version cohérente dans `gradle.properties`
      (`mod_version`), aucun `TODO` bloquant : `grep -rn "TODO" src/main/java`.

## 2. Construire

```
gradlew build
```

- Jar de release : `build/libs/penumbra-<version>.jar` (prendre celui SANS
  suffixe `-sources`).
- Test froid : copier le jar dans un profil Fabric propre (loader 0.19.3,
  Fabric API 0.155.2+26.1.2, Java 25) et lancer une partie.

## 3. Publier (Modrinth d'abord, CurseForge ensuite)

- [ ] Créer le projet : nom `Penumbra`, slug `penumbra`, catégories
      *Adventure*, *Mobs*, *Game Mechanics* ; environnement client & serveur.
- [ ] Description : coller `docs/MODRINTH.md` (EN puis FR).
- [ ] Icône : `src/main/resources/assets/penumbra/icon.png` (512×512).
- [ ] Galerie : 4–6 captures nocturnes (Hôte en OBSERVING, silhouette
      lointaine, refuge contaminé, album photo ouvert) + 1 Faux-Semblant
      en plein jour + 1 visage de screamer.
- [ ] Version : `0.19.0`, canal *Beta* recommandé pour la première publication
      (les retours de pacing valent de l'or avant la 1.0.0).
- [ ] Dépendances déclarées : Fabric API (obligatoire).
- [ ] Licence : MIT (déjà dans le repo).

## 4. Après publication

- [ ] Épingler un post « Signaler un problème de pacing » : le réglage fin de
      l'habituation vient des retours réels.
- [ ] Surveiller les premiers logs de crash (surtout multijoueur).
- [ ] Roadmap publique courte : 1.0.0 = retours de pacing intégrés +
      refactor interne du Directeur (sans changement de gameplay).

## Notes de version suggérées (0.19.0)

> Le Faux-Semblant (créatures vanilla possédées, parfois parfaites), preuve
> photographique dédiée, anti-habituation des screamers (6 mises en scène,
> décote serveur, refus sensibilisants), Hôte remodelé (statue, cou, mains),
> identité visuelle unifiée, 14 visages de screamers, 29 sons resynthétisés
> avec variantes, 28 répliques de leurre dans le tchat.
