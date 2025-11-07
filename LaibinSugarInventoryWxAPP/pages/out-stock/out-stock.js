import request from "../../utils/request";
import dayjs from 'dayjs';

Page({
  data: {
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
    currentRecord: {
      productId: null,
      productName: null,
      warehouseName: null,
      quantity: null,
      side: null
    },
    role: ''
  },

  onLoad() {
    this.loadRecords();
    const role = wx.getStorageSync("role") || '';
    this.setData({ role });
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

      const res = await request('/api/out-stock/records', 'POST', params);

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
        showLabData: false
      }));

      this.setData({
        semiProductRecords: processedData,
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

  toggleLabData(e) {
    const index = e.currentTarget.dataset.index; // 获取点击的记录索引
    const records = this.data.semiProductRecords;

    records[index].showLabData = !records[index].showLabData; // 切换展开/隐藏状态

    this.setData({
      semiProductRecords: records
    });
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