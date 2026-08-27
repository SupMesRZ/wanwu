<template>
  <div class="business-center-page">
    <header class="page-hero">
      <div>
        <span class="eyebrow">CAMPUS CAPABILITY ECOSYSTEM</span>
        <h1>校园业务中心</h1>
        <p>集中展示河小智可理解、可调用、可编排的校园业务服务。</p>
      </div>
      <div class="hero-summary">
        <div>
          <strong>{{ services.length }}</strong>
          <span>业务系统</span>
        </div>
        <div>
          <strong>{{ capabilityCount }}</strong>
          <span>AI服务能力</span>
        </div>
        <div>
          <strong>1</strong>
          <span>真实能力底座</span>
        </div>
      </div>
    </header>

    <section class="call-chain">
      <div
        v-for="(step, index) in callChain"
        :key="step.title"
        class="chain-step"
      >
        <span class="chain-icon"><i :class="step.icon"></i></span>
        <div>
          <strong>{{ step.title }}</strong>
          <small>{{ step.description }}</small>
        </div>
        <i
          v-if="index < callChain.length - 1"
          class="el-icon-right chain-arrow"
        ></i>
      </div>
    </section>

    <section class="service-panel">
      <div class="audience-tabs">
        <button
          v-for="audience in audiences"
          :key="audience.key"
          type="button"
          :class="{ active: activeAudience === audience.key }"
          @click="activeAudience = audience.key"
        >
          <span><i :class="audience.icon"></i></span>
          <div>
            <strong>{{ audience.name }}</strong>
            <small>{{ audience.description }}</small>
          </div>
          <em>{{ audience.count }} 个系统</em>
        </button>
      </div>
      <div class="panel-heading">
        <div>
          <h2>已规划校园服务</h2>
          <p>“已接入”表示连接现有平台能力；“模拟接入”用于比赛流程展示。</p>
        </div>
        <div class="panel-tools">
          <el-radio-group v-model="activeFilter" size="small">
            <el-radio-button label="all">全部</el-radio-button>
            <el-radio-button label="connected">已接入</el-radio-button>
            <el-radio-button label="demo">模拟接入</el-radio-button>
          </el-radio-group>
          <el-input
            v-model.trim="keyword"
            size="small"
            prefix-icon="el-icon-search"
            placeholder="搜索业务系统或能力"
            clearable
          />
        </div>
      </div>

      <div class="service-grid">
        <article
          v-for="service in filteredServices"
          :key="service.key"
          class="system-card"
        >
          <div class="card-head">
            <div class="system-title">
              <span class="system-icon" :class="service.color">
                <i :class="service.icon"></i>
              </span>
              <div>
                <h3>{{ service.name }}</h3>
                <small>{{ service.provider }}</small>
              </div>
            </div>
            <el-tag
              size="mini"
              :type="service.connected ? 'success' : 'warning'"
              effect="plain"
            >
              {{ service.connected ? '已接入' : '模拟接入' }}
            </el-tag>
          </div>

          <p class="system-description">{{ service.description }}</p>
          <div class="capability-title">AI可调用能力</div>
          <div class="capability-list">
            <span v-for="capability in service.capabilities" :key="capability">
              <i class="el-icon-check"></i>
              {{ capability }}
            </span>
          </div>
          <div class="connection-info">
            <span>连接方式</span>
            <strong>
              <i class="el-icon-connection"></i>
              {{ service.connection }}
            </strong>
          </div>
          <div class="card-actions">
            <el-button type="primary" size="mini" @click="experience(service)">
              立即体验
            </el-button>
            <el-button size="mini" @click="showInterface(service)">
              查看接口
            </el-button>
            <el-button type="text" size="mini" @click="showFlow(service)">
              调用流程
            </el-button>
          </div>
        </article>
      </div>

      <el-empty
        v-if="!filteredServices.length"
        description="未找到匹配的校园服务"
      />
    </section>

    <section class="technical-entry">
      <div>
        <span class="eyebrow">TECHNICAL FOUNDATION</span>
        <h2>从展示能力进入真实技术底座</h2>
        <p>
          校园业务卡片面向服务使用者；MCP配置和数据连接仍在平台能力中统一管理。
        </p>
      </div>
      <div class="technical-actions">
        <el-button
          v-if="canManageMcp"
          size="small"
          @click="$router.push('/mcpService')"
        >
          进入能力连接中心
        </el-button>
        <el-button
          v-if="canManageKnowledge"
          type="primary"
          size="small"
          @click="$router.push('/knowledge')"
        >
          进入校园知识中心
        </el-button>
      </div>
    </section>

    <el-dialog
      :title="dialogMode === 'interface' ? '服务接口示意' : 'AI调用流程'"
      :visible.sync="dialogVisible"
      width="650px"
      append-to-body
    >
      <template v-if="dialogMode === 'interface'">
        <div class="dialog-badge">
          <el-tag size="mini" type="warning" effect="plain">演示数据</el-tag>
          <span>{{ activeService.name }}</span>
        </div>
        <div class="interface-row">
          <span class="method">POST</span>
          <code>{{ activeService.endpoint }}</code>
        </div>
        <div class="code-title">请求示例</div>
        <pre>{{ activeService.requestExample }}</pre>
        <div class="code-title">返回示例</div>
        <pre>{{ activeService.responseExample }}</pre>
      </template>
      <template v-else>
        <div class="flow-dialog">
          <div
            v-for="(step, index) in flowSteps"
            :key="step.title"
            class="flow-item"
          >
            <span>{{ index + 1 }}</span>
            <div>
              <strong>{{ step.title }}</strong>
              <p>{{ step.description }}</p>
            </div>
          </div>
        </div>
        <div class="flow-note">
          当前为比赛展示流程，接入校园 MCP 服务后可替换为真实请求日志。
        </div>
      </template>
      <span slot="footer">
        <el-button type="primary" size="small" @click="dialogVisible = false">
          关闭
        </el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { checkPerm, PERMS } from '@/router/permission';

