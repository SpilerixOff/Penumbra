# SPEC.md — PENUMBRA — Contrat technique partagé (Phase 1)

> **TOUS LES AGENTS DOIVENT LIRE CE FICHIER EN ENTIER AVANT D'ÉCRIRE DU CODE.**
> Chaque agent n'écrit QUE les fichiers de son scope. Toute référence à une classe hors scope
> doit utiliser EXACTEMENT la signature définie ici. Aucune improvisation sur les noms.

## 1. Constantes du projet

| Élément | Valeur |
|---|---|
| Racine projet | `C:\Users\PlayerName\Documents\kimi\workspace\penumbra` |
| mod id | `penumbra` |
| Nom affiché | `Penumbra` |
| Version mod | `0.1.0` |
| Package racine | `fr.penumbra` |
| Minecraft | `26.1.2` |
| Fabric Loader | `0.19.3` (vérifié) |
| Fabric API | `0.155.2+26.1.2` (vérifié) |
| Java | 25 (sourceCompatibility/targetCompatibility 25) |
| Mappings | officiels Mojang (26.1 est non-obfusqué ; NE PAS déclarer yarn) |
| Loom | variable `fabric_loom_version=1.17.16 (RÉSOLU : dernière stable vérifiée)` + commentaire dans gradle.properties (dernière stable sur https://fabricmc.net/develop ; 1.11+ requis pour 26.1) |
| Gradle wrapper | `gradle-9.6.1-bin.zip` (RÉSOLU : current vérifié) |

## 2. Règles de code

- Java 25, aucun `TODO`, aucun `// Ajouter le reste ici`. Fichiers COMPILABLES et complets.
- Mappings officiels Mojang : packages `net.minecraft.*`, noms de méthodes officiels (`tick()`, `getWorld()`, etc. — attention : en mappings officiels les méthodes s'appellent comme dans le code déobfusqué Mojang ; utilisez les signatures de l'ère 1.21.x officielle, ex. `entity.level()` n'existe pas côté Yarn mais côté officiel si. **RÈGLE PRATIQUE** : utilisez les noms officiels Mojang 1.21.x : `level()`, `getCommandSenderWorld()`, `playSound(...)`, `blockPosition()`, etc. Quand un doute existe entre nom Yarn et nom officiel, choisissez l'officiel).
- Commentaires Javadoc en français, courts et utiles.
- Pas de mixin en Phase 1 (fabric.mod.json ne déclare PAS de section `mixins`).
- Séparation stricte : aucune classe `net.minecraft.client.*` hors des classes client (PenumbraClient, package `client.*`).
- Logging : `PenumbraMod.LOGGER` (slf4j Logger via LogUtils/LoggerFactory).

## 3. Arborescence autorisée

```
penumbra/
├── settings.gradle
├── build.gradle
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── .gitignore
├── LICENSE                      (MIT)
├── README.md
├── CHANGELOG.md
├── docs/INSTALL.md
├── docs/COMPILE.md
├── docs/TEST_PROTOCOL.md
├── docs/KNOWN_ISSUES.md
├── docs/ROADMAP.md
├── docs/ASSETS.md
├── docs/SOUNDS.md
└── src/main/
    ├── java/fr/penumbra/
    │   ├── PenumbraMod.java
    │   ├── PenumbraClient.java
    │   ├── config/PenumbraConfig.java
    │   ├── registry/PenumbraItems.java
    │   ├── registry/PenumbraParticles.java
    │   ├── registry/PenumbraSounds.java
    │   ├── registry/PenumbraEntities.java
    │   ├── network/PenumbraNetworking.java
    │   ├── director/HorrorDirector.java
    │   ├── director/TensionState.java
    │   ├── director/PlayerHorrorProfile.java
    │   ├── director/DelayedTaskScheduler.java
    │   ├── data/PenumbraPersistentState.java
    │   ├── command/PenumbraCommands.java
    │   ├── event/HorrorEvent.java
    │   ├── event/HorrorEvents.java
    │   ├── event/impl/FootstepMimicEvent.java
    │   ├── event/impl/BreathBehindEvent.java
    │   ├── event/impl/FakeCreeperEvent.java
    │   ├── event/impl/TorchSputterEvent.java
    │   ├── event/impl/DoorAnomalyEvent.java
    │   ├── event/impl/DistantHostSightingEvent.java
    │   ├── event/impl/SilenceEvent.java
    │   ├── event/impl/SleepInterruptEvent.java
    │   ├── entity/HostEntity.java
    │   ├── entity/HostBehavior.java
    │   ├── entity/ai/VanishWhenSeenGoal.java
    │   ├── entity/ai/ObservePlayerGoal.java
    │   ├── entity/ai/StalkPlayerGoal.java
    │   └── client/
    │       ├── PenumbraClientNet.java
    │       ├── jumpscare/JumpscareManager.java
    │       ├── particle/AshMoteParticle.java
    │       └── render/HostEntityModel.java
    │       └── render/HostEntityRenderer.java
    └── resources/
        ├── fabric.mod.json
        ├── assets/penumbra/
        │   ├── icon.png
        │   ├── sounds.json
        │   ├── lang/en_us.json
        │   ├── lang/fr_fr.json
        │   ├── models/item/ash_sigil.json
        │   ├── textures/item/ash_sigil.png
        │   ├── textures/entity/host.png
        │   ├── textures/particle/ash_mote.png
        │   ├── particles/ash_mote.json
        │   └── sounds/  (fichiers .ogg générés ou placeholder)
        └── data/penumbra/loot_tables/entities/host.json
```

## 4. Contrats de classes (signatures EXACTES)

### 4.1 `fr.penumbra.PenumbraMod` (Agent_Core)
```java
public class PenumbraMod implements ModInitializer {
    public static final String MOD_ID = "penumbra";
    public static final Logger LOGGER = LoggerFactory.getLogger("Penumbra");
    public static Identifier id(String path);            // Identifier.of(MOD_ID, path)
    @Override public void onInitialize();
    // Ordre : PenumbraConfig.load(); PenumbraItems.register(); PenumbraParticles.register();
    //         PenumbraSounds.register(); PenumbraEntities.register();
    //         PenumbraNetworking.registerPayloads(); PenumbraCommands.register();
    //         ServerTickEvents.END_SERVER_TICK.register(HorrorDirector::onServerTick);
    //         ServerTickEvents.END_SERVER_TICK.register(DelayedTaskScheduler::onServerTick);
}
```

### 4.2 `fr.penumbra.PenumbraClient` (Agent_Core)
```java
public class PenumbraClient implements ClientModInitializer {
    @Override public void onInitializeClient();
    // EntityRendererRegistry.register(PenumbraEntities.HOST, HostEntityRenderer::new);
    // EntityModelLayerRegistry.registerModelLayer(HostEntityModel.LAYER, HostEntityModel::getTexturedModelData);
    // ParticleFactoryRegistry.getInstance().register(PenumbraParticles.ASH_MOTE, AshMoteParticle.Factory::new);
    // PenumbraClientNet.register();
    // ClientTickEvents.END_CLIENT_TICK.register(JumpscareManager::tick);
}
```

### 4.3 `fr.penumbra.config.PenumbraConfig` (Agent_Core)
Singleton Gson, fichier `FabricLoader.getInstance().getConfigDir().resolve("penumbra.json")`.
```java
public class PenumbraConfig {
    public float horrorIntensity = 1.0f;        // 0.0–2.0 multiplicateur global
    public float eventFrequency = 1.0f;         // 0.0–2.0
    public float jumpscareFrequency = 1.0f;     // 0.0–2.0
    public float jumpscareVolume = 0.8f;        // 0.0–1.0 (sécurité : jamais > 1)
    public boolean cameraShakeEnabled = true;
    public boolean distortionEnabled = true;
    public boolean flashesEnabled = false;      // OFF par défaut (photosensibilité)
    public boolean fogEnabled = true;
    public boolean chasesEnabled = true;
    public boolean worldModificationEnabled = true;
    public boolean sleepEventsEnabled = true;
    public boolean hostEnabled = true;
    public boolean storyMode = true;
    public boolean multiplayerContent = true;
    public boolean debugLogging = false;
    public static PenumbraConfig get();
    public static void load();                  // crée le fichier avec défauts si absent ; robuste au JSON invalide (backup .broken puis défauts)
    public static void save();
}
```

### 4.4 Registres

`fr.penumbra.registry.PenumbraSounds` (Agent_Events) — tous `public static final SoundEvent`, enregistrés via `Registry.register(Registries.SOUND_EVENT, PenumbraMod.id(name), SoundEvent.of(PenumbraMod.id(name)))` :
`HOST_WHISPER("host_whisper")`, `HOST_STINGER("host_stinger")`, `HOST_STEP("host_step")`, `FOOTSTEP_MIMIC("footstep_mimic")`, `BREATH_BEHIND("breath_behind")`, `DOOR_GROAN("door_groan")`, `FAKE_CREEPER("fake_creeper")`, `TORCH_SPUTTER("torch_sputter")`, `SILENCE_DRONE("silence_drone")`, `HEARTBEAT("heartbeat")`. Méthode `public static void register() {}` (force le chargement statique).

`fr.penumbra.registry.PenumbraItems` (Agent_Core) :
```java
public static final Item ASH_SIGIL; // id "ash_sigil", Item.Settings().maxCount(1), enregistré + ItemGroupEvents.INGREDIENTS
public static void register();
```

`fr.penumbra.registry.PenumbraParticles` (Agent_Core) :
```java
public static final DefaultParticleType ASH_MOTE; // FabricParticleTypes.simple(), id "ash_mote"
public static void register();
```

`fr.penumbra.registry.PenumbraEntities` (Agent_EntityAI) :
```java
public static final EntityType<HostEntity> HOST;
// EntityType.Builder.create(HostEntity::new, MobCategory.MONSTER).sized(0.6f, 2.6f).eyeHeight(2.3f)
//   .clientTrackingRange(48).build(ResourceKey)  // en 1.21.2+ : build(ResourceKey.create(Registries.ENTITY_TYPE, PenumbraMod.id("host")))
// En mappings officiels : MobCategory.MONSTER, ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, id)
public static void register(); // + FabricDefaultAttributeRegistry.register(HOST, HostEntity.createHostAttributes());
```
⚠️ Mappings officiels = packages `net.minecraft.core.Registry`, `net.minecraft.core.registries.BuiltInRegistries`, `net.minecraft.world.entity.*`. Fabric API reste `net.fabricmc.*`.

### 4.5 Directeur (Agent_Director)

`TensionState` enum : `CALM(0f)`, `UNEASE(15f)`, `WATCHED(35f)`, `MANIFESTATION(55f)`, `HUNT(75f)`, `CRISIS(90f)`, `FALSE_CALM(0f)` ; `public final float threshold;` + `public static TensionState forTension(float t)` (FALSE_CALM géré par le directeur, pas par forTension).

`PlayerHorrorProfile` :
```java
public class PlayerHorrorProfile {
    public static final Codec<PlayerHorrorProfile> CODEC; // RecordCodecBuilder : tension(FLOAT), lastEventTick(LONG), recentEvents(STRING list), totalEvents(INT), ticksUnderground(LONG), looksBehind(INT)
    public float tension();
    public void addTension(float delta);        // clamp 0–100
    public void decayTension(float perSecond);  // appelé par tick
    public long lastEventTick(); public void markEvent(long tick, String eventId); // maj deque (max 6) + compteur
    public boolean wasRecent(String eventId);
    public void tickBehavior(ServerPlayerEntity player); // met à jour ticksUnderground, immobilité, regards arrière (variation yaw > 120° en < 10 ticks)
    public int looksBehind(); public long ticksUnderground();
}
```

`DelayedTaskScheduler` : `public static void runLater(ServerLevel level, int delayTicks, Runnable task);` + `public static void onServerTick(MinecraftServer server);` (file par niveau, exécute quand `level.getGameTime()` atteint l'échéance).

`HorrorDirector` :
```java
public class HorrorDirector {
    public static final long GRACE_PERIOD_TICKS = 9000L;      // 7,5 min avant le 1er événement
    public static void onServerTick(MinecraftServer server);  // tous les 20 ticks : profils ; tous les 100 ticks : sélection
    public static float getTension(MinecraftServer server, UUID playerId);
    public static TensionState getState(MinecraftServer server, UUID playerId);
    public static boolean forceEvent(ServerPlayerEntity player, String eventId); // bypass cooldown/grace, false si id inconnu ou canRun false
    public static void setTension(ServerPlayerEntity player, float value);
    public static void reset(ServerPlayerEntity player);                        // profil neuf + storyStage 0 si demandé
    public static void runLater(ServerLevel level, int delayTicks, Runnable r); // délègue à DelayedTaskScheduler
    public static void notifyFalseCalm(ServerPlayerEntity player);              // entre en FALSE_CALM 1200 ticks après CRISIS
    // Sélection : événements de HorrorEvents.ALL filtrés par canRun + cooldown individuel + !wasRecent ;
    // poids * config.eventFrequency * état ; min gap = (1200 / eventFrequency) ticks * modulateur état ;
    // tension : événement réussi +6..+14 selon état ; decay 0.35/s ; jumpscare majeur +20.
}
```

`PenumbraPersistentState` (data) : stocke `Map<UUID, PlayerHorrorProfile> profiles`, `int storyStage`, `long worldSeedMirror`. API :
```java
public static PenumbraPersistentState get(MinecraftServer server);
public PlayerHorrorProfile profile(UUID id);   // crée si absent
public int storyStage(); public void advanceStoryStage(int stage);
```
Codec avec `Codec.unboundedMap(Codec.STRING.xmap(UUID::fromString, UUID::toString), PlayerHorrorProfile.CODEC)`. Récupération via `server.overworld().getDataStorage().computeIfAbsent(...)`. ⚠️ La signature exacte de PersistentState en 26.1 (constructeur avec Codec, sans DataFixTypes) doit être encadrée d'un commentaire `// RÉSOLU-EN-26.1.2: persistance 26.1` avec l'alternative 1.21.4 en commentaire.

### 4.6 Événements

`fr.penumbra.event.HorrorEvent` (Agent_Events) :
```java
public interface HorrorEvent {
    String id();
    int weight(TensionState state);                          // 0 = jamais dans cet état
    int cooldownTicks();                                     // par événement
    boolean canRun(ServerPlayerEntity player, PlayerHorrorProfile profile);
    void run(ServerPlayerEntity player);
}
```
`HorrorEvents` : `public static final List<HorrorEvent> ALL;` + `public static HorrorEvent byId(String id);` (null si inconnu). IDs : `footsteps`, `breath`, `fake_creeper`, `torch_sputter`, `door_anomaly`, `distant_sighting`, `silence`, `sleep_interrupt`.

Règles communes (Agent_Events) : sons via `PenumbraNetworking.sendSound(...)` (jamais `level().playSound` côté serveur pour les sons directionnels personnalisés — le packet S2C garantit l'individualisation multijoueur) ; respect de `PenumbraConfig` (worldModificationEnabled, sleepEventsEnabled, hostEnabled) ; aucun scan de blocs > 24³ ; pas d'allocation par tick.

### 4.7 Réseau (Agent_Core pour payloads ; Agent_ClientRender pour récepteurs)

`fr.penumbra.network.PenumbraNetworking` :
```java
public record HorrorSoundS2CPacket(ResourceLocation soundId, double x, double y, double z, float volume, float pitch) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HorrorSoundS2CPacket> TYPE = ...; // id "horror_sound"
    public static final StreamCodec<? super RegistryFriendlyByteBuf, HorrorSoundS2CPacket> CODEC = ...;
}
public record JumpscareS2CPacket(String scareType, float intensity) implements CustomPacketPayload { /* id "jumpscare" */ }
public static void registerPayloads();  // PayloadTypeRegistry.playS2C().register(...)
public static void sendSound(ServerPlayer player, SoundEvent sound, Vec3 pos, float volume, float pitch);
public static void sendJumpscare(ServerPlayer player, String scareType, float intensity);
```
⚠️ Mappings officiels : `CustomPacketPayload`, `ResourceLocation`, `ServerPlayer`, `Vec3` (`net.minecraft.world.phys.Vec3`). Fabric : `ServerPlayNetworking.send(player, packet)`.

`fr.penumbra.client.PenumbraClientNet` (Agent_ClientRender) : `public static void register();` — `ClientPlayNetworking.registerGlobalReceiver(HorrorSoundS2CPacket.TYPE, (packet, context) -> ...)` : joue le son côté client via `client.level.playLocalSound(x,y,z, SoundEvent, SoundSource.HOSTILE, volume * config.jumpscareVolume pour les stingers, pitch, false)` ; jumpscare → `JumpscareManager.trigger(packet.scareType(), packet.intensity())`.

### 4.8 Entité (Agent_EntityAI)

`HostBehavior` enum : `DORMANT, OBSERVING, STALKING, INVESTIGATING, HIDING, LURING, CHASING, ATTACKING, RETREATING, COOLDOWN`.

`HostEntity extends Monster` (`net.minecraft.world.entity.monster.Monster`) :
```java
public HostEntity(EntityType<? extends Monster> type, Level level);
public static AttributeSupplier.Builder createHostAttributes(); // HP 40, speed 0.23, attack 6, followRange 48, knockbackResistance 1.0, armor 2
public HostBehavior getBehavior(); public void setBehavior(HostBehavior b);   // TrackedData<Integer> ordinal
@Override protected void registerGoals();  // goalSelector: 0 VanishWhenSeenGoal, 1 ObservePlayerGoal, 2 StalkPlayerGoal,
                                           //   3 MeleeAttackGoal(this, 1.35, true) (seulement si CHASING/ATTACKING via targetSelector),
                                           //   6 WaterAvoidingRandomStrollGoal(0.6), 7 LookAtPlayerGoal, 8 RandomLookAroundGoal
                                           // targetSelector: NearestAttackableTargetGoal uniquement actif en CHASING (condition custom)
public void checkDespawn(); // no-op : durée de vie contrôlée par les goals/directeur
public static HostEntity spawnNear(ServerLevel level, ServerPlayer target, double minDist, double maxDist);
// 24 essais : angle aléatoire biaisé hors champ de vision, dist min..max, position sombre (light <= 4),
// sol solide + 2 blocs d'air, pas de liquide ; retourne null si échec (jamais d'apparition injuste visible en plein jour à 3 blocs).
```

Goals (package `fr.penumbra.entity.ai`, `net.minecraft.world.entity.ai.goal.Goal`) :
- `VanishWhenSeenGoal(HostEntity host)` : toutes les 5 ticks, si un joueur ≤ 40 blocs a l'Hôte dans son cône de vision (dot(lookVec, dirToHost) > 0.94 ET ligne de vue libre `player.hasLineOfSight(host)`) pendant ≥ 8 ticks cumulés → 70 % : particules + son `HOST_WHISPER` aux joueurs proches via `PenumbraNetworking.sendSound` + `host.discard()` ; 30 % : téléportation 8–14 blocs derrière le joueur le plus proche + `setBehavior(STALKING)`. Config `hostEnabled=false` → discard immédiat.
- `ObservePlayerGoal` : si behavior ∈ {OBSERVING, DORMANT} : immobile, `getLookControl()` vers joueur ≤ 32 blocs ; si joueur < 8 blocs → `setBehavior(RETREATING)` et recul de 6 blocs ; passe en STALKING si non observé 300 ticks.
- `StalkPlayerGoal` : si behavior == STALKING : maintient 12–20 blocs du joueur cible, vitesse ×1.15, s'arrête quand regardé (laisse Vanish agir), contourne via `getNavigation().moveTo(x,y,z,1.15)` recalculé toutes les 40 ticks max.

### 4.9 Client (Agent_ClientRender)

`JumpscareManager` :
```java
public static void trigger(String scareType, float intensity);  // types : "sting", "fake_creeper", "apparition"
public static void tick(Minecraft client);
// - cooldown client 400 ticks entre deux jumpscares (anti-empoisonnement)
// - respect PenumbraConfig : jumpscareFrequency (0 = désactivé), cameraShakeEnabled, jumpscareVolume
// - "sting" : joue HOST_STINGER en local (volume = config.jumpscareVolume * intensity, jamais > 1.0) + shake caméra
//   12 ticks (yaw/pitch += bruit ±(1.2*intensity)°, amorti) + si flashesEnabled : overlay noir 3 ticks
// - "fake_creeper" : son FAKE_CREEPER uniquement
// - "apparition" : whisper + shake léger 6 ticks
// - shakes appliqués en modifiant player.yRot / xRot dans tick client (sans mixin), restaurés par amortissement
```

`render/HostEntityModel` : `public static final EntityModelLayer LAYER = new EntityModelLayer(PenumbraMod.id("host"), "main");` — `EntityModel<LivingEntityRenderState>`… ⚠️ En 1.21.2+ : `public class HostEntityModel extends EntityModel<LivingEntityRenderState>`, construit via `ModelData`/`ModelPartData` 64x64 : `head` (8x8x10 à y=-24), `body` (8x20x4), `left_arm`/`right_arm` (3x16x3 décalés ±5, pivot épaule), `left_leg`/`right_leg` (3x14x3). `public static TexturedModelData getTexturedModelData();` — `setupAnim(LivingEntityRenderState state)` : bras ballants lents quand immobile, swing de marche via `state.walkAnimationPos/Speed`, tête qui pivote lentement vers `state.yRot/xRot` relatifs.

`render/HostEntityRenderer extends MobEntityRenderer<HostEntity, LivingEntityRenderState, HostEntityModel>` : super(context, new HostEntityModel(context.bakeLayer(LAYER)), 0.4f) ; `public ResourceLocation getTextureLocation(LivingEntityRenderState state)` → `PenumbraMod.id("textures/entity/host.png")` ; `public LivingEntityRenderState createRenderState()`.

`particle/AshMoteParticle extends SingleQuadParticle` (ou TextureSheetParticle selon mappings 26.1 — choisir la classe parente encore présente, mettre un commentaire RÉSOLU-EN-26.1.2) : cendre grise qui monte lentement, vie 40–80 ticks, gravité négative légère ; `public static class Factory implements ParticleFactory<SimpleParticleType>`.

### 4.10 Commandes (Agent_Director)
`/penumbra status` (état, tension, dernier événement, storyStage — visible op niveau 2) · `/penumbra tension <0-100>` · `/penumbra event <id>` (ids de HorrorEvents) · `/penumbra spawnhost` · `/penumbra jumpscare <sting|fake_creeper|apparition>` · `/penumbra reset`. Enregistrement via `CommandRegistrationCallback`. Feedback via `source.sendSystemMessage(Component.literal(...))` avec clés de traduction `command.penumbra.*` quand pertinent (littéral accepté en Phase 1 si trad déléguée — utiliser Component.translatable avec les clés définies §4.12).

### 4.11 Événements — comportements précis (Agent_Events)
- `FootstepMimicEvent` (poids fort sous terre/nuit) : 4–7 pas espacés de 6–10 ticks (`HorrorDirector.runLater`), chacun = son `FOOTSTEP_MIMIC` 2–5 blocs derrière le joueur, pitch 0.85–1.0, volume 0.5.
- `BreathBehindEvent` : `BREATH_BEHIND` 1,5 bloc derrière la tête, volume 0.3, pitch 0.8–0.95. +4 tension.
- `FakeCreeperEvent` : `FAKE_CREEPER` à 3 blocs latéral ; 30 % → `sendJumpscare(player,"fake_creeper",0.7f)`.
- `TorchSputterEvent` : torche la plus proche ≤ 24 blocs (scan borné) ; particules `ASH_MOTE` ×12 + `TORCH_SPUTTER` ; si `worldModificationEnabled` et 40 % : remplace par air et restaure l'état d'origine après 1200 ticks via `runLater` (capture `BlockState` + `BlockPos`, vérifie que le bloc est toujours air avant restauration).
- `DoorAnomalyEvent` : porte en bois fermée ≤ 16 blocs → `setBlockState(OPEN=true)` + `DOOR_GROAN` à la porte. Ne casse rien.
- `DistantHostSightingEvent` : si `hostEnabled` → `HostEntity.spawnNear(level, player, 24, 40)` behavior OBSERVING + `HOST_WHISPER` volume 0.4 à la position de l'Hôte ; si spawn null → événement annulé (retour sans effet, ne compte pas comme événement).
- `SilenceEvent` : `SILENCE_DRONE` 12 s volume 0.25 sur le joueur + tension +6.
- `SleepInterruptEvent` : canRun = `player.isSleeping()` && `sleepEventsEnabled` ; réveille (`player.stopSleeping()`), `HOST_WHISPER` au lit, `sendJumpscare(player,"sting",0.6f)`.

### 4.12 Ressources (Agent_Resources)
- `sounds.json` : les 10 ids §4.4, category `hostile` (stinger) / `ambient` (autres), `subtitle` = `subtitles.penumbra.<id>`.
- Lang `en_us.json` + `fr_fr.json` : `entity.penumbra.host` = "The Host" / "l'Hôte" ; `item.penumbra.ash_sigil` = "Ash Sigil" / "Sceau de cendre" ; les 10 sous-titres ; `command.penumbra.*` (feedback) ; `modmenu` non requis.
- `models/item/ash_sigil.json` : `item/generated`, texture `penumbra:item/ash_sigil`.
- `particles/ash_mote.json` : texture `penumbra:ash_mote`.
- `loot_tables/entities/host.json` : 0–1 `penumbra:ash_sigil` (chance 0.35).
- PNG générés par script Python (PIL) : `host.png` 64x64 (silhouette pâle #c9c4bb, bras/jambes assombris #8f8a82, deux points braise #d97b3f sur la zone tête), `ash_sigil.png` 16x16, `ash_mote.png` 8x8, `icon.png` 128x128 (logo PENUMBRA sobre, fond #141216, texte #c9c4bb).
- Sons : tenter `pip install soundfile` → écrire de vrais `.ogg` procéduraux (numpy) dans `assets/penumbra/sounds/` : host_whisper (2 s souffle filtré), host_stinger (0,8 s cluster dissonant -3 dBFS max), host_step, footstep_mimic, breath_behind (3 s), door_groan, fake_creeper (0,7 s), torch_sputter (1,2 s), silence_drone (12 s nappe grave -18 dBFS), heartbeat (2 s). Normaliser tous les pics ≤ -3 dBFS. ÉCHEC soundfile → WAV + note dans SOUNDS.md (conversion : `ffmpeg -i in.wav -c:a libvorbis out.ogg`) ; sounds.json reste inchangé (le jeu loguera un son manquant, le mod reste fonctionnel).
- `docs/ASSETS.md` : specs de chaque asset final (dimensions, palette, prompt de génération) ; `docs/SOUNDS.md` : durée/rôle/normalisation/prompt par son.

### 4.13 Build & docs (Agent_BuildMeta)
- `settings.gradle` (pluginManagement : fabric-loom `id "fabric-loom" version "${fabric_loom_version}"`, repos maven.fabricmc.net + gradle plugin portal + mavenCentral).
- `build.gradle` : plugins java + fabric-loom ; group `fr.penumbra` ; `loom { }` SANS mappings (jeu non-obfusqué) avec commentaire `// 26.1 : pas de couche yarn ; si une version yarn paraît, ajouter mappings ...` ; dépendances : `minecraft "com.mojang:minecraft:26.1.2"`, `modImplementation "net.fabricmc:fabric-loader:0.19.3"`, `modImplementation "net.fabricmc.fabric-api:fabric-api:0.155.2+26.1.2"` ; `java { toolchain languageVersion 25 }` ; tâches sourcesJar + jar (archivesBaseName "penumbra").
- `gradle.properties` : toutes les versions + `fabric_loom_version=1.17.16 (RÉSOLU : dernière stable vérifiée)` commenté, `org.gradle.jvmargs=-Xmx3G`, `org.gradle.java.home` laissé vide avec commentaire JDK 25.
- `fabric.mod.json` : schemaVersion 1, id/name/version, entrypoints `main`/`client`, depends `fabricloader >=0.19.3`, `fabric-api *`, `minecraft ~26.1.2`, `java >=25` ; environment `*` ; icon `assets/penumbra/icon.png` ; description EN+FR courte ; license MIT.
- README.md (FR, professionnel : pitch, features, installation joueur, dev, config, avertissement photosensibilité, crédits) ; INSTALL.md ; COMPILE.md (`gradlew build`, `gradlew runClient`, JDK 25 requis) ; TEST_PROTOCOL.md (protocole de test Phase 1 point par point : boot, `/penumbra status`, forcer chaque événement, vérifier persistance après rechargement de monde, solo + serveur local) ; KNOWN_ISSUES.md (dont : compilation non vérifiée faute de JDK local ; signatures 26.1 marquées RÉSOLU-EN-26.1.2 ; sons placeholder) ; ROADMAP.md (Phases 2–5 détaillées) ; CHANGELOG.md (0.1.0) ; LICENSE MIT ; .gitignore (gradle, run/, build/, .idea, .vscode).

## 5. Interdictions transverses
- Aucune écriture hors de votre liste de fichiers.
- Aucune invention de version au-delà de celles listées/commentées RÉSOLU.
- Aucun accès réseau, fichier personnel ou donnée utilisateur dans le code du mod (règle §12 du prompt).
- Aucun faux message Mojang/Microsoft ; toute illusion reste dans le cadre du jeu.
