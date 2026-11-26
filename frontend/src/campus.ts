import type { Slot } from './api'
export const spaceTypes = { LIBRARY: '图书馆', STUDY: '自习室', DISCUSSION: '研讨间' }
export const quietLevels = { QUIET: '安静自习', SOFT: '可轻声交流', DISCUSSION: '可讨论', UNSPECIFIED: '安静程度待补充' }
export const serviceCategories = { NAILS: '美甲', PRINT: '打印', PHOTO: '摄影', CONSULT: '咨询', CLUB: '社团资源', OTHER: '其他服务' }
export interface Discovery {
  spaceType: keyof typeof spaceTypes; category: keyof typeof serviceCategories
  quietLevel: keyof typeof quietLevels; address: string; rules: string
}
export interface ServiceListing {
  slug: string; name: string; description: string; category: keyof typeof serviceCategories; address: string
  serviceCount: number; staffCount: number; rating: number | null; reviewCount: number
  cover: string; logo: string; minPriceCents: number | null; hasFree: boolean
}
export interface ServiceBooking extends Slot {
  id: string; slug: string; venueName: string; serviceName: string; employeeName: string
  status: 'CONFIRMED' | 'CANCELLED'; address: string; rating: number | null
}
export interface ServiceReview { rating: number; body: string; createdAt: string; serviceName: string }
