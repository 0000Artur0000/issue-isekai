// Классификация ссылок в описании заявки. Чистый модуль без React — проверяется node:test.

const URL_PATTERN = /https?:\/\/\S+/g
const TRAILING_PUNCTUATION = /[).,!?;:\]]+$/
const IMAGE_EXTENSIONS = /\.(png|jpe?g|gif|webp)$/i
const VIDEO_EXTENSIONS = /\.(mp4|webm|ogv)$/i
const YOUTUBE_HOSTS = new Set(['youtube.com', 'www.youtube.com', 'm.youtube.com'])
const YOUTUBE_ID = /^[\w-]{6,20}$/

export function classifyUrls(text) {
  const segments = []
  let last = 0
  for (const match of text.matchAll(URL_PATTERN)) {
    const raw = match[0].replace(TRAILING_PUNCTUATION, '')
    if (match.index > last) {
      segments.push({ type: 'text', text: text.slice(last, match.index) })
    }
    segments.push(classify(raw))
    last = match.index + raw.length
  }
  if (last < text.length) {
    segments.push({ type: 'text', text: text.slice(last) })
  }
  return segments
}

function classify(raw) {
  let url
  try {
    url = new URL(raw)
  } catch {
    return { type: 'text', text: raw }
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    return { type: 'text', text: raw }
  }
  const id = youtubeId(url)
  if (id) {
    return { type: 'youtube', url: raw, embed: `https://www.youtube-nocookie.com/embed/${id}` }
  }
  if (IMAGE_EXTENSIONS.test(url.pathname)) {
    return { type: 'image', url: raw }
  }
  if (VIDEO_EXTENSIONS.test(url.pathname)) {
    return { type: 'video', url: raw }
  }
  return { type: 'link', url: raw }
}

function youtubeId(url) {
  if (url.hostname === 'youtu.be') {
    const id = url.pathname.slice(1)
    return YOUTUBE_ID.test(id) ? id : null
  }
  if (!YOUTUBE_HOSTS.has(url.hostname)) {
    return null
  }
  if (url.pathname === '/watch') {
    const id = url.searchParams.get('v') ?? ''
    return YOUTUBE_ID.test(id) ? id : null
  }
  const match = url.pathname.match(/^\/(shorts|embed|live)\/([\w-]{6,20})$/)
  return match ? match[2] : null
}
