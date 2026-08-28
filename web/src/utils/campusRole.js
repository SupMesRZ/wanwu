export const CAMPUS_ROLE_KEYS = ['student', 'teacher', 'academic_admin'];

export const CAMPUS_ROLE_STATUS = {
  RESOLVED: 'resolved',
  UNCONFIGURED: 'unconfigured',
  CONFLICT: 'conflict',
};

export const CAMPUS_ROLES = {
  student: {
    key: 'student',
    label: '学生',
    assistantName: '河小智学生助手',
    salutation: '同学',
    subtitle: '你的学习与校园生活智能伙伴',
    welcome: '我可以帮你查询课表、考试、成绩、请假记录和学习情况。',
    examples: ['今天有什么课？', '查看我的请假记录', '我最近有什么考试？'],
    abilities: [
      { icon: 'el-icon-reading', text: '学业中心' },
      { icon: 'el-icon-edit-outline', text: '事务办理' },
      { icon: 'el-icon-school', text: '校园服务' },
      { icon: 'el-icon-data-analysis', text: 'AI 学习助手' },
    ],
  },
  teacher: {
    key: 'teacher',
    label: '教师',
    assistantName: '河小智教师助手',
    salutation: '老师',
    subtitle: '你的教学与课程管理智能伙伴',
    welcome: '我可以帮你查询教学安排、智能备课和分析学情反馈。',
    examples: ['我今天有哪些课？', '根据这份资料生成教案', '分析本班高频错题'],
    abilities: [
      { icon: 'el-icon-notebook-2', text: '我的教学' },
      { icon: 'el-icon-document', text: 'AI 智能备课' },
      { icon: 'el-icon-data-line', text: '学情与反馈' },
    ],
  },
  academic_admin: {
    key: 'academic_admin',
    label: '教务',
    assistantName: '河小智教务助手',
    salutation: '',
    subtitle: '校园教学运行与辅助决策中心',
    welcome: '我可以帮你查询教学数据、发现运行问题并生成改进建议。',
    examples: [
      '哪些课程平均成绩低于 70 分？',
      '分析本学期课程运行情况',
      '查看平台服务运行状态',
    ],
    abilities: [
      { icon: 'el-icon-data-analysis', text: '教学运行驾驶舱' },
      { icon: 'el-icon-chat-dot-round', text: 'AI 数据问答' },
      { icon: 'el-icon-pie-chart', text: '教学运行分析' },
      { icon: 'el-icon-monitor', text: '平台服务监控' },
    ],
  },
};

const asRoleList = value => (Array.isArray(value) ? value : []);
const roleCode = role =>
  typeof role === 'string' ? role : role?.code || role?.name || '';

export const createCampusRoleState = () => ({
  actualRole: null,
  previewRole: null,
  effectiveRole: null,
  roleStatus: CAMPUS_ROLE_STATUS.UNCONFIGURED,
  canPreview: false,
});

export const resolveCampusRoleState = ({
  roles = [],
  previewRole = null,
  isAdmin = false,
  isSystem = false,
} = {}) => {
  const matchedRoles = [
    ...new Set(
      asRoleList(roles)
        .map(roleCode)
        .filter(role => CAMPUS_ROLE_KEYS.includes(role)),
    ),
  ];
  const roleStatus =
    matchedRoles.length === 1
      ? CAMPUS_ROLE_STATUS.RESOLVED
      : matchedRoles.length > 1
        ? CAMPUS_ROLE_STATUS.CONFLICT
        : CAMPUS_ROLE_STATUS.UNCONFIGURED;
  const actualRole =
    roleStatus === CAMPUS_ROLE_STATUS.RESOLVED ? matchedRoles[0] : null;
  const canPreview = Boolean(isAdmin || isSystem);
  const safePreviewRole =
    canPreview && CAMPUS_ROLE_KEYS.includes(previewRole) ? previewRole : null;

  return {
    actualRole,
    previewRole: safePreviewRole,
    effectiveRole: safePreviewRole || actualRole,
    roleStatus,
    canPreview,
  };
};

export const getCampusRoleProfile = role => CAMPUS_ROLES[role] || null;

export const canAccessCampusRoles = (
  allowedRoles,
  campusRole = createCampusRoleState(),
) => {
  const roles = asRoleList(allowedRoles);
  return !roles.length || roles.includes(campusRole.actualRole);
};

export const campusDisplayName = (userName, role) => {
  if (!role) return userName || '校园用户';
  const fallback = role.key === 'academic_admin' ? '教务管理员' : role.label;
  const name = userName || fallback;
  if (!role.salutation || name.endsWith(role.salutation)) return name;
  return `${name}${role.salutation}`;
};
