import { PNG } from 'pngjs'

const BASE_URL = process.env.EVEN_SIM_URL ?? 'http://127.0.0.1:9898'
const READY_MARKER = '[langbang-g2] ready'
const TIMEOUT_MS = 30_000

async function get(path) {
  const response = await fetch(`${BASE_URL}${path}`)
  if (!response.ok) throw new Error(`${path} returned ${response.status}`)
  return response
}

async function getJson(path) {
  return (await get(path)).json()
}

async function getPng(path) {
  const bytes = Buffer.from(await (await get(path)).arrayBuffer())
  return { bytes, png: PNG.sync.read(bytes) }
}

async function postJson(path, body) {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw new Error(`${path} returned ${response.status}`)
}

async function waitForReady() {
  const deadline = Date.now() + TIMEOUT_MS
  let sinceId = 0

  while (Date.now() < deadline) {
    const data = await getJson(`/api/console?since_id=${sinceId}`)
    for (const entry of data.entries ?? []) {
      sinceId = Math.max(sinceId, entry.id)
      if (String(entry.message ?? '').includes(READY_MARKER)) return
    }
    await new Promise((resolve) => setTimeout(resolve, 250))
  }

  throw new Error(`Simulator did not log ${READY_MARKER}`)
}

function litPixelCount(png) {
  let lit = 0
  for (let index = 3; index < png.data.length; index += 4) {
    if (png.data[index] > 0) lit += 1
  }
  return lit
}

function changedByteCount(left, right) {
  const length = Math.min(left.length, right.length)
  let changed = Math.abs(left.length - right.length)
  for (let index = 0; index < length; index += 1) {
    if (left[index] !== right[index]) changed += 1
  }
  return changed
}

async function main() {
  const ping = await (await get('/api/ping')).text()
  if (ping.trim() !== 'pong') throw new Error(`Unexpected simulator ping: ${ping}`)

  await waitForReady()

  const before = await getPng('/api/screenshot/glasses')
  const lit = litPixelCount(before.png)
  if (before.png.width !== 576 || before.png.height !== 288) {
    throw new Error(`Expected 576x288 framebuffer, got ${before.png.width}x${before.png.height}`)
  }
  if (lit < 100) throw new Error(`Glasses framebuffer is effectively blank: ${lit} lit pixels`)

  await postJson('/api/input', { action: 'double_click' })
  await new Promise((resolve) => setTimeout(resolve, 750))

  const after = await getPng('/api/screenshot/glasses')
  const changed = changedByteCount(before.bytes, after.bytes)
  if (changed < 100) throw new Error(`Double click did not visibly update the framebuffer: ${changed} changed bytes`)

  console.log(`OK simulator smoke: ${lit} lit pixels, ${changed} changed bytes after double click`)
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
