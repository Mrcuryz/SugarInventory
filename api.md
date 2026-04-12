# 冰糖工厂仓库管理 API


**简介**:冰糖工厂仓库管理 API


**HOST**:http://localhost:8080


**联系人**:技术支持


**Version**:1.0


**接口路径**:/v3/api-docs


[TOC]






# 用户管理


## 获取用户信息


**接口地址**:`/api/user/info`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据JWT Token解析用户信息，返回用户姓名、工号、角色代码</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultMapStringObject|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


# 出库管理


## 产品调拨出库


**接口地址**:`/api/out-stock/transferOut`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>产品调拨出库</p>



**请求示例**:


```javascript
{
  "productId": 101,
  "warehouseId": 101,
  "quantity": 10,
  "unit": "0",
  "outType": 0,
  "side": "LEFT",
  "inWarehouseName": "101"
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|transferOutStockRequestDTO|调拨出库请求DTO|body|true|TransferOutStockRequestDTO|TransferOutStockRequestDTO|
|&emsp;&emsp;productId|产品id||true|integer(int32)||
|&emsp;&emsp;warehouseId|库位ID||true|integer(int32)||
|&emsp;&emsp;quantity|出库数量||true|integer(int32)||
|&emsp;&emsp;unit|出库单位：0板1件||true|string||
|&emsp;&emsp;outType|0整版优先1散件优先||true|integer(int32)||
|&emsp;&emsp;side|先从左/右侧出库，默认为左||false|string||
|&emsp;&emsp;inWarehouseName|库位名称||true|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultInVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||InVO|InVO|
|&emsp;&emsp;remainingQuantity|多余的产品数量（板）|integer(int32)||
|&emsp;&emsp;message|详细信息|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"remainingQuantity": 0,
		"message": ""
	}
}
```


## 栈式出库操作


**接口地址**:`/api/out-stock/stack-out`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>新增特殊库位出库记录</p>



**请求示例**:


```javascript
{
  "productId": 101,
  "warehouseId": 101,
  "quantity": 10,
  "unit": "0",
  "outType": 0,
  "side": "LEFT"
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|outStockRequestDTO|出库请求DTO|body|true|OutStockRequestDTO|OutStockRequestDTO|
|&emsp;&emsp;productId|产品id||true|integer(int32)||
|&emsp;&emsp;warehouseId|库位ID||true|integer(int32)||
|&emsp;&emsp;quantity|出库数量||true|integer(int32)||
|&emsp;&emsp;unit|出库单位：0板1件||true|string||
|&emsp;&emsp;outType|0整版优先1散件优先||true|integer(int32)||
|&emsp;&emsp;side|先从左/右侧出库，默认为左||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultOutVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||OutVO|OutVO|
|&emsp;&emsp;remainingQuantity|需补充的库存数量（板）|integer(int32)||
|&emsp;&emsp;message|详细信息|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"remainingQuantity": 0,
		"message": ""
	}
}
```


## 出库记录查询


**接口地址**:`/api/out-stock/records`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据条件批量查询出库记录信息</p>



**请求示例**:


```javascript
{
  "warehouseName": "",
  "productName": "",
  "startDate": "",
  "endDate": "",
  "operatorName": "i",
  "page": 1,
  "size": 10
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|outRecordQueryDTO|查询出库记录DTO|body|true|OutRecordQueryDTO|OutRecordQueryDTO|
|&emsp;&emsp;warehouseName|||false|string||
|&emsp;&emsp;productName|||false|string||
|&emsp;&emsp;startDate|查询起始日期||false|string(date-time)||
|&emsp;&emsp;endDate|查询结束日期||false|string(date-time)||
|&emsp;&emsp;operatorName|操作员姓名||false|string||
|&emsp;&emsp;page|当前页码||false|integer(int32)||
|&emsp;&emsp;size|每页记录数||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResultOutStockRecordVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResultOutStockRecordVO|PageResultOutStockRecordVO|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|OutStockRecordVO|
|&emsp;&emsp;&emsp;&emsp;warehouseName||string||
|&emsp;&emsp;&emsp;&emsp;productName||string||
|&emsp;&emsp;&emsp;&emsp;quantity||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;inDate||string(date)||
|&emsp;&emsp;&emsp;&emsp;totalWeight||number||
|&emsp;&emsp;&emsp;&emsp;outDate||string(date)||
|&emsp;&emsp;&emsp;&emsp;operator||string||
|&emsp;&emsp;&emsp;&emsp;assayId|化验记录id|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;sampleDate|采样日期|string(date)||
|&emsp;&emsp;&emsp;&emsp;colorValue|色值|number||
|&emsp;&emsp;&emsp;&emsp;reducingSugar|还原糖分|number||
|&emsp;&emsp;&emsp;&emsp;dryWeight|干燥失重|number||
|&emsp;&emsp;&emsp;&emsp;conductivityAsh|电导灰分|number||
|&emsp;&emsp;&emsp;&emsp;sucrose|蔗糖分|number||
|&emsp;&emsp;&emsp;&emsp;insolubleImpurity|不溶于水杂质|number||
|&emsp;&emsp;&emsp;&emsp;phValue|pH值|number||
|&emsp;&emsp;&emsp;&emsp;testedBy||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;testerName|化验人名称|string||
|&emsp;&emsp;&emsp;&emsp;isQualified|是否检验合格|string||
|&emsp;&emsp;&emsp;&emsp;qualifiedStandards|JSON格式的合格标准|string||
|&emsp;&emsp;&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;&emsp;&emsp;pieces|件数|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;unit|出库单位：0板1件|string||
|&emsp;&emsp;&emsp;&emsp;outType|0整版优先1散件优先|integer(int32)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": [
			{
				"warehouseName": "",
				"productName": "",
				"quantity": 0,
				"inDate": "",
				"totalWeight": 0,
				"outDate": "",
				"operator": "",
				"assayId": 0,
				"sampleDate": "2025-02-27",
				"colorValue": 0,
				"reducingSugar": 0,
				"dryWeight": 0,
				"conductivityAsh": 0,
				"sucrose": 0,
				"insolubleImpurity": 0,
				"phValue": 0,
				"testedBy": 0,
				"testerName": "李四",
				"isQualified": "合格/不合格",
				"qualifiedStandards": "",
				"createdAt": "",
				"pieces": 0,
				"unit": "0",
				"outType": 0
			}
		]
	}
}
```


## 产品出库操作


**接口地址**:`/api/out-stock/out`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>创建出库记录</p>



**请求示例**:


```javascript
{
  "productId": 101,
  "warehouseId": 101,
  "quantity": 10,
  "unit": "0",
  "outType": 0,
  "side": "LEFT"
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|outStockRequestDTO|出库请求DTO|body|true|OutStockRequestDTO|OutStockRequestDTO|
|&emsp;&emsp;productId|产品id||true|integer(int32)||
|&emsp;&emsp;warehouseId|库位ID||true|integer(int32)||
|&emsp;&emsp;quantity|出库数量||true|integer(int32)||
|&emsp;&emsp;unit|出库单位：0板1件||true|string||
|&emsp;&emsp;outType|0整版优先1散件优先||true|integer(int32)||
|&emsp;&emsp;side|先从左/右侧出库，默认为左||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultOutVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||OutVO|OutVO|
|&emsp;&emsp;remainingQuantity|需补充的库存数量（板）|integer(int32)||
|&emsp;&emsp;message|详细信息|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"remainingQuantity": 0,
		"message": ""
	}
}
```


# 产品管理


## 创建产品


**接口地址**:`/api/products`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据产品创建信息创建新产品，返回新创建产品的详细信息</p>



**请求示例**:


```javascript
{
  "productName": "中冰",
  "productType": "白冰糖",
  "status": "半成品",
  "packagingMethod": "箱",
  "weightPerPiece": 40,
  "piecesPerPallet": 100,
  "canStack": true,
  "screenMeshId": 0,
  "id": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|productCreateDTO|产品创建请求DTO，用于创建新产品|body|true|ProductCreateDTO|ProductCreateDTO|
|&emsp;&emsp;productName|产品名称||true|string||
|&emsp;&emsp;productType|产品类型||true|string||
|&emsp;&emsp;status|产品状态（如半成品、成品）||true|string||
|&emsp;&emsp;packagingMethod|产品包装方式（如袋、箱、罐）||false|string||
|&emsp;&emsp;weightPerPiece|每件产品重量（kg）||false|number||
|&emsp;&emsp;piecesPerPallet|每板产品数量||false|integer(int32)||
|&emsp;&emsp;canStack|是否可堆叠||false|boolean||
|&emsp;&emsp;screenMeshId|筛网id||false|integer(int32)||
|&emsp;&emsp;id|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultBoolean|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||boolean||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": true
}
```


## 更新产品


**接口地址**:`/api/products`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据产品ID更新产品信息，允许部分字段更新（产品名称、类型、状态、包装方式、单件重量）</p>



**请求示例**:


```javascript
{
  "productId": 1,
  "productName": "中冰",
  "productType": "白冰糖",
  "status": "成品",
  "packagingMethod": "箱",
  "weightPerPiece": 1.5,
  "piecesPerPallet": 25,
  "canStack": false,
  "id": 0,
  "screenMeshId": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|productUpdateDTO|产品更新请求DTO，用于更新产品信息|body|true|ProductUpdateDTO|ProductUpdateDTO|
|&emsp;&emsp;productId|产品ID||false|integer(int32)||
|&emsp;&emsp;productName|产品名称||false|string||
|&emsp;&emsp;productType|产品类型||false|string||
|&emsp;&emsp;status|产品状态||false|string||
|&emsp;&emsp;packagingMethod|产品包装方式||false|string||
|&emsp;&emsp;weightPerPiece|每件产品重量（kg）||false|number||
|&emsp;&emsp;piecesPerPallet|每板数量||false|integer(int32)||
|&emsp;&emsp;canStack|是否可堆叠||false|boolean||
|&emsp;&emsp;id|||false|integer(int32)||
|&emsp;&emsp;screenMeshId|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultProduct|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||Product|Product|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;productName||string||
|&emsp;&emsp;productType||string||
|&emsp;&emsp;status||string||
|&emsp;&emsp;packagingMethod||string||
|&emsp;&emsp;weightPerPiece||number||
|&emsp;&emsp;piecesPerPallet||integer(int32)||
|&emsp;&emsp;canStack||boolean||
|&emsp;&emsp;screenMeshId||integer(int32)||
|&emsp;&emsp;createdBy||integer(int32)||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;updatedBy||integer(int32)||
|&emsp;&emsp;updatedAt||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"productName": "",
		"productType": "",
		"status": "",
		"packagingMethod": "",
		"weightPerPiece": 0,
		"piecesPerPallet": 0,
		"canStack": true,
		"screenMeshId": 0,
		"createdBy": 0,
		"createdAt": "",
		"updatedBy": 0,
		"updatedAt": ""
	}
}
```


## 查询产品信息


**接口地址**:`/api/products/{id}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据产品ID查询产品信息，返回产品详细信息</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id||path|true|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultProduct|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||Product|Product|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;productName||string||
|&emsp;&emsp;productType||string||
|&emsp;&emsp;status||string||
|&emsp;&emsp;packagingMethod||string||
|&emsp;&emsp;weightPerPiece||number||
|&emsp;&emsp;piecesPerPallet||integer(int32)||
|&emsp;&emsp;canStack||boolean||
|&emsp;&emsp;screenMeshId||integer(int32)||
|&emsp;&emsp;createdBy||integer(int32)||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;updatedBy||integer(int32)||
|&emsp;&emsp;updatedAt||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"productName": "",
		"productType": "",
		"status": "",
		"packagingMethod": "",
		"weightPerPiece": 0,
		"piecesPerPallet": 0,
		"canStack": true,
		"screenMeshId": 0,
		"createdBy": 0,
		"createdAt": "",
		"updatedBy": 0,
		"updatedAt": ""
	}
}
```


## 删除产品


**接口地址**:`/api/products/{id}`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据产品ID删除产品</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|产品ID|path|true|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultVoid|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 根据条件查询半成品名称


**接口地址**:`/api/products/semi-products`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据半成品名称和类型条件查询半成品列表</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|name|半成品名称，支持模糊查询|query|false|string||
|type|半成品类型，例如 '白冰糖' 或 '黄冰糖'|query|false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListProductInfoVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|ProductInfoVO|
|&emsp;&emsp;productId|产品ID|integer(int32)||
|&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;packagingMethod|包装方式|string||
|&emsp;&emsp;productType|产品类型|string||
|&emsp;&emsp;weightPerPiece|每件重量|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"productId": 36,
			"productName": "中冰",
			"packagingMethod": "袋",
			"productType": "白冰糖",
			"weightPerPiece": "40kg"
		}
	]
}
```


## 查询所有半成品名称


**接口地址**:`/api/products/semi-product-names`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>返回所有半成品的产品ID和产品名称列表</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListProductInfoVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|ProductInfoVO|
|&emsp;&emsp;productId|产品ID|integer(int32)||
|&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;packagingMethod|包装方式|string||
|&emsp;&emsp;productType|产品类型|string||
|&emsp;&emsp;weightPerPiece|每件重量|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"productId": 36,
			"productName": "中冰",
			"packagingMethod": "袋",
			"productType": "白冰糖",
			"weightPerPiece": "40kg"
		}
	]
}
```


## 根据名称查询产品信息


**接口地址**:`/api/products/product`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据产品名称支持模糊查询，返回产品列表</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|name|产品名称，支持模糊查询|query|false|string||
|type|产品类型，例如 '白冰糖' 或 '黄冰糖'|query|false|string||
|status|产品状态，例如 '半成品' 或 '成品'|query|false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListProduct|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|Product|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;productName||string||
|&emsp;&emsp;productType||string||
|&emsp;&emsp;status||string||
|&emsp;&emsp;packagingMethod||string||
|&emsp;&emsp;weightPerPiece||number||
|&emsp;&emsp;piecesPerPallet||integer(int32)||
|&emsp;&emsp;canStack||boolean||
|&emsp;&emsp;screenMeshId||integer(int32)||
|&emsp;&emsp;createdBy||integer(int32)||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;updatedBy||integer(int32)||
|&emsp;&emsp;updatedAt||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"id": 0,
			"productName": "",
			"productType": "",
			"status": "",
			"packagingMethod": "",
			"weightPerPiece": 0,
			"piecesPerPallet": 0,
			"canStack": true,
			"screenMeshId": 0,
			"createdBy": 0,
			"createdAt": "",
			"updatedBy": 0,
			"updatedAt": ""
		}
	]
}
```


## 获取产品所有存放的库位


**接口地址**:`/api/products/getProductWarehouse/{id}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>获取产品所有存放的库位</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|产品ID|path|true|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultList库存详情视图|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|库存详情视图|
|&emsp;&emsp;warehouseId|库位ID|integer(int32)||
|&emsp;&emsp;warehouseName|库位名称|string||
|&emsp;&emsp;productId|产品id|integer(int32)||
|&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;entryDate|入库日期|string(date)||
|&emsp;&emsp;totalQuantity|产品库存总板数|integer(int32)||
|&emsp;&emsp;totalPieces|产品库存总件数|integer(int32)||
|&emsp;&emsp;firstEntryDate|产品最早入库时间|string(date-time)||
|&emsp;&emsp;totalWeight|产品库存总重量|number||
|&emsp;&emsp;stockInfo|库存信息|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"warehouseId": 0,
			"warehouseName": "",
			"productId": 0,
			"productName": "",
			"entryDate": "",
			"totalQuantity": 0,
			"totalPieces": 0,
			"firstEntryDate": "",
			"totalWeight": 0,
			"stockInfo": ""
		}
	]
}
```


## 根据条件查询成品名称


**接口地址**:`/api/products/finished-products`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据成品名称和类型条件查询成品列表</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|name|成品名称，支持模糊查询|query|false|string||
|type|成品类型，例如 '白冰糖' 或 '黄冰糖'|query|false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListProductInfoVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|ProductInfoVO|
|&emsp;&emsp;productId|产品ID|integer(int32)||
|&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;packagingMethod|包装方式|string||
|&emsp;&emsp;productType|产品类型|string||
|&emsp;&emsp;weightPerPiece|每件重量|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"productId": 36,
			"productName": "中冰",
			"packagingMethod": "袋",
			"productType": "白冰糖",
			"weightPerPiece": "40kg"
		}
	]
}
```


