const NAMED_COLORS = new Set([
  'black',
  'dark_blue',
  'dark_green',
  'dark_aqua',
  'dark_red',
  'dark_purple',
  'gold',
  'gray',
  'dark_gray',
  'blue',
  'green',
  'aqua',
  'red',
  'light_purple',
  'yellow',
  'white',
])

export function componentClasses(component) {
  const classes = []
  if (NAMED_COLORS.has(component?.color)) classes.push(`mc-color-${component.color}`)
  if (component?.bold) classes.push('mc-bold')
  if (component?.italic) classes.push('mc-italic')
  if (component?.underlined) classes.push('mc-underlined')
  if (component?.strikethrough) classes.push('mc-strikethrough')
  return classes.join(' ')
}
