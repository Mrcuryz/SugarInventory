import { requireLogin } from '../../../utils/auth';
import { formatAssayStandard, formatDateTime } from '../../../utils/dict';

const METRICS = [
  { key: 'colorValue', label: '色值', unit: '' },
  { key: 'reducingSugar', label: '还原糖', unit: '%' },
  { key: 'dryWeight', label: '干燥失重', unit: '%' },
  { key: 'conductivityAsh', label: '电导灰分', unit: '%' },
  { key: 'sucrose', label: '蔗糖分', unit: '%' },
  { key: 'insolubleImpurity', label: '不溶于水杂质', unit: '' },
  { key: 'phValue', label: 'pH', unit: '' }
];

function valueText(value, unit) {
  if (value === null || value === undefined || value === '') return '未记录';
  return `${value}${unit || ''}`;
}

function normalizeConclusion(value) {
  return value || '无标准';
}

function conclusionType(value) {
  if (value === '合格') return 'success';
  if (value === '不合格') return 'danger';
  return 'info';
}

function buildJudgeSummary(assay, standardText) {
  const conclusion = normalizeConclusion(assay.isQualified);
  const hasStandard = standardText && !standardText.includes('未关联') && !standardText.includes('无匹配');
  if (!hasStandard) {
    return {
      title: '当前产品没有关联标准',
      desc: '本次化验只能展示实测指标，无法生成标准区间对比。'
    };
  }
  if (conclusion === '不合格') {
    return {
      title: '系统返回结论为不合格',
      desc: `满足标准：${standardText}。请结合管理端标准配置查看具体不达标项。`
    };
  }
  if (conclusion === '合格') {
    return {
      title: '系统返回结论为合格',
      desc: `满足标准：${standardText}。`
    };
  }
  return {
    title: '系统未给出明确合格结论',
    desc: '请核对产品是否已维护验收标准，或在管理端查看标准配置。'
  };
}

Page({
  data: {
    assay: null,
    metrics: [],
    qualifiedType: 'info',
    standardText: '本次未关联标准',
    judgeSummary: null
  },

  onLoad() {
    requireLogin();
    const assay = wx.getStorageSync('p1AssayDetail');
    if (!assay) {
      this.setData({ assay: null });
      return;
    }
    const conclusion = normalizeConclusion(assay.isQualified);
    const standardText = formatAssayStandard(assay.qualifiedStandards, '无匹配标准');
    this.setData({
      assay: {
        ...assay,
        createdAtText: formatDateTime(assay.createdAt),
        sampleDateText: assay.sampleDate || '-',
        isQualified: conclusion
      },
      qualifiedType: conclusionType(conclusion),
      standardText,
      judgeSummary: buildJudgeSummary({ ...assay, isQualified: conclusion }, standardText),
      metrics: METRICS.map(metric => ({
        ...metric,
        valueText: valueText(assay[metric.key], metric.unit),
        standardRangeText: '',
        statusText: '',
        statusType: 'info'
      }))
    });
  },

  onShow() {
    requireLogin();
  }
});
