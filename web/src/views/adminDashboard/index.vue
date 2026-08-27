<template>
  <div class="dashboard-page">
    <header class="dashboard-header">
      <div class="header-copy">
        <div class="title-line">
          <h1>智慧校园管理中心</h1>
          <el-tag size="mini" type="warning" effect="plain">演示数据</el-tag>
        </div>
        <p>面向学校管理人员展示 AI 校园服务运行情况与辅助决策信息。</p>
      </div>
      <div class="header-actions">
        <span class="updated-at">
          <i></i>
          数据更新于 {{ updatedAt }}
        </span>
        <el-button
          type="primary"
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

    <section class="overview-hero">
      <div class="hero-summary">
        <div class="hero-section-title">
          <span class="hero-title-icon"><i class="el-icon-data-line"></i></span>
          <div>
            <h2>今日运行概览</h2>
            <p>集中查看校园智能服务核心运行指标</p>
          </div>
        </div>

        <div class="hero-metric-grid">
          <article
            v-for="metric in metrics"
            :key="metric.label"
            class="hero-metric"
          >
            <span class="metric-icon" :class="metric.color">
              <i :class="metric.icon"></i>
            </span>
            <div class="metric-main">
              <span>{{ metric.label }}</span>
              <div class="metric-value-row">
                <strong>
                  {{ metric.value }}
                  <small>{{ metric.unit }}</small>
                </strong>
                <p :class="metric.trend >= 0 ? 'up' : 'down'">
                  <i
                    :class="
                      metric.trend >= 0 ? 'el-icon-top' : 'el-icon-bottom'
                    "
                  ></i>
                  {{ Math.abs(metric.trend) }}%
                </p>
              </div>
            </div>
          </article>
        </div>
      </div>

      <div class="hero-trend">
        <div class="hero-trend-title">
          <div>
            <h3>近7日服务趋势</h3>
            <p>校园 AI 服务调用量</p>
          </div>
          <span>
            <i></i>
            成功率 98.5%
          </span>
        </div>
        <div class="line-chart">
          <svg viewBox="0 0 520 140" preserveAspectRatio="none">
            <defs>
              <linearGradient id="trendArea" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#79a7ff" stop-opacity="0.38" />
                <stop offset="100%" stop-color="#79a7ff" stop-opacity="0" />
              </linearGradient>
            </defs>
            <line
              v-for="line in 3"
              :key="line"
              x1="20"
              x2="500"
              :y1="line * 35"
              :y2="line * 35"
              class="chart-grid-line"
            />
            <polygon :points="trendAreaPoints" fill="url(#trendArea)" />
            <polyline :points="trendPoints" class="trend-line" />
            <circle
              v-for="(item, index) in weeklyTrend"
              :key="`point-${item.day}`"
              :cx="trendX(index)"
              :cy="trendY(item.percent)"
              r="4"
              class="trend-point"
            />
          </svg>
          <div class="chart-labels">
            <span v-for="item in weeklyTrend" :key="`label-${item.day}`">
              {{ item.day }}
            </span>
          </div>
        </div>
      </div>
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

    <section class="panel user-panel">
      <div class="panel-title">
        <div>
          <h2>用户服务分析</h2>
          <p>不同校园角色的使用情况与热门需求</p>
        </div>
        <el-tag size="mini" effect="plain">今日数据</el-tag>
      </div>
      <div class="user-segments">
        <div
          v-for="segment in userSegments"
          :key="segment.name"
          class="user-segment"
        >
          <div class="segment-head">
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
          </div>
          <div class="segment-kpis">
            <div v-for="metric in segment.metrics" :key="metric.label">
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
            </div>
          </div>
          <div class="segment-topics">
            <span>热门需求</span>
            <div class="topic-list">
              <em v-for="topic in segment.topics" :key="topic">
                {{ topic }}
              </em>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="decision-panel">
      <div class="decision-side">
        <span class="ai-mark">
          <img :src="schoolIconSrc" alt="河北大学校徽" />
        </span>
        <span class="eyebrow">智能辅助分析</span>
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
import { mapGetters } from 'vuex';
import { checkPerm, PERMS } from '@/router/permission';
import { avatarSrc } from '@/utils/util';
import { basePath } from '@/utils/config';

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
          label: '平台服务用户',
          value: '18,624',
          unit: '人',
          trend: 6.2,
          icon: 'el-icon-user',
          color: 'blue',
        },
        {
          label: '角色智能体',
          value: '3',
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
        {
          label: '今日 AI 调用',
          value: '3,568',
          unit: '次',
          trend: 12.6,
          icon: 'el-icon-chat-dot-round',
          color: 'green',
        },
        {
          label: '工作流执行',
          value: '1,286',
          unit: '次',
          trend: 18.4,
          icon: 'el-icon-share',
          color: 'blue',
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
          metrics: [
            { label: '服务调用次数', value: '2,846' },
            { label: '流程办理次数', value: '438' },
          ],
          topics: ['考试与成绩查询', '课程安排', '请假办理'],
        },
        {
          name: '教师用户',
          visits: '516',
          icon: 'el-icon-user',
          color: 'green',
          metrics: [
            { label: 'AI 备课次数', value: '126' },
            { label: '教学资源生成', value: '84' },
          ],
          topics: ['教学安排', '课程资料', '学生反馈'],
        },
        {
          name: '管理人员',
          visits: '206',
          icon: 'el-icon-office-building',
          color: 'violet',
          metrics: [
            { label: '数据分析次数', value: '38' },
            { label: '管理任务完成率', value: '92%' },
          ],
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
        '总结本周 AI 服务运行情况',
      ],
    };
  },
  computed: {
    ...mapGetters('user', ['commonInfo']),
    schoolIconSrc() {
      const schoolIconPath =
        this.commonInfo?.data?.tab?.logo?.path ||
        this.commonInfo?.data?.home?.logo?.path ||
        '';
      return avatarSrc(schoolIconPath, `${basePath}/aibase/favicon.ico`);
    },
    trendPoints() {
      return this.weeklyTrend
        .map(
          (item, index) => `${this.trendX(index)},${this.trendY(item.percent)}`,
        )
        .join(' ');
    },
    trendAreaPoints() {
      return `20,130 ${this.trendPoints} 500,130`;
    },
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
    trendX(index) {
      return 20 + index * 80;
    },
    trendY(percent) {
      return 130 - percent;
    },
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

/* 参考运营台的信息层级与栅格规则，保留智慧校园现有业务内容。 */
.dashboard-page {
  box-sizing: border-box;
  padding: 20px 24px 28px;
  color: #172b4d;
  background: #f5f7fb;
  font-family: 'PingFang SC', 'Noto Sans SC', sans-serif;
  font-size: 12px;
}

.dashboard-header,
.demo-notice,
.overview-hero,
.dashboard-grid,
.user-panel,
.decision-panel,
.management-links {
  width: 100%;
  max-width: 1360px;
  box-sizing: border-box;
  margin-right: auto;
  margin-left: auto;
}

.dashboard-header {
  min-height: 58px;
  margin-bottom: 12px;
  align-items: flex-start;

  .header-copy {
    min-width: 0;
  }

  h1 {
    color: #142544;
    font-size: 24px;
    font-weight: 600;
    line-height: 32px;
    letter-spacing: -0.4px;
  }

  p {
    margin-top: 4px;
    color: #7d889c;
    font-size: 12px;
    line-height: 20px;
  }
}

.header-actions {
  padding-top: 2px;
  gap: 14px;

  .updated-at {
    display: inline-flex;
    align-items: center;
    color: #8792a6;
    font-size: 12px;
    white-space: nowrap;
  }

  .updated-at > i {
    width: 7px;
    height: 7px;
    margin-right: 7px;
    border-radius: 50%;
    background: #25a675;
    box-shadow: 0 0 0 4px rgba(37, 166, 117, 0.1);
  }

  ::v-deep .el-button {
    min-width: 92px;
    border-radius: 6px;
  }
}

.demo-notice {
  margin-bottom: 12px;
  border: 1px solid #f1dfb9;
  border-radius: 6px;

  ::v-deep .el-alert__title {
    font-size: 12px;
  }
}

.overview-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(540px, 1.3fr) minmax(390px, 0.9fr);
  min-height: 232px;
  margin-bottom: 12px;
  overflow: hidden;
  color: #fff;
  border: 1px solid rgba(118, 163, 235, 0.16);
  border-radius: 9px;
  background:
    radial-gradient(
      circle at 76% 12%,
      rgba(57, 119, 221, 0.26),
      transparent 32%
    ),
    linear-gradient(118deg, #0b2852 0%, #103568 54%, #0a244a 100%);
  box-shadow: 0 8px 22px rgba(18, 51, 97, 0.13);

  &::after {
    position: absolute;
    top: -110px;
    right: -80px;
    width: 310px;
    height: 310px;
    border: 1px solid rgba(255, 255, 255, 0.05);
    border-radius: 50%;
    content: '';
  }
}

.hero-summary,
.hero-trend {
  position: relative;
  z-index: 1;
  min-width: 0;
  padding: 20px 22px;
}

.hero-summary {
  border-right: 1px solid rgba(255, 255, 255, 0.12);
}

.hero-section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 15px;

  h2 {
    margin: 0;
    color: #fff;
    font-size: 16px;
    font-weight: 600;
    line-height: 23px;
  }

  p {
    margin: 1px 0 0;
    color: rgba(222, 234, 255, 0.62);
    font-size: 12px;
  }
}

