<template>
  <div class="smart-assistant-page">
    <main class="assistant-workspace">
      <header class="assistant-header">
        <div class="brand-block">
          <span :class="['brand-logo', { 'has-image': platformLogo }]">
            <img
              v-if="platformLogo"
              :src="avatarSrc(platformLogo)"
              alt="河小智"
            />
            <i v-else class="el-icon-cpu"></i>
          </span>
          <div>
            <h1>河小智</h1>
            <p>河北大学智能体服务助手</p>
          </div>
        </div>
        <div class="user-context">
          <span>
            <i class="el-icon-user"></i>
            {{ userInfo.userName || '校园用户' }}
          </span>
          <span>
            <i class="el-icon-office-building"></i>
            {{ currentOrgName }}
          </span>
        </div>
      </header>

      <section class="connection-strip">
        <div class="connection-label">
          <span class="status-dot"></span>
          <div>
            <strong>校园服务连接</strong>
            <small>通过智能体统一调用知识与业务能力</small>
          </div>
        </div>
        <div class="connection-items">
          <span
            v-for="item in connections"
            :key="item.name"
            :class="{ connected: item.connected }"
          >
            <i class="el-icon-circle-check"></i>
            {{ item.name }}
            <small>{{ item.connected ? '已接入' : '演示连接' }}</small>
          </span>
        </div>
      </section>

      <section ref="chatSection" class="conversation-card">
        <div class="assistant-shell">
          <CampusAgent ref="campusAgent" embedded>
            <template #welcome>
              <div class="agent-welcome">
                <span class="intro-kicker">HEBEI UNIVERSITY CAMPUS AI</span>
                <h2>你好，我是河小智。</h2>
                <p>我可以帮助你查询校园信息、办理校园事务、调用学校服务。</p>
                <div class="example-prompts">
                  <button
                    v-for="example in examples"
                    :key="example"
                    type="button"
                    @click="askAssistant(example)"
                  >
                    {{ example }}
                    <i class="el-icon-right"></i>
                  </button>
                </div>
              </div>
            </template>
          </CampusAgent>
        </div>
      </section>

      <section class="service-section">
        <div class="section-heading">
          <div>
            <span class="section-kicker">QUICK SERVICES</span>
            <h2>快捷校园服务</h2>
            <p>选择服务快速发起查询或演示校园业务办理流程。</p>
          </div>
          <el-tag size="small" type="info" effect="plain">比赛演示版</el-tag>
        </div>

        <div class="service-groups">
          <div
            v-for="group in serviceGroups"
            :key="group.name"
            class="service-group"
          >
            <div class="group-heading">
              <i :class="group.icon"></i>
              <strong>{{ group.name }}</strong>
              <span>{{ group.description }}</span>
            </div>
            <div class="service-grid">
              <button
                v-for="service in group.services"
                :key="service.key"
                type="button"
                class="service-card"
                @click="handleService(service)"
              >
                <span class="service-icon" :class="service.color">
                  <i :class="service.icon"></i>
                </span>
                <span class="service-copy">
                  <strong>{{ service.name }}</strong>
                  <small>{{ service.description }}</small>
                </span>
                <span
                  class="service-status"
                  :class="{ live: service.type === 'agent' }"
                >
                  {{ service.type === 'agent' ? '河小智' : '演示' }}
                </span>
              </button>
            </div>
          </div>
        </div>
      </section>
    </main>

    <el-dialog
      :visible.sync="demoVisible"
      width="620px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div slot="title" class="demo-dialog-title">
        <span>{{ activeService.name }}</span>
        <el-tag size="mini" type="warning" effect="plain">模拟服务</el-tag>
      </div>
      <div class="demo-question">
        <span>你</span>
        <p>{{ activeService.prompt }}</p>
      </div>
      <div class="demo-trace">
        <div v-for="(step, index) in activeService.steps || []" :key="step">
          <span>{{ index + 1 }}</span>
          <p>{{ step }}</p>
          <i
            v-if="index < activeService.steps.length - 1"
            class="el-icon-arrow-right"
          ></i>
        </div>
      </div>
      <div class="demo-answer">
        <div class="answer-head">
          <i class="el-icon-cpu"></i>
          河小智
        </div>
        <p>{{ activeService.answer }}</p>
      </div>
      <div class="demo-note">
        此结果用于比赛界面演示，接入校园 MCP 后替换为真实业务数据。
      </div>
      <span slot="footer">
        <el-button type="primary" size="small" @click="demoVisible = false">
          知道了
        </el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { mapGetters } from 'vuex';
