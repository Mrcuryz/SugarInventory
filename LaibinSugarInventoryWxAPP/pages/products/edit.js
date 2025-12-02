import request from "../../utils/request";

Page({
  data: {
    product: {},
    packagingMethodOptions: ["袋", "箱", "罐"],
    productTypeOptions: ["白冰糖", "黄冰糖"],
    statusOptions: ["半成品", "成品"],
    canStackOptions: ["是", "否"],
  },

  onLoad(options) {
    this.getProductDetail(options.id);
  },

  getProductDetail(id) {
    request(`/api/products/${id}`, "GET")
      .then((data) => {
        this.setData({ product: data });
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
      selectedValue = selectedValue === "是" ? true : false;
    }
  
    this.setData({
      [`product.${field}`]: selectedValue,
    });
  },  
  
  submitForm() {
    let product = this.data.product;
  
    // 确保字段与后端 DTO 对齐
    const requestBody = {
      productId: product.id,  // 修改 `id` 为 `productId`
      productName: product.productName,
      productType: product.productType,
      status: product.status,
      packagingMethod: product.packagingMethod,
      weightPerPiece: parseFloat(product.weightPerPiece),  // 转换为浮点数
      piecesPerPallet: parseInt(product.piecesPerPallet, 10),  // 转换为整数
      canStack: product.canStack === "是" ? true : false,  // 转换为 Boolean
    };
    request(`/api/products`, "PUT", requestBody)
    .then(() => {
      wx.showToast({ title: "修改成功", icon: "success" });
  
      setTimeout(() => {
        let pages = getCurrentPages();
        let prevPage = pages[pages.length - 2]; // 上一页
  
        if (prevPage && prevPage.getProducts) {
          prevPage.getProducts(); // 重新获取产品列表
        }
  
        wx.navigateBack(); // 返回上一页
      }, 1000); // 延迟 1 秒再跳转
    })
    .catch(() => {
      wx.showToast({ title: "修改失败", icon: "none" });
    });
  }  
});
