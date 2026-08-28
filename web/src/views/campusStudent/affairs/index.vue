<template>
  <main v-loading="loading" class="campus-student-page">
    <section class="student-hero">
      <span>CAMPUS AFFAIRS</span>
      <h1>我的事务</h1>
      <p>集中查看当前账号的请假记录与办理状态。</p>
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
        <span>未通过</span>
        <strong>{{ statusCount('rejected') }} 条</strong>
        <small>可查看具体记录</small>
      </article>
    </section>

    <section class="student-panel">
      <div class="panel-heading">
        <div>
          <h2>请假记录</h2>
          <p>本阶段提供只读查询，身份由登录上下文自动确定。</p>
        </div>
        <el-tag size="mini" effect="plain">当前账号</el-tag>
      </div>

      <el-table v-if="records.length" :data="records" style="width: 100%">
        <el-table-column prop="applicationNo" label="申请编号" min-width="160" />
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
            <el-tag :type="statusType(scope.row.status)" size="mini" effect="plain">
              {{ scope.row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-text">暂无请假记录</div>
    </section>
  </main>
</template>

<script>
import { getStudentLeaveRecords } from '@/api/campusStudent';

export default {
  name: 'CampusStudentAffairs',
  data() {
    return {
      loading: false,
      records: [],
    };
  },
  created() {
    this.loadData();
  },
  methods: {
    async loadData() {
      this.loading = true;
      try {
        const res = await getStudentLeaveRecords();
        this.records = res.data || [];
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
  },
};
</script>

<style lang="scss" scoped>
@import '../student-page.scss';
</style>
