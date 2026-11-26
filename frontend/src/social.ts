import type { Seat } from './library'
export interface ContactSeat extends Seat {
  reservationId: string | null; nickname: string | null; chatEnabled: boolean
  startsAt: string | null; endsAt: string | null; remindersEnabled: boolean
}
export interface Preferences { nickname: string; chatEnabled: boolean }
export interface QuietNotice { id: string; kind: 'VOICE' | 'HEADPHONES' | 'ITEMS'; createdAt: string; acknowledged: boolean }
export interface SentNotice extends QuietNotice { seatLabel: string }
export type ChatStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'WITHDRAWN' | 'DISABLED' | 'BLOCKED' | 'CLOSED' | 'EXPIRED'
export interface Chat { id: string; status: ChatStatus; resolutionReason: string | null; incoming: boolean; nickname: string; unread: number }
export interface ChatMessage { id: number; body: string; mine: boolean; createdAt: string }
export const reminders = {
  VOICE: '附近有位读者希望保持安静，请适当降低交谈声音。感谢理解，祝学习顺利。',
  HEADPHONES: '附近有位读者希望保持安静，播放音频时请佩戴耳机。感谢理解，祝学习顺利。',
  ITEMS: '附近有位读者希望保持安静，请轻放物品。感谢理解，祝学习顺利。',
}
export function chatStatus(chat: Pick<Chat, 'status' | 'resolutionReason'>) {
  if (chat.status === 'DECLINED') return chat.resolutionReason === 'RECIPIENT_DECLINED' ? '邀请已拒绝' : '邀请已结束（未记录原因）'
  if (chat.status === 'WITHDRAWN') return '邀请已撤回'
  if (chat.status === 'DISABLED') return '对方暂不接收新邀请'
  if (chat.status === 'BLOCKED') return '会话已关闭'
  return { PENDING: '等待接受', ACCEPTED: '聊天中', CLOSED: '会话已结束', EXPIRED: '邀请已过期' }[chat.status]
}
