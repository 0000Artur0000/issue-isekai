import en from './lang/en.json'
import ru from './lang/ru.json'

export type Locale = 'ru' | 'en'
type Key = keyof typeof ru
type Values = Record<string, string | number>

const dictionaries: Record<Locale, typeof ru> = { ru, en }
let locale: Locale = 'ru'

const formats = {
  ru: {
    short: new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' }),
    medium: new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short' }),
    date: new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium' }),
    long: new Intl.DateTimeFormat('ru-RU', { dateStyle: 'long' }),
  },
  en: {
    short: new Intl.DateTimeFormat('en-US', { dateStyle: 'short', timeStyle: 'short' }),
    medium: new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }),
    date: new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' }),
    long: new Intl.DateTimeFormat('en-US', { dateStyle: 'long' }),
  },
}

export function setLocale(value: string) {
  if (value !== 'ru' && value !== 'en') throw new Error(`Unsupported locale: ${value}`)
  locale = value
  document.documentElement.lang = value
}

export function t(key: Key, values: Values = {}): string {
  let text = dictionaries[locale][key]
  for (const [name, value] of Object.entries(values)) {
    text = text.replaceAll(`{${name}}`, String(value))
  }
  return text
}

export function statusLabel(value: string): string {
  return t(`status.${value}` as Key)
}

export function priorityLabel(value: string): string {
  return t(`priority.${value}` as Key)
}

export function serverStateLabel(value: string): string {
  return t(`server-state.${value}` as Key)
}

export function permissionLabel(value: string): string {
  const key = `permission.${value}` as Key
  return dictionaries[locale][key] ?? value
}

export function permissionGroupLabel(value: string): string {
  const key = `permission-group.${value}` as Key
  return dictionaries[locale][key] ?? value
}

export function roleLabel(code: string, fallback: string): string {
  const key = `role.${code}` as Key
  return dictionaries[locale][key] ?? fallback
}

export function eventLabel(value: string): string {
  const key = `event.${value}` as Key
  return dictionaries[locale][key] ?? value
}

export function inventoryReason(value: string, detail = ''): string {
  const key = `inventory.reason.${value}` as Key
  return dictionaries[locale][key] ? t(key, { value: detail }) : value
}

export function inventoryCaptureError(value: string): string {
  const key = `inventory.capture.${value}` as Key
  return dictionaries[locale][key] ?? value
}

export function errorLabel(code: string | undefined, fallback: string, status: number): string {
  const key = `error.${code}` as Key
  return (code && dictionaries[locale][key]) || fallback || t('api.http-error', { status })
}

export function formatDate(
  value: string,
  style: 'short' | 'medium' | 'date' | 'long' = 'medium',
): string {
  return formats[locale][style].format(new Date(value))
}
