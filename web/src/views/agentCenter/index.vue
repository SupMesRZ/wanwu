<template>
  <div class="agent-center">
    <header class="page-header">
      <div>
        <span>INTELLIGENT SERVICE MANAGEMENT</span>
        <h1>智能体管理中心</h1>
        <p>统一管理面向学生、教师和教务人员的角色智能体及其服务能力。</p>
      </div>
      <el-button
        v-if="canCreateAgent"
        type="primary"
        icon="el-icon-plus"
        @click="$router.push('/appSpace/agent')"
      >
        新建智能体
      </el-button>
    </header>

    <section class="summary-grid">
      <article v-for="item in summaries" :key="item.label">
        <span :class="item.color"><i :class="item.icon"></i></span>
        <div>
          <p>{{ item.label }}</p>
          <strong>{{ item.value }}</strong>
          <small>{{ item.note }}</small>
        </div>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h2>角色智能体</h2>
          <p>同一平台根据登录身份自动加载对应服务与权限。</p>
        </div>
        <div class="online">
          <i></i>
          3 个智能体运行正常
        </div>
      </div>
      <div class="agent-grid">
        <article
          v-for="agent in agents"
          :key="agent.key"
          :class="['agent-card', agent.color]"
        >
          <div class="agent-head">
            <span class="agent-avatar"><i :class="agent.icon"></i></span>
            <div>
              <small>{{ agent.audience }}</small>
              <h3>{{ agent.name }}</h3>
            </div>
            <el-tag
              size="mini"
              :type="agentStatus(agent).type"
              :title="agentStatus(agent).tip || ''"
              effect="plain"
            >
              {{ agentStatus(agent).text }}
            </el-tag>
          </div>
          <p>{{ agent.description }}</p>
          <div class="ability-list">
            <span v-for="item in agent.abilities" :key="item">
              <i class="el-icon-check"></i>
              {{ item }}
            </span>
          </div>
          <div class="agent-data">
            <div>
              <strong>{{ agent.mcp }}</strong>
              <small>MCP 服务</small>
            </div>
            <div>
              <strong>{{ agent.knowledge }}</strong>
              <small>知识库</small>
            </div>
            <div>
              <strong>{{ agent.workflow }}</strong>
              <small>工作流</small>
            </div>
            <div>
              <strong>{{ agent.calls }}</strong>
              <small>今日调用</small>
            </div>
          </div>
          <div class="agent-actions">
            <template v-if="canManageAgent">
              <el-button
                type="primary"
                size="small"
                :disabled="!binding(agent).assistantId"
                @click="editAssistant(agent)"
              >
                编辑配置
              </el-button>
              <el-button
                size="small"
                :disabled="!binding(agent).assistantId"
                @click="publishAssistant(agent)"
              >
                发布
              </el-button>
              <el-button
                size="small"
                :disabled="!binding(agent).published"
                @click="experience(agent)"
              >
                预览
              </el-button>
            </template>
            <el-button size="small" @click="showDetail(agent)">
              能力详情
            </el-button>
          </div>
        </article>
      </div>
    </section>

    <section class="bottom-grid">
      <article class="panel capability-panel">
        <div class="panel-heading">
          <div>
            <h2>平台能力关系</h2>
            <p>角色智能体共享底座，并按权限调用校园业务。</p>
          </div>
        </div>
        <div class="capability-flow">
          <div class="flow-role">
            <span v-for="agent in agents" :key="agent.key">
              <i :class="agent.icon"></i>
              {{ agent.shortName }}
            </span>
          </div>
          <i class="el-icon-bottom"></i>
          <div class="flow-core">
            <strong>河小智智能体编排引擎</strong>
            <small>身份识别 · 意图理解 · 权限校验 · 能力路由</small>
          </div>
          <i class="el-icon-bottom"></i>
          <div class="flow-foundation">
            <span>MCP 服务</span>
            <span>校园知识库</span>
            <span>智能工作流</span>
            <span>模型服务</span>
          </div>
        </div>
      </article>
      <article class="panel activity-panel">
        <div class="panel-heading">
          <div>
            <h2>最近运行动态</h2>
            <p>智能服务调用摘要</p>
          </div>
          <el-tag size="mini" effect="plain">演示数据</el-tag>
        </div>
        <div
          v-for="activity in activities"
          :key="activity.time + activity.text"
          class="activity-item"
        >
          <span :class="activity.color"><i :class="activity.icon"></i></span>
          <div>
            <strong>{{ activity.text }}</strong>
            <p>{{ activity.agent }} · {{ activity.time }}</p>
          </div>
          <el-tag size="mini" type="success" effect="plain">成功</el-tag>
        </div>
      </article>
    </section>

    <el-dialog
      title="智能体能力详情"
      :visible.sync="detailVisible"
      width="620px"
      append-to-body
    >
      <div v-if="activeAgent.name" class="detail-content">
        <h3>{{ activeAgent.name }}</h3>
        <p>{{ activeAgent.description }}</p>
        <div
          v-for="(value, key) in activeAgent.capabilityDetail"
          :key="key"
          class="detail-row"
        >
          <strong>{{ key }}</strong>
          <span>{{ value }}</span>
        </div>
        <el-alert
          title="当前校园业务数据为比赛模拟数据，底层 Agent、知识库与工作流能力沿用平台真实能力。"
          type="warning"
          :closable="false"
          show-icon
        />
      </div>
      <span slot="footer">
        <el-button type="primary" size="small" @click="detailVisible = false">
          关闭
        </el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { checkPerm, PERMS } from '@/router/permission';
