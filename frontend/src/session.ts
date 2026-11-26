import { shallowRef } from 'vue'
import { api, ApiError, type Viewer } from './api'

export const session = shallowRef<Viewer | null>(null)
let version = 0

export function setSession(viewer: Viewer | null) {
  version++
  session.value = viewer
}

export async function refreshSession() {
  const request = ++version
  try {
    const viewer = await api<Viewer | null>('/auth/me')
    if (request === version) session.value = viewer
  } catch (issue) {
    if (request === version && issue instanceof ApiError && issue.status === 401) session.value = null
  }
}

export async function signOut() {
  await api('/auth/logout', 'POST')
  setSession(null)
}