const buildExample = (endpoint, result) => ({
  endpoint,
  requestExample: JSON.stringify(
    { userId: '20250001', orgId: 'hebu', query: '自然语言业务请求' },
    null,
    2,
  ),
  responseExample: JSON.stringify(
    { code: 0, message: 'success', data: result, demo: true },
    null,
    2,
  ),
});

export default {
  name: 'BusinessCenter',
  data() {
    return {
      activeFilter: 'all',
      activeAudience: this.$route.query.audience || 'all',
      keyword: '',
      dialogVisible: false,
      dialogMode: 'interface',
      activeService: {},
      audiences: [
        {
          key: 'all',
          name: '全部服务',
          description: '校园业务统一能力目录',
          icon: 'el-icon-menu',
          count: 8,
        },
        {
          key: 'student',
          name: '学生服务',
          description: '教务、学工、图书与生活',
          icon: 'el-icon-reading',
          count: 4,
        },
        {
          key: 'teacher',
          name: '教师服务',
          description: '教学与课程资源',
          icon: 'el-icon-notebook-2',
          count: 2,
        },
        {
          key: 'academic_admin',
          name: '管理服务',
          description: '数据分析与综合管理',
          icon: 'el-icon-data-analysis',
          count: 2,
        },
      ],
      callChain: [
        {
          title: '自然语言需求',
          description: '学生或教师提出问题',
          icon: 'el-icon-chat-dot-round',
        },
        {
          title: '智能体理解',
          description: '识别身份、意图和参数',
          icon: 'el-icon-cpu',
        },
        {
          title: '校园能力调用',
          description: '选择MCP或知识服务',
          icon: 'el-icon-connection',
        },
        {
          title: '结果与办理',
          description: '返回信息或启动工作流',
          icon: 'el-icon-circle-check',
        },
      ],
      services: [
        {
          key: 'academic',
          audience: 'student',
          name: '教务服务中心',
          provider: '河北大学教务系统',
          description: '连接课程、成绩、考试和培养方案等教务数据与业务。',
          icon: 'el-icon-school',
          color: 'blue',
          connected: false,
          connection: 'MCP Service（模拟）',
          capabilities: ['查询课程', '查询成绩', '查询考试', '查询培养方案'],
          experienceKey: 'schedule',
          ...buildExample('/mcp/campus/academic/query', {
            courses: 2,
            source: 'academic-demo',
          }),
        },
        {
          key: 'student',
          audience: 'student',
          name: '学工服务中心',
          provider: '河北大学学生工作系统',
          description: '连接请假、奖助、学籍和宿舍等学生事务服务。',
          icon: 'el-icon-user',
          color: 'violet',
          connected: false,
          connection: 'MCP + Workflow（模拟）',
          capabilities: ['请假申请', '奖助查询', '学籍信息', '宿舍服务'],
          experienceKey: 'leave',
          ...buildExample('/mcp/campus/student-affairs/leave', {
            workflowId: 'leave-demo-001',
          }),
        },
        {
          key: 'library',
          audience: 'student',
          name: '图书馆与知识服务',
          provider: '校园知识库 / 图书馆服务',
          description:
            '当前已连接平台RAG能力，可用于制度、通知和校园知识问答。',
          icon: 'el-icon-reading',
          color: 'green',
          connected: true,
          connection: 'RAG Knowledge Service',
          capabilities: ['校园知识问答', '馆藏咨询', '借阅规则', '开放时间'],
          experienceKey: 'library',
          ...buildExample('/knowledge/campus/query', {
            answer: '由校园知识库检索生成',
          }),
        },
        {
          key: 'logistics',
          audience: 'student',
          name: '校园后勤服务',
          provider: '河北大学后勤服务系统',
          description: '连接报修、会议室、场地和生活服务等校园后勤能力。',
          icon: 'el-icon-house',
          color: 'orange',
          connected: false,
          connection: 'MCP Service（模拟）',
          capabilities: ['报修申请', '会议室预约', '场地预约', '服务进度查询'],
          experienceKey: 'repair',
          ...buildExample('/mcp/campus/logistics/repair', {
            ticketId: 'REPAIR-DEMO-1024',
          }),
        },
        {
          key: 'teaching',
          audience: 'teacher',
          name: '教师教学服务',
          provider: '河北大学教师教学系统',
          description: '面向教师提供授课安排、课程信息和教学分析服务。',
          icon: 'el-icon-notebook-2',
          color: 'cyan',
          connected: false,
          connection: 'MCP Service（模拟）',
          capabilities: ['授课安排', '课程信息', '学生反馈', '教学分析'],
          experienceKey: 'teaching',
          ...buildExample('/mcp/campus/teaching/schedule', {
            classes: 1,
            source: 'teaching-demo',
          }),
        },
        {
          key: 'notice',
          audience: 'teacher',
          name: '校园通知服务',
          provider: '校内通知与政策信息',
          description: '聚合学校通知、规章制度和办事指南，提供统一检索入口。',
          icon: 'el-icon-bell',
          color: 'red',
          connected: true,
          connection: 'RAG Knowledge Service',
          capabilities: ['通知查询', '政策解读', '办事指南', '制度问答'],
          experienceKey: 'library',
          ...buildExample('/knowledge/campus/notices', {
            answer: '由校园知识库检索生成',
          }),
        },
        {
          key: 'dataAnalysis',
          audience: 'academic_admin',
          name: '教学数据分析服务',
          provider: '河北大学数据分析平台',
          description: '聚合课程、成绩、教师与学生数据，支持跨业务统计分析。',
          icon: 'el-icon-data-analysis',
          color: 'violet',
          connected: false,
          connection: 'Data Analysis MCP（模拟）',
          capabilities: [
            '学院教学分析',
            '课程运行分析',
            '成绩分析',
            '教师教学分析',
          ],
          experienceKey: 'collegeAnalysis',
          ...buildExample('/mcp/campus/analytics/query', {
            reportId: 'REPORT-DEMO-20260822',
          }),
        },
        {
          key: 'management',
          audience: 'academic_admin',
          name: '综合管理服务',
          provider: '河北大学综合管理平台',
          description: '连接统计报告、服务管理和任务协同能力，辅助教务决策。',
          icon: 'el-icon-office-building',
          color: 'blue',
          connected: false,
          connection: 'Management MCP + Workflow（模拟）',
          capabilities: ['统计报告', '服务管理', '任务协同', '辅助决策'],
          experienceKey: 'report',
          ...buildExample('/mcp/campus/management/report', {
            taskId: 'TASK-DEMO-0186',
          }),
        },
      ],
    };
  },
  computed: {
    capabilityCount() {
      return this.services.reduce(
        (count, item) => count + item.capabilities.length,
        0,
      );
    },
    filteredServices() {
      const keyword = this.keyword.toLowerCase();
      return this.services.filter(item => {
        const matchesAudience =
          this.activeAudience === 'all' ||
          item.audience === this.activeAudience;
        const matchesStatus =
          this.activeFilter === 'all' ||
          (this.activeFilter === 'connected' && item.connected) ||
          (this.activeFilter === 'demo' && !item.connected);
        const text = [
          item.name,
          item.provider,
          item.description,
          ...item.capabilities,
        ]
          .join(' ')
          .toLowerCase();
        return (
          matchesAudience &&
          matchesStatus &&
          (!keyword || text.includes(keyword))
        );
      });
    },
    canManageMcp() {
      return checkPerm(PERMS.MCP_SERVICE);
    },
    canManageKnowledge() {
      return checkPerm(PERMS.KNOWLEDGE);
    },
    flowSteps() {
      return [
        {
          title: '理解用户意图',
          description: `识别为“${this.activeService.name || '校园服务'}”业务请求。`,
        },
        {
          title: '校验身份与参数',
          description: '根据登录账号、组织和业务规则补齐调用参数。',
        },
        {
          title: '选择校园能力',
          description: `通过${this.activeService.connection || 'MCP Service'}发起调用。`,
        },
        {
          title: '组织服务结果',
          description: '将结构化业务数据转换为自然语言并返回给用户。',
        },
      ];
    },
  },
  methods: {
    experience(service) {
      this.$router.push({
        path: '/smartAssistant',
        query: {
          service: service.experienceKey,
          previewRole: service.audience,
        },
      });
    },
    showInterface(service) {
      this.activeService = service;
      this.dialogMode = 'interface';
      this.dialogVisible = true;
    },
    showFlow(service) {
      this.activeService = service;
      this.dialogMode = 'flow';
      this.dialogVisible = true;
    },
  },
};
</script>

