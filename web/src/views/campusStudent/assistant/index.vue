<template>
  <div
    class="student-assistant"
    :class="{ 'student-assistant--conversation': hasConversation }"
  >
    <header class="student-assistant__header">
      <slot name="header-title"></slot>
      <button
        v-if="hasConversation && !configError"
        type="button"
        class="student-assistant__new"
        title="新建对话"
        aria-label="新建对话"
        @click="newConversation"
      >
        <i class="el-icon-plus"></i>
        <span>新对话</span>
      </button>
    </header>

    <section v-if="configError" class="student-assistant__empty">
      <img
        :src="displayAvatar"
        alt="河小智学生助手"
        @error="handleAvatarError"
      />
      <h2>河小智学生助手暂未开放</h2>
      <p>{{ configError }}</p>
      <div>
        <el-button size="small" @click="openPage('/campus/student/courses')">
          查看我的课程
        </el-button>
        <el-button size="small" @click="openPage('/campus/student/affairs')">
          查看我的事务
        </el-button>
        <el-button size="small" @click="openPage('/campus/student/analysis')">
          打开学习分析
        </el-button>
      </div>
    </section>

    <AgentChat
      v-else
      ref="chat"
      chat-type="chat"
      type="campusStudent"
      input-placeholder="问问河小智，课程、考试、成绩、校园事务都可以……"
      :edit-form="chatEditForm"
      :campus-student-adapter="campusStudentAdapter"
      :visible-clear-history="false"
      :visible-upload="false"
      :max-pic-num="0"
      :max-file-num="0"
      @conversation-state="hasConversation = $event"
    >
      <template #welcome>
        <section class="student-welcome-intro" aria-labelledby="welcome-title">
          <img
            :src="welcomeAvatar"
            alt="河北大学校徽"
            @error="handleWelcomeAvatarError"
          />
          <h1 id="welcome-title">你好，我是河小智</h1>
          <p>问课程、查成绩、看事务，让校园服务更简单。</p>
        </section>
      </template>

      <template #welcome-after-composer>
        <div class="student-welcome-followup">
          <nav class="student-suggestions" aria-label="快捷问题">
            <button
              v-for="question in quickQuestions"
              :key="question"
              type="button"
              @click="ask(question)"
            >
              {{ question }}
            </button>
          </nav>
          <p class="student-assistant__tip">
            河小智提供校园信息辅助查询，请以学校正式通知为准。
          </p>
        </div>
      </template>

      <template #afterContent="{ item }">
        <nav
          v-if="actionLinks(item).length"
          class="student-answer-links"
          aria-label="相关学生服务"
        >
          <button
            v-for="link in actionLinks(item)"
            :key="link.label"
            type="button"
            @click="openPage(link.path)"
          >
            {{ link.label }}
            <i class="el-icon-right"></i>
          </button>
        </nav>
      </template>
    </AgentChat>
  </div>
</template>

<script>
import AgentChat from '@/views/agent/components/chat.vue';
import { mapGetters } from 'vuex';
import { avatarSrc, getXClientId } from '@/utils/util';
import { CAMPUS_ROLES, campusStudentAssistantAvatar } from '@/utils/campusRole';
import {
  createStudentAssistantConversation,
  getStudentAssistant,
  STUDENT_ASSISTANT_CHAT_URL,
} from '@/api/campusStudent';

const STRUCTURED_ACTIONS = [
  {
    markers: ['query_my_schedule', '查询我的课程'],
    label: '查看完整课表',
    path: '/campus/student/courses',
  },
  {
    markers: ['query_my_exam_schedule', '查询我的考试'],
    label: '查看考试安排',
    path: '/campus/student/courses',
  },
  {
    markers: ['query_my_score', '查询我的成绩'],
    label: '查看成绩详情',
    path: '/campus/student/analysis',
  },
  {
    markers: ['query_my_leave_records', '查询我的请假记录'],
    label: '查看全部请假记录',
    path: '/campus/student/affairs',
  },
  {
    markers: ['query_my_learning_summary', '学习情况分析'],
    label: '打开学习分析',
    path: '/campus/student/analysis',
  },
];

