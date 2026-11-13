#!/bin/bash
# 并发出价正确性测试（Concurrent bid correctness test）
# 用法: MYSQL_PWD=xxx bash concurrent-bid-test.sh [BASE_URL] [USERS]
# 流程：发布新拍品 → 审核通过开拍 → N 个用户并行出价（价格各异）→ 校验数据库不变量
# 不变量：最终价 = 最大出价；LEADING 记录恰 1 条；成功数 = 出价记录数
BASE_URL="${1:-http://localhost:8080}"
USERS="${2:-20}"
MYSQL_BIN="C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
TMP=/tmp/conc-bid
rm -rf $TMP && mkdir -p $TMP/tokens $TMP/res

echo "== 1. 发布拍品并审核通过 =="
SELLER_TOKEN=$(curl -s -X POST $BASE_URL/api/v1/auth/login -H "Content-Type: application/json" -d '{"code":"seller-conc"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
ITEM_ID=$(curl -s -X POST $BASE_URL/api/v1/items -H "Content-Type: application/json" -H "Authorization: Bearer $SELLER_TOKEN" \
  -d '{"title":"Parallel Bid Test","categoryId":2,"description":"concurrent test","condition":"Near new","startPrice":1000,"increment":100,"durationHours":1}' \
  | sed -E 's/.*"data":([0-9]+).*/\1/')
echo "itemId=$ITEM_ID"

ADMIN_TOKEN=$(curl -s -X POST $BASE_URL/api/v1/admin/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -s -X POST $BASE_URL/api/v1/admin/audit/items/$ITEM_ID/approve -H "Authorization: Bearer $ADMIN_TOKEN" > /dev/null

AUCTION_ID=$(export MYSQL_PWD="${MYSQL_PWD}"; "$MYSQL_BIN" -u root -N -e "USE campus_auction; SELECT id FROM t_auction WHERE item_id=$ITEM_ID;")
echo "auctionId=$AUCTION_ID"

echo "== 2. 登录 $USERS 个用户 =="
for i in $(seq 1 $USERS); do
  curl -s -X POST $BASE_URL/api/v1/auth/login -H "Content-Type: application/json" -d "{\"code\":\"conc-$i-$RANDOM\"}" \
    | sed -E 's/.*"token":"([^"]+)".*/\1/' > $TMP/tokens/$i
done
echo "tokens ready: $(ls $TMP/tokens | wc -l)"

echo "== 3. 并行出价（价格 1100 ~ $((1000+USERS*100))）=="
START=$(date +%s%3N)
for i in $(seq 1 $USERS); do
  (
    price=$((1000 + i * 100))
    token=$(cat "$TMP/tokens/$i")
    curl -s -X POST "$BASE_URL/api/v1/auctions/$AUCTION_ID/bids" \
      -H "Content-Type: application/json" -H "Authorization: Bearer $token" \
      -d "{\"price\":$price}" > "$TMP/res/$i"
  ) &
done
wait
END=$(date +%s%3N)
OK_CNT=$(grep -l '"code":0' $TMP/res/* 2>/dev/null | wc -l)
echo "elapsed: $((END-START)) ms | success: $OK_CNT/$USERS"

echo "== 4. 数据库不变量校验 =="
export MYSQL_PWD="${MYSQL_PWD}"
"$MYSQL_BIN" -u root --default-character-set=utf8mb4 -e "
USE campus_auction;
SELECT id, current_price, current_winner_id, bid_count, status FROM t_auction WHERE id=$AUCTION_ID;
SELECT status, COUNT(*) AS cnt FROM t_bid_record WHERE auction_id=$AUCTION_ID GROUP BY status;
SELECT MAX(price) AS max_bid FROM t_bid_record WHERE auction_id=$AUCTION_ID;
SELECT COUNT(DISTINCT user_id) AS frozen_users FROM t_account_log WHERE ref_id=$AUCTION_ID AND type='FREEZE';"

echo "== 判定 =="
FINAL_PRICE=$(export MYSQL_PWD="${MYSQL_PWD}"; "$MYSQL_BIN" -u root -N -e "USE campus_auction; SELECT current_price FROM t_auction WHERE id=$AUCTION_ID;")
MAX_BID=$(export MYSQL_PWD="${MYSQL_PWD}"; "$MYSQL_BIN" -u root -N -e "USE campus_auction; SELECT MAX(price) FROM t_bid_record WHERE auction_id=$AUCTION_ID;")
LEADING_CNT=$(export MYSQL_PWD="${MYSQL_PWD}"; "$MYSQL_BIN" -u root -N -e "USE campus_auction; SELECT COUNT(*) FROM t_bid_record WHERE auction_id=$AUCTION_ID AND status='LEADING';")
if [ "$FINAL_PRICE" = "$MAX_BID" ] && [ "$LEADING_CNT" = "1" ]; then
  echo "PASS: final price == max bid ($FINAL_PRICE), leading count == 1"
else
  echo "FAIL: final=$FINAL_PRICE max=$MAX_BID leading=$LEADING_CNT"
fi
