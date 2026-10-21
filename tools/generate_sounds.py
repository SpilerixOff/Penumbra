#!/usr/bin/env python3
"""Synthèse des sons d'horreur Penumbra (0.15.0) — déterministe.

Remplace les placeholders par un sound design en couches et produit des
VARIANTES par événement (le tirage aléatoire est fait par sounds.json côté
vanilla) : plusieurs variantes réduisent la répétition sans l'interdire.

Après ce script historique, exécuter gen_sounds_021.py pour restaurer les
nappes de huit secondes attendues par le client actuel.

Tout est mono 44,1 kHz (l'atténuation spatiale de Minecraft exige du mono
pour les sons positionnés). Sortie WAV → ffmpeg (libvorbis, q4).

Usage : python3 tools/generate_sounds.py [--out src/main/resources/assets/penumbra/sounds]
"""

import argparse
import os
import shutil
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100


# ============================================================ boîte à outils
def t_axis(duration):
    return np.arange(int(duration * SR)) / SR


def env_adsr(n, attack, decay, sustain_level, release):
    """Enveloppe ADSR en échantillons ; sustain implicite entre decay et release."""
    a = max(1, int(attack * SR))
    d = max(1, int(decay * SR))
    r = max(1, int(release * SR))
    s = max(0, n - a - d - r)
    return np.concatenate([
        np.linspace(0.0, 1.0, a),
        np.linspace(1.0, sustain_level, d),
        np.full(s, sustain_level),
        np.linspace(sustain_level, 0.0, r),
    ])[:n]


def fade(x, fade_in=0.01, fade_out=0.05):
    y = x.copy()
    fi = min(len(y), max(1, int(fade_in * SR)))
    fo = min(len(y), max(1, int(fade_out * SR)))
    y[:fi] *= np.linspace(0, 1, fi)
    y[-fo:] *= np.linspace(1, 0, fo)
    return y


def noise(n, rng):
    return rng.standard_normal(n)


def pink(n, rng):
    """Bruit rose approché par filtrage 1/f dans le domaine spectral."""
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = 1.0
    spec /= np.sqrt(f)
    return np.fft.irfft(spec, n)


