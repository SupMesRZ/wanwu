<template>
  <div class="workflow-center">
    <header class="page-header">
      <div>
        <span>AI WORKFLOW CENTER</span>
        <h1>智能流程中心</h1>
        <p>让 AI 从“回答问题”走向“理解需求、调用系统、完成校园业务”。</p>
      </div>
      <div class="header-stats">
        <div>
          <strong>16</strong>
          <small>流程数量</small>
        </div>
        <div>
          <strong>1,286</strong>
          <small>今日执行</small>
        </div>
        <div>
          <strong>98.7%</strong>
          <small>成功率</small>
        </div>
      </div>
    </header>

    <section class="story-strip">
      <div>
        <i class="el-icon-chat-dot-round"></i>
        <strong>一句话发起</strong>
        <small>自然语言描述业务需求</small>
      </div>
      <i class="el-icon-right"></i>
      <div>
        <i class="el-icon-cpu"></i>
        <strong>智能体理解</strong>
        <small>识别身份、意图和参数</small>
      </div>
      <i class="el-icon-right"></i>
      <div>
        <i class="el-icon-share"></i>
        <strong>工作流执行</strong>
        <small>编排规则、知识与 MCP</small>
      </div>
      <i class="el-icon-right"></i>
      <div>
        <i class="el-icon-circle-check"></i>
        <strong>业务完成</strong>
        <small>返回结果并持续跟踪</small>
      </div>
    </section>

    <section class="flow-grid">
      <article
        v-for="flow in flows"
        :key="flow.key"
        class="flow-card"
        :class="flow.color"
      >
        <div class="flow-head">
          <span><i :class="flow.icon"></i></span>
          <div>
            <small>{{ flow.audience }}</small>
            <h2>{{ flow.name }}</h2>
          </div>
          <el-tag
            size="mini"
            :type="flow.live ? 'success' : 'warning'"
            effect="plain"
          >
            {{ flow.live ? '运行中' : '演示流程' }}
          </el-tag>
        </div>
        <p>{{ flow.description }}</p>
        <div class="flow-nodes">
          <template v-for="(step, index) in flow.steps">
            <div :key="step.title" class="flow-node">
              <span>{{ index + 1 }}</span>
              <div>
                <strong>{{ step.title }}</strong>
                <small>{{ step.description }}</small>
              </div>
            </div>
            <i
              v-if="index < flow.steps.length - 1"
              :key="`${flow.key}-${index}`"
              class="el-icon-bottom node-arrow"
            ></i>
          </template>
        </div>
        <div class="flow-footer">
          <span>
            <i class="el-icon-connection"></i>
            {{ flow.connections }}
          </span>
          <el-button type="primary" size="small" @click="runDemo(flow)">
            运行演示
          </el-button>
        </div>
      </article>
    </section>

    <section class="panel run-panel">
      <div class="panel-heading">
        <div>
          <h2>流程运行记录</h2>
          <p>清晰呈现智能体如何完成一次校园业务。</p>
        </div>
        <el-tag size="mini" effect="plain">最近 5 次</el-tag>
      </div>
      <el-table :data="records" size="small" style="width: 100%">
        <el-table-column prop="id" label="执行编号" min-width="145" />
        <el-table-column prop="flow" label="流程名称" min-width="145" />
        <el-table-column prop="user" label="发起角色" width="100" />
        <el-table-column prop="systems" label="调用能力" min-width="210" />
        <el-table-column prop="duration" label="耗时" width="90" />
        <el-table-column label="状态" width="90">
          <template>
            <el-tag size="mini" type="success" effect="plain">成功</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="time" label="执行时间" width="150" />
      </el-table>
    </section>

    <el-dialog
      :visible.sync="runVisible"
      width="680px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div slot="title" class="dialog-title">
        <span>{{ activeFlow.name }}</span>
        <el-tag size="mini" type="warning" effect="plain">模拟执行</el-tag>
      </div>
      <div class="demo-query">
        <strong>自然语言需求</strong>
        <p>{{ activeFlow.query }}</p>
      </div>
      <div class="execution-list">
        <div
          v-for="(step, index) in activeFlow.steps || []"
          :key="step.title"
          :class="{ done: index < completedSteps }"
        >
          <span>
            <i
              :class="index < completedSteps ? 'el-icon-check' : 'el-icon-more'"
            ></i>
          </span>
          <div>
            <strong>{{ step.title }}</strong>
            <p>{{ step.description }}</p>
          </div>
          <small>{{ index < completedSteps ? '已完成' : '等待中' }}</small>
        </div>
      </div>
      <el-alert
        v-if="completedSteps === (activeFlow.steps || []).length"
        :title="activeFlow.result"
        type="success"
        :closable="false"
        show-icon
      />
      <span slot="footer">
        <el-button size="small" @click="runVisible = false">关闭</el-button>
        <el-button
          type="primary"
          size="small"
          :loading="isRunning"
          @click="startExecution"
        >
          {{ completedSteps ? '重新运行' : '开始执行' }}
        </el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
