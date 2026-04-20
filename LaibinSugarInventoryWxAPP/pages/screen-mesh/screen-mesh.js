import request from "../../utils/request";

Page({
  data: {
    screenMeshes: [],
    searchText: "",
    showModal: false,
    editMode: false,
    currentScreenMesh: { meshName: "", description: "" },
  },

  onLoad() {
    this.getScreenMeshes();
  },

  onSearchInput(e) {
    this.setData({ searchText: e.detail.value });
    this.getScreenMeshes();
  },

  onInputChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({
      [`currentScreenMesh.${field}`]: e.detail.value
    });
  },

  getScreenMeshes() {
    const queryParams = {};
    if (this.data.searchText) {
      queryParams.meshName = this.data.searchText;
    }
    request("/api/screen-mesh/list", "GET", queryParams)
      .then(data => {
        this.setData({ screenMeshes: data });
      })
      .catch(() => {
        wx.showToast({ title: "获取筛网失败", icon: "none" });
      });
  },

  hideModal() {
    this.setData({ showModal: false });
  },

  noop() {},

  showAddScreenMeshModal() {
    this.setData({ showModal: true, editMode: false, currentScreenMesh: { meshName: "", description: "" } });
  },

  editScreenMesh(e) {
    const id = e.currentTarget.dataset.id;
    const selectedMesh = this.data.screenMeshes.find(item => item.id === id);
    this.setData({ showModal: true, editMode: true, currentScreenMesh: selectedMesh });
  },

  deleteScreenMesh(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: "确认删除",
      content: "确定要删除该筛网吗？",
      success: res => {
        if (res.confirm) {
          request(`/api/screen-mesh/delete/${id}`, "DELETE")
            .then(() => {
              wx.showToast({ title: "删除成功", icon: "success" });
              this.getScreenMeshes();
            })
            .catch(() => {
              wx.showToast({ title: "删除失败：存在该筛网的相关记录！", icon: "none" });
            });
        }
      }
    });
  },

  submitScreenMesh() {
    if (!this.data.currentScreenMesh.meshName) {
      wx.showToast({ title: "筛网名称不能为空", icon: "none" });
      return;
    }
    const payload = this.data.editMode 
      ? { 
          id: this.data.currentScreenMesh.id, // 必须包含ID
          meshName: this.data.currentScreenMesh.meshName,
          description: this.data.currentScreenMesh.description
        }
      : {
          meshName: this.data.currentScreenMesh.meshName,
          description: this.data.currentScreenMesh.description
        };
  
    // 区分请求配置
    const config = this.data.editMode 
      ? { url: "/api/screen-mesh/update", method: "PUT" }
      : { url: "/api/screen-mesh/add", method: "POST" };
  
    request(config.url, config.method, payload)
      .then(() => {
        wx.showToast({ title: "操作成功" });
        this.setData({ showModal: false });
        this.getScreenMeshes();
      })
      .catch(() => {
        wx.showToast({ title: "操作失败" });
      });
  }
});
