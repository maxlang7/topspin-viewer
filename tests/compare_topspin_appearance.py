"""Compare real application-window captures, with no rescaling or content masking.

Run in nix-shell -p python3Packages.pillow --run 'python tests/compare_topspin_appearance.py ...'.
The reference includes Wine's 16-point title bar at 2x; the native app is client-only.
An exact-match claim requires every unmasked pixel to agree, not merely similar colors.
"""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageStat, ImageDraw

p = argparse.ArgumentParser()
p.add_argument('reference', type=Path)
p.add_argument('viewer', type=Path)
p.add_argument('output', type=Path)
p.add_argument('--reference-title-pixels', type=int, default=32)
p.add_argument('--allow-differences', action='store_true')
a = p.parse_args()
reference = Image.open(a.reference).convert('RGB')
reference = reference.crop((0, a.reference_title_pixels, reference.width, reference.height))
viewer = Image.open(a.viewer).convert('RGB')
if viewer.size != reference.size:
    raise SystemExit(f'Capture sizes differ: reference client {reference.size}, viewer {viewer.size}; refusing to rescale')
a.output.mkdir(parents=True, exist_ok=True)
diff = ImageChops.difference(reference, viewer)
r, g, b = diff.split()
mask = ImageChops.lighter(ImageChops.lighter(r, g), b).point(lambda v: 255 if v else 0)
w, h = viewer.size
regions = {'whole_client': (0, 0, w, h), 'ribbon': (0, 0, w, 208),
           'toolbar': (0, 208, w, 286), 'sidebar': (0, 286, 680, h - 94),
           'empty_workspace': (680, 286, w, h - 94), 'footer': (0, h - 94, w, h)}
report = {'reference': str(a.reference), 'viewer': str(a.viewer),
          'reference_title_pixels_removed': a.reference_title_pixels,
          'rescaled': False, 'content_masked': False, 'size': viewer.size, 'regions': {}}
for name, box in regions.items():
    m = mask.crop(box)
    pixels = m.width * m.height
    same = m.histogram()[0]
    report['regions'][name] = {'identical_pixels': same, 'pixels': pixels,
                             'identical_percent': 100 * same / pixels,
                             'rgb_rms_difference': ImageStat.Stat(diff.crop(box)).rms}
report['pixel_exact'] = mask.getbbox() is None
(a.output / 'comparison.json').write_text(json.dumps(report, indent=2) + '\n')
reference.save(a.output / 'reference-client.png')
viewer.save(a.output / 'viewer-client.png')
heatmap = viewer.convert('L').convert('RGB')
heatmap = Image.blend(heatmap, Image.new('RGB', viewer.size, 'white'), 0.7)
heatmap.paste((220, 0, 160), mask=mask)
heatmap.save(a.output / 'differences.png')
pair = Image.new('RGB', (2 * w, h + 44), 'white')
draw = ImageDraw.Draw(pair)
draw.text((12, 10), 'Installed TopSpin 5.0.0, client area', fill='black')
draw.text((w + 12, 10), 'Rust viewer, TopSpin mode', fill='black')
pair.paste(reference, (0, 44)); pair.paste(viewer, (w, 44))
pair.save(a.output / 'side-by-side.png')
print(json.dumps(report, indent=2))
raise SystemExit(0 if report['pixel_exact'] or a.allow_differences else 1)