## 查询所有成品名称


**接口地址**:`/api/products/finished-product-names`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>返回所有成品的产品ID和产品名称列表</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListProductInfoVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|ProductInfoVO|
|&emsp;&emsp;productId|产品ID|integer(int32)||
|&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;packagingMethod|包装方式|string||
|&emsp;&emsp;productType|产品类型|string||
|&emsp;&emsp;weightPerPiece|每件重量|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"productId": 36,
			"productName": "中冰",
			"packagingMethod": "袋",
			"productType": "白冰糖",
			"weightPerPiece": "40kg"
		}
	]
}
```


# 化验验收标准管理


## 更新化验验收标准数据


**接口地址**:`/api/assayGroup/{id}`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据化验验收标准ID更新化验验收标准数据</p>



**请求示例**:


```javascript
{
  "id": 0,
  "relatedProducts": "1",
  "standardName": "中冰",
  "remark": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id||path|true|integer(int32)||
|assayGroupSubmitDTO|化验记录提交DTO|body|true|AssayGroupSubmitDTO|AssayGroupSubmitDTO|
|&emsp;&emsp;id|||false|integer(int32)||
|&emsp;&emsp;relatedProducts|产品ID||false|string||
|&emsp;&emsp;standardName|产品名称||false|string||
|&emsp;&emsp;remark|备注||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultAssayGroup|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||AssayGroup|AssayGroup|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;relatedProducts||string||
|&emsp;&emsp;standardName||string||
|&emsp;&emsp;remark||string||
|&emsp;&emsp;createdBy||integer(int32)||
|&emsp;&emsp;updatedBy||integer(int32)||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;updatedAt||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"relatedProducts": "",
		"standardName": "",
		"remark": "",
		"createdBy": 0,
		"updatedBy": 0,
		"createdAt": "",
		"updatedAt": ""
	}
}
```


## 删除化验验收标准数据


**接口地址**:`/api/assayGroup/{id}`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据ID删除化验验收标准数据</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id||path|true|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultBoolean|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||boolean||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": true
}
```


## 查询化验验收标准数据


**接口地址**:`/api/assayGroup/query`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据查询条件分页查询化验验收标准数据</p>



**请求示例**:


```javascript
{
  "standardName": "冰糖",
  "page": 1,
  "size": 10,
  "id": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|assayGroupQueryDTO|化验验收标准记录查询条件DTO|body|true|AssayGroupQueryDTO|AssayGroupQueryDTO|
|&emsp;&emsp;standardName|标准名称||false|string||
|&emsp;&emsp;page|当前页码||false|integer(int32)||
|&emsp;&emsp;size|每页记录数||false|integer(int32)||
|&emsp;&emsp;id|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResultAssayGroupVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResultAssayGroupVO|PageResultAssayGroupVO|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|AssayGroupVO|
|&emsp;&emsp;&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;relatedProductList|产品列表|array|Product|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productName||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productType||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;status||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;packagingMethod||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;weightPerPiece||number||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;piecesPerPallet||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;canStack||boolean||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;screenMeshId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;createdBy||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;updatedBy||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;updatedAt||string(date-time)||
|&emsp;&emsp;&emsp;&emsp;relatedProducts|产品ID|string||
|&emsp;&emsp;&emsp;&emsp;standardName|标准名称|string||
|&emsp;&emsp;&emsp;&emsp;createdAt|创建时间|string(date)||
|&emsp;&emsp;&emsp;&emsp;createName|创建人|string||
|&emsp;&emsp;&emsp;&emsp;updateName|更新人|string||
|&emsp;&emsp;&emsp;&emsp;updatedAt|修改时间|string(date)||
|&emsp;&emsp;&emsp;&emsp;remark|备注|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": [
			{
				"id": 0,
				"relatedProductList": 1,
				"relatedProducts": "1",
				"standardName": "冰糖",
				"createdAt": "2025-02-27",
				"createName": "",
				"updateName": "",
				"updatedAt": "2025-02-27",
				"remark": ""
			}
		]
	}
}
```


## 保存化验验收标准


**接口地址**:`/api/assayGroup/add`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>保存化验验收标准</p>



**请求示例**:


```javascript
{
  "id": 0,
  "relatedProducts": "1",
  "standardName": "中冰",
  "remark": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|assayGroupSubmitDTO|化验记录提交DTO|body|true|AssayGroupSubmitDTO|AssayGroupSubmitDTO|
|&emsp;&emsp;id|||false|integer(int32)||
|&emsp;&emsp;relatedProducts|产品ID||false|string||
|&emsp;&emsp;standardName|产品名称||false|string||
|&emsp;&emsp;remark|备注||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultBoolean|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||boolean||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": true
}
```


# 库位信息管理


## 修改库位信息


**接口地址**:`/api/warehouse/update`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0,
  "warehouseName": "",
  "maxRows": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|warehouseUpdateDTO|库位更新DTO|body|true|WarehouseUpdateDTO|WarehouseUpdateDTO|
|&emsp;&emsp;id|id||false|integer(int32)||
|&emsp;&emsp;warehouseName|库位名称||false|string||
|&emsp;&emsp;maxRows|最大排数||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultWarehouse|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||Warehouse|Warehouse|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;warehouseName||string||
|&emsp;&emsp;status||string||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;maxCapacity|最大库存量（板）|integer(int32)||
|&emsp;&emsp;curCapacity|当前库存量（板）|integer(int32)||
|&emsp;&emsp;maxRows|最大排数|integer(int32)||
|&emsp;&emsp;warehouseId||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"warehouseName": "",
		"status": "",
		"createdAt": "",
		"maxCapacity": 0,
		"curCapacity": 0,
		"maxRows": 0,
		"warehouseId": ""
	}
}
```


## 设置指定库位为维修状态


**接口地址**:`/api/warehouse/maintain/{id}`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|库位ID|path|true|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultWarehouse|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||Warehouse|Warehouse|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;warehouseName||string||
|&emsp;&emsp;status||string||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;maxCapacity|最大库存量（板）|integer(int32)||
|&emsp;&emsp;curCapacity|当前库存量（板）|integer(int32)||
|&emsp;&emsp;maxRows|最大排数|integer(int32)||
|&emsp;&emsp;warehouseId||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"warehouseName": "",
		"status": "",
		"createdAt": "",
		"maxCapacity": 0,
		"curCapacity": 0,
		"maxRows": 0,
		"warehouseId": ""
	}
}
```


## 新增库位


**接口地址**:`/api/warehouse/create`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "warehouseName": "",
  "maxRows": 0,
  "id": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|warehouseDTO|库位新增DTO|body|true|WarehouseDTO|WarehouseDTO|
|&emsp;&emsp;warehouseName|库位名称||false|string||
|&emsp;&emsp;maxRows|最大排数||false|integer(int32)||
|&emsp;&emsp;id|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultWarehouse|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||Warehouse|Warehouse|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;warehouseName||string||
|&emsp;&emsp;status||string||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;maxCapacity|最大库存量（板）|integer(int32)||
|&emsp;&emsp;curCapacity|当前库存量（板）|integer(int32)||
|&emsp;&emsp;maxRows|最大排数|integer(int32)||
|&emsp;&emsp;warehouseId||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"warehouseName": "",
		"status": "",
		"createdAt": "",
		"maxCapacity": 0,
		"curCapacity": 0,
		"maxRows": 0,
		"warehouseId": ""
	}
}
```


## getWarehouse


**接口地址**:`/api/warehouse/{id}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id||path|true|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultWarehouse|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||Warehouse|Warehouse|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;warehouseName||string||
|&emsp;&emsp;status||string||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;maxCapacity|最大库存量（板）|integer(int32)||
|&emsp;&emsp;curCapacity|当前库存量（板）|integer(int32)||
|&emsp;&emsp;maxRows|最大排数|integer(int32)||
|&emsp;&emsp;warehouseId||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"warehouseName": "",
		"status": "",
		"createdAt": "",
		"maxCapacity": 0,
		"curCapacity": 0,
		"maxRows": 0,
		"warehouseId": ""
	}
}
```


## queryWarehouse


**接口地址**:`/api/warehouse/query`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|name||query|false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListWarehouse|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|Warehouse|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;warehouseName||string||
|&emsp;&emsp;status||string||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;maxCapacity|最大库存量（板）|integer(int32)||
|&emsp;&emsp;curCapacity|当前库存量（板）|integer(int32)||
|&emsp;&emsp;maxRows|最大排数|integer(int32)||
|&emsp;&emsp;warehouseId||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"id": 0,
			"warehouseName": "",
			"status": "",
			"createdAt": "",
			"maxCapacity": 0,
			"curCapacity": 0,
			"maxRows": 0,
			"warehouseId": ""
		}
	]
}
```


## 删除库位


**接口地址**:`/api/warehouse/delete/{id}`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id||path|true|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


# 用户认证模块


## 微信登录


**接口地址**:`/api/auth/wechat-login`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>使用微信临时登录凭证进行登录，返回 JWT Token 和用户基本信息</p>



**请求示例**:


```javascript
{
  "code": "0c3uP8ll2TZU8f4ganml266lf83uP8l2"
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|wechatLoginDTO|微信登录请求DTO|body|true|WechatLoginDTO|WechatLoginDTO|
|&emsp;&emsp;code|微信临时登录凭证||true|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultAuthVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||AuthVO|AuthVO|
|&emsp;&emsp;token|JWT Token|string||
|&emsp;&emsp;name|用户姓名|string||
|&emsp;&emsp;roleCode|角色代码|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"token": "eyJhbGciOiJIUzUxMiJ9.eyJzd...",
		"name": "张三",
		"roleCode": "ADMIN"
	}
}
```


## Web 端管理员登录


**接口地址**:`/api/auth/web-login`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>使用姓名+统一口令登录</p>



**请求示例**:


```javascript
{
  "name": "",
  "password": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|webLoginDTO|WebLoginDTO|body|true|WebLoginDTO|WebLoginDTO|
|&emsp;&emsp;name|||true|string||
|&emsp;&emsp;password|||true|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultAuthVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||AuthVO|AuthVO|
|&emsp;&emsp;token|JWT Token|string||
|&emsp;&emsp;name|用户姓名|string||
|&emsp;&emsp;roleCode|角色代码|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"token": "eyJhbGciOiJIUzUxMiJ9.eyJzd...",
		"name": "张三",
		"roleCode": "ADMIN"
	}
}
```


## 手机号绑定


**接口地址**:`/api/auth/phone-bind`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>微信登录后绑定手机号接口</p>



**请求示例**:


```javascript
{
  "code": "0c3uP8ll2TZU8f4ganml266lf83uP8l2",
  "phoneCode": "暂无"
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|wechatPhoneDTO|微信手机号绑定请求DTO|body|true|WechatPhoneDTO|WechatPhoneDTO|
|&emsp;&emsp;code|微信登录凭证||false|string||
|&emsp;&emsp;phoneCode|加密的微信手机号||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultObject|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 工号验证绑定


**接口地址**:`/api/auth/manual-bind`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>通过工号验证并绑定用户与微信账号</p>



**请求示例**:


```javascript
{
  "employeeId": "EMP001",
  "namePart": "张*三",
  "code": "0c3uP8ll2TZU8f4ganml266lf83uP8l2"
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|employeeVerifyDTO|工号验证绑定请求DTO|body|true|EmployeeVerifyDTO|EmployeeVerifyDTO|
|&emsp;&emsp;employeeId|工号||false|string||
|&emsp;&emsp;namePart|部分姓名验证，例如 '张*三'||false|string||
|&emsp;&emsp;code|微信登录凭证||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultObject|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


# 筛网管理


## 更新筛网


**接口地址**:`/api/screen-mesh/update`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>更新一条筛网记录，需传入筛网ID以及更新后的数据</p>



**请求示例**:


```javascript
{
  "id": 0,
  "meshName": "",
  "description": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|screenMeshUpdateDTO|ScreenMeshUpdateDTO|body|true|ScreenMeshUpdateDTO|ScreenMeshUpdateDTO|
|&emsp;&emsp;id|||false|integer(int32)||
|&emsp;&emsp;meshName|||false|string||
|&emsp;&emsp;description|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultScreenMesh|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||ScreenMesh|ScreenMesh|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;meshName||string||
|&emsp;&emsp;description||string||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;createdBy||integer(int32)||
|&emsp;&emsp;updatedAt||string(date-time)||
|&emsp;&emsp;updatedBy||integer(int32)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"meshName": "",
		"description": "",
		"createdAt": "",
		"createdBy": 0,
		"updatedAt": "",
		"updatedBy": 0
	}
}
```


## 添加筛网


**接口地址**:`/api/screen-mesh/add`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>新增一条筛网记录，包含筛网名称、描述、创建人等信息</p>



**请求示例**:


```javascript
{
  "meshName": "",
  "description": "",
  "id": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|screenMeshCreateDTO|ScreenMeshCreateDTO|body|true|ScreenMeshCreateDTO|ScreenMeshCreateDTO|
|&emsp;&emsp;meshName|||false|string||
|&emsp;&emsp;description|||false|string||
|&emsp;&emsp;id|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultScreenMesh|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||ScreenMesh|ScreenMesh|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;meshName||string||
|&emsp;&emsp;description||string||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;createdBy||integer(int32)||
|&emsp;&emsp;updatedAt||string(date-time)||
|&emsp;&emsp;updatedBy||integer(int32)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"meshName": "",
		"description": "",
		"createdAt": "",
		"createdBy": 0,
		"updatedAt": "",
		"updatedBy": 0
	}
}
```


## 查询筛网列表


**接口地址**:`/api/screen-mesh/list`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据筛网名称模糊查询筛网列表，如果不传参数则返回全部匹配记录</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|meshName|筛网名称，支持模糊查询|query|false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListScreenMesh|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|ScreenMesh|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;meshName||string||
|&emsp;&emsp;description||string||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;createdBy||integer(int32)||
|&emsp;&emsp;updatedAt||string(date-time)||
|&emsp;&emsp;updatedBy||integer(int32)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"id": 0,
			"meshName": "",
			"description": "",
			"createdAt": "",
			"createdBy": 0,
			"updatedAt": "",
			"updatedBy": 0
		}
	]
}
```


## 查询所有筛网


**接口地址**:`/api/screen-mesh/all`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>返回所有筛网信息</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListScreenMesh|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|ScreenMesh|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;meshName||string||
|&emsp;&emsp;description||string||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;createdBy||integer(int32)||
|&emsp;&emsp;updatedAt||string(date-time)||
|&emsp;&emsp;updatedBy||integer(int32)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"id": 0,
			"meshName": "",
			"description": "",
			"createdAt": "",
			"createdBy": 0,
			"updatedAt": "",
			"updatedBy": 0
		}
	]
}
```


