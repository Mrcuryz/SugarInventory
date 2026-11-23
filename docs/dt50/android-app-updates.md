# Android 远程版本发布与检查更新

## 两台 Windows 电脑打包

两台电脑使用 PowerShell 7、Python 3、JDK 21（设置 JAVA_HOME）、Android SDK Platform 35 和 Build Tools 34.0.0。SDK 使用默认路径或设置 ANDROID_HOME。

原电脑在仓库根目录运行 `./scripts/export-android-signing-key.ps1`，按提示设置交接口令。将生成的 `dt50-app/.signing/release-transfer.p12` 私下复制到另一台电脑仓库的相同位置（可自行创建 .signing 目录）。首次签名输入该口令，验证成功后由 Windows 当前用户记住。

每次发布前拉取当前分支，在 `dt50-app/app/build.gradle` 递增 versionCode 并更新 versionName；与线上最新版本核对。两台电脑均在仓库根目录运行：

```powershell
./scripts/build-android-update.ps1 "本次更新说明"
```

将 `output/android-update/feed/android/` 中的新 APK 上传到服务器，核对后再替换 latest.json。私钥文件及口令应保存在 Git 之外。

## 已实现的行为

从 0.3.1（versionCode 9）开始，登录页和工作台均显示当前版本和“检查更新”。进入这两个页面时自动检查版本；成功检查后六小时内不重复自动检查，手动检查不受此限制。自动检查失败保持安静，手动失败只显示可理解的中文提示，不展示网络或解析异常。

发现更高 versionCode 且包名、签名证书、最低系统版本适配时，展示更新说明。用户可稍后处理，或点击“下载更新”交给浏览器下载，再打开 APK 按 Android 系统提示安装。Android 8 及以上可能要求为浏览器允许安装未知来源应用。此实现不包含进程关闭时的实时通知推送、静默安装或强制更新。

0.3.0 本身没有检查更新功能，必须先手动安装一次带更新功能的版本（当前待发布为 0.3.2）。2026-11-06 新签名的 0.3.0 可以覆盖升级到同签名 0.3.1；旧签名 0.2.5 仍不能直接覆盖。后续保持包名和 v2 签名不变，每次递增 versionCode。

## 公共版本文件

固定读取 `https://ccgl.site/app-updates/android/latest.json`，无需登录，不携带账号或 JWT。字段由发布工具从真实 APK 提取，格式如下（示例不代表已发布）：

```json
{
  "schemaVersion": 1,
  "packageName": "com.laibin.sugarinventory.dt50",
  "versionCode": 9,
  "versionName": "0.3.1",
  "minSdk": 23,
  "certificateSha256": "24e4ff7cb3bf9065383e0f5e086b847ae4abeaf84ba132a7dc76d06350c75f60",
  "downloadUrl": "https://ccgl.site/app-updates/android/laibin-warehouse-9-HASH.apk",
  "sha256": "APK 文件 SHA-256",
  "sizeBytes": 123456,
  "releaseNotes": "增加版本检查和更新提示。"
}
```

客户端只接受同源 HTTPS、固定更新目录内的 APK 地址，禁止认证信息、查询参数、片段和路径跳转；版本文件限制为 32 KiB，连接与读取各超时 10 秒，不跟随版本文件重定向。安装包由浏览器下载，客户端不对下载后的字节计算 SHA-256；发布工具会生成校验值，Android 安装器负责覆盖安装时的证书校验。Debug 包因包名和签名不同不会安装正式版更新。

## 构建和签名

修改 `dt50-app/app/build.gradle` 的 versionCode/versionName，使用 PowerShell 7：

```powershell
cd dt50-app
.\gradlew.bat testDebugUnitTest assembleRelease
cd ..
.\scripts\sign-android-release.ps1 -OutputApk "$PWD\output\android-update\app-0.3.2.apk"
```

签名脚本默认使用 Android SDK build-tools 34.0.0 和 JAVA_HOME，也可以通过参数指定路径。沿用 `.signing/laibin-warehouse-trial-v2.jks` 与 `laibin-warehouse-trial-v2-password.dpapi.txt`；不输出明文口令，不创建新签名。DPAPI 仅能由原 Windows 用户解密。迁机、清盘或重装 Windows 前需单独安全备份可恢复的密钥和口令，单独复制 DPAPI 文件不能保证跨机器恢复。密钥和口令不得提交 Git 或上传到公开更新目录。

## 生成发布目录

将用户可读的更新说明保存为 UTF-8 文本，再执行：

