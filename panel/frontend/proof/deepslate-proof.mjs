import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const moduleSpecifier = process.argv[2]
  ? pathToFileURL(resolve(process.argv[2])).href
  : 'deepslate'
const { BlockModel, Identifier, ItemModel, ItemRenderer, ItemStack, NbtString } = await import(
  moduleSpecifier
)
const fixtures = JSON.parse(
  await readFile(new URL('./deepslate-fixtures.json', import.meta.url), 'utf8'),
)

for (const fixture of fixtures) {
  const requestedTextures = new Set()
  const blockModels = new Map(
    Object.entries(fixture.models).map(([id, model]) => [id, BlockModel.fromJson(model)]),
  )
  const resources = {
    getBlockModel: (id) => blockModels.get(id.toString()) ?? null,
    getItemModel: (id) =>
      id.toString() === fixture.item_model ? ItemModel.fromJson(fixture.definition.model) : null,
    getItemComponents: () => new Map(),
    getTextureAtlas: () => {
      throw new Error('Mesh proof must not create a WebGL texture atlas')
    },
    getTextureUV: (id) => {
      requestedTextures.add(id.toString())
      return [0, 0, 1, 1]
    },
  }

  for (const model of blockModels.values()) model.flatten(resources)
  const item = new ItemStack(
    Identifier.parse(fixture.item),
    1,
    new Map([['minecraft:item_model', new NbtString(fixture.item_model)]]),
  )
  const mesh = ItemRenderer.getItemMesh(item, resources, { display_context: 'gui' })

  assert.equal(mesh.quads.length, fixture.expected_quads, fixture.name)
  assert.ok(requestedTextures.has(fixture.expected_texture), fixture.name)
  assert.ok(
    mesh.quads.flatMap((quad) => quad.vertices()).every((vertex) =>
      vertex.texture?.every(Number.isFinite),
    ),
    `${fixture.name}: invalid UV`,
  )
  if (fixture.expected_gui_transform) {
    assert.ok(
      mesh.quads
        .flatMap((quad) => quad.vertices())
        .some((vertex) => !Number.isInteger(vertex.pos.x) || !Number.isInteger(vertex.pos.y)),
      `${fixture.name}: gui transform was not inherited from the child model`,
    )
  }
  console.log(`PASS ${fixture.name}: ${mesh.quads.length} quads`)
}