<style lang="scss" scoped>
.business-center-page {
  min-height: 100%;
  padding: 22px;
  color: #192b48;
  background: #f4f7fb;
}

.page-hero,
.call-chain,
.service-panel,
.technical-entry {
  max-width: 1320px;
  margin: 0 auto 18px;
}

.page-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 30px 34px;
  color: #fff;
  border-radius: 18px;
  background: linear-gradient(118deg, #163a71, #1768c9 64%, #2b91c8);
  box-shadow: 0 16px 34px rgba(22, 71, 137, 0.19);

  h1 {
    margin: 6px 0 7px;
    font-size: 28px;
  }
  p {
    margin: 0;
    color: rgba(255, 255, 255, 0.8);
  }
}

.eyebrow {
  color: rgba(255, 255, 255, 0.67);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.35px;
}

.hero-summary {
  display: flex;
  gap: 8px;

  div {
    display: flex;
    flex-direction: column;
    min-width: 104px;
    padding: 13px 16px;
    border: 1px solid rgba(255, 255, 255, 0.16);
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.09);
  }
  strong {
    font-size: 24px;
  }
  span {
    margin-top: 3px;
    color: rgba(255, 255, 255, 0.68);
    font-size: 11px;
  }
}

.call-chain {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 18px 22px;
  border: 1px solid #e7edf5;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 6px 20px rgba(37, 69, 112, 0.05);
}