import { getStudentAssistantBinding } from '@/api/campusStudent';
import { getCampusRoleAssistantBinding } from '@/api/campusBusiness';

export default {
  name: 'AgentCenter',
  data() {
    return {
      detailVisible: false,
      activeAgent: {},
      bindings: {
        student: {},
        teacher: {},
        academic_admin: {},
      },
      summaries: [
        {
          label: '运行中智能体',
          value: '3',
          note: '覆盖三类校园角色',
          icon: 'el-icon-cpu',
          color: 'blue',
        },
        {
          label: 'Campus MCP',
          value: '3',
          note: '学生、教师、教务真实注册',
          icon: 'el-icon-connection',
          color: 'cyan',
        },
        {
          label: '已接入知识库',
          value: '12',
          note: '校园制度与课程资源',
          icon: 'el-icon-collection',
          color: 'violet',
        },
        {
          label: '工作流数量',
          value: '16',
          note: '今日执行 1,286 次',
          icon: 'el-icon-share',
          color: 'orange',
        },
        {
          label: '今日总调用',
          value: '3,568',
          note: '成功率 98.5%',
          icon: 'el-icon-data-line',
          color: 'green',
        },
      ],
      agents: [
        {
          key: 'student',
          shortName: '学生助手',
          name: '河小智·学生助手',
          audience: '本科生 / 研究生',
          icon: 'el-icon-reading',
          color: 'blue',
          description:
            '覆盖学习查询、校园事务和个性化学习辅助，让学生一句话完成校园服务。',
          abilities: ['课表与成绩查询', '请假与校园事务', '学习计划与错题分析'],
          mcp: 1,
          knowledge: 3,
          workflow: 6,
          calls: '2,126',
          capabilityDetail: {
            'Agent 类型': '角色服务智能体',
            'MCP 连接': '教务、学工、图书馆、宿舍',
            知识库: '培养方案、校园制度、课程知识',
            核心工作流: '请假申请、学习计划、事务咨询',
          },
        },
        {
          key: 'teacher',
          shortName: '教师助手',
          name: '河小智·教师助手',
          audience: '专任教师 / 辅导教师',
          icon: 'el-icon-notebook-2',
          color: 'cyan',
          description: '提供智能备课、教学资料生成、课程管理和学情反馈分析。',
          abilities: ['教学设计与大纲', 'PPT 与练习生成', '课程评价与学情分析'],
          mcp: 1,
          knowledge: 6,
          workflow: 5,
          calls: '986',
          capabilityDetail: {
            'Agent 类型': '教学辅助智能体',
            'MCP 连接': '教学系统、课程系统',
            知识库: '课程资源、教材教案、教学规范',
            核心工作流: '教学设计、课件生成、学情报告',
          },
        },
        {
          key: 'academic_admin',
          shortName: '教务助手',
          name: '河小智·教务助手',
          audience: '学院管理员 / 教务人员',
          icon: 'el-icon-data-analysis',
          color: 'violet',
          description: '聚合教学运行数据，自动生成统计报告并提供管理辅助决策。',
          abilities: ['学院教学分析', '课程运行分析', '统计报告与辅助决策'],
          mcp: 1,
          knowledge: 3,
          workflow: 5,
          calls: '456',
          capabilityDetail: {
            'Agent 类型': '管理决策智能体',
            'MCP 连接': '数据分析、综合管理',
            知识库: '管理制度、指标口径、历史报告',
            核心工作流: '数据分析、报告生成、异常提醒',
          },
        },
      ],
      activities: [
        {
          text: '完成学生课表查询',
          agent: '学生助手',
          time: '刚刚',
          icon: 'el-icon-date',
          color: 'blue',
        },
        {
          text: '生成《人工智能导论》教学方案',
          agent: '教师助手',
          time: '3 分钟前',
          icon: 'el-icon-document',
          color: 'cyan',
        },
        {
          text: '生成学院课程运行周报',
          agent: '教务助手',
          time: '8 分钟前',
          icon: 'el-icon-data-analysis',
          color: 'violet',
        },
        {
          text: '提交学生请假审批流程',
          agent: '学生助手',
          time: '12 分钟前',
          icon: 'el-icon-edit-outline',
          color: 'orange',
        },
      ],
    };
  },
  computed: {
    canCreateAgent() {
      return checkPerm(PERMS.AGENT);
    },
    canManageAgent() {
      const { isAdmin, isSystem } = this.$store.state.user.permission || {};
      return isAdmin || isSystem;
    },
  },
  created() {
    if (this.canCreateAgent) this.loadBindings();
  },
  methods: {
    async loadBindings() {
      const results = await Promise.all([
        getStudentAssistantBinding(),
        getCampusRoleAssistantBinding('teacher'),
        getCampusRoleAssistantBinding('academic_admin'),
      ]);
      ['student', 'teacher', 'academic_admin'].forEach((role, index) => {
        const res = results[index];
        if (res.code === 0 && res.data)
          this.$set(this.bindings, role, res.data);
      });
    },
    binding(agent) {
      return this.bindings[agent.key] || {};
    },
    agentStatus(agent) {
      const binding = this.binding(agent);
      if (binding.ready) return { type: 'success', text: '已发布' };
      if (binding.published)
        return {
          type: 'warning',
          text: '配置待修正',
          tip: '点击“编辑配置”，确认对应 Campus MCP 的必需工具已全部启用，然后重新发布。',
        };
      return {
        type: 'info',
        text: binding.assistantId ? '待发布' : '未绑定',
      };
    },
    editAssistant(agent) {
      this.$router.push({
        path: '/agent/test',
        query: { id: this.binding(agent).assistantId },
      });
    },
    publishAssistant(agent) {
      const binding = this.binding(agent);
      this.$router.push({
        path: '/agent/publishSet',
        query: {
          appId: binding.assistantId,
          appType: 'agent',
          name: binding.name || agent.name,
        },
      });
    },
    experience(agent) {
      this.$router.push({
        path: '/smartAssistant',
        query: { previewRole: agent.key },
      });
    },
    showDetail(agent) {
      this.activeAgent = agent;
      this.detailVisible = true;
    },
  },
};
</script>

