import request from "../../utils/request";
import dayjs from 'dayjs';

Page({
  data: {
    productPickerVisible: false,
    productOptions: [[], [], []], // picker数据源（三列）
    pickerIndexes: [0, 0, 0],     // 每一列的当前索引
    productMap: {
      '成品': [],
      '半成品': []
    },
    labData: [], // 化验数据列表
    queryParams: {
      productName: '',
      testerName: '',
      startDate: null,
      endDate: null,
      page: 1,
      size: 10
    },
    totalPages: 1,
    currentPage: 1,
    showModal: false,
    editMode: false,
    currentLabData: {
      id: null,
      productId: null,
      productName: null,
      sampleDate: null,
      colorValue: null,
      reducingSugar: null,
      dryWeight: null,
      conductivityAsh: null,
      sucrose: null,
      insolubleImpurity: null,
      phValue: null,
      testerName: null,
      isQualified: null,
      qualifiedStandards: []
    }
  },

  onLoad() {
    this.loadLabData();
    this.initProductPicker();
  },

  async loadLabData() {
    try {
      const params = {
        ...this.data.queryParams,
        page: this.data.currentPage,
        size: this.data.queryParams.size
      };

      if (!params.startDate) params.startDate = null;
      if (!params.endDate) params.endDate = null;
      if (!params.productName) delete params.productName;
      if (!params.testerName) delete params.testerName;

      const res = await request('/api/assay/query', 'POST', params);
      
      if (!res || !res.records) {
        wx.showToast({ title: '数据加载失败', icon: 'none' });
        return;
      }

      const processedData = res.records.map(item => ({
        ...item,
        sampleDate: dayjs(item.sampleDate).format('YYYY-MM-DD'),
        createdAt: dayjs(item.createdAt).format('YYYY-MM-DD HH:mm'),
        qualifiedStandards: item.qualifiedStandards ? JSON.parse(item.qualifiedStandards) : []
      }));

      const groupedData = processedData.reduce((acc, cur) => {
        const key = `${cur.sampleDate}_${cur.productId}`; // 使用采样日期和产品id作为组合key
        if (!acc[key]) {
          acc[key] = [];
        }
        acc[key].push(cur);
        return acc;
      }, {});

      // 提取每组最新版本数据
      const latestData = Object.values(groupedData).map(group => {
        group.sort((a, b) => b.version - a.version);
        return {
          latest: group[0],       // 最新版本
          history: group.slice(1), // 历史版本
          showHistory: false      // 默认隐藏历史版本
        };
      });

      this.setData({
        labData: latestData,
        totalPages: Math.ceil(res.total / this.data.queryParams.size),
        currentPage: this.data.currentPage
      });
    } catch (error) {
      console.error("数据加载失败:", error);
      wx.showToast({ title: '数据加载失败', icon: 'none' });
    }
  },

  onSearchInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({
      [`queryParams.${field}`]: e.detail.value
    });
    this.debouncedLoadData();
  },

  toggleHistory(e) {
    const index = e.currentTarget.dataset.record;
    const labData = this.data.labData;
  
    labData[index].showHistory = !labData[index].showHistory;
  
    this.setData({ labData });
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

  debouncedLoadData: debounce(function() {
    this.setData({ currentPage: 1 }, () => {
      this.loadLabData();
    });
  }, 500),

  prevPage() {
    if (this.data.currentPage > 1) {
      this.setData({
        currentPage: this.data.currentPage - 1
      }, () => this.loadLabData());
    }
  },

  nextPage() {
    if (this.data.currentPage < this.data.totalPages) {
      this.setData({
        currentPage: this.data.currentPage + 1
      }, () => this.loadLabData());
    }
  },
  onPageInput(e) {
    const value = Math.min(Math.max(e.detail.value, 1), this.data.totalPages);
    this.setData({ currentPage: value }, () => {
      this.loadLabData();
    });
  },

  onInputChange(e) {
    const field = e.currentTarget.dataset.field;
    let value = e.detail.value;
    
    if (!/^\d*\.?\d*$/.test(value)) {
      value = value.replace(/[^\d.]/g, ''); 
      value = value.replace(/\.{2,}/g, '.'); 
      value = value.replace(/^(\d*\.\d*?)\..*/g, '$1'); 
    }

    this.setData({
      [`currentLabData.${field}`]: value
    });
  },

  showAddLabDataModal() {
    this.setData({ 
      showModal: true,
      editMode: false,
      currentLabData: {
        id: null,
        productName: null,
        sampleDate: null,
        colorValue: null,
        reducingSugar: null,
        dryWeight: null,
        conductivityAsh: null,
        sucrose: null,
        insolubleImpurity: null,
        phValue: null,
        testerName: null,
        isQualified: null,
        qualifiedStandards: []
      }
    });
  },

  editLabData(e) {
    const id = e.currentTarget.dataset.id;
    const labItem = this.data.labData.find(item => item.latest.id === id);
    const labData = labItem?.latest;
  
    if (!labData) {
      wx.showToast({ title: '未找到对应记录', icon: 'none' });
      return;
    }
  
    this.setData({ 
      showModal: true,
      editMode: true,
      currentLabData: {
        id: labData.id,
        sampleDate: dayjs(labData.sampleDate).format('YYYY-MM-DD'),
        productId: labData.productId,
        productName: labData.productName,
        colorValue: labData.colorValue,
        reducingSugar: labData.reducingSugar,
        dryWeight: labData.dryWeight,
        conductivityAsh: labData.conductivityAsh,
        sucrose: labData.sucrose,
        insolubleImpurity: labData.insolubleImpurity,
        phValue: labData.phValue,
        testerName: labData.testerName,
        isQualified: labData.isQualified,
        qualifiedStandards: labData.qualifiedStandards
      }
    });
  },  

  hideModal() {
    this.setData({ showModal: false });
  },

  async initProductPicker() {
    try {
      const [semiProducts, finishedProducts] = await Promise.all([
        request('/api/products/semi-products', 'GET'),
        request('/api/products/finished-products', 'GET')
      ]);
  
      const organizeProducts = (products, categoryLabel) => {
        const types = [...new Set(products.map(p => p.productType || '未知类型'))];
        const productsByType = {};
  
        types.forEach(type => {
          productsByType[type] = products
            .filter(p => (p.productType || '未知类型') === type)
            .map(p => ({
              id: p.productId,
              name: `${p.productName}`
            }));
        });
  
        return { categoryLabel, types, productsByType };
      };
  
      const semiProductData = organizeProducts(semiProducts, '半成品');
      const finishedProductData = organizeProducts(finishedProducts, '成品');
  
      this.productPickerData = {
        '成品': finishedProductData,
        '半成品': semiProductData
      };
  
      const firstCategory = '成品'; // 默认选中"成品"
      const firstType = finishedProductData.types[0] || "暂无类型"; // 默认选中第一种产品类型
      const firstProducts = finishedProductData.productsByType[firstType]?.map(p => p.name) || ["暂无产品"]; // 默认产品列表
  
      this.setData({
        productOptions: [
          ['成品', '半成品'],  // 第一列：成品/半成品
          finishedProductData.types,  // 第二列：产品类型
          firstProducts  // 第三列：产品名称
        ],
        pickerIndexes: [0, 0, 0] // **确保picker索引重置**
      });
    } catch (e) {
      wx.showToast({ title: '产品列表加载失败', icon: 'none' });
    }
  },   
  
  showProductPicker() {
    this.setData({ productPickerVisible: true });
  },
  
  onProductPickerColumnChange(e) {
    const { column, value } = e.detail;
    const pickerIndexes = [...this.data.pickerIndexes]; 
    pickerIndexes[column] = value; 
  
    if (column === 0) { // 用户切换了第一列（成品/半成品）
      const category = this.data.productOptions[0][value]; // 获取选中的分类（成品/半成品）
      const types = this.productPickerData[category].types; // 获取该分类下的产品类型
      const products = this.productPickerData[category].productsByType[types[0]]?.map(p => p.name) || []; // 获取该分类下的第一个产品类型的产品列表
  
      pickerIndexes[1] = 0; // **重置第二列的选中索引**
      pickerIndexes[2] = 0; // **重置第三列的选中索引**
  
      this.setData({
        'productOptions[1]': types,  // 更新产品类型
        'productOptions[2]': products.length > 0 ? products : ["暂无产品"], // **确保 productOptions[2] 直接覆盖，不叠加**
        pickerIndexes
      });
    } else if (column === 1) { // 用户切换了第二列（产品类型）
      const category = this.data.productOptions[0][pickerIndexes[0]]; // 获取当前选中的分类（成品/半成品）
      const type = this.data.productOptions[1][value]; // 获取当前选中的产品类型
      const products = this.productPickerData[category].productsByType[type]?.map(p => p.name) || []; // 获取该产品类型下的产品列表
  
      pickerIndexes[2] = 0; // **重置第三列的选中索引**
  
      this.setData({
        'productOptions[2]': products.length > 0 ? products : ["暂无产品"], // **确保 productOptions[2] 直接覆盖**
        pickerIndexes
      });
    }
  },  
  
  onProductPickerConfirm(e) {
    const pickerIndexes = e.detail.value;
    const [categoryIdx, typeIdx, productIdx] = pickerIndexes;
  
    const category = this.data.productOptions[0][categoryIdx];
    const type = this.data.productOptions[1][typeIdx];
    const productList = this.productPickerData[category].productsByType[type];
  
    if (!productList || productList.length === 0) {
      wx.showToast({ title: '该类型暂无产品', icon: 'none' });
      return;
    }
  
    const product = productList[productIdx];
  
    this.setData({
      pickerIndexes,
      'currentLabData.productId': product.id,
      'currentLabData.productName': product.name
    });
  },  

  onSampleDateChange(e) {
    const date = e.detail.value;
    this.setData({
      'currentLabData.sampleDate': date
    });
  },  

  async submitLabData() {
    try {
      if (!this.validateForm()) return;
      const payload = this.buildPayload();

      if (this.data.editMode) {
        await request(`/api/assay/${this.data.currentLabData.id}`, 'POST', payload);
      } else {
        await request('/api/assay/import', 'POST', [payload]);
      }

      wx.showToast({ title: '操作成功' });
      this.hideModal();
      this.loadLabData();
    } catch (error) {
      wx.showToast({ title: error.message || '操作失败', icon: 'none' });
    }
  },

  validateForm() {
    const { currentLabData } = this.data;
    const validations = [
      { field: 'productName', message: '请选择产品' },
      { field: 'sampleDate', message: '请选择采样日期' },
      { 
        field: 'colorValue', 
        validate: (v) => v >= 0 && v <= 500,
        message: '色值需在0-500之间'
      },
    ];

    for (const { field, validate, message } of validations) {
      if (typeof validate === 'function' ? !validate(currentLabData[field]) : !currentLabData[field]) {
        wx.showToast({ title: message, icon: 'none' });
        return false;
      }
    }
    return true;
  },

  buildPayload() {
    return {
      productId: this.data.currentLabData.productId,
      sampleDate: this.data.currentLabData.sampleDate,
      colorValue: this.data.currentLabData.colorValue,
      reducingSugar: this.data.currentLabData.reducingSugar,
      dryWeight: this.data.currentLabData.dryWeight,
      conductivityAsh: this.data.currentLabData.conductivityAsh,
      sucrose: this.data.currentLabData.sucrose,
      insolubleImpurity: this.data.currentLabData.insolubleImpurity,
      phValue: this.data.currentLabData.phValue
    };
  },

  resetFilters() {
    this.setData({
      queryParams: {
        productName: '',
        testerName: '',
        startDate: null,
        endDate: null,
        page: 1,
        size: 10
      },
      currentPage: 1
    }, () => this.loadLabData());
  }
});

function debounce(fn, delay) {
  let timer = null;
  return function(...args) {
    clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), delay);
  };
}
