Component({
  properties: {
    title: String,
    subtitle: String,
    showBack: {
      type: Boolean,
      value: false
    },
    actionText: String
  },
  methods: {
    onBack() {
      const pages = getCurrentPages();
      if (pages.length > 1) {
        wx.navigateBack();
      } else {
        wx.switchTab({ url: '/pages/workbench/index/index' });
      }
    },
    onAction() {
      this.triggerEvent('action');
    }
  }
});

