import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'

const origin = process.argv[2] ?? 'http://127.0.0.1:8080'
const run = randomUUID().slice(0, 8)
const password = 'Smoke-test-pass42'
const date = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(Date.now() + 86_400_000)
const weekday = new Date(date + 'T00:00:00Z').getUTCDay() || 7

function client() {
  let cookie = ''
  let csrf
  return async function send(path, { method = 'GET', body, status = 200, withoutCsrf = false } = {}) {
    if (method !== 'GET' && !csrf && !withoutCsrf) {
      const response = await send('/auth/csrf')
      csrf = response.body
    }
    const headers = {}
    if (cookie) headers.Cookie = cookie
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (method !== 'GET' && !withoutCsrf) headers[csrf.headerName] = csrf.token
    const response = await fetch(origin + '/api/v1' + path, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(12000),
    })
    const setCookie = response.headers.getSetCookie().find(value => value.startsWith('JSESSIONID='))
    if (setCookie) cookie = setCookie.split(';')[0]
    const raw = await response.text()
    const result = raw ? JSON.parse(raw) : undefined
    if (status !== null) assert.equal(response.status, status, method + ' ' + path + ' ' + raw)
    assert.ok(response.headers.get('x-request-id'), 'Missing request ID on ' + path)
    if (method === 'POST' && path.startsWith('/auth/') && response.ok) csrf = undefined
    return { status: response.status, body: result }
  }
}

const owner = client()
const guest = client()
const guest2 = client()
const employee = client()
const employee2 = client()
const otherOwner = client()
const store = '/public/smoke-' + run
const orderUrl = '/bookings?from=' + date + '&through=' + date
const post = (body, status = 200) => ({ method: 'POST', body, status })
const put = (body, status = 200) => ({ method: 'PUT', body, status })
const register = suffix => ({
  username: 'owner-' + run + suffix, password, shopName: '烟雾测试工作室', slug: 'smoke-' + run + suffix,
})

await guest('/owner/shop', { status: 401 })
await owner('/auth/register', { ...post(register(''), 403), withoutCsrf: true })
await owner('/auth/register', post(register('')))
await otherOwner('/auth/register', post(register('-other')))
assert.equal((await owner('/auth/me')).body.role, 'OWNER')
await guest(store, { status: 404 })
await guest(store + '/branding', { status: 404 })
assert.deepEqual((await owner('/owner/shop/setup')).body, { hasActiveService: false, hasBookableEmployee: false })
await owner('/owner/shop/publication', put({ published: true }, 409))
await owner('/owner/shop', put({ name: '青禾验证工作室', description: '测试店铺' }))
const long = (await owner('/owner/services', post({ name: '手部护理', durationMinutes: 45, active: true }))).body
const short = (await owner('/owner/services', post({ name: '修型', durationMinutes: 30, active: true }))).body
const schedule = {
  name: '小岚', active: true, serviceIds: [long.id, short.id],
  shifts: [{ weekday, startMinute: 540, endMinute: 1080 }], closedDates: [],
}
const first = (await owner('/owner/employees', post(schedule))).body
const second = (await owner('/owner/employees', post({ ...schedule, name: '小秋' }))).body
assert.deepEqual((await owner('/owner/shop/setup')).body, { hasActiveService: true, hasBookableEmployee: true })
await otherOwner('/owner/employees', post(schedule, 400))
await otherOwner('/owner/services/' + long.id, put({ ...long, name: '越权修改' }, 404))
await otherOwner('/owner/employees/' + first.id + '/invite', post(undefined, 404))
await owner('/owner/shop/publication', put({ published: true }))
const brandPath = '/owner/shop/branding'
const logo = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='
const brand = { theme: 'plum', address: '青禾路 8 号', businessNote: '周末营业', bookingNote: '请提前到店', logo, cover: logo }
assert.equal((await guest(store + '/branding')).body.theme, 'forest')
await owner(brandPath, put({ brand, publish: false }))
assert.deepEqual((await owner(brandPath)).body, brand)
assert.equal((await guest(store + '/branding')).body.theme, 'forest')
assert.equal((await otherOwner(brandPath)).body.theme, 'forest')
await owner(brandPath, put({ brand: { ...brand, theme: 'invalid' }, publish: true }, 400))
await owner(brandPath, put({ brand: { ...brand, logo: 'data:image/jpeg;base64,' + logo.split(',')[1] }, publish: true }, 400))
await owner(brandPath, put({ brand, publish: true }))
assert.deepEqual((await guest(store + '/branding')).body, brand)
await owner(brandPath, put({ brand: { ...brand, logo: '', theme: 'ocean' }, publish: false }))
assert.deepEqual((await guest(store + '/branding')).body, brand)
assert.equal((await guest(store)).body.name, '青禾验证工作室')
assert.equal((await guest(store + '/employees?serviceId=' + long.id)).body.length, 2)

