<template>
  <main v-loading="loading" class="campus-student-page">
    <section class="student-hero">
      <span>STUDENT LEARNING SERVICE</span>
      <h1>我的课程</h1>
      <p>查看只属于当前账号的课程、周课表与考试安排。</p>
    </section>

    <section class="student-metrics">
      <article>
        <span>本学期课程</span>
        <strong>{{ courses.length }} 门</strong>
        <small>合计 {{ totalCredits }} 学分</small>
      </article>
      <article>
        <span>今日课程</span>
        <strong>{{ todayCourses.length }} 节</strong>
        <small>{{ nextCourseText }}</small>
      </article>
      <article>
        <span>本周课程</span>
        <strong>{{ weekCourses.length }} 节</strong>
        <small>按个人课表同步</small>
      </article>
      <article>
        <span>待参加考试</span>
        <strong>{{ exams.length }} 场</strong>
        <small>请留意时间与座位</small>
      </article>
    </section>

    <section class="student-panel">
      <div class="panel-heading">
        <div>
          <h2>个人课程安排</h2>
          <p>数据由当前登录身份获取，不接受手工指定学生编号。</p>
        </div>
        <el-tag size="mini" effect="plain">当前账号</el-tag>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="今日课表" name="today">
          <course-table :rows="todayCourses" @detail="showDetail" />
        </el-tab-pane>
        <el-tab-pane label="本周课表" name="week">
          <course-table :rows="weekCourses" show-date @detail="showDetail" />
        </el-tab-pane>
        <el-tab-pane label="课程详情" name="all">
          <course-table :rows="courses" @detail="showDetail" />
        </el-tab-pane>
        <el-tab-pane label="考试安排" name="exams">
          <el-table v-if="exams.length" :data="exams" style="width: 100%">
            <el-table-column prop="courseName" label="课程" min-width="150" />
            <el-table-column prop="examTime" label="考试时间" min-width="170" />
            <el-table-column label="地点" min-width="150">
              <template slot-scope="scope">
                {{ scope.row.building }} {{ scope.row.room }}
              </template>
            </el-table-column>
            <el-table-column prop="seat" label="座位" width="90" />
            <el-table-column label="时长" width="100">
              <template slot-scope="scope">
                {{ scope.row.durationMinutes }} 分钟
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-text">暂无考试安排</div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog title="课程详情" :visible.sync="detailVisible" width="520px">
      <el-descriptions v-if="selectedCourse" :column="1" border>
        <el-descriptions-item label="课程名称">
          {{ selectedCourse.courseName }}
        </el-descriptions-item>
        <el-descriptions-item label="任课教师">
          {{ selectedCourse.teacher }}
        </el-descriptions-item>
        <el-descriptions-item label="上课时间">
          {{ selectedCourse.weekdayName }} {{ selectedCourse.startTime }}—{{ selectedCourse.endTime }}
        </el-descriptions-item>
        <el-descriptions-item label="上课地点">
          {{ selectedCourse.building }} {{ selectedCourse.room }}
        </el-descriptions-item>
        <el-descriptions-item label="课程信息">
          {{ selectedCourse.courseType }} · {{ selectedCourse.credits }} 学分
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </main>
</template>

<script>
import {
  getStudentCourses,
  getStudentTodayCourses,
  getStudentWeekCourses,
  getStudentExams,
} from '@/api/campusStudent';

const CourseTable = {
  props: {
    rows: { type: Array, default: () => [] },
    showDate: { type: Boolean, default: false },
  },
  render(h) {
    if (!this.rows.length) {
      return h('div', { class: 'empty-text' }, '暂无课程安排');
    }
    const columns = [
      h('el-table-column', { props: { prop: 'courseName', label: '课程', minWidth: '150' } }),
      h('el-table-column', { props: { prop: 'weekdayName', label: '星期', width: '90' } }),
    ];
    if (this.showDate) {
      columns.push(h('el-table-column', { props: { prop: 'date', label: '日期', width: '110' } }));
    }
    columns.push(
      h('el-table-column', { props: { label: '时间', minWidth: '150' }, scopedSlots: { default: ({ row }) => `${row.startTime}—${row.endTime}` } }),
      h('el-table-column', { props: { label: '地点', minWidth: '140' }, scopedSlots: { default: ({ row }) => `${row.building} ${row.room}` } }),
      h('el-table-column', { props: { prop: 'teacher', label: '教师', width: '100' } }),
      h('el-table-column', {
        props: { label: '操作', width: '80', fixed: 'right' },
        scopedSlots: {
          default: ({ row }) => h('el-button', { props: { type: 'text' }, on: { click: () => this.$emit('detail', row) } }, '查看'),
        },
      }),
    );
    return h('el-table', { props: { data: this.rows }, style: { width: '100%' } }, columns);
  },
};

export default {
  name: 'CampusStudentCourses',
  components: { CourseTable },
  data() {
    return {
      loading: false,
      activeTab: 'today',
      courses: [],
      todayCourses: [],
      weekCourses: [],
      exams: [],
      selectedCourse: null,
      detailVisible: false,
    };
  },
  computed: {
    totalCredits() {
      return this.courses.reduce((total, course) => total + Number(course.credits || 0), 0);
    },
    nextCourseText() {
      return this.todayCourses.length
        ? `下一节 ${this.todayCourses[0].startTime}`
        : '今日无课程';
    },
  },
  created() {
    this.loadData();
  },
  methods: {
    async loadData() {
      this.loading = true;
      try {
        const [courses, today, week, exams] = await Promise.all([
          getStudentCourses(),
          getStudentTodayCourses(),
          getStudentWeekCourses(),
          getStudentExams(),
        ]);
        this.courses = courses.data || [];
        this.todayCourses = today.data || [];
        this.weekCourses = week.data || [];
        this.exams = exams.data || [];
      } finally {
        this.loading = false;
      }
    },
    showDetail(course) {
      this.selectedCourse = course;
      this.detailVisible = true;
    },
  },
};
</script>

<style lang="scss" scoped>
@import '../student-page.scss';
</style>
