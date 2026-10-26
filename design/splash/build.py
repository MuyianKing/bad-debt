# -*- coding: utf-8 -*-
"""生成 Android 启动图（.9.png 九宫格）。

品牌块（图标 + 「孽账」+ 副标题）固定在画面中心，9-patch 只拉伸四周纯色空白，
所以 16:9 / 19.5:9 / 20:9 的手机都不会把图标拉扁。背景色与 App 的 --c-bg
（#f7f4ec）一致，启动图切到首页不跳色。

用法：
    python build.py                    # 用 design/splash/logo.png
    python build.py path/to/icon.png   # 换图标时指定 1024 方图

产物：
    design/splash/out/{hdpi,xhdpi,xxhdpi}.9.png      # 版本库留档
    unpackage/res/splashscreen/{hdpi,xhdpi,xxhdpi}.9.png  # manifest 实际引用
    design/splash/out/preview.png                    # 不同长宽比拉伸效果预览

尺寸取 HBuilderX「Android启动图片设置」提示值：480x762 / 720x1242 / 1080x1882。
文件尺寸含最外 1px 九宫格标记边，实际画面为 (w-2)x(h-2)。
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
OUT_DESIGN = os.path.join(HERE, 'out')
OUT_APP = os.path.join(ROOT, 'unpackage', 'res', 'splashscreen')

BG = (247, 244, 236)
TEXT = (29, 33, 41)
SUB = (138, 144, 160)
NAME = '孽账'
TAGLINE = '无障碍自动记账'

SIZES = [
    ('hdpi', 480, 762),
    ('xhdpi', 720, 1242),
    ('xxhdpi', 1080, 1882),
]

FONT_BOLD = 'C:/Windows/Fonts/msyhbd.ttc'
FONT_REG = 'C:/Windows/Fonts/msyh.ttc'


def load_font(path, size):
    if not os.path.exists(path):
        raise SystemExit('缺少字体：' + path)
    return ImageFont.truetype(path, size)


def text_metrics(draw, text, font):
    x0, y0, x1, y1 = draw.textbbox((0, 0), text, font=font)
    return x1 - x0, y1 - y0, x0, y0


def render(w, h, logo):
    """渲染一张 w*h（含 1px 标记边）的九宫格启动图，返回 RGBA Image。"""
    cw, ch = w - 2, h - 2
    canvas = Image.new('RGBA', (cw, ch), BG + (255,))
    draw = ImageDraw.Draw(canvas)

    icon = round(cw * 0.26)
    gap1 = round(cw * 0.050)
    gap2 = round(cw * 0.024)
    name_font = load_font(FONT_BOLD, max(12, round(cw * 0.102)))
    tag_font = load_font(FONT_REG, max(10, round(cw * 0.040)))
    shadow_blur = max(2, round(cw * 0.020))
    shadow_dy = max(1, round(cw * 0.010))

    name_w, name_h, name_ox, name_oy = text_metrics(draw, NAME, name_font)
    tag_w, tag_h, tag_ox, tag_oy = text_metrics(draw, TAGLINE, tag_font)

    content_w = max(icon, name_w, tag_w)
    content_h = icon + gap1 + name_h + gap2 + tag_h
    left = (cw - content_w) // 2
    top = (ch - content_h) // 2

    icon_x = (cw - icon) // 2
    icon_y = top
    name_x = (cw - name_w) // 2 - name_ox
    name_y = top + icon + gap1 - name_oy
    tag_x = (cw - tag_w) // 2 - tag_ox
    tag_y = top + icon + gap1 + name_h + gap2 - tag_oy

    # 图标柔和投影：用图标自身 alpha 当遮罩，避免额外几何
    logo_im = logo.resize((icon, icon), Image.LANCZOS)
    pad = shadow_blur * 3
    mask = Image.new('L', (icon + pad * 2, icon + pad * 2), 0)
    mask.paste(logo_im.getchannel('A'), (pad, pad))
    mask = mask.filter(ImageFilter.GaussianBlur(shadow_blur))
    shadow = Image.new('RGBA', mask.size, (29, 33, 41, 0))
    shadow.putalpha(mask.point(lambda v: int(v * 0.34)))
    canvas.alpha_composite(shadow, (icon_x - pad, icon_y - pad + shadow_dy))
    canvas.alpha_composite(logo_im, (icon_x, icon_y))

    draw.text((name_x, name_y), NAME, font=name_font, fill=TEXT)
    draw.text((tag_x, tag_y), TAGLINE, font=tag_font, fill=SUB)

    # 受保护区：品牌块 + 投影外扩。顶/左标记避开这块，其余区域才会被拉伸。
    ext = pad + shadow_dy
    block_l = max(0, left - ext)
    block_r = min(cw - 1, left + content_w + ext)
    block_t = max(0, top - ext)
    block_b = min(ch - 1, top + content_h + ext)

    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    out.alpha_composite(canvas, (1, 1))

    black = (0, 0, 0, 255)
    for cx in range(cw):
        if cx < block_l or cx > block_r:
            out.putpixel((cx + 1, 0), black)
    for cy in range(ch):
        if cy < block_t or cy > block_b:
            out.putpixel((0, cy + 1), black)

    if block_l == 0 or block_r == cw - 1:
        raise SystemExit('品牌块过宽，左右没有可拉伸的空白，无法做九宫格')
    if block_t == 0 or block_b == ch - 1:
        raise SystemExit('品牌块过高，上下没有可拉伸的空白，无法做九宫格')
    return out


def marker_runs(flags, offset):
    """把布尔序列切成 (起点, 终点, 是否拉伸) 的连续区段，坐标换算成图片坐标。"""
    runs = []
    start = 0
    for i in range(1, len(flags) + 1):
        if i == len(flags) or flags[i] != flags[start]:
            runs.append((start + offset, i - 1 + offset, flags[start]))
            start = i
    return runs


def split_markers(im):
    w, h = im.size
    top = [im.getpixel((x, 0))[:3] == (0, 0, 0) and im.getpixel((x, 0))[3] > 0
           for x in range(1, w - 1)]
    left = [im.getpixel((0, y))[:3] == (0, 0, 0) and im.getpixel((0, y))[3] > 0
            for y in range(1, h - 1)]
    return marker_runs(top, 1), marker_runs(left, 1)


def stretch_preview(im, out_w, out_h):
    """按 Android 九宫格规则模拟拉伸：固定段保持原尺寸，拉伸段按原宽度等比例分剩余空间。"""
    x_runs, y_runs = split_markers(im)
    fixed_w = sum(b - a + 1 for a, b, stretch in x_runs if not stretch)
    fixed_h = sum(b - a + 1 for a, b, stretch in y_runs if not stretch)
    stretch_w = out_w - fixed_w
    stretch_h = out_h - fixed_h
    src_stretch_w = sum(b - a + 1 for a, b, stretch in x_runs if stretch) or 1
    src_stretch_h = sum(b - a + 1 for a, b, stretch in y_runs if stretch) or 1

    def axis_sizes(runs, stretch_total, src_total):
        sizes = []
        for a, b, stretch in runs:
            length = b - a + 1
            sizes.append(round(length / src_total * stretch_total) if stretch else length)
        return sizes

    xs = axis_sizes(x_runs, stretch_w, src_stretch_w)
    ys = axis_sizes(y_runs, stretch_h, src_stretch_h)
    out = Image.new('RGBA', (out_w, out_h), (0, 0, 0, 0))
    dy = 0
    for (ya, yb, _), dh in zip(y_runs, ys):
        dx = 0
        for (xa, xb, _), dw in zip(x_runs, xs):
            cell = im.crop((xa, ya, xb + 1, yb + 1)).resize((max(1, dw), max(1, dh)), Image.LANCZOS)
            out.alpha_composite(cell, (dx, dy))
            dx += dw
        dy += dh
    return out


def preview(sources):
    """并排预览：16:9 与 19.5:9 两种长宽比，检查图标不变形、内容仍居中。"""
    ratios = [(1080, 1920, '1080x1920 (16:9)'), (1080, 2340, '1080x2340 (19.5:9)')]
    margin = 40
    label_h = 64
    shots = []
    for out_w, out_h, label in ratios:
        src = min(sources, key=lambda s: abs(s[1] / s[2] - out_w / out_h))
        shot = stretch_preview(src[0], out_w, out_h)
        flat = Image.new('RGB', shot.size, BG)
        flat.paste(shot, (0, 0), shot)
        shots.append((flat, label))

    W = sum(s.width for s, _ in shots) + margin * (len(shots) + 1)
    H = max(s.height for s, _ in shots) + label_h + margin * 2
    sheet = Image.new('RGB', (W, H), (23, 22, 24))
    d = ImageDraw.Draw(sheet)
    font = load_font(FONT_REG, 30)
    x = margin
    for shot, label in shots:
        sheet.paste(shot, (x, margin + label_h))
        d.text((x, margin + 10), label, font=font, fill=(235, 230, 220))
        x += shot.width + margin
    sheet.save(os.path.join(OUT_DESIGN, 'preview.png'))


def main():
    logo_path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'logo.png')
    if not os.path.exists(logo_path):
        raise SystemExit('找不到图标：' + logo_path)
    logo = Image.open(logo_path).convert('RGBA')

    os.makedirs(OUT_DESIGN, exist_ok=True)
    os.makedirs(OUT_APP, exist_ok=True)
    made = []
    for name, w, h in SIZES:
        im = render(w, h, logo)
        path = os.path.join(OUT_DESIGN, name + '.9.png')
        im.save(path)
        im.save(os.path.join(OUT_APP, name + '.9.png'))
        made.append((name, w, h))
        print('generated %s  %dx%d' % (path, w, h))
    preview([(Image.open(os.path.join(OUT_DESIGN, name + '.9.png')), w, h) for name, w, h in made])
    print('preview  ' + os.path.join(OUT_DESIGN, 'preview.png'))


if __name__ == '__main__':
    main()
