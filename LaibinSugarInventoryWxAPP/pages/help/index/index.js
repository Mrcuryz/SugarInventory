import { getProfile } from '../../../api/auth';
import { requireLogin } from '../../../utils/auth';
import {
  getHelpCenterState,
  setHelpCenterState,
  setTaskViewPreference
} from '../../../utils/storage';
import { showToast } from '../../../utils/toast';

const DEFAULT_SECTION_KEY = 'login';

const SECTION_DEFINITIONS = [
  {
    key: 'login',
    shortTitle: '登录',
    title: '登录与绑定',
    description: '用于完成账号登录、手机号绑定和工号绑定。',
    summary: '先确认员工身份已在名册中，再选择手机号或工号完成绑定。',
    steps: [
      '打开登录页，先尝试一键登录。',
      '若提示需要绑定，优先使用微信手机号绑定。',
      '手机号不一致或无法授权时，改用工号和姓名完成绑定。',
      '绑定成功后返回工作台，后续可直接登录。'
    ],
    notices: [
      '员工信息必须先在后台员工名册中存在。',
      '手机号绑定失败时，不代表账号不可用，通常可改走工号绑定。',
      '退出登录不会清除后台已创建的业务数据。'
    ],
    faqs: [
      {
        q: '提示“未匹配到员工信息”怎么办？',
        a: '先核对员工名册中的姓名、手机号和工号，再重新绑定。'
      },
      {
        q: '微信手机号和名册手机号不一致怎么办？',
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
    description: '用于现场快速处理入库、出库、转入备料池、调拨等动作。',
    summary: '先选作业模式，再连续扫码，结果统一进入本次任务池闭环处理。',
    steps: [
      '先确认当前作业模式是否正确。',
      '点击开始扫码，逐个扫描二维码。',
      '扫码后检查是否进入本次任务池，并核对提示信息。',
      '完成后在底部统一批量确认或取消。'
    ],
    notices: [
      '重复扫码不会重复加入任务池。',
      '切换作业模式前，先确认当前任务池是否需要清空。',
      '固定产品二维码在未启用或已创建任务时，不需要再次绑定创建。'
    ],
    faqs: [
      {
        q: '扫码没有反应怎么办？',
        a: '先看页面顶部反馈，再检查网络和相机权限。'
      },
      {
        q: '提示“状态不允许”怎么办？',
        a: '说明二维码当前状态与作业模式不匹配，应先去详情页核对状态。'
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
    description: '用于集中查看并处理待确认、已完成、已取消的任务。',
    summary: '任务中心负责接住扫码产生的任务，并按状态继续处理。',
    steps: [
      '进入任务中心，默认先看待处理任务。',
      '按任务状态或任务类型筛选目标任务。',
      '勾选需要处理的任务，执行批量确认或批量取消。',
      '必要时点击单条任务，进入二维码详情核对业务链路。'
    ],
    notices: [
      '入库确认前，需要补充目标库位和日期等必要信息。',
      '批量操作前先核对选中数量，避免误处理。',
      '任务一旦取消，需要重新扫码才能再次创建。'
    ],
    faqs: [
      {
        q: '为什么看不到刚扫的任务？',
        a: '先确认当前筛选是否落在待处理状态，并检查是否误切到其他任务类型。'
      },
      {
        q: '为什么确认按钮是灰的？',
        a: '通常是当前没有选中任务，或当前列表不是待处理状态。'
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
    description: '用于现场单码核对，快速查看二维码状态、位置、产品和待处理任务。',
    summary: '二维码查询是现场排错和追溯的总入口。',
    steps: [
      '在查询中心输入二维码编号，或直接调用扫码查询。',
      '进入详情页后，先看当前状态、固定产品和当前位置。',
      '继续查看流转记录、化验信息和待处理任务。',
      '需要继续作业时，从详情页直接跳到扫码页或任务中心。'
    ],
    notices: [
      '查询优先用于核对状态，不建议在未确认状态前直接重复创建任务。',
      '固定产品二维码会显示当前固定产品名称。',
      '有待处理任务时，优先处理任务，不要重复发起新动作。'
    ],
    faqs: [
      {
        q: '输入编号后提示查不到怎么办？',
        a: '先核对是否输错编号，再确认该二维码是否已导入系统。'
      },
      {
        q: '详情里显示状态异常怎么办？',
        a: '优先看流转记录和待处理任务，再决定是否需要联系管理员排查。'
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
    summary: '先看汇总，再点开对应产品查看二维码明细。',
    steps: [
      '输入产品名称或选择成品、半成品条件。',
      '先查看汇总卡中的总板数、总件数、总重量和涉及库位。',
      '点击进入二维码明细，查看每个二维码的状态和位置。',
      '如需继续核对化验，可从库存结果跳到化验查询。'
    ],
    notices: [
      '库存查询是产品维度的总览，不等于单码详情。',
      '二维码明细只展示当前符合条件的在库数据。',
      '库位空间图仍以 Web 管理端查看为准。'
    ],
    faqs: [
      {
        q: '为什么有产品汇总但没有二维码明细？',
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
    summary: '化验查询先看结论，再进入详情核对实测值和标准范围。',
    steps: [
      '按产品名称、化验员、日期或结论筛选记录。',
      '先看列表中的合格结论和采用标准。',
      '点击进入详情，查看指标实测值、标准范围和结论说明。',
      '必要时继续查看历史版本，核对不同版本的差异。'
    ],
    notices: [
      '没有采用标准时，系统会明确显示无法判定。',
      '不合格时要优先看不合格指标和原因说明。',
      '历史数据用于追溯，不等于当前最新结论。'
    ],
    faqs: [
      {
        q: '为什么看到“无标准”或“待确认”？',
        a: '说明当前记录未命中唯一标准，需要先检查标准配置或历史数据。'
      },
      {
        q: '为什么搜索不到化验记录？',
        a: '先收窄日期和产品条件，再确认该二维码是否已关联化验数据。'
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
    description: '用于按时间、对象和操作类型追溯系统中的关键动作。',
    summary: '作业记录更适合追查谁在什么时候做了什么操作。',
    steps: [
      '先按操作人、对象类型或操作类型筛选记录。',
      '优先查看摘要，快速判断是否是目标操作。',
      '需要深查时，再结合二维码详情页的流转记录一起核对。',
      '排查完成后，回到业务页面修正错误或重新执行。'
    ],
    notices: [
      '作业记录偏审计视角，现场流转细节仍以二维码详情为准。',
      '时间筛选建议先缩小到当天或单个班次。',
      '无权限时不要反复刷新，直接联系管理员开通。'
    ],
    faqs: [
      {
        q: '为什么看不到自己刚做的动作？',
        a: '先确认筛选条件是否过窄，再确认该动作是否属于审计记录范围。'
      },
      {
        q: '为什么只看到摘要看不到业务明细？',
        a: '作业记录只保留审计摘要，业务细节应回到二维码详情或对应页面核对。'
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
    shortTitle: '固码',
    title: '固定产品二维码',
    description: '用于理解后台预绑定产品的二维码在现场如何被使用。',
    summary: '这类二维码的产品已由后台固定，现场扫码时重点核对状态和任务，不要重复绑定。',
    steps: [
      '先在详情页确认当前固定产品名称。',
      '若二维码尚未启用，不要在小程序重复创建入库任务。',
      '若后台已打印并启用，扫码后应直接进入既有任务链路。',
      '需要追溯异常时，优先查看二维码详情和待处理任务。'
    ],
    notices: [
      '固定产品二维码属于后台预配置模式，现场无需再次绑定产品。',
      '只有后台打印并启用后，二维码才正式进入业务流。',
      '若系统提示“无需重复创建”，说明当前二维码已有对应任务状态。'
    ],
    faqs: [
      {
        q: '为什么提示“无需绑定产品”？',
        a: '因为该二维码已预先固定产品，现场不需要再做产品绑定。'
      },
      {
        q: '为什么提示“无需重复创建”？',
        a: '因为后台已启用并创建了任务，现场应转去任务中心继续处理。'
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
    shortTitle: '常问',
    title: '常见问题',
    description: '用于快速处理现场高频报错、状态拦截和权限问题。',
    summary: '先看提示文案，再决定是重扫、换入口，还是联系管理员。',
    steps: [
      '先记录当前提示文案，不要只记结论。',
      '核对二维码当前状态、所在页面和所选模式是否一致。',
      '优先从二维码查询进入详情，确认待处理任务和流转记录。',
      '确认仍无法处理后，再联系管理员并描述完整现象。'
    ],
    notices: [
      '多数“状态不允许”都与当前业务阶段不匹配有关。',
      '重复扫码通常不会重复创建任务，先看页面反馈。',
      '权限不足时，继续点击不会生效，应直接申请开通权限。'
    ],
    faqs: [
      {
        q: '提示“当前账号无权限访问该页面”怎么办？',
        a: '说明角色未开通对应页面权限，需要联系管理员分配权限。'
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
    index,
    actions: section.actions.map(action => ({
      ...action,
      disabled: !isActionAllowed(action, permissionCodes)
    })),
    hasDisabledActions: section.actions.some(action => !isActionAllowed(action, permissionCodes))
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
