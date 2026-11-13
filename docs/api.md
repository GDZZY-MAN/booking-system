# API v1

浏览器和 API 共用一个站点。请求、响应使用 JSON，日期为 `YYYY-MM-DD`，时间戳使用 ISO 8601，展示时以北京时间为准。正常操作返回 200，退出登录返回 204。

## 会话与错误

- `GET /api/v1/auth/csrf` 返回 `{ token, headerName }`。浏览器保留会话 Cookie，所有写请求都在指定请求头发送令牌。注册、登录、接受邀请或退出后，下一次写请求前重新获取令牌。
- `POST /api/v1/auth/register` 接受 `{ username, password, shopName, slug }`，创建草稿并登录。登录、接受邀请和 `GET /api/v1/auth/me` 返回 `{ shopId, employeeId, username, role }`。
- `POST /api/v1/auth/login` 接受 `{ username, password }`。 `POST /api/v1/auth/accept` 接受 `{ token, username, password }`。 `POST /api/v1/auth/logout` 清除会话。
- 业务错误包含 `{ code, message, requestId }`。`400` 表示参数有误，`401` 表示需要登录或凭证错误，`403` 表示缺少权限或 CSRF 令牌，`404` 表示资源不可访问，`409` 表示冲突，`503` 表示服务暂不可用。排查时使用响应头 `X-Request-Id`。

## 店主

以下路径以 `/api/v1/owner` 开头。租户由会话决定。

| 方法 | 路径 | 请求与返回 |
| --- | --- | --- |
| GET | `/shop` | `{ id, slug, name, description, published }` |
| PUT | `/shop` | 写入 `{ name, description }`，返回店铺 |
| GET | `/shop/setup` | `{ hasActiveService, hasBookableEmployee }` |
| PUT | `/shop/publication` | 写入 `{ published }`，返回店铺 |
| GET / POST | `/services` | 服务列表 / 新增 `{ name, durationMinutes, active }` |
| PUT | `/services/{id}` | 更新服务，返回含 `id` 的记录 |
| GET / POST | `/employees` | 员工列表 / 新增 `{ name, active, serviceIds, shifts, closedDates }` |
| PUT | `/employees/{id}` | 替换该员工的配置，返回含 `id` 的记录 |
| POST | `/employees/{id}/invite` | 返回 `{ token, expiresAt }`，替换此前未使用的邀请 |
| GET | `/bookings?from=…&through=…` | 全店预约列表，日期范围含首尾，最多 31 天 |
| POST | `/bookings/{id}/cancel` | 取消预约，返回更新后的订单 |

员工排班记录为 `{ weekday, startMinute, endMinute }`，星期一是 1。时间为当天起算的分钟数，结束允许 1440。同一天可有多段排班，时段之间可以相邻。停约日期是全日，`closedDates` 为日期字符串数组。

## 员工

`GET /api/v1/employee/bookings?from=…&through=…` 只读取当前员工订单。店主与员工订单都含 `id, serviceId, employeeId, serviceName, employeeName, startsAt, endsAt, customerName, contact, status`。状态为 `CONFIRMED` 或 `CANCELLED`。

## 顾客

以下路径以 `/api/v1/public/{slug}` 开头。顾客无需开通账号。

| 方法 | 路径 | 请求与返回 |
| --- | --- | --- |
| GET | 根路径 | `{ name, description, services }` |
| GET | `/employees?serviceId=…` | 当前服务可选员工 `[{ id, name }]` |
| GET | `/slots?serviceId=…&employeeId=…&date=…` | 可约时段 `[{ startsAt, endsAt }]` |
| POST | `/bookings` | 接受 `{ serviceId, employeeId, startsAt, customerName, contact, requestKey }`，返回预约凭证 `{ id, serviceName, employeeName, startsAt, endsAt, status }` |

`requestKey` 是客户端生成的 UUID。同一店铺里用相同请求键和相同内容重试会得到原订单，改变内容须生成新的请求键。冲突时用 `SLOT_UNAVAILABLE` 提示重新查找时段。