def bandpass(x, low, high, order=2.0):
    """Passe-bande spectral doux (pentes gaussiennes, sans phase)."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    mask = np.ones_like(f)
    with np.errstate(divide="ignore"):
        mask *= np.exp(-np.maximum(0, np.log2(np.maximum(f, 1e-9) / max(high, 1e-9))) ** 2 * order)
        mask *= np.exp(-np.maximum(0, np.log2(max(low, 1e-9) / np.maximum(f, 1e-9))) ** 2 * order)
    return np.fft.irfft(spec * mask, len(x))


def lowpass(x, cutoff, order=2.0):
    return bandpass(x, 1.0, cutoff, order)


def highpass(x, cutoff, order=2.0):
    return bandpass(x, cutoff, SR / 2 - 1, order)


def osc(freq_curve, phase=0.0):
    """Oscillateur sinus à fréquence variable (freq_curve en Hz, par échantillon)."""
    return np.sin(2 * np.pi * np.cumsum(freq_curve) / SR + phase)


def drive(x, amount):
    return np.tanh(x * amount) / np.tanh(amount)


def reverb(x, decay=1.2, wet=0.35, rng=None):
    """Réverbération courte : convolution avec une queue de bruit exponentielle."""
    rng = rng or np.random.default_rng(7)
    n = int(decay * SR)
    ir = rng.standard_normal(n) * np.exp(-np.linspace(0, 8, n))
    ir = lowpass(ir, 6000)
    ir /= np.max(np.abs(ir)) + 1e-9
    tail = np.convolve(x, ir)[: len(x) + n // 2] / 24.0
    out = np.zeros(len(tail))
    out[: len(x)] += x
    return out * (1 - wet) + tail * wet


def normalize(x, peak_db=-3.0):
    peak = np.max(np.abs(x)) + 1e-9
    return x / peak * (10 ** (peak_db / 20))


def tremble(n, rate, depth, rng):
    """Modulation lente irrégulière (mélange de deux LFO désaccordés)."""
    t = np.arange(n) / SR
    p1, p2 = rng.uniform(0, 6.28, 2)
    return 1.0 + depth * (0.6 * np.sin(2 * np.pi * rate * t + p1)
                          + 0.4 * np.sin(2 * np.pi * rate * 0.37 * t + p2))


def write_wav(path, x):
    data = np.clip(x, -1.0, 1.0)
    pcm = (data * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


# ============================================================== les recettes
def shriek(seed):
    """Cri du screamer : formants qui montent, chute inharmonique, impact grave."""
    rng = np.random.default_rng(seed)
    dur = 0.9 + rng.uniform(0, 0.25)
    n = int(dur * SR)
    t = t_axis(dur)

    # Couche 1 : « voix » — FM inharmonique, pitch qui monte puis s'effondre.
    base = 420 + rng.uniform(-60, 60)
    peak_at = 0.22 + rng.uniform(0, 0.08)
    pitch = np.where(t < peak_at,
                     base * (1 + 2.6 * (t / peak_at)),
                     base * 3.6 * np.exp(-(t - peak_at) * (2.2 + rng.uniform(0, 1.2))))
    vib = 1 + 0.045 * np.sin(2 * np.pi * (11 + rng.uniform(0, 5)) * t)
    voice = osc(pitch * vib)
    voice += 0.55 * osc(pitch * 1.94 * vib)          # partiel presque octave (faux)
    voice += 0.32 * osc(pitch * 2.71)                # partiel inharmonique
    voice = drive(voice, 2.6) * env_adsr(n, 0.012, 0.25, 0.7, 0.42)

    # Couche 2 : déchirure — bruit formant balayé vers l'aigu.
    ripn = noise(n, rng)
    rip = bandpass(ripn, 900, 2400) * 0.8 + bandpass(ripn, 2400, 5200) * 0.7
    rip *= env_adsr(n, 0.01, 0.18, 0.55, 0.4) * tremble(n, 9, 0.35, rng)

    # Couche 3 : impact sourd à l'attaque.
    thud = osc(np.linspace(90, 34, n)) * env_adsr(n, 0.002, 0.16, 0.0, 0.05) * 1.2

    x = voice * 0.9 + rip * 0.75 + thud
    x = reverb(drive(x, 1.6), decay=0.9, wet=0.28, rng=rng)
    return normalize(fade(x, 0.003, 0.12), -1.5)


def whisper(seed, dur=1.6, syllables=5):
    """Chuchotement sans mots : bruit formanté, articulation syllabique."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    base = pink(n, rng)
    bands = (bandpass(base, 300, 900) * 0.9
             + bandpass(base, 1000, 2100) * 1.0
             + bandpass(base, 2500, 4200) * 0.55
             + bandpass(noise(n, rng), 4500, 7800) * 0.28)   # sifflantes
    # Articulation : syllabes d'amplitude irrégulière séparées de micro-pauses.
    envlp = np.zeros(n)
    cursor = int(0.04 * SR)
    for _ in range(syllables):
        length = int(rng.uniform(0.12, 0.3) * SR)
        gap = int(rng.uniform(0.03, 0.12) * SR)
        if cursor + length >= n:
            break
        seg = np.hanning(length) ** 0.7 * rng.uniform(0.55, 1.0)
        envlp[cursor:cursor + length] = np.maximum(envlp[cursor:cursor + length], seg)
        cursor += length + gap
    x = bands * envlp * tremble(n, 3.1, 0.2, rng)
    x = reverb(x, decay=0.7, wet=0.22, rng=rng)
    return normalize(fade(x, 0.02, 0.2), -12.0)


