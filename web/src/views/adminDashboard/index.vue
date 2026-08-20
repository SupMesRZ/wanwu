<template>
  <div class="dashboard-page">
    <header class="dashboard-header">
      <div>
        <div class="title-line">
          <h1>智慧校园管理中心</h1>
          <el-tag size="mini" type="warning" effect="plain">演示数据</el-tag>
        </div>
        <p>面向学校管理人员展示AI校园服务运行情况与辅助决策信息。</p>
      </div>
      <div class="header-actions">
        <span>
          <i class="el-icon-time"></i>
          更新于 {{ updatedAt }}
        </span>
        <el-button
          size="small"
          icon="el-icon-refresh"
          @click="refreshDashboard"
        >
          刷新数据
        </el-button>
      </div>
    </header>

    <el-alert
      class="demo-notice"
      title="当前统计、排行与分析结果均为比赛演示数据，不代表学校真实业务运行情况。"
      type="warning"
      show-icon
      :closable="false"
    />

    <section class="metric-grid">
      <article
        v-for="metric in metrics"
        :key="metric.label"
        class="metric-card"
      >
        <span class="metric-icon" :class="metric.color">
          <i :class="metric.icon"></i>
        </span>
        <div class="metric-main">
          <span>{{ metric.label }}</span>
          <strong>
            {{ metric.value }}
            <small>{{ metric.unit }}</small>
          </strong>
          <p :class="metric.trend >= 0 ? 'up' : 'down'">
            <i
              :class="metric.trend >= 0 ? 'el-icon-top' : 'el-icon-bottom'"
            ></i>
            {{ Math.abs(metric.trend) }}% 较昨日
          </p>
        </div>
      </article>
    </section>

    <section class="dashboard-grid dashboard-grid--top">
      <article class="panel ranking-panel">
        <div class="panel-title">
          <div>
            <h2>校园服务访问排行</h2>
            <p>今日各业务服务调用次数</p>
          </div>
          <el-tag size="mini" effect="plain">TOP 6</el-tag>
        </div>
        <div class="ranking-list">
          <div
            v-for="(item, index) in rankings"
            :key="item.name"
            class="ranking-item"
          >
            <span class="rank" :class="{ top: index < 3 }">
              {{ index + 1 }}
            </span>
            <span class="rank-name">{{ item.name }}</span>
            <div class="rank-bar">
              <span :style="{ width: item.percent + '%' }"></span>
            </div>
            <strong>{{ item.value }}次</strong>
          </div>
        </div>
      </article>

      <article class="panel trend-panel">
        <div class="panel-title">
          <div>
            <h2>近7日服务趋势</h2>
            <p>AI服务调用量与成功率</p>
          </div>
          <span class="success-label">
            <i></i>
            成功率 98.5%
          </span>
        </div>
        <div class="trend-chart">
          <div v-for="item in weeklyTrend" :key="item.day" class="trend-column">
            <div class="bar-wrap">
              <span class="bar-value">{{ item.value }}</span>
              <div class="bar" :style="{ height: item.percent + '%' }"></div>
            </div>
            <span>{{ item.day }}</span>
          </div>
        </div>
      </article>
    </section>

    <section class="dashboard-grid dashboard-grid--middle">
      <article class="panel user-panel">
        <div class="panel-title">
          <div>
            <h2>用户服务分析</h2>
            <p>不同校园角色的使用情况</p>
          </div>
        </div>
        <div class="user-segments">
          <div
            v-for="segment in userSegments"
            :key="segment.name"
            class="user-segment"
          >
            <span class="segment-icon" :class="segment.color">
              <i :class="segment.icon"></i>
            </span>
            <div class="segment-summary">
              <span>{{ segment.name }}</span>
              <strong>
                {{ segment.visits }}
                <small>次访问</small>
              </strong>
            </div>
            <div class="segment-topics">
              <span>热门需求</span>
              <p v-for="topic in segment.topics" :key="topic">{{ topic }}</p>
            </div>
          </div>
        </div>
      </article>

      <article class="panel status-panel">
        <div class="panel-title">
          <div>
            <h2>校园能力运行状态</h2>
            <p>当前服务连接与演示状态</p>
          </div>
          <span class="normal-status">
            <i></i>
            演示环境正常
          </span>
        </div>
        <div class="system-list">
          <div v-for="system in systemStatus" :key="system.name">
            <span class="system-dot" :class="system.status"></span>
            <div>
              <strong>{{ system.name }}</strong>
              <small>{{ system.mode }}</small>
            </div>
            <span class="latency">{{ system.latency }}</span>
            <el-tag
              size="mini"
              :type="system.status === 'live' ? 'success' : 'warning'"
              effect="plain"
            >
              {{ system.status === 'live' ? '已接入' : '模拟' }}
            </el-tag>
          </div>
        </div>
      </article>
    </section>

    <section class="decision-panel">
      <div class="decision-side">
        <span class="ai-mark"><i class="el-icon-cpu"></i></span>
        <span class="eyebrow">AI ASSISTED DECISION</span>
        <h2>河小智辅助决策</h2>
        <p>结合近期校园服务数据，辅助发现师生关注点和服务优化方向。</p>
        <div class="suggested-questions">
          <button
            v-for="item in suggestedQuestions"
            :key="item"
            type="button"
            @click="analysisQuestion = item"
          >
            {{ item }}
          </button>
        </div>
      </div>
      <div class="decision-workspace">
        <div class="analysis-input">
          <el-input
            v-model.trim="analysisQuestion"
            placeholder="例如：分析近期学生关注的问题"
            @keyup.enter.native="runAnalysis"
          />
          <el-button
            type="primary"
            :loading="analysisLoading"
            @click="runAnalysis"
          >
            开始分析
          </el-button>
        </div>
        <div v-if="analysisResult" class="analysis-result">
          <div class="result-head">
            <i class="el-icon-data-analysis"></i>
            河小智分析结果
            <el-tag size="mini" type="warning" effect="plain">模拟分析</el-tag>
          </div>
          <div
            v-for="(item, index) in analysisResult"
            :key="item.title"
            class="insight-item"
          >
            <span>{{ index + 1 }}</span>
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.description }}</p>
              <small>建议：{{ item.suggestion }}</small>
            </div>
          </div>
        </div>
        <div v-else class="analysis-empty">
          <i class="el-icon-data-line"></i>
          <p>输入管理问题，生成校园服务分析建议</p>
        </div>
      </div>
    </section>

    <section class="management-links">
      <div>
        <h2>深入管理</h2>
        <p>进入平台现有管理能力查看真实配置与运行信息。</p>
      </div>
      <div>
        <el-button
          v-if="canViewObservation"
          size="small"
          @click="$router.push('/statisticsDashboard')"
        >
          应用观测
        </el-button>
        <el-button
          v-if="canViewOpinion"
          size="small"
          @click="$router.push('/publicOpinion')"
        >
          舆情研判
        </el-button>
        <el-button
          v-if="canViewAdmin"
          type="primary"
          size="small"
          @click="$router.push('/permission')"
        >
          管理员中心
        </el-button>
      </div>
    </section>
  </div>
