# SPEC2.md — PENUMBRA Phase 2 — Contrat technique (additif au SPEC.md)

> **Tous les agents lisent SPEC.md PUIS ce fichier. Phase 2 est ADDITIVE : ne rien casser de la Phase 1.**
> Projet : `C:\Users\PlayerName\Documents\kimi\workspace\penumbra` — version mod : **0.2.0**.
> Toolchain inchangée : MC 26.1.2, Fabric Loader 0.19.3, Fabric API 0.155.2+26.1.2, Java 25,
> plugin `net.fabricmc.fabric-loom` 1.17.16 (pas de mappings, dépendances `implementation`).
> **Rappel 26.1 vérifié : `net.minecraft.resources.Identifier` (PAS ResourceLocation).**
> Build : depuis la racine du dépôt, avec Java 25 : `./gradlew build --console=plain`.
> Vérification API : toujours dans les vraies sources (cache loom `~/.gradle/caches/fabric-loom` + javap), jamais au deviné.

## 1. PenumbraConfig — champs ajoutés (Agent_CommandsConfig)
```java
public boolean binocularsEnabled = true;
public boolean binocularsRequireItem = false;   // exiger l'objet Jumelles en main ou inventaire
public boolean binocularsToggleMode = false;    // false = maintenir C, true = basculer
public float binocularsZoomLevel = 4.0f;        // borné 2.0–6.0 dans sanitize()
public boolean binocularsSmoothZoom = true;
public boolean binocularsBreathingMotion = true;
public boolean binocularsOverlayEnabled = true;
public boolean binocularsHostReactionsEnabled = true;
public boolean luresEnabled = true;
public boolean delayedJumpscaresEnabled = true;
public boolean binocularsDurabilityEnabled = true;
public int postChaseCooldownTicks = 7200;       // borné 1200–24000
```
`sanitize()` borne aussi `binocularsZoomLevel` et `postChaseCooldownTicks`.

## 2. Nouveaux sons (Agent_Resources : registry + ogg + sounds.json + sous-titres)
`BINOCULARS_ON("binoculars_on")` 0,3 s clic mécanique doux · `BINOCULARS_OFF("binoculars_off")` 0,25 s · `LURE_DOOR("lure_door")` 1,4 s grincement lointain · `DISTANT_HUM("distant_hum")` 6 s nappe très grave −20 dBFS · `CHASE_STINGER("chase_stinger")` 1,2 s dissonant −3 dBFS · `FOOTSTEP_MIMIC_2("footstep_mimic_2")` variante · `BREATH_BEHIND_2("breath_behind_2")` variante. Enregistrement dans `PenumbraSounds` (même helper `reg()`, Agent_Resources édite ce fichier). Tous pics ≤ −3 dBFS. Sous-titres `subtitles.penumbra.<id>` EN+FR.

## 3. Réseau C2S — `fr.penumbra.network.PenumbraC2S` (Agent_Binoculars)
```java
public record BinocularsStateC2S(boolean zooming) implements CustomPacketPayload { /* id "binoculars_state", StreamCodec */ }
public record InventoryStateC2S(boolean open) implements CustomPacketPayload { /* id "inventory_state" */ }
public static void registerPayloads();        // PayloadTypeRegistry.serverboundPlay() — VÉRIFIER le nom exact (miroir de clientboundPlay()) dans les sources fabric-api
public static void registerServerReceivers(); // ServerPlayNetworking.registerGlobalReceiver(...), maps statiques concurrentes UUID->boolean
public static boolean isZooming(UUID playerId);
public static boolean isInventoryOpen(UUID playerId);
public static void clear(UUID playerId);      // appelé à la déconnexion (ServerPlayConnectionEvents.DISCONNECT, enregistré dans registerServerReceivers)
```
`PenumbraMod.onInitialize()` gagne EXACTEMENT deux lignes (Agent_Binoculars les ajoute après `PenumbraNetworking.registerPayloads();`) :
`PenumbraC2S.registerPayloads();` puis `PenumbraC2S.registerServerReceivers();`

