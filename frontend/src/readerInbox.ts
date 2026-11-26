import { computed, shallowRef } from 'vue'
import { api } from './api'
import { session } from './session'
import type { Chat, QuietNotice } from './social'

export const inboxChats = shallowRef<Chat[]>([])
export const inboxReminders = shallowRef<QuietNotice[]>([])
export const unreadMessages = computed(() => inboxReminders.value.filter(item => !item.acknowledged).length
  + inboxChats.value.reduce((total, chat) => total + chat.unread + (chat.incoming && chat.status === 'PENDING' ? 1 : 0), 0))

export function syncInbox(reminders: QuietNotice[], chats: Chat[]) {
  inboxReminders.value = reminders
  inboxChats.value = chats
}

export async function refreshInbox() {
  const readerId = session.value?.readerId
  if (session.value?.role !== 'READER') return
  try {
    const [reminders, chats] = await Promise.all([
      api<QuietNotice[]>('/reader/social/reminders'), api<Chat[]>('/reader/social/chats'),
    ])
    if (session.value?.role === 'READER' && session.value.readerId === readerId) syncInbox(reminders, chats)
  } catch { /* Retain messages during a temporary connection interruption. */ }
}
