// 角色权限沿用后端原有编码，仅在前端按“河小智”产品架构展示。
// 不能修改 perm，否则会导致已有角色授权和接口鉴权失效。
const PERMISSION_NAME_MAP = {
  wga: '河小智助手',
  'wga.wanwu_bot': '河小智智能体',
  'wga.openclaw': '智能助手扩展',

  ontology: '数据资源中心',
  'ontology.digital_employee': '校园数字员工',
  'ontology.knowledge_network': '知识网络',
  'ontology.data_source': '数据资源连接',

  public_opinion: '校园舆情研判',
  'public_opinion.view': '舆情信息查看',
  'public_opinion.manage': '舆情数据管理',
  'public_opinion.analysis': '舆情智能分析',

  model: '模型管理',
  'model.model_management': '模型配置',

  resource: '平台能力',
  'resource.knowledge': '校园知识中心',
  'resource.mcp': '能力连接中心',
  'resource.tool': '插件工具',
  'resource.prompt': 'Prompt 管理',
  'resource.skill': '技能中心',
  'resource.safety': '安全护栏',

  app: '智能服务',
  'app.rag': '知识应用开发',
  'app.workflow': '工作流编排',
  'app.agent': '智能体工作台',

  exploration: '应用服务中心',
  'exploration.app': '应用服务管理',
  'exploration.mcp': 'MCP 服务广场',
  'exploration.template': '应用模板中心',
  'exploration.skill': '技能广场',

  operation: '运营管理',
  'operation.statistic_client': '服务统计分析',

  app_observability: '智慧校园管理中心',
  'app_observability.statistic': '校园服务运行看板',

  api_key: 'API Key 管理',
  'api_key.api_key_management': 'API Key 配置',
};

const HIDDEN_PERMISSION_PREFIXES = ['open_source'];

export function isPermissionVisible(permission) {
  if (!permission || !permission.perm) return true;
  return !HIDDEN_PERMISSION_PREFIXES.some(
    prefix =>
      permission.perm === prefix || permission.perm.startsWith(`${prefix}.`),
  );
}

export function getPermissionDisplayName(permission) {
  if (!permission) return '';
  return PERMISSION_NAME_MAP[permission.perm] || permission.name;
}

export function formatPermissionItem(permission) {
  if (!permission) return permission;

  const formatted = {
    ...permission,
    name: getPermissionDisplayName(permission),
  };

  if (Array.isArray(permission.children)) {
    formatted.children = permission.children
      .filter(isPermissionVisible)
      .map(formatPermissionItem);
  }

  return formatted;
}

export function formatPermissionTree(routes) {
  return (routes || []).filter(isPermissionVisible).map(formatPermissionItem);
}
