#!/usr/bin/env python3
"""Original, deterministic sound layers for Penumbra 0.21.

No samples or external recordings. Requires numpy and ffmpeg (libvorbis).
The three ambient events each contain three eight-second phrases. Their
1.5-second fades are part of the contract with AmbientFearManager.
Run after historical sound generators if rebuilding the complete bank.
"""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import wave

import numpy as np

from generate_sounds import SR, bandpass, fade, footstep, normalize, osc


def bed(seed, tension):
    rng = np.random.default_rng(seed)
    n = SR * 8
    t = np.arange(n) / SR
    # Slow, irregular beating; no sharp transient to mask creature warnings.
    fundamental = rng.uniform(48, 62)
    low = osc(fundamental + 0.7 * np.sin(t * 0.83))
    low += 0.55 * osc(fundamental + rng.uniform(0.4, 1.0) + 0.3 * np.sin(t * 1.1))
    air = bandpass(rng.standard_normal(n), 180, 760 + tension * 750)
    air /= max(np.max(np.abs(air)), 1e-9)
    slow = 0.65 + 0.2 * np.sin(t * rng.uniform(0.35, 0.6) + rng.uniform(0, 6))
    # Different distant resonances per phrase, subtly rougher under pressure.
    resonance = np.zeros(n)
    for freq in rng.uniform(130, 380 + 150 * tension, 3):
        resonance += osc(freq + (0.5 + tension) * np.sin(t * rng.uniform(0.2, 0.8))) / 3
    irregular = np.interp(t, np.linspace(0, 8, 17), rng.uniform(0.1, 1.0, 17))
    signal = low * slow + air * (0.22 + tension * 0.18) * irregular
    signal += resonance * (0.06 + tension * 0.12) * np.sin(np.pi * t / 8) ** 2
    signal -= np.mean(signal)
    return fade(signal, 1.5, 1.5)


def catalog():
    result = {}
    for state, tension, ceiling in [('low', 0.1, -20), ('mid', 0.5, -17), ('high', 0.9, -14)]:
        for variant in range(3):
            suffix = '' if variant == 0 else f'_{variant + 1}'
            name = f'ambient_bed_{state}{suffix}'
            result[name] = (bed(20261021 + int(tension * 100) + variant, tension), ceiling)
    for variant in range(2):
        result[f'host_step_{variant + 2}'] = (footstep(20262021 + variant, heavy=True), -8)
        result[f'footstep_mimic_{variant + 3}'] = (footstep(20263021 + variant), -8)
    return result


def inspect(ffmpeg, path, ceiling):
    raw = subprocess.run([ffmpeg, '-v', 'error', '-i', str(path), '-f', 'f32le',
                          '-ac', '1', '-ar', str(SR), '-'], check=True, capture_output=True).stdout
    data = np.frombuffer(raw, dtype='<f4')
    if not len(data) or not np.all(np.isfinite(data)):
        raise RuntimeError(f'{path.name}: invalid decoded audio')
    peak = float(20 * np.log10(max(np.max(np.abs(data)), 1e-12)))
    rms = float(20 * np.log10(max(np.sqrt(np.mean(data ** 2)), 1e-12)))
    if peak > ceiling + 0.1:
        raise RuntimeError(f'{path.name}: decoded peak {peak:.2f} exceeds {ceiling} dBFS')
    if path.name.startswith('ambient_bed'):
        if abs(len(data) / SR - 8) > 0.02:
            raise RuntimeError(f'{path.name}: bed must last 8 seconds')
        edge = np.concatenate([data[:SR // 10], data[-SR // 10:]])
        if np.sqrt(np.mean(edge ** 2)) > np.sqrt(np.mean(data ** 2)) * 0.15:
            raise RuntimeError(f'{path.name}: missing entry/exit fade')
    return {'file': path.name, 'seconds': round(len(data) / SR, 3),
            'decoded_peak_dbfs': round(peak, 2), 'rms_dbfs': round(rms, 2)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=Path('src/main/resources/assets/penumbra/sounds'))
    parser.add_argument('--ffmpeg', default=shutil.which('ffmpeg'))
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    if not args.ffmpeg:
        parser.error('ffmpeg not found; pass --ffmpeg /path/to/ffmpeg')
    args.out.mkdir(parents=True, exist_ok=True)
    report = []
    with tempfile.TemporaryDirectory() as temp:
        for name, (signal, ceiling) in catalog().items():
            # Leave one dB for Vorbis reconstruction overshoot; validate decoded output.
            signal = normalize(signal, ceiling - 1)
            wav = Path(temp) / f'{name}.wav'
            with wave.open(str(wav), 'wb') as out:
                out.setnchannels(1)
                out.setsampwidth(2)
                out.setframerate(SR)
                out.writeframes((signal * 32767).astype('<i2').tobytes())
            target = args.out / f'{name}.ogg'
            subprocess.run([args.ffmpeg, '-y', '-v', 'error', '-i', str(wav),
                            '-c:a', 'libvorbis', '-q:a', '4', str(target)], check=True)
            report.append(inspect(args.ffmpeg, target, ceiling))
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    for item in report:
        print(f"{item['file']}: {item['seconds']}s, peak {item['decoded_peak_dbfs']} dBFS")


if __name__ == '__main__':
    main()