.hero-title-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  color: #9fc0ff;
  border: 1px solid rgba(159, 192, 255, 0.38);
  border-radius: 50%;
  background: rgba(84, 137, 224, 0.13);
}

.hero-metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.hero-metric {
  display: flex;
  align-items: center;
  min-width: 0;
  padding: 11px 13px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.055);

  .metric-icon {
    width: 38px;
    height: 38px;
    margin-right: 11px;
    color: #a9c7ff;
    border: 1px solid rgba(141, 181, 255, 0.24);
    border-radius: 50%;
    background: rgba(71, 126, 219, 0.17);
    font-size: 16px;

    &.green {
      color: #8fe0c2;
      border-color: rgba(95, 203, 162, 0.2);
      background: rgba(41, 158, 117, 0.13);
    }

    &.violet {
      color: #c1b2ff;
      border-color: rgba(169, 145, 255, 0.22);
      background: rgba(120, 91, 214, 0.14);
    }

    &.orange {
      color: #ffc381;
      border-color: rgba(255, 174, 87, 0.22);
      background: rgba(207, 119, 31, 0.14);
    }
  }

  .metric-main > span {
    color: rgba(222, 234, 255, 0.7);
    font-size: 12px;
  }

  .metric-value-row {
    display: flex;
    align-items: flex-end;
    gap: 9px;
  }

  strong {
    margin: 2px 0 0;
    color: #fff;
    font-size: 24px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    line-height: 30px;
  }

  strong small {
    color: rgba(255, 255, 255, 0.72);
    font-size: 12px;
    font-weight: 400;
  }

  p {
    margin: 0 0 3px;
    font-size: 12px;
  }

  .up {
    color: #75d9b1;
  }

  .down {
    color: #ff9b9b;
  }
}

