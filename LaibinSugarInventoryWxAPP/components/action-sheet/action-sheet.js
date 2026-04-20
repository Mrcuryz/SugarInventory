Component({
  properties: {
    visible: Boolean,
    title: String,
    actions: {
      type: Array,
      value: []
    }
  },
  methods: {
    onClose() {
      this.triggerEvent('close');
    },
    onAction(e) {
      this.triggerEvent('action', { action: this.data.actions[e.currentTarget.dataset.index] });
    },
    noop() {
    }
  }
});

