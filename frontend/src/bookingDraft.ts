const key = 'campus.booking-draft'
interface Draft {
  path: string
  date: string
  startsAt: string
  seatId?: number
  serviceId?: number
  employeeId?: number
}

export function saveDraft(draft: Draft) {
  try { sessionStorage.setItem(key, JSON.stringify(draft)) } catch { /* Storage may be disabled. */ }
}

function readDraft(): Draft | undefined {
  try { return JSON.parse(sessionStorage.getItem(key) || 'null') ?? undefined } catch { return undefined }
}

export function clearDraft() {
  try { sessionStorage.removeItem(key) } catch { /* Nothing to restore. */ }
}

export function takeDraft(path: string) {
  const draft = readDraft()
  clearDraft()
  return draft?.path === path ? draft : undefined
}

export function discardUnrelatedDraft(path: string, next: unknown) {
  const draft = readDraft()
  if (!draft || path === draft.path) return
  if (['/reader/login', '/reader/register'].includes(path) && next === draft.path) return
  clearDraft()
}
