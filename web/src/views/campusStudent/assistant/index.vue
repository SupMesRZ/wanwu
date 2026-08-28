<template>
  <div class="student-assistant">
    <div class="student-assistant__header">
      <button
        type="button"
        class="student-assistant__new"
        title="新建对话"
        aria-label="新建对话"
        @click="newConversation"
      >
        <i class="el-icon-plus"></i>
      </button>
      <slot name="header-title"></slot>
    </div>

    <el-alert
      v-if="configError"
      :title="configError"
      type="error"
      :closable="false"
      show-icon
      class="student-assistant__error"
    />

    <AgentChat
      v-else
      ref="chat"
      chat-type="chat"
      type="campusStudent"
      :edit-form="editForm"
      :campus-student-adapter="campusStudentAdapter"
      :max-pic-num="0"
      :max-file-num="0"
    >
      <template #welcome>
        <slot name="welcome"></slot>
      </template>
    </AgentChat>
  </div>
</template>

<script>
import AgentChat from '@/views/agent/components/chat.vue';
import { mapGetters } from 'vuex';
import { getXClientId } from '@/utils/util';
import {
  createStudentAssistantConversation,
  getStudentAssistant,
  STUDENT_ASSISTANT_CHAT_URL,
} from '@/api/campusStudent';

export default {
  name: 'CampusStudentAssistant',
  components: { AgentChat },
  data() {
    return {
      configError: '',
      editForm: {
        avatar: {},
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
    ...mapGetters('user', ['token', 'userInfo']),
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
          this.configError = res.msg || '学生助手尚未完成配置，请联系管理员。';
          return;
        }
        this.editForm.avatar = res.data.avatar || {};
        this.editForm.prologue = res.data.prologue || '';
        this.editForm.recommendQuestion = (
          res.data.recommendQuestion || []
        ).map(value => ({ value }));
      } catch (_error) {
        this.configError = '学生助手尚未完成配置，请联系管理员。';
      }
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
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.student-assistant__header {
  min-height: 64px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #ebeef5;
}

.student-assistant__new {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 8px;
  color: #606266;
  background: #f5f7fa;
  cursor: pointer;
}

.student-assistant__error {
  margin: 24px;
  width: auto;
}

::v-deep .full-content {
  min-height: 0;
  flex: 1;
}
</style>