<style lang="scss" scoped>
.agent-center {
  min-height: 100%;
  padding: 24px;
  color: #1d3150;
  background: #f4f7fb;
}
.page-header,
.summary-grid,
.panel,
.bottom-grid {
  width: 100%;
  max-width: 1360px;
  margin: 0 auto 16px;
  box-sizing: border-box;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  color: #fff;
  border-radius: 15px;
  background: linear-gradient(120deg, #102f60, #1766c5 70%, #318bd3);
}
.page-header span {
  color: rgba(255, 255, 255, 0.65);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.4px;
}
.page-header h1 {
  margin: 6px 0;
  font-size: 27px;
}
.page-header p {
  margin: 0;
  color: rgba(255, 255, 255, 0.78);
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}
.summary-grid article {
  display: flex;
  align-items: center;
  min-width: 0;
  padding: 15px;
  border: 1px solid #e3e9f1;
  border-radius: 10px;
  background: #fff;
}
.summary-grid article > span {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  margin-right: 10px;
  color: #1768c9;
  border-radius: 10px;
  background: #ebf3ff;
}
.summary-grid span.cyan {
  color: #128898;
  background: #e9f8fa;
}
.summary-grid span.violet {
  color: #7254bd;
  background: #f2effb;
}
.summary-grid span.orange {
  color: #bd7219;
  background: #fff4e5;
}
.summary-grid span.green {
  color: #18875f;
  background: #eaf8f2;
}
.summary-grid p,
.summary-grid small {
  display: block;
  margin: 0;
  color: #8a96a6;
  font-size: 10px;
}
.summary-grid strong {
  display: block;
  margin: 3px 0;
  color: #263e5e;
  font-size: 20px;
}
.panel {
  padding: 22px;
  border: 1px solid #e2e8f0;
  border-radius: 13px;
  background: #fff;
  box-shadow: 0 5px 18px rgba(35, 70, 116, 0.045);
}
.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 17px;
}
.panel-heading h2 {
  margin: 0 0 4px;
  font-size: 17px;
}
.panel-heading p {
  margin: 0;
  color: #8b96a5;
  font-size: 11px;
}
.online {
  color: #39806a;
  font-size: 11px;
}
.online i {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 5px;
  border-radius: 50%;
  background: #2cab7f;
  box-shadow: 0 0 0 4px #e8f7f2;
}
.agent-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.agent-card {
  position: relative;
  padding: 18px;
  border: 1px solid #dfe7f1;
  border-top: 3px solid #397bd3;
  border-radius: 11px;
  background: #fcfdff;
}
.agent-card.cyan {
  border-top-color: #2195a3;
}
.agent-card.violet {
  border-top-color: #7758c5;
}
.agent-head {
  display: flex;
  align-items: center;
}
.agent-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  margin-right: 10px;
  color: #1768c9;
  border-radius: 11px;
  background: #eaf3ff;
  font-size: 18px;
}
.cyan .agent-avatar {
  color: #118796;
  background: #e9f8fa;
}
.violet .agent-avatar {
  color: #7254bd;
  background: #f2effb;
}
.agent-head > div {
  min-width: 0;
  flex: 1;
}
.agent-head small {
  color: #8b96a6;
  font-size: 9px;
}
.agent-head h3 {
  margin: 3px 0 0;
  font-size: 15px;
}
.agent-card > p {
  min-height: 42px;
  margin: 14px 0;
  color: #69798e;
  font-size: 11px;
  line-height: 1.7;
}
.ability-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 12px;
  border-radius: 8px;
  background: #f5f8fc;
}
.ability-list span {
  color: #53677f;
  font-size: 10px;
}
.ability-list i {
  margin-right: 6px;
  color: #23a176;
}
.agent-data {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin: 14px 0;
  text-align: center;
}
.agent-data div + div {
  border-left: 1px solid #e7ebf1;
}
.agent-data strong,
.agent-data small {
  display: block;
}
.agent-data strong {
  font-size: 15px;
}
.agent-data small {
  margin-top: 3px;
  color: #929dab;
  font-size: 9px;
}
.agent-actions {
  display: flex;
  gap: 7px;
}
.agent-actions .el-button {
  flex: 1;
  margin: 0;
}
.bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 0.85fr);
  gap: 14px;
}
.bottom-grid > .panel {
  margin: 0;
}
.capability-flow {
  text-align: center;
}
.flow-role,
.flow-foundation {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}
.flow-role span,
.flow-foundation span {
  padding: 8px 11px;
  color: #46617f;
  border: 1px solid #dfe7f1;
  border-radius: 7px;
  background: #f7f9fc;
  font-size: 10px;
}
.flow-role i {
  margin-right: 5px;
  color: #1768c9;
}
.capability-flow > i {
  display: block;
  margin: 7px;
  color: #8ca3c2;
}
.flow-core {
  display: flex;
  flex-direction: column;
  padding: 12px;
  color: #fff;
  border-radius: 9px;
  background: linear-gradient(90deg, #184d94, #2879d8);
}
.flow-core small {
  margin-top: 4px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 9px;
}
.activity-item {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  padding: 10px 0;
  border-bottom: 1px solid #edf0f4;
}
.activity-item:last-child {
  border: 0;
}
.activity-item > span {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: #1768c9;
  border-radius: 8px;
  background: #ebf3ff;
}
.activity-item strong {
  font-size: 11px;
}
.activity-item p {
  margin: 3px 0 0;
  color: #949eac;
  font-size: 9px;
}
.detail-content > p {
  color: #738198;
  line-height: 1.7;
}
.detail-row {
  display: grid;
  grid-template-columns: 100px 1fr;
  padding: 10px 0;
  border-bottom: 1px solid #edf0f4;
  font-size: 12px;
}
.detail-row strong {
  color: #405671;
}
.detail-row span {
  color: #758398;
}
.detail-content .el-alert {
  margin-top: 16px;
}
@media (max-width: 1150px) {
  .summary-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  .agent-grid {
    grid-template-columns: 1fr;
  }
  .bottom-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 650px) {
  .agent-center {
    padding: 12px;
  }
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }
  .summary-grid {
    grid-template-columns: 1fr;
  }
  .agent-data {
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
  }
  .agent-data div + div {
    border: 0;
  }
}
</style>
