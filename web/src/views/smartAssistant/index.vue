<template>
  <div class="smart-assistant-page">
    <CampusAgent v-if="roleProfile" ref="campusAgent">
      <template #header-title>
        <div class="assistant-title">
          <span :class="['assistant-logo', { image: platformLogo }]">
            <img
              v-if="platformLogo"
              :src="avatarSrc(platformLogo)"
              alt="河小智"
            />
            <i v-else class="el-icon-cpu"></i>
          </span>
          <span class="assistant-name">
            <strong>{{ roleProfile.assistantName }}</strong>
            <small>河北大学智能体服务平台 · {{ roleProfile.label }}身份</small>
          </span>
          <el-radio-group
            v-if="canPreviewRoles"
            :value="roleProfile.key"
            size="mini"
            @input="switchRole"
          >
            <el-radio-button
              v-for="role in previewRoles"
              :key="role.key"
              :label="role.key"
            >
              {{ role.label }}
            </el-radio-button>
          </el-radio-group>
          <el-tag v-else size="mini" effect="plain">
            {{ roleProfile.label }}
          </el-tag>
        </div>
      </template>

      <template #welcome>
        <div class="agent-welcome">
          <span class="intro-kicker">HEBEI UNIVERSITY CAMPUS AI</span>
          <h1>你好，{{ displayName }}</h1>
          <p>我是{{ roleProfile.assistantName }}，{{ roleProfile.welcome }}</p>

          <div class="role-modules">
            <button
              v-for="module in roleModules"
              :key="module.key"
              type="button"
              @click="handleModule(module)"
            >
              <i :class="module.icon"></i>
              <span>
                <strong>{{ module.name }}</strong>
                <small>{{ module.description }}</small>
              </span>
              <i class="el-icon-right"></i>
            </button>
          </div>

          <div class="example-prompts">
            <span>你可以这样问</span>
            <button
              v-for="example in roleProfile.examples"
              :key="example"
              type="button"
              @click="handleExample(example)"
            >
              {{ example }}
            </button>
          </div>
        </div>
      </template>
    </CampusAgent>

    <div v-else class="role-empty">
      <i class="el-icon-user"></i>
      <h2>{{ roleEmptyTitle }}</h2>
      <p>{{ roleEmptyDescription }}</p>
    </div>

    <el-dialog
      :visible.sync="demoVisible"
      width="620px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div slot="title" class="demo-title">
        <span>{{ activeModule.name }}</span>
        <el-tag size="mini" type="warning" effect="plain">比赛演示</el-tag>
      </div>
      <div class="demo-question">“{{ activeModule.prompt }}”</div>
      <div class="demo-trace">
        <div v-for="(step, index) in activeModule.steps || []" :key="step">
          <span>{{ index + 1 }}</span>
          <p>{{ step }}</p>
          <i
            v-if="index < activeModule.steps.length - 1"
            class="el-icon-arrow-right"
          ></i>
        </div>
      </div>
      <div class="demo-answer">
        <strong>{{ roleProfile && roleProfile.assistantName }}</strong>
        <p>{{ activeModule.answer }}</p>
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
import { mapActions, mapGetters } from 'vuex';
import CampusAgent from '@/views/generalAgent/index.vue';
import { avatarSrc } from '@/utils/util';
import {
  CAMPUS_ROLES,
  CAMPUS_ROLE_STATUS,
  campusDisplayName,
  getCampusRoleProfile,
} from '@/utils/campusRole';

const mcpSteps = system => [
  '用户身份与数据权限校验',
  `角色 Agent 调用${system} MCP（模拟）`,
  '整理业务结果并返回',
];

const servicePrompts = {
  schedule: '今天有什么课？',
  exam: '下周有哪些考试？',
  score: '这学期成绩出了哪些？',
  repair: '宿舍水龙头漏水，帮我提交报修。',
  library: '图书馆几点关门？',
  teaching: '我今天有哪些课？',
  feedback: '分析本班高频错题',
  teachingAnalysis: '分析本班知识点掌握情况',
  collegeAnalysis: '分析本学期课程运行情况',
  report: '分析本学期课程运行情况',
};