export default {
  name: 'CampusStudentAssistant',
  components: { AgentChat },
  data() {
    return {
      configError: '',
      hasConversation: false,
      schoolLogoFailed: false,
      quickQuestions: CAMPUS_ROLES.student.examples,
      editForm: {
        name: '河小智学生助手',
        avatar: { path: campusStudentAssistantAvatar() },
        prologue: '',
        recommendQuestion: [],
        recommendConfig: { recommendEnable: false },
      },
    };
  },
  async created() {
    await this.loadAssistant();
  },
  computed: {
    ...mapGetters('user', ['token', 'userInfo', 'commonInfo']),
    fallbackAvatar() {
      return campusStudentAssistantAvatar();
    },
    displayAvatar() {
      return avatarSrc(this.editForm.avatar.path, this.fallbackAvatar);
    },
    welcomeAvatar() {
      if (this.schoolLogoFailed) return this.displayAvatar;
      const data = this.commonInfo?.data || {};
      return avatarSrc(
        data.generalAgent?.logo?.path ||
          data.tab?.logo?.path ||
          data.home?.logo?.path,
        this.displayAvatar,
      );
    },
    chatEditForm() {
      return {
        ...this.editForm,
        avatar: { ...this.editForm.avatar, path: this.welcomeAvatar },
      };
    },
    campusStudentAdapter() {
      return {
        createConversation: this.createConversation,
        stream: this.stream,
      };
    },
  },
  methods: {
    async loadAssistant() {
      try {
        const res = await getStudentAssistant();
        if (res.code !== 0 || !res.data) {
          this.configError =
            '管理员完成配置并发布后即可使用，你仍可查看个人校园信息。';
          return;
        }
        this.editForm.name = res.data.name || '河小智学生助手';
        this.editForm.avatar = {
          ...(res.data.avatar || {}),
          path: campusStudentAssistantAvatar(res.data.avatar?.path),
        };
        this.editForm.prologue = res.data.prologue || '';
        this.editForm.recommendQuestion = (
          res.data.recommendQuestion || []
        ).map(value => ({ value }));
        this.emitAssistantLoaded(res.data);
      } catch (_error) {
        this.configError =
          '管理员完成配置并发布后即可使用，你仍可查看个人校园信息。';
      }
    },
    emitAssistantLoaded(data = {}) {
      this.$emit('assistant-loaded', {
        ...data,
        name: this.editForm.name,
        avatar: this.editForm.avatar,
      });
    },
    handleAvatarError(event) {
      event.currentTarget.src = this.fallbackAvatar;
      if (this.editForm.avatar.path === this.fallbackAvatar) return;
      this.editForm.avatar = {
        ...this.editForm.avatar,
        path: this.fallbackAvatar,
      };
      this.emitAssistantLoaded();
    },
    handleWelcomeAvatarError(event) {
      this.schoolLogoFailed = true;
      event.currentTarget.src = this.displayAvatar;
    },
    actionLinks(item) {
      const trace = JSON.stringify(item?.subConversions || []);
      return STRUCTURED_ACTIONS.filter(action =>
        action.markers.some(marker => trace.includes(marker)),
      );
    },
    openPage(path) {
      this.$router.push(path);
    },
    newConversation() {
      this.$refs.chat?.createConversion();
    },
    ask(message) {
      if (this.configError) return;
      this.$nextTick(() => this.$refs.chat?.preSend(message));
    },
    async createConversation(message) {
      return createStudentAssistantConversation(message);
    },
    stream(message, conversationId) {
      const chat = this.$refs.chat;
      const historyLength = chat.$refs['session-com']?.getList().length || 0;
      chat.sendEventSource(message, '', historyLength, {
        streamApi: STUDENT_ASSISTANT_CHAT_URL,
        streamData: { conversationId, message },
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this.token}`,
          'x-org-id': this.userInfo.orgId,
          'X-Client-ID': getXClientId(),
        },
      });
    },
  },
};
</script>

<style lang="scss" scoped>
.student-assistant {
  --student-brand: #1559c5;
  --student-title: #1f2329;
  --student-text: #30343b;
  --student-muted: #8a9099;

  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  color: var(--student-text);
  background: #fff;
}

.student-assistant__header {
  display: flex;
  align-items: center;
  flex: 0 0 60px;
  gap: 12px;
  min-height: 60px;
  padding: 0 20px;
  border-bottom: 1px solid #f0f1f3;
  background: rgba(255, 255, 255, 0.96);
}

.student-assistant__new {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  padding: 7px 11px;
  color: #5f6670;
  font-size: 12px;
  cursor: pointer;
  border: 1px solid #e5e8ed;
  border-radius: 10px;
  background: #fff;

  &:hover,
  &:focus-visible {
    color: var(--student-brand);
    border-color: #b9d0ed;
    background: #f5f8fd;
    outline: none;
  }
}

.student-assistant__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  flex-direction: column;
  padding: 40px 24px;
  color: #66758a;
  text-align: center;
  background: #f7f9fc;

  img {
    width: 68px;
    height: 68px;
    object-fit: contain;
  }

  h2 {
    margin: 18px 0 8px;
    color: #253d5d;
  }

  p {
    margin: 0 0 22px;
  }
}

.student-welcome-intro {
  width: min(860px, 100%);
  margin: 0 auto;
  text-align: center;

  img {
    display: block;
    width: 64px;
    height: 64px;
    margin: 0 auto;
    object-fit: contain;
  }

  h1 {
    margin: 14px 0 0;
    color: var(--student-title);
    font-size: clamp(32px, 2.25vw, 38px);
    font-weight: 650;
    line-height: 1.25;
    letter-spacing: -0.6px;
  }

  p {
    margin: 8px 0 0;
    color: var(--student-muted);
    font-size: 15px;
    line-height: 1.7;
  }
}

.student-welcome-followup {
  width: 100%;
}

.student-suggestions {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;

  button {
    padding: 7px 12px;
    color: #50627a;
    font-size: 13px;
    line-height: 18px;
    cursor: pointer;
    border: 1px solid #e3e8ef;
    border-radius: 999px;
    background: #f7f9fc;
    transition:
      color 0.18s ease,
      border-color 0.18s ease,
      background 0.18s ease;

    &:hover,
    &:focus-visible {
      color: var(--student-brand);
      border-color: #b9d0ed;
      background: #eef5fd;
      outline: none;
    }
  }
}

.student-assistant__tip {
  margin: 18px 0 0;
  color: #a2a7af;
  font-size: 11px;
  text-align: center;
}

.student-answer-links {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;

  button {
    padding: 4px 0;
    color: #526e91;
    font-size: 12px;
    cursor: pointer;
    border: 0;
    background: transparent;

    i {
      margin-left: 2px;
      font-size: 10px;
    }

    &:hover,
    &:focus-visible {
      color: var(--student-brand);
      outline: none;
    }
  }
}

::v-deep .agent-chat {
  flex: 1;
  min-height: 0;
  width: 100%;

  > .el-main {
    padding: 0 !important;
    border-radius: 0;
    background: #fff;
  }

  .smart-center {
    box-sizing: border-box;
  }

  .editable-box {
    box-sizing: border-box;
    overflow: hidden;
    border-color: #e3e7ed;
    background: #fff;
  }

  .editable-wp-right {
    box-sizing: border-box;
  }

  .aibase-textarea {
    color: var(--student-text);
    font-size: 15px;
    line-height: 24px;
    outline: none;
  }

  .editable--placeholder {
    color: #9aa0a9;
    font-size: 14px;
    line-height: 24px;
  }

  .edtable--wrap {
    width: 100%;
    justify-content: flex-end;

    &::before {
      content: '＋  校园服务';
      margin-right: auto;
      color: #788493;
      font-size: 12px;
    }
  }

  .editable-send-btn {
    width: 36px;
    height: 36px;
    padding: 0;
    color: #fff;
    border: 0;
    background: var(--student-brand);

    &:hover,
    &:focus {
      color: #fff;
      background: #0f4fae !important;
    }
  }
}

::v-deep .agent-chat.is-welcome {
  .smart-center {
    align-items: center;
    justify-content: flex-start;
    padding: clamp(52px, 10vh, 104px) 24px 28px !important;
    overflow-y: auto;
  }

  .echo {
    flex: 0 0 auto;
    width: 100%;
    overflow: visible;
  }

  .center-session {
    display: none !important;
  }

  .center-editable {
    flex: 0 0 auto;
    width: min(860px, 100%);
    margin-top: 24px;
  }

  .chat-input-wrapper,
  .editable-wp {
    width: 100%;
  }

  .editable-box {
    min-height: 116px;
    padding: 16px 18px 12px;
    border-radius: 22px;
    box-shadow: 0 8px 26px rgba(31, 56, 88, 0.07);
  }

  .editable-wp-right,
  .editable-wp-right.multi-line-layout {
    min-height: 86px;
    padding: 0 !important;
    flex-direction: column;
    align-items: stretch;
  }

  .input-and-clear-box {
    width: 100%;
    align-items: flex-start;
  }

  .aibase-textarea {
    min-height: 54px !important;
    max-height: 132px;
    padding: 0 26px 4px 0;
    overflow-y: auto;
  }

  .editable--placeholder {
    max-width: calc(100% - 28px);
    white-space: normal;
  }

  .welcome-after-composer {
    flex: 0 0 auto;
    width: min(860px, 100%);
    margin-top: 18px;
  }
}

::v-deep .agent-chat.is-conversation {
  background: #fff;

  .smart-center {
    padding: 0 24px !important;
  }

  .center-session {
    min-height: 0;
    overflow: hidden;
  }

  .component {
    height: 100%;
  }

  .history-box {
    box-sizing: border-box;
    width: min(900px, 100%);
    margin: 0 auto;
    padding: 22px 20px 76px;
  }

  .center-editable {
    position: sticky;
    z-index: 6;
    bottom: 22px;
    flex: 0 0 auto;
    width: min(860px, calc(100% - 40px));
    margin: 0 auto 22px;
    padding-top: 8px;
    background: linear-gradient(to bottom, rgba(255, 255, 255, 0), #fff 28%);
  }

  .editable-box {
    min-height: 76px;
    padding: 10px 12px 8px;
    border-radius: 18px;
    box-shadow: 0 6px 20px rgba(31, 56, 88, 0.08);
  }

  .editable-wp-right,
  .editable-wp-right.multi-line-layout {
    min-width: 0;
    padding: 0 !important;
    flex-direction: column;
    align-items: stretch;
    gap: 2px;
  }

  .input-and-clear-box {
    width: 100%;
    min-width: 0;
    min-height: 26px;
    align-items: flex-start;
  }

  .aibase-textarea {
    min-width: 0;
    min-height: 24px !important;
    max-height: 96px;
    padding: 0 26px 0 0;
    overflow-y: auto;
  }

  .editable--placeholder {
    max-width: calc(100% - 28px);
    white-space: normal;
  }

  .edtable--wrap {
    height: 32px;
  }

  .session-question .session-item {
    width: fit-content;
    max-width: 78%;
    min-height: 0;
    margin-left: auto;
    padding: 10px 0;
  }

  .session-question .logo {
    display: none;
  }

  .session-question .answer-content {
    padding: 0;
  }

  .session-question .answer-text {
    padding: 9px 14px !important;
    color: #fff;
    border-radius: 16px 5px 16px 16px !important;
    background: #5b72d6 !important;
    box-shadow: none !important;
  }

  .session-answer-wrapper {
    min-height: 0;
    gap: 12px;
    padding: 14px 0 0;
  }

  .session-answer-wrapper > .logo {
    width: 32px;
    height: 32px;
    border-radius: 9px;
    object-fit: contain;
  }

  .session-answer-wrapper .session-wrap {
    min-width: 0;
  }

  .session-answer-wrapper .answer-content {
    padding: 0 0 8px !important;
    color: var(--student-text);
    line-height: 1.75;
    border-radius: 0;
    background: transparent !important;
  }

  .sub-conversion-box {
    gap: 8px;
    margin: 7px 0 12px;
  }

  .sub-conversion-list {
    gap: 7px;
    margin-top: 6px;
    padding: 0;
  }

  .sub-conversion-item {
    overflow: hidden;
    border-color: #e8ebf0;
    border-radius: 10px;
    background: #f7f8fa;
    box-shadow: none;
  }

  .sub-conversion-header {
    min-height: 34px;
    padding: 6px 10px;
    color: #69717d;
    border-bottom-color: #eceef2;
    background: #f7f8fa;
  }

  .sub-conversion-content-wrapper {
    padding: 8px 10px;
    background: #fbfbfc;
  }

  .sub-conversion-content {
    padding: 6px 8px;
    color: #626a75;
    font-size: 12px;
    background: transparent;
  }

  .conversion-time {
    padding: 0;
    color: #9097a1;
    font-size: 11px;
    background: transparent;
  }

  .sub-conversion-footer {
    padding: 5px 10px;
    background: #f7f8fa;
  }

  .answer-operation {
    min-height: 0;
    padding: 4px 0 8px 44px;
  }
}

@media (max-width: 768px) {
  .student-assistant__header {
    flex-basis: 58px;
    min-height: 58px;
    padding: 0 14px;
  }

  .student-assistant__new span {
    display: none;
  }

  .student-welcome-intro {
    h1 {
      font-size: 30px;
    }

    p {
      font-size: 14px;
    }
  }

  .student-suggestions {
    justify-content: flex-start;
  }

  ::v-deep .agent-chat.is-welcome .smart-center {
    padding: 42px 16px 22px !important;
  }

  ::v-deep .agent-chat.is-welcome .editable-box {
    min-height: 108px;
    padding: 14px 15px 10px;
    border-radius: 20px;
  }

  ::v-deep .agent-chat.is-conversation {
    .smart-center {
      padding: 0 12px !important;
    }

    .history-box {
      padding: 14px 4px 70px;
    }

    .center-editable {
      bottom: 16px;
      width: calc(100% - 8px);
      margin-bottom: 16px;
    }

    .session-answer-wrapper > .logo {
      width: 28px;
      height: 28px;
    }

    .answer-operation {
      padding-left: 40px;
    }
  }
}
</style>