## 删除筛网


**接口地址**:`/api/screen-mesh/delete/{id}`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据筛网ID删除筛网记录</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id|筛网ID|path|true|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


# 化验记录管理


## 更新化验记录


**接口地址**:`/api/assay/{id}`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据化验记录ID更新化验数据（更新时保留旧记录，以便历史对比）</p>



**请求示例**:


```javascript
{
  "productId": 1,
  "selectType": 0,
  "relatedId": 0,
  "sampleDate": "2025-02-24",
  "colorValue": 75.5,
  "reducingSugar": 1,
  "dryWeight": 1,
  "conductivityAsh": 0.05,
  "sucrose": 98,
  "insolubleImpurity": 50,
  "phValue": 6.2
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id||path|true|integer(int32)||
|assaySubmitDTO|化验记录提交DTO，用于导入或更新化验记录|body|true|AssaySubmitDTO|AssaySubmitDTO|
|&emsp;&emsp;productId|产品ID||false|integer(int32)||
|&emsp;&emsp;selectType|选择类型：1产品默认，2添加的验收标准||false|integer(int32)||
|&emsp;&emsp;relatedId|验收标准id||false|integer(int32)||
|&emsp;&emsp;sampleDate|采样日期||true|string(date)||
|&emsp;&emsp;colorValue|色值||false|number||
|&emsp;&emsp;reducingSugar|还原糖分||false|number||
|&emsp;&emsp;dryWeight|干燥失重||false|number||
|&emsp;&emsp;conductivityAsh|电导灰分||false|number||
|&emsp;&emsp;sucrose|蔗糖分||false|number||
|&emsp;&emsp;insolubleImpurity|不溶于水杂质||false|number||
|&emsp;&emsp;phValue|pH值||false|number||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultAssayVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||AssayVO|AssayVO|
|&emsp;&emsp;id|化验记录ID|integer(int32)||
|&emsp;&emsp;productId|产品ID|integer(int32)||
|&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;sampleDate|采样日期|string(date)||
|&emsp;&emsp;colorValue|色值|number||
|&emsp;&emsp;reducingSugar|还原糖分|number||
|&emsp;&emsp;dryWeight|干燥失重|number||
|&emsp;&emsp;conductivityAsh|电导灰分|number||
|&emsp;&emsp;sucrose|蔗糖分|number||
|&emsp;&emsp;insolubleImpurity|不溶于水杂质|number||
|&emsp;&emsp;phValue|pH值|number||
|&emsp;&emsp;testerName|化验人名称|string||
|&emsp;&emsp;isQualified|是否检验合格|string||
|&emsp;&emsp;version|版本号|integer(int32)||
|&emsp;&emsp;qualifiedStandards|JSON格式的合格标准列表|string||
|&emsp;&emsp;createdAt|创建时间|string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 1,
		"productId": 1,
		"productName": "中冰",
		"sampleDate": "2025-02-27",
		"colorValue": 0,
		"reducingSugar": 0,
		"dryWeight": 0,
		"conductivityAsh": 0,
		"sucrose": 0,
		"insolubleImpurity": 0,
		"phValue": 0,
		"testerName": "李四",
		"isQualified": "合格/不合格",
		"version": 0,
		"qualifiedStandards": "",
		"createdAt": ""
	}
}
```


## 删除化验记录


**接口地址**:`/api/assay/{id}`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据化验记录ID删除化验记录</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id||path|true|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultBoolean|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||boolean||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": true
}
```


## 查询化验记录


**接口地址**:`/api/assay/query`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据查询条件分页查询化验记录</p>



**请求示例**:


```javascript
{
  "productName": "中冰",
  "productId": "1",
  "sampleDate": "2025-01-01",
  "isQualified": "合格不合格",
  "startDate": "2025-01-01",
  "endDate": "2025-03-31",
  "testerName": "1",
  "version": 1,
  "page": 1,
  "size": 10,
  "id": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|assayQueryDTO|化验记录查询条件DTO|body|true|AssayQueryDTO|AssayQueryDTO|
|&emsp;&emsp;productName|产品名称，支持模糊查询||false|string||
|&emsp;&emsp;productId|产品ID||false|string||
|&emsp;&emsp;sampleDate|化验日期||false|string||
|&emsp;&emsp;isQualified|是否合格||false|string||
|&emsp;&emsp;startDate|查询起始日期||false|string(date)||
|&emsp;&emsp;endDate|查询结束日期||false|string(date)||
|&emsp;&emsp;testerName|化验人员姓名，支持模糊查询||false|string||
|&emsp;&emsp;version|化验数据版本号||false|integer(int32)||
|&emsp;&emsp;page|当前页码||false|integer(int32)||
|&emsp;&emsp;size|每页记录数||false|integer(int32)||
|&emsp;&emsp;id|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResultAssayVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResultAssayVO|PageResultAssayVO|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|AssayVO|
|&emsp;&emsp;&emsp;&emsp;id|化验记录ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productId|产品ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;&emsp;&emsp;sampleDate|采样日期|string(date)||
|&emsp;&emsp;&emsp;&emsp;colorValue|色值|number||
|&emsp;&emsp;&emsp;&emsp;reducingSugar|还原糖分|number||
|&emsp;&emsp;&emsp;&emsp;dryWeight|干燥失重|number||
|&emsp;&emsp;&emsp;&emsp;conductivityAsh|电导灰分|number||
|&emsp;&emsp;&emsp;&emsp;sucrose|蔗糖分|number||
|&emsp;&emsp;&emsp;&emsp;insolubleImpurity|不溶于水杂质|number||
|&emsp;&emsp;&emsp;&emsp;phValue|pH值|number||
|&emsp;&emsp;&emsp;&emsp;testerName|化验人名称|string||
|&emsp;&emsp;&emsp;&emsp;isQualified|是否检验合格|string||
|&emsp;&emsp;&emsp;&emsp;version|版本号|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;qualifiedStandards|JSON格式的合格标准列表|string||
|&emsp;&emsp;&emsp;&emsp;createdAt|创建时间|string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": [
			{
				"id": 1,
				"productId": 1,
				"productName": "中冰",
				"sampleDate": "2025-02-27",
				"colorValue": 0,
				"reducingSugar": 0,
				"dryWeight": 0,
				"conductivityAsh": 0,
				"sucrose": 0,
				"insolubleImpurity": 0,
				"phValue": 0,
				"testerName": "李四",
				"isQualified": "合格/不合格",
				"version": 0,
				"qualifiedStandards": "",
				"createdAt": ""
			}
		]
	}
}
```


## 导入化验记录


**接口地址**:`/api/assay/import`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>批量导入化验记录</p>



**请求示例**:


```javascript
[
  {
    "productId": 1,
    "selectType": 0,
    "relatedId": 0,
    "sampleDate": "2025-02-24",
    "colorValue": 75.5,
    "reducingSugar": 1,
    "dryWeight": 1,
    "conductivityAsh": 0.05,
    "sucrose": 98,
    "insolubleImpurity": 50,
    "phValue": 6.2
  }
]
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|assaySubmitDTOs|化验记录提交DTO，用于导入或更新化验记录|body|true|array|AssaySubmitDTO|
|&emsp;&emsp;productId|产品ID||false|integer(int32)||
|&emsp;&emsp;selectType|选择类型：1产品默认，2添加的验收标准||false|integer(int32)||
|&emsp;&emsp;relatedId|验收标准id||false|integer(int32)||
|&emsp;&emsp;sampleDate|采样日期||true|string(date)||
|&emsp;&emsp;colorValue|色值||false|number||
|&emsp;&emsp;reducingSugar|还原糖分||false|number||
|&emsp;&emsp;dryWeight|干燥失重||false|number||
|&emsp;&emsp;conductivityAsh|电导灰分||false|number||
|&emsp;&emsp;sucrose|蔗糖分||false|number||
|&emsp;&emsp;insolubleImpurity|不溶于水杂质||false|number||
|&emsp;&emsp;phValue|pH值||false|number||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultBoolean|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||boolean||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": true
}
```


## 检查化验记录是否存在


**接口地址**:`/api/assay/exists`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据产品id和日期检查化验记录是否存在</p>



**请求示例**:


```javascript
{
  "productId": 0,
  "entryDate": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|assayCheckDTO|化验记录存在性查询DTO|body|true|AssayCheckDTO|AssayCheckDTO|
|&emsp;&emsp;productId|||false|integer(int32)||
|&emsp;&emsp;entryDate|||false|string(date)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultBoolean|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||boolean||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": true
}
```


# 质量标准管理


## 更新质量标准


**接口地址**:`/api/quality-standards/update/{id}`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据ID修改质量标准</p>



**请求示例**:


```javascript
{
  "productType": "",
  "standardName": "",
  "colorMin": 0,
  "colorMax": 0,
  "reducingSugarMin": 0,
  "reducingSugarMax": 0,
  "dryWeightMin": 0,
  "dryWeightMax": 0,
  "conductivityAshMin": 0,
  "conductivityAshMax": 0,
  "sucroseMin": 0,
  "sucroseMax": 0,
  "insolubleImpurityMax": 0,
  "insolubleImpurityMin": 0,
  "phMin": 0,
  "phMax": 0,
  "id": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id||path|true|integer(int32)||
|qualityStandardDTO|QualityStandardDTO|body|true|QualityStandardDTO|QualityStandardDTO|
|&emsp;&emsp;productType|||false|string||
|&emsp;&emsp;standardName|||false|string||
|&emsp;&emsp;colorMin|||false|number||
|&emsp;&emsp;colorMax|||false|number||
|&emsp;&emsp;reducingSugarMin|||false|number||
|&emsp;&emsp;reducingSugarMax|||false|number||
|&emsp;&emsp;dryWeightMin|||false|number||
|&emsp;&emsp;dryWeightMax|||false|number||
|&emsp;&emsp;conductivityAshMin|||false|number||
|&emsp;&emsp;conductivityAshMax|||false|number||
|&emsp;&emsp;sucroseMin|||false|number||
|&emsp;&emsp;sucroseMax|||false|number||
|&emsp;&emsp;insolubleImpurityMax|||false|number||
|&emsp;&emsp;insolubleImpurityMin|||false|number||
|&emsp;&emsp;phMin|||false|number||
|&emsp;&emsp;phMax|||false|number||
|&emsp;&emsp;id|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultQualityStandard|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||QualityStandard|QualityStandard|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;productType||string||
|&emsp;&emsp;standardName||string||
|&emsp;&emsp;colorMin||number||
|&emsp;&emsp;colorMax||number||
|&emsp;&emsp;reducingSugarMin||number||
|&emsp;&emsp;reducingSugarMax||number||
|&emsp;&emsp;dryWeightMin||number||
|&emsp;&emsp;dryWeightMax||number||
|&emsp;&emsp;conductivityAshMin||number||
|&emsp;&emsp;conductivityAshMax||number||
|&emsp;&emsp;sucroseMin||number||
|&emsp;&emsp;sucroseMax||number||
|&emsp;&emsp;insolubleImpurityMax||number||
|&emsp;&emsp;insolubleImpurityMin||number||
|&emsp;&emsp;phMin||number||
|&emsp;&emsp;phMax||number||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"productType": "",
		"standardName": "",
		"colorMin": 0,
		"colorMax": 0,
		"reducingSugarMin": 0,
		"reducingSugarMax": 0,
		"dryWeightMin": 0,
		"dryWeightMax": 0,
		"conductivityAshMin": 0,
		"conductivityAshMax": 0,
		"sucroseMin": 0,
		"sucroseMax": 0,
		"insolubleImpurityMax": 0,
		"insolubleImpurityMin": 0,
		"phMin": 0,
		"phMax": 0
	}
}
```


## 新增质量标准


**接口地址**:`/api/quality-standards/add`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>创建新的质量标准</p>



**请求示例**:


```javascript
{
  "productType": "",
  "standardName": "",
  "colorMin": 0,
  "colorMax": 0,
  "reducingSugarMin": 0,
  "reducingSugarMax": 0,
  "dryWeightMin": 0,
  "dryWeightMax": 0,
  "conductivityAshMin": 0,
  "conductivityAshMax": 0,
  "sucroseMin": 0,
  "sucroseMax": 0,
  "insolubleImpurityMax": 0,
  "insolubleImpurityMin": 0,
  "phMin": 0,
  "phMax": 0,
  "id": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|qualityStandardDTO|QualityStandardDTO|body|true|QualityStandardDTO|QualityStandardDTO|
|&emsp;&emsp;productType|||false|string||
|&emsp;&emsp;standardName|||false|string||
|&emsp;&emsp;colorMin|||false|number||
|&emsp;&emsp;colorMax|||false|number||
|&emsp;&emsp;reducingSugarMin|||false|number||
|&emsp;&emsp;reducingSugarMax|||false|number||
|&emsp;&emsp;dryWeightMin|||false|number||
|&emsp;&emsp;dryWeightMax|||false|number||
|&emsp;&emsp;conductivityAshMin|||false|number||
|&emsp;&emsp;conductivityAshMax|||false|number||
|&emsp;&emsp;sucroseMin|||false|number||
|&emsp;&emsp;sucroseMax|||false|number||
|&emsp;&emsp;insolubleImpurityMax|||false|number||
|&emsp;&emsp;insolubleImpurityMin|||false|number||
|&emsp;&emsp;phMin|||false|number||
|&emsp;&emsp;phMax|||false|number||
|&emsp;&emsp;id|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultQualityStandard|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||QualityStandard|QualityStandard|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;productType||string||
|&emsp;&emsp;standardName||string||
|&emsp;&emsp;colorMin||number||
|&emsp;&emsp;colorMax||number||
|&emsp;&emsp;reducingSugarMin||number||
|&emsp;&emsp;reducingSugarMax||number||
|&emsp;&emsp;dryWeightMin||number||
|&emsp;&emsp;dryWeightMax||number||
|&emsp;&emsp;conductivityAshMin||number||
|&emsp;&emsp;conductivityAshMax||number||
|&emsp;&emsp;sucroseMin||number||
|&emsp;&emsp;sucroseMax||number||
|&emsp;&emsp;insolubleImpurityMax||number||
|&emsp;&emsp;insolubleImpurityMin||number||
|&emsp;&emsp;phMin||number||
|&emsp;&emsp;phMax||number||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"productType": "",
		"standardName": "",
		"colorMin": 0,
		"colorMax": 0,
		"reducingSugarMin": 0,
		"reducingSugarMax": 0,
		"dryWeightMin": 0,
		"dryWeightMax": 0,
		"conductivityAshMin": 0,
		"conductivityAshMax": 0,
		"sucroseMin": 0,
		"sucroseMax": 0,
		"insolubleImpurityMax": 0,
		"insolubleImpurityMin": 0,
		"phMin": 0,
		"phMax": 0
	}
}
```


## 查询质量标准


**接口地址**:`/api/quality-standards/{id}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据ID获取质量标准</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id||path|true|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultQualityStandardVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||QualityStandardVO|QualityStandardVO|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;standardName||string||
|&emsp;&emsp;productType||string||
|&emsp;&emsp;colorMin||number||
|&emsp;&emsp;colorMax||number||
|&emsp;&emsp;reducingSugarMin||number||
|&emsp;&emsp;reducingSugarMax||number||
|&emsp;&emsp;dryWeightMin||number||
|&emsp;&emsp;dryWeightMax||number||
|&emsp;&emsp;conductivityAshMin||number||
|&emsp;&emsp;conductivityAshMax||number||
|&emsp;&emsp;sucroseMin||number||
|&emsp;&emsp;sucroseMax||number||
|&emsp;&emsp;insolubleImpurityMax||number||
|&emsp;&emsp;insolubleImpurityMin||number||
|&emsp;&emsp;phMin||number||
|&emsp;&emsp;phMax||number||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"standardName": "",
		"productType": "",
		"colorMin": 0,
		"colorMax": 0,
		"reducingSugarMin": 0,
		"reducingSugarMax": 0,
		"dryWeightMin": 0,
		"dryWeightMax": 0,
		"conductivityAshMin": 0,
		"conductivityAshMax": 0,
		"sucroseMin": 0,
		"sucroseMax": 0,
		"insolubleImpurityMax": 0,
		"insolubleImpurityMin": 0,
		"phMin": 0,
		"phMax": 0
	}
}
```


## 查询所有质量标准


**接口地址**:`/api/quality-standards/list`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>可按产品类型筛选</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|productType||query|false|string||
|standardName||query|false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListQualityStandardVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|QualityStandardVO|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;standardName||string||
|&emsp;&emsp;productType||string||
|&emsp;&emsp;colorMin||number||
|&emsp;&emsp;colorMax||number||
|&emsp;&emsp;reducingSugarMin||number||
|&emsp;&emsp;reducingSugarMax||number||
|&emsp;&emsp;dryWeightMin||number||
|&emsp;&emsp;dryWeightMax||number||
|&emsp;&emsp;conductivityAshMin||number||
|&emsp;&emsp;conductivityAshMax||number||
|&emsp;&emsp;sucroseMin||number||
|&emsp;&emsp;sucroseMax||number||
|&emsp;&emsp;insolubleImpurityMax||number||
|&emsp;&emsp;insolubleImpurityMin||number||
|&emsp;&emsp;phMin||number||
|&emsp;&emsp;phMax||number||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"id": 0,
			"standardName": "",
			"productType": "",
			"colorMin": 0,
			"colorMax": 0,
			"reducingSugarMin": 0,
			"reducingSugarMax": 0,
			"dryWeightMin": 0,
			"dryWeightMax": 0,
			"conductivityAshMin": 0,
			"conductivityAshMax": 0,
			"sucroseMin": 0,
			"sucroseMax": 0,
			"insolubleImpurityMax": 0,
			"insolubleImpurityMin": 0,
			"phMin": 0,
			"phMax": 0
		}
	]
}
```


