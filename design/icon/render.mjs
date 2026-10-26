// 用本机 Edge headless 把 SVG 渲染成 1024 PNG
import { execFileSync } from 'node:child_process'
import { mkdirSync, existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const OUT = join(here, 'out')
mkdirSync(OUT, { recursive: true })

const names = process.argv.slice(2).length ? process.argv.slice(2) : ['red-a-coin', 'red-b-nezha', 'yellow-a-lotus', 'yellow-b-flame']

for (const name of names) {
  const svg = resolve(here, 'src', `${name}.svg`)
  const png = resolve(OUT, `${name}-1024.png`)
  if (!existsSync(svg)) throw new Error(`missing ${svg}`)
  execFileSync(EDGE, [
    '--headless=new', '--disable-gpu', '--hide-scrollbars', '--no-first-run',
    '--user-data-dir=C:/Users/12808/AppData/Local/Temp/edge-icon-profile',
    '--window-size=1024,1024', '--force-device-scale-factor=1',
    '--default-background-color=00000000', '--virtual-time-budget=2500',
    `--screenshot=${png}`, `file:///${svg.replace(/\\/g, '/')}`
  ], { stdio: ['ignore', 'ignore', 'ignore'] })
  console.log('rendered', png)
}
