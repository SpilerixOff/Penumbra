# SOUNDS.md — Penumbra — Inventaire et specs des sons

> Phase 1 : 10 sons. Phase 2 : +7 sons (SPEC2 §2). Phase 5 (0.5.0) : +6 sons.
> Phase 6 (0.6.0) : +2 sons. 0.16.0 : +1 (`memoire_disc`).
> **0.17.0 : +6** (`listener_click`, `listener_inhale`, `listener_lock`,
> `listener_leave`, `held_breath`, `extra_step`, générés par
> `tools/gen_sounds_017.py`). Soit **32 événements sonores** déclarés,
> générés par `tools/gen_sounds.py`
> (numpy + soundfile, seed fixe, reproductible).
>
> **Nouveaux en 0.5.0** : `ambient_bed_low` / `ambient_bed_mid` /
> `ambient_bed_high` (nappes de 8 s à fondus de 1,5 s intégrés, jouées toutes
> les ~5 s en crossfade par recouvrement — pics -20 / -17 / -14 dBFS),
> `host_whisper_2` (variante de murmure, -3 dBFS), `originel_hum` (bourdon
> 40 Hz + formants fantômes, -18 dBFS), `ritual_release` (descente 120→38 Hz
> + souffle de cendre, -4 dBFS). Prompts de remplacement : mêmes contraintes
> que les fiches ci-dessous (OGG mono 44,1 kHz, mêmes noms) ; pour les nappes,
> CONSERVER les fondus d'1,5 s en tête et queue — le crossfade du client en
> dépend.
> Ce sont des **placeholders fonctionnels** : le jeu les charge et les joue,
> mais ils doivent être remplacés par un sound design final.
>
> Format livré : OGG Vorbis, mono, 44,1 kHz. Normalisation : pics ≤ **-3 dBFS**
> pour tous les sons, sauf `silence_drone` à **-18 dBFS** (nappe subliminale)
> et `distant_hum` à **-20 dBFS** (nappe très grave SPEC2 §2).
>
> Repli documenté : si `soundfile` est indisponible, `gen_sounds.py` écrit des
> `.wav` 16 bits à la place ; conversion attendue avant livraison :
> `ffmpeg -i in.wav -c:a libvorbis out.ogg` (sounds.json reste inchangé, le jeu
> loguerait sinon un son manquant et le mod resterait fonctionnel).

## Comment remplacer un placeholder

1. Produire ou générer le son final (voir prompts ci-dessous).
2. Exporter en **OGG Vorbis, mono, 44,1 kHz**.
3. Normaliser le pic à **≤ -3 dBFS** (-18 dBFS pour `silence_drone`).
4. Déposer le fichier dans `src/main/resources/assets/penumbra/sounds/` sous
   **exactement le même nom** (`<id>.ogg`). Aucune autre modification n'est
   nécessaire : `sounds.json` référence les 25 ids. La tâche
   `validatePenumbraResources` refuse tout fichier manquant ou non déclaré.

---

## Fiche par son

### 1. `host_whisper.ogg` — 2,0 s
- **Rôle narratif** : la voix de l'Hôte. Murmuré indistinct, jamais de mots
  reconnaissables — le doute doit rester entier. Joué lors des apparitions
  distantes, disparitions et interruptions de sommeil.
- **Normalisation** : pic -3 dBFS.
- **Prompt sonore** : « Close-mic whispered breath, unintelligible syllables,
  no recognizable words in any language, dry with very light room tone,
  slightly uneven rhythm like someone speaking through teeth, unsettling but
  quiet, 2 seconds. »

### 2. `host_stinger.ogg` — 0,8 s
- **Rôle narratif** : le choc. Accompagne le jumpscare « sting » et le réveil
  brutal. Cluster dissonant (secondes mineures) + impact bruité aigu.
- **Normalisation** : pic -3 dBFS (**plafond dur** — sécurité d'écoute ; le
  volume perçu est de plus plafonné par `jumpscareVolume` en config).
- **Catégorie** : `hostile` dans sounds.json.
- **Prompt sonore** : « Short horror stinger, 0.8 seconds: dissonant string
  cluster in minor seconds, brutal 10 ms attack, metallic shriek layered with
  a high noise impact, fast exponential decay, no reverb tail, controlled
  loudness, peak -3 dBFS. »