</template>

<script>
import { checkPerm, PERMS } from '@/router/permission';

export default {
  name: 'AdminDashboard',
  data() {
    return {
      updatedAt: this.formatTime(new Date()),
      analysisQuestion: '分析近期学生关注的问题',
      analysisLoading: false,
      analysisResult: null,
      metrics: [
        {
          label: '今日AI服务次数',
          value: '3,568',
          unit: '',
          trend: 12.6,
          icon: 'el-icon-chat-dot-round',
          color: 'blue',
        },
        {
          label: '服务成功率',
          value: '98.5',
          unit: '%',
          trend: 0.8,
          icon: 'el-icon-circle-check',
          color: 'green',
        },
        {
          label: '当前运行智能体',
          value: '12',
          unit: '个',
          trend: 9.1,
          icon: 'el-icon-cpu',
          color: 'violet',
        },
        {
          label: '已规划业务系统',
          value: '8',
          unit: '个',
          trend: 14.3,
          icon: 'el-icon-connection',
          color: 'orange',
        },
      ],
      rankings: [
        { name: '查询成绩', value: 1200, percent: 100 },
        { name: '查询课表', value: 980, percent: 82 },
        { name: '请假服务', value: 560, percent: 47 },
        { name: '图书查询', value: 420, percent: 35 },
        { name: '考试安排', value: 288, percent: 24 },
        { name: '校园报修', value: 120, percent: 10 },
      ],
      weeklyTrend: [
        { day: '周一', value: 2180, percent: 61 },
        { day: '周二', value: 2540, percent: 71 },
        { day: '周三', value: 2310, percent: 65 },
        { day: '周四', value: 2980, percent: 83 },
        { day: '周五', value: 3568, percent: 100 },
        { day: '周六', value: 1820, percent: 51 },
        { day: '周日', value: 1430, percent: 40 },
      ],
      userSegments: [
        {
          name: '学生用户',
          visits: '2,846',
          icon: 'el-icon-school',
          color: 'blue',
          topics: ['考试与成绩查询', '课程安排', '请假办理'],
        },
        {
          name: '教师用户',
          visits: '516',
          icon: 'el-icon-user',
          color: 'green',
          topics: ['教学安排', '课程资料', '学生反馈'],
        },
        {
          name: '管理人员',
          visits: '206',
          icon: 'el-icon-office-building',
          color: 'violet',
          topics: ['服务统计', '热点分析', '运行状态'],
        },
      ],
      systemStatus: [
        {
          name: '校园知识库',
          mode: 'RAG Knowledge',
          latency: '320ms',
          status: 'live',
        },
        {
          name: '教务服务',
          mode: 'MCP Service',
          latency: '--',
          status: 'demo',
        },
        {
          name: '学工服务',
          mode: 'MCP + Workflow',
          latency: '--',
          status: 'demo',
        },
        {
          name: '图书馆服务',
          mode: 'MCP Service',
          latency: '--',
          status: 'demo',
        },
        {
          name: '后勤服务',
          mode: 'MCP Service',
          latency: '--',
          status: 'demo',
        },
      ],
      suggestedQuestions: [
        '分析近期学生关注的问题',
        '哪些服务需要优先优化？',
        '总结本周AI服务运行情况',
      ],
    };
  },
  computed: {
    canViewObservation() {
      return checkPerm(PERMS.OBSERVATION_STATISTIC);
    },
    canViewOpinion() {
      return checkPerm([PERMS.PUBLIC_OPINION, PERMS.OBSERVATION_STATISTIC]);
    },
    canViewAdmin() {
      return checkPerm(PERMS.ADMIN_CENTER);
    },
  },
  methods: {
    formatTime(date) {
      const pad = value => String(value).padStart(2, '0');
      return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
    },
    refreshDashboard() {
      this.updatedAt = this.formatTime(new Date());
      this.$message.success('演示数据已刷新');
    },
    runAnalysis() {
      if (!this.analysisQuestion) {
        this.$message.warning('请输入需要分析的问题');
        return;
      }
      this.analysisLoading = true;
      window.setTimeout(() => {
        this.analysisResult = [
          {
            title: '考试相关咨询增加35%',
            description:
              '近30天考试时间、考场和成绩发布成为学生最集中的关注点。',
            suggestion: '提前汇总并推送考试安排与成绩发布通知。',
          },
          {
            title: '图书馆预约需求持续上升',
            description: '晚间和考试周的自习座位咨询量明显增加。',
            suggestion: '评估延长开放时间并优化座位预约提醒。',
          },
          {
            title: '请假流程咨询集中',
            description: '学生主要询问材料要求、审批进度和销假方式。',
            suggestion: '完善请假工作流提示，并提供办理进度主动通知。',
          },
        ];
        this.analysisLoading = false;
      }, 450);
    },
  },
};
</script>