export default {
  name: 'SmartAssistant',
  components: { CampusAgent },
  data() {
    return {
      demoVisible: false,
      activeModule: {},
      modules: {
        student: [
          {
            key: 'academic',
            name: '学业中心',
            description: '课表、考试、成绩与培养方案',
            icon: 'el-icon-reading',
            type: 'route',
            path: '/campus/student/courses',
          },
          {
            key: 'affairs',
            name: '事务办理',
            description: '一句话发起请假等校园流程',
            icon: 'el-icon-edit-outline',
            type: 'mock',
            prompt: '我下午想请假',
            steps: [
              '学生 Agent 解析时间、原因与请假类型',
              '检查缺失参数并启动请假 Workflow',
              '通过学工 MCP 提交模拟学工系统',
            ],
            answer:
              '已识别为事假，时间为今天下午。请补充请假原因，确认后将提交辅导员审批。',
          },
          {
            key: 'campus',
            name: '校园服务',
            description: '知识问答与实时校园服务',
            icon: 'el-icon-school',
            type: 'agent',
            prompt: '图书馆几点关门？',
          },
          {
            key: 'learning',
            name: 'AI 学习助手',
            description: '总结、计划、错题与同类练习',
            icon: 'el-icon-data-analysis',
            type: 'route',
            path: '/campus/student/analysis',
          },
        ],
        teacher: [
          {
            key: 'teaching',
            name: '我的教学',
            description: '授课、教室与监考安排',
            icon: 'el-icon-notebook-2',
            type: 'route',
            path: '/campus/teacher/teaching',
          },
          {
            key: 'preparation',
            name: 'AI 智能备课',
            description: '从课程资料生成教案与任务',
            icon: 'el-icon-document',
            type: 'route',
            path: '/campus/teacher/resources',
          },
          {
            key: 'feedback',
            name: '学情与反馈',
            description: '掌握度、高频错题与教学建议',
            icon: 'el-icon-data-line',
            type: 'route',
            path: '/campus/teacher/analysis',
          },
        ],
        academic_admin: [
          {
            key: 'dashboard',
            name: '教学运行驾驶舱',
            description: '核心指标、趋势与异常提醒',
            icon: 'el-icon-data-analysis',
            type: 'route',
            path: '/adminDashboard',
          },
          {
            key: 'dataQa',
            name: 'AI 数据问答',
            description: '用自然语言查询教学数据',
            icon: 'el-icon-chat-dot-round',
            type: 'mock',
            prompt: '哪些课程平均成绩低于 70 分？',
            steps: mcpSteps('教学数据'),
            answer:
              '发现3门课程平均成绩低于70分：数据结构 68.4，高等数学 66.8，大学物理 69.5。',
          },
          {
            key: 'operation',
            name: '教学运行分析',
            description: '发现问题、分析原因并给出建议',
            icon: 'el-icon-pie-chart',
            type: 'mock',
            prompt: '分析本学期课程运行情况',
            steps: mcpSteps('教务统计'),
            answer:
              '课程整体运行平稳，任务完成率98.6%；建议重点关注3门调课频繁课程与8门反馈波动课程。',
          },
          {
            key: 'monitoring',
            name: '平台服务监控',
            description: 'MCP 状态、调用量与成功率',
            icon: 'el-icon-monitor',
            type: 'route',
            path: '/businessCenter',
          },
        ],
      },
    };
  },
  computed: {
    ...mapGetters('user', ['commonInfo', 'userInfo', 'campusRole']),
    canPreviewRoles() {
      return this.campusRole.canPreview;
    },
    previewRoles() {
      return Object.values(CAMPUS_ROLES);
    },
    roleProfile() {
      return getCampusRoleProfile(this.campusRole.effectiveRole);
    },
    roleModules() {
      return this.roleProfile ? this.modules[this.roleProfile.key] || [] : [];
    },
    displayName() {
      return campusDisplayName(this.userInfo?.userName, this.roleProfile);
    },
    roleEmptyTitle() {
      return this.campusRole.roleStatus === CAMPUS_ROLE_STATUS.CONFLICT
        ? '当前账号存在校园角色冲突'
        : '当前账号尚未配置校园角色';
    },
    roleEmptyDescription() {
      return this.campusRole.roleStatus === CAMPUS_ROLE_STATUS.CONFLICT
        ? '请联系管理员，仅保留一个学生、教师或教务角色。'
        : '请联系管理员配置 student、teacher 或 academic_admin 角色。';
    },
    platformLogo() {
      const data = this.commonInfo?.data || {};
      return (
        data.generalAgent?.logo?.path ||
        data.tab?.logo?.path ||
        data.home?.logo?.path ||
        ''
      );
    },
  },
  created() {
    this.syncPreviewRole();
  },
  watch: {
    '$route.query.previewRole'() {
      this.syncPreviewRole();
    },
  },
  mounted() {
    const service = this.$route.query.service;
    if (!service) return;
    this.$nextTick(() =>
      this.handleExample(servicePrompts[service] || service),
    );
  },
  methods: {
    avatarSrc,
    ...mapActions('user', ['setCampusPreviewRole']),
    async switchRole(previewRole) {
      const campusRole = await this.setCampusPreviewRole(previewRole);
      if (campusRole.previewRole !== previewRole) return;
      this.$router.replace({
        query: { ...this.$route.query, previewRole },
      });
    },
    syncPreviewRole() {
      return this.setCampusPreviewRole(this.$route.query.previewRole || null);
    },
    askAssistant(prompt) {
      this.$nextTick(() => this.$refs.campusAgent?.ask(prompt));
    },
    handleExample(prompt) {
      const module = this.roleModules.find(item => item.prompt === prompt);
      module ? this.handleModule(module) : this.askAssistant(prompt);
    },
    handleModule(module) {
      if (module.type === 'route') {
        this.$router.push(module.path);
      } else if (module.type === 'agent') {
        this.askAssistant(module.prompt);
      } else {
        this.activeModule = module;
        this.demoVisible = true;
      }
    },
  },
};
</script>

