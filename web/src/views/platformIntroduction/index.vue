<template>
  <div class="platform-introduction">
    <section class="hero-card">
      <div class="hero-copy">
        <div class="eyebrow">HEBEI UNIVERSITY · SMART CAMPUS</div>
        <h1>河小智——河北大学智能体服务平台</h1>
        <p>
          面向学生、教师和管理人员提供统一 AI
          服务入口，让校园咨询、信息查询和业务办理更简单、更高效。
        </p>
        <div class="hero-actions">
          <el-button type="primary" @click="goTo('/smartAssistant')">
            开始体验河小智
          </el-button>
          <el-button plain @click="goTo('/businessCenter')">
            查看校园业务能力
          </el-button>
        </div>
      </div>
      <img
        v-if="schoolIconPath"
        class="hero-mark"
        :src="avatarSrc(schoolIconPath)"
        alt="河北大学"
      />
    </section>

    <section class="section-card flow-section">
      <div class="section-heading">
        <div>
          <span class="section-kicker">SERVICE CLOSED LOOP</span>
          <h2>从一句话需求到校园服务完成</h2>
          <p class="section-description">
            河小智不只是对话助手，而是连接校园知识、业务系统与管理分析的统一入口。
          </p>
        </div>
        <el-tag size="small" effect="plain">比赛演示主线</el-tag>
      </div>
      <div class="flow-grid">
        <article v-for="(item, index) in serviceFlow" :key="item.title">
          <span class="flow-index">0{{ index + 1 }}</span>
          <i :class="item.icon"></i>
          <h3>{{ item.title }}</h3>
          <p>{{ item.description }}</p>
        </article>
      </div>
    </section>

    <section class="section-card demo-section">
      <div class="section-heading">
        <div>
          <span class="section-kicker">QUICK DEMO</span>
          <h2>按角色开始演示</h2>
          <p class="section-description">
            建议按“师生体验 → 能力连接 → 管理分析”的顺序讲解。
          </p>
        </div>
      </div>
      <div class="demo-grid">
        <button
          v-for="entry in visibleDemoEntries"
          :key="entry.path"
          type="button"
          class="demo-entry"
          @click="goTo(entry.path)"
        >
          <span class="entry-icon" :class="entry.color">
            <i :class="entry.icon"></i>
          </span>
          <span class="entry-copy">
            <small>{{ entry.audience }}</small>
            <strong>{{ entry.title }}</strong>
            <p>{{ entry.description }}</p>
          </span>
          <i class="el-icon-right entry-arrow"></i>
        </button>
      </div>
    </section>

    <section class="section-card">
      <h2>平台目标</h2>
      <p class="section-description">
        通过智能体理解用户需求，连接校内知识与业务服务，逐步实现从“用户寻找系统”到“AI
        协助完成业务”的服务方式升级。
      </p>
      <div class="value-grid">
        <div v-for="item in serviceValues" :key="item.title" class="value-item">
          <div class="value-icon">{{ item.icon }}</div>
          <div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.description }}</p>
          </div>
        </div>
      </div>
    </section>

    <section class="section-card capability-card">
      <div>
        <h2>服务对象</h2>
        <p class="section-description">覆盖校园学习、教学与管理等典型场景。</p>
      </div>
      <div class="audience-list">
        <span>学生服务</span>
        <span>教师服务</span>
        <span>管理服务</span>
        <span>校园综合服务</span>
      </div>
    </section>
  </div>
</template>

<script>
import { mapGetters } from 'vuex';
import { avatarSrc } from '@/utils/util';
import { checkPerm, PERMS } from '@/router/permission';

export default {
  name: 'PlatformIntroduction',
  data() {
    return {
      serviceFlow: [
        {
          title: 'AI 统一入口',
          description: '师生通过自然语言描述查询或办事需求。',
          icon: 'el-icon-chat-dot-round',
        },
        {
          title: '身份与意图理解',
          description: '智能体识别用户角色、业务意图和所需参数。',
          icon: 'el-icon-cpu',
        },
        {
          title: '校园能力调用',
          description: '通过 MCP、知识库或工作流连接对应服务。',
          icon: 'el-icon-connection',
        },
        {
          title: '服务与管理分析',
          description: '返回查询结果、启动办理流程并沉淀运行数据。',
          icon: 'el-icon-data-analysis',
        },
      ],
      demoEntries: [
        {
          audience: '学生·教师',
          title: '河小智助手',
          description: '体验课表查询、请假、图书查询和校园知识问答。',
          icon: 'el-icon-chat-line-round',
          color: 'blue',
          path: '/smartAssistant',
        },
        {
          audience: '平台建设人员',
          title: '校园业务中心',
          description: '查看教务、学工、图书馆与后勤服务的 AI 调用流程。',
          icon: 'el-icon-connection',
          color: 'cyan',
          path: '/businessCenter',
        },
        {
          audience: '学校管理人员',
          title: '智慧校园管理中心',
          description: '查看演示运行数据、服务热点和 AI 辅助决策。',
          icon: 'el-icon-data-line',
          color: 'violet',
          path: '/adminDashboard',
          perm: [PERMS.ADMIN_CENTER, PERMS.OBSERVATION_STATISTIC],
        },
      ],
      serviceValues: [
        {
          icon: '问',
          title: '一句话咨询',
          description: '统一获取校园制度、通知和办事指引。',
        },
        {
          icon: '查',
          title: '一句话查询',
          description: '自然语言表达需求，快速找到相关信息。',
        },
        {
          icon: '办',
          title: '一句话办理',
          description: '逐步连接校园业务能力，提供智能协助。',
        },
      ],
    };
  },
  computed: {
    ...mapGetters('user', ['commonInfo']),
    visibleDemoEntries() {
      return this.demoEntries.filter(item => checkPerm(item.perm));
    },
    schoolIconPath() {
      return (
        this.commonInfo?.data?.tab?.logo?.path ||
        this.commonInfo?.data?.home?.logo?.path ||
        ''
      );
    },
  },
  methods: {
    avatarSrc,
    goTo(path) {
      this.$router.push(path);
    },
  },
};
</script>

