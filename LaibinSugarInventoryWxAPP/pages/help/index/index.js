import { getProfile } from '../../../api/auth';
import { requireLogin } from '../../../utils/auth';
import {
  getHelpCenterState,
  setHelpCenterState,
  setTaskViewPreference
} from '../../../utils/storage';
import { showToast } from '../../../utils/toast';

const DEFAULT_SECTION_KEY = 'home';

const SECTION_DEFINITIONS = [
  {
    key: 'home',
    shortTitle: '首页',
    title: '帮助中心首页',
    description: '先看整体说明，再按目录进入具体章节。',
    poster: '/assets/poster.png',
    summary: '小程序面向现场作业，核心是扫码执行、任务处理、库存查询和二维码追溯。进入对应章节后，可以按流程说明核对操作入口、现场注意事项和常见问题。'
  },
  {
    key: 'login',
    shortTitle: '登录',
    title: '登录与绑定',
    description: '用于完成员工登录、手机号绑定和工号绑定。',
    summary: '先确认员工信息已在后台名册中存在，再按实际情况选择微信一键登录、手机号绑定或工号绑定。',
    steps: [
      '打开登录页，先尝试微信一键登录。',
      '如果提示需要绑定，优先使用手机号绑定。',
      '手机号不一致或无法授权时，改用工号绑定。',
      '绑定成功后返回工作台，后续即可直接登录。'
    ],
    notices: [
      '员工信息必须先由管理员录入后台。',
      '退出登录只会清理当前设备登录态，不会删除后台业务数据。',
      '手机号绑定失败时，不代表账号不可用，通常可以改走工号绑定。'
    ],
    faqs: [
      {
        q: '提示“未匹配到员工信息”怎么办？',
        a: '先检查后台员工名册里的姓名、手机号和工号是否正确，再重新绑定。'
      },
      {
        q: '微信手机号和员工名册手机号不一致怎么办？',
        a: '直接改用工号绑定，不要反复尝试手机号授权。'
      }
    ],
    actions: [
      { label: '去登录', kind: 'primary', routeType: 'reLaunch', url: '/pages/auth/login/index' },
      { label: '去手机号绑定', kind: 'secondary', routeType: 'navigateTo', url: '/pages/auth/bind-phone/index' },
      { label: '去工号绑定', kind: 'secondary', routeType: 'navigateTo', url: '/pages/auth/bind-manual/index' }
    ]
  },
  {
    key: 'scan',
    shortTitle: '扫码',
    title: '扫码作业',
    description: '用于现场快速处理入库、出库、转入备料池和调拨。',
    summary: '扫码页以连续扫码为主，扫到的二维码会统一进入本次任务池，再集中确认或取消。',
    steps: [
      '先确认当前作业模式是否正确。',
      '点击开始扫码，逐个扫描二维码。',
      '扫码后查看是否进入本次任务池，并留意页面反馈。',
      '完成后在底部统一批量确认或取消。'
    ],
    notices: [
      '重复扫码不会重复加入任务池。',
      '切换模式前，先确认当前任务池是否需要清空。',
      '固定产品二维码如果未启用或已创建任务，不需要现场重复绑定。'
    ],
    faqs: [
      {
        q: '扫码没有反应怎么办？',
        a: '先看顶部反馈，再检查网络、相机权限和当前模式。'
      },
      {
        q: '提示“状态不允许”怎么办？',
        a: '先去二维码查询页核对当前状态和待处理任务，再决定是否继续操作。'
      }
    ],
    actions: [
      {
        label: '去扫码页',
        kind: 'primary',
        routeType: 'switchTab',
        url: '/pages/scan/index/index',
        scanMode: 'in',
        anyPermissions: ['task:create']
      },
      {
        label: '去任务中心',
        kind: 'secondary',
        routeType: 'switchTab',
        url: '/pages/tasks/index/index',
        taskView: { status: 'PENDING', taskType: '' },
        anyPermissions: ['task:view']
      },
      {
        label: '去二维码查询',
        kind: 'secondary',
        routeType: 'switchTab',
        url: '/pages/query/index/index',
        anyPermissions: ['qrcode:view']
      }
    ]
  },
  {
    key: 'task',
    shortTitle: '任务',
    title: '任务处理',
    description: '用于集中处理待确认、已完成和已取消任务。',
    summary: '任务中心负责承接扫码产生的任务，并按状态继续处理，适合批量确认和批量取消。',
    steps: [
      '进入任务中心，默认先看待处理任务。',
      '按状态或任务类型筛选目标任务。',
      '勾选后执行批量确认或批量取消。',
      '需要核对业务明细时，先回到二维码详情页查看完整链路。'
    ],
    notices: [
      '入库确认前先补齐库位、日期等必要信息。',
      '批量操作前先核对选中数量。',
      '任务取消后，如需重做，通常需要重新扫码创建。'
    ],
    faqs: [
      {
        q: '看不到刚扫出的任务怎么办？',
        a: '先检查筛选条件是否落在待处理状态，再确认是否切到了别的任务类型。'
      },
      {
        q: '确认按钮为什么是灰色的？',
        a: '通常是当前没有选中任务，或者当前列表不是待处理状态。'
      }
    ],
    actions: [
      {
        label: '去任务中心',
        kind: 'primary',
        routeType: 'switchTab',
        url: '/pages/tasks/index/index',
        taskView: { status: 'PENDING', taskType: '' },
        anyPermissions: ['task:view']
      },
      {
        label: '去扫码作业',
        kind: 'secondary',
        routeType: 'switchTab',
        url: '/pages/scan/index/index',
        scanMode: 'in',
        anyPermissions: ['task:create']
      },
      {
        label: '去二维码查询',
        kind: 'secondary',
        routeType: 'switchTab',
        url: '/pages/query/index/index',
        anyPermissions: ['qrcode:view']
      }
    ]
  },
  {
    key: 'qrcode',
    shortTitle: '码查',
    title: '二维码查询',
    description: '用于现场单码核对，快速查看状态、位置、产品和待处理任务。',
    summary: '二维码查询是现场排错和追溯的总入口。先看状态，再看位置、任务和流转记录。',
    steps: [
      '输入二维码编号，或直接扫码查询。',
      '先看当前状态、固定产品和当前位置。',
      '继续查看流转记录、化验信息和待处理任务。',
      '需要继续作业时，直接从详情页跳回扫码或任务中心。'
    ],
    notices: [
      '查询优先用于核对状态，不建议在未确认前重复创建任务。',
      '固定产品二维码会显示当前固定产品名称。',
      '有待处理任务时，优先处理任务，不要重复发起新动作。'
    ],
    faqs: [
      {
        q: '输入编号后提示查不到怎么办？',
        a: '先确认编号无误，再检查该二维码是否已导入系统。'
      },
      {
        q: '详情里状态看起来异常怎么办？',
        a: '优先看流转记录和待处理任务，再决定是否联系管理员处理。'
      }
    ],
    actions: [
      {
        label: '去查询中心',
        kind: 'primary',
        routeType: 'switchTab',
        url: '/pages/query/index/index',
        anyPermissions: ['qrcode:view']
      },
      {
        label: '去扫码页',
        kind: 'secondary',
        routeType: 'switchTab',
        url: '/pages/scan/index/index',
        scanMode: 'in',
        anyPermissions: ['task:create']
      },
      {
        label: '去任务中心',
        kind: 'secondary',
        routeType: 'switchTab',
        url: '/pages/tasks/index/index',
        taskView: { status: 'PENDING', taskType: '' },
        anyPermissions: ['task:view']
      }
    ]
  },
  {
    key: 'inventory',
    shortTitle: '库存',
    title: '库存查询',
    description: '用于按产品查看库存汇总，并继续追到具体二维码明细。',
    summary: '库存查询先看产品汇总，再看二维码明细，适合快速核对在库数量和位置分布。',
    steps: [
      '按产品名称或成品/半成品筛选。',
      '先看汇总卡里的数量和重量。',
      '再进入明细查看具体二维码状态和位置。',
      '需要继续核对时，转去二维码查询或化验查询。'
    ],
    notices: [
      '库存查询是产品维度的总览，不等于单码详情。',
      '二维码明细通常只展示当前符合筛选条件的在库数据。',
      '精确库位布局仍以 Web 端仓库平面图为准。'
    ],
    faqs: [
      {
        q: '为什么有库存汇总却没有二维码明细？',
        a: '先检查筛选条件和分页，再确认该产品是否存在正常在库二维码。'
      },
      {
        q: '为什么位置显示为空？',
        a: '说明当前二维码没有读取到普通库存位置信息，需要进一步核对业务状态。'
      }
    ],
    actions: [
      {
        label: '去库存查询',
        kind: 'primary',
        routeType: 'navigateTo',
        url: '/pages/query/inventory/index',
        anyPermissions: ['inventory:view', 'record:query']
      },
      {
        label: '去二维码查询',
        kind: 'secondary',
        routeType: 'switchTab',
        url: '/pages/query/index/index',
        anyPermissions: ['qrcode:view']
      },
      {
        label: '去化验查询',
        kind: 'secondary',
        routeType: 'navigateTo',
        url: '/pages/query/assay/index',
        anyPermissions: ['assay:query', 'assay:view', 'quality:test']
      }
    ]
  },
  {
    key: 'assay',
    shortTitle: '化验',
    title: '化验查询',
    description: '用于查看化验结论、指标明细、标准匹配和历史记录。',
    summary: '化验查询先看结论，再看指标、标准范围和不合格原因。',
    steps: [
      '按产品、日期、化验员或结论筛选记录。',
      '先看列表中的合格结论和采用标准。',
      '进入详情后查看实测值、标准范围和判定结果。',
      '需要追溯时，继续查看历史版本或二维码详情。'
    ],
    notices: [
      '没有采用标准时，系统会明确显示无法判定。',
      '不合格时应先看不合格指标和原因说明。',
      '历史版本用于追溯，不等于当前最新结论。'
    ],
    faqs: [
      {
        q: '为什么会看到“无标准”或“无法判定”？',
        a: '说明当前记录没有命中唯一标准，需要先检查标准配置或产品关联。'
      },
      {
        q: '为什么搜索不到化验记录？',
        a: '先缩小日期和产品范围，再确认该二维码是否已经关联化验数据。'
      }
    ],
    actions: [
      {
        label: '去化验查询',
        kind: 'primary',
        routeType: 'navigateTo',
        url: '/pages/query/assay/index',
        anyPermissions: ['assay:query', 'assay:view', 'quality:test']
      },
      {
        label: '去库存查询',
        kind: 'secondary',
        routeType: 'navigateTo',
        url: '/pages/query/inventory/index',
        anyPermissions: ['inventory:view', 'record:query']
      },
      {
        label: '去二维码查询',
        kind: 'secondary',
        routeType: 'switchTab',
        url: '/pages/query/index/index',
        anyPermissions: ['qrcode:view']
      }
    ]
  },
  {
    key: 'records',
    shortTitle: '记录',
    title: '作业记录',
    description: '用于按时间、对象和操作类型追溯关键动作。',
    summary: '作业记录更适合查看谁在什么时间做了什么，适合作为排错和追溯入口。',
    steps: [
      '按操作人、对象类型或操作类型筛选记录。',
      '先看摘要，判断是否是目标操作。',
      '再结合二维码详情和流转记录一起核对。',
      '确认原因后，回到业务页处理问题。'
    ],
    notices: [
      '作业记录偏审计视角，流转细节仍以二维码详情为准。',
      '时间筛选建议先缩小到当天或单个班次。',
      '无权限时应直接联系管理员开通。'
    ],
    faqs: [
      {
        q: '为什么看不到自己刚做的动作？',
        a: '先检查筛选条件是否过窄，再确认该动作是否属于记录范围。'
      },
      {
        q: '为什么只有摘要看不到业务细节？',
        a: '作业记录只保存审计摘要，详细业务应回到二维码详情或目标页面核对。'
      }
    ],
    actions: [
      {
        label: '去作业记录',
        kind: 'primary',
        routeType: 'navigateTo',
        url: '/pages/query/records/index',
        anyPermissions: ['record:view', 'record:query', 'log:view']
      },
      {
        label: '去任务中心',
        kind: 'secondary',
        routeType: 'switchTab',
        url: '/pages/tasks/index/index',
        taskView: { status: 'PENDING', taskType: '' },
        anyPermissions: ['task:view']
      },
      {
        label: '去二维码查询',
        kind: 'secondary',
        routeType: 'switchTab',
        url: '/pages/query/index/index',
        anyPermissions: ['qrcode:view']
      }
    ]
  },
  {
    key: 'fixed-qrcode',
    shortTitle: '固定码',
    title: '固定产品二维码',
    description: '用于理解后台预绑定产品的二维码在现场如何使用。',
    summary: '固定产品二维码的产品由后台预先绑定。现场重点是看状态和任务，不要重复绑定或重复创建。',
    steps: [
      '先在详情页确认当前固定产品名称。',
      '如果二维码尚未启用，不要在小程序重复创建入库任务。',
      '如果后台已打印并启用，扫码后应直接进入既有任务链路。',
      '需要追溯异常时，优先看二维码详情和待处理任务。'
    ],
    notices: [
      '固定产品二维码属于后台预配置模式，现场无需再次绑定产品。',
      '只有后台打印并启用后，二维码才正式投入业务流。',
      '如果系统提示“无需重复创建”，说明当前二维码已有任务状态。'
    ],
    faqs: [
      {
        q: '为什么提示“无需绑定产品”？',
        a: '因为该二维码已经固定产品，现场不需要再次绑定。'
      },
      {
        q: '为什么提示“无需重复创建”？',
        a: '因为后台已经启用并创建任务，现场应直接去任务中心继续处理。'
      }
    ],
    actions: [
      {
        label: '去扫码作业',
        kind: 'primary',
        routeType: 'switchTab',
        url: '/pages/scan/index/index',
        scanMode: 'in',
        anyPermissions: ['task:create']
      },
      {
        label: '去二维码查询',
        kind: 'secondary',
        routeType: 'switchTab',
        url: '/pages/query/index/index',
        anyPermissions: ['qrcode:view']
      },
      {
        label: '去任务中心',
        kind: 'secondary',
        routeType: 'switchTab',
        url: '/pages/tasks/index/index',
        taskView: { status: 'PENDING', taskType: '' },
        anyPermissions: ['task:view']
      }
    ]
  },
  {
    key: 'faq',
    shortTitle: '常见问',
    title: '常见问题',
    description: '用于快速处理现场高频报错、状态拦截和权限问题。',
    summary: '先看系统提示原文，再判断是重扫、换入口，还是联系管理员。',
    steps: [
      '先记录完整提示文案，不要只记结论。',
      '核对二维码当前状态、所在页面和作业模式是否一致。',
      '优先从二维码查询进入详情，看待处理任务和流转记录。',
      '确认仍无法处理后，再联系管理员。'
    ],
    notices: [
      '大多数“状态不允许”都与当前业务阶段不匹配有关。',
      '重复扫码通常不会重复创建任务，先看页面反馈。',
      '权限不足时继续点击不会生效，应直接申请开通权限。'
    ],
    faqs: [
      {
        q: '提示“当前账号无权限访问该页面”怎么办？',
        a: '说明当前角色未开通对应权限，需要联系管理员分配权限。'
      },
      {
        q: '找不到二维码怎么办？',
        a: '先确认编号无误，再从查询中心或库存查询交叉核对。'
      }
    ],
    actions: [
      {
        label: '去扫码作业',
        kind: 'primary',
        routeType: 'switchTab',
        url: '/pages/scan/index/index',
        scanMode: 'in',
        anyPermissions: ['task:create']
      },
      {
        label: '去二维码查询',
        kind: 'secondary',
        routeType: 'switchTab',
        url: '/pages/query/index/index',
        anyPermissions: ['qrcode:view']
      },
      {
        label: '去任务中心',
        kind: 'secondary',
        routeType: 'switchTab',
        url: '/pages/tasks/index/index',
        taskView: { status: 'PENDING', taskType: '' },
        anyPermissions: ['task:view']
      }
    ]
  }
];

