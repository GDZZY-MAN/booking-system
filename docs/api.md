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
| GET / PUT | `/shop/branding` | 读取草稿 / 写入 `{ brand, publish }`。`publish: true` 同时更新公开版本 |
| GET / POST | `/services` | 服务列表 / 新增 `{ name, durationMinutes, active }` |
| PUT | `/services/{id}` | 更新服务，返回含 `id` 的记录 |
| GET / POST | `/employees` | 员工列表 / 新增 `{ name, active, serviceIds, shifts, closedDates }` |
| PUT | `/employees/{id}` | 替换该员工的配置，返回含 `id` 的记录 |
| POST | `/employees/{id}/invite` | 返回 `{ token, expiresAt }`，替换此前未使用的邀请 |
| GET | `/bookings?from=…&through=…` | 全店预约列表，日期范围含首尾，最多 31 天 |
| POST | `/bookings/{id}/cancel` | 取消预约，返回更新后的订单 |

员工排班记录为 `{ weekday, startMinute, endMinute }`，星期一是 1。时间为当天起算的分钟数，结束允许 1440。同一天可有多段排班，时段之间可以相邻。排班时间可精确到分钟。停约日期是全日，`closedDates` 为日期字符串数组，最多 366 天。服务 `durationMinutes` 支持 1–480 的任意整数。可选预约开始时间从各段排班起点起每隔 15 分钟生成，提交时按相同基准校验。

## 员工

`GET /api/v1/employee/bookings?from=…&through=…` 只读取当前员工订单。店主与员工订单都含 `id, serviceId, employeeId, serviceName, employeeName, startsAt, endsAt, customerName, contact, status`。状态为 `CONFIRMED` 或 `CANCELLED`。

## 顾客

以下路径以 `/api/v1/public/{slug}` 开头。GET 公开，POST 预约仅对已登录的 READER 开放。

| 方法 | 路径 | 请求与返回 |
| --- | --- | --- |
| GET | 根路径 | `{ name, description, services }` |
| GET | `/branding` | 已发布店铺的品牌信息。默认使用松林主题 |
| GET | `/employees?serviceId=…` | 当前服务可选员工 `[{ id, name }]` |
| GET | `/slots?serviceId=…&employeeId=…&date=…` | 可约时段 `[{ startsAt, endsAt }]` |
| POST | `/bookings` | 接受 `{ serviceId, employeeId, startsAt, customerName, contact, requestKey }`，返回预约凭证 `{ id, serviceName, employeeName, startsAt, endsAt, status }` |

`requestKey` 是客户端生成的 UUID。同一店铺里用相同请求键和相同内容重试会得到原订单，改变内容须生成新的请求键。冲突时用 `SLOT_UNAVAILABLE` 提示重新查找时段。

品牌信息为 `{ theme, address, businessNote, bookingNote, logo, cover }`。主题支持 `forest`、`plum`、`ocean`、`clay`。图片使用 JPG 或 PNG 的 Base64 Data URL，空字符串表示移除。店标上限 200 KiB，封面上限 400 KiB，长宽均不超过 2400 像素。公开品牌接口与预约接口独立。

校园空间回收站使用管理员接口。正常列表为 `GET /admin/venues`，回收站列表使用 `?archived=true`。使用 `DELETE /admin/venues/{id}` 移入回收站，存在未结束预约时返回 409（VENUE_HAS_RESERVATIONS），可通过 `GET /admin/venues/{id}/reservations/unfinished` 查询跨日期记录。`POST /admin/venues/{id}/restore` 恢复后需手动开放预约。删除与恢复成功均返回 204。

批量排座使用 `POST /admin/venues/{id}/seats/batch`，请求体是含楼层和坐标的座位数组（1–240 个）。成功返回该空间的完整座位列表；座号或位置冲突时返回 409（SEAT_LAYOUT_CONFLICT），整批取消。

## 座位预约

读者通过 `POST /api/v1/auth/reader/register` 和 `POST /api/v1/auth/reader/login` 注册、登录，请求为 `{ username, password }`。这是独立的读者账号入口。会话沿用平台的 Cookie 和 CSRF 机制，返回角色 `READER` 和固定 `readerId`。`auth/me` 对已有店主和员工也增加可空 `readerId` 字段。