import CampusAgent from '@/views/generalAgent/index.vue';
import { avatarSrc } from '@/utils/util';

const commonSteps = system => [
  '识别当前用户身份与组织',
  `调用${system} MCP（模拟）`,
  '校验业务参数并返回结果',
];

const repairService = {
  key: 'repair',
  name: '校园报修',
  description: '提交宿舍和设施报修',
  icon: 'el-icon-setting',
  color: 'red',
  type: 'mock',
  prompt: '宿舍水龙头漏水，帮我提交报修。',
  steps: commonSteps('后勤服务'),
  answer: '已识别为宿舍水暖报修，请补充宿舍楼、房间号和方便维修的时间。',
};

export default {
  name: 'SmartAssistant',
  components: { CampusAgent },
  data() {
    return {
      demoVisible: false,
      activeService: {},
      examples: ['帮我查今天的课程', '我要申请请假', '帮我查询图书馆开放时间'],
      connections: [
        { name: '教务系统', connected: false },
        { name: '学工系统', connected: false },
        { name: '图书馆系统', connected: false },
        { name: '校园知识库', connected: true },
        { name: '后勤服务', connected: false },
      ],
      hiddenServices: [repairService],
      serviceGroups: [
        {
          name: '学生服务',
          description: '学习与校园事务',
          icon: 'el-icon-school',
          services: [
            {
              key: 'schedule',
              name: '查询课表',
              description: '查看今日及本周课程',
              icon: 'el-icon-date',
              color: 'blue',
              type: 'mock',
              prompt: '帮我查一下今天的课程。',
              steps: commonSteps('教务系统'),
              answer:
                '今天共有2节课：第3—4节《数据结构》，地点为逸夫楼302；第7—8节《大学英语》，地点为综合楼B204。',
            },
            {
              key: 'score',
              name: '查询成绩',
              description: '查询课程成绩与学分',
              icon: 'el-icon-data-analysis',
              color: 'violet',
              type: 'mock',
              prompt: '查询我本学期已经发布的成绩。',
              steps: commonSteps('教务系统'),
              answer:
                '本学期已有3门课程发布成绩，平均分为87.3分；另有2门课程尚未发布。',
            },
            {
              key: 'leave',
              name: '我要请假',
              description: '发起并跟踪请假流程',
              icon: 'el-icon-edit-outline',
              color: 'orange',
              type: 'mock',
              prompt: '我明天下午有事，想请假半天。',
              steps: [
                '理解请假时间与原因',
                '启动学生请假工作流（模拟）',
                '提交辅导员审批',
              ],
              answer:
                '请假信息已整理：明天下午，共0.5天。确认原因后即可提交辅导员审批。',
            },
            {
              key: 'exam',
              name: '查询考试安排',
              description: '查看考试时间与考场',
              icon: 'el-icon-tickets',
              color: 'green',
              type: 'mock',
              prompt: '帮我查一下最近的考试安排。',
              steps: commonSteps('教务系统'),
              answer:
                '最近一场考试为《高等数学》，时间是6月18日09:00—11:00，考场为综合楼A201。',
            },
            {
              key: 'library',
              name: '图书查询',
              description: '查询开放时间与借阅规则',
              icon: 'el-icon-collection',
              color: 'cyan',
              type: 'agent',
              prompt: '请介绍河北大学图书馆的开放时间和借阅规则。',
            },
            {
              key: 'teacherInfo',
              name: '查找教师信息',
              description: '查询院系与教师公开信息',
              icon: 'el-icon-search',
              color: 'green',
              type: 'agent',
              prompt: '如何查询河北大学院系和教师的公开信息？',
            },
          ],
        },
        {
          name: '教师服务',
          description: '教学与课程管理',
          icon: 'el-icon-user',
          services: [
            {
              key: 'teaching',
              name: '查看教学安排',
              description: '查看授课与调课信息',
              icon: 'el-icon-notebook-2',
              color: 'blue',
              type: 'mock',
              prompt: '查看我今天下午的教学安排。',
              steps: commonSteps('教师教务服务'),
              answer:
                '今天下午第5—6节在综合楼C301讲授《人工智能导论》，当前没有调课通知。',
            },
            {
              key: 'course',
              name: '查询课程信息',
              description: '查询课程与教学班信息',
              icon: 'el-icon-reading',
              color: 'green',
              type: 'mock',
              prompt: '查询我本学期负责的课程信息。',
              steps: commonSteps('教师教务服务'),
              answer: '本学期负责2门课程、3个教学班，共计126名学生。',
            },
            {
              key: 'feedback',
              name: '查看学生反馈',
              description: '汇总课程反馈与关注点',
              icon: 'el-icon-chat-line-square',
              color: 'orange',
              type: 'mock',
              prompt: '帮我汇总近期学生的课程反馈。',
              steps: commonSteps('教学评价服务'),
              answer:
                '近期反馈主要集中在实验时间安排、课件下载和作业答疑三个方面。',
            },
            {
              key: 'teachingAnalysis',
              name: '教学数据分析',
              description: '查看教学数据与趋势',
              icon: 'el-icon-pie-chart',
              color: 'violet',
              type: 'mock',
              prompt: '分析本月课程互动情况。',
              steps: commonSteps('教学数据服务'),
              answer:
                '本月课堂互动次数较上月提升18%，学生最关注知识点为模型评估与数据预处理。',
            },
          ],
        },
      ],
    };
  },
  computed: {
    ...mapGetters('user', ['commonInfo', 'userInfo', 'orgInfo']),
    platformLogo() {
      const data = this.commonInfo?.data || {};
      return (
        data.generalAgent?.logo?.path ||
        data.tab?.logo?.path ||
        data.home?.logo?.path ||
        ''
      );
    },
    currentOrgName() {
      const orgs = this.orgInfo?.orgs || [];
      return (
        orgs.find(item => item.id === this.userInfo?.orgId)?.name || '河北大学'
      );
    },
    flatServices() {
      return this.serviceGroups
        .reduce((list, group) => list.concat(group.services), [])
        .concat(this.hiddenServices);
    },
  },
  mounted() {
    const service = this.$route.query.service;
    if (!service) return;
    const target = this.flatServices.find(item => item.key === service);
    if (target) this.$nextTick(() => this.handleService(target));
  },
  methods: {
    avatarSrc,
    askAssistant(prompt) {
      this.$refs.chatSection?.scrollIntoView({ behavior: 'smooth' });
      this.$nextTick(() => this.$refs.campusAgent?.ask(prompt));
    },
    handleService(service) {
      if (service.type === 'agent') {
        this.askAssistant(service.prompt);
        return;
      }
      this.activeService = service;
      this.demoVisible = true;
    },
  },
};
</script>