.chain-step {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 0;
  padding: 0 20px;

  &:first-child {
    padding-left: 0;
  }
  &:last-child {
    padding-right: 0;
  }
  > div {
    display: flex;
    min-width: 0;
    flex-direction: column;
  }
  strong {
    margin-bottom: 3px;
    font-size: 13px;
  }
  small {
    overflow: hidden;
    color: #8793a5;
    font-size: 10px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.chain-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  margin-right: 9px;
  color: #1768c9;
  border-radius: 10px;
  background: #edf5ff;
}

.chain-arrow {
  position: absolute;
  right: 0;
  color: #c2ccd8;
}

.service-panel {
  padding: 25px 28px 30px;
  border: 1px solid #e7edf5;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 7px 24px rgba(37, 69, 112, 0.05);
}

.audience-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 22px;

  button {
    display: grid;
    grid-template-columns: 38px minmax(0, 1fr) auto;
    align-items: center;
    gap: 9px;
    padding: 12px;
    color: inherit;
    text-align: left;
    cursor: pointer;
    border: 1px solid #e3e9f1;
    border-radius: 10px;
    background: #f9fbfd;
    transition: 0.2s ease;

    &:hover,
    &.active {
      color: #1559c5;
      border-color: #9fc3ed;
      background: #f1f7ff;
    }

    > span {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      color: #1768c9;
      border-radius: 9px;
      background: #e8f2ff;
    }

    strong,
    small {
      display: block;
    }

    strong {
      margin-bottom: 3px;
      font-size: 12px;
    }

    small {
      overflow: hidden;
      color: #8b97a7;
      font-size: 9px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    em {
      color: #8493a7;
      font-size: 9px;
      font-style: normal;
      white-space: nowrap;
    }
  }
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 22px;

  h2 {
    margin: 0 0 5px;
    font-size: 20px;
  }
  p {
    margin: 0;
    color: #7b899c;
    font-size: 12px;
  }
}

.panel-tools {
  display: flex;
  gap: 10px;

  .el-input {
    width: 220px;
  }
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.system-card {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 18px;
  border: 1px solid #e4eaf2;
  border-radius: 13px;
  transition: 0.2s ease;

  &:hover {
    border-color: #9fc4ef;
    box-shadow: 0 9px 22px rgba(30, 84, 151, 0.1);
    transform: translateY(-2px);
  }
}

.card-head,
.system-title,
.connection-info,
.card-actions {
  display: flex;
  align-items: center;
}

.card-head {
  justify-content: space-between;
  gap: 10px;
}
.system-title {
  min-width: 0;
}

.system-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  margin-right: 10px;
  color: #1768c9;
  font-size: 19px;
  border-radius: 11px;
  background: #edf5ff;

  &.green {
    color: #18875f;
    background: #eaf8f2;
  }
  &.orange {
    color: #cd7816;
    background: #fff4e5;
  }
  &.violet {
    color: #7656c7;
    background: #f2effb;
  }
  &.cyan {
    color: #168c9b;
    background: #eaf8fa;
  }
  &.red {
    color: #c94c57;
    background: #fceff0;
  }
}

.system-title {
  h3 {
    margin: 0 0 3px;
    font-size: 15px;
  }
  small {
    display: block;
    overflow: hidden;
    color: #8995a6;
    font-size: 10px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.system-description {
  min-height: 39px;
  margin: 15px 0;
  color: #68778c;
  font-size: 12px;
  line-height: 1.65;
}

.capability-title {
  margin-bottom: 8px;
  color: #405675;
  font-size: 11px;
  font-weight: 700;
}
.capability-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px;
  min-height: 46px;

  span {
    color: #5d6d83;
    font-size: 11px;
  }
  i {
    margin-right: 4px;
    color: #20a477;
  }
}

.connection-info {
  justify-content: space-between;
  margin: 15px 0 13px;
  padding: 9px 10px;
  color: #8490a1;
  font-size: 10px;
  border-radius: 8px;
  background: #f6f8fb;

  strong {
    color: #42628c;
    font-size: 10px;
  }
  i {
    margin-right: 5px;
  }
}

.card-actions {
  margin-top: auto;
  .el-button + .el-button {
    margin-left: 7px;
  }
  .el-button--text {
    margin-left: auto !important;
  }
}

.technical-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 28px;
  border-radius: 15px;
  background: #eaf3ff;

  .eyebrow {
    color: #1768c9;
  }
  h2 {
    margin: 4px 0 5px;
    color: #204978;
    font-size: 18px;
  }
  p {
    margin: 0;
    color: #6680a0;
    font-size: 12px;
  }
}

.technical-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}
.dialog-badge {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 15px;
  font-weight: 700;
}
.interface-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 7px;
  background: #f3f6fa;
}
.method {
  padding: 3px 7px;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  border-radius: 4px;
  background: #21a374;
}
.code-title {
  margin: 16px 0 7px;
  color: #57677e;
  font-size: 12px;
  font-weight: 700;
}
pre {
  max-height: 180px;
  margin: 0;
  padding: 13px;
  overflow: auto;
  color: #dbe7f7;
  font-size: 11px;
  line-height: 1.6;
  border-radius: 8px;
  background: #17253a;
}