<style lang="scss" scoped>
.dashboard-page {
  min-height: 100%;
  padding: 22px;
  color: #1d304e;
  background: #f3f6fa;
}

.dashboard-header,
.demo-notice,
.metric-grid,
.dashboard-grid,
.decision-panel,
.management-links {
  max-width: 1320px;
  margin: 0 auto 18px;
}

.demo-notice {
  box-sizing: border-box;
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;

  .title-line {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  h1 {
    margin: 0;
    font-size: 25px;
  }
  p {
    margin: 6px 0 0;
    color: #7a8799;
    font-size: 13px;
  }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #8792a2;
  font-size: 11px;
  i {
    margin-right: 4px;
  }
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 15px;
}

.metric-card {
  display: flex;
  align-items: center;
  min-width: 0;
  padding: 20px;
  border: 1px solid #e5eaf2;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 6px 20px rgba(39, 67, 105, 0.05);
}

.metric-icon,
.segment-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  margin-right: 14px;
  color: #1768c9;
  font-size: 21px;
  border-radius: 13px;
  background: #edf5ff;

  &.green {
    color: #18845e;
    background: #eaf8f2;
  }
  &.violet {
    color: #7255c0;
    background: #f2effb;
  }
  &.orange {
    color: #ca7618;
    background: #fff4e6;
  }
}

