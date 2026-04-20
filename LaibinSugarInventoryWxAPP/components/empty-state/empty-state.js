Component({
  properties: {
    title: {
      type: String,
      value: '暂无数据'
    },
    description: String,
    actionText: String,
    icon: String
  },
  methods: {
    onAction() {
      this.triggerEvent('action');
    }
  }
});

