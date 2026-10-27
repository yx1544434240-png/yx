// 构建后体积门槛：单个 JS chunk 或 JS 总量超限时让构建失败（E3）
import { readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const dir = fileURLToPath(new URL('../dist/assets/', import.meta.url))
const CHUNK_LIMIT = 600 * 1024
const TOTAL_LIMIT = 1600 * 1024
const files = readdirSync(dir).filter(name => name.endsWith('.js'))
let total = 0
const tooBig = []
for (const name of files) {
  const size = statSync(join(dir, name)).size
  total += size
  if (size > CHUNK_LIMIT) tooBig.push(`${name} ${(size / 1024).toFixed(1)} kB`)
}
console.log(`JS total ${(total / 1024).toFixed(1)} kB in ${files.length} chunks`)
if (tooBig.length || total > TOTAL_LIMIT) {
  console.error(`Bundle budget exceeded (chunk ≤ ${CHUNK_LIMIT / 1024} kB, total ≤ ${TOTAL_LIMIT / 1024} kB)`)
  tooBig.forEach(line => console.error('  ' + line))
  process.exit(1)
}
