import { pageScreenMeshes } from '../../../api/query';
import { getProductsByStatus } from '../../../api/product';
import { requireLogin } from '../../../utils/auth';
import { showError } from '../../../utils/toast';

function matchMesh(product, mesh) {
  if (product.screenMeshId && mesh.id) return product.screenMeshId === mesh.id;
  if (product.screenMeshName && mesh.meshName) return product.screenMeshName === mesh.meshName;
  return false;
}

Page({
  data: {
    meshName: '',
    page: 1,
    size: 10,
    total: 0,
    loading: false,
    error: false,
    meshes: []
  },

  onLoad() {
    requireLogin();
    this.loadMeshes();
  },

  onShow() {
    requireLogin();
  },

  onMeshNameInput(e) {
    this.setData({ meshName: e.detail.value });
  },

  resetSearch() {
    this.setData({ meshName: '', page: 1 });
    this.loadMeshes();
  },

  search() {
    this.setData({ page: 1 });
    this.loadMeshes();
  },

  async loadMeshes() {
    this.setData({ loading: true, error: false });
    try {
      const [res, semi, finish] = await Promise.all([
        pageScreenMeshes({
          page: this.data.page,
          size: this.data.size,
          meshName: this.data.meshName.trim() || undefined
        }),
        getProductsByStatus('半成品').catch(() => []),
        getProductsByStatus('成品').catch(() => [])
      ]);
      const products = [
        ...(Array.isArray(semi) ? semi : []),
        ...(Array.isArray(finish) ? finish : [])
      ];
      const meshes = (res.records || []).map(mesh => {
        const related = products.filter(product => matchMesh(product, mesh)).map(product => product.productName).filter(Boolean);
        return {
          ...mesh,
          descriptionText: mesh.description || '暂无描述',
          relatedProducts: related.slice(0, 6),
          relatedCount: related.length,
          relatedMoreText: related.length > 6 ? `还有 ${related.length - 6} 个产品` : ''
        };
      });
      this.setData({
        meshes,
        total: res.total || 0
      });
    } catch (error) {
      this.setData({ error: true });
      showError(error, '筛网查询失败');
    } finally {
      this.setData({ loading: false });
    }
  },

  onPageChange(e) {
    this.setData({ page: e.detail.page });
    this.loadMeshes();
  }
});