## 删除质量标准


**接口地址**:`/api/quality-standards/delete/{id}`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据ID删除质量标准</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|id||path|true|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


# 库存详情查询


## 库存详情查询


**接口地址**:`/api/inventory/summary`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据库位ID和产品名称查询库存详情</p>



**请求示例**:


```javascript
{
  "warehouseId": 0,
  "page": 0,
  "size": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|inventoryQueryDTO|库存详情查询DTO|body|true|InventoryQueryDTO|InventoryQueryDTO|
|&emsp;&emsp;warehouseId|库位ID||false|integer(int32)||
|&emsp;&emsp;page|页码||false|integer(int32)||
|&emsp;&emsp;size|每页数量||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResult库存详情视图|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResult库存详情视图|PageResult库存详情视图|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|库存详情视图|
|&emsp;&emsp;&emsp;&emsp;warehouseId|库位ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;warehouseName|库位名称|string||
|&emsp;&emsp;&emsp;&emsp;productId|产品id|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;&emsp;&emsp;entryDate|入库日期|string(date)||
|&emsp;&emsp;&emsp;&emsp;totalQuantity|产品库存总板数|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;totalPieces|产品库存总件数|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;firstEntryDate|产品最早入库时间|string(date-time)||
|&emsp;&emsp;&emsp;&emsp;totalWeight|产品库存总重量|number||
|&emsp;&emsp;&emsp;&emsp;stockInfo|库存信息|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": [
			{
				"warehouseId": 0,
				"warehouseName": "",
				"productId": 0,
				"productName": "",
				"entryDate": "",
				"totalQuantity": 0,
				"totalPieces": 0,
				"firstEntryDate": "",
				"totalWeight": 0,
				"stockInfo": ""
			}
		]
	}
}
```


## 库存容量百分比查询


**接口地址**:`/api/inventory/query`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>查询库存容量百分比</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|ids||query|true|array|integer|
|page||query|true|integer(int32)||
|size||query|true|integer(int32)||
|warehouseName||query|false|string||
|status||query|false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResultVWarehouseCapacity|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResultVWarehouseCapacity|PageResultVWarehouseCapacity|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|VWarehouseCapacity|
|&emsp;&emsp;&emsp;&emsp;warehouseId|库位ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;warehouseName|库位名称|string||
|&emsp;&emsp;&emsp;&emsp;status|库位状态|string||
|&emsp;&emsp;&emsp;&emsp;curCapacity|当前库存量|number||
|&emsp;&emsp;&emsp;&emsp;maxCapacity|最大库存量|number||
|&emsp;&emsp;&emsp;&emsp;capacityPercentage|库位容量百分比|number||
|&emsp;&emsp;&emsp;&emsp;firstEntryDate|最早入库日期|string(date)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": [
			{
				"warehouseId": 0,
				"warehouseName": "",
				"status": "",
				"curCapacity": 0,
				"maxCapacity": 0,
				"capacityPercentage": 0,
				"firstEntryDate": ""
			}
		]
	}
}
```


## 库存容量百分比查询


**接口地址**:`/api/inventory/query`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>查询库存容量百分比</p>



**请求示例**:


```javascript
{
  "ids": [],
  "page": 0,
  "size": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|outStockBatchQueryDTO|批量出库查询DTO|body|true|OutStockBatchQueryDTO|OutStockBatchQueryDTO|
|&emsp;&emsp;ids|||false|array|integer(int32)|
|&emsp;&emsp;page|||false|integer(int32)||
|&emsp;&emsp;size|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResultVWarehouseCapacity|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResultVWarehouseCapacity|PageResultVWarehouseCapacity|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|VWarehouseCapacity|
|&emsp;&emsp;&emsp;&emsp;warehouseId|库位ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;warehouseName|库位名称|string||
|&emsp;&emsp;&emsp;&emsp;status|库位状态|string||
|&emsp;&emsp;&emsp;&emsp;curCapacity|当前库存量|number||
|&emsp;&emsp;&emsp;&emsp;maxCapacity|最大库存量|number||
|&emsp;&emsp;&emsp;&emsp;capacityPercentage|库位容量百分比|number||
|&emsp;&emsp;&emsp;&emsp;firstEntryDate|最早入库日期|string(date)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": [
			{
				"warehouseId": 0,
				"warehouseName": "",
				"status": "",
				"curCapacity": 0,
				"maxCapacity": 0,
				"capacityPercentage": 0,
				"firstEntryDate": ""
			}
		]
	}
}
```


## 查询所有存有符合标准的产品的库位


**接口地址**:`/api/inventory/qualified-warehouses`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据产品名称、标准名称、筛网ID、入库日期查询存有符合条件产品的库位</p>



**请求示例**:


```javascript
{
  "productName": "",
  "standardNames": "",
  "screenMeshId": 0,
  "startDate": "",
  "endDate": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|outProductQueryDTO|库存查询请求DTO|body|true|OutProductQueryDTO|OutProductQueryDTO|
|&emsp;&emsp;productName|产品名称||false|string||
|&emsp;&emsp;standardNames|标准名称列表||false|string||
|&emsp;&emsp;screenMeshId|筛网 ID||false|integer(int32)||
|&emsp;&emsp;startDate|查询开始日期||false|string(date)||
|&emsp;&emsp;endDate|查询结束日期||false|string(date)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListOutWarehouseVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|OutWarehouseVO|
|&emsp;&emsp;warehouseId|库位ID|integer(int32)||
|&emsp;&emsp;warehouseName|库位名称|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"warehouseId": 0,
			"warehouseName": ""
		}
	]
}
```


## 库位中库存详情查询


**接口地址**:`/api/inventory/qualified-inventory/{warehouseId}`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据库位ID和条件查询库位中库存详情</p>



**请求示例**:


```javascript
{
  "productName": "",
  "standardNames": "",
  "screenMeshId": 0,
  "startDate": "",
  "endDate": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|warehouseId||path|true|integer(int32)||
|outProductQueryDTO|库存查询请求DTO|body|true|OutProductQueryDTO|OutProductQueryDTO|
|&emsp;&emsp;productName|产品名称||false|string||
|&emsp;&emsp;standardNames|标准名称列表||false|string||
|&emsp;&emsp;screenMeshId|筛网 ID||false|integer(int32)||
|&emsp;&emsp;startDate|查询开始日期||false|string(date)||
|&emsp;&emsp;endDate|查询结束日期||false|string(date)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListOutProductVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|OutProductVO|
|&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;warehouseName|库位名称|string||
|&emsp;&emsp;sampleDate|检测日期（入库日期）|string(date)||
|&emsp;&emsp;productType|产品类型|string||
|&emsp;&emsp;standardNames|标准名称|string||
|&emsp;&emsp;meshName|筛网名称|string||
|&emsp;&emsp;side|库位左/右侧|string||
|&emsp;&emsp;rowNumber|排数|integer(int32)||
|&emsp;&emsp;layer|层数|integer(int32)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"productName": "",
			"warehouseName": "",
			"sampleDate": "",
			"productType": "",
			"standardNames": "",
			"meshName": "",
			"side": "",
			"rowNumber": 0,
			"layer": 0
		}
	]
}
```


## 库存容量百分比查询


**接口地址**:`/api/inventory/warehouses`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>查询库存容量百分比</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListVWarehouseCapacity|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|VWarehouseCapacity|
|&emsp;&emsp;warehouseId|库位ID|integer(int32)||
|&emsp;&emsp;warehouseName|库位名称|string||
|&emsp;&emsp;status|库位状态|string||
|&emsp;&emsp;curCapacity|当前库存量|number||
|&emsp;&emsp;maxCapacity|最大库存量|number||
|&emsp;&emsp;capacityPercentage|库位容量百分比|number||
|&emsp;&emsp;firstEntryDate|最早入库日期|string(date)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"warehouseId": 0,
			"warehouseName": "",
			"status": "",
			"curCapacity": 0,
			"maxCapacity": 0,
			"capacityPercentage": 0,
			"firstEntryDate": ""
		}
	]
}
```


## 产品所有库存