.hero-trend {
  display: flex;
  flex-direction: column;
}

.hero-trend-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;

  h3 {
    margin: 0;
    color: #fff;
    font-size: 16px;
    font-weight: 600;
    line-height: 22px;
  }

  p {
    margin: 1px 0 0;
    color: rgba(222, 234, 255, 0.6);
    font-size: 12px;
  }

  > span {
    display: inline-flex;
    align-items: center;
    color: rgba(232, 241, 255, 0.78);
    font-size: 12px;
    white-space: nowrap;
  }

  > span i {
    width: 7px;
    height: 7px;
    margin-right: 6px;
    border-radius: 50%;
    background: #73d7b0;
    box-shadow: 0 0 0 3px rgba(115, 215, 176, 0.12);
  }
}

.line-chart {
  min-height: 0;
  margin-top: 10px;
  flex: 1;

  svg {
    display: block;
    width: 100%;
    height: 135px;
    overflow: visible;
  }

  .chart-grid-line {
    stroke: rgba(255, 255, 255, 0.1);
    stroke-width: 1;
    stroke-dasharray: 4 5;
  }

  .trend-line {
    fill: none;
    stroke: #83adff;
    stroke-width: 3;
    stroke-linecap: round;
    stroke-linejoin: round;
    vector-effect: non-scaling-stroke;
  }

  .trend-point {
    fill: #dbe8ff;
    stroke: #4f87ec;
    stroke-width: 2;
    vector-effect: non-scaling-stroke;
  }
}

