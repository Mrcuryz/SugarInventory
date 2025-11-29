import request from "../../utils/request";

Page({
  data: {
    activeName: '成品',
    tableData: [],
    productName: ''
  },

  onLoad() {
    this.handleClick();
  },

  onProductNameInput(e) {
    this.setData({
      productName: e.detail.value
    });
  },

  switchTab(e) {
    this.setData({
      activeName: e.currentTarget.dataset.name
    }, () => {
      this.handleClick();
    });
  },
  handleClick() {
   request('/api/inventory/stock', 'GET', {
      productStatus: this.data.activeName,
      productName: this.data.productName
    }).then(res => {
      console.log("接口返回：", res);
      this.setData({
        tableData: res || []
      });
    }).catch(err => {
      console.error("请求失败：", err);
      wx.showToast({
        title: '请求失败',
        icon: 'none'
      });
    });
  }
});