import request from "../../utils/request";

Page({
  data: {
    products: [],
    searchText: "",
    selectedType: "",
    selectedStatus: "",
    showModal: false,
    newProduct: {
      productName: "",
      packagingMethod: "",
      productType: "",
      status: "",
      weightPerPiece: "",
      piecesPerPallet: "",
      canStack: true,
    },
    packagingMethodOptions: ["袋", "箱", "罐"],
    productTypeOptions: ["白冰糖", "黄冰糖"],
    statusOptions: ["半成品", "成品"],
    canStackOptions: ["是", "否"],
  },

  onLoad() {
    this.getProducts();
  },

  showAddProductModal() {
    this.setData({ showModal: true });
  },

  hideModal() {
    this.setData({ showModal: false });
  },

  onInputChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({
      [`newProduct.${field}`]: e.detail.value,
    });
  },

  onPickerChange(e) {
    const field = e.currentTarget.dataset.field; // 选中的 picker 字段
    const value = e.detail.value; // 选中的 index
  
    // 确保 options 变量正确
    let options;
    if (field === "selectedType") {
      options = this.data.productTypeOptions;
    } else if (field === "selectedStatus") {
      options = this.data.statusOptions;
    } else {
      options = this.data[`${field}Options`];
    }
  
    if (!options || value < 0 || value >= options.length) {
      console.error(`Picker 选项错误: field=${field}, value=${value}, options=`, options);
      return;
    }
  
    let selectedValue = options[value];
  
    // 处理筛选栏 picker
    if (field === "selectedType" || field === "selectedStatus") {
      this.setData({
        [field]: selectedValue,
      }, () => {
        this.getProducts(); // 触发筛选
      });
      return;
    }
  
    // 处理新增产品 picker
    if (field === "canStack") {
      selectedValue = selectedValue === "是";
    }
  
    this.setData({
      [`newProduct.${field}`]: selectedValue,
    });
  },  

  resetFilters() {
    this.setData({
      searchText: "",
      selectedType: "",
      selectedStatus: "",
    }, () => {
      this.getProducts(); // 重新获取全部数据
    });
  },

  onSearchInput(e) {
    this.setData({ searchText: e.detail.value });
    this.getProducts();
  },

  getProducts() {
    const { searchText, selectedType, selectedStatus } = this.data;
    const queryParams = {};
    if (searchText) queryParams.name = searchText;
    if (selectedType) queryParams.type = selectedType;
    if (selectedStatus) queryParams.status = selectedStatus;
    request("/api/products/product", "GET", queryParams)
      .then(data => {
        this.setData({ products: data });
      })
      .catch(() => {
        wx.showToast({ title: "获取产品失败", icon: "none" });
      });
  },

  editProduct(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/products/edit?id=${id}` });
  },

  deleteProduct(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: "确认删除",
      content: "确定要删除该产品吗？",
      success: res => {
        if (res.confirm) {
          request(`/api/products/${id}`, "DELETE")
            .then(() => {
              wx.showToast({ title: "删除成功", icon: "success" });
              this.getProducts();
            })
            .catch(() => {
              wx.showToast({ title: "删除失败:存在该产品的相关记录！", icon: "none" });
            });
        }
      }
    });
  },
  submitNewProduct() {
    request("/api/products", "POST", this.data.newProduct)
      .then(() => {
        wx.showToast({ title: "新增成功", icon: "success" });
        this.setData({ showModal: false });
        this.getProducts();
      })
      .catch(() => {
        wx.showToast({ title: "新增失败", icon: "none" });
      });
  }
});
