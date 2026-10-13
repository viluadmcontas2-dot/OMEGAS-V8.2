"""Compara duas pastas de PNG (mesmo nome) e conta pixels diferentes, ignorando a barra do topo (y < 44) que mostra números vivos.
Uso: python3 tools/ci/comparar_pngs.py <pastaA> <pastaB>   (precisa de Pillow)"""
import os, sys
from PIL import Image, ImageChops
a, b = sys.argv[1:3]
bad = 0
for name in sorted(os.listdir(a)):
    if not name.endswith('.png'): continue
    ia = Image.open(os.path.join(a, name)).convert('RGB'); ib = Image.open(os.path.join(b, name)).convert('RGB')
    box = (0, 44, ia.width, ia.height)
    diff = ImageChops.difference(ia.crop(box), ib.crop(box)).convert('L').point(lambda v: 255 if v > 24 else 0)
    n = sum(1 for v in diff.get_flattened_data() if hasattr(diff, "get_flattened_data") else diff.getdata() if v)
    print(f"{name}: {n} pixels diferentes")
    if n > 40: bad += 1; diff.save(os.path.join(b, 'DIFF-' + name))
sys.exit(1 if bad else 0)