平台管理员使用 `GET/PUT /api/v1/admin/venues/{id}` 配置 `{ published, openMinute, closeMinute, slotMinutes }`。通过该路径的 `/seats` 与 `/seats/{seat}` 管理座位。顾客入口仍为 `/library/{slug}`。旧 OWNER 场馆管理接口已移除。

公共接口包括 `GET /api/v1/public/libraries`、`GET .../libraries/{slug}`、`GET .../libraries/{slug}/slots?date=...` 和 `GET .../libraries/{slug}/seats?startsAt=...`。可查今天起七天内尚未开始的固定时段，座位列表通过 `available` 标识余位。

座位写入、管理列表和公共余位列表都支持可空 `floor`、`mapRow`、`mapColumn`。楼层为 1–20，行列成对填写，分别为 1–20 和 1–12，同楼层坐标唯一。旧调用可以省略这些字段，前端自动排列无坐标座位并展示「未标楼层」。

读者调用 `POST /api/v1/reader/libraries/{slug}/reservations` 提交 `{ seatId, startsAt, requestKey }`，相同请求键和内容返回原预约。 `GET /api/v1/reader/profile` 返回读者账号，`GET /api/v1/reader/reservations` 返回自己的最近 100 笔预约。 `POST .../reader/reservations/{id}/cancel` 可在开始前取消，`POST .../reader/reservations/{id}/finish` 可在预约时段内提前结束使用。

平台管理员调用 `GET /api/v1/admin/venues/{id}/reservations?date=...` 查看指定场馆预约，`POST .../reservations/{reservation}/cancel` 取消有效预约。无需签到。商家与员工均无场馆管理权限。

预约状态为 `RESERVED`（已预约）、`ACTIVE`（预约时段内）、`COMPLETED`、`CANCELLED`。一位读者在全部场所同时最多有一笔有效预约。到开始时间自动进入 ACTIVE，到结束时间自动完成，无需签到，也不设置迟到或爽约处罚。后台每分钟更新状态，相关预约请求也会先更新。数据库约束保证有效预约不会重叠，限制绑定固定读者 ID。后续可为该身份绑定唯一的已验证手机号。

## 校园三个入口

页面入口为 `/libraries`（找座位）、`/services`（约服务）、`/reader`（我的安排）。`GET /api/v1/public/campus/services` 返回已发布且存在上架服务的服务商目录，包含分类、地点、服务数、人员数及真实评价均分与数量。没有评价时均分为 `null`。

`GET/PUT /api/v1/owner/discovery` 和 `/api/v1/admin/venues/{id}/discovery` 分别管理商家、空间展示资料 `{ spaceType, category, quietLevel, address, rules }`。空间类型为 `LIBRARY/STUDY/DISCUSSION`；服务分类为 `NAILS/PRINT/PHOTO/CONSULT/CLUB/OTHER`；安静程度为 `QUIET/SOFT/DISCUSSION/UNSPECIFIED`。地点限 180 字，规则限 500 字。

服务订单从校园账号会话取得读者 ID。旧访客订单保留原归属。读者可通过 `GET /reader/service-bookings` 查询自己的预约，开始前用 `POST /reader/service-bookings/{id}/cancel` 取消，结束后用 `POST /reader/service-bookings/{id}/review` 提交一次评价（1–5 星，正文最多 500 字）。公开评价仅展示最近 30 条。

服务项目支持 `priceMode`（FIXED、FREE、INQUIRE）和 `priceCents`（固定价为正整数分）。商家与平台总览分别使用 `GET /owner/overview` 和 `GET /admin/overview`。管理员用 `GET /admin/merchants/{id}/setup` 查阅服务及排班，用 `GET/PUT /admin/venues/{id}/cover` 管理场馆封面（字段 cover）。

## 馆内提醒与聊天

接口前缀为 `/api/v1/reader/social`，要求读者会话和写请求 CSRF。 `GET/PUT /preferences` 读写 `{ nickname, chatEnabled }`。关闭邀请会将现有待处理邀请标为“对方暂不接收新邀请”，已接受的聊天仍可继续。

