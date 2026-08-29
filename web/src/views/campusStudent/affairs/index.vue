<template>
  <main v-loading="loading" class="campus-student-page">
    <section class="student-hero">
      <span>CAMPUS AFFAIRS</span>
      <h1>我的事务</h1>
      <p>集中查看当前账号的请假记录与办理状态。</p>
      <el-button
        class="student-hero__assistant"
        size="small"
        icon="el-icon-chat-dot-round"
        @click="askAssistant"
      >
        问河小智
      </el-button>
    </section>

    <section class="student-metrics">
      <article>
        <span>请假记录</span>
        <strong>{{ records.length }} 条</strong>
        <small>仅显示当前学生数据</small>
      </article>
      <article>
        <span>审批中</span>
        <strong>{{ statusCount('pending') }} 条</strong>
        <small>等待审批结果</small>
      </article>
      <article>
        <span>已通过</span>
        <strong>{{ statusCount('approved') }} 条</strong>
        <small>历史审批记录</small>
      </article>
      <article>
        <span>待办事项</span>
        <strong>{{ pendingTaskCount }} 项</strong>
        <small>需要持续关注</small>
      </article>
    </section>

    <section class="student-panel">
      <div class="panel-heading">
        <div>
          <h2>请假记录与办理状态</h2>
          <p>本阶段提供只读查询，身份由登录上下文自动确定。</p>
        </div>
        <el-tag size="mini" effect="plain">当前账号</el-tag>
      </div>

      <el-table v-if="records.length" :data="records" style="width: 100%">
        <el-table-column
          prop="applicationNo"
          label="申请编号"
          min-width="160"
        />
        <el-table-column prop="leaveType" label="类型" width="90" />
        <el-table-column label="请假时间" min-width="270">
          <template slot-scope="scope">
            {{ scope.row.startTime }} 至 {{ scope.row.endTime }}
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="130" />
        <el-table-column prop="createdAt" label="申请时间" min-width="160" />
        <el-table-column label="状态" width="110">
          <template slot-scope="scope">
            <el-tag
              :type="statusType(scope.row.status)"
              size="mini"
              effect="plain"
            >
              {{ scope.row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-text">暂无请假记录</div>
    </section>

    <section class="student-panel">
      <div class="panel-heading">
        <div>
          <h2>待办事项</h2>
          <p>集中查看需要你关注的校园事务与办理进度。</p>
        </div>
        <el-tag size="mini" effect="plain">只读</el-tag>
      </div>
      <el-table v-if="tasks.length" :data="tasks" style="width: 100%">
        <el-table-column prop="title" label="事项" min-width="180" />
        <el-table-column label="类型" width="110">
          <template slot-scope="scope">
            {{ taskTypeText(scope.row.type) }}
          </template>
        </el-table-column>
        <el-table-column prop="courseName" label="相关课程" min-width="130">
          <template slot-scope="scope">
            {{ scope.row.courseName || '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="dueAt" label="截止时间" min-width="170" />
        <el-table-column label="状态" width="100">
          <template slot-scope="scope">
            <el-tag
              size="mini"
              :type="taskStatusType(scope.row.status)"
              effect="plain"
            >
              {{ taskStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-text">暂无待办事项</div>
    </section>
  </main>
</template>

<script>
import { getStudentLeaveRecords, getStudentSummary } from '@/api/campusStudent';

const TASK_TYPES = { homework: '课程任务', notice: '校园通知' };
const TASK_STATUSES = { pending: '待完成', completed: '已完成' };
const TASK_STATUS_TYPES = { pending: 'warning', completed: 'success' };

export default {
  name: 'CampusStudentAffairs',
  data() {
    return {
      loading: false,
      records: [],
      tasks: [],
    };
  },
  computed: {
    pendingTaskCount() {
      return this.tasks.filter(task => task.status === 'pending').length;
    },
  },
  created() {
    this.loadData();
  },
  methods: {
    askAssistant() {
      this.$router.push({
        path: '/smartAssistant',
        query: { service: 'leave' },
      });
    },
    async loadData() {
      this.loading = true;
      try {
        const [records, summary] = await Promise.all([
          getStudentLeaveRecords(),
          getStudentSummary(),
        ]);
        this.records = records.data || [];
        this.tasks = summary.data?.tasks || [];
      } finally {
        this.loading = false;
      }
    },
    statusCount(status) {
      return this.records.filter(record => record.status === status).length;
    },
    statusType(status) {
      return { approved: 'success', pending: 'warning', rejected: 'danger' }[
        status
      ];
    },
    taskTypeText(type) {
      return TASK_TYPES[type] || type || '其他';
    },
    taskStatusText(status) {
      return TASK_STATUSES[status] || status || '待确认';
    },
    taskStatusType(status) {
      return TASK_STATUS_TYPES[status] || 'info';
    },
  },
};
</script>

<style lang="scss" scoped>
@import '../student-page.scss';
</style>
