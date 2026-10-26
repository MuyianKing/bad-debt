"""把 1024 主图缩成 Android 各密度尺寸，并拼一张对比预览图。"""
from PIL import Image, ImageDraw, ImageFont
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'out')
SIZES = [192, 144, 96, 72, 48, 32]

DESIGNS = [
    ('red-a-coin', '红 · 乾坤钱', '铜钱 + 混天绫', '#ef4436'),
    ('red-b-nezha', '红 · 钱髻哪吒', '铜钱当发髻的哪吒头像', '#b61621'),
    ('yellow-a-lotus', '黄 · 莲钱', '莲花化身 + 方孔金铜钱', '#ffc733'),
    ('yellow-b-flame', '黄 · 火尖焰', '哪吒之火 + ¥', '#ffc02c'),
]


def export():
    for name, *_ in DESIGNS:
        src = Image.open(os.path.join(OUT, f'{name}-1024.png'))
        for s in SIZES:
            src.resize((s, s), Image.LANCZOS).save(os.path.join(OUT, f'{name}-{s}.png'))
        print('exported', name)


def font(size, bold=False):
    path = 'C:/Windows/Fonts/msyhbd.ttc' if bold else 'C:/Windows/Fonts/msyh.ttc'
    return ImageFont.truetype(path, size)


def sheet():
    W, H = 1520, 1200
    bg = (23, 22, 24)
    img = Image.new('RGB', (W, H), bg)
    d = ImageDraw.Draw(img)

    # 顶部标题
    d.text((72, 54), '孽账 · App 图标方案', font=font(46, True), fill=(255, 246, 228))
    d.text((74, 118), '孽障 → 哪吒。红 / 黄各两版，含 72px 实际尺寸预览', font=font(24), fill=(168, 162, 156))

    col_w = (W - 144) // 4
    top = 210
    for i, (name, title, desc, accent) in enumerate(DESIGNS):
        x = 72 + i * col_w
        icon = Image.open(os.path.join(OUT, f'{name}-1024.png')).resize((252, 252), Image.LANCZOS)
        # 卡片底
        d.rounded_rectangle([x, top, x + col_w - 26, top + 536], radius=26, fill=(33, 32, 35))
        img.paste(icon, (x + (col_w - 26 - 252) // 2, top + 34), icon)
        d.text((x + 22, top + 308), title, font=font(31, True), fill=(255, 246, 228))
        d.text((x + 22, top + 354), desc, font=font(21), fill=(158, 152, 146))
        # 实际尺寸
        smalls = [72, 48, 32]
        sx = x + 22
        for s in smalls:
            im = Image.open(os.path.join(OUT, f'{name}-{s}.png'))
            img.paste(im, (sx, top + 400), im)
            d.text((sx, top + 486), f'{s}px', font=font(16), fill=(120, 116, 112))
            sx += s + 34

    # 下方：模拟桌面一排（浅色壁纸条）
    strip_top = 796
    d.rounded_rectangle([72, strip_top, W - 72, strip_top + 300], radius=28, fill=(238, 236, 231))
    d.text((100, strip_top + 26), '桌面观感（160px 圆角磁贴）', font=font(22), fill=(120, 116, 112))
    tile = 160
    for i, (name, title, *_ ) in enumerate(DESIGNS):
        x = 100 + i * 200
        y = strip_top + 78
        t = Image.open(os.path.join(OUT, f'{name}-1024.png')).resize((tile, tile), Image.LANCZOS)
        mask = Image.new('L', (tile, tile), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, tile, tile], radius=38, fill=255)
        img.paste(t, (x, y), mask)
        d.text((x, y + tile + 16), title, font=font(17), fill=(110, 106, 102))

    # 底部说明
    d.text((72, 1136), '尺寸 1024 主图 → 192 / 144 / 96 / 72；圆角外透明，兼容各家桌面。',
           font=font(21), fill=(140, 134, 128))

    path = os.path.join(OUT, 'preview.png')
    img.save(path)
    print('sheet', path)


if __name__ == '__main__':
    export()
    sheet()