.flow-dialog {
  position: relative;
}
.flow-item {
  display: flex;
  gap: 12px;
  padding: 0 0 18px;

  > span {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: 28px;
    height: 28px;
    color: #1768c9;
    font-size: 12px;
    font-weight: 700;
    border-radius: 50%;
    background: #eaf3ff;
  }
  strong {
    color: #324965;
  }
  p {
    margin: 4px 0 0;
    color: #7b899b;
    font-size: 12px;
  }
}
.flow-note {
  padding: 10px 12px;
  color: #8b682f;
  font-size: 11px;
  border-radius: 7px;
  background: #fff7e9;
}

@media (max-width: 1120px) {
  .audience-tabs {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .service-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .panel-heading {
    flex-direction: column;
  }
}

@media (max-width: 820px) {
  .page-hero,
  .technical-entry {
    align-items: flex-start;
    flex-direction: column;
  }
  .call-chain {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 18px;
  }
  .chain-arrow {
    display: none;
  }
  .panel-tools {
    width: 100%;
    flex-wrap: wrap;
  }
}

@media (max-width: 620px) {
  .business-center-page {
    padding: 12px;
  }
  .hero-summary {
    width: 100%;
    flex-wrap: wrap;
  }
  .service-grid,
  .call-chain,
  .audience-tabs {
    grid-template-columns: 1fr;
  }
  .panel-tools .el-input {
    width: 100%;
  }
}
</style>
