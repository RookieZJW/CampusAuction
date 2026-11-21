// 环境配置
// 本地开发 / 真机调试 / 公网部署 三种模式切换
//
// 【答辩现场：真机调试】
//   1. 把 USE_LAN_IP 改为 true
//   2. 确认下面的 LAN_IP 是当前电脑的局域网 IP（cmd 输入 ipconfig 查 WLAN 的 IPv4）
//   3. 手机与电脑连同一个 WiFi
//   4. 开发者工具：详情 → 本地设置 → 勾选「不校验合法域名、web-view(业务域名)、TLS 版本以及 HTTPS 证书」
//   5. 点「真机调试」生成二维码，评委手机扫码（注意：不是「预览」，预览会校验域名）
//   6. 答辩结束把 USE_LAN_IP 改回 false
//
// 【正式部署】把 BASE_URL / IMG_BASE 改成已备案的 https 域名

// 答辩演示开关：true = 用局域网 IP（手机真机调试），false = 用 localhost（开发者工具模拟器）
const USE_LAN_IP = true

// 当前电脑的局域网 IP（换网络环境后需重新查 ipconfig 更新）
const LAN_IP = '192.168.3.228'

const HOST = USE_LAN_IP ? 'http://' + LAN_IP + ':8080' : 'http://localhost:8080'

module.exports = {
  BASE_URL: HOST,
  IMG_BASE: HOST
}
