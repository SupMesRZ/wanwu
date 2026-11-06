<template>
  <div
    class="student-assistant"
    :class="{
      'student-assistant--conversation': hasConversation,
      'student-assistant--motion-paused': motionPaused,
    }"
    :style="ambientMotionStyle"
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

    <div
      v-if="!configError"
      :class="['digital-human', `digital-human--${expression}`]"
      :style="digitalHumanMotionStyle"
    >
      <span class="digital-human__aura" aria-hidden="true"></span>
      <span class="digital-human__shadow" aria-hidden="true"></span>
      <button
        type="button"
        class="digital-human__character"
        :aria-label="`和${roleProfile.assistantName}打个招呼`"
        title="点我打个招呼"
        @click="setExpression('happy', 1500)"
      >
        <img
          v-if="digitalHumanFailed"
          class="digital-human__fallback"
          :src="displayAvatar"
          alt=""
          @error="handleAvatarError"
        />
        <template v-else>
          <img
            class="digital-human__body"
            src="@/assets/imgs/hexiaozhi-digital-human.png"
            alt=""
            @error="digitalHumanFailed = true"
          />
          <img
            class="digital-human__antenna"
            src="@/assets/imgs/hexiaozhi-digital-human.png"
            alt=""
          />
          <img
            class="digital-human__arm digital-human__arm--left"
            src="@/assets/imgs/hexiaozhi-digital-human.png"
            alt=""
          />
          <img
            class="digital-human__arm digital-human__arm--right"
            src="@/assets/imgs/hexiaozhi-digital-human.png"
            alt=""
          />
          <span class="digital-human__screen" aria-hidden="true">
            <svg class="digital-human__eyes" viewBox="0 0 120 64">
              <defs>
                <filter
                  :id="`hexiaozhi-eye-glow-${_uid}`"
                  x="-60%"
                  y="-60%"
                  width="220%"
                  height="220%"
                  color-interpolation-filters="sRGB"
                >
                  <feGaussianBlur stdDeviation="2" />
                  <feMerge>
                    <feMergeNode />
                    <feMergeNode in="SourceGraphic" />
                    <feMergeNode in="SourceGraphic" />
                  </feMerge>
                </filter>
              </defs>
              <g
                class="digital-human__gaze"
                :filter="`url(#hexiaozhi-eye-glow-${_uid})`"
              >
                <g
                  v-for="side in ['left', 'right']"
                  :key="side"
                  :transform="`translate(${side === 'left' ? 32 : 88}, 30)`"
                >
                  <g class="digital-human__eye">
                    <path
                      v-if="['happy', 'answering'].includes(expression)"
                      class="digital-human__eye-shape"
                      d="M -13 3 Q 0 -16 13 3"
                    />
                    <text
                      v-else-if="expression === 'doubt' && side === 'right'"
                      class="digital-human__eye-question"
                      x="0"
                      y="11"
                    >
                      ?
                    </text>
                    <path
                      v-else-if="expression === 'thinking'"
                      class="digital-human__eye-shape"
                      d="M -12 -4 H 12 M -10 -3 Q 0 13 10 -3"
                    />
                    <path
                      v-else-if="expression === 'success'"
                      class="digital-human__eye-shape"
                      d="M -12 6 L 0 -9 L 12 6"
                    />
                    <g v-else-if="expression === 'unrecognized'">
                      <circle class="digital-human__eye-shape" r="13" />
                      <circle class="digital-human__eye-shape" r="6" />
                      <circle class="digital-human__eye-core" r="1.5" />
                    </g>
                    <g v-else>
                      <rect
                        class="digital-human__eye-shape"
                        x="-14"
                        y="-14"
                        width="28"
                        height="28"
                        rx="7"
                      />
                      <rect
                        class="digital-human__eye-core"
                        :x="side === 'left' ? 9 : -13"
                        y="-5"
                        width="4"
                        height="10"
                        rx="1.5"
                      />
                    </g>
                  </g>
                </g>
              </g>
            </svg>
            <span class="digital-human__mouth"></span>
          </span>
        </template>
      </button>
      <span
        class="digital-human__sparkle digital-human__sparkle--left"
        aria-hidden="true"
      >
        ✦
      </span>
      <span
        class="digital-human__sparkle digital-human__sparkle--right"
        aria-hidden="true"
      >
        ✧
      </span>
    </div>

    <section v-if="configError" class="student-assistant__empty">
      <img
        :src="displayAvatar"
        :alt="roleProfile.assistantName"
        @error="handleAvatarError"
      />
      <h2>{{ roleProfile.assistantName }}暂未开放</h2>
      <p>{{ configError }}</p>
      <div v-if="isStudent">
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
      :input-placeholder="`问问${roleProfile.assistantName}……`"
      :edit-form="chatEditForm"
      :campus-student-adapter="campusStudentAdapter"
      :visible-clear-history="false"
      :visible-upload="false"
      :visible-voice="true"
      :max-pic-num="0"
      :max-file-num="0"
      @conversation-state="hasConversation = $event"
      @voice-state="handleVoiceState"
    >
      <template #welcome>
        <section class="student-welcome-intro" aria-labelledby="welcome-title">
          <h1 id="welcome-title">你好，我是河小智</h1>
          <p>{{ roleProfile.welcome }}</p>
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
import {
  campusRoleAssistantChatUrl,
  createCampusRoleAssistantConversation,
  getCampusRoleAssistant,
} from '@/api/campusBusiness';
import motionTimeline from './motion/build/timeline.json';

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
  props: {
    role: { type: String, default: 'student' },
  },
  data() {
    return {
      configError: '',
      hasConversation: false,
      schoolLogoFailed: false,
      digitalHumanFailed: false,
      motionPaused: false,
      expression: 'idle',
      expressionTimer: null,
      editForm: {
        name: CAMPUS_ROLES[this.role]?.assistantName || '河小智助手',
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
  mounted() {
    this.handleMotionVisibility();
    document.addEventListener('visibilitychange', this.handleMotionVisibility);
  },
  beforeDestroy() {
    clearTimeout(this.expressionTimer);
    document.removeEventListener(
      'visibilitychange',
      this.handleMotionVisibility,
    );
  },
  computed: {
    ...mapGetters('user', ['token', 'userInfo', 'commonInfo']),
    ...mapGetters('app', ['sessionStatus']),
    roleProfile() {
      return CAMPUS_ROLES[this.role] || CAMPUS_ROLES.student;
    },
    isStudent() {
      return this.role === 'student';
    },
    quickQuestions() {
      return this.roleProfile.examples;
    },
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
    digitalHumanMotionStyle() {
      const duration = this.motionDuration;
      return {
        '--breath-duration': `${duration / 2}s`,
        '--expression-duration': `${duration}s`,
        '--float-duration': `${duration}s`,
      };
    },
    motionDuration() {
      return motionTimeline.segments[motionTimeline.segments.length - 1]
        .endExclusive;
    },
    ambientMotionStyle() {
      return { '--ambient-duration': `${this.motionDuration}s` };
    },
  },
  watch: {
    sessionStatus(value, previous) {
      if (value === 0) {
        this.setExpression('thinking');
        this.expressionTimer = setTimeout(() => {
          if (this.sessionStatus === 0) this.setExpression('answering');
        }, 650);
      } else if (previous === 0) {
        this.setExpression('happy', 1500);
      }
    },
  },
  methods: {
    handleMotionVisibility() {
      this.motionPaused = document.hidden;
    },
    setExpression(expression, duration = 0) {
      clearTimeout(this.expressionTimer);
      this.expression = expression;
      if (duration) {
        this.expressionTimer = setTimeout(() => {
          this.expression = 'idle';
        }, duration);
      }
    },
    handleVoiceState(expression) {
      const duration = {
        success: 1200,
        doubt: 1500,
        unrecognized: 1800,
      }[expression];
      this.setExpression(expression, duration);
    },
    async loadAssistant() {
      try {
        const res = this.isStudent
          ? await getStudentAssistant()
          : await getCampusRoleAssistant(this.role);
        if (res.code !== 0 || !res.data) {
          this.configError =
            '管理员完成配置并发布后即可使用，你仍可查看个人校园信息。';
          return;
        }
        this.editForm.name = res.data.name || this.roleProfile.assistantName;
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
      return this.isStudent
        ? createStudentAssistantConversation(message)
        : createCampusRoleAssistantConversation(this.role, message);
    },
    stream(message, conversationId) {
      const chat = this.$refs.chat;
      const historyLength = chat.$refs['session-com']?.getList().length || 0;
      chat.sendEventSource(message, '', historyLength, {
        streamApi: this.isStudent
          ? STUDENT_ASSISTANT_CHAT_URL
          : campusRoleAssistantChatUrl(this.role),
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

  position: relative;
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  color: var(--student-text);
  overflow: hidden;
  isolation: isolate;
  background: linear-gradient(135deg, #fafdff 0%, #f1f8ff 48%, #fbf8ff 100%);

  &::before,
  &::after {
    position: absolute;
    z-index: 0;
    pointer-events: none;
    content: '';
  }

  &::before {
    inset: -22%;
    background:
      radial-gradient(
        circle at 15% 28%,
        rgba(74, 157, 255, 0.4) 0 10%,
        transparent 32%
      ),
      radial-gradient(
        circle at 78% 18%,
        rgba(177, 145, 255, 0.32) 0 9%,
        transparent 30%
      ),
      radial-gradient(
        circle at 72% 82%,
        rgba(75, 210, 224, 0.26) 0 8%,
        transparent 28%
      ),
      radial-gradient(
        circle at 28% 86%,
        rgba(126, 185, 255, 0.3) 0 7%,
        transparent 26%
      );
    opacity: 1;
    animation: campus-background-breathe var(--ambient-duration) ease-in-out
      infinite;
  }

  &::after {
    inset: -30% -18%;
    background:
      linear-gradient(
        116deg,
        transparent 35%,
        rgba(255, 255, 255, 0.78) 44%,
        rgba(150, 210, 255, 0.32) 48%,
        transparent 57%
      ),
      radial-gradient(
        circle at 84% 72%,
        rgba(255, 255, 255, 0.55) 0 1.2%,
        transparent 1.8%
      ),
      radial-gradient(
        circle at 9% 66%,
        rgba(255, 255, 255, 0.45) 0 0.9%,
        transparent 1.6%
      );
    opacity: 0.78;
    transform: rotate(-5deg) translate3d(-3%, 0, 0);
    animation: campus-light-drift calc(var(--ambient-duration) * 1.5)
      ease-in-out infinite;
  }

  > * {
    position: relative;
    z-index: 1;
  }
}

.student-assistant__header {
  backdrop-filter: blur(12px);
}

.student-assistant--conversation::before {
  opacity: 0.58;
}

.student-assistant--conversation::after {
  opacity: 0.36;
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
  padding-top: 135px;
  text-align: center;

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

.digital-human {
  position: absolute;
  z-index: 8;
  top: 104px;
  left: 50%;
  width: 192px;
  height: 192px;
  font-size: 192px;
  transform: translateX(-50%);
  transition:
    width 0.2s ease,
    height 0.2s ease,
    top 0.2s ease;
}

.digital-human__character {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  padding: 0;
  font-size: inherit;
  border: 0;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transform-origin: 50% 62%;
  animation: digital-float var(--float-duration) ease-in-out infinite;

  &:focus-visible {
    outline: 2px solid #76a9ff;
    outline-offset: 8px;
  }

  > img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: contain;
    pointer-events: none;
    user-select: none;
  }
}

// ponytail: rigid PNG parts; use pose footage if articulated joints are needed.
.digital-human__body {
  clip-path: inset(22% 0 0 0);
  mask-image:
    linear-gradient(#000 0 0),
    radial-gradient(ellipse 8% 6% at 25% 65%, #000 99%, transparent 100%),
    radial-gradient(ellipse 8% 6% at 75% 65%, #000 99%, transparent 100%);
  mask-composite: exclude;
}

.digital-human__antenna {
  clip-path: inset(0 0 78% 0);
  transform-origin: 50% 22%;
  animation: digital-antenna var(--float-duration) ease-in-out infinite;
}

.digital-human__arm {
  animation: digital-wave var(--float-duration) ease-in-out infinite;
  mask-image: linear-gradient(#000 69%, transparent 69%);

  &--left {
    clip-path: ellipse(8% 6% at 25% 65%);
    transform-origin: 33% 65%;
  }

  &--right {
    clip-path: ellipse(8% 6% at 75% 65%);
    transform-origin: 67% 65%;
    animation-name: digital-resting-arm;
  }
}

.digital-human__aura,
.digital-human__shadow,
.digital-human__sparkle {
  position: absolute;
  pointer-events: none;
}

.digital-human__aura {
  inset: -20%;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(191, 236, 255, 0.6),
    rgba(221, 230, 255, 0.28) 45%,
    transparent 70%
  );
  animation: campus-background-breathe var(--float-duration) ease-in-out
    infinite;
}

.digital-human__shadow {
  bottom: -1%;
  left: 14%;
  width: 72%;
  height: 12%;
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 50%;
  background: radial-gradient(
    ellipse,
    rgba(74, 190, 255, 0.45),
    rgba(166, 224, 255, 0.15) 45%,
    transparent 72%
  );
  box-shadow:
    0 0 0 7px rgba(255, 255, 255, 0.3),
    0 0 18px rgba(109, 202, 255, 0.18);
  animation: digital-shadow var(--float-duration) ease-in-out infinite;
}

.digital-human__sparkle {
  color: #fff;
  font-size: 23px;
  text-shadow: 0 0 12px #8dceff;
  animation: digital-sparkle var(--float-duration) ease-in-out infinite;

  &--left {
    top: 35%;
    left: -8%;
  }
  &--right {
    top: 18%;
    right: -5%;
    animation-delay: calc(var(--float-duration) / -2);
  }
}

.student-assistant--conversation > .digital-human {
  top: 72px;
  right: 22px;
  left: auto;
  width: 104px;
  height: 104px;
  font-size: 104px;
  transform: none;

  .digital-human__sparkle {
    display: none;
  }
}

.digital-human__screen {
  position: absolute;
  top: 32%;
  left: 28.5%;
  width: 43%;
  height: 24.5%;
  overflow: hidden;
  border-radius: 42% 42% 46% 46%;
  background: radial-gradient(circle at 50% 120%, #183e6c, #06152d 58%);
  box-shadow: inset 0 0 0.05682em rgba(51, 154, 255, 0.2);

  &::after {
    position: absolute;
    top: 8%;
    left: 12%;
    width: 48%;
    height: 16%;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.1);
    content: '';
    transform: rotate(-7deg);
  }
}

.digital-human__eyes {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.digital-human__eye {
  transform-origin: 0 0;
}

.digital-human__eye-shape {
  fill: none;
  stroke: #9afaff;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.digital-human__eye-core,
.digital-human__eye-question {
  fill: #e7ffff;
}

.digital-human__eye-question {
  font-family: Arial, sans-serif;
  font-size: 34px;
  font-weight: 700;
  text-anchor: middle;
}

.digital-human__mouth {
  position: absolute;
  bottom: 18%;
  left: 42%;
  width: 16%;
  height: 10%;
  border-bottom: 0.01136em solid #8feaff;
  border-radius: 0 0 50% 50%;
  box-shadow: 0 0.01136em 0.02273em rgba(56, 191, 255, 0.55);
  transition: all 0.15s ease-out;
}

.digital-human--idle .digital-human__gaze {
  animation: digital-eye-gaze 16s infinite;
}

.digital-human--idle .digital-human__eye,
.digital-human--listening .digital-human__eye {
  animation: digital-eye-blink 4.2s ease-in-out infinite;
}

.digital-human--idle .digital-human__mouth {
  animation: digital-idle-mouth var(--expression-duration) ease-in-out infinite;
}

.digital-human--happy .digital-human__mouth,
.digital-human--success .digital-human__mouth {
  bottom: 15%;
  left: 38%;
  width: 24%;
  height: 15%;
  border-width: 0 0 0.01705em;
}

.digital-human--answering .digital-human__mouth {
  animation: digital-talk 0.45s ease-in-out infinite alternate;
}

.digital-human--thinking {
  .digital-human__mouth {
    height: 0;
    border-radius: 0;
  }
}

.digital-human--listening .digital-human__eye-core {
  animation: digital-listen 0.9s ease-in-out infinite alternate;
}

.digital-human--doubt .digital-human__mouth {
  left: 44%;
  width: 13%;
  transform: rotate(-12deg);
}

.digital-human--unrecognized .digital-human__mouth {
  bottom: 14%;
  left: 44%;
  width: 12%;
  height: 15%;
  border: 0.01136em solid #8feaff;
  border-radius: 50%;
}

@keyframes digital-float {
  0%,
  100% {
    transform: translateY(0) rotate(-2deg) scale(0.98);
  }
  20% {
    transform: translateY(-5%) rotate(-1deg) scale(0.99);
  }
  40% {
    transform: translateY(-11%) rotate(1deg) scale(0.995);
  }
  50% {
    transform: translateY(-14%) rotate(2deg) scale(1);
  }
  75% {
    transform: translateY(-6%) rotate(0) scale(0.99);
  }
}

@keyframes digital-wave {
  0%,
  100% {
    transform: rotate(0);
  }
  20% {
    transform: rotate(18deg);
  }
  40% {
    transform: rotate(48deg);
  }
  46%,
  58% {
    transform: rotate(65deg);
  }
  52% {
    transform: rotate(48deg);
  }
  75% {
    transform: rotate(16deg);
  }
}

@keyframes digital-resting-arm {
  0%,
  100% {
    transform: rotate(0);
  }
  45%,
  55% {
    transform: rotate(-12deg);
  }
}

@keyframes digital-antenna {
  0%,
  100% {
    transform: rotate(-3deg);
  }
  50% {
    transform: rotate(5deg);
  }
}

@keyframes digital-shadow {
  0%,
  100% {
    opacity: 0.85;
    transform: scale(1);
  }
  50% {
    opacity: 0.4;
    transform: scale(0.7);
  }
}

@keyframes digital-sparkle {
  0%,
  100% {
    opacity: 0.25;
    transform: scale(0.8);
  }
  50% {
    opacity: 0.95;
    transform: scale(1.1);
  }
}

@keyframes campus-background-breathe {
  0%,
  100% {
    opacity: 0.72;
    transform: scale(0.96);
  }

  50% {
    opacity: 1;
    transform: scale(1.08);
  }
}

@keyframes campus-light-drift {
  0%,
  100% {
    opacity: 0.5;
    transform: rotate(-5deg) translate3d(-6%, 1%, 0);
  }

  50% {
    opacity: 0.9;
    transform: rotate(-2deg) translate3d(6%, -2%, 0);
  }
}

@keyframes digital-eye-blink {
  0%,
  90%,
  97.142857%,
  100% {
    transform: scaleY(1);
  }

  93.571429% {
    transform: scaleY(0.06);
  }
}

@keyframes digital-eye-gaze {
  0%,
  9%,
  21%,
  34%,
  46%,
  59%,
  71%,
  84%,
  96%,
  100% {
    transform: translate(0, 0);
  }
  10%,
  20% {
    transform: translate(-3px, 0);
  }
  35%,
  45% {
    transform: translate(3px, 0);
  }
  60%,
  70% {
    transform: translate(0, -3px);
  }
  85%,
  95% {
    transform: translate(0, 3px);
  }
}

@keyframes digital-idle-mouth {
  0%,
  34%,
  51%,
  84%,
  100% {
    bottom: 18%;
    left: 42%;
    width: 16%;
    height: 10%;
    border: 0;
    border-bottom: 0.01136em solid #8feaff;
    border-radius: 0 0 50% 50%;
  }

  36%,
  49% {
    bottom: 15%;
    left: 38%;
    width: 24%;
    height: 15%;
    border: 0;
    border-bottom: 0.01705em solid #8feaff;
    border-radius: 0 0 50% 50%;
  }

  86%,
  99% {
    bottom: 15%;
    left: 45%;
    width: 10%;
    height: 13%;
    border: 0.01136em solid #8feaff;
    border-radius: 50%;
  }
}

@keyframes digital-listen {
  to {
    opacity: 0.5;
  }
}

@keyframes digital-talk {
  to {
    bottom: 14%;
    left: 45%;
    width: 10%;
    height: 16%;
    border: 0.01136em solid #8feaff;
    border-radius: 50%;
  }
}

.student-assistant--motion-paused {
  &::before,
  &::after,
  .digital-human * {
    animation-play-state: paused;
  }
}

@supports not (mask-composite: exclude) {
  .digital-human__body {
    clip-path: none;
    mask-image: none;
  }
  .digital-human__arm,
  .digital-human__antenna {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .digital-human *,
  .student-assistant::before,
  .student-assistant::after {
    animation: none;
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
  background: transparent;

  > .el-main {
    padding: 0 !important;
    border-radius: 0;
    background: transparent;
  }

  .smart-center {
    box-sizing: border-box;
    background: transparent;
  }

  .echo,
  .welcome-after-composer,
  .center-session,
  .component {
    background: transparent;
  }

  .editable-box {
    box-sizing: border-box;
    overflow: hidden;
    border-color: #e3e7ed;
    background: rgba(255, 255, 255, 0.68);
    box-shadow: 0 12px 34px rgba(58, 111, 170, 0.1);
    backdrop-filter: blur(18px);
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
  background: transparent;

  .smart-center {
    padding: 0 24px !important;
    background: transparent;
  }

  .center-session {
    min-height: 0;
    overflow: hidden;
    background: transparent;
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
    background: linear-gradient(
      to bottom,
      rgba(255, 255, 255, 0),
      rgba(255, 255, 255, 0.82) 28%
    );
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
  .digital-human {
    top: 94px;
    width: 140px;
    height: 140px;
    font-size: 140px;
  }

  .student-assistant--conversation > .digital-human {
    top: 66px;
    right: 10px;
    width: 72px;
    height: 72px;
    font-size: 72px;
  }

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
