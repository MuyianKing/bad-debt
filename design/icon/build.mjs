// 孽账 App 图标方案生成器：输出 4 个 1024 主图 SVG（红 2 / 黄 2）
// 用 Edge headless 渲染 PNG，再由 resize.py 缩出各密度尺寸。
import { writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
mkdirSync(join(here, 'src'), { recursive: true })

const R = 232 // 圆角，约 iOS squircle 观感

const grad = (id, x1, y1, x2, y2, stops) =>
  `<linearGradient id="${id}" x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}">${stops
    .map(([o, c, op]) => `<stop offset="${o}" stop-color="${c}"${op !== undefined ? ` stop-opacity="${op}"` : ''}/>`)
    .join('')}</linearGradient>`

const rad = (id, cx, cy, r, stops) =>
  `<radialGradient id="${id}" cx="${cx}" cy="${cy}" r="${r}">${stops
    .map(([o, c, op]) => `<stop offset="${o}" stop-color="${c}"${op !== undefined ? ` stop-opacity="${op}"` : ''}/>`)
    .join('')}</radialGradient>`

// ---------- 通用部件 ----------

// 铜钱：外圈钱缘 + 方孔 + 高光 + 内圈刻线
function coin({ cx, cy, r, rot = 0, rimRatio = 0.86, holeRatio = 0.5, hole = 'url(#holeFill)' }) {
  const hs = Math.round(r * holeRatio)
  const hx = cx - hs / 2
  const hy = cy - hs / 2
  return `<g transform="rotate(${rot} ${cx} ${cy})">
    <circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#gold)"/>
    <circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#coinSheen)"/>
    <circle cx="${cx}" cy="${cy}" r="${r - 5}" fill="none" stroke="url(#goldRim)" stroke-width="26"/>
    <circle cx="${cx}" cy="${cy}" r="${Math.round(r * rimRatio)}" fill="none" stroke="#a86c04" stroke-width="8" opacity="0.5"/>
    <circle cx="${cx}" cy="${cy}" r="${Math.round(r * rimRatio) + 9}" fill="none" stroke="#fff3c6" stroke-width="6" opacity="0.55"/>
    <rect x="${hx}" y="${hy}" width="${hs}" height="${hs}" rx="${Math.round(hs * 0.1)}" fill="${hole}"/>
    <rect x="${hx}" y="${hy}" width="${hs}" height="${hs}" rx="${Math.round(hs * 0.1)}" fill="url(#holeShade)"/>
  </g>`
}

// 四角星火花
function spark(cx, cy, s, fill = '#ffe89a', op = 0.95) {
  const w = s * 0.24
  return `<path d="M ${cx} ${cy - s} Q ${cx + w} ${cy - w} ${cx + s} ${cy} Q ${cx + w} ${cy + w} ${cx} ${cy + s} Q ${cx - w} ${cy + w} ${cx - s} ${cy} Q ${cx - w} ${cy - w} ${cx} ${cy - s} Z" fill="${fill}" opacity="${op}"/>`
}

// 混天绫：金边打底 + 红绫压面
function ribbon(d, width, ribbons = 'url(#ribbon)', edge = '#ffd24d', edgeOp = 0.9) {
  return `<path d="${d}" fill="none" stroke="${edge}" stroke-width="${width + 13}" stroke-linecap="round" stroke-linejoin="round" opacity="${edgeOp}"/>
    <path d="${d}" fill="none" stroke="${ribbons}" stroke-width="${width}" stroke-linecap="round" stroke-linejoin="round"/>`
}

// 火焰舌（向上），baseY 为底部
function flame(cx, baseY, h, w, fill) {
  return `<path d="M ${cx - w / 2} ${baseY}
    C ${cx - w * 0.66} ${baseY - h * 0.4} ${cx - w * 0.18} ${baseY - h * 0.66} ${cx} ${baseY - h}
    C ${cx + w * 0.18} ${baseY - h * 0.66} ${cx + w * 0.66} ${baseY - h * 0.4} ${cx + w / 2} ${baseY} Z" fill="${fill}"/>`
}

// 风火轮火舌：同向倾斜的卷火，沿圆环外缘排布（旋转感）
function flameBand({ cx, cy, r0, n, fills }) {
  const step = 360 / n
  const lens = [92, 80, 96, 74]
  let out = ''
  for (let i = 0; i < n; i++) {
    const a = ((i + 0.5) * step) * Math.PI / 180
    const ca = Math.cos(a), sa = Math.sin(a)
    const P = (r, t) => [cx + ca * r - sa * t, cy + sa * r + ca * t]
    const L = lens[i % lens.length]
    const ln = 66 + (i % 3) * 9
    const w = 44 + (i % 2) * 6
    const p = (r, t) => P(r, t).map(v => v.toFixed(1)).join(' ')
    // 最宽处靠下（被轮圈挡住的部分），露出的部分才是饱满的火舌
    out += `<path d="M ${p(r0, -w * 0.55)}
      C ${p(r0 + L * 0.1, -w * 1.05)} ${p(r0 + L * 0.4, -w * 1.12)} ${p(r0 + L * 0.74, ln * 0.42 - w * 0.4)}
      C ${p(r0 + L * 0.92, ln * 0.8 - w * 0.1)} ${p(r0 + L, ln)} ${p(r0 + L, ln)}
      C ${p(r0 + L * 0.8, ln * 0.5 + w * 0.5)} ${p(r0 + L * 0.36, w * 1.02)} ${p(r0, w * 0.55)} Z"
      fill="${fills[i % fills.length]}"/>`
  }
  return out
}

// ¥ 符号（笔画绘制，避免依赖字体）
function yen({ cx, cy, w = 148, h = 250, color = '#fff6e0' }) {
  const top = cy - h / 2
  const mid = cy - h * 0.06
  const sw = Math.round(h * 0.185)
  const half = w / 2
  return `<g stroke="${color}" stroke-width="${sw}" stroke-linecap="round" fill="none">
    <path d="M ${cx - half} ${top} L ${cx} ${mid} L ${cx + half} ${top}"/>
    <path d="M ${cx} ${mid - 2} L ${cx} ${cy + h / 2}"/>
    <path d="M ${cx - half * 0.82} ${cy + h * 0.1} L ${cx + half * 0.82} ${cy + h * 0.1}"/>
    <path d="M ${cx - half * 0.82} ${cy + h * 0.3} L ${cx + half * 0.82} ${cy + h * 0.3}"/>
  </g>`
}

const goldDefs = `
  ${grad('gold', 0.12, 0.02, 0.86, 0.98, [[0, '#ffeeb4'], [0.28, '#ffd75c'], [0.6, '#f5b31c'], [1, '#d08605']])}
  ${grad('goldRim', 0.1, 0.1, 0.9, 0.9, [[0, '#ffeaa0'], [0.4, '#f4b722'], [1, '#b97503']])}
  ${rad('coinSheen', 0.3, 0.2, 0.8, [[0, '#ffffff', 0.42], [0.5, '#ffffff', 0.04], [1, '#8a4b00', 0.24]])}
  ${grad('holeFill', 0.2, 0, 0.8, 1, [[0, '#8f0d1b'], [1, '#4a030a']])}
  ${grad('holeShade', 0, 0, 0, 1, [[0, '#000000', 0.45], [0.4, '#000000', 0]])}`

const ribbonDefs = `
  ${grad('ribbon', 0, 0, 1, 0.7, [[0, '#ff9a78'], [0.45, '#ff6144'], [1, '#dd3222']])}`

// ============================================================
// 方案 1 · 红 A「乾坤钱」：金铜钱 + 混天绫 + 火星
// ============================================================
const redA = `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
<defs>
  ${grad('bgA', 0.12, 0, 0.88, 1, [[0, '#f0483a'], [0.4, '#c01226'], [1, '#66050e']])}
  ${rad('glowA', 0.28, 0.14, 0.68, [[0, '#ff9d78', 0.52], [1, '#ff9d78', 0]])}
  ${goldDefs}
  ${ribbonDefs}
</defs>
<rect width="1024" height="1024" rx="${R}" fill="url(#bgA)"/>
<rect width="1024" height="1024" rx="${R}" fill="url(#glowA)"/>

${ribbon('M 40 806 C 190 862 322 738 512 754 C 700 770 812 674 984 712', 82, 'url(#ribbon)', '#ffd24d', 0.6)}

${coin({ cx: 512, cy: 500, r: 264, rot: -8, rimRatio: 0.85, holeRatio: 0.5 })}

${spark(236, 282, 38)}
${spark(792, 296, 24, '#fff0b8', 0.8)}
${spark(812, 762, 30, '#ffe08a', 0.75)}
</svg>`

// ============================================================
// 方案 2 · 红 B「钱髻哪吒」：哪吒头像，双髻=铜钱
// ============================================================
const FACE_PATH = 'M 512 318 C 640 318 708 400 708 506 C 708 598 662 666 594 698 C 570 710 542 718 512 718 C 482 718 454 710 430 698 C 362 666 316 598 316 506 C 316 400 384 318 512 318 Z'

const redB = `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
<defs>
  ${rad('bgB', 0.5, 0.42, 0.82, [[0, '#bb1722'], [0.5, '#7c0a14'], [1, '#33030a']])}
  ${rad('spotB', 0.5, 0.46, 0.44, [[0, '#ffa25c', 0.22], [1, '#ffa25c', 0]])}
  ${goldDefs}
  ${grad('skin', 0.2, 0.05, 0.8, 1, [[0, '#fffdf6'], [0.5, '#fff2d8'], [1, '#f7d095']])}
  ${rad('faceShade', 0.5, 0.34, 0.74, [[0, '#ffffff', 0.42], [0.7, '#e8a35a', 0.07], [1, '#c9791f', 0.22]])}
  ${grad('hair', 0.25, 0, 0.75, 1, [[0, '#3a0a12'], [1, '#160207']])}
  ${grad('robe', 0.2, 0, 0.8, 1, [[0, '#e6262c'], [0.6, '#c01321'], [1, '#8e0a17']])}
  ${grad('ribbonB', 0, 0, 1, 0.7, [[0, '#ff9c7c'], [0.35, '#f2493a'], [1, '#b4161f']])}
  ${rad('robeLight', 0.38, 0.16, 0.66, [[0, '#ffffff', 0.16], [1, '#ffffff', 0]])}
  ${grad('robeShade', 0.5, 0, 0.5, 1, [[0, '#4d040c', 0], [1, '#4d040c', 0.35]])}
  <clipPath id="iconClip"><rect width="1024" height="1024" rx="${R}"/></clipPath>
  <clipPath id="faceClip"><path d="${FACE_PATH}"/></clipPath>
  <clipPath id="hairClip"><ellipse cx="512" cy="456" rx="236" ry="168"/></clipPath>
</defs>
<g clip-path="url(#iconClip)">
  <rect width="1024" height="1024" fill="url(#bgB)"/>
  <rect width="1024" height="1024" fill="url(#spotB)"/>

  <!-- 混天绫：一条绫从身后横掠，两端一低一高，读成被风吹起 -->
  ${ribbon('M -20 900 C 140 948 250 840 360 802 C 440 774 476 770 512 770 C 552 770 606 782 688 806 C 800 840 936 838 1044 766', 54, 'url(#ribbonB)', '#ffd24d', 0.5)}

  <!-- 发团（比脸大一圈，形成头发轮廓） -->
  <ellipse cx="512" cy="456" rx="236" ry="168" fill="url(#hair)"/>
  <!-- 两侧蓄发 -->
  <path d="M 334 474 C 320 518 322 552 338 578" fill="none" stroke="url(#hair)" stroke-width="28" stroke-linecap="round"/>
  <path d="M 690 474 C 704 518 702 552 686 578" fill="none" stroke="url(#hair)" stroke-width="28" stroke-linecap="round"/>

  <!-- 脖颈 -->
  <rect x="462" y="676" width="100" height="180" rx="48" fill="#efc184"/>
  <path d="M 462 706 Q 512 742 562 706 L 562 760 Q 512 796 462 760 Z" fill="#d99e58" opacity="0.45"/>

  <!-- 发丝高光 -->
  <g clip-path="url(#hairClip)">
    <path d="M 336 408 C 352 330 418 284 496 276" fill="none" stroke="#7a2029" stroke-width="24" stroke-linecap="round" opacity="0.5"/>
  </g>

  <!-- 脸 -->
  <path d="${FACE_PATH}" fill="url(#skin)"/>
  <path d="${FACE_PATH}" fill="url(#faceShade)"/>

  <!-- 刘海：顶部盖过脸缘，下缘是头发分缝的波浪线 -->
  <path d="M 300 476 C 298 340 388 288 512 288 C 636 288 726 340 724 476
           C 684 442 650 490 606 452 C 566 418 542 482 512 450
           C 482 482 458 418 418 452 C 374 490 340 442 300 476 Z" fill="url(#hair)"/>

  <!-- 双髻铜钱 -->
  ${coin({ cx: 342, cy: 316, r: 88, rot: -15, rimRatio: 0.84, holeRatio: 0.48 })}
  ${coin({ cx: 682, cy: 316, r: 88, rot: 15, rimRatio: 0.84, holeRatio: 0.48 })}

  <!-- 眉眼嘴 -->
  <rect x="416" y="562" width="40" height="56" rx="20" fill="#4a0810"/>
  <rect x="568" y="562" width="40" height="56" rx="20" fill="#4a0810"/>
  <rect x="426" y="573" width="14" height="17" rx="7" fill="#fff8e6" opacity="0.9"/>
  <rect x="578" y="573" width="14" height="17" rx="7" fill="#fff8e6" opacity="0.9"/>
  <circle cx="384" cy="642" r="29" fill="#ff7a5c" opacity="0.3"/>
  <circle cx="640" cy="642" r="29" fill="#ff7a5c" opacity="0.3"/>
  <path d="M 466 642 Q 512 694 558 642" fill="none" stroke="#4a0810" stroke-width="20" stroke-linecap="round"/>

  <!-- 眉心火焰 -->
  ${flame(512, 552, 88, 62, '#c2141f')}
  ${flame(512, 544, 54, 32, '#f04a24')}
  ${flame(512, 536, 28, 14, '#ffb03a')}

  <!-- 战袍 -->
  <path d="M 246 1052 C 252 838 330 764 440 738 L 512 816 L 584 738 C 694 764 772 838 778 1052 Z" fill="url(#robe)"/>
  <path d="M 246 1052 C 252 838 330 764 440 738 L 512 816 L 584 738 C 694 764 772 838 778 1052 Z" fill="url(#robeLight)"/>
  <path d="M 246 1052 C 400 1006 624 1006 778 1052 L 778 1060 L 246 1060 Z" fill="url(#robeShade)"/>
  <path d="M 446 758 L 512 826 L 578 758" fill="none" stroke="#ffd24d" stroke-width="22" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M 512 834 L 512 1052" fill="none" stroke="#7d0713" stroke-width="14" opacity="0.3"/>
  <path d="M 300 980 Q 512 1046 724 980" fill="none" stroke="#7d0713" stroke-width="40" opacity="0.18"/>
</g>
</svg>`

// 莲瓣（指向外，配合 rotate 使用）
function petal({ cx, cy, r0, r1, w0, w1, fill, edge }) {
  const L = r1 - r0
  return `<path d="M ${cx + r0} ${cy - w0}
    C ${cx + r0 + L * 0.34} ${cy - w1} ${cx + r0 + L * 0.72} ${cy - w1 * 0.55} ${cx + r1} ${cy}
    C ${cx + r0 + L * 0.72} ${cy + w1 * 0.55} ${cx + r0 + L * 0.34} ${cy + w1} ${cx + r0} ${cy + w0} Z"
    fill="${fill}"/>`
}

// 一圈莲瓣
function petalRing({ cx, cy, n, r0, r1, w0, w1, fills, edge, offset = 0 }) {
  let out = ''
  for (let i = 0; i < n; i++) {
    const a = offset + (i * 360) / n
    out += `<g transform="rotate(${a} ${cx} ${cy})">${petal({
      cx, cy, r0, r1, w0, w1, fill: fills[i % fills.length], edge
    })}</g>`
  }
  return out
}

// ============================================================
// 方案 3 · 黄 A「莲钱」：红莲 + 金铜钱（莲花化身）
// ============================================================
const yellowA = `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
<defs>
  ${rad('bgY', 0.42, 0.3, 0.9, [[0, '#ffc733'], [0.44, '#f0920a'], [1, '#964600']])}
  ${rad('glowY', 0.3, 0.08, 0.6, [[0, '#fff4c8', 0.55], [1, '#fff4c8', 0]])}
  ${goldDefs}
  ${rad('floorY', 0.5, 0.5, 0.5, [[0, '#5f2f00', 0.4], [1, '#5f2f00', 0]])}
  ${rad('roseLight', 0.36, 0.24, 0.64, [[0, '#ffffff', 0.14], [1, '#ffffff', 0]])}
  ${rad('holeAmber', 0.5, 0.42, 0.75, [[0, '#b96300'], [1, '#7d3c00']])}
</defs>
<rect width="1024" height="1024" rx="${R}" fill="url(#bgY)"/>
<rect width="1024" height="1024" rx="${R}" fill="url(#glowY)"/>
<ellipse cx="512" cy="742" rx="300" ry="56" fill="url(#floorY)"/>

<!-- 外圈莲瓣 -->
${petalRing({ cx: 512, cy: 500, n: 12, r0: 172, r1: 336, w0: 30, w1: 64,
  fills: ['#b01120', '#bf1424'], edge: '#ffd24d' })}
<!-- 内圈莲瓣 -->
${petalRing({ cx: 512, cy: 500, n: 8, r0: 166, r1: 292, w0: 30, w1: 58, offset: 22.5,
  fills: ['#ec4232', '#d8262b'], edge: '#ffe08a' })}

<!-- 金铜钱 -->
${coin({ cx: 512, cy: 500, r: 158, rimRatio: 0.8, holeRatio: 0.46, hole: 'url(#holeAmber)' })}

${spark(812, 268, 28, '#fffbe8', 0.9)}
${spark(214, 286, 20, '#fffbe8', 0.6)}
</svg>`

// ============================================================
// 方案 4 · 黄 B「火尖焰」：火苗 + ¥（哪吒之火）
// ============================================================
const yellowB = `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
<defs>
  ${rad('bgW', 0.5, 0.42, 0.78, [[0, '#ffc02c'], [0.46, '#ec9000'], [1, '#8e4400']])}
  ${grad('flameBody', 0.2, 0.95, 0.8, 0.06, [[0, '#8c0917'], [0.34, '#c2151f'], [0.7, '#e82a1c'], [1, '#ff6a1f']])}
  ${grad('flameCore', 0.5, 0.95, 0.5, 0.15, [[0, '#ffd24d'], [0.45, '#ffb01f'], [1, '#ff7a12']])}
  ${rad('flameGlow', 0.5, 0.6, 0.5, [[0, '#ffb43a', 0.4], [1, '#ffb43a', 0]])}
  ${rad('floorW', 0.5, 0.5, 0.5, [[0, '#6b3300', 0.45], [1, '#6b3300', 0]])}
</defs>
<rect width="1024" height="1024" rx="${R}" fill="url(#bgW)"/>
<rect width="1024" height="1024" rx="${R}" fill="url(#flameGlow)"/>
<ellipse cx="512" cy="806" rx="264" ry="46" fill="url(#floorW)"/>

<g transform="translate(512 500) scale(0.91) translate(-512 -500)">
<!-- 外焰 -->
<path d="M 566 150
         C 636 292 738 372 748 512
         C 758 646 660 782 512 786
         C 364 782 266 640 282 512
         C 290 420 344 372 384 306
         C 410 372 452 388 476 330
         C 498 268 522 214 566 150 Z" fill="url(#flameBody)"/>
<!-- 内焰 -->
<path d="M 528 396
         C 576 468 640 520 640 596
         C 640 682 584 730 512 730
         C 440 730 384 682 384 596
         C 384 536 420 500 452 452
         C 462 508 494 512 504 470 Z" fill="url(#flameCore)"/>

${yen({ cx: 512, cy: 516, w: 126, h: 196, color: '#fff8e8' })}

</g>

${spark(818, 314, 30, '#fff3c8', 0.9)}
${spark(214, 390, 22, '#fff3c8', 0.65)}
${spark(742, 746, 26, '#ffd24d', 0.7)}
</svg>`

const files = { 'red-a-coin': redA, 'red-b-nezha': redB, 'yellow-a-lotus': yellowA, 'yellow-b-flame': yellowB }
for (const [name, svg] of Object.entries(files)) {
  writeFileSync(join(here, 'src', `${name}.svg`), svg)
  console.log('wrote', `src/${name}.svg`)
}