### 3. `host_step.ogg` — 0,5 s
- **Rôle narratif** : le pas de l'Hôte — sourd, trop lourd, trop proche.
  Utilisé quand l'Hôte se déplace en STALKING.
- **Normalisation** : pic -3 dBFS.
- **Prompt sonore** : « Single heavy footstep on packed dirt and stone, deep
  muffled thud around 50–60 Hz with a short dark noise transient, no echo,
  no crunch of gravel, 0.5 seconds. »

### 4. `footstep_mimic.ogg` — 0,45 s
- **Rôle narratif** : l'imitation. Le joueur entend « ses » pas quelques
  secondes après les siens, légèrement creux, légèrement faux.
- **Normalisation** : pic -3 dBFS.
- **Prompt sonore** : « Single hollow footstep, like a bootstep heard through a
  wall or in an empty corridor, slightly boxy midrange, dry, 0.45 seconds,
  subtly wrong compared to a normal step. »

### 5. `breath_behind.ogg` — 3,0 s
- **Rôle narratif** : une respiration lente juste derrière la tête du joueur
  (1,5 bloc). Deux cycles inspire/expire, proximité intime.
- **Normalisation** : pic -3 dBFS.
- **Prompt sonore** : « Slow human-like breathing directly behind the
  listener's head, two full inhale/exhale cycles in 3 seconds, close-mic,
  binaural intimacy, slightly raspy exhale, no words, no mouth clicks. »

### 6. `door_groan.ogg` — 1,6 s
- **Rôle narratif** : une porte en bois s'ouvre seule à proximité
  (DoorAnomalyEvent). Grincement grave descendant.
- **Normalisation** : pic -3 dBFS.
- **Prompt sonore** : « Old wooden door slowly opening on dry hinges, low
  groaning creak that bends downward in pitch, wood friction texture, 1.6
  seconds, no slam, no handle click. »

### 7. `fake_creeper.ogg` — 0,7 s
- **Rôle narratif** : le leurre sonore signature — un sifflement de Creeper
  qui ne mène à aucune explosion. La tension retombe sans résolution.
- **Normalisation** : pic -3 dBFS.
- **Prompt sonore** : « A short fuse-hiss identical in character to a creeper
  priming: bright band-passed white noise 4–8 kHz, rising tension over 0.7
  seconds, then a dry abrupt cut — and NO explosion. »
- **Note** : le placeholder est une synthèse originale « dans l'esprit » du
  sifflement ; ne pas extraire le son vanilla du jeu (actif Mojang sous
  licence).

### 8. `torch_sputter.ogg` — 1,2 s
- **Rôle narratif** : la torche la plus proche crache et parfois s'éteint
  (TorchSputterEvent). Cérémonie sonore d'une lumière qui meurt.
- **Normalisation** : pic -3 dBFS.
- **Prompt sonore** : « A torch flame sputtering: sparse dry crackles and small
  pops over a very low fire bed, 1.2 seconds, ends weaker than it started, as
  if the flame is about to die. »

### 9. `silence_drone.ogg` — 12,0 s
- **Rôle narratif** : l'anti-son. Nappe grave quasi subliminale pendant
  SilenceEvent — le monde paraît « sourd » ; le joueur tend l'oreille vers un
  silence qui n'en est pas un.
