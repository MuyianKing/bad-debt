"""从 design/hero/assets 的源 PNG 生成方形头像裁切（static/nezha/head-*.webp）。

头像裁切供抽屉品牌位、空态等小尺寸场景使用；头图的完整图仍用 *.webp。
换角色/换素材时，改下面的 JOBS 再跑一次即可：

    python design/hero/build_heads.py

裁切框按「头部+少量肩部」手调，避免小尺寸下脸被切掉或被身体挤小。
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "design" / "hero" / "assets"
OUT = ROOT / "static" / "nezha"

# (源文件, 输出文件, 裁切框 left/top/right/bottom)
JOBS = [
    ("nezha-grin.png", "head-grin.webp", (0, 0, 654, 660)),
    ("nezha-cheer.png", "head-cheer.webp", (36, 0, 496, 460)),
    ("nezha-grumpy.png", "head-grumpy.webp", (0, 0, 547, 580)),
]

SIZE = 320
PAD_RATIO = 0.06


def build(src_name: str, out_name: str, box: tuple[int, int, int, int]) -> None:
    img = Image.open(SRC / src_name).convert("RGBA")
    head = img.crop(box)
    # 先按比例缩进留白后的画布，保证四周有透明呼吸区
    inner = round(SIZE * (1 - PAD_RATIO * 2))
    head.thumbnail((inner, inner), Image.LANCZOS)
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    canvas.alpha_composite(
        head,
        ((SIZE - head.width) // 2, (SIZE - head.height) // 2),
    )
    canvas.save(OUT / out_name, "WEBP", quality=90, method=6)
    print(f"{out_name}: {head.width}x{head.height} -> {SIZE}x{SIZE}")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for job in JOBS:
        build(*job)


if __name__ == "__main__":
    main()