**接口地址**:`/api/inventory/stock`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>产品所有库存</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|productStatus||query|true|string||
|productName||query|false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultList库存详情视图|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|库存详情视图|
|&emsp;&emsp;warehouseId|库位ID|integer(int32)||
|&emsp;&emsp;warehouseName|库位名称|string||
|&emsp;&emsp;productId|产品id|integer(int32)||
|&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;entryDate|入库日期|string(date)||
|&emsp;&emsp;totalQuantity|产品库存总板数|integer(int32)||
|&emsp;&emsp;totalPieces|产品库存总件数|integer(int32)||
|&emsp;&emsp;firstEntryDate|产品最早入库时间|string(date-time)||
|&emsp;&emsp;totalWeight|产品库存总重量|number||
|&emsp;&emsp;stockInfo|库存信息|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"warehouseId": 0,
			"warehouseName": "",
			"productId": 0,
			"productName": "",
			"entryDate": "",
			"totalQuantity": 0,
			"totalPieces": 0,
			"firstEntryDate": "",
			"totalWeight": 0,
			"stockInfo": ""
		}
	]
}
```


# 入库管理


## 批量查询入库记录


**接口地址**:`/api/in-stock/query`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据查询条件分页查询入库记录</p>



**请求示例**:


```javascript
{
  "productName": "冰糖",
  "warehouseName": "101",
  "startDate": "2025-01-01",
  "endDate": "2025-03-31",
  "operatorName": "张三",
  "page": 1,
  "size": 10
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|inStockQueryDTO|入库记录查询条件DTO|body|true|InStockQueryDTO|InStockQueryDTO|
|&emsp;&emsp;productName|产品名称，支持模糊查询||false|string||
|&emsp;&emsp;warehouseName|仓库名称||false|string||
|&emsp;&emsp;startDate|查询起始日期||false|string(date)||
|&emsp;&emsp;endDate|查询结束日期||false|string(date)||
|&emsp;&emsp;operatorName|操作员名称，支持模糊查询||false|string||
|&emsp;&emsp;page|当前页码||false|integer(int32)||
|&emsp;&emsp;size|每页记录数||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResultInStockVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResultInStockVO|PageResultInStockVO|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|InStockVO|
|&emsp;&emsp;&emsp;&emsp;id|入库记录ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;&emsp;&emsp;warehouseName|库位名称|string||
|&emsp;&emsp;&emsp;&emsp;unit|出库单位：0板1件|string||
|&emsp;&emsp;&emsp;&emsp;quantity|数量|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;totalWeight|总重量（kg）|number||
|&emsp;&emsp;&emsp;&emsp;entryDate|入库日期|string(date)||
|&emsp;&emsp;&emsp;&emsp;operator|操作员名称|string||
|&emsp;&emsp;&emsp;&emsp;meshName|筛网规格名称|string||
|&emsp;&emsp;&emsp;&emsp;semiProductRecords|半成品记录|string||
|&emsp;&emsp;&emsp;&emsp;assayId|化验记录id|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;sampleDate|采样日期|string(date)||
|&emsp;&emsp;&emsp;&emsp;colorValue|色值|number||
|&emsp;&emsp;&emsp;&emsp;reducingSugar|还原糖分|number||
|&emsp;&emsp;&emsp;&emsp;dryWeight|干燥失重|number||
|&emsp;&emsp;&emsp;&emsp;conductivityAsh|电导灰分|number||
|&emsp;&emsp;&emsp;&emsp;sucrose|蔗糖分|number||
|&emsp;&emsp;&emsp;&emsp;insolubleImpurity|不溶于水杂质|number||
|&emsp;&emsp;&emsp;&emsp;phValue|pH值|number||
|&emsp;&emsp;&emsp;&emsp;testedBy||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;testerName|化验人名称|string||
|&emsp;&emsp;&emsp;&emsp;isQualified|是否检验合格|string||
|&emsp;&emsp;&emsp;&emsp;qualifiedStandards|JSON格式的合格标准|string||
|&emsp;&emsp;&emsp;&emsp;createdAt|创建时间|string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": [
			{
				"id": 1,
				"productName": "中冰",
				"warehouseName": "101",
				"unit": "0",
				"quantity": 250,
				"totalWeight": 3750,
				"entryDate": "2025-02-25",
				"operator": "张三",
				"meshName": "大筛网",
				"semiProductRecords": "5",
				"assayId": 0,
				"sampleDate": "2025-02-27",
				"colorValue": 0,
				"reducingSugar": 0,
				"dryWeight": 0,
				"conductivityAsh": 0,
				"sucrose": 0,
				"insolubleImpurity": 0,
				"phValue": 0,
				"testedBy": 0,
				"testerName": "李四",
				"isQualified": "合格/不合格",
				"qualifiedStandards": "",
				"createdAt": "2025-02-25T16:00:00Z"
			}
		]
	}
}
```


## 新增成品入库记录


**接口地址**:`/api/in-stock/add`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>新增入库记录，包含产品、仓库、半成品信息、筛网规格及多个库存位置</p>



**请求示例**:


```javascript
{
  "productId": 57,
  "warehouseName": "101",
  "entryDate": "2025-05-01",
  "quantity": 30,
  "unit": "30",
  "side": "左",
  "screenMeshId": 2,
  "returnInStockFlag": "",
  "semiRecords": [
    {
      "semiProductId": 1,
      "semiPalletCodeId": 1,
      "productName": "",
      "productionDate": "2025-01-01",
      "warehouseId": 1,
      "fromPreparePool": false,
      "cycleNo": 1,
      "quantity": 20,
      "unit": "30",
      "useAssay": false
    }
  ]
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|inStockRequestDTO|入库请求DTO|body|true|InStockRequestDTO|InStockRequestDTO|
|&emsp;&emsp;productId|产品ID||true|integer(int32)||
|&emsp;&emsp;warehouseName|仓库ID||true|string||
|&emsp;&emsp;entryDate|入库日期||true|string(date)||
|&emsp;&emsp;quantity|数量（板）||true|integer(int32)||
|&emsp;&emsp;unit|单位0板1件（板/件）||true|string||
|&emsp;&emsp;side|库位左/右列，默认为左||false|string||
|&emsp;&emsp;screenMeshId|筛网规格ID||false|integer(int32)||
|&emsp;&emsp;returnInStockFlag|标记是否退货入库：0不是1是||false|string||
|&emsp;&emsp;semiRecords|半成品DTO，包含半成品产品id、半成品生产日期、生产该批成品使用的原料数量||false|array|SemiRecordDTO|
|&emsp;&emsp;&emsp;&emsp;semiProductId|半成品ID||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;semiPalletCodeId|半成品托盘ID||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productName|产品名称||false|string||
|&emsp;&emsp;&emsp;&emsp;productionDate|生产日期||false|string(date)||
|&emsp;&emsp;&emsp;&emsp;warehouseId|库位||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;fromPreparePool|是否来自备料池||false|boolean||
|&emsp;&emsp;&emsp;&emsp;cycleNo|半成品来源循环号||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;quantity|使用的半成品数量||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;unit|单位0板1件（板/件）||true|string||
|&emsp;&emsp;&emsp;&emsp;useAssay|是否套用该半成品的化验数据||false|boolean||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultInVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||InVO|InVO|
|&emsp;&emsp;remainingQuantity|多余的产品数量（板）|integer(int32)||
|&emsp;&emsp;message|详细信息|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"remainingQuantity": 0,
		"message": ""
	}
}
```


# 操作日志管理


## 查询操作日志


**接口地址**:`/api/logs/query`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>分页查询操作日志，可根据表名、操作类型、操作人、时间段筛选</p>



**请求示例**:


```javascript
{
  "tableName": "",
  "operationType": "",
  "operator": "",
  "startTime": "",
  "endTime": "",
  "page": 0,
  "size": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|operationLogQueryDTO|OperationLogQueryDTO|body|true|OperationLogQueryDTO|OperationLogQueryDTO|
|&emsp;&emsp;tableName|||false|string||
|&emsp;&emsp;operationType|||false|string||
|&emsp;&emsp;operator|||false|string||
|&emsp;&emsp;startTime|||false|string(date-time)||
|&emsp;&emsp;endTime|||false|string(date-time)||
|&emsp;&emsp;page|||false|integer(int32)||
|&emsp;&emsp;size|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResultOperationLog|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResultOperationLog|PageResultOperationLog|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|OperationLog|
|&emsp;&emsp;&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;tableName||string||
|&emsp;&emsp;&emsp;&emsp;operationType||string||
|&emsp;&emsp;&emsp;&emsp;operator||string||
|&emsp;&emsp;&emsp;&emsp;operationTime||string(date-time)||
|&emsp;&emsp;&emsp;&emsp;changedFields||string||
|&emsp;&emsp;&emsp;&emsp;oldData||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": [
			{
				"id": 0,
				"tableName": "",
				"operationType": "",
				"operator": "",
				"operationTime": "",
				"changedFields": "",
				"oldData": ""
			}
		]
	}
}
```


# 托盘码


## 托盘码列表查询


**接口地址**:`/api/pallet-codes`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>托盘码管理页分页查询接口</p>



**请求示例**:


```javascript
{
  "code": "",
  "productName": "",
  "productType": "",
  "productStatus": "",
  "productionDateStart": "",
  "productionDateEnd": "",
  "pageNum": 0,
  "pageSize": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|palletCodeQueryDTO|PalletCodeQueryDTO|body|true|PalletCodeQueryDTO|PalletCodeQueryDTO|
|&emsp;&emsp;code|||false|string||
|&emsp;&emsp;productName|||false|string||
|&emsp;&emsp;productType|||false|string||
|&emsp;&emsp;productStatus|||false|string||
|&emsp;&emsp;productionDateStart|||false|string(date)||
|&emsp;&emsp;productionDateEnd|||false|string(date)||
|&emsp;&emsp;pageNum|||false|integer(int64)||
|&emsp;&emsp;pageSize|||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResultPalletCodePageVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResultPalletCodePageVO|PageResultPalletCodePageVO|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|PalletCodePageVO|
|&emsp;&emsp;&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;code||string||
|&emsp;&emsp;&emsp;&emsp;status||string||
|&emsp;&emsp;&emsp;&emsp;productName||string||
|&emsp;&emsp;&emsp;&emsp;productType||string||
|&emsp;&emsp;&emsp;&emsp;productStatus||string||
|&emsp;&emsp;&emsp;&emsp;productionDate||string(date)||
|&emsp;&emsp;&emsp;&emsp;screenMeshName||string||
|&emsp;&emsp;&emsp;&emsp;assayId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;&emsp;&emsp;createdByName||string||
|&emsp;&emsp;&emsp;&emsp;updatedAt||string(date-time)||
|&emsp;&emsp;&emsp;&emsp;updatedByName||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": [
			{
				"id": 0,
				"code": "",
				"status": "",
				"productName": "",
				"productType": "",
				"productStatus": "",
				"productionDate": "",
				"screenMeshName": "",
				"assayId": 0,
				"createdAt": "",
				"createdByName": "",
				"updatedAt": "",
				"updatedByName": ""
			}
		]
	}
}
```


## 创建托盘调拨任务


**接口地址**:`/api/pallet-codes/transfer/create`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>扫码一个或多个在库托盘码，创建托盘级调拨任务</p>



**请求示例**:


```javascript
{
  "items": [
    {
      "code": "",
      "targetWarehouseName": "",
      "targetSide": "",
      "remark": ""
    }
  ]
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|createTransferTaskDTO|创建托盘调拨任务请求|body|true|CreateTransferTaskDTO|CreateTransferTaskDTO|
|&emsp;&emsp;items|调拨任务明细列表||true|array|CreateTransferTaskItemDTO|
|&emsp;&emsp;&emsp;&emsp;code|托盘码||true|string||
|&emsp;&emsp;&emsp;&emsp;targetWarehouseName|目标仓库名称||true|string||
|&emsp;&emsp;&emsp;&emsp;targetSide|目标侧，左/右，默认左||false|string||
|&emsp;&emsp;&emsp;&emsp;remark|备注，可选||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultVoid|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 确认托盘调拨


**接口地址**:`/api/pallet-codes/transfer/confirm`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>批量确认托盘级调拨任务</p>



**请求示例**:


```javascript
{
  "codes": [],
  "remark": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|confirmTransferBatchDTO|确认托盘调拨请求|body|true|ConfirmTransferBatchDTO|ConfirmTransferBatchDTO|
|&emsp;&emsp;codes|托盘码列表||true|array|string|
|&emsp;&emsp;remark|备注，可选||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultVoid|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 成品任务绑定半成品明细


**接口地址**:`/api/pallet-codes/tasks/semi-bind`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>为成品入库任务绑定使用的半成品托盘明细（全量覆盖）</p>



**请求示例**:


```javascript
{
  "code": "",
  "items": [
    {
      "semiPalletCode": "",
      "quantity": 0,
      "unit": "",
      "useAssay": true
    }
  ]
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|bindTaskSemiItemsDTO|BindTaskSemiItemsDTO|body|true|BindTaskSemiItemsDTO|BindTaskSemiItemsDTO|
|&emsp;&emsp;code|成品托盘码||true|string||
|&emsp;&emsp;items|半成品明细列表（全量覆盖）||true|array|TaskSemiItemDTO|
|&emsp;&emsp;&emsp;&emsp;semiPalletCode|半成品托盘码||true|string||
|&emsp;&emsp;&emsp;&emsp;quantity|数量||true|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;unit|单位：0板，1件||true|string||
|&emsp;&emsp;&emsp;&emsp;useAssay|是否套用化验数据||false|boolean||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListTaskSemiItemVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|TaskSemiItemVO|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;semiPalletCode||string||
|&emsp;&emsp;semiProductId||integer(int32)||
|&emsp;&emsp;semiProductName||string||
|&emsp;&emsp;productionDate||string(date)||
|&emsp;&emsp;quantity||integer(int32)||
|&emsp;&emsp;unit||string||
|&emsp;&emsp;useAssay||boolean||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"id": 0,
			"semiPalletCode": "",
			"semiProductId": 0,
			"semiProductName": "",
			"productionDate": "",
			"quantity": 0,
			"unit": "",
			"useAssay": true
		}
	]
}
```


## 托盘入库任务列表


**接口地址**:`/api/pallet-codes/tasks/list`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>托盘任务分页查询接口</p>



**请求示例**:


```javascript
{
  "code": "",
  "taskType": "",
  "bizScene": "",
  "status": "",
  "productName": "",
  "productType": "",
  "productStatus": "",
  "productionDateStart": "",
  "productionDateEnd": "",
  "pageNum": 0,
  "pageSize": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|palletTaskQueryDTO|托盘入库任务分页查询条件|body|true|PalletTaskQueryDTO|PalletTaskQueryDTO|
|&emsp;&emsp;code|托盘码（精确匹配）||false|string||
|&emsp;&emsp;taskType|任务类型：SEMI_IN / FINISH_IN / OUT，可选||false|string||
|&emsp;&emsp;bizScene|任务业务场景：DIRECT_OUT / PREPARE_CONSUMED / FINISH_OUT，可选||false|string||
|&emsp;&emsp;status|任务状态：PENDING / CONFIRMED / CANCELED，可选||false|string||
|&emsp;&emsp;productName|产品名称（模糊）||false|string||
|&emsp;&emsp;productType|产品类型（黄/白冰糖等），可选||false|string||
|&emsp;&emsp;productStatus|产品状态：半成品/成品，可选||false|string||
|&emsp;&emsp;productionDateStart|生产日期起||false|string(date)||
|&emsp;&emsp;productionDateEnd|生产日期止||false|string(date)||
|&emsp;&emsp;pageNum|页码，从1开始||false|integer(int64)||
|&emsp;&emsp;pageSize|每页大小，默认10||false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResultPalletTaskPageVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResultPalletTaskPageVO|PageResultPalletTaskPageVO|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|PalletTaskPageVO|
|&emsp;&emsp;&emsp;&emsp;taskId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;taskType||string||
|&emsp;&emsp;&emsp;&emsp;bizScene||string||
|&emsp;&emsp;&emsp;&emsp;taskStatus||string||
|&emsp;&emsp;&emsp;&emsp;palletCodeId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;code||string||
|&emsp;&emsp;&emsp;&emsp;targetWarehouseId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;targetWarehouseName||string||
|&emsp;&emsp;&emsp;&emsp;targetSide||string||
|&emsp;&emsp;&emsp;&emsp;productId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productName||string||
|&emsp;&emsp;&emsp;&emsp;productType||string||
|&emsp;&emsp;&emsp;&emsp;productStatus||string||
|&emsp;&emsp;&emsp;&emsp;productionDate||string(date)||
|&emsp;&emsp;&emsp;&emsp;screenMeshId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;screenMeshName||string||
|&emsp;&emsp;&emsp;&emsp;hasSemiItems||boolean||
|&emsp;&emsp;&emsp;&emsp;semiItemCount||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;semiItems||array|TaskSemiItemVO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiPalletCode||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiProductId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiProductName||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productionDate||string(date)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;quantity||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;unit||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;useAssay||boolean||
|&emsp;&emsp;&emsp;&emsp;assayId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;createdBy||string||
|&emsp;&emsp;&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;&emsp;&emsp;confirmedBy||string||
|&emsp;&emsp;&emsp;&emsp;confirmedAt||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": [
			{
				"taskId": 0,
				"taskType": "",
				"bizScene": "",
				"taskStatus": "",
				"palletCodeId": 0,
				"code": "",
				"targetWarehouseId": 0,
				"targetWarehouseName": "",
				"targetSide": "",
				"productId": 0,
				"productName": "",
				"productType": "",
				"productStatus": "",
				"productionDate": "",
				"screenMeshId": 0,
				"screenMeshName": "",
				"hasSemiItems": true,
				"semiItemCount": 0,
				"semiItems": [
					{
						"id": 0,
						"semiPalletCode": "",
						"semiProductId": 0,
						"semiProductName": "",
						"productionDate": "",
						"quantity": 0,
						"unit": "",
						"useAssay": true
					}
				],
				"assayId": 0,
				"createdBy": "",
				"createdAt": "",
				"confirmedBy": "",
				"confirmedAt": ""
			}
		]
	}
}
```


## 托盘任务确认入库


**接口地址**:`/api/pallet-codes/tasks/confirm`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>批量确认托盘入库任务</p>



**请求示例**:


```javascript
{
  "items": [
    {
      "code": "",
      "warehouseName": "",
      "entryDate": "",
      "side": "",
      "quantity": 0,
      "unit": "",
      "remark": ""
    }
  ]
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|confirmPalletInBatchDTO|托盘入库确认批量请求|body|true|ConfirmPalletInBatchDTO|ConfirmPalletInBatchDTO|
|&emsp;&emsp;items|待确认入库的托盘列表||true|array|ConfirmPalletInItemDTO|
|&emsp;&emsp;&emsp;&emsp;code|托盘码，例如 BT0A3ZK||true|string||
|&emsp;&emsp;&emsp;&emsp;warehouseName|入库仓库名称，对应 warehouse.warehouse_name||true|string||
|&emsp;&emsp;&emsp;&emsp;entryDate|入库日期，默认使用任务的生产日期||false|string(date)||
|&emsp;&emsp;&emsp;&emsp;side|优先存放侧，左/右，默认左||false|string||
|&emsp;&emsp;&emsp;&emsp;quantity|本次入库数量（板/件），不传默认1||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;unit|单位：0=板，1=件，不传默认0||false|string||
|&emsp;&emsp;&emsp;&emsp;remark|备注，可选||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListInVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|InVO|
|&emsp;&emsp;remainingQuantity|多余的产品数量（板）|integer(int32)||
|&emsp;&emsp;message|详细信息|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"remainingQuantity": 0,
			"message": ""
		}
	]
}
```


## 批量取消入库任务


**接口地址**:`/api/pallet-codes/tasks/cancel`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>按托盘码取消当前轮次待处理入库任务，并释放托盘回 FREE</p>



**请求示例**:


```javascript
{
  "codes": [],
  "remark": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|cancelPalletBatchDTO|托盘批量作废/取消请求|body|true|CancelPalletBatchDTO|CancelPalletBatchDTO|
|&emsp;&emsp;codes|托盘码列表||true|array|string|
|&emsp;&emsp;remark|备注，可选||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultVoid|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 创建半成品转入备料池任务


**接口地址**:`/api/pallet-codes/semi/prepare/create`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>扫码一个或多个半成品托盘码，创建转入备料池任务</p>



**请求示例**:


```javascript
{
  "codes": [],
  "remark": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|createSemiPrepareTaskDTO|创建半成品转入备料池任务请求|body|true|CreateSemiPrepareTaskDTO|CreateSemiPrepareTaskDTO|
|&emsp;&emsp;codes|半成品托盘码列表||true|array|string|
|&emsp;&emsp;remark|备注，建议填写备料池实际位置||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultVoid|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 确认半成品转入备料池


**接口地址**:`/api/pallet-codes/semi/prepare/confirm`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>批量确认半成品转入备料池任务</p>



**请求示例**:


```javascript
{
  "codes": [],
  "remark": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|confirmSemiPrepareBatchDTO|确认半成品转入备料池请求|body|true|ConfirmSemiPrepareBatchDTO|ConfirmSemiPrepareBatchDTO|
|&emsp;&emsp;codes|半成品托盘码列表||true|array|string|
|&emsp;&emsp;remark|备注，可选||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultVoid|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 创建半成品普通出库任务


**接口地址**:`/api/pallet-codes/semi/out/create`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>扫码一个或多个半成品托盘码，创建普通出库任务</p>



**请求示例**:


```javascript
{
  "codes": [],
  "remark": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|createSemiOutTaskDTO|创建半成品普通出库任务请求|body|true|CreateSemiOutTaskDTO|CreateSemiOutTaskDTO|
|&emsp;&emsp;codes|半成品托盘码列表||true|array|string|
|&emsp;&emsp;remark|备注，可选||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultVoid|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 确认半成品普通出库


**接口地址**:`/api/pallet-codes/semi/out/confirm`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>批量确认半成品普通出库任务</p>



**请求示例**:


```javascript
{
  "codes": [],
  "remark": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|confirmSemiOutBatchDTO|确认半成品普通出库请求|body|true|ConfirmSemiOutBatchDTO|ConfirmSemiOutBatchDTO|
|&emsp;&emsp;codes|半成品托盘码列表||true|array|string|
|&emsp;&emsp;remark|备注，可选||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultVoid|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 确认半成品消耗


**接口地址**:`/api/pallet-codes/semi/consume/confirm`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>批量确认备料池中的半成品托盘已最终消耗</p>



**请求示例**:


```javascript
{
  "codes": [],
  "remark": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|confirmSemiConsumeBatchDTO|确认半成品备料池消耗请求|body|true|ConfirmSemiConsumeBatchDTO|ConfirmSemiConsumeBatchDTO|
|&emsp;&emsp;codes|半成品托盘码列表||true|array|string|
|&emsp;&emsp;remark|备注，可选||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultVoid|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 批量作废托盘码


**接口地址**:`/api/pallet-codes/invalid`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>仅允许将空闲托盘码置为 INVALID</p>



**请求示例**:


```javascript
{
  "codes": [],
  "remark": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|cancelPalletBatchDTO|托盘批量作废/取消请求|body|true|CancelPalletBatchDTO|CancelPalletBatchDTO|
|&emsp;&emsp;codes|托盘码列表||true|array|string|
|&emsp;&emsp;remark|备注，可选||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultVoid|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 批量生成托盘码


**接口地址**:`/api/pallet-codes/generate`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据数量批量生成托盘码</p>



**请求示例**:


```javascript
{
  "count": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|generatePalletCodeDTO|批量生成托盘码请求|body|true|GeneratePalletCodeDTO|GeneratePalletCodeDTO|
|&emsp;&emsp;count|||true|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": []
}
```


## 批量删除托盘流转记录


**接口地址**:`/api/pallet-codes/flows/delete`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>仅允许删除超过180天且非当前轮次的历史流转记录</p>



**请求示例**:


```javascript
{
  "ids": []
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|deletePalletFlowBatchDTO|批量删除托盘流转记录请求|body|true|DeletePalletFlowBatchDTO|DeletePalletFlowBatchDTO|
|&emsp;&emsp;ids|流转记录ID列表||true|array|integer(int64)|


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultVoid|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 创建成品出库任务


**接口地址**:`/api/pallet-codes/finish/out/create`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>扫码一个或多个成品托盘码，创建成品出库任务</p>



**请求示例**:


```javascript
{
  "codes": [],
  "remark": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|createFinishOutTaskDTO|创建成品出库任务请求|body|true|CreateFinishOutTaskDTO|CreateFinishOutTaskDTO|
|&emsp;&emsp;codes|成品托盘码列表||true|array|string|
|&emsp;&emsp;remark|备注，可选||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultVoid|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 确认成品出库


**接口地址**:`/api/pallet-codes/finish/out/confirm`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>批量确认成品出库任务</p>



**请求示例**:


```javascript
{
  "codes": [],
  "remark": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|confirmFinishOutBatchDTO|确认成品出库请求|body|true|ConfirmFinishOutBatchDTO|ConfirmFinishOutBatchDTO|
|&emsp;&emsp;codes|成品托盘码列表||true|array|string|
|&emsp;&emsp;remark|备注，可选||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultVoid|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 扫码绑定托盘并创建入库任务


**接口地址**:`/api/pallet-codes/bind`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>小程序/PC 扫描托盘二维码后，绑定产品信息并创建入库任务（不处理化验记录）</p>



**请求示例**:


```javascript
{
  "code": "",
  "productId": 0,
  "productStatus": "",
  "productionDate": "",
  "quantity": 20,
  "unit": "0",
  "remark": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|bindPalletTaskDTO|BindPalletTaskDTO|body|true|BindPalletTaskDTO|BindPalletTaskDTO|
|&emsp;&emsp;code|扫码得到的托盘码，例如 BT0A3ZK||true|string||
|&emsp;&emsp;productId|绑定的产品ID||true|integer(int32)||
|&emsp;&emsp;productStatus|产品状态：半成品/成品||true|string||
|&emsp;&emsp;productionDate|生产日期||true|string(date)||
|&emsp;&emsp;quantity|数量，默认为1板；件数需单独输入||false|integer(int32)||
|&emsp;&emsp;unit|单位0板1件（板/件）||true|string||
|&emsp;&emsp;remark|备注，可选||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPalletBindResultVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PalletBindResultVO|PalletBindResultVO|
|&emsp;&emsp;palletCodeId||integer(int32)||
|&emsp;&emsp;code||string||
|&emsp;&emsp;palletStatus||string||
|&emsp;&emsp;taskId||integer(int32)||
|&emsp;&emsp;taskType||string||
|&emsp;&emsp;taskStatus||string||
|&emsp;&emsp;productId||integer(int32)||
|&emsp;&emsp;productName||string||
|&emsp;&emsp;productType||string||
|&emsp;&emsp;productStatus||string||
|&emsp;&emsp;screenMeshId||integer(int32)||
|&emsp;&emsp;screenMeshName||string||
|&emsp;&emsp;productionDate||string(date)||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;remark||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"palletCodeId": 0,
		"code": "",
		"palletStatus": "",
		"taskId": 0,
		"taskType": "",
		"taskStatus": "",
		"productId": 0,
		"productName": "",
		"productType": "",
		"productStatus": "",
		"screenMeshId": 0,
		"screenMeshName": "",
		"productionDate": "",
		"createdAt": "",
		"remark": ""
	}
}
```


## 托盘码二维码


**接口地址**:`/api/pallet-codes/{code}/qrcode`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>生成托盘码对应的二维码图片(PNG)</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|code||path|true|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK||


**响应参数**:


暂无


**响应示例**:
```javascript

```


## 托盘库存位置


**接口地址**:`/api/pallet-codes/{code}/inventory`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据托盘码查询当前库存位置</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|code||path|true|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPalletInventoryVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PalletInventoryVO|PalletInventoryVO|
|&emsp;&emsp;warehouseName||string||
|&emsp;&emsp;side||string||
|&emsp;&emsp;rowNumber||integer(int32)||
|&emsp;&emsp;layer||integer(int32)||
|&emsp;&emsp;quantity||integer(int32)||
|&emsp;&emsp;unit||boolean||
|&emsp;&emsp;inStockTime||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"warehouseName": "",
		"side": "",
		"rowNumber": 0,
		"layer": 0,
		"quantity": 0,
		"unit": true,
		"inStockTime": ""
	}
}
```


## 托盘流转明细


**接口地址**:`/api/pallet-codes/{code}/flows`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>按托盘码和循环号查询流转时间线</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|code||path|true|string||
|cycleNo||query|true|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListPalletFlowDetailVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|PalletFlowDetailVO|
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;cycleNo||integer(int32)||
|&emsp;&emsp;taskId||integer(int32)||
|&emsp;&emsp;operationType||string||
|&emsp;&emsp;operationName||string||
|&emsp;&emsp;operationTime||string(date-time)||
|&emsp;&emsp;operatorId||integer(int32)||
|&emsp;&emsp;operatorName||string||
|&emsp;&emsp;productId||integer(int32)||
|&emsp;&emsp;productName||string||
|&emsp;&emsp;productStatus||string||
|&emsp;&emsp;assayId||integer(int32)||
|&emsp;&emsp;fromWarehouseId||integer(int32)||
|&emsp;&emsp;fromWarehouseName||string||
|&emsp;&emsp;fromSide||string||
|&emsp;&emsp;fromRowNumber||integer(int32)||
|&emsp;&emsp;fromLayer||integer(int32)||
|&emsp;&emsp;toWarehouseId||integer(int32)||
|&emsp;&emsp;toWarehouseName||string||
|&emsp;&emsp;toSide||string||
|&emsp;&emsp;toRowNumber||integer(int32)||
|&emsp;&emsp;toLayer||integer(int32)||
|&emsp;&emsp;remark||string||
|&emsp;&emsp;extData||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"id": 0,
			"cycleNo": 0,
			"taskId": 0,
			"operationType": "",
			"operationName": "",
			"operationTime": "",
			"operatorId": 0,
			"operatorName": "",
			"productId": 0,
			"productName": "",
			"productStatus": "",
			"assayId": 0,
			"fromWarehouseId": 0,
			"fromWarehouseName": "",
			"fromSide": "",
			"fromRowNumber": 0,
			"fromLayer": 0,
			"toWarehouseId": 0,
			"toWarehouseName": "",
			"toSide": "",
			"toRowNumber": 0,
			"toLayer": 0,
			"remark": "",
			"extData": ""
		}
	]
}
```


## 托盘流转轮次分页


**接口地址**:`/api/pallet-codes/{code}/flows/cycles`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>按托盘码分页查询历史循环轮次摘要</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|code||path|true|string||
|pageNum||query|false|integer(int64)||
|pageSize||query|false|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResultPalletFlowCyclePageVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResultPalletFlowCyclePageVO|PageResultPalletFlowCyclePageVO|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|PalletFlowCyclePageVO|
|&emsp;&emsp;&emsp;&emsp;cycleNo||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productName||string||
|&emsp;&emsp;&emsp;&emsp;productStatus||string||
|&emsp;&emsp;&emsp;&emsp;startTime||string(date-time)||
|&emsp;&emsp;&emsp;&emsp;endTime||string(date-time)||
|&emsp;&emsp;&emsp;&emsp;flowCount||integer(int64)||
|&emsp;&emsp;&emsp;&emsp;isCurrentCycle||boolean||
|&emsp;&emsp;&emsp;&emsp;isEnded||boolean||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": [
			{
				"cycleNo": 0,
				"productId": 0,
				"productName": "",
				"productStatus": "",
				"startTime": "",
				"endTime": "",
				"flowCount": 0,
				"isCurrentCycle": true,
				"isEnded": true
			}
		]
	}
}
```


## 托盘化验数据


**接口地址**:`/api/pallet-codes/{code}/assay`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据托盘码查询化验数据</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|code||path|true|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPalletAssayVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PalletAssayVO|PalletAssayVO|
|&emsp;&emsp;productName||string||
|&emsp;&emsp;sampleDate||string(date)||
|&emsp;&emsp;colorValue||number||
|&emsp;&emsp;reducingSugar||number||
|&emsp;&emsp;dryWeight||number||
|&emsp;&emsp;conductivityAsh||number||
|&emsp;&emsp;sucrose||number||
|&emsp;&emsp;insolubleImpurity||number||
|&emsp;&emsp;phValue||number||
|&emsp;&emsp;testerName||string||
|&emsp;&emsp;isQualified||string||
|&emsp;&emsp;qualifiedStandards||string||
|&emsp;&emsp;createdAt||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"productName": "",
		"sampleDate": "",
		"colorValue": 0,
		"reducingSugar": 0,
		"dryWeight": 0,
		"conductivityAsh": 0,
		"sucrose": 0,
		"insolubleImpurity": 0,
		"phValue": 0,
		"testerName": "",
		"isQualified": "",
		"qualifiedStandards": "",
		"createdAt": ""
	}
}
```


## 解析托盘码


**接口地址**:`/api/pallet-codes/parse`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>小程序扫码后解析托盘码并返回基础信息</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|code||query|true|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPalletCodeInfoVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PalletCodeInfoVO|PalletCodeInfoVO|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;code||string||
|&emsp;&emsp;status||string||
|&emsp;&emsp;productName||string||
|&emsp;&emsp;productStatus||string||
|&emsp;&emsp;productionDate||string(date)||
|&emsp;&emsp;screenMeshName||string||
|&emsp;&emsp;assayId||integer(int32)||
|&emsp;&emsp;createdAt||string(date-time)||
|&emsp;&emsp;createdBy||string||
|&emsp;&emsp;updatedAt||string(date-time)||
|&emsp;&emsp;updatedBy||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"code": "",
		"status": "",
		"productName": "",
		"productStatus": "",
		"productionDate": "",
		"screenMeshName": "",
		"assayId": 0,
		"createdAt": "",
		"createdBy": "",
		"updatedAt": "",
		"updatedBy": ""
	}
}
```


