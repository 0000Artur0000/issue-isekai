import { useEffect, useState, type ReactNode } from 'react'
import { componentClasses } from './adventure.mjs'
import { api } from './api'
import { inventoryCaptureError, inventoryReason, t } from './i18n'
import {
  EQUIPMENT_SLOTS,
  HOTBAR_SLOTS,
  STORAGE_SLOTS,
  isKnownSlot,
} from './inventory-slots.mjs'
import { resolveItemDefinition } from './items.mjs'

export type Slot = {
  slot: string
  material: string
  amount: number
  name?: { plain: string; component?: unknown } | null
  lore?: { plain: string; component?: unknown }[] | null
  damage?: number | null
  max_damage?: number | null
  item_model?: string | null
  custom_model_data?: {
    floats?: number[]
    flags?: boolean[]
    strings?: string[]
    colors?: number[]
  } | null
  enchantments?: { key: string; level: number }[] | null
}

type Snapshot = {
  schemaVersion: number
  minecraftVersion: string
  selectedHotbarSlot: number
  slots: Slot[]
  packRevision: { id: string } | null
  packMatch: string | null
  captureError: string | null
}

// --- Adventure component -> React nodes (безопасно, без HTML) ---

type Component = {
  text?: string
  color?: string
  bold?: boolean
  italic?: boolean
  underlined?: boolean
  strikethrough?: boolean
  extra?: unknown[]
}

function renderComponent(node: unknown, key: number): ReactNode {
  if (typeof node === 'string' || typeof node === 'number') return String(node)
  if (Array.isArray(node)) return node.map(renderComponent)
  if (node === null || typeof node !== 'object') return null
  const component = node as Component
  const className = componentClasses(component)
  return (
    <span key={key} className={className || undefined}>
      {component.text}
      {(component.extra ?? []).map(renderComponent)}
    </span>
  )
}

export function AdventureText({ component, plain }: { component?: unknown; plain: string }) {
  return component ? <>{renderComponent(component, 0)}</> : <>{plain}</>
}

// --- ItemIcon: definition -> model chain -> 2D texture или placeholder с причиной ---

type Icon = { src?: string; reason?: string; value?: string }

// ponytail: один модульный кэш по (revision, model key, visual components) — плану ровно это и нужно
const iconCache = new Map<string, Promise<Icon>>()

function split(key: string): [string, string] {
  const colon = key.indexOf(':')
  return colon < 0 ? ['minecraft', key] : [key.slice(0, colon), key.slice(colon + 1)]
}

function assetUrl(revision: string, namespace: string, kind: string, path: string): string {
  return `/api/resource-packs/${revision}/assets/${namespace}/${kind}/${path}`
}

type Model = { parent?: string; elements?: unknown; textures?: Record<string, string> }

async function modelIcon(revision: string, modelKey: string): Promise<Icon> {
  let textures: Record<string, string> = {}
  let current: string | undefined = modelKey
  for (let depth = 0; current && depth < 8; depth++) {
    const plain = current.replace(/^minecraft:/, '')
    if (plain === 'builtin/generated') break
    if (plain.startsWith('builtin/')) return { reason: 'client-model', value: plain }
    let model: Model
    const [namespace, path] = split(current)
    try {
      model = await api<Model>(assetUrl(revision, namespace, 'models', `${path}.json`))
    } catch {
      return { reason: 'model-missing', value: current }
    }
    if (model.elements) {
      // ponytail: mesh proof пройден, WebGL pixel proof ждёт реальный pack (RENDERER_PROOF.md)
      return { reason: 'webgl' }
    }
    textures = { ...model.textures, ...textures }
    current = model.parent
  }
  let ref = textures.layer0 ?? textures.texture
  for (let depth = 0; ref?.startsWith('#') && depth < 8; depth++) {
    ref = textures[ref.slice(1)]
  }
  if (!ref || ref.startsWith('#')) return { reason: 'no-texture' }
  const [namespace, path] = split(ref)
  return { src: assetUrl(revision, namespace, 'textures', `${path}.png`) }
}

