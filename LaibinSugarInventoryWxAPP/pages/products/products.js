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
      screenMeshId: null
    },
    packagingMethodOptions: ["袋", "箱", "罐"],
    productTypeOptions: ["白冰糖", "黄冰糖"],
    statusOptions: ["半成品", "成品"],
    canStackOptions: ["是", "否"],
    screenMeshOptions: [],
    screenMeshMap: [],
    screenMeshNameMap: {},
    selectedScreenMeshIndex: 0, // 🔥 当前选中的索引
  },

  async loadScreenMeshes() {
    try {
      const res = await request('/api/screen-mesh/list', 'GET');
      console.log('筛网列表:', res);
  
      const list = Array.isArray(res) ? res :
      (Array.isArray(res.data) ? res.data : []);

      if (list.length) {
        const options = list.map(mesh => ({
          id: mesh.id,
          name: `${mesh.meshName} (${mesh.description || '无描述'})`
        }));
  
        const optionNames = options.map(o => o.name);
        const nameMap = {};
        options.forEach(o => { nameMap[o.id] = o.name; });
  
        this.setData({
          screenMeshOptions: optionNames,
          screenMeshMap: options,
          screenMeshNameMap: nameMap,
          selectedScreenMeshIndex: 0
        });
      }
    } catch (error) {
      wx.showToast({ title: '筛网加载失败', icon: 'none' });
      console.error("筛网加载失败:", error);
    }
  },  

  onLoad() {
    this.loadScreenMeshes(); // 🔥 先加载筛网
    this.getProducts();
  },

  showAddProductModal() {
    const firstMesh = this.data.screenMeshMap[0];
    // 🔥 打开弹窗时重置选中索引
    this.setData({ 
      showModal: true,
      selectedScreenMeshIndex: firstMesh ? 0 : -1,
      'newProduct.screenMeshId': firstMesh ? firstMesh.id : null
    });
  },

  hideModal() {
    // 🔥 关闭弹窗时重置表单
    this.setData({ 
      showModal: false,
      newProduct: {
        productName: "",
        packagingMethod: "",
        productType: "",
        status: "",
        weightPerPiece: "",
        piecesPerPallet: "",
        canStack: true,
        screenMeshId: null
      },
      selectedScreenMeshIndex: 0
    });
  },

  noop() {},

  onInputChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({
      [`newProduct.${field}`]: e.detail.value,
    });
  },

  onPickerChange(e) {
    const field = e.currentTarget.dataset.field;
    const value = e.detail.value;
  
    let options;
    if (field === "selectedType") {
      options = this.data.productTypeOptions;
    } else if (field === "selectedStatus") {
      options = this.data.statusOptions;
    } else {
      options = this.data[`${field}Options`];
    }
  
    if (!options || value < 0 || value >= options.length) {
      console.error(`Picker 选项错误: field=${field}, value=${value}`);
      return;
    }
  
    let selectedValue = options[value];
  
    // 处理筛选栏 picker
    if (field === "selectedType" || field === "selectedStatus") {
      this.setData({
        [field]: selectedValue,
      }, () => {
        this.getProducts();
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

  // 🔥 修复筛网选择逻辑
  onScreenMeshChange(e) { 
    const selectedIndex = parseInt(e.detail.value);
    const selectedMesh = this.data.screenMeshMap[selectedIndex];
  
    if (!selectedMesh) {
      console.error("无效的筛网选择索引:", selectedIndex);
      return;
    }
  
    console.log('选中筛网:', selectedMesh);
  
    // 🔥 同时更新索引和产品的 screenMeshId
    this.setData({ 
      selectedScreenMeshIndex: selectedIndex,
      'newProduct.screenMeshId': selectedMesh.id // 🔥 关键修复：写入到 newProduct
    });
  },  

  resetFilters() {
    this.setData({
      searchText: "",
      selectedType: "",
      selectedStatus: "",
    }, () => {
      this.getProducts();
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

  // 🔥 修复提交逻辑
  submitNewProduct() {
    const { newProduct } = this.data;
    
    // 🔥 验证必填字段
    if (!newProduct.productName || !newProduct.screenMeshId) {
      wx.showToast({ title: "请填写完整信息", icon: "none" });
      return;
    }

    console.log('提交的产品数据:', newProduct);

    request("/api/products", "POST", newProduct)
      .then(() => {
        wx.showToast({ title: "新增成功", icon: "success" });
        this.hideModal();
        this.getProducts();
      })
      .catch((error) => {
        console.error('新增失败:', error);
        wx.showToast({ title: "新增失败", icon: "none" });
      });
  }
});
