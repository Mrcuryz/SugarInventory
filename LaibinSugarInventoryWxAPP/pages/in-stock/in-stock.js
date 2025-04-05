import request from "../../utils/request";
import dayjs from 'dayjs';

Page({
  data: {
    productPickerVisible: false,
    productOptions: [
      [],
      [],
      []
    ], // picker数据源（三列）
    pickerIndexes: [0, 0, 0], // 每一列的当前索引
    productMap: {
      '成品': [],
      '半成品': []
    },
    productRecords: [],
    semiProductOptions: [],
    semiProductRecords: [],
    screenMeshOptions: [],
    selectedScreenMeshId: null,
    queryParams: {
      productName: '',
      warehouseName: '',
      operatorName: '',
      startDate: null,
      endDate: null,
      page: 1,
      size: 10
    },
    totalPages: 1,
    currentPage: 1,
    showModal: false,
    sideOptions: ['左', '右'],
    currentRecord: {
      productId: null,
      productName: null,
      warehouseName: null,
      quantity: null,
      screenMeshId: null,
      semiRecords: null,
      side: null
    }
  },

  onLoad() {
    this.loadRecords();
    this.initFinishedProductPicker();
    this.initSemiProductPicker();
    this.loadScreenMeshes();
  },

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

  async loadRecords() {
    try {
      const params = {
        ...this.data.queryParams,
        page: this.data.currentPage,
        size: this.data.queryParams.size
      };

      if (!params.startDate) params.startDate = null;
      if (!params.endDate) params.endDate = null;
      if (!params.productName) delete params.productName;
      if (!params.warehouseName) delete params.warehouseName;
      if (!params.operatorName) delete params.operatorName;

      const res = await request('/api/in-stock/query', 'POST', params);

      if (!res || !res.records) {
        wx.showToast({
          title: '数据加载失败',
          icon: 'none'
        });
        return;
      }
      console.log(res.records)

      const processedData = res.records.map(item => ({
        id: item.id,
        productName: item.productName,
        warehouseName: item.warehouseName,
        quantity: item.quantity,
        totalWeight: item.totalWeight,
        operationDate: dayjs(item.operationDate).format('YYYY-MM-DD'),
        createdAt: dayjs(item.createdAt).format('YYYY-MM-DD HH:mm'),
        operator: item.operator,
        meshName: item.meshName,
        labData: item.assayId ? [{ // 化验记录单独封装成数组
          assayId: item.assayId,
          sampleDate: dayjs(item.sampleDate).format('YYYY-MM-DD'),
          colorValue: item.colorValue,
          reducingSugar: item.reducingSugar,
          dryWeight: item.dryWeight,
          conductivityAsh: item.conductivityAsh,
          sucrose: item.sucrose,
          insolubleImpurity: item.insolubleImpurity,
          phValue: item.phValue,
          testerName: item.testerName,
          isQualified: item.isQualified,
          qualifiedStandards: item.qualifiedStandards ? JSON.parse(item.qualifiedStandards) : []
        }] : [], // 没有assayId则为空数组
        showLabData: false,
        showSemiProductRecords: false,
        semiProductRecords: item.semiProductRecords ? JSON.parse(item.semiProductRecords) : []
      }));

      console.log(processedData)

      this.setData({
        productRecords: processedData,
        totalPages: Math.ceil(res.total / this.data.queryParams.size),
        currentPage: this.data.currentPage
      });
    } catch (error) {
      console.error("数据加载失败:", error);
      wx.showToast({
        title: '数据加载失败',
        icon: 'none'
      });
    }
  },

  onSearchInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({
      [`queryParams.${field}`]: e.detail.value
    });
    this.debouncedLoadData();
  },

  onDateChange(e) {
    const field = e.currentTarget.dataset.field;
    const value = dayjs(e.detail.value).format('YYYY-MM-DD');

    this.setData({
      [`queryParams.${field}`]: value
    }, () => {
      this.debouncedLoadData();
    });
  },

  debouncedLoadData: debounce(function () {
    this.setData({
      currentPage: 1
    }, () => {
      this.loadRecords();
    });
  }, 500),

  prevPage() {
    if (this.data.currentPage > 1) {
      this.setData({
        currentPage: this.data.currentPage - 1
      }, () => this.loadRecords());
    }
  },

  nextPage() {
    if (this.data.currentPage < this.data.totalPages) {
      this.setData({
        currentPage: this.data.currentPage + 1
      }, () => this.loadRecords());
    }
  },

  onPageInput(e) {
    const value = Math.min(Math.max(e.detail.value, 1), this.data.totalPages);
    this.setData({
      currentPage: value
    }, () => {
      this.loadRecords();
    });
  },

  onsideChange(e) {
    this.setData({
      'currentRecord.side': this.data.sideOptions[e.detail.value]
    });
  },

  onInputChange(e) {
    const field = e.currentTarget.dataset.field;
    let value = e.detail.value.trim();
    if (field === 'quantity') {
      if (!/^\d*\.?\d*$/.test(value)) {
        return;
      }
    }
    this.setData({
      [`currentRecord.${field}`]: value
    });
  },

  showAddRecordModal() {
    this.setData({
      showModal: true,
      currentRecord: {
        productId: null,
        productName: null,
        warehouseName: null,
        semiRecords: [],
        screenMeshId: null,
        quantity: null,
        side: null
      }
    });
  },

  hideModal() {
    this.setData({
      showModal: false,
      additionalStorage: false
    });
  },

  async loadSemiProducts() {
    const res = await request('/api/semi-products/list', 'GET');
    if (res) {
      this.setData({ semiProductOptions: res.map(p => p.productName) });
    }
  },
  
  addSemiProductRecord() {
    const newRecord = {
      semiProductId: null,
      productName: null,
      productionDate: null,
      quantity: null
    };

    let updatedRecords = [...this.data.currentRecord.semiRecords, newRecord];
    
    this.setData({
      'currentRecord.semiRecords': updatedRecords
    });
  },
  
  removeSemiProductRecord(e) {
    const index = e.currentTarget.dataset.index;
  
    if (this.data.currentRecord.semiRecords.length === 0) {
      wx.showToast({ title: '没有可删除的记录', icon: 'none' });
      return;
    }
  
    let updatedRecords = [...this.data.currentRecord.semiRecords];
    updatedRecords.splice(index, 1);
  
    this.setData({ 'currentRecord.semiRecords': updatedRecords });
  },
  
  onSemiProductQuantityInput(e) {
    const index = e.currentTarget.dataset.index;
    const inputValue = e.detail.value.trim(); // 确保去除空格
  
    let records = this.data.currentRecord.semiRecords ? [...this.data.currentRecord.semiRecords] : [];
  
    if (!records[index]) {
      records[index] = { semiProductId: null, productName: '', productionDate: '', quantity: null };
    }
  
    records[index].quantity = inputValue === "" ? null : parseFloat(inputValue); // 允许为空
  
    this.setData({
      'currentRecord.semiRecords': records
    });
  
    console.log(`更新后的半成品数量 ${index}:`, records[index].quantity);
  },  

  async initFinishedProductPicker() {
    try {
      const finishedProducts = await request('/api/products/finished-products', 'GET');
  
      // 整理产品数据（分类 → 产品名称）
      const types = [...new Set(finishedProducts.map(p => p.productType || '未知类型'))];
      const productsByType = {};
  
      types.forEach(type => {
        productsByType[type] = finishedProducts
          .filter(p => (p.productType || '未知类型') === type)
          .map(p => ({
            id: p.productId,
            name: `${p.productName}`
          }));
      });
  
      // 默认选中第一个产品类别
      const firstType = types[0] || "暂无类型";
      const firstProducts = productsByType[firstType]?.map(p => p.name) || ["暂无产品"];
  
      this.setData({
        finishedProductOptions: [
          types,  // 第一列：产品类型
          firstProducts  // 第二列：产品名称
        ],
        finishedProductData: productsByType,
        finishedPickerIndexes: [0, 0]
      });
    } catch (e) {
      wx.showToast({ title: '成品列表加载失败', icon: 'none' });
    }
  },  

  async initSemiProductPicker() {
    try {
      const semiProducts = await request('/api/products/semi-products', 'GET');
  
      // 整理产品数据（分类 → 产品名称）
      const types = [...new Set(semiProducts.map(p => p.productType || '未知类型'))];
      const productsByType = {};
  
      types.forEach(type => {
        productsByType[type] = semiProducts
          .filter(p => (p.productType || '未知类型') === type)
          .map(p => ({
            id: p.productId,
            name: `${p.productName}`
          }));
      });
  
      // 默认选中第一个产品类别
      const firstType = types[0] || "暂无类型";
      const firstProducts = productsByType[firstType]?.map(p => p.name) || ["暂无产品"];
  
      this.setData({
        semiProductOptions: [
          types,  // 第一列：产品类型
          firstProducts  // 第二列：产品名称
        ],
        semiProductData: productsByType,
        semiPickerIndexes: [0, 0]
      });
    } catch (e) {
      wx.showToast({ title: '半成品列表加载失败', icon: 'none' });
    }
  },
  
  onFinishedProductPickerChange(e) {
    const { column, value } = e.detail;
    const pickerIndexes = [...this.data.finishedPickerIndexes];
    pickerIndexes[column] = value;
  
    if (column === 0) {  // 选择了产品类型
      const type = this.data.finishedProductOptions[0][value];
      const products = this.data.finishedProductData[type]?.map(p => p.name) || [];
  
      pickerIndexes[1] = 0;  // **重置第二列**
      this.setData({
        'finishedProductOptions[1]': products.length > 0 ? products : ["暂无产品"],
        finishedPickerIndexes: pickerIndexes
      });
    }
  },  

  onSemiProductPickerChange(e) {
    const { column, value } = e.detail;
    const pickerIndexes = [...this.data.semiPickerIndexes];
    pickerIndexes[column] = value;
  
    if (column === 0) {  // 选择了产品类型
      const type = this.data.semiProductOptions[0][value];
      const products = this.data.semiProductData[type]?.map(p => p.name) || [];
  
      pickerIndexes[1] = 0;  // **重置第二列**
      this.setData({
        'semiProductOptions[1]': products.length > 0 ? products : ["暂无产品"],
        semiPickerIndexes: pickerIndexes
      });
    }
  },
  
  onFinishedProductPickerConfirm(e) {
    const pickerIndexes = e.detail.value;
    const [typeIdx, productIdx] = pickerIndexes;
  
    const type = this.data.finishedProductOptions[0][typeIdx];
    const productList = this.data.finishedProductData[type];
  
    if (!productList || productList.length === 0) {
      wx.showToast({ title: '该类型暂无产品', icon: 'none' });
      return;
    }
  
    const product = productList[productIdx];
  
    this.setData({
      finishedPickerIndexes: pickerIndexes,
      'currentRecord.productId': product.id,
      'currentRecord.productName': product.name
    });
  },  

  onSemiProductPickerConfirm(e) {
    const index = e.currentTarget.dataset.index; // 获取当前的索引
    const pickerIndexes = e.detail.value;
    const [typeIdx, productIdx] = pickerIndexes;
  
    // 获取选中的产品类型
    const type = this.data.semiProductOptions[0][typeIdx];
    const productList = this.data.semiProductData[type];
  
    if (!productList || productList.length === 0) {
      wx.showToast({ title: '该类型暂无产品', icon: 'none' });
      return;
    }
  
    // 选中的产品信息
    const selectedProduct = productList[productIdx];
  
    let records = this.data.currentRecord.semiRecords ? [...this.data.currentRecord.semiRecords] : [];

    if (!records[index]) {
      records[index] = { semiProductId: null, productName: '', productionDate: '', quantity: null };
    }

    records[index].productName = selectedProduct.name; // 更新对应索引
    records[index].semiProductId = selectedProduct.id; 

    console.log(records);
    console.log(`选中的半成品记录 ${index}:`, records[index]);
    // 更新数据
    this.setData({
      'currentRecord.semiRecords': records
    });
  },  

  onProductionDateChange(e) {
    const index = e.currentTarget.dataset.index; // 获取索引
  
    const selectedDate = e.detail.value; // 获取用户选择的日期
  
    // 确保 semiRecords 存在
    let records = this.data.currentRecord.semiRecords ? [...this.data.currentRecord.semiRecords] : [];
  
    if (!records[index]) {
      records[index] = { semiProductId: null, productName: '', productionDate: '', quantity: null };
    }
  
    records[index].productionDate = selectedDate; // 更新生产日期
  
    this.setData({ 'currentRecord.semiRecords': records });
  },  

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

  showProductPicker() {
    this.setData({
      productPickerVisible: true
    });
  },

  toggleLabData(e) {
    const index = e.currentTarget.dataset.index; // 获取点击的记录索引
    const records = this.data.productRecords;

    records[index].showLabData = !records[index].showLabData; // 切换展开/隐藏状态

    this.setData({
      productRecords: records
    });
  },

  toggleSemiProductRecords(e) {
    const index = e.currentTarget.dataset.index;
    const records = this.data.productRecords;

    if (!records[index].semiProductRecords || records[index].semiProductRecords.length === 0) {
      wx.showToast({
        title: '暂无半成品记录',
        icon: 'none'
      });
      return;
    }
    records[index].showSemiProductRecords = !records[index].showSemiProductRecords;
    this.setData({ productRecords: records });
  },

  async submitNormalRecord() {
    try {
      if (!this.validateForm()) return;

      const payload = {
        productId: this.data.currentRecord.productId,
        warehouseName: this.data.currentRecord.warehouseName,
        quantity: parseFloat(this.data.currentRecord.quantity),
        side: this.data.currentRecord.side,
        semiRecords: this.data.currentRecord.semiRecords,
        screenMeshId: this.data.selectedScreenMeshId
      };

      console.log(payload)

      const res = await request('/api/in-stock/add', 'POST', payload);

      console.log("res:", res);
      console.log("remainingQuantity", res.remainingQuantity);
      console.log("message", res.message);

      this.hideModal();
      // 处理剩余数量
      if (res.remainingQuantity && res.remainingQuantity > 0) {
        wx.showModal({
          title: '提示',
          content: res.message || `当前库位已满，还剩${res.remainingQuantity}板，是否存入其他库位？`,
          confirmText: '是',
          cancelText: '否',
          success: (modalRes) => {
            if (modalRes.confirm) {
              this.promptAdditionalStorage(res.remainingQuantity);
            } else {
              wx.showToast({
                title: `入库已取消，还剩${res.remainingQuantity}板未入库`,
                icon: 'none'
              });
            }
          },
          fail: (err) => {
            console.error("wx.showModal 失败:", err);
          }
        });
      } else {
        wx.showToast({
          title: '入库成功'
        });
        this.loadRecords();
      }
    } catch (error) {
      console.log(error)
      wx.showToast({
        title: error.message,
        icon: 'none'
      });
    }
  },

  // 提示用户选择额外库位的方法
  promptAdditionalStorage(remainingQuantity) {
    this.setData({
      showModal: true,
      currentRecord: {
        ...this.data.currentRecord,
        quantity: remainingQuantity,
        warehouseName: '',
        side: null
      },
      sideOptions: ['左', '右'],
      additionalStorage: true,
      extraStorage: true
    });
  },

  // 提交额外库位入库请求
  async submitAdditionalRecord() {
    try {
      if (!this.validateForm()) return;

      const payload = {
        productId: this.data.currentRecord.productId,
        warehouseName: this.data.currentRecord.warehouseName,
        quantity: this.data.currentRecord.quantity,
        side: this.data.currentRecord.side,
        semiRecords: this.data.currentRecord.semiRecords,
        screenMeshId: this.data.selectedScreenMeshId
      };

      const res = await request('/api/in-stock/add', 'POST', payload);

      console.log("remainingQuantity", res.remainingQuantity);
      console.log("message", res.message);
      if (res.remainingQuantity > 0) {
        wx.showModal({
          title: '库位不足',
          content: `当前库位已满！
          还剩${res.remainingQuantity}板，确认不存入另一库位？`,
          confirmText: '确定',
          cancelText: '继续入库',
          success: (confirmRes) => {
            if (confirmRes.confirm) {
              wx.showToast({
                title: '已结束入库',
                icon: 'none'
              });
              this.hideModal();
              this.loadRecords();
            } else {
              // 用户继续选择其他库位
              this.promptAdditionalStorage(res.remainingQuantity);
            }
          }
        });
      } else {
        wx.showToast({
          title: '入库成功'
        });
        this.hideModal();
        this.loadRecords();
      }
    } catch (error) {
      console.log(error.message);
      wx.showToast({
        title: error.message || '操作失败: 此库位已满！',
        icon: 'none'
      });
    }
  },

  // 修改 submitRecord 以区分普通提交和额外入库
  submitRecord() {
    if (this.data.additionalStorage) {
      this.submitAdditionalRecord();
    } else {
      this.submitNormalRecord();
    }
  },

  validateForm() {
    const {
      currentRecord
    } = this.data;
    const validations = [{
        field: 'productId',
        message: '请选择产品',
        type: 'number'
      },
      {
        field: 'warehouseName',
        message: '请输入库位名称',
        type: 'string'
      },
      {
        field: 'quantity',
        message: '请输入数量',
        type: 'number'
      },
      {
        field: 'side',
        message: '请选择入库顺序',
        type: 'string'
      }
    ];

    for (const {
        field,
        message
      } of validations) {
      const value = currentRecord[field];

      if (value === null || value === undefined || value === '') {
        wx.showToast({
          title: message,
          icon: 'none'
        });
        return false;
      }

      if (typeof value === 'string' && value.trim() === '') {
        wx.showToast({
          title: message,
          icon: 'none'
        });
        return false;
      }
    }
    return true;
  },

  resetFilters() {
    this.setData({
      queryParams: {
        productName: '',
        warehouseName: '',
        operatorName: '',
        startDate: null,
        endDate: null,
        page: 1,
        size: 10
      },
      currentPage: 1
    }, () => this.loadRecords());
  }
});

function debounce(fn, delay) {
  let timer = null;
  return function (...args) {
    clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), delay);
  };
}