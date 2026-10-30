/* 生产日报 DT50 交互原型：演示数据仅保存在当前浏览器页面，不调用后端。 */
(() => {
  const FIELDS = [
    { key: 'dailyActual', label: '当日实绩' },
    { key: 'convertedTons', label: '折成吨' },
    { key: 'monthQuantity', label: '月累计（件）' },
    { key: 'monthTons', label: '月累计（吨）' },
    { key: 'yearTons', label: '年累计（吨）' },
    { key: 'remark', label: '备注' }
  ];
  const ICON_PATHS = {
    back: '<path d="m15 18-6-6 6-6"/>',
    next: '<path d="m9 18 6-6-6-6"/>',
    more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4M17 3v4M3 10h18"/>',
    factory: '<path d="M3 21V10l6 4V8l6 4V4h6v17H3Z"/><path d="M7 18h2m4 0h2m4 0h2"/>',
    flame: '<path d="M12 22c4.4 0 7-3 7-7 0-3-2-5-3-6-.2 2-1.3 3-2 3-.1-4-2-7-5-10 .3 4-4 6-4 12 0 4.5 2.6 8 7 8Z"/><path d="M12 22c2 0 3-1.6 3-3.5 0-1.4-.9-2.3-2-3.5-.2 1.5-1.5 2-2 2.5-.5-.7-.7-1.3-.7-2.5C9 17 9 22 12 22Z"/>',
    crystal: '<path d="m7 3 10 0 5 7-10 12L2 10l5-7Z"/><path d="m2 10 20 0M7 3l-2 7 7 12 7-12-2-7"/>',
    chart: '<rect x="3" y="3" width="18" height="18" rx="3"/><path d="M7 16v-4m5 4V8m5 8v-6"/>',
    clipboard: '<rect x="5" y="5" width="14" height="16" rx="2"/><rect x="9" y="3" width="6" height="4" rx="1"/><path d="M9 12h6m-6 4h5"/>',
    box: '<path d="m12 3 9 5-9 5-9-5 9-5ZM3 8v9l9 5 9-5V8M12 13v9"/>',
    grid: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    alert: '<circle cx="12" cy="12" r="9"/><path d="M12 7v6m0 4h.01"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    close: '<path d="M6 6 18 18M18 6 6 18"/>',
    upload: '<path d="M12 16V3m-5 5 5-5 5 5M4 17v3h16v-3"/>',
    download: '<path d="M12 3v13m-5-5 5 5 5-5M4 17v3h16v-3"/>'
  };
  function icon(name, extraClass = '') {
    return `<svg class="ui-icon ${extraClass}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON_PATHS[name] || ''}</svg>`;
  }
  const NAMES = {
    POLYCRYSTAL: '多晶车间', BROWN_SUGAR: '红糖车间',
    MONOCRYSTAL: '单晶车间', FACTORY_SUMMARY: '全厂汇总'
  };
  const TEMPLATE = [
    { code: 'POLYCRYSTAL', name: NAMES.POLYCRYSTAL, groups: [
      { code: 'RAW_IN_PROCESS', name: '原料及在制品', metrics: [
        ['POLY_SUGAR_OIL', '出糖油', '桶'], ['POLY_SUGAR_DISSOLVING', '化糖', '锅'],
        ['POLY_WHITE_SUGAR_USAGE', '白糖用量', '包'], ['POLY_ROCK_SUGAR_REBOIL', '冰糖回煮', '吨'],
        ['POLY_SUGAR_OIL_REBOIL', '糖油回煮', '吨'], ['POLY_SYRUP_USAGE', '糖浆用量', '吨'],
        ['POLY_ROCK_SUGAR_BOILING', '煮冰糖', '锅'], ['POLY_SUGAR_RELEASE_BUCKETS', '放糖桶数', '桶'],
        ['POLY_DEMOULDING', '脱模', '车']
      ] },
      { code: 'SEMI_PRODUCT', name: '半成品', allowed: ['半成品'], metrics: [
        ['POLY_SEMI_SUBTOTAL', '小计', '件', 'after']
      ] },
      { code: 'DIRECT_PRODUCT', name: '直接成品', allowed: ['成品'], metrics: [
        ['POLY_DIRECT_SUBTOTAL', '小计', '件', 'after']
      ] },
      { code: 'SECONDARY_PRODUCT', name: '装包或二次加工成品', allowed: ['成品'], metrics: [
        ['POLY_SECONDARY_SUBTOTAL', '小计', '件', 'after']
      ] },
      { code: 'COMPREHENSIVE', name: '综合指标', metrics: [
        ['POLY_OUTPUT', '冰糖产量', '吨'], ['POLY_STEAM_USAGE', '冰糖汽耗', '吨'],
        ['POLY_STEAM_PER_TON', '吨冰糖耗汽', '吨'], ['POLY_POWER_USAGE', '冰糖总电耗', 'Kw.h'],
        ['POLY_POWER_PER_TON', '吨冰糖电耗', 'Kw.h'], ['POLY_CRYSTALLIZATION_RATE', '冰糖结晶率', '%'],
        ['POLY_YIELD_RATE', '冰糖产率', '%'], ['POLY_RETURN_SAND_RATE', '返砂占比', '%']
      ] }
    ] },
    { code: 'BROWN_SUGAR', name: NAMES.BROWN_SUGAR, groups: [
      { code: 'RAW_IN_PROCESS', name: '原料及在制品', metrics: [
        ['BROWN_SUGAR_DISSOLVING', '化糖', '锅'], ['BROWN_RAW_SUGAR_USAGE', '赤砂糖用量', '包'],
        ['BROWN_SYRUP_USAGE', '糖浆用量', '吨'], ['BROWN_ROCK_SUGAR_DISSOLUTION', '冰糖回溶', '吨'],
        ['BROWN_WHITE_SUGAR_USAGE', '白砂糖用量', '包'], ['BROWN_SUGAR_BOILING', '红糖煮糖', '锅'],
        ['BROWN_SUGAR_PICKING', '拣糖', '盆']
      ] },
      { code: 'PRODUCT', name: '产品', allowed: ['成品', '半成品'], metrics: [
        ['BROWN_PRODUCT_SUBTOTAL', '小计', '件', 'after']
      ] },
      { code: 'COMPREHENSIVE', name: '综合指标', metrics: [
        ['BROWN_STEAM_USAGE', '红糖汽耗', '吨'], ['BROWN_STEAM_PER_TON', '吨红糖耗汽', '吨'],
        ['BROWN_POWER_USAGE', '红糖总电耗', 'Kw.h'], ['BROWN_POWER_PER_TON', '吨红糖电耗', 'Kw.h'],
        ['BROWN_YIELD_RATE', '红糖产率', '%']
      ] }
    ] },
    { code: 'MONOCRYSTAL', name: NAMES.MONOCRYSTAL, groups: [
      { code: 'RAW_IN_PROCESS', name: '原料及在制品', metrics: [
        ['MONO_SUGAR_DISSOLVING', '单晶化糖', '锅'], ['MONO_WHITE_SUGAR_USAGE', '白糖用量', '包'],
        ['MONO_SYRUP_USAGE', '糖浆用量', '吨'], ['MONO_TANKS_STARTED', '开煮罐数', '罐'],
        ['MONO_SEED_USAGE', '投单晶种', '桶'], ['MONO_SEED_NO4_USAGE', '投4号种', '桶'],
        ['MONO_SEED_NO3_USAGE', '投3号种', '桶'], ['MONO_SEED_NO2_USAGE', '投2号种', '桶'],
        ['MONO_COFFEE_SEED_USAGE', '投咖啡糖种', '吨']
      ] },
      { code: 'PRODUCT', name: '产品', allowed: ['成品', '半成品'], metrics: [
        ['MONO_OUTPUT_TANKS', '出糖罐数', '罐', 'before'],
        ['MONO_SEED_OUTPUT', '种子产量', '桶', 'before'],
        ['MONO_PRODUCT_SUBTOTAL', '小计', '件', 'after']
      ] },
      { code: 'COMPREHENSIVE', name: '综合指标', metrics: [
        ['MONO_STEAM_USAGE', '单晶汽耗', '吨'], ['MONO_STEAM_PER_TON', '吨单晶汽耗', '吨'],
        ['MONO_POWER_USAGE', '单晶总电耗', 'Kw.h'], ['MONO_POWER_PER_TON', '吨单晶电耗', 'Kw.h'],
        ['MONO_YIELD_RATE', '单晶产率', '%'], ['MONO_CRYSTALLIZATION_RATE', '单晶结晶率', '%'],
        ['MONO_POLY_SEED_RATE', '多晶种子占比', '%'], ['MONO_COFFEE_SEED_RATE', '咖啡糖种子占比', '%']
      ] }
    ] },
    { code: 'FACTORY_SUMMARY', name: NAMES.FACTORY_SUMMARY, groups: [
      { code: 'FACTORY_METRIC', name: '全厂综合指标', metrics: [
        ['FACTORY_TOTAL_SUGAR_USAGE', '总用糖量（折白糖）', '件'],
        ['FACTORY_TOTAL_OUTPUT', '总产量', '件'], ['FACTORY_MIXED_YIELD_RATE', '混合成品产率', '%'],
        ['FACTORY_TOTAL_STEAM', '总耗汽', '吨'], ['FACTORY_TOTAL_POWER', '总耗电量', 'Kw.h'],
        ['FACTORY_TOTAL_WATER', '总耗水量', 'M3'], ['FACTORY_STEAM_PER_TON', '吨混合成品汽耗', '吨'],
        ['FACTORY_POWER_PER_TON', '吨混合成品电耗', 'Kw.h'],
        ['FACTORY_WATER_PER_TON', '吨混合成品水耗', 'M3']
      ] }
    ] }
  ];
  const PRODUCTS = [
    { id: 'product-1', name: '白冰糖半成品', status: '半成品' },
    { id: 'product-2', name: '小粒黄冰糖', status: '成品' },
    { id: 'product-3', name: '多晶冰糖礼盒', status: '成品' },
    { id: 'product-4', name: '红糖块', status: '成品' },
    { id: 'product-5', name: '单晶冰糖', status: '成品' },
    { id: 'product-6', name: '冰糖晶种半成品', status: '半成品' }
  ];
  const DEMO_VALUES = {
    POLY_SUGAR_OIL: { dailyActual: '18', monthQuantity: '312' },
    POLY_SUGAR_DISSOLVING: { dailyActual: '4' },
    POLY_WHITE_SUGAR_USAGE: { dailyActual: '128', convertedTons: '6.4', monthQuantity: '3152' },
    POLY_ROCK_SUGAR_REBOIL: { dailyActual: '0.8' },
    POLY_SUGAR_RELEASE_BUCKETS: { dailyActual: '26' },
    POLY_SEMI_SUBTOTAL: { dailyActual: '320' },
    POLY_DIRECT_SUBTOTAL: { dailyActual: '240' },
    POLY_SECONDARY_SUBTOTAL: { dailyActual: '48' },
    POLY_OUTPUT: { dailyActual: '12.6', monthTons: '328.4' },
    POLY_CRYSTALLIZATION_RATE: { dailyActual: '68.5' },
    BROWN_SUGAR_DISSOLVING: { dailyActual: '3' },
    BROWN_RAW_SUGAR_USAGE: { dailyActual: '86' },
    BROWN_PRODUCT_SUBTOTAL: { dailyActual: '280' },
    FACTORY_TOTAL_SUGAR_USAGE: { dailyActual: '214' },
    FACTORY_TOTAL_OUTPUT: { dailyActual: '568' },
    FACTORY_TOTAL_STEAM: { dailyActual: '21.6' }
  };
  const DEMO_PRODUCTS = {
    'POLYCRYSTAL:SEMI_PRODUCT': [{ id: 'line-1', productId: 'product-1', name: '白冰糖半成品', dailyActual: '320', monthQuantity: '8460' }],
    'POLYCRYSTAL:DIRECT_PRODUCT': [{ id: 'line-2', productId: 'product-2', name: '小粒黄冰糖', dailyActual: '240', monthQuantity: '6240' }],
    'POLYCRYSTAL:SECONDARY_PRODUCT': [{ id: 'line-3', productId: 'product-3', name: '多晶冰糖礼盒', dailyActual: '48' }],
    'BROWN_SUGAR:PRODUCT': [{ id: 'line-4', productId: 'product-4', name: '红糖块', dailyActual: '280' }]
  };
  const sampleStatuses = {
    POLYCRYSTAL: ['DRAFT', '陈思聪 · 09:18'],
    BROWN_SUGAR: ['SUBMITTED', '李师傅 · 昨日 17:45'],
    MONOCRYSTAL: ['EMPTY', '尚未填写'],
    FACTORY_SUMMARY: ['DRAFT', '张主任 · 10:05']
  };
  let productSequence = 10;

  function isoDate(value) {
    return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
  }

  function dateWithOffset(value, offset) {
    const [year, month, day] = value.split('-').map(Number);
    return isoDate(new Date(year, month - 1, day + offset, 12));
  }

  function monthWithOffset(value, offset) {
    const [year, month] = value.split('-').map(Number);
    const day = new Date(year, month - 1 + offset, 1, 12);
    return isoDate(day).slice(0, 7);
  }

  function makeReport(date, sample = false) {
    return {
      date,
      preparedDate: sample ? date : '',
      preparedBy: sample ? '张主任' : '',
      persisted: sample,
      headerDirty: false,
      sections: TEMPLATE.map(section => ({
        code: section.code, name: section.name,
        status: sample ? sampleStatuses[section.code][0] : 'EMPTY',
        updated: sample ? sampleStatuses[section.code][1] : '尚未填写',
        dirty: false,
        groups: section.groups.map(group => ({
          code: group.code, name: group.name, allowed: group.allowed || null,
          metrics: group.metrics.map(([id, name, unit, position]) => ({
            id, name, unit, position: position || 'normal', kind: 'metric',
            ...(sample ? DEMO_VALUES[id] || {} : {})
          })),
          products: sample ? (DEMO_PRODUCTS[`${section.code}:${group.code}`] || []).map(p => ({
            kind: 'product', unit: '件', ...p
          })) : []
        }))
      }))
    };
  }

  const reports = new Map();
  const initialDate = isoDate(new Date());
  reports.set(initialDate, makeReport(initialDate, true));
  const submittedDate = dateWithOffset(initialDate, -3);
  const submittedReport = makeReport(submittedDate, true);
  submittedReport.sections.forEach(item => { item.status = 'SUBMITTED'; item.updated = '李师傅 · 16:30'; });
  reports.set(submittedDate, submittedReport);
  reports.set(dateWithOffset(initialDate, -8), makeReport(dateWithOffset(initialDate, -8), true));
  const state = {
    date: initialDate, view: 'overview', sectionCode: null, groupCode: null,
    field: 'dailyActual', sheet: null, calendarMonth: initialDate.slice(0, 7), itemId: null, pickerTargetId: null,
    confirmAction: null, pendingDate: null, pendingDeleteId: null, previousSheet: null, renderedView: null
  };
  const app = document.getElementById('app');
  let toastTimer;

  const report = () => reports.get(state.date);
  const section = () => report().sections.find(item => item.code === state.sectionCode);
  const group = () => section()?.groups.find(item => item.code === state.groupCode);
  const fieldLabel = () => FIELDS.find(item => item.key === state.field)?.label || '当日实绩';
  const text = value => String(value == null ? '' : value).replace(/[&<>"']/g, char => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[char]));

  function allItems(currentGroup) {
    if (!currentGroup.allowed) return currentGroup.metrics;
    return [
      ...currentGroup.metrics.filter(item => item.position === 'before'),
      ...currentGroup.products,
      ...currentGroup.metrics.filter(item => item.position !== 'before')
    ];
  }

  function itemById(id) {
    return allItems(group()).find(item => item.id === id);
  }

  function dateLabel(value) {
    const [year, month, day] = value.split('-').map(Number);
    return `${year}年${month}月${day}日`;
  }

  function dateParts(value) {
    const [year, month, day] = value.split('-').map(Number);
    const weekday = '日一二三四五六'[new Date(year, month - 1, day, 12).getDay()];
    return { year, month, day, weekday };
  }

  function itemIsEmpty(item) {
    return FIELDS.every(field => item[field.key] == null || String(item[field.key]).trim() === '');
  }

  function missingCount(currentGroup) {
    return allItems(currentGroup).filter(itemIsEmpty).length;
  }

  function sectionMissingCount(currentSection) {
    return currentSection.groups.reduce((sum, currentGroup) => sum + missingCount(currentGroup), 0);
  }

  function calendarStatus(date) {
    const saved = reports.get(date);
    if (!saved?.persisted) return '';
    return saved.sections.every(item => item.status === 'SUBMITTED' && !item.dirty) ? 'submitted' : 'draft';
  }

  function statusInfo(currentSection) {
    if (currentSection.dirty) return { label: '未保存', cls: 'unsaved' };
    if (currentSection.status === 'SUBMITTED') return { label: '已提交', cls: 'submitted' };
    if (currentSection.status === 'DRAFT') return { label: '草稿', cls: 'draft' };
    return { label: '待填写', cls: 'empty' };
  }

  function badge(currentSection) {
    const info = statusInfo(currentSection);
    return `<span class="badge ${info.cls}">${info.label}</span>`;
  }

  function overallInfo() {
    if (report().sections.some(item => item.dirty) || report().headerDirty) return '未保存';
    if (!report().persisted) return '待填写';
    if (report().sections.every(item => item.status === 'SUBMITTED' && !item.dirty)) return '已提交';
    return '草稿';
  }

  function renderTopbar() {
    const title = state.view === 'overview' ? '生产日报' : state.view === 'section' ? section().name : group().name;
    const subtitle = state.view === 'overview' ? '生产与仓库管理'
      : state.view === 'section' ? dateLabel(state.date) : `${section().name} · ${dateLabel(state.date)}`;
    return `<header class="topbar">
      <button class="icon-button" data-action="back" aria-label="返回">${icon('back')}</button>
      <div class="topbar-copy"><strong class="topbar-title">${text(title)}</strong><small class="topbar-subtitle">${text(subtitle)}</small></div>
      <button class="icon-button more-button" data-action="more" aria-label="更多操作">${icon('more')}</button>
    </header>`;
  }

  function renderOverview() {
    const parts = dateParts(state.date);
    const icons = { POLYCRYSTAL: 'factory', BROWN_SUGAR: 'flame', MONOCRYSTAL: 'crystal', FACTORY_SUMMARY: 'chart' };
    const cards = report().sections.map(item => `<button class="surface-card workshop-card" data-action="open-section" data-code="${item.code}">
      <span class="workshop-main"><span class="workshop-icon ${item.code.toLowerCase()}">${icon(icons[item.code])}</span>
      <span class="workshop-copy"><strong class="workshop-title">${text(item.name)}</strong><small class="workshop-meta">${text(item.updated)}</small></span>
      ${badge(item)}<span class="chevron">${icon('next')}</span></span>
      ${item.status === 'SUBMITTED' && sectionMissingCount(item) ? `<span class="card-missing">${icon('alert')} ${sectionMissingCount(item)} 项未填，可补录</span>` : ''}</button>`).join('');
    return `<div class="date-card">
      <div class="date-card-top"><span class="date-eyebrow">日报日期</span><span class="status-pill">${overallInfo()}</span></div>
      <div class="date-display"><span class="date-day">${parts.day}</span>
        <span class="date-detail"><strong>${parts.year}年${parts.month}月</strong><small>星期${parts.weekday}${state.date === initialDate ? ' · 今天' : ''}</small></span>
        <button class="calendar-trigger" data-action="calendar" aria-label="打开日报日历">${icon('calendar')}<span>日历</span></button>
      </div>
      <div class="date-controls">
        <button data-action="shift-date" data-offset="-1" aria-label="上一日">${icon('back')}前一天</button>
        <button data-action="today">今天</button>
        <button data-action="shift-date" data-offset="1" aria-label="下一日">后一天${icon('next')}</button>
      </div></div>
      <div class="section-heading"><h2>制表信息</h2><span>${report().headerDirty ? '未保存' : '可修改'}</span></div>
      <button class="surface-card header-card" data-action="edit-header"><span class="header-info">
        <small class="overline">制表日期 · 制表人</small>
        <strong class="header-main">${text(report().preparedDate || '待填写')} · ${text(report().preparedBy || '待填写')}</strong>
      </span><span class="chevron">${icon('next')}</span></button>
      <div class="section-heading"><h2>车间与汇总</h2><span>共 4 个分区</span></div>
      <div class="workshop-list">${cards}</div>`;
  }

  function renderSection() {
    const current = section();
    const groups = current.groups.map(item => {
      const count = item.allowed ? item.products.length + item.metrics.length : item.metrics.length;
      const remaining = current.status === 'SUBMITTED' ? missingCount(item) : 0;
      const preview = item.allowed
        ? item.products.length ? item.products.map(product => product.name).join('、') : '暂无产品行'
        : item.metrics.slice(0, 3).map(metric => metric.name).join('、');
      return `<button class="surface-card group-card" data-action="open-group" data-code="${item.code}">
        <span class="group-main"><span class="group-icon">${icon(item.allowed ? 'box' : 'grid')}</span>
        <span class="group-copy"><strong class="group-title">${text(item.name)}</strong>
        <small class="group-meta">${item.allowed ? `${item.products.length} 条产品行` : `${count} 项固定指标`}</small></span>
        ${remaining ? `<span class="group-missing">${remaining} 项未填</span>` : ''}<span class="chevron">${icon('next')}</span></span>
        <small class="group-preview">${text(preview)}</small></button>`;
    }).join('');
    return `<div class="surface-card section-hero"><div class="section-hero-top"><h2>${text(current.name)}</h2>${badge(current)}</div>
      <p>最后修改：${text(current.updated)}</p></div>
      ${renderMissingAlert(sectionMissingCount(current), current.status === 'SUBMITTED')}
      <div class="section-heading"><h2>填写分组</h2><span>依样表顺序</span></div>
      <div class="group-list">${groups}</div>`;
  }

  function renderMissingAlert(count, submitted) {
    if (!submitted || !count) return '';
    return `<div class="missing-alert" role="note">${icon('alert')}
      <span><strong>已提交，仍有 <b class="missing-count">${count}</b> 项未填</strong>
      <small>这些项目可以继续补录，不影响已提交记录。</small></span></div>`;
  }

  function otherValues(item) {
    return FIELDS.some(field => field.key !== state.field && item[field.key] != null && item[field.key] !== '');
  }

  function renderItemRow(item) {
    const isRemark = state.field === 'remark';
    const needsAttention = section().status === 'SUBMITTED' && itemIsEmpty(item);
    return `<div class="metric-row ${item.kind === 'product' ? 'is-product' : ''} ${needsAttention ? 'is-missing' : ''}" data-row-item="${text(item.id)}">
      <div class="metric-copy"><strong class="metric-name">${text(item.name)}</strong>
        <small class="metric-unit">单位：${text(item.unit)}${needsAttention ? '<span class="metric-missing">未填</span>' : otherValues(item) ? '<span class="metric-other"> · 其他字段已填</span>' : ''}</small></div>
      <input class="quick-input ${needsAttention ? 'needs-attention' : ''}" data-item="${text(item.id)}" data-field="${state.field}"
        inputmode="${isRemark ? 'text' : 'decimal'}" type="text" value="${text(item[state.field] ?? '')}"
        placeholder="${needsAttention ? '可补录' : isRemark ? '填写备注' : '点击填写'}" aria-label="${text(item.name)}，${text(fieldLabel())}">
      <button class="row-arrow" data-action="open-item" data-id="${text(item.id)}" aria-label="查看${text(item.name)}全部字段">${icon('next')}</button>
    </div>`;
  }

  function renderGroup() {
    const currentGroup = group();
    const entries = allItems(currentGroup);
    return `<div class="group-context"><span>${text(section().name)} / ${text(currentGroup.name)}</span>${badge(section())}</div>
      ${renderMissingAlert(missingCount(currentGroup), section().status === 'SUBMITTED')}
      <div class="field-switch"><div class="field-switch-title"><strong>连续录入</strong><small>左右滑动，选择填写列</small></div>
      <div class="field-tabs" role="group" aria-label="选择日报字段">${FIELDS.map(field => `<button class="field-tab ${field.key === state.field ? 'active' : ''}"
        data-action="set-field" data-field="${field.key}" aria-pressed="${field.key === state.field}">${text(field.label)}</button>`).join('')}</div></div>
      <div class="list-caption"><strong>${currentGroup.allowed ? '指标与产品' : '固定指标'}</strong><small>点右侧图标查看全部字段</small></div>
      <div class="metric-list">${entries.map(renderItemRow).join('')}</div>
      ${currentGroup.allowed ? `<button class="add-product" data-action="add-product">${icon('plus')} 新增产品</button>` : ''}`;
  }

  function renderFooter() {
    if (state.view === 'overview') return `<footer class="bottom-bar">
      <button class="button secondary" data-action="calendar">${icon('calendar')} 日历与历史</button>
      <button class="button primary" data-action="submit-all">提交整张日报</button></footer>`;
    const current = section();
    return `<footer class="bottom-bar"><button class="button secondary" data-action="save-section">保存草稿</button>
      <button class="button primary" data-action="submit-section">${current.code === 'FACTORY_SUMMARY' ? '提交汇总' : '提交本车间'}</button></footer>`;
  }

  function sheetChrome(title, subtitle, body, footer = '', sheetClass = '') {
    return `<div class="overlay" data-action="overlay"><section class="sheet ${sheetClass}" role="dialog" aria-modal="true" aria-label="${text(title)}">
      <div class="sheet-handle"></div><header class="sheet-head"><span class="sheet-head-copy"><strong class="sheet-title">${text(title)}</strong>
      <small class="sheet-subtitle">${text(subtitle || '')}</small></span>
      <button class="sheet-close" data-action="close-sheet" aria-label="关闭">${icon('close')}</button></header>
      <div class="sheet-body">${body}</div>${footer ? `<footer class="sheet-footer">${footer}</footer>` : ''}
    </section></div>`;
  }

  function renderCalendarSheet() {
    const [year, month] = state.calendarMonth.split('-').map(Number);
    const first = new Date(year, month - 1, 1, 12);
    const offset = (first.getDay() + 6) % 7;
    const daysInMonth = new Date(year, month, 0, 12).getDate();
    const cellCount = Math.ceil((offset + daysInMonth) / 7) * 7;
    const cells = Array.from({ length: cellCount }, (_, index) => {
      const date = isoDate(new Date(year, month - 1, index - offset + 1, 12));
      const dateNumber = Number(date.slice(-2));
      const status = calendarStatus(date);
      const classes = ['calendar-day', date.slice(0, 7) !== state.calendarMonth ? 'adjacent' : '',
        date === state.date ? 'selected' : '', date === initialDate ? 'today' : '', status].filter(Boolean).join(' ');
      const label = `${dateLabel(date)}${status === 'submitted' ? '，已提交' : status === 'draft' ? '，草稿' : '，待填写'}`;
      return `<button class="${classes}" data-action="pick-date" data-date="${date}" aria-label="${label}"
        aria-current="${date === state.date ? 'date' : 'false'}"><span>${dateNumber}</span><i></i></button>`;
    }).join('');
    const history = [...reports.values()]
      .filter(item => item.persisted && item.date.startsWith(state.calendarMonth))
      .sort((a, b) => b.date.localeCompare(a.date));
    const rows = history.length ? history.map(item => {
      const status = calendarStatus(item.date);
      const parts = dateParts(item.date);
      return `<button class="history-row" data-action="pick-date" data-date="${item.date}">
        <span class="history-date"><strong>${parts.month}月${parts.day}日</strong><small>星期${parts.weekday}</small></span>
        <span class="badge ${status}">${status === 'submitted' ? '已提交' : '草稿'}</span>${icon('next')}</button>`;
    }).join('') : '<p class="calendar-empty">本月暂无已保存的日报，可在日历上选择日期开始填写。</p>';
    return sheetChrome('日报日历', '选择日期 · 查看历史日报',
      `<div class="calendar-month"><button data-action="month-shift" data-offset="-1" aria-label="上个月">${icon('back')}</button>
        <strong>${year} 年 ${month} 月</strong><button data-action="month-shift" data-offset="1" aria-label="下个月">${icon('next')}</button></div>
       <div class="calendar-weekdays">${'一二三四五六日'.split('').map(day => `<span>${day}</span>`).join('')}</div>
       <div class="calendar-grid">${cells}</div>
       <div class="calendar-legend"><span><i class="draft"></i>草稿</span><span><i class="submitted"></i>已提交</span>
         <span>无标记为待填写</span></div>
       <div class="calendar-history-head"><strong>本月历史日报</strong><small>${history.length} 条已保存</small></div>
       <div class="calendar-history">${rows}</div>`,
      state.date !== initialDate || state.calendarMonth !== initialDate.slice(0, 7)
        ? `<button class="button secondary full" data-action="calendar-today">回到今天</button>` : '',
      'calendar-sheet');
  }

  function renderSheet() {
    if (!state.sheet) return '';
    if (state.sheet === 'calendar') return renderCalendarSheet();
    if (state.sheet === 'header') return sheetChrome('制表信息', dateLabel(state.date),
      `<label class="detail-field"><span>制表日期</span><input class="detail-input" type="date" data-header="preparedDate" value="${text(report().preparedDate)}"></label>
       <label class="detail-field"><span>制表人</span><input class="detail-input" type="text" maxlength="100" data-header="preparedBy" value="${text(report().preparedBy)}" placeholder="可留空"></label>`,
      '<button class="button primary full" data-action="save-header">保存制表信息</button>');
    if (state.sheet === 'item') {
      const item = itemById(state.itemId);
      const productPicker = item.kind === 'product'
        ? `<button class="product-select-trigger" data-action="choose-product" data-id="${text(item.id)}"><span>${text(item.name)}</span><span class="chevron">${icon('next')}</span></button>` : '';
      const fields = FIELDS.map(field => `<label class="detail-field"><span>${text(field.label)}${field.key === 'dailyActual' ? `<em>单位：${text(item.unit)}</em>` : ''}</span>
        ${field.key === 'remark'
          ? `<textarea class="detail-input" maxlength="500" data-item="${text(item.id)}" data-field="remark" placeholder="可留空">${text(item.remark || '')}</textarea>`
          : `<input class="detail-input number" inputmode="decimal" type="text" data-item="${text(item.id)}" data-field="${field.key}" value="${text(item[field.key] ?? '')}" placeholder="可留空">`}
        </label>`).join('');
      const productTools = item.kind === 'product' ? `<div class="product-tools">
        <button data-action="move-product" data-direction="-1" data-id="${text(item.id)}">上移</button>
        <button data-action="move-product" data-direction="1" data-id="${text(item.id)}">下移</button>
        <button class="delete" data-action="delete-product" data-id="${text(item.id)}">删除</button></div>` : '';
      return sheetChrome(item.kind === 'product' ? '产品行' : item.name,
        `${section().name} · ${group().name} · ${item.kind === 'product' ? '单位：件' : `单位：${item.unit}`}`,
        `${productPicker}${fields}${productTools}`, '<button class="button primary full" data-action="close-sheet">完成</button>');
    }
    if (state.sheet === 'product-picker') {
      const options = PRODUCTS.filter(product => group().allowed.includes(product.status)).map(product =>
        `<button class="sheet-option product-option" data-action="pick-product" data-product="${product.id}" data-name="${text(product.name)}">
           <span>${text(product.name)}</span><small>${text(product.status)} ${icon('next')}</small></button>`).join('');
      return sheetChrome('选择产品', `${section().name} · ${group().name}`,
        `<input class="search-input" type="search" data-search="product" placeholder="搜索产品名称" aria-label="搜索产品名称">
         <div class="product-options">${options}</div><div class="empty-copy" hidden>没有匹配的产品</div>`);
    }
    if (state.sheet === 'more') return sheetChrome('更多操作', '生产日报',
      `<button class="sheet-option" data-action="calendar">${icon('calendar')} 日历与历史 ${icon('next')}</button>
       <button class="sheet-option" data-action="demo-file" data-file="导入 Excel">${icon('upload')} 导入 Excel ${icon('next')}</button>
       <button class="sheet-option" data-action="demo-file" data-file="导出 Excel">${icon('download')} 导出 Excel ${icon('next')}</button>`);
    if (state.sheet === 'confirm') {
      const isWhole = state.confirmAction === 'all';
      return sheetChrome(isWhole ? '提交整张日报？' : '提交当前分区？',
        isWhole ? dateLabel(state.date) : section().name,
        `<p class="sheet-explain">${isWhole ? '将提交四个分区当前填写的内容。' : '将提交当前分区已填写的内容。'}提交后仍可修改；修改并保存后状态将回到草稿。</p>`,
        `<button class="button secondary" data-action="close-sheet">取消</button>
         <button class="button primary" data-action="confirm-submit">确认提交</button>`);
    }
    if (state.sheet === 'date-switch-confirm') return sheetChrome('有未保存的修改',
      `将切换到 ${dateLabel(state.pendingDate)}`,
      '<p class="sheet-explain">当前填写会保留在本次原型会话中。返回原日期后仍可继续编辑。</p>',
      '<button class="button secondary" data-action="cancel-confirm">继续编辑</button><button class="button primary" data-action="confirm-date-switch">切换日期</button>');
    if (state.sheet === 'delete-confirm') return sheetChrome('移除这条产品行？', group().name,
      '<p class="sheet-explain">只会从当前日报移除这条产品行，不会删除产品资料。</p>',
      '<button class="button secondary" data-action="cancel-confirm">取消</button><button class="button danger" data-action="confirm-delete">确认移除</button>');
    return '';
  }

  function render(resetScroll = false) {
    const oldContent = app.querySelector('.content');
    const oldScroll = oldContent?.scrollTop || 0;
    app.dataset.view = state.view;
    app.innerHTML = `${renderTopbar()}<main class="content">${state.view === 'overview' ? renderOverview()
      : state.view === 'section' ? renderSection() : renderGroup()}</main>${renderFooter()}${renderSheet()}<div class="toast" role="status"></div>`;
    if (!resetScroll && state.renderedView === state.view) app.querySelector('.content').scrollTop = oldScroll;
    const tabs = app.querySelector('.field-tabs');
    const selectedTab = tabs?.querySelector('.field-tab.active');
    if (tabs && selectedTab) tabs.scrollLeft = selectedTab.offsetLeft - tabs.offsetLeft - tabs.clientWidth / 2 + selectedTab.clientWidth / 2;
    state.renderedView = state.view;
  }

  function toast(message) {
    clearTimeout(toastTimer);
    const node = app.querySelector('.toast');
    node.textContent = message;
    node.classList.add('visible');
    toastTimer = setTimeout(() => node.classList.remove('visible'), 2600);
  }

  function hasDirty() {
    return report().headerDirty || report().sections.some(item => item.dirty);
  }

  function switchDate(date, confirmed = false) {
    if (date === state.date) { state.sheet = null; render(); return true; }
    if (hasDirty() && !confirmed) {
      state.pendingDate = date;
      state.previousSheet = state.sheet;
      state.sheet = 'date-switch-confirm';
      render();
      return false;
    }
    if (!reports.has(date)) reports.set(date, makeReport(date));
    state.date = date;
    state.view = 'overview';
    state.sheet = null;
    render(true);
    return true;
  }

  function shiftDate(offset) {
    switchDate(dateWithOffset(state.date, offset));
  }

  function markDirty(isHeader = false) {
    if (isHeader) report().headerDirty = true;
    else section().dirty = true;
    const badgeNode = app.querySelector('.group-context .badge');
    if (badgeNode && !isHeader) { badgeNode.className = 'badge unsaved'; badgeNode.textContent = '未保存'; }
    const overallNode = app.querySelector('.date-card .status-pill');
    if (overallNode) overallNode.textContent = '未保存';
  }

  function updateVisibleMissing(item, input) {
    if (state.view !== 'group' || section().status !== 'SUBMITTED') return;
    const row = input.closest('.metric-row');
    const needsAttention = itemIsEmpty(item);
    if (row) {
      row.classList.toggle('is-missing', needsAttention);
      input.classList.toggle('needs-attention', needsAttention);
      input.placeholder = needsAttention ? '可补录' : input.dataset.field === 'remark' ? '填写备注' : '点击填写';
      const unit = row.querySelector('.metric-unit');
      if (unit) unit.innerHTML = `单位：${text(item.unit)}${needsAttention ? '<span class="metric-missing">未填</span>' : otherValues(item) ? '<span class="metric-other"> · 其他字段已填</span>' : ''}`;
    }
    const count = missingCount(group());
    let alert = app.querySelector('.missing-alert');
    if (count && !alert) {
      app.querySelector('.field-switch').insertAdjacentHTML('beforebegin', renderMissingAlert(count, true));
      alert = app.querySelector('.missing-alert');
    }
    if (alert) { alert.hidden = count === 0; alert.querySelector('.missing-count').textContent = count; }
  }

  function saveSection() {
    const current = section();
    current.dirty = false;
    current.status = 'DRAFT';
    current.updated = '当前用户 · 刚刚';
    report().persisted = true;
    render();
    toast('当前分区已保存为草稿（原型模拟）');
  }

  function selectProduct(productId) {
    const selected = PRODUCTS.find(item => item.id === productId);
    if (!selected) return;
    let line = state.pickerTargetId ? group().products.find(item => item.id === state.pickerTargetId) : null;
    if (!line) {
      line = { id: `line-${++productSequence}`, kind: 'product', unit: '件' };
      group().products.push(line);
    }
    line.productId = selected.id;
    line.name = selected.name;
    markDirty();
    state.itemId = line.id;
    state.pickerTargetId = null;
    state.sheet = 'item';
    render();
  }

  app.addEventListener('click', event => {
    const control = event.target.closest('[data-action]');
    if (!control || !app.contains(control)) return;
    const action = control.dataset.action;
    if (action === 'overlay' && event.target !== control) return;
    if (action === 'overlay' || action === 'close-sheet' || action === 'cancel-confirm') {
      state.sheet = state.sheet === 'date-switch-confirm' ? state.previousSheet
        : state.sheet === 'delete-confirm' ? 'item' : null;
      state.pendingDate = null;
      state.pendingDeleteId = null;
      state.previousSheet = null;
      render(); return;
    }
    if (action === 'confirm-date-switch') { const date = state.pendingDate; state.pendingDate = null; state.previousSheet = null; switchDate(date, true); return; }
    if (action === 'confirm-delete') {
      group().products = group().products.filter(item => item.id !== state.pendingDeleteId);
      markDirty(); state.pendingDeleteId = null; state.sheet = null; render(); toast('产品行已移除'); return;
    }
    if (action === 'back') {
      if (state.sheet) { state.sheet = null; render(); }
      else if (state.view === 'group') { state.view = 'section'; render(true); }
      else if (state.view === 'section') { state.view = 'overview'; render(true); }
      else toast('这里是独立交互原型，已在日报首页');
      return;
    }
    if (action === 'more' || action === 'calendar' || action === 'edit-header') {
      state.sheet = { more: 'more', calendar: 'calendar', 'edit-header': 'header' }[action];
      if (action === 'calendar') state.calendarMonth = state.date.slice(0, 7);
      render(); return;
    }
    if (action === 'month-shift') { state.calendarMonth = monthWithOffset(state.calendarMonth, Number(control.dataset.offset)); render(); return; }
    if (action === 'calendar-today') { switchDate(isoDate(new Date())); return; }
    if (action === 'open-section') { state.sectionCode = control.dataset.code; state.view = 'section'; render(true); return; }
    if (action === 'open-group') { state.groupCode = control.dataset.code; state.field = 'dailyActual'; state.view = 'group'; render(true); return; }
    if (action === 'open-item') { state.itemId = control.dataset.id; state.sheet = 'item'; render(); return; }
    if (action === 'set-field') { state.field = control.dataset.field; render(); return; }
    if (action === 'shift-date') { shiftDate(Number(control.dataset.offset)); return; }
    if (action === 'today') { switchDate(isoDate(new Date())); return; }
    if (action === 'pick-date') { switchDate(control.dataset.date); return; }
    if (action === 'save-header') { report().headerDirty = false; report().persisted = true; state.sheet = null; render(); toast('制表信息已保存（原型模拟）'); return; }
    if (action === 'save-section') { saveSection(); return; }
    if (action === 'submit-section' || action === 'submit-all') {
      state.confirmAction = action === 'submit-all' ? 'all' : 'section'; state.sheet = 'confirm'; render(); return;
    }
    if (action === 'confirm-submit') {
      report().persisted = true;
      if (state.confirmAction === 'all') {
        report().headerDirty = false;
        report().sections.forEach(item => { item.dirty = false; item.status = 'SUBMITTED'; item.updated = '当前用户 · 刚刚'; });
      } else {
        section().dirty = false; section().status = 'SUBMITTED'; section().updated = '当前用户 · 刚刚';
      }
      state.sheet = null; render(); toast('提交成功（原型模拟）'); return;
    }
    if (action === 'add-product' || action === 'choose-product') {
      state.pickerTargetId = action === 'choose-product' ? control.dataset.id : null;
      state.sheet = 'product-picker'; render(); return;
    }
    if (action === 'pick-product') { selectProduct(control.dataset.product); return; }
    if (action === 'move-product') {
      const current = group().products; const from = current.findIndex(item => item.id === control.dataset.id);
      const to = from + Number(control.dataset.direction);
      if (from >= 0 && to >= 0 && to < current.length) {
        [current[from], current[to]] = [current[to], current[from]]; markDirty();
        state.sheet = null; render(); toast('产品顺序已调整');
      } else toast('已经是第一条或最后一条');
      return;
    }
    if (action === 'delete-product') {
      state.pendingDeleteId = control.dataset.id;
      state.sheet = 'delete-confirm'; render();
      return;
    }
    if (action === 'demo-file') { state.sheet = null; render(); toast(`${control.dataset.file}将在正式版接入现有接口`); }
  });

  app.addEventListener('input', event => {
    const input = event.target;
    if (input.dataset.item && input.dataset.field) {
      const current = itemById(input.dataset.item);
      if (current) { current[input.dataset.field] = input.value; markDirty(); updateVisibleMissing(current, input); }
    } else if (input.dataset.header) {
      report()[input.dataset.header] = input.value;
      markDirty(true);
    } else if (input.dataset.search === 'product') {
      const query = input.value.trim().toLowerCase();
      const options = [...app.querySelectorAll('.product-option')];
      options.forEach(option => { option.hidden = !option.dataset.name.toLowerCase().includes(query); });
      app.querySelector('.empty-copy').hidden = options.some(option => !option.hidden);
    }
  });

  render(true);
})();
