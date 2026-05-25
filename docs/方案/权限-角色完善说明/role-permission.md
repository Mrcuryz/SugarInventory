基于现有表结构的 RBAC 最小落地方案
1. 目标

在不重构现有登录体系、不新增复杂权限模型的前提下，基于当前已经存在的 4 张核心表：

permission
role
role_permission
user（主要是role_code字段）

补齐一套 可由甲方自行管理的用户-角色-权限分配能力。

本次方案坚持最小改动原则：

一个用户只分配一个角色
角色绑定一组权限
权限点由系统预置
甲方只负责给用户分角色、给角色勾权限
不开放“自定义权限编码”能力
不引入多角色、数据权限、部门权限等复杂模型
2. 当前基础情况

你当前已经有以下结构：

2.1 permission

用于定义权限点。

核心字段：

id
perm_code
perm_name
description

例如：

system:access
product:create
product:update
quality:test
record:query
2.2 role

用于定义角色。

核心字段：

id
role_name
role_code
description

当前已有角色示例：

ADMIN
QC
STAFF
2.3 role_permission

用于建立角色与权限点的关系。

核心字段：

role_id
permission_id
2.4 user.role_code

用户表中已经通过 role_code 直接关联角色。

这意味着当前方案已经具备最基本的 RBAC 骨架，不需要重新设计数据库范式。

3. 本次方案的核心原则
3.1 一个用户只分配一个角色

这是本次最重要的约束。

原因：

逻辑简单
前后端实现成本低
甲方容易理解
避免多角色冲突、优先级问题

即：

user -> role_code -> role -> role_permission -> permission

3.2 权限点由系统预置，甲方只做分配

甲方可以：

新增角色
编辑角色名称和说明
给角色分配权限
给用户分配角色

甲方不可以：

自己新增 perm_code
自己修改权限编码语义
自己设计权限树结构

原因：

保证前后端权限编码一致
避免后续升级兼容性问题
降低误配置风险
3.3 菜单权限与操作权限统一走 perm_code

建议系统中所有权限都统一落到 perm_code。

包括：

菜单可见性
按钮显隐
后端接口鉴权

这样可以做到：

前端按权限隐藏页面和按钮
后端按权限控制真实访问
前后端口径一致
4. 建议的数据约束与校验

本次不要求重构表结构，但建议补齐以下约束或服务层校验。

4.1 permission

建议保证：

perm_code 唯一
4.2 role

建议保证：

role_code 唯一
4.3 role_permission

建议保证：

(role_id, permission_id) 联合唯一

避免重复插入同一权限。

4.4 user.role_code

建议保证：

分配角色时，role_code 必须真实存在于 role 表
若不加数据库外键，也必须在服务层校验
5. 权限点补齐建议

当前已有权限点偏老，建议按当前新版系统实际页面和操作重新整理一套权限清单。

5.1 一级建议：菜单访问类权限

这些权限决定页面/菜单是否可见。

建议至少包含：

system:access 系统访问
dashboard:view 首页查看
product:view 产品管理查看
inventory:view 库存汇总查看
warehouse:view 库位管理查看
warehouse_map:view 仓库平面图查看
qrcode:view 二维码管理查看
task:view 任务中心查看
screen_mesh:view 筛网管理查看
assay:view 化验管理查看
assay_group:view 批量化验组查看
quality_standard:view 化验标准管理查看
document:view 单据中心查看
employee:view 员工管理查看
rbac:user:view 用户管理查看
rbac:role:view 角色管理查看
5.2 二级建议：操作类权限

这些权限决定按钮和接口是否可执行。

产品
product:create
product:update
product:delete
库位
warehouse:create
warehouse:update
warehouse:delete
warehouse:status
二维码
qrcode:generate
qrcode:print
qrcode:bind_fixed_product
qrcode:activate
qrcode:pool_view
任务
task:create
task:confirm
task:cancel
化验
assay:create
assay:update
assay:delete
assay:query
assay:copy
批量化验组
assay_group:create
assay_group:update
assay_group:delete
化验标准
quality_standard:create
quality_standard:update
quality_standard:delete
quality_standard:bind_product
单据/记录
record:view
record:export
员工
employee:create
employee:update
employee:delete
用户/角色
rbac:user:update_role
rbac:user:enable_disable
rbac:user:reset_password
rbac:role:create
rbac:role:update
rbac:role:delete
rbac:role:assign_permission

