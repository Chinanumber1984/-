import { readFileSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const dir = resolve(root, 'demo/build')
let html = readFileSync(resolve(dir, 'demo.html'), 'utf8')
html = html.replace(/<script\b[^>]*src="([^"]+)"[^>]*><\/script>/g, (_, asset) => {
 const script = readFileSync(resolve(dir, asset), 'utf8').replace(/<\/script/gi, '<\\/script')
 return `<script type="module">${script}</script>`
})
html = html.replace(/<link\b[^>]*href="([^"]+\.css)"[^>]*>/g, (_, asset) => `<style>${readFileSync(resolve(dir, asset), 'utf8')}</style>`)
if (/<(?:script|link)\b[^>]*(?:src|href)="/.test(html)) throw new Error('Standalone export still has external assets')
writeFileSync(resolve(root, 'demo/SRM演示.html'), html)
console.log('Standalone demo exported: demo/SRM演示.html')
