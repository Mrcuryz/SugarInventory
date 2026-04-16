Component({
  properties: {
    task: {
      type: Object,
      value: {}
    },
    selectable: {
      type: Boolean,
      value: false
    },
    selected: {
      type: Boolean,
      value: false
    },
    showActions: {
      type: Boolean,
      value: false
    }
  },
  methods: {
    onTap() {
      this.triggerEvent('tapTask', { task: this.data.task });
    },
    onSelect() {
      this.triggerEvent('selectTask', { task: this.data.task });
    },
    onConfirm() {
      this.triggerEvent('confirmTask', { task: this.data.task });
    },
    onCancel() {
      this.triggerEvent('cancelTask', { task: this.data.task });
    },
    onRemove() {
      this.triggerEvent('removeTask', { task: this.data.task });
    },
    onRetry() {
      this.triggerEvent('retryTask', { task: this.data.task });
    },
    noop() {
    }
  }
});
