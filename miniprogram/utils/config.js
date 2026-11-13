// 环境配置
// 本地开发指向 Spring Boot 服务端；开发者工具需勾选「不校验合法域名」
// 部署到体验版/正式环境时改为已备案的 https 域名
module.exports = {
  BASE_URL: 'http://localhost:8080',
  // 图片基础路径（上传接口 W2 实现）
  IMG_BASE: 'http://localhost:8080'
}
