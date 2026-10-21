#!/usr/bin/env python3
"""Génère les sons procéduraux de Penumbra (SPEC §4.12, SPEC2 §2).

Phase 1 : 10 sons. Phase 2 : +7 sons (jumelles, leurres, poursuite, variantes).
Écrit de vrais .ogg (Vorbis) via soundfile dans assets/penumbra/sounds/.
Tous les pics sont normalisés ≤ -3 dBFS, sauf silence_drone (-18 dBFS) et
distant_hum (-20 dBFS, nappe subliminale SPEC2 §2).
Si soundfile est indisponible, repli en .wav 16 bits (voir docs/SOUNDS.md
pour la conversion ffmpeg : `ffmpeg -i in.wav -c:a libvorbis out.ogg`).
"""

import math
import os

import numpy as np

SR = 44100
BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
OUT = os.path.join(BASE, "assets", "penumbra", "sounds")

try:
    import soundfile as sf
    HAVE_SF = True
except ImportError:
    HAVE_SF = False

rng = np.random.default_rng(20260101)


def dbfs(peak):
    return 20.0 * math.log10(max(peak, 1e-12))


def normalize(x, peak_db=-3.0):
    target = 10.0 ** (peak_db / 20.0)
    m = float(np.max(np.abs(x)))
    if m > 0:
        x = x * (target / m)
    return x


def bandpass(x, lo, hi):
    """Filtre passe-bande brutal par FFT (zéro hors [lo, hi] Hz)."""
    spec = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(len(x), 1.0 / SR)
    spec[(freqs < lo) | (freqs > hi)] = 0.0
    return np.fft.irfft(spec, len(x))


def envelope(n, attack=0.01, release=0.2):
    env = np.ones(n)
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    env[:a] = np.linspace(0.0, 1.0, a)
    env[-r:] = np.linspace(1.0, 0.0, r)
    return env


def sine(freq, n, phase=0.0):
    t = np.arange(n) / SR
    return np.sin(2.0 * np.pi * freq * t + phase)


def noise(n):
    return rng.standard_normal(n)


def host_whisper():
    """2 s — souffle filtré, syllabes chuchotées indistinctes."""
    n = int(2.0 * SR)
    x = bandpass(noise(n), 500.0, 2800.0)
    # syllabes : 5 bouffées d'amplitude irrégulières
    t = np.arange(n) / SR
    mod = np.zeros(n)
    for start, dur in ((0.05, 0.35), (0.5, 0.3), (0.9, 0.45), (1.4, 0.25), (1.7, 0.28)):
        s, e = int(start * SR), min(n, int((start + dur) * SR))
        seg = e - s
        mod[s:e] += np.sin(np.linspace(0, np.pi, seg)) ** 1.5
    x = x * mod * envelope(n, 0.02, 0.3)
    return x


