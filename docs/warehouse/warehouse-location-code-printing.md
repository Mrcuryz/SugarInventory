# 库位码打印

## 用户入口

Web 端在“二维码中心”下提供“库位码打印”页面。具备 `qrcode:print` 权限的用户可以按库位名称和状态筛选，选择一个或多个库位后直接提交到本机标签打印助手，也可以导出 PDF。

## 数据与打印边界

- 库位码由后端 `WarehouseLocationCodeService` 生成，前端不得自行拼接编码；
- 每个库位只对应一个固定库位码，不包含左/右、排、层等库位内部位置；
- 批量取码接口为 `POST /api/warehouse/location-codes/query`；
- 批量 PDF 接口为 `POST /api/warehouse/location-qrcode-labels/pdf`；
- 本机直接打印继续使用 `127.0.0.1:9527` 标签打印助手，模板为 `warehouse_location_qrcode`；
- 打印助手不可用时，页面回退为 PDF 导出，不影响库位码生成规则。

## 标签内容

标签二维码保存受控库位码，标签可见文字展示库位名称。二维码解析时仍会查询当前库位记录，因此库位名称、状态和容量以扫码时数据库中的实时数据为准。