<style lang="scss" scoped>
.smart-assistant-page {
  min-height: 100%;
  padding: 24px;
  color: #202123;
  background: #f7f7f8;
}

.assistant-workspace {
  max-width: 1180px;
  margin: 0 auto;
}

.assistant-header,
.connection-strip,
.conversation-card,
.service-section {
  border: 1px solid #e5e7eb;
  background: #fff;
}

.assistant-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 22px;
  border-radius: 14px 14px 0 0;
}

.brand-block,
.user-context,
.connection-label,
.group-heading {
  display: flex;
  align-items: center;
}

.brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  margin-right: 12px;
  color: #fff;
  font-size: 20px;
  border-radius: 11px;
  background: #1559c5;

  &.has-image {
    background: transparent;
    border-radius: 0;
  }

  img {
    width: 42px;
    height: 42px;
    object-fit: contain;
  }
}

.brand-block {
  h1 {
    margin: 0 0 3px;
    font-size: 18px;
    line-height: 1.2;
  }

  p {
    margin: 0;
    color: #6b7280;
    font-size: 12px;
  }
}

.user-context {
  gap: 8px;

  span {
    padding: 6px 9px;
    color: #60646c;
    font-size: 11px;
    border-radius: 7px;
    background: #f5f5f5;
  }

  i {
    margin-right: 5px;
  }
}