等等，据实际接口情况而定

5.3 实施建议
根据当前已有接口分配对应权限，确保接口前权限注解与权限表中一致

6. 后端鉴权建议
维持当前登录认证校验体系，保证系统安全性

7. 前端实现建议

本次前端只建议补两个核心页面即可：

用户管理（补角色分配，在当前“员工管理”的基础上修改）
角色管理（补权限分配）

7.1 用户管理页
页面目标

让甲方可以：

查看用户基础信息（部分在employee_roster）表
给用户分配角色
启用/停用用户（当前表现为在职/离职）

建议列表字段
工号
姓名
手机号
部门
当前角色
状态

操作：
编辑

编辑中除修改角色外，还应保留原有的修改基本信息的能力

弹窗中只做一件事：

单选一个角色

因为本次明确规定：

一个用户只能分配一个角色

保存时：

直接更新 user.role_code

注：保留原有的查询能力，并确保“导入Excel”与更新后的用户管理页兼容

7.2 角色管理页
页面目标

让甲方可以：

新增角色
编辑角色
给角色勾选权限
启用/停用角色


建议列表字段
角色名称
角色编码
说明
状态
用户列表（给一个标签列表，展示当前为该角色的用户，UI可参考批量化验组的关联产品，允许换行）

操作：
编辑
分配权限
启用/停用
删除（受限制）
7.3 权限分配抽屉/弹窗
目标

让甲方可以直观地勾选角色权限。

展示方式建议

建议按功能分组展示，而不是裸列表：

首页
产品管理
库位管理
仓库平面图
二维码管理
任务中心
化验管理
批量化验组
化验标准管理
单据中心
员工管理
用户管理
角色管理

每组下再勾选对应的：

查看
新增
编辑
删除
打印
确认
取消
分配

前端可以维护一份固定的权限分组配置：

按 perm_code 映射展示名称与分组
页面渲染时根据这份配置展示权限树

这样无需新增权限分组表，开发最快。

8. 默认角色建议

本次建议先保留当前已有角色体系，不强制改库。

当前已有：

ADMIN
QC
STAFF

可以继续沿用，只需重新补权限。

8.1 ADMIN

建议拥有：

全部菜单权限
全部操作权限
用户/角色管理权限
8.2 QC

建议拥有：

首页查看
产品查看
化验管理查看/新增/编辑
批量化验组查看/新增/编辑
化验标准查看
库存、二维码、任务等必要查询权限
不具备系统管理权限
不具备用户/角色分配权限
8.3 STAFF

建议拥有：

首页查看
查询类页面查看
任务中心查看
与自身作业相关的基础操作权限
不具备用户管理、角色管理、产品管理、标准管理等高权限
9. 关键安全规则
9.1 必须保留一个最高权限角色

至少要有一个超级管理角色可用，避免整套系统无人可管。

9.2 最后一个最高权限用户不能被停用或降权

避免系统把自己锁死。

9.3 角色删除前先检查是否仍有用户在使用

若仍有用户绑定该角色：

不允许直接删除
需要先迁移用户角色
9.4 权限点不开放给甲方自定义

甲方只能勾选已有权限，不能新建权限编码。

10. 实施顺序建议
第一阶段：最小可用版
整理现有 permission 表，补齐新版系统需要的权限点
做用户管理页的“分配角色”
做角色管理页
做角色分配权限弹窗
后端登录时返回权限集合
前端菜单显隐接权限
后端关键接口接权限校验
第二阶段：优化版
按钮级权限显隐补齐
角色用户数统计
启用/停用角色
用户最后登录时间展示
权限变更操作日志
11. 给 Codex 的明确实现要求
基于现有表结构继续实现，不重构数据库模型
保持一个用户只分配一个角色
继续使用：
permission
role
role_permission
user.role_code
不开放权限点自定义能力，权限编码由系统预置
补齐与当前新版系统对应的权限点
新增两个页面：
用户管理（分配角色）
角色管理（分配权限）
分配角色时，只改单个 user.role_code
分配权限时，维护 role_permission
登录后要能拿到当前用户完整权限集合
菜单、按钮、接口鉴权都尽量统一走 perm_code
必须加安全限制：
最后一个管理员不可被降权/停用
角色被使用时不可直接删除