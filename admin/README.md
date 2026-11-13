# 校园拍卖系统 · Web 管理后台

基于 **Vue 3 + Vite + Element Plus** 的管理端，对接 Spring Boot 的 `/api/v1/admin/**` 接口，
用于拍品审核、用户管理、拍卖监控、订单查询与数据看板。

## 一、运行前准备

1. 启动后端（默认 8080）：见 `../docs/部署文档.md`
2. 确认数据库已初始化（`sql/init.sql`），管理员账号 `admin / admin123`
3. Node.js ≥ 18

## 二、开发运行

```bash
cd admin
npm install      # 首次运行
npm run dev      # http://localhost:5173
```

`vite.config.js` 已把 `/api` 与 `/uploads` 代理到 `http://localhost:8080`，无需处理跨域。

## 三、生产构建

```bash
npm run build          # 产物在 dist/
npm run preview        # 本地预览构建结果
```

Nginx 部署时把 `dist/` 作为静态根目录，并把 `/api` 反向代理到后端：

```nginx
location / {
    root   /var/www/campus-auction-admin;
    index  index.html;
    try_files $uri $uri/ /index.html;   # history 路由必须
}

location /api/ {
    proxy_pass http://127.0.0.1:8080;
}
```

## 四、目录结构

```
admin/
├── index.html
├── vite.config.js          # 端口 5173 + /api 代理
├── package.json
└── src/
    ├── main.js             # 挂载 Element Plus（中文包）+ 全量图标 + 路由
    ├── App.vue
    ├── api/
    │   ├── request.js      # axios 实例：注入 Bearer token、解包 Result{code,msg,data}、401 跳登录
    │   └── admin.js        # 全部管理接口封装
    ├── router/index.js     # 路由表 + 未登录守卫
    └── views/
        ├── Login.vue        # 管理员登录
        ├── Layout.vue        # 侧边栏框架
        ├── Dashboard.vue     # 8 项指标卡 + 状态分布条形图 + 类目成交分布
        ├── Items.vue         # 待审核拍品：图片预览、描述弹窗、通过/驳回
        ├── Users.vue         # 用户分页搜索、余额/信用/状态标签、封禁解封
        ├── Auctions.vue      # 拍卖监控：按状态切换、当前价、出价数、延时次数
        ├── Orders.vue        # 订单查询：状态、成交价、交割地点、完成时间
        └── Reports.vue       # 举报处理（W5）：状态过滤、核实扣分（信用 -20）、驳回
```

## 五、鉴权说明

- 登录成功后 token 存 `localStorage.admin_token`
- 所有请求由 `src/api/request.js` 注入 `Authorization: Bearer <token>`
- 后端 `AuthInterceptor` 对 `/api/v1/admin/**` 前缀强制校验管理员身份（admin token 的 subject 为 `admin:{id}`），
  普通用户 token 访问管理接口会被拒绝
- 返回体非 0 时统一弹 `ElMessage.error(msg)` 并 reject；HTTP 401 清 token 回登录页

## 六、已知限制（论文中可写入“后续工作”）

- 目前仅一个管理员角色，未做多角色权限细分
- 看板为实时统计查询，数据量大时应改为缓存或离线汇总表
- 未做操作审计日志（谁在何时封禁了哪个用户）
