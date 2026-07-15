import assert from 'node:assert/strict'
import { test } from 'node:test'
import { resolveItemDefinition } from './items.mjs'

const context = {
  damage: 121,
  maxDamage: 1561,
  amount: 1,
  customModelData: { floats: [1.5], flags: [true], strings: ['ruby'], colors: [] },
}

test('plain model resolves to its key', () => {
  assert.deepEqual(
    resolveItemDefinition(
      { model: { type: 'minecraft:model', model: 'example:item/ruby_pickaxe' } },
      context,
    ),
    { models: ['example:item/ruby_pickaxe'] },
  )
})

test('composite collects supported children', () => {
  const definition = {
    model: {
      type: 'minecraft:composite',
      models: [
        { type: 'minecraft:model', model: 'a:base' },
        { type: 'minecraft:special', model: {} },
        { type: 'minecraft:model', model: 'a:overlay' },
      ],
    },
  }
  assert.deepEqual(resolveItemDefinition(definition, context), { models: ['a:base', 'a:overlay'] })
})

test('condition damaged picks on_true branch', () => {
  const definition = {
    model: {
      type: 'minecraft:condition',
      property: 'minecraft:damaged',
      on_true: { type: 'minecraft:model', model: 'a:broken' },
      on_false: { type: 'minecraft:model', model: 'a:new' },
    },
  }
  assert.deepEqual(resolveItemDefinition(definition, context), { models: ['a:broken'] })
})

test('select matches custom_model_data string and falls back otherwise', () => {
  const definition = {
    model: {
      type: 'minecraft:select',
      property: 'minecraft:custom_model_data',
      cases: [{ when: 'ruby', model: { type: 'minecraft:model', model: 'a:ruby' } }],
      fallback: { type: 'minecraft:model', model: 'a:plain' },
    },
  }
  assert.deepEqual(resolveItemDefinition(definition, context), { models: ['a:ruby'] })
  const other = { ...context, customModelData: { strings: ['emerald'] } }
  assert.deepEqual(resolveItemDefinition(definition, other), { models: ['a:plain'] })
})

test('range_dispatch by normalized damage picks highest threshold below value', () => {
  const definition = {
    model: {
      type: 'minecraft:range_dispatch',
      property: 'minecraft:damage',
      entries: [
        { threshold: 0.5, model: { type: 'minecraft:model', model: 'a:half' } },
        { threshold: 0.9, model: { type: 'minecraft:model', model: 'a:dying' } },
      ],
      fallback: { type: 'minecraft:model', model: 'a:fresh' },
    },
  }
  assert.deepEqual(resolveItemDefinition(definition, { ...context, damage: 1500 }), {
    models: ['a:dying'],
  })
  assert.deepEqual(resolveItemDefinition(definition, context), { models: ['a:fresh'] })
})

test('dynamic property and unknown type surface a reason, not a crash', () => {
  const dynamic = {
    model: {
      type: 'minecraft:condition',
      property: 'minecraft:using_item',
      on_true: { type: 'minecraft:model', model: 'a:x' },
      on_false: { type: 'minecraft:model', model: 'a:y' },
    },
  }
  assert.ok(resolveItemDefinition(dynamic, context).unsupported)
  assert.ok(resolveItemDefinition({ model: { type: 'mod:custom' } }, context).unsupported)
  assert.ok(resolveItemDefinition({}, context).unsupported)
})