.metric-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  > span {
    color: #748196;
    font-size: 11px;
  }
  strong {
    margin: 4px 0 3px;
    color: #213a5f;
    font-size: 24px;
  }
  strong small {
    margin-left: 2px;
    font-size: 11px;
    font-weight: 500;
  }
  p {
    margin: 0;
    font-size: 10px;
  }
  .up {
    color: #16936a;
  }
  .down {
    color: #d35a5f;
  }
}

.dashboard-grid {
  display: grid;
  gap: 15px;

  &--top {
    grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  }
  &--middle {
    grid-template-columns: minmax(0, 1.25fr) minmax(330px, 0.75fr);
  }
}

.panel {
  min-width: 0;
  padding: 22px 24px;
  border: 1px solid #e5eaf2;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 6px 20px rgba(39, 67, 105, 0.05);
}

.panel-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
  h2 {
    margin: 0 0 4px;
    font-size: 16px;
  }
  p {
    margin: 0;
    color: #909bab;
    font-size: 10px;
  }
}

.ranking-list {
  display: flex;
  flex-direction: column;
  gap: 13px;
}
.ranking-item {
  display: grid;
  grid-template-columns: 25px 76px minmax(80px, 1fr) 58px;
  align-items: center;
  gap: 9px;
  font-size: 11px;

  strong {
    color: #53657e;
    text-align: right;
  }
}

.rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  color: #7d899b;
  border-radius: 6px;
  background: #f1f3f6;
  &.top {
    color: #1768c9;
    font-weight: 700;
    background: #eaf3ff;
  }
}

.rank-name {
  color: #40536f;
}
.rank-bar {
  height: 7px;
  overflow: hidden;
  border-radius: 8px;
  background: #edf1f6;
}
.rank-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2f7bd2, #63a9e7);
}
.success-label,
.normal-status {
  display: flex;
  align-items: center;
  color: #3f7965;
  font-size: 10px;
}
.success-label i,
.normal-status i {
  width: 7px;
  height: 7px;
  margin-right: 5px;
  border-radius: 50%;
  background: #29a978;
  box-shadow: 0 0 0 3px rgba(41, 169, 120, 0.12);
}

.trend-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  height: 190px;
  padding-top: 17px;
  border-bottom: 1px solid #e7ebf1;
  background: repeating-linear-gradient(
    to top,
    transparent 0,
    transparent 44px,
    #f0f3f7 45px
  );
}

