import assert from 'node:assert/strict'
import { test } from 'node:test'
import { componentClasses } from './adventure.mjs'

test('Adventure styles use only allowlisted CSS classes', () => {
  assert.equal(
    componentClasses({
      color: 'red',
      bold: true,
      italic: true,
      underlined: true,
      strikethrough: true,
    }),
    'mc-color-red mc-bold mc-italic mc-underlined mc-strikethrough',
  )
  assert.equal(componentClasses({ color: '#ff0000' }), '')
  assert.equal(componentClasses({ color: 'red;display:none' }), '')
})
