Component({
  properties: {
    page: { type: Number, value: 1 },
    size: { type: Number, value: 10 },
    total: { type: Number, value: 0 },
    loading: { type: Boolean, value: false }
  },
  observers: {
    'page,size,total': function pageInfoChanged() {
      this.updateState();
    }
  },
  lifetimes: {
    attached() {
      this.updateState();
    }
  },
  data: {
    totalPages: 1,
    hasPrev: false,
    hasNext: false,
    rangeText: '0-0'
  },
  methods: {
    updateState() {
      const page = Math.max(1, Number(this.data.page || 1));
      const size = Math.max(1, Number(this.data.size || 10));
      const total = Math.max(0, Number(this.data.total || 0));
      const totalPages = Math.max(1, Math.ceil(total / size));
      const start = total ? (page - 1) * size + 1 : 0;
      const end = total ? Math.min(page * size, total) : 0;
      this.setData({
        totalPages,
        hasPrev: page > 1,
        hasNext: page < totalPages,
        rangeText: `${start}-${end}`
      });
    },
    prevPage() {
      if (this.data.loading || !this.data.hasPrev) return;
      this.triggerEvent('change', { page: this.data.page - 1 });
    },
    nextPage() {
      if (this.data.loading || !this.data.hasNext) return;
      this.triggerEvent('change', { page: this.data.page + 1 });
    }
  }
});
