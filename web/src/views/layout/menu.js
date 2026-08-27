import { PERMS } from '@/router/permission';
import { i18n } from '@/lang';
import { basePath, vegaOrigin } from '@/utils/config';

/**
 * 左侧菜单按产品使用对象分为三层：
 * 1. 智慧校园：学生、教师和管理人员使用的校园服务入口；
 * 2. 智能服务：智能体与工作流的开发、发布入口；
 * 3. 平台能力：知识、MCP、数据和模型等技术底座。
 *
 * index 为唯一标识，children 下的 index 使用“父级-子级”格式。
 */
export const menuList = [
  {
    name: i18n.t('menu.smartCampus'),
    index: 'smartCampus',
    children: [
      {
        name: i18n.t('menu.smartAssistant'),
        roleNames: {
          student: i18n.t('menu.studentAssistant'),
          teacher: i18n.t('menu.teacherAssistant'),
          academic_admin: i18n.t('menu.academicAdminAssistant'),
        },
        index: 'smartCampus-smartAssistant',
        icon: 'menu_robot',
        path: '/smartAssistant',
      },
      {
        name: i18n.t('menu.studentAcademic'),
        index: 'smartCampus-studentAcademic',
        icon: 'menu_rag',
        path: '/campus/student/courses',
        roles: ['student'],
      },
      {
        name: i18n.t('menu.studentAffairs'),
        index: 'smartCampus-studentAffairs',
        icon: 'menu_develop',
        path: '/campus/student/affairs',
        roles: ['student'],
      },
      {
        name: i18n.t('menu.studentLearning'),
        index: 'smartCampus-studentLearning',
        icon: 'menu_statistics',
        path: '/campus/student/analysis',
        roles: ['student'],
      },
      {
        name: i18n.t('menu.teacherTeaching'),
        index: 'smartCampus-teacherTeaching',
        icon: 'menu_agent',
        path: '/campus/teacher/teaching',
        roles: ['teacher'],
      },
      {
        name: i18n.t('menu.teacherPreparation'),
        index: 'smartCampus-teacherPreparation',
        icon: 'menu_knowledge',
        path: '/campus/teacher/resources',
        roles: ['teacher'],
      },
      {
        name: i18n.t('menu.teacherAnalysis'),
        index: 'smartCampus-teacherAnalysis',
        icon: 'menu_statistics',
        path: '/campus/teacher/analysis',
        roles: ['teacher'],
      },
      {
        name: i18n.t('menu.academicDashboard'),
        index: 'smartCampus-adminDashboard',
        icon: 'menu_statistics',
        path: '/adminDashboard',
        perm: [PERMS.ADMIN_CENTER, PERMS.OBSERVATION_STATISTIC],
        roles: ['academic_admin'],
      },
      {
        name: i18n.t('menu.academicServiceManagement'),
        index: 'smartCampus-businessCenter',
        icon: 'menu_appSquare',
        path: '/businessCenter',
        roles: ['academic_admin'],
      },
    ],
  },
  {
    name: i18n.t('menu.intelligentService'),
    index: 'intelligentService',
    perm: [PERMS.AGENT, PERMS.WORKFLOW],
    children: [
      {
        name: i18n.t('menu.agentDevelopment'),
        index: 'intelligentService-agent',
        icon: 'menu_agent',
        path: '/appSpace/agent',
        perm: PERMS.AGENT,
      },
      {
        name: i18n.t('menu.intelligentAgentManagement'),
        index: 'intelligentService-agentManagement',
        icon: 'menu_agent',
        path: '/agentCenter',
        perm: PERMS.AGENT,
      },
      {
        name: i18n.t('menu.workflowOrchestration'),
        index: 'intelligentService-workflow',
        icon: 'menu_workflow',
        path: '/appSpace/workflow',
        perm: PERMS.WORKFLOW,
      },
    ],
  },
  {
    name: i18n.t('menu.platformCapability'),
    index: 'platformCapability',
    perm: [
      PERMS.KNOWLEDGE,
      PERMS.MCP_SERVICE,
      PERMS.ONTOLOGY_DATA_SOURCE,
      PERMS.MODEL_MANAGE,
    ],
    children: [
      {
        name: i18n.t('menu.campusKnowledge'),
        index: 'platformCapability-knowledge',
        icon: 'menu_knowledge',
        path: '/knowledge',
        perm: PERMS.KNOWLEDGE,
      },
      {
        name: i18n.t('menu.capabilityConnection'),
        index: 'platformCapability-mcp',
        icon: 'menu_mcpService',
        path: '/mcpService',
        perm: PERMS.MCP_SERVICE,
      },
      {
        name: i18n.t('menu.dataResource'),
        index: 'platformCapability-data',
        icon: 'menu_link',
        perm: PERMS.ONTOLOGY_DATA_SOURCE,
        redirect: () => {
          location.href = vegaOrigin + basePath + '/vega/data-connect';
        },
      },
      {
        name: i18n.t('menu.modelAccess'),
        index: 'platformCapability-model',
        icon: 'menu_model',
        path: '/modelAccess',
        perm: PERMS.MODEL_MANAGE,
      },
    ],
  },
];