.connection-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 22px;
  border-top: 0;
  border-radius: 0 0 14px 14px;
}

.status-dot {
  width: 8px;
  height: 8px;
  margin-right: 9px;
  border-radius: 50%;
  background: #1559c5;
  box-shadow: 0 0 0 4px rgba(21, 89, 197, 0.1);
}

.connection-label {
  min-width: 230px;

  > div {
    display: flex;
    flex-direction: column;
  }

  strong {
    font-size: 12px;
  }

  small {
    margin-top: 2px;
    color: #8b8e94;
    font-size: 9px;
  }
}

.connection-items {
  display: flex;
  justify-content: flex-end;
  gap: 7px;
  flex-wrap: wrap;

  > span {
    display: inline-flex;
    align-items: center;
    padding: 5px 7px;
    color: #526f95;
    font-size: 10px;
    border: 1px solid #dae5f2;
    border-radius: 7px;
    background: #f6f9fd;

    > i {
      margin-right: 4px;
      color: #6b8db8;
    }

    > small {
      margin-left: 5px;
      color: #7d93ae;
      font-size: 8px;
    }

    &.connected {
      color: #1559c5;
      border-color: #cfe0f8;
      background: #f2f7fd;

      > i {
        color: #1559c5;
      }

      > small {
        color: #5680b7;
      }
    }
  }
}

.agent-welcome {
  max-width: 820px;
  width: 100%;
  text-align: center;

  h2 {
    margin: 8px 0 10px;
    color: #202123;
    font-size: 30px;
    font-weight: 600;
    letter-spacing: -0.5px;
  }

  > p {
    margin: 0;
    color: #6b7280;
    font-size: 14px;
    line-height: 1.8;
  }
}

.intro-kicker,
.section-kicker {
  color: #1559c5;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.4px;
}

.example-prompts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 9px;
  margin-top: 24px;

  button {
    display: flex;
    align-items: center;
    justify-content: space-between;
    min-width: 0;
    padding: 11px 13px;
    color: #4c4f55;
    font-size: 11px;
    text-align: left;
    cursor: pointer;
    border: 1px solid #e5e7eb;
    border-radius: 10px;
    background: #fff;
    transition: 0.2s ease;

    &:hover {
      border-color: #b9c0c9;
      background: #fafafa;
    }

    i {
      margin-left: 8px;
      color: #a2a5aa;
    }
  }
}

.conversation-card {
  overflow: hidden;
  border-radius: 16px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.05);
}

.assistant-shell {
  height: 650px;
  background: #fff;

  ::v-deep .general-agent-page.embedded .input-area.is-centered {
    padding-top: 18px;
  }

  ::v-deep .general-agent-page.embedded .input-container:focus-within {
    border-color: #1559c5;
  }

  ::v-deep .general-agent-page.embedded .send-btn {
    color: #1559c5;
  }

  ::v-deep .general-agent-page.embedded .history-loading i,
  ::v-deep .general-agent-page.embedded .action-icon:hover,
  ::v-deep .general-agent-page.embedded .config-btn:hover,
  ::v-deep .general-agent-page.embedded .mode-btn:hover,
  ::v-deep .general-agent-page.embedded .mode-btn-selected {
    color: #1559c5;
  }

  ::v-deep .general-agent-page.embedded .config-btn:hover,
  ::v-deep .general-agent-page.embedded .mode-btn:hover,
  ::v-deep .general-agent-page.embedded .mode-btn-selected {
    border-color: rgba(21, 89, 197, 0.3);
    background: rgba(21, 89, 197, 0.08);
  }

  ::v-deep .general-agent-page.embedded .scroll-to-bottom-btn {
    color: #1559c5;
    border-color: #1559c5;

    &:hover {
      color: #fff;
      background: #1559c5;
      box-shadow: 0 4px 12px rgba(21, 89, 197, 0.3);
    }
  }
}

