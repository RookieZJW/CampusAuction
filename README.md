# CampusAuction — 基于微信小程序的校园在线拍卖系统

本科毕业设计项目。学生发布闲置拍品（起拍价、加价幅度、截止时间），他人公开竞价，价高者得，线下当面交割。资金采用虚拟保证金机制，平台不触碰真实支付。

## 目录结构

```
campus-auction/
├── project.config.json   # 微信开发者工具项目配置
├── miniprogram/          # 小程序端（微信原生，手写样式）
│   ├── pages/            # 主包：大厅 / 详情 / 发布 / 我的 / 登录
│   ├── packageUser/      # 分包：个人中心二级页（订单/出价/发布/钱包/信用/消息/评价/收藏）
│   ├── packageAdmin/     # 分包：管理员审核（演示用）
│   └── utils/            # 请求封装 / 环境配置
├── admin/                # Web 管理后台（Vue3 + Element Plus，Vite 开发）
│   └── src/views/        # 登录/看板/审核/用户/拍卖监控/订单/举报处理
├── server/               # Spring Boot 3.2 服务端
│   └── src/main/java/com/campus/auction/
│       ├── common/       # 统一响应 / 全局异常
│       ├── config/       # Web / MyBatis-Plus / Redis / OpenAPI 配置
│       ├── interceptor/  # JWT 登录拦截器（双身份：用户/管理员）
│       ├── controller/   # 接口层
│       ├── service/      # 业务层（含 AI 定价双通道）
│       ├── mapper/       # 数据访问层
│       ├── entity/       # 实体
│       ├── task/         # 定时任务（结算 30s / 违约扫描 1h）
│       └── util/         # JWT / JSON 工具
├── sql/init.sql          # 建库建表脚本（14 张表 + 初始数据）
├── jmeter/               # 并发压测方案（jmx + token 生成脚本）
└── docs/                 # 项目文档
    ├── 校园拍卖小程序-项目计划书.md  # 需求/架构/排期（含实际执行偏差附录）
    ├── 使用说明书.md      # 用户 + 管理员操作手册
    ├── 部署文档.md        # 开发/演示/生产三级部署指南
    ├── 小程序项目档案.md  # 开发进度档案
    └── screenshots/      # 后台实测截图
```

## 技术栈

| 端 | 技术 |
|---|---|
| 小程序 | 微信原生框架（手写 WXSS，无组件库） |
| 服务端 | Spring Boot 3.2 / MyBatis-Plus / MySQL 8 / Redis 8 / JWT / Knife4j |
| 管理后台 | Vue3 + Vite + Element Plus（W4 完成） |
| AI | DeepSeek 大模型（OpenAI 兼容接口）+ 规则估价降级（W5） |

## 本地启动

1. **数据库**：执行 `sql/init.sql`（MySQL 8.0，需要 utf8mb4）
2. **Redis**：启动 `D:\Redis-8.6.4\redis-server.exe`（端口 6379）
3. **服务端**：
   - 密码与微信密钥放在 `server/src/main/resources/application-local.yml`（已 gitignore，不入库）
   - `cd server && mvn spring-boot:run`，接口文档：http://localhost:8080/doc.html
   - 当前 `wx.mock=false`：登录走真实微信 code2session（AppID/Secret 已配，仅本机 local.yml 生效）
4. **管理后台**：`cd admin && npm install && npm run dev` → http://localhost:5173（admin / admin123）
5. **小程序**：微信开发者工具导入项目根目录的 `project.config.json`，
   **务必勾选「不校验合法域名」**（详情 → 本地设置）

> ⚠️ 本机已卸载 Chrome，浏览器自动化/打印用 Edge（`--browser=msedge` / msedge.exe）。

## 开发约定

- 金额一律 `int` 存「分」，前端展示再转元
- 响应体统一 `{ code, msg, data }`，code=0 成功；2001/2002 登录失效
- 鉴权头：`Authorization: Bearer <token>`；用户 token subject=openid，管理员 token subject=`admin:{id}`
- 提交信息用中文，格式：`feat/fix/chore/docs(scope): 描述`
- 密钥、数据库密码只放 `application-local.yml` 或环境变量，**严禁提交**
- 服务端日志落文件：`mvn -q -o spring-boot:run > server-run.log 2>&1`（该文件已 gitignore）

## 关键实现（论文素材）

- **竞价双层一致性**：Redis Lua 原子预校验（HASH `auction:bid:{id}`）+ MySQL 乐观锁（version + current_price 条件更新），Redis 不可用自动降级 DB 单层；实测 50 并发零错价
- **防狙击**：结束前 5 分钟出价自动延时 2 分钟，最多 3 次
- **三重幂等结算**：Redis SETNX 锁 + 状态 CAS + `t_order.auction_id` 唯一索引
- **违约自动处理**：48h 未交割 → 没收保证金 + 信用分 -10 + 双方通知
- **AI 定价双通道**：大模型优先，未配 Key/超时/异常自动降级规则估价，永不 500
- **互评/举报/收藏**：一单一人一次、同人同对象幂等、管理端核实扣分闭环

## 进度

见 `docs/小程序项目档案.md`（每轮开发后更新）。**W1–W5 全部功能完成（2026-11-13 当天），仅剩收尾：真机验证、中期材料、论文撰写。**
