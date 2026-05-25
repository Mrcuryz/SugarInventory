# 2026-04-22 用户与权限管理交付说明

本次按 [role-permission.md](./role-permission.md) 完成了 RBAC 最小落地版的数据库、后端与 Web 接入。

## 1. 数据库

新增迁移：

- `migrations/2026-04-22-finalize-rbac-role-permission.sql`

本迁移完成以下调整：

1. `role.role_code` 从固定枚举改为 `varchar(50)`，支持新增自定义角色
2. `role` 新增：
   - `status`
   - `updated_at`
3. `role.role_code` 改为唯一索引
4. 预置新版系统所需权限点
5. 初始化 `ADMIN` / `QC` / `STAFF` 默认角色权限

## 2. 后端

### 2.1 登录与当前用户

以下返回已补齐权限集合：

- `POST /api/auth/web-login`
- `POST /api/auth/wechat-login`
- `POST /api/auth/phone-bind`
- `POST /api/auth/manual-bind`
- `GET /api/user/info`

返回字段新增：

- `permissionCodes`

同时 Web 登录不再硬编码 `ADMIN` 才能登录，改为要求角色具备：

- `system:access`

### 2.2 角色与权限管理接口

新增接口：

- `GET /api/rbac/permissions`
- `POST /api/rbac/roles/query`
- `GET /api/rbac/roles/options`
- `GET /api/rbac/roles/{id}`
- `POST /api/rbac/roles`
- `PUT /api/rbac/roles/{id}`
- `PUT /api/rbac/roles/{id}/permissions`
- `PUT /api/rbac/roles/{id}/status`
- `DELETE /api/rbac/roles/{id}`

### 2.3 安全限制

已落地的限制：

1. 管理员角色不可删除
2. 管理员角色不可停用
3. 管理员角色编码不可改
4. 角色被员工或用户使用时不可直接删除
5. 最后一个在职管理员不可被停用或降权
6. 给员工分配角色时，角色必须存在且为启用状态

## 3. Web

### 3.1 权限状态管理

新增：

- `webpage/src/stores/auth.js`

登录后会缓存：

- 姓名
- 工号
- 角色代码
- 权限编码集合

### 3.2 菜单与路由权限

已按 `permCode` 接入：

- 左侧菜单显隐
- 路由访问拦截
- 登录后默认跳转首个有权限页面

### 3.3 页面

1. 当前 `员工管理` 页面已作为 `用户管理` 使用
   - 角色下拉改为动态加载
   - 新增/编辑/删除按钮按权限显隐
2. 新增 `角色管理` 页面
   - 角色分页查询
   - 新增角色
   - 编辑角色
   - 启用/停用角色
   - 删除角色
   - 权限分配抽屉

## 4. 编译校验

本次已执行：

1. 后端编译校验：
   - `mvn -q -Dtest=PalletQrLabelPdfRendererTest test`
2. Web 构建校验：
   - `cd webpage && npm run build`

两项均已通过。