.service-section {
  margin-top: 22px;
  padding: 25px;
  border-radius: 16px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;

  h2 {
    margin: 5px 0 4px;
    font-size: 18px;
  }

  p {
    margin: 0;
    color: #85888e;
    font-size: 11px;
  }
}

.service-groups {
  display: flex;
  flex-direction: column;
  gap: 24px;
  margin-top: 23px;
}

.group-heading {
  margin-bottom: 10px;

  > i {
    margin-right: 7px;
    color: #1559c5;
  }

  strong {
    font-size: 12px;
  }

  span {
    margin-left: 8px;
    color: #9a9da2;
    font-size: 9px;
  }
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 9px;
}

.service-card {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 0;
  padding: 12px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  border: 1px solid #e8e8e8;
  border-radius: 11px;
  background: #fff;
  transition: 0.2s ease;

  &:hover {
    border-color: #c9cbd0;
    background: #fafafa;
    transform: translateY(-1px);
  }
}

.service-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  margin-right: 9px;
  color: #4169a8;
  border-radius: 9px;
  background: #f0f4fa;

  &.green,
  &.orange,
  &.violet,
  &.cyan,
  &.red {
    color: #4169a8;
    background: #f0f4fa;
  }
}

.service-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding-right: 35px;

  strong {
    margin-bottom: 3px;
    color: #34363a;
    font-size: 11px;
  }

  small {
    overflow: hidden;
    color: #93969c;
    font-size: 9px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.service-status {
  position: absolute;
  top: 9px;
  right: 9px;
  color: #6f86a5;
  font-size: 8px;

  &.live {
    color: #1559c5;
  }
}

.demo-dialog-title {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #253858;
  font-size: 18px;
  font-weight: 700;
}

.demo-question,
.demo-answer {
  display: flex;
  gap: 10px;
  padding: 13px 15px;
  border-radius: 10px;

  p {
    margin: 0;
    line-height: 1.7;
  }
}

.demo-question {
  background: #f3f6fa;

  span {
    color: #69788f;
    font-weight: 700;
  }
}

.demo-trace {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 18px 0;

  div {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  span {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    color: #1559c5;
    font-size: 11px;
    border-radius: 50%;
    background: #eaf3ff;
  }

  p {
    margin: 0;
    color: #5e6d82;
    font-size: 11px;
  }

  i {
    color: #b6c0ce;
  }
}

.demo-answer {
  flex-direction: column;
  border: 1px solid #d9e8f8;
  background: #f5faff;
}

.answer-head {
  color: #1559c5;
  font-weight: 700;

  i {
    margin-right: 6px;
  }
}

.demo-note {
  margin-top: 12px;
  color: #9a702f;
  font-size: 11px;
}

@media (max-width: 960px) {
  .connection-strip {
    align-items: flex-start;
    flex-direction: column;
  }

  .connection-items {
    justify-content: flex-start;
  }

  .service-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .smart-assistant-page {
    padding: 10px;
  }

  .assistant-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .user-context {
    flex-wrap: wrap;
  }

  .agent-welcome {
    h2 {
      font-size: 24px;
    }
  }

  .example-prompts,
  .service-grid {
    grid-template-columns: 1fr;
  }

  .assistant-shell {
    height: 600px;
  }

  .service-section {
    padding: 18px;
  }

  .demo-trace {
    align-items: flex-start;
    flex-direction: column;
  }

  .demo-trace i {
    display: none;
  }
}
</style>