def host_stinger():
    """0,8 s — cluster dissonant (secondes mineures), attaque brutale."""
    n = int(0.8 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for f in (220.0, 233.0, 311.0, 466.0, 622.0, 627.0):  # clusters désaccordés
        x += sine(f, n) * rng.uniform(0.5, 1.0)
    x += bandpass(noise(n), 2000.0, 9000.0) * 0.6  # choc bruité aigu
    env = np.exp(-t * 5.5)
    env[: int(0.008 * SR)] = np.linspace(0, 1, int(0.008 * SR))  # attaque 8 ms
    return x * env


def host_step():
    """0,5 s — pas lourd, sourd, trop proche."""
    n = int(0.5 * SR)
    t = np.arange(n) / SR
    x = sine(52.0 - 18.0 * t, n)  # glissando grave descendant
    x += bandpass(noise(n), 80.0, 400.0) * 0.5
    return x * np.exp(-t * 9.0) * envelope(n, 0.004, 0.15)


def footstep_mimic():
    """0,45 s — pas creux, imitation lointaine du pas du joueur."""
    n = int(0.45 * SR)
    t = np.arange(n) / SR
    x = sine(64.0 - 20.0 * t, n) * 0.8
    x += bandpass(noise(n), 300.0, 900.0) * 0.35  # cavité plus claire
    return x * np.exp(-t * 10.0) * envelope(n, 0.004, 0.15)


def breath_behind():
    """3 s — respiration lente juste derrière la tête (deux cycles)."""
    n = int(3.0 * SR)
    x = bandpass(noise(n), 300.0, 1800.0)
    mod = np.zeros(n)
    # inspiration 0.0-0.7, expiration 0.8-1.6, inspiration 1.7-2.3, expiration 2.35-3.0
    for start, dur, gain in ((0.0, 0.7, 0.8), (0.8, 0.8, 1.0), (1.7, 0.6, 0.7), (2.35, 0.65, 0.9)):
        s, e = int(start * SR), min(n, int((start + dur) * SR))
        seg = e - s
        mod[s:e] += np.sin(np.linspace(0, np.pi, seg)) * gain
    return x * mod * envelope(n, 0.05, 0.2)


def door_groan():
    """1,6 s — grincement grave de porte qui s'ouvre seule."""
    n = int(1.6 * SR)
    t = np.arange(n) / SR
    f0 = 85.0 - 30.0 * (t / 1.6)  # descente lente
    phase = np.cumsum(2.0 * np.pi * f0 / SR)
    x = np.zeros(n)
    for h in (1, 2, 3, 5):
        x += np.sin(h * phase) / h  # timbre âpre type scie
    x *= 1.0 + 0.6 * np.sin(2.0 * np.pi * 7.0 * t)  # tremolo de friction
    x += bandpass(noise(n), 150.0, 700.0) * 0.4
    return x * envelope(n, 0.08, 0.5)


def fake_creeper():
    """0,7 s — sifflement de Creeper qui ne mène à rien."""
    n = int(0.7 * SR)
    x = bandpass(noise(n), 3500.0, 8500.0)
    t = np.arange(n) / SR
    env = np.minimum(1.0, t / 0.05) * (1.0 + 0.8 * t)  # montée de tension
    env[-int(0.06 * SR):] *= np.linspace(1.0, 0.0, int(0.06 * SR))  # coupure sèche
    return x * env


def torch_sputter():
    """1,2 s — crépitements clairsemés de torche qui crache."""
    n = int(1.2 * SR)
    x = bandpass(noise(n), 100.0, 500.0) * 0.05  # lit de flamme grave
    for _ in range(26):  # impulsions de crépitement
        pos = int(rng.uniform(0.0, 0.95) * n)
        ln = int(rng.uniform(0.002, 0.006) * SR)
        e = min(n, pos + ln)
        seg = e - pos
        burst = bandpass(noise(seg), 1200.0, 7000.0) * np.exp(-np.linspace(0, 4, seg))
        x[pos:e] += burst * rng.uniform(0.3, 1.0)
    return x * envelope(n, 0.01, 0.1)


def silence_drone():
    """12 s — nappe grave quasi subliminale (pic -18 dBFS)."""
    n = int(12.0 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for f in (41.2, 41.6, 55.0, 82.4, 82.9):  # paires désaccordées = battements lents
        x += sine(f, n) * 0.8
    x += bandpass(noise(n), 30.0, 120.0) * 0.15
    x *= 0.75 + 0.25 * np.sin(2.0 * np.pi * 0.08 * t)  # houle très lente
    return x * envelope(n, 1.5, 2.5)


def heartbeat():
    """2 s — deux cycles lub-dub, cœur qui monte dans les oreilles."""
    n = int(2.0 * SR)
    x = np.zeros(n)

    def thump(start, freq, gain):
        s = int(start * SR)
        ln = int(0.16 * SR)
        e = min(n, s + ln)
        seg = e - s
        tt = np.arange(seg) / SR
        x[s:e] += np.sin(2.0 * np.pi * freq * tt) * np.exp(-tt * 22.0) * gain

    thump(0.00, 50.0, 1.0)
    thump(0.28, 44.0, 0.7)
    thump(1.00, 52.0, 1.0)
    thump(1.28, 46.0, 0.7)
    return x * envelope(n, 0.005, 0.3)


# ---------------------------------------------------------------------------
# Phase 2 (SPEC2 §2) : 7 nouveaux générateurs.
# ---------------------------------------------------------------------------


def binoculars_on():
    """0,3 s — clic mécanique doux : les jumelles se déploient devant les yeux."""
    n = int(0.3 * SR)
    x = np.zeros(n)

    def click(start, freq, gain, decay):
        s = int(start * SR)
        ln = int(0.045 * SR)
        e = min(n, s + ln)
        seg = e - s
        tt = np.arange(seg) / SR
        # transient bruité + résonance courte de plastique/métal léger
        burst = bandpass(noise(seg), freq * 0.5, freq * 2.5) * np.exp(-tt * decay)
        ring = sine(freq, seg) * np.exp(-tt * decay * 1.4)
        x[s:e] += (burst * 0.7 + ring * 0.3) * gain

    click(0.00, 2400.0, 1.0, 90.0)   # claquement initial
    click(0.11, 1800.0, 0.65, 80.0)  # enclenchement
    return x * envelope(n, 0.002, 0.08)


def binoculars_off():
    """0,25 s — clic mécanique doux plus grave : les jumelles redescendent."""
    n = int(0.25 * SR)
    x = np.zeros(n)
    s = 0
    ln = int(0.05 * SR)
    tt = np.arange(ln) / SR
    burst = bandpass(noise(ln), 900.0, 3600.0) * np.exp(-tt * 100.0)
    ring = sine(1450.0, ln) * np.exp(-tt * 150.0)
    x[s:s + ln] += burst * 0.7 + ring * 0.3
    # petite friction de descente
    t = np.arange(n) / SR
    x += bandpass(noise(n), 400.0, 1200.0) * 0.12 * np.exp(-t * 14.0)
    return x * envelope(n, 0.002, 0.06)


def lure_door():
    """1,4 s — grincement de porte lointain : filtré, voilé, comme à travers les murs."""
    n = int(1.4 * SR)
    t = np.arange(n) / SR
    f0 = 70.0 - 22.0 * (t / 1.4)  # descente lente, plus grave que door_groan
    phase = np.cumsum(2.0 * np.pi * f0 / SR)
    x = np.zeros(n)
    for h in (1, 2, 3):
        x += np.sin(h * phase) / h
    x *= 1.0 + 0.5 * np.sin(2.0 * np.pi * 5.5 * t)  # tremolo de friction
    # voile de distance : coupe tout l'aigu, lit de bruit grave feutré
    x = bandpass(x, 40.0, 600.0)
    x += bandpass(noise(n), 80.0, 350.0) * 0.3
    return x * envelope(n, 0.15, 0.6)


def distant_hum():
    """6 s — nappe très grave et lointaine (pic -20 dBFS), présence plus qu'événement."""
    n = int(6.0 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for f in (36.0, 36.4, 54.0, 72.2):  # sous-graves désaccordés, battements lents
        x += sine(f, n) * 0.8
    x += bandpass(noise(n), 25.0, 90.0) * 0.2
    x *= 0.7 + 0.3 * np.sin(2.0 * np.pi * 0.11 * t)  # houle lente
    return x * envelope(n, 1.2, 1.8)


def chase_stinger():
    """1,2 s — stinger de poursuite : cluster dissonant montant, attaque brutale."""
    n = int(1.2 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    # cluster grave désaccordé qui monte en tension (glissando progressif)
    for f0, f1 in ((110.0, 116.5), (165.0, 174.6), (220.0, 233.0), (466.0, 493.9)):
        f = f0 + (f1 - f0) * (t / 1.2)
        phase = np.cumsum(2.0 * np.pi * f / SR)
        x += np.sin(phase) * rng.uniform(0.5, 1.0)
    x += bandpass(noise(n), 1800.0, 8500.0) * 0.7  # impact bruité aigu
    env = np.exp(-t * 3.2)
    env[: int(0.006 * SR)] = np.linspace(0, 1, int(0.006 * SR))  # attaque 6 ms
    return x * env


def footstep_mimic_2():
    """0,4 s — variante de footstep_mimic : pas raclé, traînant, encore plus faux."""
    n = int(0.4 * SR)
    t = np.arange(n) / SR
    x = sine(58.0 - 16.0 * t, n) * 0.7
    # traînement : raclement médium après l'impact
    scrape = bandpass(noise(n), 500.0, 1400.0)
    scrape_env = np.exp(-t * 6.5) * np.minimum(1.0, t / 0.05)
    x += scrape * scrape_env * 0.5
    return x * np.exp(-t * 8.0) * envelope(n, 0.004, 0.12)


def breath_behind_2():
    """2,4 s — variante de breath_behind : un seul cycle long, rauque et saccadé."""
    n = int(2.4 * SR)
    x = bandpass(noise(n), 350.0, 2200.0)
    mod = np.zeros(n)
    # inspiration lente 0.0-1.0, expiration rauque 1.1-2.3
    for start, dur, gain in ((0.0, 1.0, 0.75), (1.1, 1.2, 1.0)):
        s, e = int(start * SR), min(n, int((start + dur) * SR))
        seg = e - s
        mod[s:e] += np.sin(np.linspace(0, np.pi, seg)) * gain
    # saccades : tremolo irrégulier pendant l'expiration
    t = np.arange(n) / SR
    trem = 1.0 + 0.35 * np.sin(2.0 * np.pi * 11.0 * t) * np.clip((t - 1.1) * 3.0, 0.0, 1.0)
    x = x * mod * trem
    # raucité : légère distorsion douce sur l'expiration
    x = np.tanh(x * 1.6)
    return x * envelope(n, 0.08, 0.25)


# ---------------------------------------------------------------------------
# Phase 5 (0.5.0) — nappes ambiantes, variantes, Originel, rituel.
# Les trois nappes ont des fondus de 1,5 s intégrés : jouées toutes les 5 s
# (segments de 8 s), elles se recouvrent en crossfade quasi continu.
# ---------------------------------------------------------------------------

def _bed_fade(x, fade=1.5):
    n = len(x)
    f = max(1, int(fade * SR))
    env = np.ones(n)
    env[:f] = np.sin(np.linspace(0.0, np.pi / 2.0, f)) ** 2
    env[-f:] = np.sin(np.linspace(np.pi / 2.0, 0.0, f)) ** 2
    return x * env


def _slow_wobble(n, hz, depth):
    t = np.arange(n) / SR
    return 1.0 - depth + depth * 0.5 * (1.0 + np.sin(2.0 * np.pi * hz * t))


def ambient_bed_low():
    """8 s — nappe WATCHED : deux sub-sinus désaccordés + plancher de bruit grave."""
    n = int(8.0 * SR)
    x = 0.6 * sine(55.0, n) + 0.45 * sine(82.4, n, phase=1.3)
    x *= _slow_wobble(n, 0.11, 0.35)
    x += 0.25 * bandpass(noise(n), 30.0, 140.0) * _slow_wobble(n, 0.07, 0.5)
    return _bed_fade(x)


def ambient_bed_mid():
    """8 s — nappe MANIFESTATION : sub + quinte fausse + filet d'air haut instable."""
    n = int(8.0 * SR)
    x = 0.55 * sine(55.0, n) + 0.4 * sine(110.8, n, phase=0.6)
    x *= _slow_wobble(n, 0.13, 0.4)
    x += 0.2 * bandpass(noise(n), 40.0, 200.0)
    air = bandpass(noise(n), 2200.0, 3200.0) * _slow_wobble(n, 0.21, 0.9)
    x += 0.06 * air
    return _bed_fade(x)


def ambient_bed_high():
    """8 s — nappe HUNT/CRISIS : cluster dissonant serré + battements + riser discret."""
    n = int(8.0 * SR)
    t = np.arange(n) / SR
    x = 0.4 * sine(58.0, n) + 0.35 * sine(233.1, n) + 0.32 * sine(246.9, n, phase=2.1)
    x *= _slow_wobble(n, 0.17, 0.45)
    x += 0.22 * bandpass(noise(n), 60.0, 400.0)
    riser = sine(320.0 + 90.0 * t / 8.0, n) * np.linspace(0.0, 1.0, n) ** 2
    x += 0.1 * riser
    return _bed_fade(x)


def host_whisper_2():
    """2,2 s — variante de murmure : syllabes plus lentes, bande plus grave, fin suspendue."""
    n = int(2.2 * SR)
    x = bandpass(noise(n), 380.0, 2200.0)
    mod = np.zeros(n)
    for start, dur in ((0.1, 0.5), (0.75, 0.4), (1.3, 0.6)):
        s, e = int(start * SR), min(n, int((start + dur) * SR))
        mod[s:e] += np.sin(np.linspace(0, np.pi, e - s)) ** 1.2
    x = x * mod * envelope(n, 0.03, 0.05)  # release très court : coupé net, pas fini
    return x


def originel_hum():
    """5 s — bourdon de l'Originel : 40 Hz + formants vocaliques fantômes, très bas."""
    n = int(5.0 * SR)
    x = 0.7 * sine(40.0, n) * _slow_wobble(n, 0.09, 0.3)
    for f, a in ((160.0, 0.12), (490.0, 0.07), (910.0, 0.04)):
        x += a * sine(f, n, phase=f) * _slow_wobble(n, 0.15 + f / 9000.0, 0.8)
    x += 0.08 * bandpass(noise(n), 25.0, 90.0)
    return x * envelope(n, 1.2, 1.6)


def ritual_release():
    """6 s — libération du rituel : descente 120→38 Hz, souffle de cendre, longue queue."""
    n = int(6.0 * SR)
    t = np.arange(n) / SR
    freq = 120.0 * np.exp(-t / 2.4) + 38.0
    phase = 2.0 * np.pi * np.cumsum(freq) / SR
    x = 0.8 * np.sin(phase) * np.exp(-t / 3.2)
    ash = bandpass(noise(n), 900.0, 6500.0) * np.exp(-(t - 1.0) ** 2 / 0.18)
    x += 0.3 * ash
    x += 0.15 * bandpass(noise(n), 30.0, 120.0) * np.exp(-t / 2.0)
    return x * envelope(n, 0.05, 2.5)


def screamer_shriek():
    """0,9 s — hurlement du screamer : cluster inharmonique + bruit dense + chute de pitch."""
    n = int(0.9 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for f, a in ((720.0, 0.9), (1123.0, 0.7), (1531.0, 0.55), (2417.0, 0.4), (3251.0, 0.3)):
        freq = f * (1.0 - 0.35 * t / 0.9)  # chute de ~35 % sur la durée
        phase = 2.0 * np.pi * np.cumsum(freq) / SR
        x += a * np.sin(phase + rng.random() * 6.28)
    x += 0.8 * bandpass(noise(n), 300.0, 6000.0)
    x *= 1.0 + 0.4 * np.sin(2.0 * np.pi * 31.0 * t)  # tremolo rauque
    x = np.tanh(2.2 * x)  # saturation : cri, pas note
    return x * envelope(n, 0.004, 0.25)


def voice_hey():
    """0,5 s — aboiement vocal type « HÉ » : source sawtooth + formants /ɛ/ brutaux."""
    n = int(0.5 * SR)
    t = np.arange(n) / SR
    f0 = 190.0 * (1.0 + 0.25 * np.exp(-t / 0.06))  # attaque haute qui retombe
    phase = 2.0 * np.pi * np.cumsum(f0) / SR
    saw = 2.0 * ((phase / (2.0 * np.pi)) % 1.0) - 1.0  # source glottale grossière
    v = np.zeros(n)
    for f, a, bw in ((600.0, 1.0, 90.0), (1750.0, 0.7, 120.0), (2500.0, 0.4, 160.0)):
        v += a * bandpass(saw, f - bw, f + bw)  # formants de /ɛ/
    v += 0.15 * bandpass(noise(n), 1500.0, 5000.0) * np.exp(-t / 0.05)  # attaque « h »
    v = np.tanh(3.0 * v)
    return v * envelope(n, 0.006, 0.18)


SOUNDS = {
    "host_whisper": (host_whisper, -3.0),
    "host_stinger": (host_stinger, -3.0),
    "host_step": (host_step, -3.0),
    "footstep_mimic": (footstep_mimic, -3.0),
    "breath_behind": (breath_behind, -3.0),
    "door_groan": (door_groan, -3.0),
    "fake_creeper": (fake_creeper, -3.0),
    "torch_sputter": (torch_sputter, -3.0),
    "silence_drone": (silence_drone, -18.0),
    "heartbeat": (heartbeat, -3.0),
    # Phase 2 (SPEC2 §2)
    "binoculars_on": (binoculars_on, -3.0),
    "binoculars_off": (binoculars_off, -3.0),
    "lure_door": (lure_door, -3.0),
    "distant_hum": (distant_hum, -20.0),
    "chase_stinger": (chase_stinger, -3.0),
    "footstep_mimic_2": (footstep_mimic_2, -3.0),
    "breath_behind_2": (breath_behind_2, -3.0),
    # Phase 5 (0.5.0)
    "ambient_bed_low": (ambient_bed_low, -20.0),
    "ambient_bed_mid": (ambient_bed_mid, -17.0),
    "ambient_bed_high": (ambient_bed_high, -14.0),
    "host_whisper_2": (host_whisper_2, -3.0),
    "originel_hum": (originel_hum, -18.0),
    "ritual_release": (ritual_release, -4.0),
    # 0.6.0 : screamers
    "screamer_shriek": (screamer_shriek, -3.0),
    "voice_hey": (voice_hey, -3.0),
}


def write_wav_16(path, x):
    import wave

    pcm = np.clip(x, -1.0, 1.0)
    pcm16 = (pcm * 32767.0).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm16.tobytes())


def main():
    os.makedirs(OUT, exist_ok=True)
    mode = "ogg" if HAVE_SF else "wav"
    print("mode:", mode, "(soundfile" + (" OK)" if HAVE_SF else " ABSENT — repli WAV)"))
    for name, (fn, peak_db) in SOUNDS.items():
        x = normalize(fn().astype(np.float64), peak_db)
        actual = dbfs(float(np.max(np.abs(x))))
        dur = len(x) / SR
        if HAVE_SF:
            path = os.path.join(OUT, name + ".ogg")
            # Écriture par blocs : sf.write() one-shot plante sur les longs
            # fichiers (libsndfile/vorbis) dans cette build.
            with sf.SoundFile(path, "w", samplerate=SR, channels=1,
                              format="OGG", subtype="VORBIS") as f:
                for i in range(0, len(x), SR):
                    f.write(x[i:i + SR])
        else:
            path = os.path.join(OUT, name + ".wav")
            write_wav_16(path, x)
        print(f"{name}: {dur:.2f} s, pic {actual:.1f} dBFS -> {os.path.basename(path)}")
    print("Sons générés.")


if __name__ == "__main__":
    main()
