import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import {
  EQUIPMENT_SLOTS,
  HOTBAR_SLOTS,
  STORAGE_SLOTS,
  isKnownSlot,
} from './inventory-slots.mjs'

test('inventory layout matches the shared Bukkit slot contract', async () => {
  const fixture = JSON.parse(
    await readFile(new URL('../../../contracts/inventory-slots.json', import.meta.url), 'utf8'),
  )

  assert.deepEqual(HOTBAR_SLOTS, fixture.storage_contents.slice(0, 9))
  assert.deepEqual(STORAGE_SLOTS, fixture.storage_contents.slice(9))
  assert.deepEqual(EQUIPMENT_SLOTS, [
    ...fixture.armor_contents.toReversed(),
    ...fixture.extra_contents,
  ])
  assert.ok([...HOTBAR_SLOTS, ...STORAGE_SLOTS, ...EQUIPMENT_SLOTS].every(isKnownSlot))
  assert.equal(isKnownSlot('storage_0'), false)
  assert.equal(isKnownSlot('armor_head'), false)
  assert.equal(isKnownSlot('off_hand'), false)
})
