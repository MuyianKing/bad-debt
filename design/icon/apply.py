"""把选中的方案写入 App 实际读取的图标路径。

用法：python apply.py red-a-coin
可选：red-a-coin / red-b-nezha / yellow-a-lotus / yellow-b-flame
写入 unpackage/res/icons/{72x72,96x96,144x144,192x192}.png 与 icon_1024.png，
manifest.json 里已有的图标路径不需要改动。
unpackage/ 在 .gitignore 里，改动前的旧图标已备份在 backup-original/。
"""
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'out')
DEST = os.path.abspath(os.path.join(HERE, '..', '..', 'unpackage', 'res', 'icons'))
MAP = {'72': '72x72.png', '96': '96x96.png', '144': '144x144.png', '192': '192x192.png'}

CHOICES = ['red-a-coin', 'red-b-nezha', 'yellow-a-lotus', 'yellow-b-flame']

name = sys.argv[1] if len(sys.argv) > 1 else ''
if name not in CHOICES:
    print('用法: python apply.py <方案名>')
    print('可选:', ', '.join(CHOICES))
    sys.exit(1)

for size, filename in MAP.items():
    shutil.copyfile(os.path.join(OUT, f'{name}-{size}.png'), os.path.join(DEST, filename))
shutil.copyfile(os.path.join(OUT, f'{name}-1024.png'), os.path.join(DEST, 'icon_1024.png'))
print(f'已应用 {name} → {DEST}')
print('提示：HBuilderX 需重新打包/自定义基座，图标才会更新到桌面。')
