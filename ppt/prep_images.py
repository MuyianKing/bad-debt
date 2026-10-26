# -*- coding: utf-8 -*-
"""截图预处理：去状态栏、圆角、细节特写裁剪"""
import os
from PIL import Image, ImageDraw

A = r"E:\project\muyian\muyian-app\ppt\assets"
STATUS_BAR = 130          # 1200 宽下状态栏高度
RADIUS = 40

def rounded(im, radius=RADIUS):
    im = im.convert("RGBA")
    mask = Image.new("L", im.size, 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], radius=radius, fill=255)
    im.putalpha(mask)
    return im

def load(name, top=STATUS_BAR):
    return Image.open(os.path.join(A, name)).convert("RGB").crop((0, top, 1200, 2670))

# --- 整屏截图（去状态栏 + 圆角）---
for src, out in [("shot_35.jpg", "phone_home.png"),      # 首页
                 ("shot_30.jpg", "phone_record.png"),    # 记一笔
                 ("shot_34.jpg", "phone_calendar.png"),  # 日历
                 ("shot_32.jpg", "phone_rank.png"),      # 分类排行
                 ("shot_29.jpg", "phone_cats.png")]:     # 分类管理
    rounded(load(src)).save(os.path.join(A, out))
    print(out)

# --- 细节特写 ---
crops = [
    ("shot_30.jpg", "crop_categories.png", (0, 320, 1200, 1580)),    # 分类图标网格
    ("shot_30.jpg", "crop_keyboard.png",   (0, 1755, 1200, 2670)),   # 备注行 + 快捷标签 + 键盘
    ("shot_33.jpg", "crop_overview.png",   (20, 495, 1180, 1090)),   # 收支总览卡
    ("shot_33.jpg", "crop_daily.png",      (20, 1090, 1180, 1950)),  # 每日统计柱状图
    ("shot_29.jpg", "crop_subcat.png",     (0, 620, 1200, 1120)),    # 零食二级分类展开
]
for src, out, box in crops:
    im = Image.open(os.path.join(A, src)).convert("RGB").crop(box)
    rounded(im, radius=28).save(os.path.join(A, out))
    print(out, im.size)
print("done")