def breath(seed, dur=2.2):
    """Souffle dans la nuque : inspiration voilée, expiration plus longue, très proche."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    base = pink(n, rng)
    air = bandpass(base, 180, 750) * 1.0 + bandpass(base, 800, 1900) * 0.4
    split = 0.42 + rng.uniform(-0.05, 0.05)
    inh = int(split * n)
    envlp = np.concatenate([
        np.hanning(2 * inh)[:inh] ** 1.3 * 0.8,
        np.hanning(2 * (n - inh))[n - inh:] ** 0.8,
    ])
    x = drive(air * envlp, 1.8)
    x = lowpass(x, 2600)                      # bouche trop près, sans aigus
    x *= tremble(n, 5.7, 0.14, rng)
    return normalize(fade(x, 0.03, 0.12), -10.0)


def stinger(seed, dark=False):
    """Stinger orchestral corrompu : grappe dissonante, boom, résonance métallique."""
    rng = np.random.default_rng(seed)
    dur = 1.5 if not dark else 2.0
    n = int(dur * SR)
    root = (196 if not dark else 98) * (1 + rng.uniform(-0.04, 0.04))
    cluster = np.zeros(n)
    for ratio in (1.0, 1.06, 1.5, 1.59, 2.02):        # secondes mineures empilées
        detune = 1 + rng.uniform(-0.006, 0.006)
        cluster += osc(np.full(n, root * ratio * detune) * (1 - 0.12 * t_axis(dur)))
    cluster = drive(cluster / 5, 2.2) * env_adsr(n, 0.004, 0.5, 0.35, 0.7)
    boom = osc(np.linspace(70, 30, n)) * env_adsr(n, 0.002, 0.3, 0.0, 0.12) * 1.3
    metal_src = noise(n, rng)
    metal = sum(bandpass(metal_src, f * 0.98, f * 1.02, order=8.0)
                for f in (1170, 1560, 2340)) * env_adsr(n, 0.001, 0.7, 0.0, 0.2) * 2.2
    x = cluster + boom + metal
    x = reverb(x, decay=1.4, wet=0.33, rng=rng)
    return normalize(fade(x, 0.002, 0.35), -2.0)


def heartbeat(seed, bpm=64, beats=8):
    """Battement lub-dub bouclable, grave, légèrement irrégulier."""
    rng = np.random.default_rng(seed)
    beat_len = 60.0 / bpm
    n = int(beat_len * beats * SR)
    x = np.zeros(n)
    for b in range(beats):
        start = b * beat_len + rng.uniform(-0.012, 0.012) * (b > 0)
        for offset, strength, freq in ((0.0, 1.0, 58), (0.16, 0.65, 47)):
            at = int((start + offset) * SR)
            ln = int(0.16 * SR)
            if at + ln >= n:
                continue
            thump = osc(np.linspace(freq, freq * 0.6, ln)) * np.hanning(ln) ** 0.6 * strength
            click = highpass(noise(ln, rng), 900) * np.exp(-np.linspace(0, 40, ln)) * 0.12
            x[at:at + ln] += thump + click
    x = lowpass(drive(x, 1.5), 900)
    return normalize(x, -6.0)                         # pas de fade : boucle propre


def ambient_bed(seed, dur, brightness):
    """Nappe d'ambiance bouclable : drones désaccordés + partiels épars inquiets.
    brightness 0..1 : de la pesanteur sourde (low) à la tension aiguë (high)."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    t = np.arange(n) / SR
    root = 55 * (1 + brightness * 0.5)
    x = np.zeros(n)
    for ratio, amp in ((1.0, 1.0), (1.5, 0.5), (2.01, 0.4), (2.98, 0.22)):
        for det in (-1, 1):
            f = root * ratio * (1 + det * 0.004)
            lfo = 1 + 0.28 * np.sin(2 * np.pi * rng.uniform(0.02, 0.06) * t + rng.uniform(0, 6.28))
            x += osc(np.full(n, f)) * amp * lfo / 8
    x = lowpass(x, 300 + 2200 * brightness)
    # Partiels épars : fines apparitions aiguës, jamais au même endroit.
    for _ in range(int(3 + brightness * 5)):
        at = int(rng.uniform(0.1, 0.85) * n)
        ln = int(rng.uniform(1.5, 3.5) * SR)
        ln = min(ln, n - at - 1)
        f = rng.uniform(700, 1600) * (1 + brightness)
        part = osc(np.full(ln, f) * (1 + 0.002 * np.sin(2 * np.pi * 5.5 * np.arange(ln) / SR)))
        x[at:at + ln] += part * np.hanning(ln) * 0.045
    breathn = bandpass(pink(n, rng), 150, 700 + 1500 * brightness)
    x += breathn * 0.16 * tremble(n, 0.9, 0.4, rng)
    # Bouclage : fondu croisé de la fin sur le début.
    cross = int(1.5 * SR)
    w = np.linspace(0, 1, cross)
    x[:cross] = x[:cross] * w + x[-cross:] * (1 - w)
    x = x[: n - cross]
    return normalize(x, -14.0)


