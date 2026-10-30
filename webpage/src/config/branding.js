// Production defaults stay unchanged; demo builds explicitly opt in.
export const demoMode = import.meta.env.VITE_DEMO_MODE === 'true'
export const companyName = demoMode ? '数字仓储平台演示系统' : '广西糖罐子食品有限公司'
export const reportCompany = demoMode ? '示例企业' : '来冰公司'
