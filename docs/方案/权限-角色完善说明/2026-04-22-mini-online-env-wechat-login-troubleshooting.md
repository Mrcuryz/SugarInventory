# 小程序线上环境切换后的登录排查

日期：2026-04-22

问题拆分：

1. 前端配置错误  
- `app.json` 中配置了 `permission.scope.phoneNumber`
- 该配置在小程序中无效，会触发：
  - `invalid app.json permission["scope.phoneNumber"]`

2. 线上微信配置问题  
- 日志中出现：
  - `INVALID_TOKEN, invalid credential, access_token is invalid or not latest`
- 这说明当前线上服务在调用微信相关接口时，使用的凭证不可用或不是最新
- 该问题与 `BASE_URL` 改为线上后才暴露有关，本质是线上后端微信配置或缓存令牌状态异常

本次处理：

- 移除了 `LaibinSugarInventoryWxAPP/app.json` 中无效的 `permission.scope.phoneNumber`

后续需核对：

- 线上服务使用的 `appid` / `secret` 是否与当前小程序一致
- 线上服务器时间是否准确
- 微信接口调用相关 token 缓存是否失效或被旧实例污染