.chart-labels {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-top: -2px;
  color: rgba(222, 234, 255, 0.56);
  font-size: 12px;
  text-align: center;
}

.dashboard-grid {
  margin-bottom: 12px;
  gap: 12px;

  &--top {
    grid-template-columns: minmax(0, 1.15fr) minmax(340px, 0.85fr);
  }
}

.panel {
  padding: 18px 20px;
  border: 1px solid #e3e8f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 3px 12px rgba(29, 59, 103, 0.045);
}

.panel-title {
  margin-bottom: 14px;

  > div:first-child {
    position: relative;
    padding-left: 10px;
  }

  > div:first-child::before {
    position: absolute;
    top: 3px;
    bottom: 3px;
    left: 0;
    width: 3px;
    border-radius: 2px;
    background: #5983ff;
    content: '';
  }

  h2 {
    margin-bottom: 3px;
    color: #243754;
    font-size: 16px;
    font-weight: 600;
  }

  p {
    color: #97a0af;
    font-size: 12px;
  }
}

.ranking-list {
  gap: 0;
}

.ranking-item {
  min-height: 42px;
  grid-template-columns: 26px 92px minmax(100px, 1fr) 62px;
  border-bottom: 1px solid #eef1f5;
  font-size: 12px;

  &:last-child {
    border-bottom: 0;
  }

  strong {
    color: #3f526f;
    font-variant-numeric: tabular-nums;
  }
}

.rank {
  width: 21px;
  height: 21px;
  border-radius: 5px;

  &.top {
    color: #3569db;
    background: #edf3ff;
  }
}

.rank-bar {
  height: 6px;
  background: #eef2f7;
}

