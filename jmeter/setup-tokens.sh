#!/bin/bash
# 生成 JMeter 压测用的用户 token 与出价 CSV
# 用法: MYSQL_PWD=xxx bash setup-tokens.sh [用户数] [基础出价(分)] > tokens.csv
# 每行格式: token,price（价格递增，避免全部被"出价过低"拒绝）
USERS="${1:-50}"
BASE="${2:-1100}"

for i in $(seq 1 $USERS); do
  TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d "{\"code\":\"jmeter-$i-$RANDOM\"}" | sed -E 's/.*"token":"([^"]+)".*/\1/')
  PRICE=$((BASE + i * 100))
  echo "$TOKEN,$PRICE"
done
