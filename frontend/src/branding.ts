export const themes = [
  { id: 'forest', name: '松林' },
  { id: 'plum', name: '莓紫' },
  { id: 'ocean', name: '海蓝' },
  { id: 'clay', name: '陶红' },
] as const

export interface ShopBrand {
  theme: typeof themes[number]['id']
  address: string
  businessNote: string
  bookingNote: string
  logo: string
  cover: string
}

export const defaultBrand: ShopBrand = {
  theme: 'forest', address: '', businessNote: '', bookingNote: '', logo: '', cover: '',
}