```powershell
python scripts/package-android-update.py --apk output/android-update/app-0.3.2.apk --build-tools "$env:LOCALAPPDATA/Android/Sdk/build-tools/34.0.0" --notes output/android-update/notes.txt --output output/android-update/feed
```

工具验证签名、包名、非调试模式及版本递增，生成 `feed/android/` 下的 APK 与 `latest.json`。同目录存在相同或更高版本时拒绝覆盖。APK 文件名包含版本号和内容哈希；先落盘 APK，再原子替换本地版本文件。

## 服务器启用与后续发布

仓库内 `deploy/simple` 已增加独立的 `./artifacts/app-updates:/srv/app-updates:ro` 挂载及 Nginx `/app-updates/` 静态路由。更新资源不经过后端、不连接数据库，文件不存在返回 404，不回退到网页首页。响应禁止缓存，APK 使用 Android 安装包 MIME 类型。

首次启用须在服务器创建 `deploy/simple/artifacts/app-updates/android/`，复制 APK，再上传 `latest.json.tmp` 并在同一目录重命名为 `latest.json`；检查 `docker compose config`，运行 `docker compose run --rm --no-deps nginx nginx -t`，成功后重建 Nginx 容器使挂载生效。使用其他部署方式时须配置等价路由。发布操作不要覆盖整个前端 dist，也不要将签名目录复制到服务器。

以后每次更新：构建签名 → 生成发布目录 → 上传带版本号的 APK → 核对远端下载的 SHA-256 → 最后原子切换 latest.json。可在服务器将旧版本 JSON 恢复以停止推荐新版本；已经升级的设备不会自动降级。避免两个发布操作同时进行，发布前比对线上 versionCode。

## 验收与上线边界

自动化覆盖版本递增、同版本/降级忽略、系统版本兼容、包名和证书不匹配、跨域/明文/路径绕过 URL 拒绝；构建还需通过 Android Lint。发布工具应用于真实签名 APK 并检验重复版本拒绝。

上线后应在隔离测试设备验证：手动无更新、远端更高版本提示、稍后取消、浏览器下载、安装授权、同签名覆盖、登录和草稿保留、断网提示、未登录可检查。当前代码和本地发布目录不代表生产服务器已经启用，也不代表已完成系统安装器端到端验收。生产日报写入与 DT50 扫描硬件仍沿用既有未验收边界。

## 2026-11-08 本机验证记录

26 项 Android 单测通过，release 构建及 Lint 通过；0.3.1 APK 的 v1/v2/v3 签名通过，证书与 2026-11-06 的 0.3.0 新签名一致。发布工具已在真实 APK 上验证哈希与版本，重复版本和旧签名均被拒绝且未修改 latest.json。本机 Docker 服务未运行，尚未执行 Nginx 运行验证、生产启用或手机系统安装器端到端验证。

## 2026-11-18 复核

当前构建为 0.3.2（versionCode 10），包括按车间记录制表人的修复。新 APK 沿用 v2 证书，未发布线上。

再次请求线上 `latest.json` 返回 HTTP 200 / text/html，并非 JSON；线上静态路由和更新资源仍未启用。手机已有隔离环境证据覆盖自动发现版本、工作台手动检查、无更新、签名不匹配和友好错误提示。该结果不等于生产下载和安装端到端验收通过。新版制表人字段须在数据库迁移和后端发布完成后才能生效。

本次在 Samsung SM-G9910 / Android 13 复核自动更新提示、工作台手动触发、“当前已是最新版本”以及畸形 JSON 的友好提示，均通过。独立验收包由 code 10 覆盖安装至 code 11，安装后登录信息保持一致，直接进入工作台。覆盖安装通过 ADB 触发，没有完成浏览器下载→系统安装器这一链路，也未验证本机日报草稿升级保留。

- [更新弹窗](screenshots/2026-11-18/12-workbench-update-20261118.png)
- [当前已是最新版本](screenshots/2026-11-18/13-current-version-20261118.png)
- [错误转换为中文提示](screenshots/2026-11-18/14-friendly-error-20261118.png)
- [覆盖安装后仍保持登录](screenshots/2026-11-18/15-upgraded-session-retained.png)

本次交付候选包：`output/android-update/app-0.3.2.apk`，SHA-256 `e3941e3a7fdadebb5917bd7cd15dea3cf00bea2fe646f09378c314877852d25e`。签名 v1/v2/v3 验证通过，证书 SHA-256 与既有 v2 密钥一致。发布目录为 `output/android-update/feed/android/`；尚未上传服务器。
