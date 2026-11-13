# CampusAuction — 基于微信小程序的校园在线拍卖系统

本科毕业设计项目。学生发布闲置拍品（起拍价、加价幅度、截止时间），他人公开竞价，价高者得，线下当面交割。资金采用虚拟保证金机制，平台不触碰真实支付。

## 目录结构

```
campus-auction/
├── project.config.json   # 微信开发者工具项目配置
├── miniprogram/          # 小程序端（微信原生，手写样式）
│   ├── pages/            # 主包：大厅 / 详情 / 发布 / 我的 / 登录
│   ├── packageUser/      # 分包：个人中心二级页
│   ├── packageAdmin/     # 分包：管理员审核（演示用）
│   └── utils/            # 请求封装 / 环境配置
├── server/               # Spring Boot 3.2 服务端
│   └── src/main/java/com/campus/auction/
│       ├── common/       # 统一响应 / 全局异常
│       ├── config/       # Web / MyBatis-Plus / Redis / OpenAPI 配置
│       ├── interceptor/  # JWT 登录拦截器
│       ├── controller/   # 接口层
│       ├── service/      # 业务层
│       ├── mapper/       # 数据访问层
│       ├── entity/       # 实体
│       └── util/         # JWT 工具
├── sql/init.sql          # 建库建表脚本（14 张表 + 初始数据）
└── docs/                 # 项目计划书 / 项目档案
```

## 技术栈

| 端 | 技术 |
|---|---|
| 小程序 | 微信原生框架（手写 WXSS，无组件库） |
| 服务端 | Spring Boot 3.2 / MyBatis-Plus / MySQL 8 / Redis 7 / JWT / Knife4j |
| 管理后台 | Vue3 + Element Plus（W4 开始） |

## 本地启动

1. **数据库**：执行 `sql/init.sql`（MySQL 8.0，需要 utf8mb4）
2. **Redis**：启动 `D:\Redis-8.6.4\redis-server.exe`（端口 6379）
3. **服务端**：
   - 密码等敏感信息放在 `server/src/main/resources/application-local.yml`（已 gitignore，不入库）
   - `cd server && mvn spring-boot:run`，接口文档：http://localhost:8080/doc.html
   - 开发期默认 `wx.mock=true`：不调微信接口，openid 用模拟值，无 AppSecret 也能跑通登录
4. **小程序**：微信开发者工具导入项目根目录的 `project.config.json`，
   **务必勾选「不校验合法域名」**（详情 → 本地设置）

## 开发约定

- 金额一律 `int` 存「分」，前端展示再转元
- 响应体统一 `{ code, msg, data }`，code=0 成功；2001/2002 登录失效
- 鉴权头：`Authorization: Bearer <token>`
- 提交信息用中文，格式：`feat/fix/chore/docs(scope): 描述`
- 密钥、数据库密码只放 `application-local.yml` 或环境变量，**严禁提交**

## 进度

见 `docs/小程序项目档案.md`（每轮开发后更新）。
