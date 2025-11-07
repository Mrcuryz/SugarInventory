import request from "../../utils/request";

Page({
  data: {
    activeTab: 'inventory',

    // 库存列表
    warehouseList: [],
    totalItems: 0,
    totalPages: 0,
    currentPage: 1,
    pageSize: 10,

    // 检验标准列表
    standardNameOptions: [],
    standardNameIndex: 0,

    qualifiedWarehouseList: [], // 查询结果库位
    // 筛网列表
    screenMeshOptions: [],
    selectedScreenMeshId: null,

    // 查询条件
    statusOptions: ['请选择', '正常', '满仓', '空置', '维护', '临期预警'],
    statusIndex: 0,
    warehouseName: '',

    // 库存详情
    showDetailModal: false,
    detailList: [],
    detailCurrentPage: 1,
    detailTotalPages: 0,
    selectedWarehouseId: null,
    // 化验信息
    showAssayModal:false,
    productAssayInfo:{},
    // 出库查询
    productName: '',
    standardNames: '',
    screenMeshId: '',
    startDate: '',
    endDate: '',
    // 所有的库位
    CapacityList:[
    ],
    // 出库操作
    showOutboundModal: false,
    selectedWarehouse: {},
    outboundQuantity: '',
    outboundSide: '左',
    quantityError: '',
    inWarehouseNameError:'',
    productError:'',
    isOutboundValid: false,
    outType: '0',
    // 调拨出库操作
    showTransferOutboundModal: false,
    unitIndex: 0,
    unit: '0',
    inWarehouseName: '请选择库位',
    inWarehouseValue: '',
    inWarehouseNameIndex: 0,
    // 产品选择
    warehouseProductList:[],
    warehouseProductId:'',
    warehouseProductName: '请选择产品',
    warehouseProductIndex: 0,
    // 加载状态
    isLoading: false,
  },

  onLoad() {
    this.loadInventory();
    this.getAllWarehouseCapacity();
    this.loadStandardNameOptions();
    this.loadScreenMeshes();
  },

  getStatusClass(status) {
    switch (status) {
      case '正常':
        return 'status-green';
      case '空置':
        return 'status-gray';
      case '临期预警':
        return 'status-orange';
      case '维护':
        return 'status-black';
      case '满仓':
        return 'status-red';
      default:
        return 'status-default';
    }
  },  

  // Tab切换事件
  switchTab(e) {
    const selectedTab = e.currentTarget.dataset.tab;
    if (selectedTab !== this.data.activeTab) {
      this.setData({
        activeTab: selectedTab
      });
    }
  },

  // 加载筛网列表
  async loadScreenMeshes() {
    try {
      const res = await request('/api/screen-mesh/list', 'GET');
      console.log(res)
  
      if (res && Array.isArray(res)) {
        const options = res.map(mesh => ({
          id: mesh.id,
          name: `${mesh.meshName} (${mesh.description || '无描述'})`
        }));
        console.log(options)
  
        // 提取 picker 需要的字符串数组
        const optionNames = options.map(option => option.name);
  
        this.setData({ 
          screenMeshOptions: optionNames,  // 传递字符串数组
          screenMeshMap: options,          // 存储完整对象映射
          selectedScreenMeshId: options[0]?.id || null 
        });
      }
    } catch (error) {
      wx.showToast({ title: '筛网加载失败', icon: 'none' });
      console.error("筛网加载失败:", error);
    }
  },  
 // 单位选择处理
 onUnitChange(e) {
  const index = e.detail.value;
  this.setData({
    unitIndex: index,
    unit: index.toString()
  });
},
getProductAssayData(e) {
  const index = e.currentTarget.dataset.index;
  const product = this.data.detailList[index];
  let params = {
    page: 1,
    size: 100,
    productId: product.productId,
    entryDate:product.entryDate,
    isQualified: '合格'
  }
  request('/api/assay/query', 'POST', params)
  .then(res =>{
    console.log(res)
    this.setData({
      showAssayModal: true,
      productAssayInfo: res.records[0] || {}
    });
  })
},
  // 入库库位选择处理
  onInWarehouseNameChange(e) {
    const index = e.detail.value;
    this.setData({
      inWarehouseNameIndex: index,
      inWarehouseName: this.data.CapacityList[index].warehouseName,
      inWarehouseValue: this.data.CapacityList[index].warehouseName,
    });
  },
  // 产品选择选择处理
  onProductChange(e) {
    const index = e.detail.value;
    let product = this.data.warehouseProductList[index];
    this.setData({
      warehouseProductIndex: index,
      'selectedWarehouse.curCapacity':(product.totalQuantity > 0? product.totalQuantity + '板' :'') 
      + (product.totalPieces > 0 ? product.totalPieces + '件' : ''),
      warehouseProductName: product.productFullName,
      warehouseProductId: product.productId,
    });
    this.validateOutboundForm();
  },
getWarehouseProduct(warehouse) {
  let params = {
    warehouseId: warehouse,
    page: 1,
    size: 1000
  }
  request('/api/inventory/summary', 'POST', params)
  .then(res =>{
    if (res.records) {
    const processedList = res.records.map(item => ({
      ...item,
      productFullName: `${item.productName}（${item.entryDate}）`
    }));
    this.setData({
      warehouseProductList: processedList
    });
  }
  })
},
getAllWarehouseCapacity() {
  request('/api/inventory/warehouses', 'GET', {})
  .then(res =>{
    this.setData({
      CapacityList: res || []
    });
  })
},
  /**
   * 加载库存列表
   */
  async loadInventory() {
    console.log("loading");
    try {
      this.setData({
        isLoading: true
      });
      const warehouseName = this.data.warehouseName === '' ? '' : this.data.warehouseName;
      const status = this.data.statusIndex === 0 ? '' : this.data.statusOptions[this.data.statusIndex];
      const res = await request('/api/inventory/query', 'GET', {
        warehouseName: warehouseName,
        page: this.data.currentPage,
        size: this.data.pageSize,
        status: status,
      });
      console.log("接口返回：", res);

      if (res && Array.isArray(res.records)) {
        const list = res.records.map(item => ({
          ...item,
          capacityPercentage: parseFloat((item.capacityPercentage * 100).toFixed(1)),
          statusClass: this.getStatusClass(item.status)
        }));

        console.log("最终 warehouseList：", list);

        this.setData({
          warehouseList: list,
          totalItems: res.total, // ✅ 使用 total 字段
          totalPages: Math.ceil(res.total / this.data.pageSize)
        });
      }
    } catch (error) {
      wx.showToast({
        title: '加载失败: ' + (error.message || '未知错误'),
        icon: 'none'
      });
    } finally {
      this.setData({
        isLoading: false
      });
    }
  },

  async loadStandardNameOptions() {
    try {
      const res = await request('/api/quality-standards/list', 'GET');
      if (res && Array.isArray(res)) {
        const names = res.map(item => item.standardName);
        this.setData({
          standardNameOptions: ['请选择标准名', ...names],
          standardNames: ''
        });
      }
    } catch (err) {
      wx.showToast({
        title: '加载标准名称失败',
        icon: 'none'
      });
    }
  },  

  /**
   * 查看库存详情
   */
  async showDetail(e) {
    const warehouseId = e.currentTarget.dataset.id;

    this.setData({
      showDetailModal: true,
      selectedWarehouseId: warehouseId,
      detailCurrentPage: 1,
      isOutboundDetail: false,
    });

    await this.loadInventoryDetail();
  },

  /**
   * 加载库存详情数据
   */
  async loadInventoryDetail() {
    try {
      this.setData({
        isLoading: true
      });

      const res = await request('/api/inventory/summary', 'POST', {
        warehouseId: this.data.selectedWarehouseId,
        page: this.data.detailCurrentPage,
        size: 10
      });

      console.log(res);

      if (res && res.records) {
        // 格式化日期
        const list = res.records.map(item => {
          return {
            ...item,
            entryDate: this.formatDate(item.entryDate),
            totalWeight: parseFloat(item.totalWeight).toFixed(2)
          };
        });
        const totalPages = parseInt(res.total / 10) + 1;
        console.log(totalPages)
        this.setData({
          detailList: list,
          detailTotalPages: totalPages
        });
      }
    } catch (error) {
      wx.showToast({
        title: '加载详情失败',
        icon: 'none'
      });
    } finally {
      this.setData({
        isLoading: false
      });
    }
  },

  /**
   * 隐藏详情弹窗
   */
  hideDetail() {
    this.setData({
      showDetailModal: false,
      detailList: []
    });
  },

  /**
   * 隐藏化验信息弹窗
   */
  hideAssayDetail() {
    this.setData({
      showAssayModal: false,
      productAssayInfo: {}
    });
  },

  // 出库搜索
  async searchOutboundCondition() {
    const {
      productName,
      standardNames,
      screenMeshId,
      startDate,
      endDate
    } = this.data;

    try {
      this.setData({
        isLoading: true
      });

      const queryBody = {
        productName: productName || null,
        standardNames: standardNames ? standardNames : null,
        screenMeshId: screenMeshId ? parseInt(screenMeshId) : null,
        startDate: startDate || null,
        endDate: endDate || null
      };

      const res = await request('/api/inventory/qualified-warehouses', 'POST', queryBody);

      if (res && Array.isArray(res) && res.length > 0) {
        const warehouseIds = res.map(item => item.warehouseId);
        // 查询库位简略信息
        const detailRes = await request('/api/inventory/query', 'POST', {
          ids: warehouseIds,
          page: 1,
          size: 10
        });

        if (detailRes && detailRes.records) {
          const list = detailRes.records.map(item => ({
            ...item,
            capacityPercentage: parseFloat((item.capacityPercentage * 100).toFixed(1)),
            statusClass: this.getStatusClass(item.status)
          }));

          this.setData({
            warehouseList: list,
            totalItems: detailRes.total,
            totalPages: Math.ceil(detailRes.total / 50),
            currentPage: 1
          });
        }
      } else {
        this.setData({
          warehouseList: [],
          totalItems: 0,
          totalPages: 0,
        });
        wx.showToast({
          title: '未查询到符合条件的库位',
          icon: 'none'
        });
      }
    } catch (error) {
      wx.showToast({
        title: '查询失败: ' + (error.message || '未知错误'),
        icon: 'none'
      });
    } finally {
      this.setData({
        isLoading: false
      });
    }
  },

  // 查看某个库位下的符合条件的产品
  async showQualifiedDetail(e) {
    const warehouseId = e.currentTarget.dataset.id;
    const {
      productName,
      standardNames,
      screenMeshId,
      startDate,
      endDate
    } = this.data;

    try {
      this.setData({
        isLoading: true
      });
      
      const queryBody = {
        productName: productName || null,
        standardNames: standardNames ? standardNames : null,
        screenMeshId: screenMeshId ? parseInt(screenMeshId) : null,
        startDate: startDate || null,
        endDate: endDate || null
      };

      const res = await request(`/api/inventory/qualified-inventory/${warehouseId}`, 'POST', queryBody);

      console.log(res)
      const formattedList = res.map(item => ({
        ...item,
        standardNames: Array.isArray(item.standardNames)
          ? item.standardNames.join(',')
          : (typeof item.standardNames === 'string' && item.standardNames.startsWith('['))
            ? JSON.parse(item.standardNames).join(',')
            : item.standardNames
      }));

      if (res && Array.isArray(res)) {
        // 你可以将 res 设置为 detailList 或其它弹窗展示用的数据
        this.setData({
          isOutboundDetail: true,
          detailList: formattedList,
          showDetailModal: true
        });
      }
    } catch (error) {
      wx.showToast({
        title: '加载详情失败',
        icon: 'none'
      });
    } finally {
      this.setData({
        isLoading: false
      });
    }
  },

  /**
   * 详情分页 - 上一页
   */
  detailPrevPage() {
    if (this.data.detailCurrentPage > 1) {
      this.setData({
        detailCurrentPage: this.data.detailCurrentPage - 1
      });
      this.loadInventoryDetail();
    }
  },

  /**
   * 详情分页 - 下一页
   */
  detailNextPage() {
    if (this.data.detailCurrentPage < this.data.detailTotalPages) {
      this.setData({
        detailCurrentPage: this.data.detailCurrentPage + 1
      });
      this.loadInventoryDetail();
    }
  },

  /**
   * 开始出库操作
   */
  startOutbound(e) {
    const {
      id,
      name,
      capacity
    } = e.currentTarget.dataset;
    this.getWarehouseProduct(id)
    this.setData({
      showOutboundModal: true,
      selectedWarehouse: {
        warehouseId: id,
        warehouseName: name,
        curCapacity: ''
      },
      outboundQuantity: '',
      outboundSide: '左',
      quantityError: '',
      inWarehouseNameError: '',
      productError: '',
      isOutboundValid: false,
      unitIndex: 0,
      unit: '0',
      inWarehouseName: '请选择库位',
      inWarehouseValue: '',
      inWarehouseNameIndex: 0,
      // 产品选择
      warehouseProductId:'',
      warehouseProductName: '请选择产品',
      warehouseProductIndex: 0,
    });
  },
  startTransferOutbound(e) {
    const {
      id,
      name,
      capacity
    } = e.currentTarget.dataset;
    this.getWarehouseProduct(id)
    this.setData({
      showTransferOutboundModal: true,
      selectedWarehouse: {
        warehouseId: id,
        warehouseName: name,
        curCapacity: ''
      },
      outboundQuantity: '',
      outboundSide: '左',
      quantityError: '',
      inWarehouseNameError: '',
      productError: '',
      isOutboundValid: false,
      unitIndex: 0,
      unit: '0',
      inWarehouseName: '请选择库位',
      inWarehouseValue: '',
      inWarehouseNameIndex: 0,
      // 产品选择
      warehouseProductId:'',
      warehouseProductName: '请选择产品',
      warehouseProductIndex: 0,
    });
  },

  /**
   * 隐藏出库弹窗
   */
  hideOutbound() {
    this.setData({
      showTransferOutboundModal: false,
      showOutboundModal: false
    });
  },

  /**
   * 确认出库
   */
confirmOutbound() {
    if (!this.validateOutboundForm()) {
      return;
    }
    try {
      this.setData({
        isLoading: true
      });
      request('/api/out-stock/out', 'POST', {
        warehouseId: this.data.selectedWarehouse.warehouseId,
        quantity: parseInt(this.data.outboundQuantity),
        side: this.data.outboundSide,
        outType: this.data.outType,
        productId: this.data.warehouseProductId,
        unit: this.data.unit
      }).then(res => {
        wx.showToast({
          title: '调拨成功',
          icon: 'success'
        });
        this.hideOutbound();
        // 重新加载库存数据
        this.loadInventory();
      })
    } catch (error) {
      wx.showToast({
        title: '出库操作失败: ' + (error.message || '未知错误'),
        icon: 'none'
      });
    } finally {
      this.setData({
        isLoading: false
      });
    }
  },
  /**
   * 确认调拨出库
   */
  async confirmTransferOutbound() {
    if (!this.validateOutboundForm()) {
      return;
    }
    if(!this.data.inWarehouseValue || this.data.inWarehouseValue === '') {
      this.setData({
        inWarehouseNameError: "请选择入库库位"
      })
      return;
    }
    try {
      this.setData({
        isLoading: true
      });
      request('/api/out-stock/transferOut', 'POST', {
        warehouseId: this.data.selectedWarehouse.warehouseId,
        quantity: parseInt(this.data.outboundQuantity),
        side: this.data.outboundSide,
        outType: this.data.outType,
        productId: this.data.warehouseProductId,
        inWarehouseName: this.data.inWarehouseValue,
        unit: this.data.unit
      }).then(res => {
        wx.showToast({
          title: '调拨成功',
          icon: 'success'
        });
        this.hideOutbound();
        // 重新加载库存数据
        this.loadInventory();
      })
    } catch (error) {
      wx.showToast({
        title: '出库操作失败: ' + (error.message || '未知错误'),
        icon: 'none'
      });
    } finally {
      this.setData({
        isLoading: false
      });
    }
  },
  /**
   * 常规库存查询
   */
  searchInventory() {
    this.setData({
      currentPage: 1
    });
    this.loadInventory();
  },

  /**
   * 上一页
   */
  prevPage() {
    if (this.data.currentPage > 1) {
      this.setData({
        currentPage: this.data.currentPage - 1
      });
      this.loadInventory();
    }
  },

  /**
   * 下一页
   */
  nextPage() {
    if (this.data.currentPage < this.data.totalPages) {
      this.setData({
        currentPage: this.data.currentPage + 1
      });
      this.loadInventory();
    }
  },

  /**
   * 校验出库表单
   */
  validateOutboundForm() {
    const {
      outboundQuantity,
      selectedWarehouse,
      warehouseProductId 
    } = this.data;
    let error = '';
    if (!outboundQuantity) {
      error = '请输入出库数量';
    } else if (isNaN(outboundQuantity) || parseInt(outboundQuantity) <= 0) {
      error = '请输入有效的出库数量';
    }
    let productError = '';
    if(!warehouseProductId || warehouseProductId == '') {
      productError = '请选择出库产品'
    }

    this.setData({
      quantityError: error,
      productError: productError,
      isOutboundValid: !error && !productError
    });

    return !error;
  },

  /**
   * 处理状态选择变化
   */
  onStatusChange(e) {
    this.setData({
      statusIndex: parseInt(e.detail.value)
    });
  },

  /**
   * 处理名称输入
   */
  onWarehouseNameInput(e) {
    this.setData({
      warehouseName: e.detail.value
    });
  },

  // 处理筛网选择
  onScreenMeshChange(e) { 
    const selectedIndex = e.detail.value; // 获取选择的索引
    const selectedMesh = this.data.screenMeshMap[selectedIndex]; // 从映射中获取完整对象
  
    if (!selectedMesh) {
      console.error("无效的筛网选择索引:", selectedIndex);
      return;
    }
  
    this.setData({ 
      selectedScreenMeshId: selectedMesh.id, // 这里存 ID
      selectedScreenMeshIndex: selectedIndex // 这里存索引，确保 picker 正确回显
    });
  },  

  /**
   * 处理出库数量输入
   */
  onQuantityInput(e) {
    this.setData({
      outboundQuantity: e.detail.value
    });

    this.validateOutboundForm();
  },

  /**
   * 处理出库顺序选择
   */
  onSideChange(e) {
    this.setData({
      outboundSide: e.detail.value
    });
  },

  onOutTypeChange(e) {
    this.setData({
      outType : e.detail.value
    })
  },

  onStandardNameChange(e) {
    const index = parseInt(e.detail.value);
    const selected = this.data.standardNameOptions[index];
    this.setData({
      standardNameIndex: index,
      standardNames: selected === '请选择标准名' ? '' : selected
    });
  },  

  onOutboundProductNameInput(e) {
    this.setData({
      productName: e.detail.value
    });
  },

  onStandardInput(e) {
    this.setData({
      standardNames: e.detail.value
    });
  },

  onScreenIdInput(e) {
    this.setData({
      screenMeshId: e.detail.value
    });
  },

  onStartDateChange(e) {
    this.setData({
      startDate: e.detail.value
    });
  },

  onEndDateChange(e) {
    this.setData({
      endDate: e.detail.value
    });
  },

  /**
   * 阻止穿透
   */
  preventTouchMove() {
    return false;
  },

  /**
   * 格式化日期
   */
  formatDate(dateStr) {
    if (!dateStr) return '';

    try {
      const date = new Date(dateStr);
      return `${date.getFullYear()}-${this.padZero(date.getMonth() + 1)}-${this.padZero(date.getDate())}`;
    } catch (e) {
      return dateStr;
    }
  },

  /**
   * 数字补零
   */
  padZero(num) {
    return num < 10 ? '0' + num : num;
  },

  /**
   * 封装请求
   */
  request(url, method, data) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: url,
        method: method,
        data: data,
        header: {
          'content-type': method === 'GET' ? 'application/json' : 'application/json'
        },
        success(res) {
          if (res.statusCode === 200) {
            resolve(res.data);
          } else {
            reject(new Error(`请求失败: ${res.statusCode}`));
          }
        },
        fail(err) {
          reject(err);
        }
      });
    });
  }
});