<style lang="scss" scoped>
.smart-assistant-page {
  position: absolute;
  inset: 0;
}

.assistant-title,
.assistant-logo,
.role-modules button,
.example-prompts,
.demo-title,
.demo-trace,
.demo-trace > div {
  display: flex;
  align-items: center;
}

.assistant-title {
  min-width: 0;
  gap: 10px;
}

.assistant-logo {
  justify-content: center;
  flex: 0 0 34px;
  height: 34px;
  color: #fff;
  border-radius: 9px;
  background: #1559c5;

  &.image {
    background: transparent;
  }

  img {
    width: 34px;
    height: 34px;
    object-fit: contain;
  }
}

.assistant-name {
  display: flex;
  min-width: 190px;
  flex-direction: column;

  strong {
    color: #202123;
    font-size: 14px;
  }

  small {
    margin-top: 2px;
    color: #8a8f98;
    font-size: 10px;
    font-weight: 400;
  }
}

.agent-welcome {
  width: min(820px, 100%);
  text-align: center;

  h1 {
    margin: 8px 0 8px;
    color: #202123;
    font-size: 30px;
  }

  > p {
    margin: 0;
    color: #69707d;
    line-height: 1.7;
  }
}

.intro-kicker {
  color: #1559c5;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.4px;
}

.role-modules {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 22px;

  button {
    min-width: 0;
    padding: 13px 14px;
    color: #334155;
    text-align: left;
    cursor: pointer;
    border: 1px solid #e3e8ef;
    border-radius: 11px;
    background: #fff;

    &:hover {
      border-color: #a9c5ea;
      background: #f7faff;
    }

    > i:first-child {
      margin-right: 10px;
      color: #1559c5;
      font-size: 20px;
    }

    > i:last-child {
      margin-left: auto;
      color: #a9b1bd;
    }

    span {
      display: flex;
      min-width: 0;
      flex-direction: column;
    }

    strong {
      font-size: 13px;
    }

    small {
      margin-top: 3px;
      overflow: hidden;
      color: #8a8f98;
      font-size: 10px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

.example-prompts {
  justify-content: center;
  gap: 7px;
  margin-top: 18px;
  flex-wrap: wrap;

  > span {
    color: #9aa0aa;
    font-size: 10px;
  }

  button {
    padding: 6px 9px;
    color: #52647c;
    font-size: 10px;
    cursor: pointer;
    border: 1px solid #e0e6ed;
    border-radius: 8px;
    background: #f8fafc;
  }
}

.role-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #69707d;
  flex-direction: column;
  background: #f5f7fa;

  > i {
    color: #1559c5;
    font-size: 42px;
  }

  h2 {
    margin: 16px 0 8px;
    color: #202123;
  }

  p {
    margin: 0;
  }
}

.demo-title {
  gap: 9px;
  color: #253858;
  font-size: 18px;
  font-weight: 700;
}

.demo-question,
.demo-answer {
  padding: 13px 15px;
  border-radius: 10px;
}

.demo-question {
  color: #46566d;
  background: #f3f6fa;
}

.demo-trace {
  justify-content: center;
  gap: 8px;
  margin: 18px 0;

  > div {
    gap: 6px;
  }

  span {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 22px;
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
  color: #365270;
  border: 1px solid #d9e8f8;
  background: #f5faff;

  strong {
    color: #1559c5;
  }

  p {
    margin: 7px 0 0;
    line-height: 1.7;
  }
}

::v-deep .general-agent-page .header-title {
  overflow: visible;
}

@media (max-width: 760px) {
  .assistant-name small,
  .assistant-title .el-radio-group {
    display: none;
  }

  .assistant-name {
    min-width: 0;
  }

  .role-modules {
    grid-template-columns: 1fr;
  }

  .demo-trace {
    align-items: flex-start;
    flex-direction: column;

    i {
      display: none;
    }
  }
}
</style>
