import {
  CreateStartUpPageContainer,
  EvenAppBridge,
  OsEventTypeList,
  StartUpPageCreateResult,
  TextContainerProperty,
  TextContainerUpgrade,
  waitForEvenAppBridge,
  type EvenHubEvent,
} from '@evenrealities/even_hub_sdk'
import { fixturePhrases, type GlassPhrase } from './phrases'
import './styles.css'

const CONTAINER_ID = 1
const CONTAINER_NAME = 'main'
const MAX_CONTENT_CHARS = 900
const BRIDGE_TIMEOUT_MS = 5_000
const FEED_POLL_MS = 1_000

const feedUrl = import.meta.env.VITE_LANGBANG_FEED_URL?.trim()

let bridge: EvenAppBridge | null = null
let startupPageCreated = false
let phraseIndex = 0
let showGloss = true
let asciiFallback = false
let readyLogged = false
let status = 'Waiting for Even App bridge...'
let feedPhrase: GlassPhrase | null = null

const app = document.querySelector<HTMLDivElement>('#app')

function activePhrase(): GlassPhrase {
  return feedPhrase ?? fixturePhrases[phraseIndex]
}

function formatForG2(text: string): string {
  const normalized = asciiFallback
    ? text.replace(/[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]/g, (char) => {
      const ascii: Record<string, string> = {
        'ą': 'a',
        'ć': 'c',
        'ę': 'e',
        'ł': 'l',
        'ń': 'n',
        'ó': 'o',
        'ś': 's',
        'ź': 'z',
        'ż': 'z',
        'Ą': 'A',
        'Ć': 'C',
        'Ę': 'E',
        'Ł': 'L',
        'Ń': 'N',
        'Ó': 'O',
        'Ś': 'S',
        'Ź': 'Z',
        'Ż': 'Z',
      }
      return ascii[char] ?? char
    })
    : text

  return normalized
    .replace(/[^\S\n]+/g, ' ')
    .trim()
}

function formatGlassText(): string {
  const phrase = activePhrase()
  const mode = feedPhrase ? 'LIVE FEED' : `${phraseIndex + 1}/${fixturePhrases.length}`
  const lines = [
    `LangBang G2  ${mode}`,
    asciiFallback ? 'ASCII fallback' : 'Polish diacritics',
    phrase.position ?? 'Polish practice',
    '',
    formatForG2(phrase.pl),
    '',
  ]

  if (showGloss) {
    lines.push(`EN: ${formatForG2(phrase.en)}`)
    if (phrase.literal) lines.push(`LIT: ${formatForG2(phrase.literal)}`)
  } else {
    lines.push('EN hidden')
    lines.push('Gloss hidden')
  }

  lines.push('', 'Press next | Up/Down move', 'Double press toggles gloss')
  if (!asciiFallback) lines.push('Glyphs: ąćęłńóśźż Łódź')

  const content = lines.join('\n')
  return content.length > MAX_CONTENT_CHARS
    ? `${content.slice(0, MAX_CONTENT_CHARS - 3)}...`
    : content
}

async function syncGlasses(): Promise<void> {
  if (!bridge) return

  const content = formatGlassText()

  if (!startupPageCreated) {
    const result = await bridge.createStartUpPageContainer(
      new CreateStartUpPageContainer({
        containerTotalNum: 1,
        textObject: [
          new TextContainerProperty({
            xPosition: 0,
            yPosition: 0,
            width: 576,
            height: 288,
            borderWidth: 0,
            borderColor: 5,
            paddingLength: 8,
            containerID: CONTAINER_ID,
            containerName: CONTAINER_NAME,
            content,
            isEventCapture: 1,
          }),
        ],
      }),
    )

    if (result !== StartUpPageCreateResult.success) {
      status = result === StartUpPageCreateResult.invalid
        ? 'No Even host accepted the startup page. Open this URL in the simulator or sideload it with the Even app.'
        : `G2 startup page failed: ${StartUpPageCreateResult[result] ?? result}`
      renderCompanion()
      return
    }

    startupPageCreated = true
    if (!readyLogged) {
      console.info('[langbang-g2] ready')
      readyLogged = true
    }
    status = 'Connected to Even App bridge.'
    renderCompanion()
    return
  }

  await bridge.textContainerUpgrade(
    new TextContainerUpgrade({
      containerID: CONTAINER_ID,
      containerName: CONTAINER_NAME,
      contentOffset: 0,
      contentLength: content.length,
      content,
    }),
  )
}

function eventType(event: EvenHubEvent): OsEventTypeList | undefined {
  return (
    event.textEvent?.eventType ??
    event.listEvent?.eventType ??
    event.sysEvent?.eventType
  )
}