function isActionAllowed(action, permissionCodes) {
  if (!action || !action.anyPermissions || !action.anyPermissions.length) {
    return true;
  }
  return action.anyPermissions.some(code => permissionCodes.includes(code));
}

function decorateSections(permissionCodes = []) {
  return SECTION_DEFINITIONS.map((section, index) => ({
    ...section,
    isHome: section.key === DEFAULT_SECTION_KEY,
    index,
    steps: section.steps || [],
    notices: section.notices || [],
    faqs: section.faqs || [],
    actions: (section.actions || []).map(action => ({
      ...action,
      disabled: !isActionAllowed(action, permissionCodes)
    })),
    hasDisabledActions: (section.actions || []).some(action => !isActionAllowed(action, permissionCodes))
  }));
}

function buildSectionMap(sections = []) {
  return sections.reduce((result, item) => {
    result[item.key] = item;
    return result;
  }, {});
}

Page({
  data: {
    sections: decorateSections(),
    sectionMap: buildSectionMap(decorateSections()),
    activeKey: DEFAULT_SECTION_KEY,
    activeSection: decorateSections()[0],
    tocCollapsed: false,
    permissionCodes: [],
    contentScrollTop: 0
  },

  onLoad() {
    const saved = getHelpCenterState();
    const sections = decorateSections();
    const sectionMap = buildSectionMap(sections);
    const activeKey = sectionMap[saved.activeKey] ? saved.activeKey : DEFAULT_SECTION_KEY;
    this.setData({
      sections,
      sectionMap,
      activeKey,
      activeSection: sectionMap[activeKey],
      tocCollapsed: Boolean(saved.tocCollapsed)
    });
  },

  onShow() {
    if (!requireLogin()) return;
    this.loadProfile();
  },

  async loadProfile() {
    try {
      const userInfo = await getProfile();
      const permissionCodes = Array.isArray(userInfo && userInfo.permissionCodes) ? userInfo.permissionCodes : [];
      const sections = decorateSections(permissionCodes);
      const sectionMap = buildSectionMap(sections);
      const activeKey = sectionMap[this.data.activeKey] ? this.data.activeKey : DEFAULT_SECTION_KEY;
      this.setData({
        permissionCodes,
        sections,
        sectionMap,
        activeSection: sectionMap[activeKey]
      });
    } catch (error) {
      const sections = decorateSections([]);
      const sectionMap = buildSectionMap(sections);
      const activeKey = sectionMap[this.data.activeKey] ? this.data.activeKey : DEFAULT_SECTION_KEY;
      this.setData({
        permissionCodes: [],
        sections,
        sectionMap,
        activeSection: sectionMap[activeKey]
      });
    }
  },

  toggleToc() {
    const tocCollapsed = !this.data.tocCollapsed;
    this.setData({ tocCollapsed });
    setHelpCenterState({ tocCollapsed });
  },

  selectSection(e) {
    const key = e.currentTarget.dataset.key;
    if (!key || key === this.data.activeKey) return;
    const activeSection = this.data.sectionMap[key];
    if (!activeSection) return;
    this.setData({
      activeKey: key,
      activeSection,
      contentScrollTop: 0
    });
    setHelpCenterState({ activeKey: key });
  },

  goPrevSection() {
    const index = this.data.activeSection ? this.data.activeSection.index : 0;
    if (index <= 0) return;
    const target = this.data.sections[index - 1];
    if (!target) return;
    this.setData({
      activeKey: target.key,
      activeSection: target,
      contentScrollTop: 0
    });
    setHelpCenterState({ activeKey: target.key });
  },

  goNextSection() {
    const index = this.data.activeSection ? this.data.activeSection.index : 0;
    const target = this.data.sections[index + 1];
    if (!target) return;
    this.setData({
      activeKey: target.key,
      activeSection: target,
      contentScrollTop: 0
    });
    setHelpCenterState({ activeKey: target.key });
  },

  onActionTap(e) {
    const sectionIndex = Number(e.currentTarget.dataset.sectionIndex);
    const actionIndex = Number(e.currentTarget.dataset.actionIndex);
    const section = this.data.sections[sectionIndex];
    const action = section && section.actions ? section.actions[actionIndex] : null;
    if (!action) return;
    if (action.disabled) {
      showToast('当前账号无权限访问该页面');
      return;
    }

    if (action.scanMode) {
      wx.setStorageSync('preferredScanMode', action.scanMode);
    }
    if (action.taskView) {
      setTaskViewPreference(action.taskView);
    }

    if (action.routeType === 'switchTab') {
      wx.switchTab({ url: action.url });
      return;
    }
    if (action.routeType === 'reLaunch') {
      wx.reLaunch({ url: action.url });
      return;
    }
    wx.navigateTo({ url: action.url });
  }
});
