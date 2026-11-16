# 动享：城市运动场馆与体验券平台

基于 Java 17 / Spring Boot 3 的后端示例，包含场馆查询、体验分享、点赞评论、验证码登录和限量体验券异步下单。使用 MyBatis-Plus、MySQL、Redis、Redisson、Lua 与 RocketMQ。

## 本地运行

需要 Java 17、Maven、Docker Compose。下面的配置仅用于本地开发，示例数据和密码不可用于生产。

```bash
docker compose up -d
# 等待 MySQL 和 RocketMQ 就绪，再初始化示例活动的 Redis 库存（仅在全新环境执行）
docker compose exec redis redis-cli SET 'seckill:{orders}:stock:1' 100 NX
EXPOSE_DEV_CODE=true PAYMENT_CALLBACK_SECRET=local-secret mvn spring-boot:run
```

MySQL 初始化脚本只在数据库卷首次创建时运行。生产中必须先制定活动发布与库存初始化流程：Redis 库存、买家集合和订单消息流必须持久化，不能在有未处理订单或库存回补任务时直接重置。不要使用 `SET NX` 作为 Redis 丢数据后的恢复方案；须停单并核对数据库、消息和未完成预扣后人工重建。示例 Redis 启用了 AOF，但系统不能保证断电时绝不丢失预扣；生产还需配置持久化和高可用，并建立对账及告警。

验证码默认不返回，也尚未接入短信供应商；本地设置 `EXPOSE_DEV_CODE=true` 才能取得验证码。生产必须接入短信服务并限制接口调用频率。支付回调示例使用共享密钥；真实支付应对接支付服务商并验证签名、金额、商户及交易号。可用环境变量配置 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`ROCKETMQ_NAME_SERVER`、`PAYMENT_CALLBACK_SECRET`。

## API 示例

所有响应通常为 `{ "success": true, "message": "ok", "data": ... }`。认证接口使用 `Authorization: Bearer <token>`。

```bash
# 发送验证码，开发环境下响应包含 code
curl -X POST localhost:8080/api/auth/code -H 'Content-Type: application/json' -d '{"phone":"13800138000"}'
# 使用返回的 code 换取 token
curl -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"phone":"13800138000","code":"123456"}'
curl 'localhost:8080/api/venues?city=上海'
curl localhost:8080/api/venues/1
curl localhost:8080/api/vouchers
curl -X POST localhost:8080/api/vouchers/1/orders -H 'Authorization: Bearer <token>'
curl localhost:8080/api/orders/<orderId> -H 'Authorization: Bearer <token>'
curl -X POST localhost:8080/api/orders/<orderId>/payment-callback -H 'X-Callback-Secret: local-secret'
```

其他接口：`GET /api/posts`、`POST /api/posts`（`venueId`、`title`、`content`）、`POST/DELETE /api/posts/{id}/likes`、`GET/POST /api/posts/{id}/comments`（正文为 `content`）、`POST /api/auth/logout`。下单返回 `ACCEPTED` 表示预扣成功，订单异步落库；客户端应轮询订单详情。

## 一致性设计与边界

- 验证码和 Token 存 Redis，验证码一次性消费，拦截器续期并清理 ThreadLocal。
- 场馆详情使用空值缓存、随机 TTL 和逻辑过期后台重建；布隆过滤器记录已查询到的场馆，但由于数据库可能被外部修改，布隆过滤器的否定结果不会直接拒绝请求，避免误判有效场馆。
- Lua 原子完成预扣、买家去重与写入 Redis Stream。转发器持有 Redisson 锁，先重试待确认记录，RocketMQ 同步发送成功后才 ACK；消费者在数据库事务中先插入唯一订单再条件扣库存。重复投递依靠订单主键和唯一约束幂等。消息发送与确认之间的崩溃可能造成重复投递，但不会重复扣减数据库库存。
- 超时关单与支付只允许从 `PENDING` 条件更新。关单事务同时回补数据库库存、写入回补任务；后台 Lua 脚本按订单 ID 幂等回补 Redis。每人每券终身限一单，关单后不可重新购买同一券。
- 当前是教学/项目骨架，尚未实现短信、真实支付、后台管理、部署监控、死信人工处理、完整对账或集群级容灾；高并发与真实服务集成需要部署环境压测。转发或消费持续失败时，必须监控 Stream pending、RocketMQ 重试/死信以及库存回补表并人工处理。
