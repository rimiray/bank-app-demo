/** Sentence-case label for API status enums (ACTIVE → Active). */
export function formatStatus(status: string): string {
  const lower = status.trim().toLowerCase()
  if (!lower) return status
  return lower.charAt(0).toUpperCase() + lower.slice(1)
}
