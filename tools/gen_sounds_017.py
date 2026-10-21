#!/usr/bin/env python3
"""Génère les six sons procéduraux ajoutés en 0.17.0.

L'Écouteur : listener_click, listener_inhale, listener_lock, listener_leave.
Événements du Directeur : held_breath, extra_step.

Mêmes conventions que tools/gen_sounds.py : mono 44,1 kHz, Vorbis ~86 kb/s,
pics normalisés à -3 dBFS au plus (et bien plus bas pour les nappes discrètes).
Écrit directement dans src/main/resources/assets/penumbra/sounds/.

Repli : si `soundfile` est absent, un .wav 16 bits temporaire est produit puis
converti par ffmpeg (`ffmpeg -i in.wav -c:a libvorbis -b:a 86k out.ogg`).
"""

import math
import os
import shutil
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "src", "main", "resources")
OUT = os.path.join(BASE, "assets", "penumbra", "sounds")

rng = np.random.default_rng(20260717)


# ————————————————————————— utilitaires —————————————————————————

def normalize(x, peak_db=-3.0):
    target = 10.0 ** (peak_db / 20.0)
    m = float(np.max(np.abs(x)))
    if m > 0:
        x = x * (target / m)
    return x


def bandpass(x, lo, hi):
    """Passe-bande brutal par FFT, comme gen_sounds.py."""
    spec = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(len(x), 1.0 / SR)
    spec[(freqs < lo) | (freqs > hi)] = 0.0
    return np.fft.irfft(spec, len(x))


def noise(n):
    return rng.standard_normal(n)


def sweep(f0, f1, n, curve=1.0):
    """Balayage de fréquence par phase intégrée (pas de discontinuité)."""
    t = np.arange(n) / SR
    shape = (t / max(t[-1], 1e-9)) ** curve
    freq = f0 + (f1 - f0) * shape
    phase = 2.0 * np.pi * np.cumsum(freq) / SR
    return np.sin(phase)


def env_ad(n, attack, release, curve=2.0):
    """Enveloppe attaque/decay simple, sans plateau."""
    a = max(1, int(attack * SR))
    a = min(a, n - 1)
    env = np.empty(n)
    env[:a] = np.linspace(0.0, 1.0, a) ** 0.6
    tail = n - a
    env[a:] = np.linspace(1.0, 0.0, tail) ** curve
    return env


def env_swell(n, peak_at=0.7, tail=0.25):
    """Montée lente jusqu'à peak_at, puis coupure franche : l'inspiration."""
    peak = max(1, int(n * peak_at))
    env = np.empty(n)
    env[:peak] = np.linspace(0.0, 1.0, peak) ** 1.7
    rest = n - peak
    cut = max(1, int(rest * tail))
    env[peak:peak + cut] = np.linspace(1.0, 0.0, cut) ** 0.8
    env[peak + cut:] = 0.0
    return env


def write(name, data, peak_db=-3.0):
    data = normalize(np.asarray(data, dtype=np.float64), peak_db)
    data = np.clip(data, -1.0, 1.0)
    os.makedirs(OUT, exist_ok=True)
    target = os.path.join(OUT, name + ".ogg")

    try:
        import soundfile as sf
        sf.write(target, data, SR, format="OGG", subtype="VORBIS")
        print(f"  {name}.ogg  {len(data) / SR:.2f}s  "
              f"pic {20 * math.log10(max(float(np.max(np.abs(data))), 1e-12)):.1f} dBFS")
        return
    except ImportError:
        pass

    if shutil.which("ffmpeg") is None:
        raise SystemExit("Ni soundfile ni ffmpeg : impossible d'écrire un .ogg")

    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, name + ".wav")
        with wave.open(wav_path, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(SR)
            wav.writeframes((data * 32767.0).astype("<i2").tobytes())
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path,
             "-c:a", "libvorbis", "-b:a", "86k", "-ac", "1", target],
            check=True)
    print(f"  {name}.ogg  {len(data) / SR:.2f}s  "
          f"pic {20 * math.log10(max(float(np.max(np.abs(data))), 1e-12)):.1f} dBFS")


# ————————————————————————— L'Écouteur —————————————————————————

