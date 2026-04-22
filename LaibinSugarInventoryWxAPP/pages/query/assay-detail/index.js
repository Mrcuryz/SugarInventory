import { getAssayDetail } from '../../../api/query';
import { requireLogin } from '../../../utils/auth';
import {
  formatDateTime,
  formatMetricRange,
  getAssayJudgeMeta,
  getAssayPrimaryStandard
} from '../../../utils/dict';
import { showError } from '../../../utils/toast';

const ACTUAL_FIELD_MAP = {
  color_value: 'colorValue',
  reducing_sugar: 'reducingSugar',
  dry_weight_loss: 'dryWeight',
  dry_weight: 'dryWeight',
  conductivity_ash: 'conductivityAsh',
  sucrose: 'sucrose',
  insoluble_impurity: 'insolubleImpurity',
  ph: 'phValue',
  ph_value: 'phValue'
};

function formatValue(value, unit) {
  if (value === null || value === undefined || value === '') return '未录入';
  return `${value}${unit || ''}`;
}

function buildFailedMetricMap(failedMetrics = []) {
  return failedMetrics.reduce((result, item) => {
    result[item.metricCode] = item;
    return result;
  }, {});
}

function buildMetrics(assay) {
  const snapshotItems = assay.standardSnapshot?.items?.length
    ? assay.standardSnapshot.items
    : [
      { metricCode: 'color_value', metricName: '色值', unit: 'IU', compareType: 'lte' },
      { metricCode: 'reducing_sugar', metricName: '还原糖分', unit: 'g/100g', compareType: 'range' },
      { metricCode: 'dry_weight', metricName: '干燥失重', unit: 'g/100g', compareType: 'lte' },
      { metricCode: 'conductivity_ash', metricName: '电导灰分', unit: 'g/100g', compareType: 'lte' },
      { metricCode: 'sucrose', metricName: '蔗糖分', unit: 'g/100g', compareType: 'gte' },
      { metricCode: 'insoluble_impurity', metricName: '不溶于水杂质', unit: 'mg/kg', compareType: 'lte' },
      { metricCode: 'ph', metricName: 'pH', unit: '', compareType: 'range' }
    ];
  const failedMap = buildFailedMetricMap(assay.failedMetrics || []);
  return snapshotItems.map((item) => {
    const field = ACTUAL_FIELD_MAP[item.metricCode];
    const actualValue = field ? assay[field] : null;
    const failedItem = failedMap[item.metricCode];
    return {
      key: item.metricCode,
      label: item.metricName,
      actualText: formatValue(actualValue, item.unit),
      standardRangeText: formatMetricRange(item),
      statusText: failedItem ? '未达标' : '达标',
      statusType: failedItem ? 'danger' : 'success',
      reason: failedItem ? failedItem.reason : ''
    };
  });
}

function buildMatchedStandards(assay) {
  return assay.matchedStandards || [];
}

function buildStandardNotice(assay) {
  if (assay.judgeResult === 'NO_STANDARD') {
    return {
      title: '当前产品未配置化验标准',
      desc: '本页仅展示实测数据。要得到合格判定，需要先在 Web 端维护产品与标准关系。'
    };
  }
  if (assay.judgeResult === 'MULTIPLE_CANDIDATES') {
    return {
      title: '存在多个候选标准',
      desc: '系统未唯一确定采用标准，以下结果用于辅助排查，请以标准配置为准。'
    };
  }
  return {
    title: '本次判定基于记录生成时的标准快照',
    desc: '后续标准配置调整不会回写历史化验结果，本页优先展示当时采用的标准。'
  };
}

function normalizeAssay(assay) {
  const judge = getAssayJudgeMeta(assay.judgeResult, assay.isQualified || '未出结论');
  return {
    ...assay,
    createdAtText: formatDateTime(assay.createdAt),
    sampleDateText: assay.sampleDate || '-',
    judgeLabel: judge.label,
    judgeType: judge.type,
    appliedStandardText: getAssayPrimaryStandard(assay, '当前未采用标准'),
    matchedStandardsText: buildMatchedStandards(assay).join('、') || '无',
    metrics: buildMetrics(assay),
    standardNotice: buildStandardNotice(assay)
  };
}

Page({
  data: {
    id: '',
    loading: true,
    assay: null
  },

  onLoad(options = {}) {
    requireLogin();
    this.setData({ id: options.id || '' });
    this.loadDetail();
  },

  onShow() {
    requireLogin();
  },

  async loadDetail() {
    if (!this.data.id) {
      this.setData({ loading: false, assay: null });
      return;
    }
    this.setData({ loading: true });
    try {
      const res = await getAssayDetail(this.data.id);
      this.setData({ assay: normalizeAssay(res) });
    } catch (error) {
      this.setData({ assay: null });
      showError(error, '化验详情加载失败');
    } finally {
      this.setData({ loading: false });
    }
  }
});