- **Normalisation** : pic **-18 dBFS** (volontairement sous le seuil d'attention).
- **Stream** : `stream: true` dans sounds.json (fichier long).
- **Prompt sonore** : « Very low ambient drone, 12 seconds: detuned sub-bass
  sine pairs around 40–80 Hz creating slow beats, extremely quiet, gentle
  0.1 Hz amplitude swell, no melody, no rhythm, felt more than heard,
  peak -18 dBFS. »

### 10. `heartbeat.ogg` — 2,0 s
- **Rôle narratif** : le corps du joueur trahit sa peur. Deux cycles lub-dub
  pendant les phases de forte tension.
- **Normalisation** : pic -3 dBFS.
- **Prompt sonore** : « Human heartbeat heard from inside the body, two
  lub-dub cycles in 2 seconds (~60 bpm), deep and muffled around 45–55 Hz,
  slightly accelerating feel, dry, no room reverb. »

---

## Phase 2 (SPEC2 §2) — 7 sons ajoutés

### 11. `binoculars_on.ogg` — 0,3 s
- **Rôle narratif** : les jumelles se déploient devant les yeux. Clic
  mécanique doux en deux temps (claquement + enclenchement) — un objet
  utilitaire, rassurant, contraste volontaire avec l'horreur ambiante.
- **Normalisation** : pic -3 dBFS. Volume en jeu plafonné à
  `jumpscareVolume * 0.4` (SPEC2 §8) : jamais de son fort.
- **Prompt sonore** : « Soft mechanical click of compact binoculars being
  raised: two gentle plastic-metal latch clicks in 0.3 seconds, dry, close-mic,
  quiet and utilitarian, no snap, no electronic beep. »

### 12. `binoculars_off.ogg` — 0,25 s
- **Rôle narratif** : les jumelles redescendent. Variante plus grave et plus
  courte du clic d'activation, avec une petite friction de descente.
- **Normalisation** : pic -3 dBFS. Même plafond de volume en jeu.
- **Prompt sonore** : « Softer, lower mechanical click of binoculars being
  lowered in 0.25 seconds, brief fabric-plastic friction trailing off, dry,
  close-mic, quiet. »

### 13. `lure_door.ogg` — 1,4 s
- **Rôle narratif** : le leurre. Une porte grince quelque part — lointaine,
  voilée, comme à travers les murs. Aucune porte ne s'est réellement ouverte
  (aucune modification de bloc, SPEC2 §6).
- **Normalisation** : pic -3 dBFS.
- **Prompt sonore** : « Distant wooden door creak heard through walls: low
  muffled groan bending downward, all high frequencies absorbed, 1.4 seconds,
  indistinct direction, no slam, no handle click. »

### 14. `distant_hum.ogg` — 6,0 s
- **Rôle narratif** : la présence, plus que l'événement. Nappe très grave
  accompagnant `distant_light` — le monde lui-même semble vrombir.
- **Normalisation** : pic **-20 dBFS** (SPEC2 §2 — volontairement sous le
  seuil d'attention, encore plus bas que `silence_drone`).
- **Stream** : `stream: true` dans sounds.json (fichier long).
- **Prompt sonore** : « Extremely low distant hum, 6 seconds: sub-bass detuned
  sine cluster around 35–75 Hz with slow amplitude swell, felt in the chest
  more than heard, no melody, no pulse, industrial yet organic, peak -20 dBFS. »

### 15. `chase_stinger.ogg` — 1,2 s
- **Rôle narratif** : la poursuite commence (`HorrorDirector.startChase`).
  Cluster dissonant montant — plus long et plus « ascendant » que
  `host_stinger` : la terreur ne retombe pas, elle démarre.
- **Normalisation** : pic -3 dBFS (**plafond dur** — sécurité d'écoute).
- **Catégorie** : `hostile` dans sounds.json.
- **Prompt sonore** : « Horror chase stinger, 1.2 seconds: rising dissonant
  string cluster in minor seconds, brutal 6 ms attack, high noise impact,
  tense ascending glissando with fast decay, no reverb tail, controlled
  loudness, peak -3 dBFS. »

### 16. `footstep_mimic_2.ogg` — 0,4 s
- **Rôle narratif** : variante de `footstep_mimic`. Le pas n'est plus seulement
  imité, il est **traîné** — un raclement après l'impact, encore plus faux.
  Utilisé pour varier FootstepMimicEvent et les réactions sonores de l'Hôte.
- **Normalisation** : pic -3 dBFS.
- **Prompt sonore** : « Single dragging footstep: a dull low thud followed by a
  short scrape of a sole pulled across stone, 0.4 seconds, dry, slightly
  wrong, as if whatever imitates the player does not lift its feet. »

### 17. `breath_behind_2.ogg` — 2,4 s
- **Rôle narratif** : variante de `breath_behind`. Un seul cycle long, rauque
  et saccadé — la respiration d'une chose qui ne sait plus tout à fait
  comment respirer. Utilisé par les réactions jumelles (`sound_behind`).
- **Normalisation** : pic -3 dBFS.
- **Prompt sonore** : « One long raspy breathing cycle directly behind the
  listener's head, 2.4 seconds: slow inhale, then a stuttering rattly exhale
  with an irregular tremor, close-mic binaural intimacy, no words, no mouth
  clicks. »

---

## Phases 5 et 6 — ambiance, rituel et screamers

| Son | Durée | Pic | Rôle |
|---|---:|---:|---|
| `ambient_bed_low.ogg` | 8,0 s | -20 dBFS | Nappe discrète de l'état WATCHED |
| `ambient_bed_mid.ogg` | 8,0 s | -17 dBFS | Nappe de manifestation |
| `ambient_bed_high.ogg` | 8,0 s | -14 dBFS | Nappe de traque/crise |
| `host_whisper_2.ogg` | 2,2 s | -3 dBFS | Variante lente du murmure de l'Hôte |
| `originel_hum.ogg` | 5,0 s | -18 dBFS | Bourdon très grave de l'Originel |
| `ritual_release.ogg` | 6,0 s | -4 dBFS | Descente et souffle de la libération rituelle |
| `screamer_shriek.ogg` | 0,9 s | -3 dBFS | Hurlement court du visage plein écran |
| `voice_hey.ogg` | 0,5 s | -3 dBFS | Voix-leurre brève de `mining_bait` |

Les trois nappes conservent leurs fondus de 1,5 s pour le recouvrement client.
Les deux sons de screamer sont volontairement classés parmi les contenus
sensibles et sont coupés avec `screamersEnabled: false`.

---

## Vérification

Après tout remplacement, rejouer `tools/gen_sounds.py` n'est PAS requis.
Vérifier simplement : nom de fichier exact, format OGG Vorbis, mono 44,1 kHz,
et pic mesuré conforme (ex. `ffmpeg -i <fichier> -af volumedetect -f null -`).

## §0.17.0 — L'Écouteur et les événements sonores

Générés par `tools/gen_sounds_017.py` (numpy + ffmpeg, seed `20260717`, sortie
identique à chaque exécution). Mono 44,1 kHz, Vorbis ~86 kb/s, comme le reste
de la banque.

| Fichier | Durée | Pic | Intention |
|---|---|---|---|
| `listener_click.ogg` | 0,18 s | -6 dBFS | Écholocation : un os sec frappé, plus une résonance de cavité vide à 2450 et 3170 Hz. Aucune queue musicale — le clic doit rester un repère de distance, pas une nappe. |
| `listener_inhale.ogg` | 1,70 s | -8 dBFS | Elle se met à l'écoute. Bruit filtré dont la bande monte de 220 à 740 Hz pendant que l'enveloppe gonfle, puis coupure : la gorge se resserre. Grondement 45–120 Hz dessous. |
| `listener_lock.ogg` | 1,05 s | -3 dBFS | Elle a entendu. Balayage 165→880 Hz doublé à la quinte diminuée (×1,414) : dissonance volontaire, jamais un accord. Attaque de 6 ms. |
| `listener_leave.ogg` | 2,30 s | -9 dBFS | Elle renonce. Raclement dont le spectre descend de 2600 à 600 Hz, plus une sub-basse 78→42 Hz : la source s'éloigne. |
| `held_breath.ogg` | 3,10 s | -14 dBFS | Le monde inspire. Sub-basse 38→74 Hz, souffle 160–900 Hz, battement d'intermodulation à 0,7 Hz. Volontairement sous le seuil conscient : le corps l'entend, pas l'oreille. |
| `extra_step.ogg` | 0,22 s | -7 dBFS | Le pas de trop. Talon 70–320 Hz plus un grain 1400–4800 Hz de 50 ms. Aucune réverbération : il doit sembler venir du même sol que le vôtre. |

Contraintes de remplacement par un sound design final : conserver les noms,
le format (OGG mono 44,1 kHz) et surtout les durées de `listener_click`
(≤ 0,25 s, joué toutes les 7 ticks en charge) et `extra_step` (≤ 0,25 s, calé
sur la cadence de pas vanilla). `held_breath` doit rester une nappe discrète :
un pic au-dessus de -10 dBFS transformerait la promesse en annonce.
