import type { Slot } from './api'
import type { Discovery } from './campus'

export interface LibrarySettings { published: boolean; openMinute: number; closeMinute: number; slotMinutes: number }
export interface Venue extends LibrarySettings, Pick<Discovery, 'spaceType' | 'quietLevel' | 'address' | 'rules'> {
  shopId: number; slug: string; name: string; description: string; cover: string; seatCount: number; windowCount: number; powerCount: number; floors: string
}
export interface Seat {
  id: number; label: string; area: string; nearWindow: boolean; powerOutlet: boolean; active: boolean; available?: boolean
  floor?: number | null; mapRow?: number | null; mapColumn?: number | null
}
/** Positions in the existing 12-column floor map, including older seats without coordinates. */
export function placeFloorSeats(seats: Seat[]) {
  const occupied = new Set(seats.filter(s => s.mapRow && s.mapColumn)
    .map(s => (s.mapRow! - 1) * 12 + s.mapColumn! - 1))
  let next = 0
  return seats.map(seat => {
    if (seat.mapRow && seat.mapColumn) return { seat, row: seat.mapRow, column: seat.mapColumn }
    while (occupied.has(next)) next++
    const index = next++
    return { seat, row: Math.floor(index / 12) + 1, column: index % 12 + 1 }
  })
}

export interface SeatReservation extends Slot {
  id: string; slug: string; venueName: string; seatLabel: string; area: string; username: string
  status: 'RESERVED' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED'
}
export const reservationStatus = {
  RESERVED: '已预约', ACTIVE: '预约时段内', COMPLETED: '已结束', CANCELLED: '已取消',
}
export const minutesLabel = (minute: number) =>
  String(Math.floor(minute / 60)).padStart(2, '0') + ':' + String(minute % 60).padStart(2, '0')