## 4. HorrorDirector — API ajoutée (Agent_Director2)
```java
public static void startChase(ServerPlayer player, HostEntity host);   // stinger CHASE_STINGER, behavior CHASING, log debug
public static void endChase(ServerPlayer player, boolean fakeRetreat); // cooldown global postChaseCooldownTicks ; fakeRetreat -> l'Hôte passe HIDING et reste <= 24 blocs
public static boolean isInGlobalCooldown(ServerPlayer player);
public static void tryBinocularReaction(ServerPlayer player, HostEntity host); // 7 réactions pondérées, cooldown 2400t, anti-répétition via profile.wasRecent("bino_<n>")
public static void noteChestOpened(ServerPlayer player, BlockPos pos);
public static void noteSlept(ServerPlayer player);
public static void noteTorchExtinguished(ServerPlayer player, BlockPos pos);
public static void noteLookedBack(ServerPlayer player);
public static void noteLongQuiet(ServerPlayer player);
public static void noteRefugeReturn(ServerPlayer player);
public static boolean forceScare(ServerPlayer player, String scareId); // ids §6, pour /penumbra scare
```
- Réactions jumelles (rare, pondéré par état, jamais d'attaque immédiate, jamais de téléport au contact) :
  1 `freeze` (immobile 300t) · 2 `head_turn` (lent vers le joueur, HOST_HEAD_CREAK si dispo sinon HOST_WHISPER) · 3 `vanish_on_unzoom` · 4 `approach_between` (rapproche de 6-10 blocs quand le joueur dézoom, max 1 fois/observation, jamais < 16 blocs) · 5 `sound_behind` (disparaît + BREATH_BEHIND/HOST_WHISPER 3 blocs derrière) · 6 `mimic_posture` (copie sneak/sprint du joueur 200t) · 7 `reappear_checked` (apparaît dans une zone récemment vérifiée du profil, 20-30 blocs).
- Jumpscares retardés : chaque `note*` arme un déclencheur retardé (100–400 ticks) avec probabilité pondérée par la tension, cooldown par type 3000t, anti-doublon (jamais deux scares à moins de 800 ticks), respect `delayedJumpscaresEnabled`. Effets = jumpscares existants ("sting", "apparition", "fake_creeper") + sons positionnés.
- Anti-frustration : après `endChase`, aucun événement agressif (chase, scare, distant_sighting) pendant le cooldown global ; `startChase` exige un avertissement contextuel préalable (dernier événement = distant_sighting/stalking récent OU tension >= HUNT) ; aucune attaque de mêlée sans 20 ticks de télégraphe (géré côté ChasePlayerGoal).

## 5. Mémoire comportementale — PlayerHorrorProfile (Agent_Director2)
Champs persistés (Codec v2, champs OPTIONNELS avec défauts pour compatibilité des mondes Phase 1) :
`homePoints` (≤4 BlockPos appris : immobile >600t, nuit ou lumière<8, revisité ≥3 fois), `frequentCells` (≤16 cellules grossières x>>3,z>>3 avec compteurs), `bedUses` (int), `lookBackCount` (int), `lastQuietEventTick` (long).
Accesseurs : `List<BlockPos> homePoints()`, `boolean isNearRefuge(BlockPos pos, double radius)`, `Optional<BlockPos> randomFrequentCell(RandomSource)`, `void noteBedUse()`, `int bedUses()`. `tickBehavior` alimente tout ça à faible coût (compteurs, pas de scan).

## 6. Nouveaux événements HorrorEvent (Agent_Events2, enregistrés dans HorrorEvents.ALL)
- `lure_door` : LURE_DOOR 4-10 blocs latéral, aucune modification de bloc. Poids fort en intérieur/refuge.
- `fake_silhouette` : `HostEntity.spawnNear(level, player, 28, 40)` behavior HIDING ; l'entité se discard dès qu'un joueur l'a en LOS pendant 4 ticks cumulés (réutiliser VanishWhenSeenGoal avec seuil réduit via constructeur `VanishWhenSeenGoal(host, 4)`) ou que le joueur est < 16 blocs. Null -> rien.
- `distant_light` : si worldModificationEnabled : torche posée 24-40 blocs (sol valide), restaurée (retirée) après 1200t via runLater (même pattern que TorchSputterEvent) + DISTANT_HUM ; sinon DISTANT_HUM seul.
- `anticlimax` : HEARTBEAT 6 s + SILENCE_DRONE, tension +8, **aucun autre effet**. Poids élevé en FALSE_CALM.
- `binoculars_sighting` : canRun = `PenumbraC2S.isZooming(player.getUUID())` && tension >= WATCHED && hostEnabled && binocularsHostReactionsEnabled ; run = spawn Hôte à 60–90 blocs dans le cône de regard (±15°), behavior OBSERVING ; jamais si un Hôte existe déjà à <100 blocs.
Tous : `luresEnabled` requis pour les leurres ; gains de tension cohérents (lure +4, silhouette +10, light +5, anticlimax +8, sighting +12).

Ids des jumpscares retardés (directeur, commande `/penumbra scare <id>`) : `chest_scare`, `refuge_scare`, `torch_scare`, `lookback_scare`, `inventory_scare`, `quiet_scare`.
Déclencheurs : `noteChestOpened` (UseBlockCallback sur coffre, Agent_Events2 enregistre le hook dans `HorrorEvents.register()`) · `noteRefugeReturn` (directeur : joueur à <10 blocs d'un homePoint après >1200t d'absence) · `noteTorchExtinguished` (appelé par TorchSputterEvent quand il retire la torche — Agent_Events2 ajoute cet appel) · `noteLookedBack` (directeur quand lookBackCount franchit 5 en <600t) · `noteLongQuiet` (aucun événement depuis 6000t, état >= UNEASE) · inventory : director vérifie `PenumbraC2S.isInventoryOpen` (chance pondérée à l'ouverture signalée par le packet).

## 7. IA de poursuite (Agent_ChaseAI — propriétaire du package entity/)
- `entity/ai/ChasePlayerGoal extends Goal` : actif si behavior CHASING ; mise en scène de début (arrêt 15t face au joueur + CHASE_STINGER via PenumbraNetworking.sendSound, joué par HorrorDirector.startChase) ; vitesse 1.15 -> 1.45 sur 60t ; recalcul du chemin toutes les 10t max ; contourne via navigation vanilla (jamais de chargement forcé de chunks : rester dans les chunks chargés, abandon si cible hors zone chargée) ; attaque uniquement en ATTACKING après télégraphe 20t (HOST_STEP rapproché + ralentissement visible) ; abandon si distance > 48 pendant 100t ou durée > 1200t -> `HorrorDirector.endChase(player, random<0.4)`.
- `entity/ai/HiddenWatchGoal` : behavior HIDING : reste immobile hors LOS directe, 12-24 blocs ; si découvert (LOS 4t) -> 60% vanish cendre, 40% passe STALKING ; après 2400t sans découverte -> vanish silencieux.
- `HostEntity` : ajouter `registerGoals` : ChasePlayerGoal(0, avant Vanish), HiddenWatchGoal(4) ; méthode `public boolean hasAnyHostNearby(ServerLevel level, BlockPos pos, double radius)` statique utilitaire ; pas d'autre changement de contrat Phase 1. L'Hôte exploite la mémoire : `ObservePlayerGoal` choisit en priorité (30%) un point à 8-16 blocs d'un homePoint ou d'une cellule fréquente du profil de la cible.

## 8. Jumelles (Agent_Binoculars — propriétaire de client/binoculars/, de PenumbraC2S, de l'objet)
- `client/binoculars/BinocularsManager` : tick client ; KeyMapping enregistré dans `PenumbraClient` (ligne ajoutée par cet agent) avec l'API officielle Fabric keymapping 26.1 (VÉRIFIER le nom exact dans les sources : KeyMappingHelper ou renommé) ; catégorie `key.categories.penumbra`, défaut C (GLFW 67). Conflit C : comportement vanilla (Minecraft affiche le conflit), documenté dans README.
- Zoom : FOV progressif. CHERCHER D'ABORD un hook FOV stable dans fabric-api 0.155.2 (grep sources : `fov`) ; s'il n'y en a pas, approche documentée : sauvegarder `options.fov().get()` à l'activation, appliquer `fovBase / zoomFacteur` lerpé chaque tick (ease 6t in/out si binocularsSmoothZoom), restaurer la valeur EXACTE à la désactivation et sur tout événement (ouverture écran, mort, déconnexion, changement de monde via ClientPlayConnectionEvents + ClientTickEvents) ; ne JAMAIS écrire options.txt (ne pas appeler options.save()). Troisième personne : aucun effet (documenté), sauf si le joueur repasse en 1ère personne pendant le maintien.
- Overlay (binocularsOverlayEnabled) : dessin procédural dans un HudElement (même API que JumpscareManager) : deux disques clairs (scène visible) sur fond assombri + vignette douce, géométrie relative à guiWidth/guiHeight (compatible ratios et GUI scale). Aucune texture requise. Aucun flash.
- Respiration (binocularsBreathingMotion) : dérive sinusoïdale ±0,05° pitch/yaw, uniquement en zoom, jamais de flash, désactivable.
- Son : BINOCULARS_ON/OFF en local à l'activation/désactivation, volume = jumpscareVolume*0.4, pitch 1.0 ; aucun son fort.
- Conditions : `binocularsEnabled` ; si `binocularsRequireItem`, objet Jumelles dans l'inventaire (test côté client sur l'inventaire du joueur) ; envoi de `BinocularsStateC2S` sur tout changement d'état ; `InventoryStateC2S` envoyé par un hook d'ouverture/fermeture d'écran (ScreenEvents, API Fabric client — vérifier le nom réel).
- Objet `PenumbraItems.BINOCULARS` (Agent_Binoculars édite PenumbraItems) : `stacksTo(1)`, `durability(256)` seulement si le jeu l'exige — sinon Item simple ; la consommation de durabilité (1 point / activation, si binocularsDurabilityEnabled) est gérée côté serveur via un packet C2S réutilisant BinocularsStateC2S (handler serveur endommage l'item en main si présent). Recette/texture/modèle/lang : Agent_Resources (spec §9).
- Commandes (Agent_CommandsConfig implémente, en appelant l'API publique du manager/directeur) : `/penumbra binoculars test` (force zoom client 3s via JumpscareManager-like local call — le manager expose `public static void debugZoom(int ticks)`) · `/penumbra binoculars host_sighting` (force l'événement binoculars_sighting) · `/penumbra binoculars reaction <freeze|head_turn|vanish_on_unzoom|approach_between|sound_behind|mimic_posture|reappear_checked>` (force `HorrorDirector.tryBinocularReaction` avec type imposé : ajouter surcharge `tryBinocularReaction(ServerPlayer, HostEntity, String forcedType)`) · `/penumbra binoculars reset` (réinitialise états client+serveur du joueur).

