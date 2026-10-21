#!/usr/bin/env python3
"""Génère le son ajouté en 0.18.0 : la page tournée du Relevé.

Réutilise les utilitaires de `gen_sounds_017.py` (mêmes conventions : mono
44,1 kHz, Vorbis ~86 kb/s, écriture directe dans
src/main/resources/assets/penumbra/sounds/).

Usage : python3 tools/gen_sounds_018.py
"""

import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gen_sounds_017 import SR, bandpass, env_ad, noise, write  # noqa: E402


def page_turn():
    """Page tournée : froissement bref de papier sec, sans queue.

    Trois bouffées de bruit filtré très courtes se succèdent en 0,36 s — c'est
    le rythme réel d'une feuille qu'on soulève, qu'on plie et qu'on repose.
    Aucune résonance, aucune réverbération : un carnet, ça ne sonne pas. Le
    troisième froissement est plus aigu que les deux premiers, parce que la
    page retombe sur la tranche.
    """
    n = int(0.36 * SR)
    out = np.zeros(n)
    for start_ratio, length_ratio, gain, lo, hi in (
            (0.00, 0.30, 1.00, 1800, 7000),
            (0.26, 0.34, 0.72, 1200, 5200),
            (0.62, 0.38, 0.55, 2400, 8800)):
        start = int(n * start_ratio)
        length = min(n - start, int(n * length_ratio))
        if length <= 8:
            continue
        burst = bandpass(noise(length), lo, hi)
        burst = burst * env_ad(length, 0.004, length / SR, curve=2.6) * gain
        out[start:start + length] += burst
    return out


def main():
    print("Génération des sons 0.18.0 :")
    # Discret : le carnet se lit, il ne s'annonce pas.
    write("page_turn", page_turn(), -10.0)
    print("Terminé.")


if __name__ == "__main__":
    main()
