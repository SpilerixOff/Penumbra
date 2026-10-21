#!/usr/bin/env python3
"""Compose « Mémoire » — la berceuse du disque maudit (0.16.0).

Boîte à musique en la mineur qui se désaccorde lentement : la première moitié
est presque belle ; le dernier quart tombe faux note après note, le tempo
s'étire, et quelque chose chuchote à l'envers sous la mélodie. La musique
s'arrête au milieu d'une phrase — le disque continue de tourner.

Usage : python3 tools/generate_disc.py
Écrit  src/main/resources/assets/penumbra/sounds/memoire_disc.ogg (~78 s, mono).
"""

import os
import subprocess
import sys
import tempfile

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from generate_sounds import (  # noqa: E402
    SR, fade, lowpass, normalize, pink, whisper, write_wav)

A3 = 220.0


def bell(freq, dur, strike_gain=1.0):
    """Note de boîte à musique : partiels inharmoniques, longue chute."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for ratio, amp, decay in ((1.0, 1.0, 2.1), (2.76, 0.38, 1.2), (5.40, 0.15, 0.6)):
        out += np.sin(2 * np.pi * freq * ratio * t) * amp * np.exp(-t / decay)
    click = np.zeros(n)
    click[: int(0.004 * SR)] = np.linspace(1, 0, int(0.004 * SR)) * 0.25
    return (out + click) * strike_gain


def semitone(offset):
    return A3 * (2.0 ** (offset / 12.0))


def main():
    rng = np.random.default_rng(31)
    total_seconds = 78.0
    n_total = int(total_seconds * SR)
    mix = np.zeros(n_total)

    # ————— Partition : berceuse en la mineur, 3/4, deux phrases + coda tronquée.
    # (demi-tons depuis A3, durée en temps ; None = soupir)
    phrase_a = [(12, 1), (7, 1), (3, 1), (7, 2), (3, 1),
                (5, 1), (7, 1), (8, 1), (7, 2), (None, 1),
                (3, 1), (5, 1), (7, 1), (5, 1), (3, 1), (2, 1),
                (0, 3)]
    phrase_b = [(8, 1), (7, 1), (5, 1), (8, 2), (7, 1),
                (5, 1), (3, 1), (2, 1), (3, 2), (None, 1),
                (12, 1), (8, 1), (7, 1), (5, 1), (3, 1), (5, 1),
                (0, 3)]
    coda = [(12, 1), (7, 1), (3, 1), (7, 2)]          # coupée en plein élan
    score = phrase_a + phrase_b + phrase_a + coda

    beat = 60.0 / 56.0                                 # 56 BPM
    cursor = 2.2                                       # amorce : sillon + souffle

    for index, (note, beats) in enumerate(score):
        if cursor >= total_seconds - 5.0:
            break                                      # le disque n'a pas la place : tant mieux
        progress = index / max(1, len(score) - 1)
        # Ritardando final : les derniers temps s'étirent.
        stretch = 1.0 + max(0.0, progress - 0.82) * 1.8
        dur = beats * beat * stretch
        if note is not None:
            # Désaccord croissant : juste au début, faux à la fin.
            drift = -0.30 * max(0.0, progress - 0.55) / 0.45
            jitter = rng.uniform(-0.08, 0.08) * max(0.0, progress - 0.7) / 0.3
            freq = semitone(note) * (2.0 ** (drift / 12.0 + jitter / 12.0))
            length = min(3.2, dur + 1.6)
            start = int(cursor * SR)
            voice = bell(freq, length, strike_gain=0.6 + 0.15 * rng.random())
            end = min(n_total, start + len(voice))
            if end > start:
                mix[start:end] += voice[: end - start]
            # Basse une octave sous la tonique, un temps sur trois.
            if index % 3 == 0:
                low = bell(semitone(note) / 2.0, min(3.5, dur + 2.0), 0.25)
                end_low = min(n_total, start + len(low))
                if end_low > start:
                    mix[start:end_low] += low[: end_low - start]
        cursor += dur

    # ————— Chuchotements à l'envers, sous la mélodie.
    for at_seconds, seed in ((21.0, 71), (44.0, 72), (66.5, 73)):
        voice = whisper(seed, dur=2.4, syllables=5)[::-1] * 0.5
        start = int(at_seconds * SR)
        end = min(n_total, start + len(voice))
        mix[start:end] += voice[: end - start]

    # ————— Support : souffle de bande + pleurage lent (wow).
    t = np.arange(n_total) / SR
    hiss = lowpass(pink(n_total, np.random.default_rng(99)), 5200) * 0.016
    wow = 1.0 + 0.05 * np.sin(2 * np.pi * 0.31 * t) + 0.02 * np.sin(2 * np.pi * 0.043 * t)
    mix = mix * wow + hiss

    # ————— Fin : la musique s'est arrêtée, le sillon tourne encore 4 s.
    silence_from = int((total_seconds - 4.0) * SR)
    mix[silence_from:] *= np.linspace(1.0, 0.0, n_total - silence_from) ** 0.35
    mix[silence_from:] += hiss[silence_from:] * 1.5
    tail_whisper = whisper(74, dur=1.6, syllables=3)[::-1] * 0.30
    start = int((total_seconds - 3.1) * SR)
    mix[start:start + len(tail_whisper)] += tail_whisper[: n_total - start]

    mix = normalize(fade(mix, 0.8, 1.2), -9.0)

    out = "src/main/resources/assets/penumbra/sounds/memoire_disc.ogg"
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "memoire.wav")
        write_wav(wav, mix)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav,
                        "-c:a", "libvorbis", "-qscale:a", "4", "-ar", "44100",
                        "-ac", "1", out], check=True)
    print(f"memoire_disc.ogg : {total_seconds:.0f} s, "
          f"pic {20 * np.log10(np.max(np.abs(mix)) + 1e-9):.1f} dB")


if __name__ == "__main__":
    main()
