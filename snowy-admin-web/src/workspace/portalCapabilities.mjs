const rolesByCapability = Object.freeze({
  sourcing: ['ADMIN', 'QUOTE'],
  collaboration: ['ADMIN', 'SHIP', 'FINANCE'],
  shipping: ['ADMIN', 'SHIP'],
  settlements: ['ADMIN', 'FINANCE'],
  quality: ['ADMIN', 'QUALITY'],
  performance: ['ADMIN', 'QUOTE', 'QUALITY', 'FINANCE']
})

export function portalCapabilities(roles = []) {
  const assigned = new Set(Array.isArray(roles) ? roles : [])
  return Object.fromEntries(Object.entries(rolesByCapability).map(([capability, allowed]) => [
    capability,
    allowed.some(role => assigned.has(role))
  ]))
}