function loadIcon(revision: string | null, slot: Slot): Promise<Icon> {
  if (!revision) {
    return Promise.resolve({ reason: 'no-revision' })
  }
  const key = slot.item_model ?? slot.material
  const cacheKey = `${revision}:${key}:${slot.damage ?? 0}:${JSON.stringify(slot.custom_model_data ?? null)}`
  let promise = iconCache.get(cacheKey)
  if (!promise) {
    promise = (async () => {
      const [namespace, path] = split(key)
      let definition: unknown
      try {
        definition = await api(assetUrl(revision, namespace, 'items', `${path}.json`))
      } catch {
        return { reason: 'definition-missing', value: key }
      }
      const resolved = resolveItemDefinition(definition, {
        damage: slot.damage ?? 0,
        maxDamage: slot.max_damage ?? 0,
        amount: slot.amount,
        customModelData: slot.custom_model_data ?? {},
      }) as { models?: string[]; unsupported?: string; value?: string }
      if (resolved.unsupported) return { reason: resolved.unsupported, value: resolved.value }
      if (!resolved.models?.length) return { reason: 'empty-model' }
      // ponytail: composite рисуем первой моделью, полная стопка — вместе с renderer
      return modelIcon(revision, resolved.models[0])
    })()
    iconCache.set(cacheKey, promise)
  }
  return promise
}

function ItemIcon({ revision, slot }: { revision: string | null; slot: Slot }) {
  const [icon, setIcon] = useState<Icon | null>(null)
  useEffect(() => {
    let cancelled = false
    loadIcon(revision, slot).then((loaded) => {
      if (!cancelled) setIcon(loaded)
    })
    return () => {
      cancelled = true
    }
  }, [revision, slot])
  if (icon?.src) {
    return <img className="item-icon" src={icon.src} alt="" />
  }
  const abbreviation = split(slot.material)[1].split('_').slice(0, 2)
  return (
    <span className="item-fallback" title={icon?.reason && inventoryReason(icon.reason, icon.value)}>
      {abbreviation.map((word) => word[0]?.toUpperCase()).join('')}
    </span>
  )
}

// --- Tooltip и сетка ---

function levelSuffix(level: number): string {
  return level === 1 ? '' : ` ${level}`
}

function Tooltip({ slot }: { slot: Slot }) {
  const damage = slot.damage ?? 0
  const maxDamage = slot.max_damage ?? 0
  return (
    <div role="tooltip" className="item-tooltip mc-tooltip">
      <p className="tooltip-name">
        <AdventureText component={slot.name?.component} plain={slot.name?.plain ?? slot.material} />
      </p>
      {slot.lore?.map((line, index) => (
        <p key={index} className="tooltip-lore">
          <AdventureText component={line.component} plain={line.plain} />
        </p>
      ))}
      {slot.enchantments?.map((enchantment) => (
        <p key={enchantment.key} className="tooltip-enchant">
          {split(enchantment.key)[1].replaceAll('_', ' ')}
          {levelSuffix(enchantment.level)}
        </p>
      ))}
      {slot.amount > 1 && <p>{t('inventory.amount', { amount: slot.amount })}</p>}
      {maxDamage > 0 && (
        <p>{t('inventory.durability', {
          current: maxDamage - damage,
          max: maxDamage,
          percent: Math.round(((maxDamage - damage) / maxDamage) * 100),
        })}</p>
      )}
      <p className="tooltip-model">{slot.item_model ?? slot.material}</p>
    </div>
  )
}

