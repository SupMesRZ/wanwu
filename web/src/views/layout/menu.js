import { PERMS } from '@/router/permission';
import { i18n } from '@/lang';
import { basePath, vegaOrigin } from '@/utils/config';

/**
 *  index: 为唯一标识，children 下定义的 index 标准为： 父级 index-子级定义的唯一标识
 */
export const menuList = [
  {
    name: i18n.t('menu.generalAgent'),
    index: 'generalAgent',
    perm: [PERMS.WGA],
    children: [
      {
        name: i18n.t('menu.wanwuAgent'),
        index: 'generalAgent-wanwuAgent',
        icon: 'menu_robot',
        path: '/generalAgent',
        perm: PERMS.WGA_WANWU_BOT,
      },
      {
        name: i18n.t('menu.aiAssistant'),
        index: 'generalAgent-aiAssistant',
        icon: 'menu_openClaw',
        path: '/aiAssistant',
        perm: PERMS.WGA_OPENCLAW,
      },
    ],
  },
  {
    name: i18n.t('menu.campusGovernance'),
    index: 'campusGovernance',
    perm: [PERMS.PUBLIC_OPINION],
    children: [
      {
        name: i18n.t('menu.publicOpinion'),
        index: 'publicOpinion',
        icon: 'menu_statistics',
        path: '/publicOpinion',
        perm: PERMS.PUBLIC_OPINION,
      },
    ],
  },
  {
    name: i18n.t('menu.ontologyAgent'),
    index: 'ontologyAgent',
    perm: [PERMS.ONTOLOGY],
    children: [
      {
        name: i18n.t('menu.digitalHuman'),
        index: 'ontologyAgent-digital-human',
        icon: 'menu_team',
        perm: PERMS.ONTOLOGY_DATA_SOURCE,
        redirect: () => {
          location.href = vegaOrigin + basePath + '/vega/digital-human';
        },
      },
      {
        name: i18n.t('menu.ontology'),
        index: 'ontologyAgent-ontology',
        icon: 'menu_net',
        perm: PERMS.ONTOLOGY_KNOWLEDGE_NETWORK,
        redirect: () => {
          location.href = vegaOrigin + basePath + '/vega/ontology';
        },
      },
      {
        name: i18n.t('menu.dataConnect'),
        index: 'ontologyAgent-data-connect',
        icon: 'menu_link',
        perm: PERMS.ONTOLOGY_DATA_SOURCE,
        redirect: () => {
          location.href = vegaOrigin + basePath + '/vega/data-connect';
        },
      },
    ],
  },
  {
    name: i18n.t('menu.modelService'),
    index: 'modelService',
    perm: [PERMS.MODEL_SERVICE],
    children: [
      {
        name: i18n.t('menu.modelAccess'),
        index: 'modelService-modelAccess',
        icon: 'menu_model',
        path: '/modelAccess',
        perm: PERMS.MODEL_MANAGE,
      },
    ],
  },
  {
    name: i18n.t('menu.resource'),
    index: 'resource',
    perm: [PERMS.RESOURCE],
    children: [
      {
        name: i18n.t('menu.knowledge'),
        index: 'resource-knowledge',
        icon: 'menu_knowledge',
        path: '/knowledge',
        perm: PERMS.KNOWLEDGE,
      },
      {
        name: i18n.t('menu.mcpService'),
        index: 'resource-mcpService',
        icon: 'menu_mcpService',
        path: '/mcpService',
        perm: PERMS.MCP_SERVICE,
      },
      {
        name: i18n.t('menu.tool'),
        index: 'resource-tool',
        icon: 'menu_tools',
        path: '/tool',
        perm: PERMS.TOOL,
      },
      {
        name: i18n.t('menu.prompt'),
        index: 'resource-prompt',
        icon: 'menu_prompt',
        path: '/prompt',
        perm: PERMS.PROMPT,
      },
      {
        name: 'Skills',
        index: 'resource-skill',
        icon: 'menu_skills',
        path: '/skill',
        perm: PERMS.SKILL,
      },
      {
        name: i18n.t('menu.safetyGuard'),
        index: 'resource-safetyGuard',
        icon: 'menu_safety',
        path: '/safety',
        perm: PERMS.SAFETY,
      },
    ],
  },
  {
    name: i18n.t('menu.app.index'),
    index: 'appSpace',
    perm: [PERMS.APP_SPACE],
    children: [
      {
        name: i18n.t('menu.app.rag'),
        index: 'appSpace-rag',
        icon: 'menu_rag',
        path: '/appSpace/rag',
        perm: PERMS.RAG,
      },
      {
        name: i18n.t('menu.app.workflow'),
        index: 'appSpace-workflow',
        icon: 'menu_workflow',
        path: '/appSpace/workflow',
        perm: PERMS.WORKFLOW,
      },
      {
        name: i18n.t('menu.app.agent'),
        index: 'appSpace-agent',
        icon: 'menu_agent',
        path: '/appSpace/agent',
        perm: PERMS.AGENT,
      },
    ],
  },
  {
    name: i18n.t('menu.square'),
    index: 'square',
    perm: [PERMS.SQUARE],
    icon: 'square',
    children: [
      {
        name: i18n.t('menu.explore'),
        index: 'square-explore',
        icon: 'menu_appSquare',
        path: '/explore',
        perm: PERMS.EXPLORE,
      },
      {
        name: i18n.t('menu.mcp'),
        index: 'square-mcpManage',
        icon: 'menu_mcpSquare',
        path: '/mcp',
        perm: PERMS.MCP,
      },
      {
        name: i18n.t('menu.templateSquare'),
        index: 'square-templateSquare',
        icon: 'menu_templateSquare',
        path: '/templateSquare',
        perm: PERMS.TEMPLATE,
      },
      {
        name: i18n.t('menu.skillSquare'),
        index: 'square-skillSquare',
        icon: 'menu_skillSquare',
        path: '/skillSquare',
        perm: PERMS.SKILL_SQUARE,
      },
    ],
  },
  {
    name: i18n.t('menu.appObservation'),
    index: 'appObservation',
    perm: [PERMS.APP_OBSERVATION],
    children: [
      {
        name: i18n.t('menu.statisticsDashboard'),
        index: 'appObservation-statisticsDashboard',
        icon: 'menu_statistics',
        path: '/statisticsDashboard',
        perm: PERMS.OBSERVATION_STATISTIC,
      },
    ],
  },
  {
    name: i18n.t('menu.apiKey'),
    index: 'apiKey',
    icon: 'apiKey',
    perm: [PERMS.API_KEY],
    children: [
      {
        name: i18n.t('menu.apiKey'),
        index: 'apiKey-openApiKey',
        icon: 'menu_apiKey',
        path: '/openApiKey',
        perm: PERMS.API_KEY_MANAGE,
      },
    ],
  },
];