## 9. Ressources (Agent_Resources)
- `PenumbraSounds` : +7 sons §2. `gen_sounds.py` : +7 générateurs, régénérer tout. `sounds.json` + sous-titres EN/FR.
- `PenumbraItems.BINOCULARS` existe (Agent_Binoculars l'ajoute) — Agent_Resources fournit : `models/item/binoculars.json` (item/generated), `textures/item/binoculars.png` 16x16 via gen_textures.py (deux tubes sombres #2b2b30 cerclés #8f8a82), `data/penumbra/recipes/binoculars.json` (shaped : `G G` / `ILI` / `G G` avec G=glass_pane, I=iron_ingot, L=leather — équilibré début de partie), lang `item.penumbra.binoculars` = "Binoculars" / "Jumelles".
- Lang : + `key.penumbra.binoculars` ("Binoculars"/"Jumelles"), `key.categories.penumbra` ("Penumbra"/"Penumbra"), `command.penumbra.scare_triggered`, `command.penumbra.chase_started`, `command.penumbra.binoculars.*` (test_done, sighting_done, reaction_done, reaction_unknown, reset_done), sous-titres §2.
- `fabric.mod.json` : version "0.2.0". Docs : ASSETS.md/SOUNDS.md complétés (binoculars.png + 7 sons).
- Version projet : `build.gradle` version = "0.2.0" (Agent_CommandsConfig).

## 10. Commandes ajoutées (Agent_CommandsConfig, dans PenumbraCommands existant)
`/penumbra chase` (HorrorDirector.startChase avec Hôte spawné via spawnNear 16-24 ou réutilisé à proximité) · `/penumbra scare <id§6>` · `/penumbra binoculars <test|host_sighting|reaction <type>|reset>` · suggestions brigadier partout · clés lang §9. Permission niveau 2 conservée.

## 11. Docs (Agent_CommandsConfig)
README (section Jumelles + conflit touche C + nouvelles mécaniques), CHANGELOG 0.2.0, TEST_PROTOCOL (checklist Phase 2 complète fournie par l'utilisateur), KNOWN_ISSUES mis à jour.

## 12. Interdictions Phase 2
- Aucune modification de fichier hors de votre scope ; aucun nouveau marqueur A_VERIFIER (vérifier dans les sources réelles).
- Aucun mixin. Aucun scan > 24³. Aucun chunk forcé. Aucune attaque sans télégraphe. Aucun flash lumineux.
- Restauration de TOUTE modification du monde (pattern runLater + capture d'état).
- Séparation stricte client/serveur (visuel = client ; décisions Hôte = serveur synchronisé).
