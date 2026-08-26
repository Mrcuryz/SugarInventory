# 设备管理模块实施说明

## 实施范围

本次实现严格依据《设备管理模块分析与设计》，仅包含旧程序已确认的设备台账字段、附属设备关系、生产厂家、设备单位、设备类别、技改类别、修理类型、修理记录和设备编号规则。

未实现巡检、保养计划、故障工单、备件、IoT、折旧、附件或独立设备账号体系。

## 数据库

迁移文件：`migrations/2026-08-26-add-equipment-management-module.sql`

新增表：

- `equipment_unit`
- `equipment_category`
- `equipment_manufacturer`
- `equipment_renovation_type`
- `equipment_repair_type`
- `equipment_code_rule`
- `equipment_asset`
- `equipment_repair_record`

新增权限仅默认授予 `ADMIN`。其他角色需在现有角色管理页面按需配置。

执行迁移时使用项目现有受控脚本，不要直接连接生产数据库执行 SQL：

```powershell
.\scripts\apply-database-migrations.ps1 -EnvFile <环境文件> -VerifyOnly
.\scripts\apply-database-migrations.ps1 -EnvFile <环境文件>
```

远程数据库仍需按脚本要求显式提供 `-AllowRemoteDatabase` 和 `-ExpectedDatabase`。

## 后端

代码位于 `com.Laibin.SugarInventory.equipment`，包括：

- `EquipmentAssetController`：设备台账查询、详情、新增、编辑、删除和导出；
- `EquipmentRepairController`：修理记录查询、增删改和导出；
- `EquipmentBasicDataController`：单位、类别、厂家、技改类别、修理类型和编号规则；
- DTO/PO/VO、Assembler、Mapper 和 Service 分层；
- 写接口使用 `@LogOperation`，所有接口使用独立设备权限。

设备编号在新增事务中生成。新增接口只接收设备单位和设备类别，不接收工段代码；后端自动匹配该单位和类别下唯一启用的编号规则，从规则中取得工段代码，锁定规则行后以 `company-major-unit-section+sequence` 生成编号，并通过唯一索引阻止重复。没有启用规则或存在多条启用规则时拒绝生成编号。设备编辑接口不接收单位、类别或完整编号。

## 前端

新增菜单“设备管理”：

- 设备台账：查询、分页、详情、录入、编辑、删除、导出；
- 修理记录：查询、分页、录入、编辑、删除、导出；
- 基础资料：六类基础数据以标签页统一维护。

页面位于 `webpage/src/components/equipment/`，API 封装位于 `webpage/src/api/equipment.js`。菜单、路由和按钮均按现有 RBAC 权限显示。

新增设备表单不显示“工段代码”输入项。工段仅在基础资料的编号规则中由管理员维护；同一设备单位和设备类别同时只能启用一条规则。

## 上线前初始化顺序

1. 执行数据库迁移；
2. 导入或维护设备单位；
3. 导入或维护设备类别；
4. 导入厂家、技改类别和修理类型；
5. 根据旧设备编号配置并校准编号规则；
6. 导入主设备和附属设备；
7. 导入修理记录；
8. 对账完成后开放新增设备权限。

在编号规则校准完成前，不应授予 `equipment:asset:create`。

## 验证命令

```powershell
mvn test
cd webpage
npm run build
```

迁移文件、权限表达式、编号生成和引用删除限制均有自动化测试覆盖。
