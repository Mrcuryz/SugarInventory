Component({
  properties: {
    actions: {
      type: Array,
      value: []
    },
    fixed: {
      type: Boolean,
      value: true
    }
  },
  methods: {
    onAction(e) {
      const item = this.data.actions[e.currentTarget.dataset.index];
      if (!item || item.disabled) return;
      this.triggerEvent('action', { action: item });
    }
  }
});