def listener_click():
    """Clic d'écholocation : un os sec frappé, 0,18 s, aucune queue musicale."""
    n = int(0.18 * SR)
    transient = noise(n) * env_ad(n, 0.0008, 0.17, curve=5.0)
    transient = bandpass(transient, 900, 6500)
    # Résonance métallique courte : elle « sonne » comme une cavité vide.
    ring = sine_ring(2450.0, n, 0.045) * 0.5 + sine_ring(3170.0, n, 0.03) * 0.3
    return transient * 0.9 + ring


def sine_ring(freq, n, decay):
    t = np.arange(n) / SR
    return np.sin(2.0 * np.pi * freq * t) * np.exp(-t / max(decay, 1e-4))


def listener_inhale():
    """Inspiration humide : elle se met à l'écoute. 1,7 s, montée puis coupure."""
    n = int(1.7 * SR)
    env = env_swell(n, peak_at=0.72, tail=0.22)
    # Bruit filtré dont la bande monte : la gorge se resserre.
    body = np.zeros(n)
    chunk = 2048
    for start in range(0, n, chunk):
        end = min(n, start + chunk)
        progress = start / max(n - 1, 1)
        lo = 220 + 520 * progress
        hi = lo + 700 + 900 * progress
        segment = bandpass(noise(end - start), lo, hi)
        body[start:end] = segment
    rumble = bandpass(noise(n), 45, 120) * 0.55
    return (body * 0.85 + rumble) * env


def listener_lock():
    """Verrouillage : elle a entendu. Balayage montant dissonant, attaque nette."""
    n = int(1.05 * SR)
    env = env_ad(n, 0.006, 1.04, curve=1.5)
    fundamental = sweep(165.0, 880.0, n, curve=0.55)
    # Quinte diminuée : dissonance volontaire, jamais un accord.
    harsh = sweep(165.0 * 1.414, 880.0 * 1.414, n, curve=0.55) * 0.42
    breath = bandpass(noise(n), 1200, 5200) * 0.3
    return (fundamental + harsh + breath) * env


def listener_leave():
    """Renoncement : un raclement qui s'éloigne, 2,3 s, spectre qui descend."""
    n = int(2.3 * SR)
    env = env_ad(n, 0.09, 2.2, curve=1.2)
    drag = np.zeros(n)
    chunk = 4096
    for start in range(0, n, chunk):
        end = min(n, start + chunk)
        progress = start / max(n - 1, 1)
        hi = 2600 - 2000 * progress
        drag[start:end] = bandpass(noise(end - start), 90, max(180.0, hi))
    sub = sweep(78.0, 42.0, n, curve=1.4) * 0.4
    return (drag * 0.8 + sub) * env


# ———————————————————— Événements du Directeur ————————————————————

def held_breath():
    """Le monde inspire, puis se tait net. Sub-basse + souffle, 3,1 s."""
    n = int(3.1 * SR)
    env = env_swell(n, peak_at=0.82, tail=0.06)
    sub = sweep(38.0, 74.0, n, curve=1.8) * 0.9
    air = bandpass(noise(n), 160, 900) * 0.32
    # Un très léger battement d'intermodulation : le corps l'entend, pas l'oreille.
    beat = 1.0 + 0.06 * np.sin(2.0 * np.pi * 0.7 * np.arange(n) / SR)
    return (sub + air) * env * beat


def extra_step():
    """Le pas de trop : un talon sec sur la pierre, 0,22 s, sans réverbération."""
    n = int(0.22 * SR)
    thud = bandpass(noise(n), 70, 320) * env_ad(n, 0.001, 0.21, curve=3.2)
    grit = bandpass(noise(n), 1400, 4800) * env_ad(n, 0.0006, 0.05, curve=6.0) * 0.45
    return thud + grit


SOUNDS = [
    ("listener_click", listener_click, -6.0),
    ("listener_inhale", listener_inhale, -8.0),
    ("listener_lock", listener_lock, -3.0),
    ("listener_leave", listener_leave, -9.0),
    # Le souffle retenu est une nappe : il doit rester sous le seuil conscient.
    ("held_breath", held_breath, -14.0),
    ("extra_step", extra_step, -7.0),
]


def main():
    print("Génération des sons 0.17.0 :")
    for name, builder, peak in SOUNDS:
        write(name, builder(), peak)
    print("Terminé.")


if __name__ == "__main__":
    main()
