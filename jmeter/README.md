# JMeter 竞价并发压测指南

## 用途

论文第 6 章「系统测试」的并发压测：验证高并发出价下系统零错价（最终价 = 最高出价、领先记录唯一）。

## 前置条件

1. JMeter 5.6+（https://jmeter.apache.org 下载解压即可，无需安装）
2. 服务端已启动（`cd /e/campus-auction/server && mvn spring-boot:run`）
3. MySQL 可连接（用于结果校验）

## 操作步骤

### 第一步：准备测试拍卖

用两个 curl 创建一场新拍卖（或用小程序发布 + 审核通过）：

```bash
# 发布（任意用户）
curl -X POST http://localhost:8080/api/v1/items -H "Content-Type: application/json" \
  -H "Authorization: Bearer <用户token>" \
  -d '{"title":"JMeter压测拍品","categoryId":2,"condition":"九成新","startPrice":1000,"increment":100,"durationHours":1}'
# 审核通过（admin）
curl -X POST http://localhost:8080/api/v1/admin/audit/items/<itemId>/approve \
  -H "Authorization: Bearer <admin token>"
# 查 auctionId
mysql -u root -p -e "USE campus_auction; SELECT id,item_id FROM t_auction ORDER BY id DESC LIMIT 1;"
```

### 第二步：生成压测用户 CSV

```bash
cd /e/campus-auction/jmeter
MYSQL_PWD=xxx bash setup-tokens.sh 50 1100 > tokens.csv
# 每行：token,price（价格 1200~6100 递增）
```

### 第三步：配置并运行

1. JMeter 打开 `bid-concurrency.jmx`；
2. 修改 Test Plan 变量 `auctionId` 为第一步的拍卖 ID；
3. 确认 `CSVDataSet` 的 filename 指向 tokens.csv（相对 jmx 所在目录）；
4. 点击启动 ▶；
5. 查看「聚合报告」：平均响应时间、P90/P95、错误率（HTTP 200 为正常，业务码 3003 表示被规则拒绝——属预期竞态）。

> ⚠️ 本 jmx 为手工编写，首次使用请在 JMeter 图形界面核对各组件加载是否正常；如有问题按下面手动配置。

### 手动配置（备用）

| 组件 | 配置 |
|---|---|
| Thread Group | Number of Threads=50, Ramp-up=5s, Loop=1 |
| CSV Data Set Config | filename=tokens.csv, delimiter=`,`, variable names=`token,price`, recycle=false, share=all threads |
| HTTP Request | POST `http://localhost:8080/api/v1/auctions/${auctionId}/bids`，Body: `{"price": ${price}}` |
| HTTP Header Manager | `Content-Type: application/json`、`Authorization: Bearer ${token}` |
| 断言 | Response Code = 200；可加 JSON 断言 `$.code` |
| 监听器 | 聚合报告（截图进论文）、查看结果树 |

### 第四步：结果校验（论文核心数据）

```sql
USE campus_auction;
-- 不变量 1：最终价 = 最高出价
SELECT current_price, (SELECT MAX(price) FROM t_bid_record WHERE auction_id=<id>) AS max_bid
FROM t_auction WHERE id=<id>;
-- 不变量 2：领先记录恰好 1 条
SELECT status, COUNT(*) FROM t_bid_record WHERE auction_id=<id> GROUP BY status;
-- 不变量 3：冻结保证金人数 = 参拍人数
SELECT COUNT(DISTINCT user_id) FROM t_account_log
WHERE ref_id=<id> AND type='FREEZE';
```

## 本机已完成的实测基线（curl 并行版）

| 并发数 | 总耗时 | 成功/总数 | 零错价 | 工具 |
|---|---|---|---|---|
| 20 | 1862 ms | 17/20（3 个被「出价过低」规则拒绝，属正常竞态） | ✅ | `server/scripts/concurrent-bid-test.sh` |
| 50 | 3897 ms | 50/50 | ✅ | 同上 |

> curl 版脚本 `server/scripts/concurrent-bid-test.sh` 可重复运行，适合快速回归；JMeter 版用于论文正式压测报告（有聚合报告与图表）。