const leaveSteps = [
  { title: 'AI 解析需求', description: '提取请假时间、原因与课程信息' },
  { title: '验证学生身份', description: '读取当前登录用户及所属班级' },
  { title: '检查请假规则', description: '调用校园制度知识库校验申请' },
  { title: '调用请假 MCP', description: '向学工系统提交结构化申请' },
  { title: '启动审批流程', description: '按规则发送辅导员与教师审批' },
  { title: '返回办理结果', description: '生成申请编号并持续跟踪状态' },
];
const lessonSteps = [
  { title: '分析课程目标', description: '理解章节、对象与教学要求' },
  { title: '调用课程知识库', description: '检索教材、教案和教学规范' },
  { title: '生成教学方案', description: '编排教学重点、活动与时间' },
  { title: '生成课堂 PPT', description: '自动形成结构化演示课件' },
  { title: '教师审核确认', description: '保留人工调整和发布环节' },
  { title: '保存课程资源', description: '写入课程资源库并记录版本' },
];
const reportSteps = [
  { title: '识别分析范围', description: '确认学院、时间与指标口径' },
  { title: '校验管理权限', description: '按组织权限限制数据范围' },
  { title: '调用数据 MCP', description: '聚合课程、成绩和教学数据' },
  { title: '执行统计分析', description: '计算趋势、异常和核心指标' },
  { title: '生成管理报告', description: '形成图表、结论与改进建议' },
  { title: '归档并通知', description: '保存报告并发送相关管理人员' },
];