`GET /venues/{slug}/seats` 要求读者在本馆有尚未结束的有效预约，无需等待开始时间，返回平面图座位及有效预约读者的预约标识 `reservationId`、公开昵称 `nickname` 、聊天开关 `chatEnabled`、预约时间 `startsAt/endsAt` 和提醒开关 `remindersEnabled`，不返回账号或读者 ID。自身座位与已屏蔽对象不提供可联系身份；列表按 `available && chatEnabled` 筛选；同一座位不同预约会返回多条记录，使用 `reservationId` 标识读者对象，座位图按所选预约时间分组。 `POST /venues/{slug}/reminders` 接收 `{ seatId, reservationId, kind }`，类型为 `VOICE`、`HEADPHONES`、`ITEMS`。使用者变化时拒绝发送。安静提醒仍要求双方已进入各自预约时段；聊天邀请不受开始时间限制。相同目标预约 30 分钟内合并提醒，每位发送者每小时最多实际发送 10 条。`GET /reminders` 返回收到的最近 50 条提醒，隐藏发送者。`GET /sent-reminders` 返回自己发出的最近 50 条提醒及回应状态。`POST /reminders/{id}/acknowledge` 回应，`POST /reminders/{id}/block` 屏蔽发送者。

`POST /venues/{slug}/invitations` 接收 `{ seatId, reservationId }`，将两笔尚未结束的有效预约绑定到固定读者，支持预约开始前邀请、接受并聊天。每位发送者每小时最多发起 5 个邀请，同次预约被拒绝或会话结束后不可重复邀请。待处理邀请会一直保留，即使关联预约结束或取消后仍可接受，不会因长时间未操作而自动拒绝或过期。`GET /chats` 返回最近 100 个会话、未读计数及 `resolutionReason`；新增状态包括 `WITHDRAWN`、`DISABLED`、`BLOCKED`，历史没有原因的 `DECLINED` 会返回空原因。`POST /chats/{id}/accept|decline|withdraw|close|block` 接受、拒绝、撤回、结束或屏蔽：仅接收方能接受或拒绝，只有发起方能撤回待处理邀请。

`GET /chats/{id}/messages` 返回已接受或关闭会话的最近 100 条消息，按时间顺序显示。`POST /chats/{id}/messages` 接收 `{ body, requestKey }`，消息最多 500 字符，重复请求键与相同内容幂等，每人每分钟最多实际发送 20 条。`POST /chats/{id}/seen` 接收 `{ through }`，仅标记已获取到该消息 ID 的记录已读。微信号作为读者主动确认的普通文本消息分享。

消息只对会话双方开放。屏蔽会关闭两人之间所有会话，阻止继续联系。后台保留提醒和消息的发送账号。当前版本站内静音轮询每 15 秒更新，离线后下次登录可读。

## 平台管理与入驻审核

`POST /api/v1/auth/admin/login` 使用独立管理员账号，返回角色 ADMIN。所有 `/api/v1/admin/**` 接口仅 ADMIN 可访问，不提供管理员注册。管理员由部署环境 `PLATFORM_ADMIN_USERNAME/PASSWORD` 初始化，已有密码不自动覆盖。旧场馆 OWNER 账号不能作为管理员登录。

`GET /admin/merchants` 返回商家列表；`POST /admin/merchants/{id}/decision` 接收 `{ action, note }`，action 为 `approve/reject/suspend/resume`。驳回和下架须填写原因，审核操作保存审计记录。`GET /admin/merchants/{id}/bookings?from=...&through=...` 查询订单，范围最多 31 天。

商家仍用 `PUT /owner/shop/publication` 提交 `{ published: true }`：未通过审核时转为 PENDING，不公开展示；APPROVED 可以恢复自主暂停的营业；SUSPENDED 不能自行恢复。状态包括 DRAFT、PENDING、APPROVED、REJECTED、SUSPENDED。下架不删除原订单，所有公开查询和新预约同时校验审核与营业状态。

`GET/POST /admin/venues` 列出／新增校园空间，新增接收 `{ name, slug }`，无需为场馆创建登录账号。每个空间使用 `/admin/venues/{id}/profile`、`/discovery`、根设置、`/seats`、`/reservations` 管理；跨空间修改座位会被拒绝。商家和校园空间由 `resourceKind` 区分，旧数据迁移保留原 ID、链接和预约历史。

## 商家回收站补充

平台管理员可调用 `DELETE /api/v1/admin/merchants/{id}` 将商家移入回收站，成功返回 204。有尚未结束的已确认服务预约时返回 409 `MERCHANT_HAS_RESERVATIONS`。订单和账号保留，公开预约入口停用，店主及员工后台权限失效。

`POST /api/v1/admin/merchants/{id}/restore` 恢复为 DRAFT、未发布状态。恢复后需重新提交审核。商家列表中的 DELETED 状态表示回收站记录，正常列表默认隐藏。