.rank-bar span {
  background: linear-gradient(90deg, #3569db, #77a4ff);
}

.normal-status {
  color: #2c7e62;
  font-size: 12px;
}

.system-list > div {
  min-height: 42px;
  box-sizing: border-box;
  padding: 6px 0;
  grid-template-columns: 10px minmax(0, 1fr) 50px 54px;

  strong {
    color: #344863;
    font-size: 12px;
    font-weight: 600;
  }

  small {
    color: #9aa3b1;
    font-size: 12px;
  }
}

.user-panel {
  margin-bottom: 12px;
}

.user-segments {
  gap: 10px;
}

.user-segment {
  display: flex;
  min-width: 0;
  padding: 13px 14px;
  flex-direction: column;
  border: 1px solid #e7ebf2;
  border-radius: 7px;
  background: #fbfcfe;
}

.segment-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.segment-head > .segment-icon {
  width: 34px;
  height: 34px;
  margin: 0;
  border-radius: 50%;
  font-size: 14px;
}

.segment-summary {
  min-width: 0;

  span {
    color: #657289;
    font-size: 12px;
  }

  strong {
    margin: 2px 0 0;
    color: #263b59;
    font-size: 20px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  small {
    font-size: 12px;
    font-weight: 400;
  }
}

.segment-kpis {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px;
  margin-top: 12px;

  > div {
    min-width: 0;
    padding: 8px 9px;
    border-radius: 5px;
    background: #f1f5fa;
  }

  span,
  strong {
    display: block;
  }

  span {
    overflow: hidden;
    color: #8995a6;
    font-size: 11px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    margin-top: 3px;
    color: #284565;
    font-size: 16px;
    font-variant-numeric: tabular-nums;
  }
}

.segment-topics {
  min-width: 0;
  margin-top: 11px;
  padding: 9px 0 0;
  border-top: 1px solid #e5e9ef;
  border-left: 0;

  > span {
    color: #939dac;
    font-size: 12px;
  }
}

.topic-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 5px;

  em {
    max-width: 100%;
    padding: 3px 6px;
    overflow: hidden;
    color: #536680;
    border-radius: 4px;
    background: #f0f3f8;
    font-size: 12px;
    font-style: normal;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.decision-panel {
  grid-template-columns: 320px minmax(0, 1fr);
  margin-bottom: 12px;
  overflow: hidden;
  border: 1px solid #dedff4;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 3px 14px rgba(73, 67, 143, 0.055);
}

.decision-side {
  padding: 22px;
  color: #343261;
  border-right: 1px solid #e1e1f4;
  background:
    radial-gradient(
      circle at 15% 10%,
      rgba(117, 93, 216, 0.12),
      transparent 34%
    ),
    linear-gradient(145deg, #f7f5ff, #efedff);

  .eyebrow {
    color: #8b82bb;
    font-size: 12px;
    font-weight: 500;
  }

  h2 {
    margin: 5px 0 7px;
    color: #39346c;
    font-size: 18px;
    font-weight: 600;
  }

  > p {
    margin-bottom: 12px;
    color: #746f95;
    font-size: 12px;
    line-height: 1.65;
  }
}

.ai-mark {
  width: 44px;
  height: 44px;
  margin-bottom: 11px;
  color: #6954c7;
  border: 1px solid rgba(105, 84, 199, 0.14);
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.72);
  overflow: hidden;

  img {
    display: block;
    width: 34px;
    height: 34px;
    object-fit: contain;
  }
}

.suggested-questions {
  gap: 5px;
}

.suggested-questions button {
  padding: 7px 9px;
  color: #5f5884;
  border-color: rgba(105, 84, 199, 0.14);
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.6);
  font-size: 12px;
  line-height: 18px;

  &:hover {
    color: #5140a5;
    border-color: rgba(105, 84, 199, 0.3);
    background: #fff;
  }
}

.decision-workspace {
  min-height: 285px;
  padding: 21px 22px;
}

.analysis-input {
  ::v-deep .el-input__inner,
  ::v-deep .el-button {
    border-radius: 6px;
  }
}

.analysis-empty {
  height: 192px;
}

.result-head,
.insight-item > span,
.insight-item strong,
.insight-item p,
.insight-item small,
.analysis-empty p,
.latency {
  font-size: 12px;
}

.result-head {
  font-weight: 600;
}

.management-links {
  margin-bottom: 0;
  padding: 16px 19px;
  border: 1px solid #e3e8f0;
  border-radius: 8px;
  box-shadow: 0 3px 12px rgba(29, 59, 103, 0.04);

  h2 {
    color: #2b3e5a;
    font-size: 16px;
    font-weight: 600;
  }

  p {
    font-size: 12px;
  }

  ::v-deep .el-button {
    border-radius: 6px;
  }
}

@media (max-width: 1180px) {
  .overview-hero {
    grid-template-columns: 1fr;
  }

  .hero-summary {
    border-right: 0;
    border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  }

  .line-chart svg {
    height: 125px;
  }

  .segment-topics {
    margin-top: 10px;
    padding: 9px 0 0;
    border-top: 1px solid #e5e9ef;
    border-left: 0;
  }
}

@media (max-width: 860px) {
  .dashboard-page {
    padding: 16px;
  }

  .dashboard-grid--top,
  .decision-panel {
    grid-template-columns: 1fr;
  }

  .decision-side {
    border-right: 0;
    border-bottom: 1px solid #e1e1f4;
  }

  .user-segments {
    grid-template-columns: 1fr;
  }

  .hero-metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .segment-topics {
    margin-top: 10px;
    padding: 9px 0 0;
    border-top: 1px solid #e5e9ef;
    border-left: 0;
  }
}

@media (max-width: 620px) {
  .dashboard-header,
  .header-actions,
  .management-links {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-summary,
  .hero-trend {
    padding: 17px;
  }

  .hero-metric-grid {
    grid-template-columns: 1fr;
  }

  .segment-topics {
    margin-top: 9px;
    padding: 9px 0 0;
    border-top: 1px solid #e5e9ef;
    border-left: 0;
  }
}
</style>
