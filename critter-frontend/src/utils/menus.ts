/**
 * 菜单码 → 中文名/路径的兜底映射。
 * 正常展示用后端下发的 MenuView（含 path 与归属人），这里只用于
 * 后台角色矩阵这类"只有 code"的场景，避免每处各写一份。
 */
export const MENU_TITLES: Record<string, string> = {
  home: '工作台',
  archive: '生物档案',
  adopt: '认养专区',
  report: '救助上报',
  fund: '资金透明',
  medical: '医疗协作',
  volunteer: '志愿任务',
  content: '消息中心',
  stats: '数据统计',
  admin: '后台管理',
  me: '个人中心',
}

export const MENU_OWNERS: Record<string, string> = {
  home: 'A',
  archive: 'B',
  adopt: 'C',
  report: 'B',
  fund: 'B',
  medical: 'C',
  volunteer: 'C',
  content: 'C',
  stats: 'A',
  admin: 'A',
  me: 'A',
}

export function menuTitle(code: string): string {
  return MENU_TITLES[code] ?? code
}
