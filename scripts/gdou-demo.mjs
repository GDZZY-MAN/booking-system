// Run against a development instance. Credentials are supplied through environment variables.
const origin = process.argv[2] ?? 'http://127.0.0.1:8080'
const username = process.env.GDOU_DEMO_USERNAME
const password = process.env.GDOU_DEMO_PASSWORD
if (!username || !password) throw new Error('Set GDOU_DEMO_USERNAME and GDOU_DEMO_PASSWORD first.')
let cookie = ''
let csrf
async function request(path, method = 'GET', body) {
  if (method !== 'GET' && !csrf) csrf = await request('/auth/csrf')
  const headers = { Cookie: cookie, 'Content-Type': 'application/json' }
  if (method !== 'GET') headers[csrf.headerName] = csrf.token
  const response = await fetch(origin + '/api/v1' + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  const session = response.headers.getSetCookie().find(item => item.startsWith('JSESSIONID='))
  if (session) cookie = session.split(';')[0]
  const result = await response.json()
  if (!response.ok) throw new Error(result.message)
  if (path.startsWith('/auth/') && method === 'POST') csrf = undefined
  return result
}
// Platform administrators manage the existing demo venue; only missing seats are added.
await request('/auth/admin/login', 'POST', { username, password })
const venues = await request('/admin/venues')
const shop = venues.find(venue => venue.slug === 'gdou-huguang-demo') ?? await request('/admin/venues', 'POST', {
  name: '广东海洋大学湖光图书馆 · 选座示例', slug: 'gdou-huguang-demo',
})
const root = '/admin/venues/' + shop.id
const config = await request(root)
await request(root, 'PUT', { ...config, openMinute: 480, closeMinute: 1320, slotMinutes: 120 })
const seats = await request(root + '/seats')
for (const floor of [1, 2]) {
  for (let row = 1; row <= 4; row++) {
    for (let column = 1; column <= 6; column++) {
      const label = floor + 'F-' + String((row - 1) * 6 + column).padStart(2, '0')
      if (seats.some(seat => seat.label === label)) continue
      await request(root + '/seats', 'POST', {
        label, area: floor === 1 ? '一楼学习大厅 · 模拟区' : '二楼阅读空间 · 模拟区',
        floor, mapRow: row, mapColumn: column > 3 ? column + 1 : column,
        nearWindow: row === 1, powerOutlet: column === 1 || column === 6, active: true,
      })
    }
  }
}
await request(root + '/profile', 'PUT', { name: shop.name, description: '参照湖光校区公开资料制作的二维选座示例，含两层 48 个模拟座位，供本项目体验预约。' })
await request(root, 'PUT', { published: true, openMinute: 480, closeMinute: 1320, slotMinutes: 120 })
console.log('Demo ready: ' + origin + '/library/gdou-huguang-demo')