export default {
  name: 'CampusWorkflow',
  data() {
    return {
      runVisible: false,
      isRunning: false,
      completedSteps: 0,
      activeFlow: {},
      timer: null,
      flows: [
        {
          key: 'leave',
          name: '学生智能请假流程',
          audience: '学生服务',
          icon: 'el-icon-edit-outline',
          color: 'blue',
          live: false,
          description:
            '从一句话请假需求到规则校验、系统提交和审批跟踪，完整展示 AI 办理校园事务。',
          connections: '身份服务 · 制度知识库 · 学工 MCP',
          query: '我今天下午身体不舒服，帮我请假半天。',
          result:
            '请假申请已提交，申请编号 LEAVE-20260822-0186，当前等待辅导员审批。',
          steps: leaveSteps,
        },
        {
          key: 'lesson',
          name: 'AI 智能备课流程',
          audience: '教师服务',
          icon: 'el-icon-notebook-2',
          color: 'cyan',
          live: true,
          description:
            '结合课程目标与知识库生成教学方案、课堂课件和练习，并保留教师审核环节。',
          connections: '课程知识库 · 内容生成 · 资源服务',
          query: '为《人工智能导论》的机器学习章节生成 90 分钟教学方案和 PPT。',
          result: '教学方案与 32 页课堂 PPT 已生成，已保存至待审核资源。',
          steps: lessonSteps,
        },
        {
          key: 'report',
          name: '教学运行分析流程',
          audience: '教务管理',
          icon: 'el-icon-data-analysis',
          color: 'violet',
          live: false,
          description:
            '聚合跨系统教学数据，自动完成指标统计、异常分析和管理报告生成。',
          connections: '权限中心 · 数据分析 MCP · 报告服务',
          query: '统计计算机学院本周课程运行情况，生成一份分析报告。',
          result:
            '本周教学运行报告已生成，共分析 86 门课程，发现 3 项需要关注的异常。',
          steps: reportSteps,
        },
      ],
      records: [
        {
          id: 'WF-20260822-1286',
          flow: '学生智能请假',
          user: '学生',
          systems: '身份服务 / 学工 MCP',
          duration: '2.4s',
          time: '今天 14:26',
        },
        {
          id: 'WF-20260822-1285',
          flow: 'AI 智能备课',
          user: '教师',
          systems: '课程知识库 / 资源服务',
          duration: '18.6s',
          time: '今天 14:22',
        },
        {
          id: 'WF-20260822-1284',
          flow: '教学运行分析',
          user: '教务',
          systems: '数据分析 MCP / 报告服务',
          duration: '12.3s',
          time: '今天 14:18',
        },
        {
          id: 'WF-20260822-1283',
          flow: '学生成绩查询',
          user: '学生',
          systems: '教务系统 MCP',
          duration: '1.8s',
          time: '今天 14:15',
        },
        {
          id: 'WF-20260822-1282',
          flow: '课程反馈分析',
          user: '教师',
          systems: '教学评价 / 模型服务',
          duration: '6.7s',
          time: '今天 14:11',
        },
      ],
    };
  },
  beforeDestroy() {
    clearInterval(this.timer);
  },
  methods: {
    runDemo(flow) {
      clearInterval(this.timer);
      this.activeFlow = flow;
      this.completedSteps = 0;
      this.isRunning = false;
      this.runVisible = true;
    },
    startExecution() {
      clearInterval(this.timer);
      this.completedSteps = 0;
      this.isRunning = true;
      this.timer = setInterval(() => {
        this.completedSteps += 1;
        if (this.completedSteps >= this.activeFlow.steps.length) {
          clearInterval(this.timer);
          this.isRunning = false;
        }
      }, 420);
    },
  },
};
</script>