const invitePath = id => '/owner/employees/' + id + '/invite'
const firstOld = (await owner(invitePath(first.id), post())).body
const invite = (await owner(invitePath(first.id), post())).body
const credentials = { username: 'staff-' + run, password }
await employee('/auth/accept', post({ ...credentials, token: firstOld.token }, 404))
await employee('/auth/accept', post({ ...credentials, token: invite.token }))
await guest('/auth/accept', post({ username: 'reuse-' + run, password, token: invite.token }, 404))
await employee('/owner/shop', { status: 403 })
await employee(brandPath, { status: 403 })
const secondInvite = (await owner(invitePath(second.id), post())).body
await employee2('/auth/accept', post({ username: 'second-' + run, password, token: secondInvite.token }))

const slots = (service, person) => store + '/slots?' + new URLSearchParams({ serviceId: service.id, employeeId: person.id, date })
const start = minute => date + 'T' + minute + ':00+08:00'
const input = (service, person, minute) => ({
  serviceId: service.id, employeeId: person.id, startsAt: start(minute),
  customerName: '测试顾客', contact: 'test-contact', requestKey: randomUUID(),
})
assert.equal((await guest(slots(long, first))).body[0].startsAt, start('09:00'))
await guest(slots(long, first).replace(date, '2099-01-01'), { status: 400 })
await Promise.all([guest('/auth/csrf'), guest2('/auth/csrf')])
const collision = [input(long, first, '09:00'), input(short, first, '09:15')]
const competing = await Promise.all(collision.map((value, index) => [guest, guest2][index](store + '/bookings', post(value, null))))
assert.deepEqual(competing.map(result => result.status).sort(), [200, 409])
const win = competing.findIndex(result => result.status === 200)
assert.equal(competing[1 - win].body.code, 'SLOT_UNAVAILABLE')
const order = competing[win].body
assert.equal((await guest(store + '/bookings', post(collision[win]))).body.id, order.id)
await guest(store + '/bookings', post({ ...collision[win], contact: 'other' }, 409))
const adjacent = await guest(store + '/bookings', post(input(short, first, '09:45')))
const parallel = await guest(store + '/bookings', post(input(short, second, '09:15')))
const retry = input(short, second, '13:00')
const [repeatA, repeatB] = await Promise.all([guest, guest2].map(send => send(store + '/bookings', post(retry))))
assert.equal(repeatA.body.id, repeatB.body.id)
assert.notEqual(adjacent.body.id, parallel.body.id)

const employeeOrders = (await employee('/employee' + orderUrl)).body
assert.deepEqual(employeeOrders.map(row => row.id), [order.id, adjacent.body.id])
assert.equal((await employee2('/employee' + orderUrl)).body.length, 2)
assert.equal((await owner('/owner' + orderUrl)).body.length, 4)
assert.deepEqual((await otherOwner('/owner' + orderUrl)).body, [])
await otherOwner('/owner/bookings/' + order.id + '/cancel', post(undefined, 404))
await employee('/owner/bookings/' + order.id + '/cancel', post(undefined, 403))

await owner('/owner/bookings/' + order.id + '/cancel', post())
assert.equal((await guest(store + '/bookings', post(collision[win]))).body.status, 'CANCELLED')
assert.equal((await guest(slots(long, first))).body[0].startsAt, start('09:00'))
const rebook = (await guest(store + '/bookings', post(input(long, first, '09:00')))).body
await owner('/owner/employees/' + first.id, put({ ...schedule, closedDates: [date] }))
assert.deepEqual((await guest(slots(long, first))).body, [])
await guest(store + '/bookings', post(input(long, first, '14:00'), 409))
assert.ok((await employee('/employee' + orderUrl)).body.some(row => row.id === rebook.id && row.status === 'CONFIRMED'))
await owner('/owner/shop/publication', put({ published: false }))
await guest(store, { status: 404 })
await guest(store + '/branding', { status: 404 })
assert.equal((await guest(store + '/bookings', post(retry))).body.id, repeatA.body.id)
await owner('/auth/logout', post(undefined, 204))
await owner('/auth/me', { status: 401 })
await owner('/auth/login', post({ username: register('').username, password }))
assert.equal((await owner('/owner' + orderUrl)).body.length, 5)
console.log('Passed: publication, branding drafts and publication, CSRF, invitation, role and shop isolation, concurrent overlap, retries, cancellation, closure and login.')
console.log('Synthetic test shop:', register('').slug, '| Persisted appointment:', rebook.id)