function SlotCell({
  slot,
  revision,
  selected = false,
  ghost,
}: {
  slot?: Slot
  revision: string | null
  selected?: boolean
  ghost?: string
}) {
  const [open, setOpen] = useState(false)
  const [flip, setFlip] = useState(false)
  if (!slot) {
    return (
      <span className={`mc-slot inv-slot${selected ? ' selected' : ''}`} aria-hidden="true">
        {ghost && <img className="slot-ghost" src={`/assets/mc/slot-icons/${ghost}.png`} alt="" />}
      </span>
    )
  }
  function show(target: HTMLElement) {
    // ponytail: держим tooltip во viewport одним flip у правого края
    setFlip(target.getBoundingClientRect().left > window.innerWidth - 300)
    setOpen(true)
  }
  return (
    <button
      type="button"
      className={`mc-slot inv-slot filled${selected ? ' selected' : ''}${flip ? ' flip' : ''}`}
      aria-label={`${slot.name?.plain ?? slot.material}${selected ? `, ${t('inventory.selected')}` : ''}`}
      onMouseEnter={(event) => show(event.currentTarget)}
      onMouseLeave={() => setOpen(false)}
      onFocus={(event) => show(event.currentTarget)}
      onBlur={() => setOpen(false)}
      onKeyDown={(event) => {
        if (event.key === 'Escape') setOpen(false)
      }}
    >
      <ItemIcon revision={revision} slot={slot} />
      {slot.amount > 1 && <span className="inv-amount">{slot.amount}</span>}
      {open && <Tooltip slot={slot} />}
    </button>
  )
}

const EQUIPMENT_GHOSTS: Record<string, string> = {
  helmet: 'helmet',
  chestplate: 'chestplate',
  leggings: 'leggings',
  boots: 'boots',
  offhand: 'shield',
}

export default function Inventory({ reportId }: { reportId: string }) {
  const [snapshot, setSnapshot] = useState<Snapshot | 'none' | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    api<Snapshot | undefined>(`/api/reports/${reportId}/inventory`).then(
      (loaded) => {
        if (!cancelled) setSnapshot(loaded ?? 'none')
      },
      (cause: Error) => {
        if (!cancelled) setError(cause.message)
      },
    )
    return () => {
      cancelled = true
    }
  }, [reportId])

  if (error) return <p role="alert">{t('inventory.error', { error })}</p>
  if (!snapshot) return <p role="status">{t('inventory.loading')}</p>
  if (snapshot === 'none') return <p className="meta">{t('inventory.none')}</p>

  const bySlot = new Map(snapshot.slots.map((slot) => [slot.slot, slot]))
  const revision = snapshot.packRevision?.id ?? null
  const extras = snapshot.slots.filter((slot) => !isKnownSlot(slot.slot))
  const packNote = !revision
    ? t('inventory.no-pack')
    : null

  return (
    <section className="report-section mc-panel" aria-label={t('inventory.player')}>
      <h2>
        <img className="mc-ico" src="/assets/mc/item/bundle.png" alt="" />
        {t('inventory.title')}
      </h2>
      {snapshot.captureError && <p role="alert">{t('inventory.incomplete', { error: inventoryCaptureError(snapshot.captureError) })}</p>}
      {packNote && <p className="meta">{packNote}</p>}
      <div className="inv-shell">
        <div className="inv-armor-col" aria-label={t('inventory.equipment')}>
          {EQUIPMENT_SLOTS.map((name) => (
            <SlotCell
              key={name}
              revision={revision}
              slot={bySlot.get(name)}
              ghost={EQUIPMENT_GHOSTS[name]}
            />
          ))}
        </div>
        <div className="inv-main">
          <div className="inv-grid" aria-label={t('inventory.main')}>
            {STORAGE_SLOTS.map((name) => (
              <SlotCell key={name} revision={revision} slot={bySlot.get(name)} />
            ))}
          </div>
          <div className="inv-grid inv-hotbar" aria-label={t('inventory.hotbar')}>
            {HOTBAR_SLOTS.map((name, index) => (
              <SlotCell
                key={name}
                revision={revision}
                slot={bySlot.get(name)}
                selected={index === snapshot.selectedHotbarSlot}
              />
            ))}
          </div>
        </div>
      </div>
      {extras.length > 0 && (
        <div className="inv-grid" aria-label={t('inventory.extra')}>
          {extras.map((slot) => (
            <SlotCell key={slot.slot} revision={revision} slot={slot} />
          ))}
        </div>
      )}
    </section>
  )
}
