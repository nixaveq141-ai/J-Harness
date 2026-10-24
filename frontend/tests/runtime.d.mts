export function stoppedHostDiagnostic(message: string, origins: Set<string>): boolean
export function navigationReadDiagnostic(message: string, requests: { method: string; url: string }[]): boolean
export function trackNavigationReads(page: { on(event: string, listener: (...args: any[]) => void): unknown; url(): string; mainFrame(): unknown }): {
  during<T>(action: () => Promise<T>): Promise<T>
  diagnostic(message: string): boolean
  snapshot(): unknown
}