def silence_drone(seed, dur=12.0):
    """Le « silence » : sous-basse à la limite de l'audible + air raréfié."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    t = np.arange(n) / SR
    sub = osc(np.full(n, 31.0)) * (1 + 0.2 * np.sin(2 * np.pi * 0.07 * t))
    air = highpass(pink(n, rng), 6200) * 0.05 * tremble(n, 0.4, 0.5, rng)
    swell = env_adsr(n, dur * 0.35, dur * 0.2, 0.8, dur * 0.4)
    x = (sub * 0.8 + air) * swell
    return normalize(fade(x, 0.4, 1.2), -13.0)


def distant_hum(seed, dur=9.0):
    """Bourdonnement lointain, presque électrique, qui n'existe peut-être pas."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = 96 * (1 + 0.01 * np.sin(2 * np.pi * 0.13 * t))
    x = osc(f) * 0.6 + osc(f * 2.02) * 0.25 + osc(f * 2.99) * 0.12
    x *= 0.5 + 0.5 * np.sin(2 * np.pi * 0.06 * t + 1.2) ** 2
    x = lowpass(drive(x, 1.4), 900)
    x += bandpass(pink(n, rng), 300, 900) * 0.05
    return normalize(fade(x, 1.0, 1.5), -16.0)


def chase_stinger(seed):
    """Départ de poursuite : montée précipitée + cluster + pulsation immédiate."""
    rng = np.random.default_rng(seed)
    dur = 2.2
    n = int(dur * SR)
    t = t_axis(dur)
    riser_len = int(0.5 * SR)
    riser = bandpass(noise(n, rng), 400, 4000) * np.concatenate([
        np.linspace(0, 1, riser_len) ** 2, np.zeros(n - riser_len)])
    hit = stinger(seed + 1)[: n - riser_len] * 1.0
    x = riser * 0.7
    x[riser_len:] += hit
    pulse_f = 4.6
    pulse = (osc(np.full(n, 65)) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * pulse_f * t))))
    pulse *= np.concatenate([np.zeros(riser_len), np.linspace(0.6, 0.25, n - riser_len)])
    x += lowpass(pulse, 240)
    return normalize(fade(x, 0.01, 0.3), -2.5)


def door_groan(seed, distant=False):
    """Grincement de porte : glissé-collé résonant, corps de bois, chute de pitch."""
    rng = np.random.default_rng(seed)
    dur = 1.9
    n = int(dur * SR)
    t = t_axis(dur)
    f = (150 - 55 * t / dur) * (1 + 0.06 * np.sin(2 * np.pi * 2.3 * t + rng.uniform(0, 6)))
    stick = osc(f) + 0.5 * osc(f * 2.01) + 0.3 * osc(f * 2.98)
    gate_f = 26 - 14 * t / dur                      # le stick-slip ralentit
    gate = (np.sin(2 * np.pi * np.cumsum(gate_f) / SR) > rng.uniform(-0.4, -0.1)).astype(float)
    body = bandpass(stick * gate, 120, 900)
    wood = bandpass(noise(n, rng), 200, 620) * 0.18 * tremble(n, 4.0, 0.4, rng)
    x = (body + wood) * env_adsr(n, 0.06, 0.5, 0.65, 0.5)
    if distant:
        x = lowpass(x, 900)
        x = reverb(x, decay=1.8, wet=0.55, rng=rng)
        return normalize(fade(x, 0.05, 0.5), -14.0)
    x = reverb(x, decay=0.9, wet=0.25, rng=rng)
    return normalize(fade(x, 0.02, 0.3), -7.0)


def torch_sputter(seed):
    """Torche qui crachote : sifflement qui meurt, dernières crépitations."""
    rng = np.random.default_rng(seed)
    dur = 1.5
    n = int(dur * SR)
    hiss = bandpass(noise(n, rng), 2800, 8200)
    dying = np.exp(-np.linspace(0, 3.4, n)) * tremble(n, 7, 0.5, rng)
    x = hiss * dying * 0.7
    for _ in range(14):                              # pops épars, plus rares vers la fin
        at = int(rng.uniform(0, 0.9) ** 1.6 * n)
        ln = int(rng.uniform(0.004, 0.014) * SR)
        if at + ln >= n:
            continue
        pop = bandpass(noise(ln, rng), 700, 3600) * np.exp(-np.linspace(0, 26, ln))
        x[at:at + ln] += pop * rng.uniform(0.5, 1.1)
    return normalize(fade(x, 0.01, 0.25), -9.0)


def fake_creeper(seed):
    """L'amorce : chuintement qui monte et enfle — coupé net avant la fin."""
    rng = np.random.default_rng(seed)
    dur = 1.5
    n = int(dur * SR)
    t = t_axis(dur)
    src = noise(n, rng)
    x = np.zeros(n)
    for lo, hi, amp in ((900, 2100, 0.6), (2100, 4600, 0.9), (4600, 7600, 0.5)):
        sweep = bandpass(src, lo, hi) * amp
        x += sweep * (0.25 + 0.75 * (t / dur) ** 1.6)
    x *= 1 + 0.12 * np.sin(2 * np.pi * 15 * t)
    cut = int(0.94 * n)
    x[cut:] = 0.0                                   # le silence est la chute
    return normalize(fade(x, 0.02, 0.004), -6.0)