.trend-column {
  display: flex;
  align-items: center;
  width: 11%;
  flex-direction: column;
  color: #8a96a7;
  font-size: 10px;
}
.bar-wrap {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  width: 100%;
  height: 158px;
  flex-direction: column;
}
.bar {
  width: 58%;
  min-height: 12px;
  border-radius: 6px 6px 0 0;
  background: linear-gradient(180deg, #4ea0e7, #1768c9);
}
.bar-value {
  margin-bottom: 4px;
  color: #6e7c90;
  font-size: 9px;
}

.user-segments {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.user-segment {
  padding: 15px;
  border: 1px solid #e8edf3;
  border-radius: 11px;
  background: #fbfcfe;
}
.user-segment > .segment-icon {
  width: 38px;
  height: 38px;
  margin: 0 0 11px;
  font-size: 17px;
}
.segment-summary {
  display: flex;
  flex-direction: column;
}
.segment-summary span {
  color: #69778b;
  font-size: 11px;
}
.segment-summary strong {
  margin: 3px 0 11px;
  font-size: 19px;
}
.segment-summary small {
  margin-left: 3px;
  color: #929cac;
  font-size: 9px;
  font-weight: 400;
}
.segment-topics {
  padding-top: 10px;
  border-top: 1px solid #e7ebf0;
}
.segment-topics > span {
  color: #929dad;
  font-size: 9px;
}
.segment-topics p {
  margin: 6px 0 0;
  color: #53647c;
  font-size: 10px;
}

.system-list {
  display: flex;
  flex-direction: column;
}
.system-list > div {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) 48px 52px;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid #eff2f6;
  &:last-child {
    border-bottom: 0;
  }
  strong {
    display: block;
    color: #40536e;
    font-size: 11px;
  }
  small {
    color: #949ead;
    font-size: 9px;
  }
}
.system-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #e1a341;
}
.system-dot.live {
  background: #27aa78;
}
.latency {
  color: #8c97a7;
  font-size: 9px;
  text-align: right;
}

.decision-panel {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  overflow: hidden;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 26px rgba(31, 66, 112, 0.08);
}

.decision-side {
  padding: 27px;
  color: #fff;
  background: linear-gradient(145deg, #183c72, #1768c9);
  .eyebrow {
    color: rgba(255, 255, 255, 0.6);
    font-size: 10px;
    letter-spacing: 1.2px;
  }
  h2 {
    margin: 6px 0 8px;
    font-size: 20px;
  }
  > p {
    margin: 0 0 17px;
    color: rgba(255, 255, 255, 0.76);
    font-size: 11px;
    line-height: 1.7;
  }
}

.ai-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  margin-bottom: 14px;
  color: #1768c9;
  font-size: 19px;
  border-radius: 12px;
  background: #fff;
}
.suggested-questions {
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.suggested-questions button {
  padding: 8px 10px;
  color: rgba(255, 255, 255, 0.84);
  font-size: 10px;
  text-align: left;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.08);
}
.suggested-questions button:hover {
  background: rgba(255, 255, 255, 0.15);
}

.decision-workspace {
  min-height: 310px;
  padding: 25px;
}
.analysis-input {
  display: flex;
  gap: 8px;
}
.analysis-result {
  margin-top: 18px;
}
.result-head {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 13px;
  color: #2e4a6e;
  font-size: 12px;
  font-weight: 700;
}
.result-head > i {
  color: #1768c9;
}
.insight-item {
  display: flex;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid #edf1f5;
}
.insight-item > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  color: #1768c9;
  font-size: 10px;
  border-radius: 50%;
  background: #eaf3ff;
}
.insight-item strong {
  color: #40546f;
  font-size: 11px;
}
.insight-item p {
  margin: 3px 0;
  color: #758297;
  font-size: 10px;
}
.insight-item small {
  color: #227c5c;
  font-size: 10px;
}
.analysis-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 220px;
  flex-direction: column;
  color: #9ba5b3;
}
.analysis-empty i {
  margin-bottom: 10px;
  color: #b8c8dc;
  font-size: 36px;
}
.analysis-empty p {
  font-size: 11px;
}

.management-links {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 19px 23px;
  border: 1px solid #e4eaf2;
  border-radius: 13px;
  background: #fff;
  h2 {
    margin: 0 0 4px;
    font-size: 15px;
  }
  p {
    margin: 0;
    color: #8793a3;
    font-size: 10px;
  }
}

@media (max-width: 1080px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .dashboard-grid--top,
  .dashboard-grid--middle {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .dashboard-page {
    padding: 12px;
  }
  .dashboard-header,
  .management-links {
    align-items: flex-start;
    flex-direction: column;
  }
  .decision-panel {
    grid-template-columns: 1fr;
  }
  .user-segments {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
  .analysis-input {
    flex-direction: column;
  }
}
</style>
