import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const load = async (name) =>
  JSON.parse(await readFile(new URL(`lang/${name}.json`, import.meta.url), 'utf8'))

test('RU and EN translation keys match', async () => {
  const [ru, en] = await Promise.all([load('ru'), load('en')])
  assert.deepEqual(Object.keys(en).sort(), Object.keys(ru).sort())
  assert.equal(en['status.IN_PROGRESS'], 'In progress')
  assert.equal(ru['status.IN_PROGRESS'], 'В работе')
})
