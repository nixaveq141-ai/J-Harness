/** IDs only. Keep uncertainty across component remounts when browser storage fails. */
export function createPendingMarker(storage: () => Pick<Storage,'getItem'|'setItem'|'removeItem'>, capacity = 1024) {
  if (!Number.isSafeInteger(capacity) || capacity < 1) throw Error('Invalid marker capacity')
  const memory = new Map<string,string>()
  return (key: string, value?: string): string => {
    if (key.length > 512 || value !== undefined && value.length > 256) return ''
    if (value !== undefined) {
      // Never evict an uncertain request to admit another write.
      if (!memory.has(key) && memory.size >= capacity) return ''
      memory.set(key,value)
      try {
        const target=storage()
        if(value)target.setItem(key,value);else target.removeItem(key)
        if(!value && !target.getItem(key))memory.delete(key)
      } catch { /* The in-memory value, including a failed-clear tombstone, stays authoritative. */ }
      return value
    }
    if(memory.has(key))return memory.get(key)!
    try {
      const saved=storage().getItem(key)??''
      if(saved.length>256)return 'unverified-marker'
      if(saved && memory.size<capacity)memory.set(key,saved)
      return saved
    } catch { return memory.size>=capacity?'marker-capacity-exhausted':'' }
  }
}

export const pendingMarker = createPendingMarker(() => sessionStorage)

export function isPendingMarkerPersisted(key: string, value: string) {
  try { return sessionStorage.getItem(key) === value } catch { return false }
}
