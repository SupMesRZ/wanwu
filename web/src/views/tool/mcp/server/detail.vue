<template>
  <div class="mcp-detail page-wrapper" id="timeScroll">
    <span class="back" @click="back">
      {{ $t('menu.back') + $t('menu.resource') }}
    </span>
    <div class="mcp-title">
      <img
        class="logo"
        :src="
          detail.avatar && detail.avatar.path
            ? avatarSrc(detail.avatar.path)
            : defaultAvatar
        "
        alt=""
      />
      <div :class="['info', { fold: foldStatus }]">
        <p class="name">{{ detail.name }}</p>
        <p v-if="detail.desc && detail.desc.length > 260" class="desc">
          {{ foldStatus ? detail.desc : detail.desc.slice(0, 268) + '...' }}
          <span class="arrow" v-show="detail.desc.length > 260" @click="fold">
            {{
              foldStatus ? $t('common.button.fold') : $t('common.button.detail')
            }}
          </span>
        </p>
        <p v-else class="desc">{{ detail.desc }}</p>
      </div>
    </div>
    <div class="mcp-main">
      <div class="info">
        <!-- tabs -->
        <div class="tabs">
          <div
            :class="['tab', { active: tabActive === 0 }]"
            @click="tabActive = 0"
          >
            SSE URL及工具
          </div>
          <div style="display: inline-block">
            <div
              :class="['tab', { active: tabActive === 1 }]"
              @click="tabActive = 1"
            >
              Streamable HTTP
            </div>
          </div>
        </div>

        <div v-if="tabActive === 0">
          <div class="tool bg-border">
            <div class="tool-item">
              <p class="title">SSE URL:</p>
              <el-input
                class="sse-url"
                v-model="detail.sseUrl"
                :readonly="true"
                style="margin-right: 20px"
              />
            </div>
          </div>
          <div class="tool bg-border">
            <div class="tool-item">
              <p class="title">{{ $t('tool.server.detail.example') }}</p>
              <el-input
                class="schema-textarea"
                v-model="detail.sseExample"
                :readonly="true"
                type="textarea"
              />
            </div>
          </div>
        </div>
        <div v-if="tabActive === 1">
          <div class="tool bg-border">
            <div class="tool-item">
              <p class="title">Streamable HTTP:</p>
              <el-input
                class="sse-url"
                v-model="detail.streamableUrl"
                :readonly="true"
                style="margin-right: 20px"
              />
            </div>
          </div>
          <div class="tool bg-border">
            <div class="tool-item">
              <p class="title">{{ $t('tool.server.detail.example') }}</p>
              <el-input
                class="schema-textarea"
                v-model="detail.streamableExample"
                :readonly="true"
                type="textarea"
              />
            </div>
          </div>
        </div>
        <div class="tool bg-border">
          <div class="tool-item">
            <div style="display: flex; align-items: center">
              <p class="title">{{ $t('tool.server.bind.title') }}</p>
              <el-tooltip
                style="margin-left: 3px"
                effect="dark"
                :content="$t('tool.server.bind.hint')"
                placement="right"
                popper-class="tooltip"
              >
                <span class="el-icon-question question-tips" />
              </el-tooltip>
            </div>
            <div>
              <el-button
                v-if="detail.kind !== 'campus'"
                size="mini"
                @click="$refs.toolDialog.showDialog(detail)"
              >
                {{ $t('tool.server.bind.action') }}
              </el-button>
              <el-button
                v-if="detail.kind !== 'campus'"
                size="mini"
                @click="$refs.addDialog.showToolDialog(mcpServerId)"
              >
                {{ $t('common.button.add') }}
              </el-button>
            </div>
            <el-table :data="detail.tools" style="width: 100%">
              <el-table-column
                :label="$t('tool.server.bind.methodName')"
                prop="methodName"
                width="250"
              >
                <template #default="scope">
                  <el-input
                    v-if="detail.kind === 'campus'"
                    :value="displayToolName(scope.row)"
                    readonly
                  />
                  <el-input
                    v-else
                    :readonly="!scope.row.isEditing || detail.kind === 'campus'"
                    v-model="scope.row.methodName"
                    :placeholder="
                      $t('common.input.placeholder') +
                      $t('tool.server.bind.methodName')
                    "
                  ></el-input>
                </template>
              </el-table-column>
              <el-table-column
                :label="$t('tool.server.bind.name')"
                prop="name"
                width="220"
              />
              <el-table-column :label="$t('tool.server.bind.type')" width="100">
                <template #default="scope">
                  <div>
                    {{ appTypeMap[scope.row.type] || scope.row.type }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column :label="$t('tool.server.bind.desc')" prop="desc">
                <template #default="scope">
                  <el-input
                    v-if="detail.kind === 'campus'"
                    :value="displayToolDesc(scope.row)"
                    readonly
                  />
                  <el-input
                    v-else
                    :readonly="!scope.row.isEditing || detail.kind === 'campus'"
                    v-model="scope.row.desc"
                    :placeholder="
                      $t('common.input.placeholder') +
                      $t('tool.server.bind.desc')
                    "
                  ></el-input>
                </template>
              </el-table-column>
              <el-table-column
                v-if="detail.kind !== 'campus'"
                :label="$t('tool.server.bind.operate')"
                width="200"
              >
                <template #default="scope">
                  <el-button
                    v-if="scope.row.isEditing"
                    size="mini"
                    type="primary"
                    @click="handleEditTool(scope.row)"
                  >
                    {{ $t('common.confirm.confirm') }}
                  </el-button>
                  <el-button
                    v-else
                    size="mini"
                    @click="scope.row.isEditing = true"
                  >
                    {{ $t('common.button.edit') }}
                  </el-button>
                  <el-button size="mini" @click="handleDeleteTool(scope.row)">
                    {{ $t('common.button.delete') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <div v-if="detail.kind !== 'campus'" class="tool bg-border">
          <div class="tool-item">
            <p class="title">{{ $t('tool.server.detail.apiKey') }}</p>
            <el-button
              style="width: 100px"
              size="mini"
              type="primary"
              :disabled="detail.hasCustom"
              @click="handleCreateApiKey"
            >
              {{ $t('tool.server.detail.action') }}
            </el-button>
            <el-table :data="apiKeyList" style="width: 100%">
              <el-table-column
                :label="$t('tool.server.detail.key')"
                prop="apiKey"
                width="300"
              ></el-table-column>
              <el-table-column
                :label="$t('tool.server.detail.createTime')"
                prop="createdAt"
              />
              <el-table-column
                :label="$t('tool.server.detail.operate')"
                width="200"
              >
                <template slot-scope="scope">
                  <copyIcon
                    :text="scope.row.apiKey"
                    :showIcon="false"
                    size="mini"
                  />
                  <el-button size="mini" @click="handleDeleteApiKey(scope.row)">
                    {{ $t('common.button.delete') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>
    </div>
    <addDialog ref="addDialog" @handleFetch="fetchList" />
    <toolDialog ref="toolDialog" @handleFetch="fetchList" />
  </div>
</template>
<script>
import { getServer, editServerTool, deleteServerTool } from '@/api/mcp';
import { createApiKey, delApiKey, getApiKeyList } from '@/api/appspace';
import { avatarSrc } from '@/utils/util';
import CopyIcon from '@/components/copyIcon.vue';
import addDialog from '@/views/tool/tool/custom/addDialog.vue';
import toolDialog from './toolDialog.vue';

const APPTYPE_MCPSERVER = 'mcpserver';
const CAMPUS_TOOL_LABELS = {
  query_my_schedule: {
    name: '查询我的课程',
    desc: '查询当前登录学生本人的今日、指定日期或本周课程安排',
  },
  query_my_exam_schedule: {
    name: '查询我的考试',
    desc: '查询当前登录学生本人的考试安排',
  },
  query_my_score: {
    name: '查询我的成绩',
    desc: '查询当前登录学生本人的课程成绩',
  },
  query_my_leave_records: {
    name: '查询我的请假记录',
    desc: '查询当前登录学生本人的请假记录和审批状态',
  },
  query_my_learning_summary: {
    name: '学习情况分析',
    desc: '获取当前登录学生本人的成绩概览和学习分析',
  },
  query_my_teaching_schedule: {
    name: '查询我的授课课表',
    desc: '查询当前登录教师本人的授课课程、班级、时间与地点',
  },
  query_my_teaching_classes: {
    name: '查询我的授课班级',
    desc: '查询当前登录教师负责的课程和班级',
  },
  query_my_invigilation: {
    name: '查询我的监考安排',
    desc: '查询当前登录教师本人的监考时间与地点',
  },
  query_available_classrooms: {
    name: '查询可用教室',
    desc: '按日期和时间查询可用教室',
  },
  query_my_adjustment_records: {
    name: '查询我的调课记录',
    desc: '查询当前登录教师本人的调课申请及审批状态',
  },
  create_course_adjustment_request: {
    name: '提交调课申请',
    desc: '在教师明确确认后提交调课申请',
  },
  query_course_operation_overview: {
    name: '查询课程运行概览',
    desc: '查询本周课程运行情况与异常指标',
  },
  query_pending_adjustment_requests: {
    name: '查询调课申请',
    desc: '默认查询待审批申请，也可查询全部或指定状态的历史记录',
  },
  query_room_utilization: {
    name: '查询教室使用情况',
    desc: '查询教学楼教室使用率',
  },
  query_grade_submission_progress: {
    name: '查询成绩提交进度',
    desc: '查询课程成绩提交完成情况',
  },
  query_teaching_service_statistics: {
    name: '查询教学服务统计',
    desc: '查询教学服务办理量、时效与满意度',
  },
  review_course_adjustment_request: {
    name: '审批调课申请',
    desc: '批准或驳回指定的待审批调课申请',
  },
};
export default {
  name: 'McpServiceServerDetail',
  components: { CopyIcon, addDialog, toolDialog },
  data() {
    return {
      tabActive: 0,
      defaultAvatar: require('@/assets/imgs/mcp_active.svg'),
      mcpServerId: '',
      detail: {},
      apiKeyList: [],
      foldStatus: false,
    };
  },
  watch: {
    $route: {
      handler() {
        this.initData();
      },
      // 深度观察监听
      deep: true,
    },
  },
  computed: {
    appTypeMap() {
      return {
        agent: this.$t('menu.app.agent'),
        rag: this.$t('menu.app.rag'),
        workflow: this.$t('menu.app.workflow'),
        custom: this.$t('menu.app.custom'),
        openapi: this.$t('menu.app.openapi'),
        builtin: this.$t('menu.app.builtIn'),
      };
    },
  },
  mounted() {
    this.initData();
  },
  methods: {
    avatarSrc,
    displayToolName(tool) {
      return (
        (CAMPUS_TOOL_LABELS[tool.methodName] || {}).name || tool.methodName
      );
    },
    displayToolDesc(tool) {
      return (CAMPUS_TOOL_LABELS[tool.methodName] || {}).desc || tool.desc;
    },
    initData() {
      this.mcpServerId = this.$route.query.mcpServerId;
      this.tabActive = 0;
      getServer({ mcpServerId: this.mcpServerId }).then(res => {
        this.detail = res.data || {};
        this.detail.tools = (this.detail.tools || []).map(tool => ({
          ...tool,
          isEditing: false,
        }));
      });

      getApiKeyList({
        appId: this.mcpServerId,
        appType: APPTYPE_MCPSERVER,
      }).then(res => {
        this.apiKeyList = res.data || [];
      });

      //滚动到顶部
      const main = document.querySelector('.el-main > .page-container');
      if (main) main.scrollTop = 0;
    },
    fold() {
      this.foldStatus = !this.foldStatus;
    },
    fetchList() {
      this.initData();
    },
    handleEditTool(row) {
      editServerTool(row).then(res => {
        if (res.code === 0) {
          this.$message.success(this.$t('common.info.edit'));
          row.isEditing = false;
        }
      });
    },
    handleDeleteTool(row) {
      deleteServerTool(row).then(res => {
        if (res.code === 0) {
          this.$message.success(this.$t('common.info.delete'));
          this.detail.tools = this.detail.tools.filter(
            item => item.mcpServerToolId !== row.mcpServerToolId,
          );
        }
      });
    },
    handleCreateApiKey() {
      createApiKey({
        appId: this.mcpServerId,
        appType: APPTYPE_MCPSERVER,
      }).then(res => {
        if (res.code === 0) {
          this.$message.success(this.$t('common.message.success'));
          this.apiKeyList = [...this.apiKeyList, res.data];
        }
      });
    },
    handleDeleteApiKey(row) {
      this.$confirm(
        this.$t('tool.server.detail.deleteHint'),
        this.$t('common.confirm.title'),
        {
          confirmButtonText: this.$t('common.confirm.confirm'),
          cancelButtonText: this.$t('common.confirm.cancel'),
          type: 'warning',
        },
      ).then(() => {
        delApiKey({ apiId: row.apiId }).then(res => {
          if (res.code === 0) {
            this.$message.success(this.$t('common.info.delete'));
            this.apiKeyList = this.apiKeyList.filter(
              item => item.apiId !== row.apiId,
            );
          }
        });
      });
    },
    back() {
      this.$router.push({ path: '/mcpService?mcp=server' });
    },
  },
};
</script>
<style lang="scss" scoped>
@import '@/style/tabs.scss';
@import '@/style/squareDetail.scss';

.mcp-detail {
  .mcp-main {
    .info {
      width: 100%;
      .tool {
        .tool-item {
          border-bottom: 1px solid #eee;

          .title {
            font-weight: bold;
            line-height: 46px;
          }

          .tool-item-bg {
            background: inherit;
            background-color: rgba(249, 249, 249, 1);
            border: none;
            border-radius: 10px;
            padding: 20px;
          }
        }

        .tool-item:last-child {
          border-bottom: none;
        }

        ::v-deep .el-table {
          margin-top: 10px;
        }

        .schema-textarea {
          ::v-deep .el-textarea__inner {
            height: 200px !important;
          }
        }

        .install-intro-item {
          p {
            line-height: 26px;
            color: #333;
          }

          .install-intro-title {
            color: $color;
            margin-top: 10px;
            font-weight: bold;
          }
        }
      }
    }
  }
}

.tooltip {
  max-width: 500px !important;
}
</style>
