export const sanitizeRememberedLogin = (value) => {
  const name = typeof value?.name === 'string' ? value.name.trim() : ''
  return name ? {name} : {}
}
