import { useEffect, useState, type CSSProperties, type ReactNode } from 'react'
import { api } from './api'
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
  resourcePack: { id: string | null; sha1: string | null; status: string } | null
  packRevision: { id: string } | null
  packMatch: string | null
  captureError: string | null
}

// --- Adventure component -> React nodes (безопасно, без HTML) ---

const NAMED_COLORS: Record<string, string> = {
  black: '#000000',
  dark_blue: '#0000aa',
  dark_green: '#00aa00',
  dark_aqua: '#00aaaa',
  dark_red: '#aa0000',
  dark_purple: '#aa00aa',
  gold: '#ffaa00',
  gray: '#aaaaaa',
  dark_gray: '#555555',
  blue: '#5555ff',
  green: '#55ff55',
  aqua: '#55ffff',
  red: '#ff5555',
  light_purple: '#ff55ff',
  yellow: '#ffff55',
  white: '#ffffff',
}

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
  const style: CSSProperties = {}
  if (component.color) style.color = NAMED_COLORS[component.color] ?? component.color
  if (component.bold) style.fontWeight = 'bold'
  if (component.italic) style.fontStyle = 'italic'
  const decorations = [
    component.underlined ? 'underline' : '',
    component.strikethrough ? 'line-through' : '',
  ]
    .join(' ')
    .trim()
  if (decorations) style.textDecoration = decorations
  return (
    <span key={key} style={style}>
      {component.text}
      {(component.extra ?? []).map(renderComponent)}
    </span>
  )
}

export function AdventureText({ component, plain }: { component?: unknown; plain: string }) {
  return component ? <>{renderComponent(component, 0)}</> : <>{plain}</>
}

// --- ItemIcon: definition -> model chain -> 2D texture или placeholder с причиной ---

type Icon = { src?: string; reason?: string }

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
    if (plain.startsWith('builtin/')) return { reason: `клиентская модель ${plain}` }
    let model: Model
    const [namespace, path] = split(current)
    try {
      model = await api<Model>(assetUrl(revision, namespace, 'models', `${path}.json`))
    } catch {
      return { reason: `модель ${current} не найдена в паке` }
    }
    if (model.elements) {
      // ponytail: 3D geometry ждёт renderer proof (см. RENDERER_DECISION.md)
      return { reason: '3D-модель: рендерер подключается после proof' }
    }
    textures = { ...model.textures, ...textures }
    current = model.parent
  }
  let ref = textures.layer0 ?? textures.texture
  for (let depth = 0; ref?.startsWith('#') && depth < 8; depth++) {
    ref = textures[ref.slice(1)]
  }
  if (!ref || ref.startsWith('#')) return { reason: 'нет плоской текстуры' }
  const [namespace, path] = split(ref)
  return { src: assetUrl(revision, namespace, 'textures', `${path}.png`) }
}

function loadIcon(revision: string | null, slot: Slot): Promise<Icon> {
  if (!revision) {
    return Promise.resolve({ reason: 'ревизия пака не привязана к заявке' })
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
        return { reason: `нет определения ${key} в паке` }
      }
      const resolved = resolveItemDefinition(definition, {
        damage: slot.damage ?? 0,
        maxDamage: slot.max_damage ?? 0,
        amount: slot.amount,
        customModelData: slot.custom_model_data ?? {},
      }) as { models?: string[]; unsupported?: string }
      if (resolved.unsupported) return { reason: resolved.unsupported }
      if (!resolved.models?.length) return { reason: 'пустая модель' }
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
    <span className="item-fallback" title={icon?.reason}>
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
    <div role="tooltip" className="item-tooltip">
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
      {slot.amount > 1 && <p>Количество: {slot.amount}</p>}
      {maxDamage > 0 && (
        <p>
          Прочность: {maxDamage - damage} / {maxDamage} (
          {Math.round(((maxDamage - damage) / maxDamage) * 100)}%)
        </p>
      )}
      <p className="tooltip-model">{slot.item_model ?? slot.material}</p>
    </div>
  )
}

function SlotCell({
  slot,
  revision,
  selected = false,
}: {
  slot?: Slot
  revision: string | null
  selected?: boolean
}) {
  const [open, setOpen] = useState(false)
  const [flip, setFlip] = useState(false)
  if (!slot) {
    return <span className={`inv-slot${selected ? ' selected' : ''}`} aria-hidden="true" />
  }
  function show(target: HTMLElement) {
    // ponytail: держим tooltip во viewport одним flip у правого края
    setFlip(target.getBoundingClientRect().left > window.innerWidth - 280)
    setOpen(true)
  }
  return (
    <button
      type="button"
      className={`inv-slot filled${selected ? ' selected' : ''}${flip ? ' flip' : ''}`}
      aria-label={`${slot.name?.plain ?? slot.material}${selected ? ', выбранный слот' : ''}`}
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

const ARMOR_ROW = ['helmet', 'chestplate', 'leggings', 'boots', 'offhand']
const KNOWN = new RegExp(`^(hotbar_[0-8]|storage_(9|1[0-9]|2[0-9]|3[0-5])|${ARMOR_ROW.join('|')})$`)

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

  if (error) return <p role="alert">Инвентарь: {error}</p>
  if (!snapshot) return <p role="status">Загрузка инвентаря…</p>
  if (snapshot === 'none') return <p className="meta">Снимок инвентаря не прилагался.</p>

  const bySlot = new Map(snapshot.slots.map((slot) => [slot.slot, slot]))
  const revision = snapshot.packRevision?.id ?? null
  const extras = snapshot.slots.filter((slot) => !KNOWN.test(slot.slot))
  const packNote = !revision
    ? snapshot.resourcePack?.status === 'DECLINED'
      ? 'Игрок отклонил resource pack — иконки custom-предметов недоступны.'
      : 'Ревизия resource pack не привязана — иконки показаны заглушками.'
    : snapshot.packMatch === 'MISMATCH'
      ? 'Pack игрока не совпал с загруженной ревизией — иконки могут отличаться.'
      : null

  return (
    <section aria-label="Инвентарь игрока">
      <h2>Инвентарь</h2>
      {snapshot.captureError && <p role="alert">Снимок неполный: {snapshot.captureError}</p>}
      {packNote && <p className="meta">{packNote}</p>}
      <div className="inv-grid" aria-label="Основной инвентарь">
        {Array.from({ length: 27 }, (_, index) => (
          <SlotCell key={index} revision={revision} slot={bySlot.get(`storage_${index + 9}`)} />
        ))}
      </div>
      <div className="inv-grid inv-hotbar" aria-label="Хотбар">
        {Array.from({ length: 9 }, (_, index) => (
          <SlotCell
            key={index}
            revision={revision}
            slot={bySlot.get(`hotbar_${index}`)}
            selected={index === snapshot.selectedHotbarSlot}
          />
        ))}
      </div>
      <div className="inv-grid inv-armor" aria-label="Броня и вторая рука">
        {ARMOR_ROW.map((name) => (
          <SlotCell key={name} revision={revision} slot={bySlot.get(name)} />
        ))}
      </div>
      {extras.length > 0 && (
        <div className="inv-grid" aria-label="Дополнительные слоты">
          {extras.map((slot) => (
            <SlotCell key={slot.slot} revision={revision} slot={slot} />
          ))}
        </div>
      )}
    </section>
  )
}
