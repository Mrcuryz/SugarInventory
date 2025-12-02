import request from "../../utils/request";

Page({
  data: {
    product: {},
    packagingMethodOptions: ["袋", "箱", "罐"],
    productTypeOptions: ["白冰糖", "黄冰糖"],
    statusOptions: ["半成品", "成品"],
    canStackOptions: ["是", "否"],
    screenMeshOptions: [],
    screenMeshMap: [], // 🔥 存储完整的筛网对象数组
    selectedScreenMeshIndex: 0, // 🔥 当前选中的索引
  },

  onLoad(options) {
    // 🔥 先加载筛网，再加载产品详情（确保筛网数据已就绪）
    this.loadScreenMeshes().then(() => {
      this.getProductDetail(options.id);
    });
  },

  async loadScreenMeshes() {
    try {
      const res = await request('/api/screen-mesh/list', 'GET');
      console.log('筛网列表:', res);
  
      if (res && Array.isArray(res)) {
        const options = res.map(mesh => ({
          id: mesh.id,
          name: `${mesh.meshName} (${mesh.description || '无描述'})`
        }));
  
        const optionNames = options.map(option => option.name);
  
        this.setData({ 
          screenMeshOptions: optionNames,
          screenMeshMap: options
        });

        return Promise.resolve(); // 🔥 返回 Promise 确保加载完成
      }
    } catch (error) {
      wx.showToast({ title: '筛网加载失败', icon: 'none' });
      console.error("筛网加载失败:", error);
      return Promise.reject(error);
    }
  },  

  getProductDetail(id) {
    request(`/api/products/${id}`, "GET")
      .then((data) => {
        console.log('产品详情:', data);

        // 🔥 关键修复：根据 screenMeshId 找到对应的索引
        let meshIndex = 0;
        if (data.screenMeshId) {
          meshIndex = this.data.screenMeshMap.findIndex(
            mesh => mesh.id === data.screenMeshId
          );
          if (meshIndex === -1) meshIndex = 0; // 找不到则默认第一个
        }

        console.log('筛网索引:', meshIndex);

        this.setData({ 
          product: data,
          selectedScreenMeshIndex: meshIndex // 🔥 设置正确的索引
        });
      })
      .catch(() => {
        wx.showToast({ title: "获取产品信息失败", icon: "none" });
      });
  },

  onInputChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({
      [`product.${field}`]: e.detail.value,
    });
  },

  hideModal() {
    wx.navigateBack();
  },

  onPickerChange(e) {
    const field = e.currentTarget.dataset.field;
    const value = e.detail.value;
    const options = this.data[`${field}Options`];
  
    if (!options || value < 0 || value >= options.length) {
      console.error(`Picker 数据异常: ${field}Options`);
      return;
    }
  
    let selectedValue = options[value];
  
    // 处理 `canStack` 转换
    if (field === "canStack") {
      selectedValue = selectedValue === "是";
    }
  
    this.setData({
      [`product.${field}`]: selectedValue,
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
  
    // 🔥 关键修复：同时更新索引和 product.screenMeshId
    this.setData({ 
      selectedScreenMeshIndex: selectedIndex,
      'product.screenMeshId': selectedMesh.id // 🔥 写入到 product 对象
    });
  },  
  
  submitForm() {
    let product = this.data.product;

    // 🔥 验证必填字段
    if (!product.productName || !product.screenMeshId) {
      wx.showToast({ title: "请填写完整信息", icon: "none" });
      return;
    }
  
    // 确保字段与后端 DTO 对齐
    const requestBody = {
      productId: product.id,
      productName: product.productName,
      productType: product.productType,
      status: product.status,
      packagingMethod: product.packagingMethod,
      weightPerPiece: parseFloat(product.weightPerPiece),
      piecesPerPallet: parseInt(product.piecesPerPallet, 10),
      canStack: product.canStack === true || product.canStack === "是",
      screenMeshId: product.screenMeshId // 🔥 确保传递筛网ID
    };

    console.log('提交的修改数据:', requestBody);

    request(`/api/products`, "PUT", requestBody)
      .then(() => {
        wx.showToast({ title: "修改成功", icon: "success" });
  
        setTimeout(() => {
          let pages = getCurrentPages();
          let prevPage = pages[pages.length - 2];
  
          if (prevPage && prevPage.getProducts) {
            prevPage.getProducts();
          }
  
          wx.navigateBack();
        }, 1000);
      })
      .catch((error) => {
        console.error('修改失败:', error);
        wx.showToast({ title: "修改失败", icon: "none" });
      });
  }  
});