<style lang="scss" scoped>
.workflow-center {
  min-height: 100%;
  padding: 24px;
  color: #1c3150;
  background: #f4f7fb;
}
.page-header,
.story-strip,
.flow-grid,
.panel {
  width: 100%;
  max-width: 1360px;
  margin: 0 auto 16px;
  box-sizing: border-box;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 28px 32px;
  color: #fff;
  border-radius: 15px;
  background: linear-gradient(120deg, #113264, #1767c6 66%, #318cd4);
}
.page-header > div:first-child > span {
  color: rgba(255, 255, 255, 0.65);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.5px;
}
.page-header h1 {
  margin: 6px 0;
  font-size: 27px;
}
.page-header p {
  margin: 0;
  color: rgba(255, 255, 255, 0.78);
}
.header-stats {
  display: flex;
}
.header-stats div {
  min-width: 95px;
  padding: 8px 15px;
  text-align: center;
  border-left: 1px solid rgba(255, 255, 255, 0.16);
}
.header-stats strong,
.header-stats small {
  display: block;
}
.header-stats strong {
  font-size: 20px;
}
.header-stats small {
  margin-top: 3px;
  color: rgba(255, 255, 255, 0.66);
  font-size: 9px;
}
.story-strip {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 15px 20px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}
.story-strip > div {
  display: grid;
  grid-template-columns: 34px auto;
  align-items: center;
  min-width: 0;
}
.story-strip > div > i {
  grid-row: 1/3;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  margin-right: 8px;
  color: #1768c9;
  border-radius: 8px;
  background: #eaf3ff;
}
.story-strip strong {
  font-size: 11px;
}
.story-strip small {
  color: #919baa;
  font-size: 9px;
}
.story-strip > i {
  margin: 0 25px;
  color: #aebccc;
}
.flow-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.flow-card {
  padding: 19px;
  border: 1px solid #dfe7f1;
  border-top: 3px solid #3279d2;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 5px 17px rgba(35, 70, 116, 0.045);
}
.flow-card.cyan {
  border-top-color: #2195a3;
}
.flow-card.violet {
  border-top-color: #7758c5;
}
.flow-head {
  display: flex;
  align-items: center;
}
.flow-head > span {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin-right: 9px;
  color: #1768c9;
  border-radius: 10px;
  background: #eaf3ff;
  font-size: 17px;
}
.cyan .flow-head > span {
  color: #118898;
  background: #e9f8fa;
}
.violet .flow-head > span {
  color: #7254bd;
  background: #f2effb;
}
.flow-head > div {
  min-width: 0;
  flex: 1;
}
.flow-head small {
  color: #8a96a6;
  font-size: 9px;
}
.flow-head h2 {
  margin: 3px 0 0;
  font-size: 14px;
}
.flow-card > p {
  min-height: 42px;
  margin: 13px 0;
  color: #69798d;
  font-size: 11px;
  line-height: 1.7;
}
.flow-nodes {
  padding: 12px;
  border-radius: 9px;
  background: #f6f8fb;
}
.flow-node {
  display: grid;
  grid-template-columns: 24px 1fr;
  align-items: center;
  gap: 8px;
}
.flow-node > span {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  color: #1768c9;
  border-radius: 50%;
  background: #e5effd;
  font-size: 9px;
  font-weight: 700;
}
.flow-node strong,
.flow-node small {
  display: block;
}
.flow-node strong {
  font-size: 10px;
}
.flow-node small {
  margin-top: 2px;
  color: #929cab;
  font-size: 8px;
}
.node-arrow {
  display: block;
  margin: 3px 0 3px 7px;
  color: #aebccb;
  font-size: 10px;
}
.flow-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 13px;
}
.flow-footer > span {
  overflow: hidden;
  color: #728298;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.flow-footer > span i {
  margin-right: 5px;
  color: #1768c9;
}
.panel {
  padding: 21px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}
.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 15px;
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
.dialog-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 700;
}
.demo-query {
  padding: 12px 14px;
  border-radius: 8px;
  background: #f2f6fb;
}
.demo-query strong {
  color: #45607f;
  font-size: 10px;
}
.demo-query p {
  margin: 5px 0 0;
  color: #263f5f;
}
.execution-list {
  margin: 16px 0;
}
.execution-list > div {
  display: grid;
  grid-template-columns: 30px 1fr auto;
  align-items: center;
  gap: 10px;
  min-height: 51px;
  opacity: 0.48;
}
.execution-list > div.done {
  opacity: 1;
}
.execution-list > div > span {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  color: #8d9caf;
  border-radius: 50%;
  background: #edf1f5;
}
.execution-list > div.done > span {
  color: #fff;
  background: #27a477;
}
.execution-list strong {
  font-size: 11px;
}
.execution-list p {
  margin: 3px 0 0;
  color: #8b96a6;
  font-size: 9px;
}
.execution-list small {
  color: #8996a7;
  font-size: 9px;
}
.execution-list .done > small {
  color: #258b69;
}
@media (max-width: 1120px) {
  .flow-grid {
    grid-template-columns: 1fr;
  }
  .flow-card > p {
    min-height: 0;
  }
  .story-strip > i {
    margin: 0 12px;
  }
}
@media (max-width: 720px) {
  .workflow-center {
    padding: 12px;
  }
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }
  .header-stats {
    width: 100%;
  }
  .header-stats div {
    flex: 1;
  }
  .story-strip {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
  .story-strip > i {
    margin: 0 0 0 12px;
    transform: rotate(90deg);
  }
  .run-panel {
    overflow: auto;
  }
}
</style>
