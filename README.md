# 有约 · 商家自助预约平台

为美甲店、摄影工作室等小店开通预约页。店主配置服务、员工与排班并发布链接，顾客在线预约，员工登录查看自己的订单。

## 本地运行

安装 Docker 和 Docker Compose 2.24.4 或更高版本，把 `.env.example` 复制为 `.env`，将 `POSTGRES_PASSWORD` 换为随机强密码。项目名 `youyue` 已固定，支持中文目录。

```sh
docker compose up -d --build
docker compose ps
```

打开 `http://localhost:8080`，健康检查是 `http://localhost:8080/health`。如端口被占用，修改 `.env` 的 `WEB_PORT`。首次启动由 Flyway 创建表。

分开开发时，用 Java 21、Maven 3.9、Node 22 和 PostgreSQL 17。先准备一个本地数据库，账号需要在该库创建 `btree_gist` 扩展的权限。

Windows 上 Docker 未启动时，也可以通过 PostgreSQL 安装程序安装数据库。在 SQL Shell 以数据库管理员身份运行下面两条语句，再用同一个密码启动 Java。

```sql
CREATE ROLE shopbooking LOGIN PASSWORD '<你的开发库密码>';
CREATE DATABASE shopbooking OWNER shopbooking;
```

```powershell
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/shopbooking'
$env:DB_USER = 'shopbooking'
$env:DB_PASSWORD = '<你的开发库密码>'
mvn.cmd -f backend/pom.xml spring-boot:run
```

另开终端进入 `frontend` 后运行 `npm ci`、`npm run dev`，访问 Vite 打印的地址。若调整了 Java 的 `PORT`，在启动 Vite 前设置 `BOOKING_API_ORIGIN`，例如 `http://127.0.0.1:18080`。前端构建运行 `npm run build`，后端打包运行 `mvn -f backend/pom.xml package`。

## 一次完整演示

1. 注册一家虚构店铺，例如「青禾工作室」，设置唯一链接标识。
2. 添加 45 分钟的「手部护理」和 30 分钟的「修型」。添加两位员工，给两人分配服务和第二天的排班。
3. 在「店铺与发布」发布链接。用两个独立浏览器会话打开链接，选择同一员工，选取有重叠的时段后同时提交。只有一笔确认。改选另一位员工可在同一时间接单。
4. 店主为第一位员工生成邀请，在独立会话中打开并注册。员工只看到分配给自己的订单。店主取消一笔预约后，顾客可重新查询并预约仍符合排班的时段。

回归测试可在专用测试数据库所连接的站点运行。测试会创建带随机标识的两家虚构店铺及测试订单。

```sh
node scripts/smoke.mjs http://127.0.0.1:8080
```

## 模块和预约调用路径

- `account` 管理会话、账号与员工邀请。 `shop` 管理店铺和服务。 `schedule` 管理员工、服务分配与排班。 `booking` 管理空档、预约与取消。模块通过业务服务协作，顾客端、商家后台和员工后台使用独立页面。接口见 [API 契约](docs/api.md)。
- 顾客提交时，`Reserve.vue` 发送服务、员工、开始时间及请求键。 `PublicController` 将请求交给 `Bookings`。事务内共享店铺配置锁，防止校验到写入之间排班被修改。 `booking_no_overlap` 约束用时间区间拦住重叠订单，冲突由 `ApiErrors` 转为 409。订单保存服务和员工名称快照，后续修改排班或服务仍可查看历史记录。
- 增加提醒时，可让提醒模块独立读取已提交订单或自行维护发送任务，失败只在提醒模块中重试。新增数据库变更使用下一条 Flyway 迁移，完成后重跑预约测试。

答辩练习是将 `Bookings` 中的 `BOOKING_DAYS` 由 30 改为 14，同时调整顾客日期控件上限。用今天起第 14 天和第 15 天验证前后端边界是否一致。

## 上线、备份与恢复

### Linux 云服务器与 HTTPS

1. 将源码传到服务器，安装 Docker Engine 和 Compose。准备固定公网 IP 或可用域名，允许 Web 端口 80、443。保持数据库和 Java 端口仅在容器网络内。使用中国内地节点前，按服务商说明完成适用的备案流程。
2. 将 `.env` 的 `PUBLIC_HOST` 设为证书对应的 IP 或域名。无域名时，用 Certbot 5.4 或更高版本申请 IP 证书。先在宿主机腾出 80 端口，创建续期使用的 webroot，再申请证书。使用域名时将 `--ip-address <PUBLIC_HOST>` 换为 `-d <PUBLIC_HOST>`。

   ```sh
   sudo mkdir -p /var/www/youyue-acme
   sudo certbot certonly --standalone --preferred-profile shortlived --ip-address <PUBLIC_HOST>
   docker compose -f compose.yml -f compose.https.yml up -d --build
   ```

3. 配置 Certbot 使用 webroot 续期并重载 Nginx。把路径替换成服务器上的绝对路径。检查 Certbot 安装提供的定时任务已启用。

   ```sh
   sudo certbot reconfigure --cert-name <PUBLIC_HOST> --webroot -w /var/www/youyue-acme \
     --deploy-hook 'docker compose --project-directory /opt/youyue -f /opt/youyue/compose.yml -f /opt/youyue/compose.https.yml exec -T web nginx -s reload'
   sudo certbot renew --dry-run --run-deploy-hooks
   ```

4. 用手机流量检查 `https://<PUBLIC_HOST>/health`、登录、店铺链接和顾客预约。公网演示用虚构联系方式。把网站地址和可访问的 GitHub 仓库地址提交给熊文睿。

若用本机加穿透，使用基础 Compose 并在 `.env` 设置 `COOKIE_SECURE=true`。让穿透服务将稳定 HTTPS 地址指向回环地址的 Web 端口，保持电脑在线，验收后再提供地址。Vite 开发服务用于本地开发。

### 备份与恢复

```sh
sh scripts/backup.sh
sh scripts/verify-backup.sh backups/<文件名>.dump
```

在服务器计划任务中每天执行备份脚本，并通过受控渠道复制到独立存储。脚本将备份恢复到同一数据库服务下一个独立临时数据库，核对订单表和冲突约束，再清理临时库。核对实际订单数量及近期订单后再恢复生产数据，恢复到新库并验证后切换连接。

检查健康端点和 `docker compose logs backend` 中的请求编号，定位数据库断开或请求失败。升级前备份并保留旧镜像。部署后运行核心预约流程，重启容器后再次验证旧订单和链接。
