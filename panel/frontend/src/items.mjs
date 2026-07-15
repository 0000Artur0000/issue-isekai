// Resolver item definitions (assets/<ns>/items/*.json) в список model keys.
// Чистый модуль без React/fetch — проверяется node:test.
// context: { damage, maxDamage, amount, customModelData: {floats, flags, strings, colors} }
// Возвращает { models: string[] } или { unsupported: 'причина' }.

export function resolveItemDefinition(definition, context) {
  if (!definition || typeof definition !== 'object' || !definition.model) {
    return { unsupported: 'нет модели в определении' }
  }
  return walk(definition.model, context)
}

function walk(node, context) {
  const type = String(node?.type ?? '').replace(/^minecraft:/, '')
  switch (type) {
    case 'model':
      return typeof node.model === 'string'
        ? { models: [node.model] }
        : { unsupported: 'нет model key' }
    case 'composite': {
      const models = []
      for (const child of node.models ?? []) {
        const resolved = walk(child, context)
        if (resolved.models) {
          models.push(...resolved.models)
        }
      }
      return models.length > 0 ? { models } : { unsupported: 'composite без поддержанных моделей' }
    }
    case 'condition': {
      const value = conditionValue(node, context)
      if (value === null) {
        return { unsupported: `условие ${node.property}` }
      }
      return walk(value ? node.on_true : node.on_false, context)
    }
    case 'select': {
      const value = selectValue(node, context)
      if (value === null) {
        return node.fallback
          ? walk(node.fallback, context)
          : { unsupported: `селектор ${node.property}` }
      }
      for (const entry of node.cases ?? []) {
        const when = Array.isArray(entry.when) ? entry.when : [entry.when]
        if (when.includes(value)) {
          return walk(entry.model, context)
        }
      }
      return node.fallback
        ? walk(node.fallback, context)
        : { unsupported: `нет case для ${value}` }
    }
    case 'range_dispatch': {
      const value = rangeValue(node, context)
      if (value === null) {
        return { unsupported: `диапазон ${node.property}` }
      }
      const scaled = value * (node.scale ?? 1)
      let chosen = null
      for (const entry of node.entries ?? []) {
        if (entry.threshold <= scaled && (chosen === null || entry.threshold >= chosen.threshold)) {
          chosen = entry
        }
      }
      if (chosen) {
        return walk(chosen.model, context)
      }
      return node.fallback
        ? walk(node.fallback, context)
        : { unsupported: 'нет entry под значение' }
    }
    case 'empty':
      return { models: [] }
    case 'special':
      return { unsupported: 'special-модель (клиентский рендер)' }
    default:
      return { unsupported: `тип ${node?.type ?? 'неизвестен'}` }
  }
}

function conditionValue(node, context) {
  switch (String(node.property ?? '').replace(/^minecraft:/, '')) {
    case 'damaged':
      return (context.damage ?? 0) > 0
    case 'custom_model_data':
      return context.customModelData?.flags?.[node.index ?? 0] ?? false
    default:
      // ponytail: динамические свойства (using_item, cast, …) требуют клиента
      return null
  }
}

function selectValue(node, context) {
  switch (String(node.property ?? '').replace(/^minecraft:/, '')) {
    case 'custom_model_data':
      return context.customModelData?.strings?.[node.index ?? 0] ?? null
    case 'display_context':
      return 'gui'
    default:
      return null
  }
}

function rangeValue(node, context) {
  switch (String(node.property ?? '').replace(/^minecraft:/, '')) {
    case 'damage':
      return node.normalize === false
        ? (context.damage ?? 0)
        : (context.damage ?? 0) / Math.max(1, context.maxDamage ?? 0)
    case 'count':
      // ponytail: max stack неизвестен из snapshot, считаем 64
      return node.normalize === false ? (context.amount ?? 1) : (context.amount ?? 1) / 64
    case 'custom_model_data':
      return context.customModelData?.floats?.[node.index ?? 0] ?? null
    default:
      return null
  }
}
