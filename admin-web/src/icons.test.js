import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import * as all from '@element-plus/icons-vue'
import { icons } from './icons.js'

function files(dir) {
  return readdirSync(dir).flatMap(n => {
    const p = join(dir, n)
    return statSync(p).isDirectory() ? files(p) : /\.(vue|js)$/.test(n) ? [p] : []
  })
}

test('every Element Plus icon referenced in src is registered', () => {
  const names = Object.keys(all)
  const missing = new Set()
  for (const f of files('src')) {
    if (f.endsWith('icons.js') || f.endsWith('.test.js') || f.endsWith('.d.ts')) continue
    const s = readFileSync(f, 'utf8')
    for (const n of names) {
      if (new RegExp(`<${n}[\\s/>]|icon: ?'${n}'|icon="${n}"`).test(s) && !icons[n]) missing.add(`${n} (${f})`)
    }
  }
  assert.deepEqual([...missing], [])
})
