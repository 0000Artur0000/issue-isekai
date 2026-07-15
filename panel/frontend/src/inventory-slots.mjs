export const HOTBAR_SLOTS = Array.from({ length: 9 }, (_, index) => `hotbar_${index}`)
export const STORAGE_SLOTS = Array.from({ length: 27 }, (_, index) => `storage_${index + 9}`)
export const EQUIPMENT_SLOTS = ['helmet', 'chestplate', 'leggings', 'boots', 'offhand']

const KNOWN_SLOTS = new Set([...HOTBAR_SLOTS, ...STORAGE_SLOTS, ...EQUIPMENT_SLOTS])

export function isKnownSlot(slot) {
  return KNOWN_SLOTS.has(slot)
}
