Page({
  data: {
    quickRules: [
      '作业前先确认模式，避免把查询当作作业提交。',
      '重复扫码不会重复建任务，先看顶部反馈再继续。',
      '批量确认、批量取消前都会再次确认。'
    ],
    sections: [
      {
        key: 'login',
        title: '登录与绑定',
        summary: '先登录，再完成账号绑定。',
        open: true,
        items: [
          '已在员工名册中的账号可直接一键登录。',
          '首次登录时，可通过手机号或工号完成绑定。',
          '手机号不一致时，优先使用工号绑定。'
        ]
      },
      {
        key: 'scan',
        title: '扫码作业',
        summary: '扫码页只做现场执行，不做复杂查询。',
        open: true,
        items: [
          '扫码页只处理现场作业：入库、出库、转入备料池、调拨。',
          '连续扫码会自动加入本次任务池。',
          '重复扫码会直接提示，不会重复加入。'
        ]
      },
      {
        key: 'task',
        title: '任务处理',
        summary: '支持本次任务池和任务中心两处继续处理。',
        open: false,
        items: [
          '创建后的任务可在扫码页本次池或任务中心继续处理。',
          '批量确认和批量取消前都会再次确认。',
          '入库任务确认时，需要补充目标库位。'
        ]
      },
      {
        key: 'query',
        title: '托盘查询',
        summary: '单码查询优先，先看状态、位置和可执行操作。',
        open: false,
        items: [
          '查询中心支持手动输入托盘码和扫码查询。',
          '托盘详情第一屏优先展示状态、产品、位置和可执行操作。',
          '流转、化验和当前任务会在下方继续展示。'
        ]
      },
      {
        key: 'faq',
        title: '常见问题',
        summary: '先看反馈文案，再决定是否重扫或重试。',
        open: false,
        items: [
          '扫了没反应：先看顶部反馈，再检查网络状态。',
          '提示不可处理：说明当前托盘状态与作业模式不匹配。',
          '任务误扫：先从本次任务池移除，若已创建任务再执行取消。'
        ]
      }
    ]
  },

  toggleSection(e) {
    const key = e.currentTarget.dataset.key;
    this.setData({
      sections: this.data.sections.map(item => item.key === key ? { ...item, open: !item.open } : item)
    });
  }
});