function move(delta: number): void {
  if (feedPhrase) feedPhrase = null
  phraseIndex = (phraseIndex + delta + fixturePhrases.length) % fixturePhrases.length
}

function handleGlassEvent(event: EvenHubEvent): void {
  console.info(`[langbang-g2] event ${eventType(event) ?? 'unknown'}`)

  switch (eventType(event)) {
    case OsEventTypeList.CLICK_EVENT:
      move(1)
      break
    case OsEventTypeList.DOUBLE_CLICK_EVENT:
      showGloss = !showGloss
      break
    case OsEventTypeList.SCROLL_TOP_EVENT:
      move(-1)
      break
    case OsEventTypeList.SCROLL_BOTTOM_EVENT:
      move(1)
      break
    default:
      return
  }

  renderCompanion()
  void syncGlasses()
}

async function waitForBridgeWithTimeout(): Promise<EvenAppBridge | null> {
  return Promise.race<EvenAppBridge | null>([
    waitForEvenAppBridge(),
    new Promise((resolve) => {
      window.setTimeout(() => resolve(null), BRIDGE_TIMEOUT_MS)
    }),
  ])
}

async function pollFeed(): Promise<void> {
  if (!feedUrl) return

  try {
    const response = await fetch(feedUrl, { cache: 'no-store' })
    if (!response.ok) throw new Error(`${response.status} ${response.statusText}`)

    const candidate = (await response.json()) as Partial<GlassPhrase>
    if (!candidate.pl || !candidate.en) {
      throw new Error('Feed JSON must include pl and en fields')
    }

    feedPhrase = {
      pl: candidate.pl,
      en: candidate.en,
      literal: candidate.literal,
      position: candidate.position ?? 'Live LangBang',
    }
    status = 'Loaded live LangBang feed.'
    renderCompanion()
    await syncGlasses()
  } catch (error) {
    status = `Feed unavailable: ${error instanceof Error ? error.message : String(error)}`
    renderCompanion()
  } finally {
    window.setTimeout(() => void pollFeed(), FEED_POLL_MS)
  }
}

function renderCompanion(): void {
  if (!app) return

  const phrase = activePhrase()
  const source = feedPhrase ? 'Live feed' : 'Fixture deck'

  app.innerHTML = `
    <section class="shell">
      <p class="eyebrow">LangBang G2 test</p>
      <h1>${escapeHtml(formatForG2(phrase.pl))}</h1>
      <p class="translation">${escapeHtml(phrase.en)}</p>
      <dl>
        <div>
          <dt>Source</dt>
          <dd>${escapeHtml(source)}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>${escapeHtml(status)}</dd>
        </div>
      </dl>
      <div class="preview">
        <pre>${escapeHtml(formatGlassText())}</pre>
      </div>
      <div class="actions">
        <button data-action="prev">Previous</button>
        <button data-action="next">Next</button>
        <button data-action="gloss">${showGloss ? 'Hide gloss' : 'Show gloss'}</button>
        <button data-action="ascii">${asciiFallback ? 'Use diacritics' : 'ASCII fallback'}</button>
        <button data-action="sync">Push to glasses</button>
      </div>
    </section>
  `

  app.querySelector<HTMLButtonElement>('[data-action="prev"]')?.addEventListener('click', () => {
    move(-1)
    renderCompanion()
    void syncGlasses()
  })
  app.querySelector<HTMLButtonElement>('[data-action="next"]')?.addEventListener('click', () => {
    move(1)
    renderCompanion()
    void syncGlasses()
  })
  app.querySelector<HTMLButtonElement>('[data-action="gloss"]')?.addEventListener('click', () => {
    showGloss = !showGloss
    renderCompanion()
    void syncGlasses()
  })
  app.querySelector<HTMLButtonElement>('[data-action="ascii"]')?.addEventListener('click', () => {
    asciiFallback = !asciiFallback
    renderCompanion()
    void syncGlasses()
  })
  app.querySelector<HTMLButtonElement>('[data-action="sync"]')?.addEventListener('click', () => {
    void syncGlasses()
  })
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

async function main(): Promise<void> {
  renderCompanion()

  bridge = await waitForBridgeWithTimeout()
  if (!bridge) {
    status = 'Even App bridge not detected. Open this URL in the simulator or sideload it with the Even app.'
    renderCompanion()
    void pollFeed()
    return
  }

  bridge.onEvenHubEvent(handleGlassEvent)
  await syncGlasses()
  void pollFeed()
}

void main()
