<template>
  <main v-loading="loading" class="campus-student-page student-analysis">
    <section class="student-hero">
      <span>LEARNING INSIGHT</span>
      <h1>学习分析</h1>
      <p>基于当前账号的课程成绩查看学期概览与学习趋势。</p>
    </section>

    <section class="student-metrics">
      <article>
        <span>学期平均分</span>
        <strong>{{ formatScore(overview.averageScore) }}</strong>
        <small>{{ overview.term || '当前学期' }}</small>
      </article>
      <article>
        <span>最高成绩</span>
        <strong>{{ formatScore(overview.highestScore) }}</strong>
        <small>已发布课程成绩</small>
      </article>
      <article>
        <span>已修学分</span>
        <strong>{{ overview.totalCredits || 0 }}</strong>
        <small>{{ overview.courseCount || 0 }} 门课程</small>
      </article>
      <article>
        <span>通过课程</span>
        <strong>{{ overview.passedCourses || 0 }} 门</strong>
        <small>当前学期统计</small>
      </article>
    </section>

    <div class="analysis-grid">
      <section class="student-panel">
        <div class="panel-heading">
          <div>
            <h2>成绩列表</h2>
            <p>仅展示当前学生已发布的课程成绩。</p>
          </div>
          <el-tag size="mini" effect="plain">{{ overview.term }}</el-tag>
        </div>
        <el-table v-if="scores.length" :data="scores" style="width: 100%">
          <el-table-column prop="courseName" label="课程" min-width="150" />
          <el-table-column prop="credits" label="学分" width="80" />
          <el-table-column prop="score" label="成绩" width="90" />
          <el-table-column prop="gradePoint" label="绩点" width="90" />
          <el-table-column label="结果" width="100">
            <template slot-scope="scope">
              <el-tag
                :type="scope.row.score >= 60 ? 'success' : 'danger'"
                size="mini"
                effect="plain"
              >
                {{ scope.row.score >= 60 ? '通过' : '未通过' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="empty-text">当前学期暂无已发布成绩</div>
      </section>

      <aside class="student-panel insight-panel">
        <div class="panel-heading">
          <div>
            <h2>学习趋势</h2>
            <p>近三个学期平均成绩</p>
          </div>
        </div>
        <div class="trend-chart">
          <div v-for="point in analysis.trend || []" :key="point.term">
            <strong>{{ formatScore(point.averageScore) }}</strong>
            <span class="bar-track">
              <i :style="{ height: `${point.averageScore || 0}%` }"></i>
            </span>
            <small>{{ shortTerm(point.term) }}</small>
          </div>
        </div>
        <div class="suggestions">
          <h3>学习建议</h3>
          <p v-for="item in analysis.suggestions || []" :key="item">
            <i class="el-icon-circle-check"></i>{{ item }}
          </p>
        </div>
      </aside>
    </div>
  </main>
</template>

<script>
import {
  getStudentScores,
  getStudentScoreOverview,
  getStudentLearningAnalysis,
} from '@/api/campusStudent';

export default {
  name: 'CampusStudentAnalysis',
  data() {
    return {
      loading: false,
      scores: [],
      overview: {},
      analysis: {},
    };
  },
  created() {
    this.loadData();
  },
  methods: {
    async loadData() {
      this.loading = true;
      try {
        const [scores, overview, analysis] = await Promise.all([
          getStudentScores(),
          getStudentScoreOverview(),
          getStudentLearningAnalysis(),
        ]);
        this.scores = scores.data || [];
        this.overview = overview.data || {};
        this.analysis = analysis.data || {};
      } finally {
        this.loading = false;
      }
    },
    formatScore(value) {
      return Number(value || 0).toFixed(1);
    },
    shortTerm(term) {
      const parts = String(term || '').split('-');
      return parts.length === 3 ? `${parts[0]}-${parts[2]}` : term;
    },
  },
};
</script>

<style lang="scss" scoped>
@import '../student-page.scss';

.analysis-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(300px, 0.7fr);
  gap: 14px;
  width: 100%;
  max-width: 1320px;
  margin: 0 auto;

  .student-panel {
    margin-bottom: 0;
  }
}

.trend-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  height: 190px;
  padding: 8px 8px 0;
  border-bottom: 1px solid #e8edf4;

  > div {
    display: flex;
    align-items: center;
    flex-direction: column;
    height: 100%;
  }

  strong {
    margin-bottom: 7px;
    color: #365577;
    font-size: 12px;
  }

  small {
    margin-top: 7px;
    color: #8995a6;
  }
}

.bar-track {
  position: relative;
  width: 32px;
  flex: 1;
  overflow: hidden;
  border-radius: 7px 7px 0 0;
  background: #eef3f9;

  i {
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    border-radius: 7px 7px 0 0;
    background: linear-gradient(180deg, #54a5e5, #1768c9);
  }
}

.suggestions {
  margin-top: 22px;

  h3 {
    margin: 0 0 12px;
    color: #253d5d;
    font-size: 15px;
  }

  p {
    display: flex;
    gap: 8px;
    margin: 10px 0;
    color: #64758b;
    font-size: 12px;
    line-height: 1.65;

    i {
      margin-top: 4px;
      color: #1f8b74;
    }
  }
}

@media (max-width: 900px) {
  .analysis-grid {
    grid-template-columns: 1fr;
  }
}
</style>