<style lang="scss" scoped>
.platform-introduction {
  min-height: 100%;
  padding: 24px;
  color: #102a56;
  background:
    radial-gradient(
      circle at 92% 4%,
      rgba(74, 143, 255, 0.16),
      transparent 28%
    ),
    #f5f8fc;
}

.hero-card,
.section-card {
  max-width: 1180px;
  margin: 0 auto 20px;
  border: 1px solid rgba(21, 89, 197, 0.1);
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 12px 34px rgba(28, 68, 125, 0.07);
}

.hero-card {
  min-height: 250px;
  padding: 48px 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  overflow: hidden;
  background: linear-gradient(135deg, #0e4cab, #2879e8);

  .hero-copy {
    max-width: 760px;
  }

  .eyebrow {
    margin-bottom: 18px;
    color: rgba(255, 255, 255, 0.68);
    font-size: 12px;
    letter-spacing: 2px;
  }

  h1 {
    margin-bottom: 20px;
    color: #fff;
    font-size: 32px;
    line-height: 1.4;
  }

  p {
    max-width: 660px;
    color: rgba(255, 255, 255, 0.84);
    font-size: 15px;
    line-height: 1.9;
  }

  .hero-actions {
    display: flex;
    gap: 10px;
    margin-top: 26px;

    .el-button--primary {
      color: #1559c5;
      border-color: #fff;
      background: #fff;
    }

    .el-button--default {
      color: #fff;
      border-color: rgba(255, 255, 255, 0.45);
      background: rgba(255, 255, 255, 0.08);
    }
  }

  .hero-mark {
    width: auto;
    max-width: 180px;
    height: auto;
    max-height: 124px;
    margin-left: 40px;
    object-fit: contain;
    filter: drop-shadow(0 18px 26px rgba(3, 31, 75, 0.25));
  }
}

.section-card {
  padding: 32px 36px;

  h2 {
    margin-bottom: 10px;
    font-size: 20px;
  }

  .section-description {
    color: #60738d;
    font-size: 14px;
    line-height: 1.8;
  }
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.section-kicker {
  color: #3976c2;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.3px;
}

.flow-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 24px;

  article {
    position: relative;
    min-width: 0;
    padding: 20px;
    border: 1px solid #e2eaf5;
    border-radius: 14px;
    background: #f8fbff;

    > i {
      display: block;
      margin: 17px 0 13px;
      color: #1768c9;
      font-size: 24px;
    }

    h3 {
      margin: 0 0 7px;
      color: #284869;
      font-size: 14px;
    }

    p {
      margin: 0;
      color: #718197;
      font-size: 11px;
      line-height: 1.7;
    }
  }
}

.flow-index {
  color: #9bb6d6;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1px;
}

.demo-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 22px;
}

.demo-entry {
  display: flex;
  align-items: center;
  min-width: 0;
  padding: 18px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  border: 1px solid #e0e8f3;
  border-radius: 14px;
  background: #fff;
  transition: 0.2s ease;

  &:hover {
    border-color: #9fc4ef;
    box-shadow: 0 8px 20px rgba(26, 82, 151, 0.1);
    transform: translateY(-1px);
  }
}

.entry-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  margin-right: 12px;
  color: #1768c9;
  font-size: 19px;
  border-radius: 12px;
  background: #edf5ff;

  &.cyan {
    color: #168c9b;
    background: #eaf8fa;
  }

  &.violet {
    color: #7356c2;
    background: #f2effb;
  }
}

.entry-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;

  small {
    color: #8593a6;
    font-size: 9px;
  }

  strong {
    margin: 3px 0 5px;
    color: #294766;
    font-size: 14px;
  }

  p {
    margin: 0;
    color: #748398;
    font-size: 10px;
    line-height: 1.6;
  }
}

.entry-arrow {
  margin-left: 10px;
  color: #a8b6c8;
}

.value-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-top: 24px;
}

.value-item {
  display: flex;
  gap: 14px;
  padding: 20px;
  border-radius: 14px;
  background: #f6f9fe;

  .value-icon {
    flex: 0 0 42px;
    width: 42px;
    height: 42px;
    border-radius: 12px;
    color: #fff;
    background: #1559c5;
    font-size: 17px;
    font-weight: 700;
    line-height: 42px;
    text-align: center;
  }

  h3 {
    margin: 2px 0 8px;
    font-size: 15px;
  }

  p {
    color: #6f8096;
    font-size: 13px;
    line-height: 1.6;
  }
}

.capability-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
}

.audience-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;

  span {
    padding: 9px 16px;
    border: 1px solid #cfe0f8;
    border-radius: 20px;
    color: #235da8;
    background: #f4f8fe;
    font-size: 13px;
  }
}

@media (max-width: 900px) {
  .hero-card {
    padding: 36px;

    .hero-mark {
      display: none;
    }

    h1 {
      font-size: 26px;
    }
  }

  .value-grid {
    grid-template-columns: 1fr;
  }

  .flow-grid,
  .demo-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .capability-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .audience-list {
    justify-content: flex-start;
  }
}

@media (max-width: 620px) {
  .platform-introduction {
    padding: 12px;
  }

  .hero-card,
  .section-card {
    padding: 24px;
  }

  .hero-card .hero-actions,
  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .flow-grid,
  .demo-grid {
    grid-template-columns: 1fr;
  }
}
</style>