def footstep(seed, drag=False, heavy=False):
    """Pas : impact sourd + gravier — et l'écho du pas qui n'est pas le vôtre."""
    rng = np.random.default_rng(seed)
    dur = 0.55 if not drag else 0.75
    n = int(dur * SR)
    x = np.zeros(n)
    thud_f = 95 if not heavy else 62
    ln = int((0.12 if not heavy else 0.18) * SR)
    x[:ln] += osc(np.linspace(thud_f, thud_f * 0.55, ln)) * np.hanning(ln) ** 0.5
    grit_len = int((0.3 if drag else 0.07) * SR)
    grit = bandpass(noise(grit_len, rng), 700, 2600) * np.exp(
        -np.linspace(0, 3 if drag else 14, grit_len)) * 0.5
    x[:grit_len] += grit
    if heavy:
        squelch_len = int(0.2 * SR)
        squelch = bandpass(noise(squelch_len, rng), 280, 950)
        squelch *= np.hanning(squelch_len) * tremble(squelch_len, 30, 0.6, rng) * 0.35
        x[int(0.02 * SR):int(0.02 * SR) + squelch_len] += squelch
    else:
        # La signature du mimétisme : un second pas fantôme, plus faible, juste après.
        at = int(rng.uniform(0.09, 0.13) * SR)
        ln2 = int(0.09 * SR)
        ghost = osc(np.linspace(thud_f * 1.06, thud_f * 0.6, ln2)) * np.hanning(ln2) ** 0.5
        x[at:at + ln2] += ghost * 0.4
    x = lowpass(x, 3200)
    return normalize(fade(x, 0.004, 0.08), -8.0)


def originel_hum(seed, dur=8.0):
    """Chœur sombre de l'Originel : voyelles fermées désaccordées, bouclable."""
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for base, amp in ((82.4, 1.0), (87.3, 0.8), (123.5, 0.55)):   # cluster mi-la♭-si
        excite = np.zeros(n)
        f = base * (1 + 0.004 * np.sin(2 * np.pi * rng.uniform(0.08, 0.2) * t
                                       + rng.uniform(0, 6.28)))
        for h in range(1, 14):
            excite += osc(f * h) / h
        vowel = (bandpass(excite, 300, 450) * 1.0
                 + bandpass(excite, 620, 820) * 0.6
                 + bandpass(excite, 2200, 2600) * 0.12)
        x += vowel * amp * (0.7 + 0.3 * np.sin(2 * np.pi * rng.uniform(0.05, 0.12) * t
                                               + rng.uniform(0, 6.28)))
    cross = int(1.2 * SR)
    w = np.linspace(0, 1, cross)
    x[:cross] = x[:cross] * w + x[-cross:] * (1 - w)
    x = x[: n - cross]
    return normalize(x, -13.0)


def ritual_release(seed):
    """Libération du rituel : aspiration inversée, éclat, résolution grave."""
    rng = np.random.default_rng(seed)
    dur = 3.6
    n = int(dur * SR)
    peak = int(0.42 * n)
    swell = bandpass(noise(n, rng), 300, 3200)
    swell[:peak] *= np.linspace(0, 1, peak) ** 2.2
    swell[peak:] *= np.exp(-np.linspace(0, 7, n - peak))
    chord = np.zeros(n)
    for ratio in (1.0, 1.5, 2.0, 3.0):
        gliss = np.concatenate([
            np.full(peak, 220.0 * ratio),
            220.0 * ratio * np.exp(-np.linspace(0, 0.35, n - peak))])
        chord += osc(gliss) / 4
    chord[:peak] *= np.linspace(0, 0.6, peak) ** 2
    shimmer_len = n - peak
    shimmer = sum(osc(np.full(shimmer_len, f * rng.uniform(0.995, 1.005)))
                  for f in (1760, 2637, 3520))
    shimmer = shimmer / 3 * np.exp(-np.linspace(0, 4.5, shimmer_len)) * 0.35
    x = swell * 0.6 + chord
    x[peak:] += shimmer
    low_len = n - peak
    x[peak:] += osc(np.linspace(110, 55, low_len)) * np.exp(-np.linspace(0, 3, low_len)) * 0.7
    x = reverb(x, decay=2.2, wet=0.42, rng=rng)
    return normalize(fade(x, 0.05, 0.9), -4.0)