# 半成品记录管理


## 半成品栈式入库


**接口地址**:`/api/semi-products/stack-in`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>在特殊库位新增一条半成品记录，记录产品名称、数量等信息。记录由当前登录用户录入。</p>



**请求示例**:


```javascript
{
  "productId": 57,
  "warehouseName": "101",
  "entryDate": "2025-05-01",
  "quantity": 30,
  "unit": "30",
  "side": "左",
  "screenMeshId": 2,
  "returnInStockFlag": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|addSemiProductRecordDTO|入库请求DTO|body|true|AddSemiProductRecordDTO|AddSemiProductRecordDTO|
|&emsp;&emsp;productId|产品ID||true|integer(int32)||
|&emsp;&emsp;warehouseName|仓库ID||true|string||
|&emsp;&emsp;entryDate|入库日期||true|string(date)||
|&emsp;&emsp;quantity|数量（板）||true|integer(int32)||
|&emsp;&emsp;unit|单位0板1件（板/件）||true|string||
|&emsp;&emsp;side|库位左/右列，默认为左||false|string||
|&emsp;&emsp;screenMeshId|筛网规格ID||false|integer(int32)||
|&emsp;&emsp;returnInStockFlag|标记是否退货入库：0不是1是||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultInVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||InVO|InVO|
|&emsp;&emsp;remainingQuantity|多余的产品数量（板）|integer(int32)||
|&emsp;&emsp;message|详细信息|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"remainingQuantity": 0,
		"message": ""
	}
}
```


## 查询半成品记录列表


**接口地址**:`/api/semi-products/records`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据查询条件（产品名称、操作日期、操作员姓名）查询半成品记录列表</p>



**请求示例**:


```javascript
{
  "productName": "冰糖",
  "warehouseName": "101",
  "startDate": "2025-01-01",
  "endDate": "2025-03-31",
  "operatorName": "张三",
  "page": 1,
  "size": 10
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|semiProductRecordDTO|半成品记录查询条件DTO|body|true|SemiProductRecordDTO|SemiProductRecordDTO|
|&emsp;&emsp;productName|产品名称，支持模糊查询||false|string||
|&emsp;&emsp;warehouseName|仓库名称||false|string||
|&emsp;&emsp;startDate|查询起始日期||false|string(date)||
|&emsp;&emsp;endDate|查询结束日期||false|string(date)||
|&emsp;&emsp;operatorName|操作员名称，支持模糊查询||false|string||
|&emsp;&emsp;page|当前页码||false|integer(int32)||
|&emsp;&emsp;size|每页记录数||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResultRecordDetailVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResultRecordDetailVO|PageResultRecordDetailVO|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|RecordDetailVO|
|&emsp;&emsp;&emsp;&emsp;id|记录ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;&emsp;&emsp;warehouseName|库位名称|string||
|&emsp;&emsp;&emsp;&emsp;quantity|数量（板）|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;totalWeight|总重量（kg）|number||
|&emsp;&emsp;&emsp;&emsp;operationDate|操作日期|string(date)||
|&emsp;&emsp;&emsp;&emsp;createdAt|创建时间|string(date-time)||
|&emsp;&emsp;&emsp;&emsp;operator|操作员姓名|string||
|&emsp;&emsp;&emsp;&emsp;meshName|筛网名称|string||
|&emsp;&emsp;&emsp;&emsp;assayId|化验记录id|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;sampleDate|采样日期|string(date)||
|&emsp;&emsp;&emsp;&emsp;colorValue|色值|number||
|&emsp;&emsp;&emsp;&emsp;reducingSugar|还原糖分|number||
|&emsp;&emsp;&emsp;&emsp;dryWeight|干燥失重|number||
|&emsp;&emsp;&emsp;&emsp;conductivityAsh|电导灰分|number||
|&emsp;&emsp;&emsp;&emsp;sucrose|蔗糖分|number||
|&emsp;&emsp;&emsp;&emsp;insolubleImpurity|不溶于水杂质|number||
|&emsp;&emsp;&emsp;&emsp;phValue|pH值|number||
|&emsp;&emsp;&emsp;&emsp;testerName|化验人名称|string||
|&emsp;&emsp;&emsp;&emsp;isQualified|是否检验合格|string||
|&emsp;&emsp;&emsp;&emsp;qualifiedStandards|JSON格式的合格标准列表|string||
|&emsp;&emsp;&emsp;&emsp;unit|出库单位：0板1件|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": [
			{
				"id": 1,
				"productName": "正中冰",
				"warehouseName": "101",
				"quantity": 100,
				"totalWeight": 1500,
				"operationDate": "2025-02-24",
				"createdAt": "",
				"operator": "张三",
				"meshName": "",
				"assayId": 0,
				"sampleDate": "2025-02-27",
				"colorValue": 0,
				"reducingSugar": 0,
				"dryWeight": 0,
				"conductivityAsh": 0,
				"sucrose": 0,
				"insolubleImpurity": 0,
				"phValue": 0,
				"testerName": "李四",
				"isQualified": "合格/不合格",
				"qualifiedStandards": "",
				"unit": "0"
			}
		]
	}
}
```


## 批量查询半成品记录详情


**接口地址**:`/api/semi-products/batch-get`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据多个记录ID批量查询半成品记录的详细信息</p>



**请求示例**:


```javascript
{
  "ids": []
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|batchGetSemiProductRecordDTO|半成品记录id列表|body|true|BatchGetSemiProductRecordDTO|BatchGetSemiProductRecordDTO|
|&emsp;&emsp;ids|||false|array|integer(int32)|


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListRecordDetailVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|RecordDetailVO|
|&emsp;&emsp;id|记录ID|integer(int32)||
|&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;warehouseName|库位名称|string||
|&emsp;&emsp;quantity|数量（板）|integer(int32)||
|&emsp;&emsp;totalWeight|总重量（kg）|number||
|&emsp;&emsp;operationDate|操作日期|string(date)||
|&emsp;&emsp;createdAt|创建时间|string(date-time)||
|&emsp;&emsp;operator|操作员姓名|string||
|&emsp;&emsp;meshName|筛网名称|string||
|&emsp;&emsp;assayId|化验记录id|integer(int32)||
|&emsp;&emsp;sampleDate|采样日期|string(date)||
|&emsp;&emsp;colorValue|色值|number||
|&emsp;&emsp;reducingSugar|还原糖分|number||
|&emsp;&emsp;dryWeight|干燥失重|number||
|&emsp;&emsp;conductivityAsh|电导灰分|number||
|&emsp;&emsp;sucrose|蔗糖分|number||
|&emsp;&emsp;insolubleImpurity|不溶于水杂质|number||
|&emsp;&emsp;phValue|pH值|number||
|&emsp;&emsp;testerName|化验人名称|string||
|&emsp;&emsp;isQualified|是否检验合格|string||
|&emsp;&emsp;qualifiedStandards|JSON格式的合格标准列表|string||
|&emsp;&emsp;unit|出库单位：0板1件|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"id": 1,
			"productName": "正中冰",
			"warehouseName": "101",
			"quantity": 100,
			"totalWeight": 1500,
			"operationDate": "2025-02-24",
			"createdAt": "",
			"operator": "张三",
			"meshName": "",
			"assayId": 0,
			"sampleDate": "2025-02-27",
			"colorValue": 0,
			"reducingSugar": 0,
			"dryWeight": 0,
			"conductivityAsh": 0,
			"sucrose": 0,
			"insolubleImpurity": 0,
			"phValue": 0,
			"testerName": "李四",
			"isQualified": "合格/不合格",
			"qualifiedStandards": "",
			"unit": "0"
		}
	]
}
```


## 半成品入库


**接口地址**:`/api/semi-products/add`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>新增一条半成品记录，记录产品名称、数量等信息。记录由当前登录用户录入。</p>



**请求示例**:


```javascript
{
  "productId": 57,
  "warehouseName": "101",
  "entryDate": "2025-05-01",
  "quantity": 30,
  "unit": "30",
  "side": "左",
  "screenMeshId": 2,
  "returnInStockFlag": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|addSemiProductRecordDTO|入库请求DTO|body|true|AddSemiProductRecordDTO|AddSemiProductRecordDTO|
|&emsp;&emsp;productId|产品ID||true|integer(int32)||
|&emsp;&emsp;warehouseName|仓库ID||true|string||
|&emsp;&emsp;entryDate|入库日期||true|string(date)||
|&emsp;&emsp;quantity|数量（板）||true|integer(int32)||
|&emsp;&emsp;unit|单位0板1件（板/件）||true|string||
|&emsp;&emsp;side|库位左/右列，默认为左||false|string||
|&emsp;&emsp;screenMeshId|筛网规格ID||false|integer(int32)||
|&emsp;&emsp;returnInStockFlag|标记是否退货入库：0不是1是||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultInVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||InVO|InVO|
|&emsp;&emsp;remainingQuantity|多余的产品数量（板）|integer(int32)||
|&emsp;&emsp;message|详细信息|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"remainingQuantity": 0,
		"message": ""
	}
}
```


## 查询当前用户的半成品记录


**接口地址**:`/api/semi-products/my-records`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>根据操作日期（可选）查询当前登录用户关联的半成品记录列表</p>



**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|dto|半成品记录查询条件DTO|query|true|RecordQueryDTO|RecordQueryDTO|
|&emsp;&emsp;operationDate|操作日期，格式为yyyyMMdd，非必填||false|string(date)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultListRecordDetailVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||array|RecordDetailVO|
|&emsp;&emsp;id|记录ID|integer(int32)||
|&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;warehouseName|库位名称|string||
|&emsp;&emsp;quantity|数量（板）|integer(int32)||
|&emsp;&emsp;totalWeight|总重量（kg）|number||
|&emsp;&emsp;operationDate|操作日期|string(date)||
|&emsp;&emsp;createdAt|创建时间|string(date-time)||
|&emsp;&emsp;operator|操作员姓名|string||
|&emsp;&emsp;meshName|筛网名称|string||
|&emsp;&emsp;assayId|化验记录id|integer(int32)||
|&emsp;&emsp;sampleDate|采样日期|string(date)||
|&emsp;&emsp;colorValue|色值|number||
|&emsp;&emsp;reducingSugar|还原糖分|number||
|&emsp;&emsp;dryWeight|干燥失重|number||
|&emsp;&emsp;conductivityAsh|电导灰分|number||
|&emsp;&emsp;sucrose|蔗糖分|number||
|&emsp;&emsp;insolubleImpurity|不溶于水杂质|number||
|&emsp;&emsp;phValue|pH值|number||
|&emsp;&emsp;testerName|化验人名称|string||
|&emsp;&emsp;isQualified|是否检验合格|string||
|&emsp;&emsp;qualifiedStandards|JSON格式的合格标准列表|string||
|&emsp;&emsp;unit|出库单位：0板1件|string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": [
		{
			"id": 1,
			"productName": "正中冰",
			"warehouseName": "101",
			"quantity": 100,
			"totalWeight": 1500,
			"operationDate": "2025-02-24",
			"createdAt": "",
			"operator": "张三",
			"meshName": "",
			"assayId": 0,
			"sampleDate": "2025-02-27",
			"colorValue": 0,
			"reducingSugar": 0,
			"dryWeight": 0,
			"conductivityAsh": 0,
			"sucrose": 0,
			"insolubleImpurity": 0,
			"phValue": 0,
			"testerName": "李四",
			"isQualified": "合格/不合格",
			"qualifiedStandards": "",
			"unit": "0"
		}
	]
}
```


# 员工名册管理


## 更新员工信息


**接口地址**:`/api/employee/update`


**请求方式**:`PUT`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:<p>根据ID修改员工信息，可选更新姓名、手机号、部门、职位、状态、角色等</p>



**请求示例**:


```javascript
{
  "id": 0,
  "employeeId": "",
  "name": "",
  "mobile": "",
  "department": "",
  "position": "",
  "status": "",
  "roleCode": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|employeeUpdateDTO|EmployeeUpdateDTO|body|true|EmployeeUpdateDTO|EmployeeUpdateDTO|
|&emsp;&emsp;id|||true|integer(int32)||
|&emsp;&emsp;employeeId|||false|string||
|&emsp;&emsp;name|||false|string||
|&emsp;&emsp;mobile|||false|string||
|&emsp;&emsp;department|||false|string||
|&emsp;&emsp;position|||false|string||
|&emsp;&emsp;status|||false|string||
|&emsp;&emsp;roleCode|||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultEmployeeRoster|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||EmployeeRoster|EmployeeRoster|
|&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;employeeId||string||
|&emsp;&emsp;name||string||
|&emsp;&emsp;mobile||string||
|&emsp;&emsp;department||string||
|&emsp;&emsp;position||string||
|&emsp;&emsp;status||string||
|&emsp;&emsp;roleCode||string||
|&emsp;&emsp;createdAt||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"employeeId": "",
		"name": "",
		"mobile": "",
		"department": "",
		"position": "",
		"status": "",
		"roleCode": "",
		"createdAt": ""
	}
}
```


## 根据条件（可选）查询员工名册


**接口地址**:`/api/employee/query`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "employeeId": "",
  "name": "",
  "mobile": "",
  "department": "",
  "status": "",
  "roleCode": "",
  "page": 0,
  "size": 0
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|employeeQueryDTO|员工查询DTO|body|true|EmployeeQueryDTO|EmployeeQueryDTO|
|&emsp;&emsp;employeeId|工号||false|string||
|&emsp;&emsp;name|姓名||false|string||
|&emsp;&emsp;mobile|手机号||false|string||
|&emsp;&emsp;department|所属部门||false|string||
|&emsp;&emsp;status|状态||false|string||
|&emsp;&emsp;roleCode|预设角色||false|string||
|&emsp;&emsp;page|页码||false|integer(int32)||
|&emsp;&emsp;size|每页条数||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResultEmployeeRoster|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResultEmployeeRoster|PageResultEmployeeRoster|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|EmployeeRoster|
|&emsp;&emsp;&emsp;&emsp;id||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;employeeId||string||
|&emsp;&emsp;&emsp;&emsp;name||string||
|&emsp;&emsp;&emsp;&emsp;mobile||string||
|&emsp;&emsp;&emsp;&emsp;department||string||
|&emsp;&emsp;&emsp;&emsp;position||string||
|&emsp;&emsp;&emsp;&emsp;status||string||
|&emsp;&emsp;&emsp;&emsp;roleCode||string||
|&emsp;&emsp;&emsp;&emsp;createdAt||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": [
			{
				"id": 0,
				"employeeId": "",
				"name": "",
				"mobile": "",
				"department": "",
				"position": "",
				"status": "",
				"roleCode": "",
				"createdAt": ""
			}
		]
	}
}
```


## 导入员工名册


**接口地址**:`/api/employee/import`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|file|员工名册EXCEL文件|query|true|file||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


## 新增员工


**接口地址**:`/api/employee/add`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "id": 0,
  "employeeId": "",
  "name": "",
  "mobile": "",
  "department": "",
  "position": "",
  "status": "",
  "roleCode": "",
  "createdAt": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|employeeRoster|EmployeeRoster|body|true|EmployeeRoster|EmployeeRoster|
|&emsp;&emsp;id|||false|integer(int32)||
|&emsp;&emsp;employeeId|||false|string||
|&emsp;&emsp;name|||false|string||
|&emsp;&emsp;mobile|||false|string||
|&emsp;&emsp;department|||false|string||
|&emsp;&emsp;position|||false|string||
|&emsp;&emsp;status|||false|string||
|&emsp;&emsp;roleCode|||false|string||
|&emsp;&emsp;createdAt|||false|string(date-time)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


## 清理离职员工


**接口地址**:`/api/employee/clearResigned`


**请求方式**:`DELETE`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:<p>删除所有状态为“离职”的员工记录</p>



**请求参数**:


暂无


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultString|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||string||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": ""
}
```


# AI解析自动入库


## 自动入库确认


**接口地址**:`/api/auto-inbound/{batchId}/confirm`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "confirmedTaskIds": [],
  "operator": {
    "id": 0,
    "name": "",
    "openid": "",
    "roleCode": "",
    "employeeId": "",
    "createdAt": "",
    "bindStatus": "",
    "bindMethod": "",
    "loginType": "",
    "enabled": true,
    "password": "",
    "username": "",
    "authorities": [
      {
        "authority": ""
      }
    ],
    "accountNonExpired": true,
    "credentialsNonExpired": true,
    "accountNonLocked": true
  },
  "updatedTasks": [
    {
      "taskId": "",
      "batchId": "",
      "type": "",
      "riskLevel": "",
      "riskReason": "",
      "rawBlock": "",
      "remark": "",
      "entryDate": "",
      "side": "",
      "hasAssay": true,
      "semiProductId": 0,
      "semiProductName": "",
      "semiWarehouseId": 0,
      "semiWarehouseName": "",
      "semiBoardQuantity": 0,
      "semiPieceQuantity": 0,
      "productId": 0,
      "productName": "",
      "warehouseId": 0,
      "warehouseName": "",
      "finishedBoardQuantity": 0,
      "finishedPieceQuantity": 0,
      "suggestedSemiRecords": [
        {
          "semiProductId": 1,
          "semiPalletCodeId": 1,
          "productName": "",
          "productionDate": "2025-01-01",
          "warehouseId": 1,
          "fromPreparePool": false,
          "cycleNo": 1,
          "quantity": 20,
          "unit": "30",
          "useAssay": false
        }
      ],
      "semiRecords": [
        {
          "semiProductId": 1,
          "semiPalletCodeId": 1,
          "productName": "",
          "productionDate": "2025-01-01",
          "warehouseId": 1,
          "fromPreparePool": false,
          "cycleNo": 1,
          "quantity": 20,
          "unit": "30",
          "useAssay": false
        }
      ],
      "canAutoStockIn": true
    }
  ]
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|batchId||path|true|string||
|autoInboundConfirmRequest|AutoInboundConfirmRequest|body|true|AutoInboundConfirmRequest|AutoInboundConfirmRequest|
|&emsp;&emsp;confirmedTaskIds|||false|array|string|
|&emsp;&emsp;operator|||false|User|User|
|&emsp;&emsp;&emsp;&emsp;id|||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;name|||false|string||
|&emsp;&emsp;&emsp;&emsp;openid|||false|string||
|&emsp;&emsp;&emsp;&emsp;roleCode|||false|string||
|&emsp;&emsp;&emsp;&emsp;employeeId|||false|string||
|&emsp;&emsp;&emsp;&emsp;createdAt|||false|string(date-time)||
|&emsp;&emsp;&emsp;&emsp;bindStatus|可用值:UNBOUND,WECHAT_BOUND,MANUAL_BOUND||false|string||
|&emsp;&emsp;&emsp;&emsp;bindMethod|可用值:WECHAT,MANUAL||false|string||
|&emsp;&emsp;&emsp;&emsp;loginType|||false|string||
|&emsp;&emsp;&emsp;&emsp;enabled|||false|boolean||
|&emsp;&emsp;&emsp;&emsp;password|||false|string||
|&emsp;&emsp;&emsp;&emsp;username|||false|string||
|&emsp;&emsp;&emsp;&emsp;authorities|||false|array|GrantedAuthority|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;authority|||false|string||
|&emsp;&emsp;&emsp;&emsp;accountNonExpired|||false|boolean||
|&emsp;&emsp;&emsp;&emsp;credentialsNonExpired|||false|boolean||
|&emsp;&emsp;&emsp;&emsp;accountNonLocked|||false|boolean||
|&emsp;&emsp;updatedTasks|||false|array|AutoInboundTask|
|&emsp;&emsp;&emsp;&emsp;taskId|||false|string||
|&emsp;&emsp;&emsp;&emsp;batchId|||false|string||
|&emsp;&emsp;&emsp;&emsp;type|可用值:SEMI_PRODUCT,FINISHED_PRODUCT||false|string||
|&emsp;&emsp;&emsp;&emsp;riskLevel|可用值:GREEN,YELLOW,RED||false|string||
|&emsp;&emsp;&emsp;&emsp;riskReason|||false|string||
|&emsp;&emsp;&emsp;&emsp;rawBlock|||false|string||
|&emsp;&emsp;&emsp;&emsp;remark|||false|string||
|&emsp;&emsp;&emsp;&emsp;entryDate|||false|string(date)||
|&emsp;&emsp;&emsp;&emsp;side|||false|string||
|&emsp;&emsp;&emsp;&emsp;hasAssay|||false|boolean||
|&emsp;&emsp;&emsp;&emsp;semiProductId|||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;semiProductName|||false|string||
|&emsp;&emsp;&emsp;&emsp;semiWarehouseId|||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;semiWarehouseName|||false|string||
|&emsp;&emsp;&emsp;&emsp;semiBoardQuantity|||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;semiPieceQuantity|||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productId|||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productName|||false|string||
|&emsp;&emsp;&emsp;&emsp;warehouseId|||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;warehouseName|||false|string||
|&emsp;&emsp;&emsp;&emsp;finishedBoardQuantity|||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;finishedPieceQuantity|||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;suggestedSemiRecords|||false|array|SemiRecordDTO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiProductId|半成品ID||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiPalletCodeId|半成品托盘ID||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productName|产品名称||false|string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productionDate|生产日期||false|string(date)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;warehouseId|库位||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;fromPreparePool|是否来自备料池||false|boolean||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;cycleNo|半成品来源循环号||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;quantity|使用的半成品数量||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;unit|单位0板1件（板/件）||true|string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;useAssay|是否套用该半成品的化验数据||false|boolean||
|&emsp;&emsp;&emsp;&emsp;semiRecords|||false|array|SemiRecordDTO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiProductId|半成品ID||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiPalletCodeId|半成品托盘ID||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productName|产品名称||false|string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productionDate|生产日期||false|string(date)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;warehouseId|库位||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;fromPreparePool|是否来自备料池||false|boolean||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;cycleNo|半成品来源循环号||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;quantity|使用的半成品数量||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;unit|单位0板1件（板/件）||true|string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;useAssay|是否套用该半成品的化验数据||false|boolean||
|&emsp;&emsp;&emsp;&emsp;canAutoStockIn|||false|boolean||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultVoid|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||object||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {}
}
```


## 自动入库文本解析


**接口地址**:`/api/auto-inbound/parse`


**请求方式**:`POST`


**请求数据类型**:`application/x-www-form-urlencoded,application/json`


**响应数据类型**:`*/*`


**接口描述**:


**请求示例**:


```javascript
{
  "rawText": "",
  "entryDate": "",
  "operator": {
    "id": 0,
    "name": "",
    "openid": "",
    "roleCode": "",
    "employeeId": "",
    "createdAt": "",
    "bindStatus": "",
    "bindMethod": "",
    "loginType": "",
    "enabled": true,
    "password": "",
    "username": "",
    "authorities": [
      {
        "authority": ""
      }
    ],
    "accountNonExpired": true,
    "credentialsNonExpired": true,
    "accountNonLocked": true
  },
  "parseType": ""
}
```


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|autoInboundParseRequest|AutoInboundParseRequest|body|true|AutoInboundParseRequest|AutoInboundParseRequest|
|&emsp;&emsp;rawText|||false|string||
|&emsp;&emsp;entryDate|||false|string(date)||
|&emsp;&emsp;operator|||false|User|User|
|&emsp;&emsp;&emsp;&emsp;id|||false|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;name|||false|string||
|&emsp;&emsp;&emsp;&emsp;openid|||false|string||
|&emsp;&emsp;&emsp;&emsp;roleCode|||false|string||
|&emsp;&emsp;&emsp;&emsp;employeeId|||false|string||
|&emsp;&emsp;&emsp;&emsp;createdAt|||false|string(date-time)||
|&emsp;&emsp;&emsp;&emsp;bindStatus|可用值:UNBOUND,WECHAT_BOUND,MANUAL_BOUND||false|string||
|&emsp;&emsp;&emsp;&emsp;bindMethod|可用值:WECHAT,MANUAL||false|string||
|&emsp;&emsp;&emsp;&emsp;loginType|||false|string||
|&emsp;&emsp;&emsp;&emsp;enabled|||false|boolean||
|&emsp;&emsp;&emsp;&emsp;password|||false|string||
|&emsp;&emsp;&emsp;&emsp;username|||false|string||
|&emsp;&emsp;&emsp;&emsp;authorities|||false|array|GrantedAuthority|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;authority|||false|string||
|&emsp;&emsp;&emsp;&emsp;accountNonExpired|||false|boolean||
|&emsp;&emsp;&emsp;&emsp;credentialsNonExpired|||false|boolean||
|&emsp;&emsp;&emsp;&emsp;accountNonLocked|||false|boolean||
|&emsp;&emsp;parseType|可用值:SEMI_PRODUCT,FINISHED_PRODUCT||false|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultAutoInboundParseResponse|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||AutoInboundParseResponse|AutoInboundParseResponse|
|&emsp;&emsp;batchId||string||
|&emsp;&emsp;tasks||array|AutoInboundTask|
|&emsp;&emsp;&emsp;&emsp;taskId||string||
|&emsp;&emsp;&emsp;&emsp;batchId||string||
|&emsp;&emsp;&emsp;&emsp;type|可用值:SEMI_PRODUCT,FINISHED_PRODUCT|string||
|&emsp;&emsp;&emsp;&emsp;riskLevel|可用值:GREEN,YELLOW,RED|string||
|&emsp;&emsp;&emsp;&emsp;riskReason||string||
|&emsp;&emsp;&emsp;&emsp;rawBlock||string||
|&emsp;&emsp;&emsp;&emsp;remark||string||
|&emsp;&emsp;&emsp;&emsp;entryDate||string(date)||
|&emsp;&emsp;&emsp;&emsp;side||string||
|&emsp;&emsp;&emsp;&emsp;hasAssay||boolean||
|&emsp;&emsp;&emsp;&emsp;semiProductId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;semiProductName||string||
|&emsp;&emsp;&emsp;&emsp;semiWarehouseId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;semiWarehouseName||string||
|&emsp;&emsp;&emsp;&emsp;semiBoardQuantity||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;semiPieceQuantity||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productName||string||
|&emsp;&emsp;&emsp;&emsp;warehouseId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;warehouseName||string||
|&emsp;&emsp;&emsp;&emsp;finishedBoardQuantity||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;finishedPieceQuantity||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;suggestedSemiRecords||array|SemiRecordDTO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiProductId|半成品ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiPalletCodeId|半成品托盘ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productionDate|生产日期|string(date)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;warehouseId|库位|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;fromPreparePool|是否来自备料池|boolean||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;cycleNo|半成品来源循环号|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;quantity|使用的半成品数量|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;unit|单位0板1件（板/件）|string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;useAssay|是否套用该半成品的化验数据|boolean||
|&emsp;&emsp;&emsp;&emsp;semiRecords||array|SemiRecordDTO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiProductId|半成品ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiPalletCodeId|半成品托盘ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productionDate|生产日期|string(date)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;warehouseId|库位|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;fromPreparePool|是否来自备料池|boolean||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;cycleNo|半成品来源循环号|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;quantity|使用的半成品数量|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;unit|单位0板1件（板/件）|string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;useAssay|是否套用该半成品的化验数据|boolean||
|&emsp;&emsp;&emsp;&emsp;canAutoStockIn||boolean||
|&emsp;&emsp;globalRemarks||array|string|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"batchId": "",
		"tasks": [
			{
				"taskId": "",
				"batchId": "",
				"type": "",
				"riskLevel": "",
				"riskReason": "",
				"rawBlock": "",
				"remark": "",
				"entryDate": "",
				"side": "",
				"hasAssay": true,
				"semiProductId": 0,
				"semiProductName": "",
				"semiWarehouseId": 0,
				"semiWarehouseName": "",
				"semiBoardQuantity": 0,
				"semiPieceQuantity": 0,
				"productId": 0,
				"productName": "",
				"warehouseId": 0,
				"warehouseName": "",
				"finishedBoardQuantity": 0,
				"finishedPieceQuantity": 0,
				"suggestedSemiRecords": [
					{
						"semiProductId": 1,
						"semiPalletCodeId": 1,
						"productName": "",
						"productionDate": "2025-01-01",
						"warehouseId": 1,
						"fromPreparePool": false,
						"cycleNo": 1,
						"quantity": 20,
						"unit": "30",
						"useAssay": false
					}
				],
				"semiRecords": [
					{
						"semiProductId": 1,
						"semiPalletCodeId": 1,
						"productName": "",
						"productionDate": "2025-01-01",
						"warehouseId": 1,
						"fromPreparePool": false,
						"cycleNo": 1,
						"quantity": 20,
						"unit": "30",
						"useAssay": false
					}
				],
				"canAutoStockIn": true
			}
		],
		"globalRemarks": []
	}
}
```


## 查询自动入库批次


**接口地址**:`/api/auto-inbound/{batchId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|batchId||path|true|string||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultAutoInboundParseResponse|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||AutoInboundParseResponse|AutoInboundParseResponse|
|&emsp;&emsp;batchId||string||
|&emsp;&emsp;tasks||array|AutoInboundTask|
|&emsp;&emsp;&emsp;&emsp;taskId||string||
|&emsp;&emsp;&emsp;&emsp;batchId||string||
|&emsp;&emsp;&emsp;&emsp;type|可用值:SEMI_PRODUCT,FINISHED_PRODUCT|string||
|&emsp;&emsp;&emsp;&emsp;riskLevel|可用值:GREEN,YELLOW,RED|string||
|&emsp;&emsp;&emsp;&emsp;riskReason||string||
|&emsp;&emsp;&emsp;&emsp;rawBlock||string||
|&emsp;&emsp;&emsp;&emsp;remark||string||
|&emsp;&emsp;&emsp;&emsp;entryDate||string(date)||
|&emsp;&emsp;&emsp;&emsp;side||string||
|&emsp;&emsp;&emsp;&emsp;hasAssay||boolean||
|&emsp;&emsp;&emsp;&emsp;semiProductId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;semiProductName||string||
|&emsp;&emsp;&emsp;&emsp;semiWarehouseId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;semiWarehouseName||string||
|&emsp;&emsp;&emsp;&emsp;semiBoardQuantity||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;semiPieceQuantity||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;productName||string||
|&emsp;&emsp;&emsp;&emsp;warehouseId||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;warehouseName||string||
|&emsp;&emsp;&emsp;&emsp;finishedBoardQuantity||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;finishedPieceQuantity||integer(int32)||
|&emsp;&emsp;&emsp;&emsp;suggestedSemiRecords||array|SemiRecordDTO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiProductId|半成品ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiPalletCodeId|半成品托盘ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productionDate|生产日期|string(date)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;warehouseId|库位|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;fromPreparePool|是否来自备料池|boolean||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;cycleNo|半成品来源循环号|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;quantity|使用的半成品数量|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;unit|单位0板1件（板/件）|string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;useAssay|是否套用该半成品的化验数据|boolean||
|&emsp;&emsp;&emsp;&emsp;semiRecords||array|SemiRecordDTO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiProductId|半成品ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;semiPalletCodeId|半成品托盘ID|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productName|产品名称|string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;productionDate|生产日期|string(date)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;warehouseId|库位|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;fromPreparePool|是否来自备料池|boolean||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;cycleNo|半成品来源循环号|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;quantity|使用的半成品数量|integer(int32)||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;unit|单位0板1件（板/件）|string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;useAssay|是否套用该半成品的化验数据|boolean||
|&emsp;&emsp;&emsp;&emsp;canAutoStockIn||boolean||
|&emsp;&emsp;globalRemarks||array|string|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"batchId": "",
		"tasks": [
			{
				"taskId": "",
				"batchId": "",
				"type": "",
				"riskLevel": "",
				"riskReason": "",
				"rawBlock": "",
				"remark": "",
				"entryDate": "",
				"side": "",
				"hasAssay": true,
				"semiProductId": 0,
				"semiProductName": "",
				"semiWarehouseId": 0,
				"semiWarehouseName": "",
				"semiBoardQuantity": 0,
				"semiPieceQuantity": 0,
				"productId": 0,
				"productName": "",
				"warehouseId": 0,
				"warehouseName": "",
				"finishedBoardQuantity": 0,
				"finishedPieceQuantity": 0,
				"suggestedSemiRecords": [
					{
						"semiProductId": 1,
						"semiPalletCodeId": 1,
						"productName": "",
						"productionDate": "2025-01-01",
						"warehouseId": 1,
						"fromPreparePool": false,
						"cycleNo": 1,
						"quantity": 20,
						"unit": "30",
						"useAssay": false
					}
				],
				"semiRecords": [
					{
						"semiProductId": 1,
						"semiPalletCodeId": 1,
						"productName": "",
						"productionDate": "2025-01-01",
						"warehouseId": 1,
						"fromPreparePool": false,
						"cycleNo": 1,
						"quantity": 20,
						"unit": "30",
						"useAssay": false
					}
				],
				"canAutoStockIn": true
			}
		],
		"globalRemarks": []
	}
}
```