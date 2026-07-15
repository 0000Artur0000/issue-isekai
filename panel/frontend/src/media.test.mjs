import assert from 'node:assert/strict'
import { test } from 'node:test'
import { classifyUrls } from './media.mjs'

test('plain text stays one segment', () => {
  assert.deepEqual(classifyUrls('просто текст без ссылок'), [
    { type: 'text', text: 'просто текст без ссылок' },
  ])
})

test('image, video and link classified by extension', () => {
  const segments = classifyUrls(
    'скрин https://ex.com/a.PNG видео https://ex.com/b.mp4 лог https://ex.com/log.txt',
  )
  assert.deepEqual(
    segments.map((segment) => segment.type),
    ['text', 'image', 'text', 'video', 'text', 'link'],
  )
})

test('trailing punctuation not swallowed into url', () => {
  const [, image] = classifyUrls('см. (https://ex.com/a.png).')
  assert.equal(image.url, 'https://ex.com/a.png')
})

test('youtube watch and short link rewritten to nocookie embed', () => {
  for (const url of [
    'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    'https://youtu.be/dQw4w9WgXcQ',
    'https://m.youtube.com/shorts/dQw4w9WgXcQ',
  ]) {
    const [segment] = classifyUrls(url)
    assert.equal(segment.type, 'youtube', url)
    assert.equal(segment.embed, 'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ')
  }
})

test('lookalike youtube host is a plain link', () => {
  const [segment] = classifyUrls('https://evil-youtube.com/watch?v=dQw4w9WgXcQ')
  assert.equal(segment.type, 'link')
})

test('non-http protocol is not embedded', () => {
  const segments = classifyUrls('ftp://ex.com/a.png javascript:alert(1)')
  assert.ok(segments.every((segment) => segment.type === 'text'))
})
