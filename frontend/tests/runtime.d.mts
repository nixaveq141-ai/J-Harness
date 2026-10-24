export function stoppedHostDiagnostic(message: string, origins: Set<string>): boolean
export function navigationReadDiagnostic(message: string, requests: { method: string; url: string }[]): boolean
