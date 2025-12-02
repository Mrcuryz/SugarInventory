import request from "../../utils/request";

Page({
  data: {
    warehouses: [],
    searchName: null,
    showModal: false,
    editMode: false,
    currentWarehouse: {
      id:null,
      warehouseName: "",
      maxRows: "",
    }
  },

  methods: {
    getStatusColor(status) {
      switch (status) {
        case "正常": return "green";
        case "空置": return "lightgray";
        case "临期预警": return "orange";
        case "满仓": return "red";
        case "维护": return "black";
        default: return "gray";
      }
    },
  },

  onLoad() {
    this.getWarehouses();
  },

  getWarehouses() {
    wx.showLoading({ title: '加载中...' });
    const queryParams = {};
    if (this.data.searchName) {
      queryParams.name = this.data.searchName;
    }

    request("/api/warehouse/query", "GET", queryParams)
      .then(data => {
        const warehouses = data.map(item => ({
          ...item,
          statusColor: this.methods.getStatusColor(item.status) // 调用 methods 中的方法
        }));
        
        this.setData({ warehouses });
      })
      .catch(() => {
        wx.showToast({ title: "获取库位失败", icon: "none" });
      })
      .finally(() => wx.hideLoading());
  },

  onSearchInput(e) {
    this.setData({ searchName: e.detail.value });
  },

  showAddWarehouseModal() {
    this.setData({ 
      showModal: true,
      editMode: false,
      currentWarehouse: { 
        warehouseName: "",  // 清空字段
        maxRows: "",
        id: null           // 确保无 id
      }
    });
  },

  hideModal() {
    this.setData({ showModal: false });
  },

  editWarehouse(e) {
    const id = e.currentTarget.dataset.id;
    const warehouse = this.data.warehouses.find(item => item.id === id);
    
    this.setData({ 
      showModal: true, 
      editMode: true,
      currentWarehouse: { 
        ...warehouse,
        id: warehouse.id // 确保传递 id
      }
    });
  },

  onInputChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({
      [`currentWarehouse.${field}`]: e.detail.value
    });
  },

  submitWarehouse() {
    // 分离参数结构
    const payload = this.data.editMode 
      ? {
          id: this.data.currentWarehouse.id, // 必须包含 id
          warehouseName: this.data.currentWarehouse.warehouseName,
          maxRows: this.data.currentWarehouse.maxRows
        }
      : {
          warehouseName: this.data.currentWarehouse.warehouseName,
          maxRows: this.data.currentWarehouse.maxRows
        };
  
    // 区分请求配置
    const requestConfig = this.data.editMode 
      ? { url: "/api/warehouse/update", method: "PUT" } // 使用 PUT 方法
      : { url: "/api/warehouse/create", method: "POST" };
  
    request(requestConfig.url, requestConfig.method, payload)
      .then(() => {
        wx.showToast({ title: this.data.editMode ? "更新成功" : "新增成功", icon: "success" });
        this.setData({ showModal: false });
        this.getWarehouses();
      })
      .catch((error) => {
        console.error("操作失败:", error);
        wx.showToast({ title: "操作失败", icon: "none" });
      });
  },

// 修改维护状态切换方法
toggleMaintenance(e) {
  const id = e.currentTarget.dataset.id;
  const currentStatus = e.currentTarget.dataset.currentStatus;
  const isMaintaining = currentStatus === '维护';
  
  wx.showModal({
    title: "确认",
    content: isMaintaining ? "确定要取消维护状态？" : "确定要设为维护状态？",
    success: (res) => {
      if (res.confirm) {
        request(`/api/warehouse/maintain/${id}`, "PUT", { maintain: !isMaintaining })
          .then(() => {
            wx.showToast({ title: isMaintaining ? "已取消维护" : "已设为维护" });
            this.getWarehouses(); // 刷新列表
          });
        }
      }
    });
  },

  deleteWarehouse(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: "确认删除",
      content: "确定要删除该库位吗？",
      success: (res) => {
        if (res.confirm) {
          request(`/api/warehouse/delete/${id}`, "DELETE")
            .then(() => {
              wx.showToast({ title: "删除成功", icon: "success" });
              this.getWarehouses();
            })
            .catch(() => {
              wx.showToast({ title: "删除失败：该库位中存在库存！", icon: "none" });
            });
        }
      }
    });
  }
});