def voice_hey(seed):
    """« hé » chuchoté : /h/ soufflé puis voyelle /e/ formantée, sans hauteur."""
    rng = np.random.default_rng(seed)
    dur = 0.85
    n = int(dur * SR)
    carrier = pink(n, rng)
    h_len = int(0.16 * SR)
    h = bandpass(carrier[:h_len], 900, 4200) * np.hanning(h_len) ** 0.6 * 0.8
    e_len = int(0.5 * SR)
    e_src = carrier[h_len:h_len + e_len]
    e = (bandpass(e_src, 420, 620, order=6.0) * 1.0
         + bandpass(e_src, 1700, 2100, order=6.0) * 0.8
         + bandpass(e_src, 2500, 2900, order=6.0) * 0.35)
    e *= np.hanning(e_len) ** 0.45
    x = np.zeros(n)
    x[:h_len] += h
    x[h_len:h_len + e_len] += e * 1.4
    x = drive(x, 1.6)
    x = reverb(x, decay=0.8, wet=0.3, rng=rng)
    return normalize(fade(x, 0.01, 0.2), -9.0)


# ================================================================== catalogue
def build_catalog():
    return {
        # Le cri : trois gorges différentes.
        "screamer_shriek.ogg": shriek(101),
        "screamer_shriek_2.ogg": shriek(202),
        "screamer_shriek_3.ogg": shriek(303),
        # Chuchotements : trois débits différents.
        "host_whisper.ogg": whisper(111, dur=1.5, syllables=4),
        "host_whisper_3.ogg": whisper(222, dur=1.9, syllables=6),
        "host_whisper_4.ogg": whisper(333, dur=1.3, syllables=3),
        "host_whisper_2.ogg": whisper(444, dur=2.2, syllables=7),   # événement dédié
        # Souffles.
        "breath_behind.ogg": breath(121, dur=2.1),
        "breath_behind_3.ogg": breath(232, dur=2.6),
        "breath_behind_2.ogg": breath(343, dur=3.0),                # événement dédié
        # Stingers.
        "host_stinger.ogg": stinger(131),
        "host_stinger_2.ogg": stinger(242, dark=True),
        "chase_stinger.ogg": chase_stinger(151),
        # Cœur et nappes.
        "heartbeat.ogg": heartbeat(161),
        "ambient_bed_low.ogg": ambient_bed(171, 24.0, 0.12),
        "ambient_bed_mid.ogg": ambient_bed(181, 24.0, 0.45),
        "ambient_bed_high.ogg": ambient_bed(191, 22.0, 0.85),
        "silence_drone.ogg": silence_drone(201),
        "distant_hum.ogg": distant_hum(211),
        # Deuxième passe : portes, feu, pas, chœur, rituel, voix.
        "door_groan.ogg": door_groan(401),
        "lure_door.ogg": door_groan(402, distant=True),
        "torch_sputter.ogg": torch_sputter(411),
        "fake_creeper.ogg": fake_creeper(421),
        "footstep_mimic.ogg": footstep(431),
        "footstep_mimic_2.ogg": footstep(432, drag=True),
        "host_step.ogg": footstep(441, heavy=True),
        "originel_hum.ogg": originel_hum(451),
        "ritual_release.ogg": ritual_release(461),
        "voice_hey.ogg": voice_hey(471),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="src/main/resources/assets/penumbra/sounds")
    parser.add_argument("--backup", default="build/backup_sounds")
    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)
    os.makedirs(args.backup, exist_ok=True)
    catalog = build_catalog()
    with tempfile.TemporaryDirectory() as tmp:
        for name, data in catalog.items():
            destination = os.path.join(args.out, name)
            if os.path.isfile(destination):
                shutil.copy2(destination, os.path.join(args.backup, name))
            wav_path = os.path.join(tmp, name.replace(".ogg", ".wav"))
            write_wav(wav_path, data)
            subprocess.run(
                ["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path,
                 "-c:a", "libvorbis", "-qscale:a", "4", "-ar", "44100", "-ac", "1",
                 destination],
                check=True)
            seconds = len(data) / SR
            print(f"{name:>24}  {seconds:5.1f}s  "
                  f"pic {20 * np.log10(np.max(np.abs(data)) + 1e-9):5.1f} dB")
    print(f"\n{len(catalog)} fichiers écrits dans {args.out}")
    print(f"anciens fichiers sauvegardés dans {args.backup}")


if __name__ == "__main__":
    main()
