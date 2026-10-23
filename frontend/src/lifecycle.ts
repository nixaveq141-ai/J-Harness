import { useEffect, useRef } from 'react'

/** A captured identity stays invalid after an effect is replaced or unmounted. */
export function useLifetime(...identity: unknown[]) {
  const state = useRef({ identity, alive: true })
  if (identity.some((v, i) => v !== state.current.identity[i])) {
    state.current.alive = false
    state.current = { identity, alive: true }
  }
  const current = state.current
  useEffect(() => { current.alive = true; return () => { current.alive = false } }, [current])
  return current
}
