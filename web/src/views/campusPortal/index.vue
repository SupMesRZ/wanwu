<template>
  <div class="campus-portal">
    <section class="portal-hero">
      <div>
        <span>{{ page.eyebrow }}</span>
        <h1>{{ page.title }}</h1>
        <p>{{ page.description }}</p>
      </div>
      <el-button type="primary" icon="el-icon-chat-dot-round" @click="ask">
        对河小智说一句话
      </el-button>
    </section>

    <section class="metric-grid">
      <article v-for="item in page.metrics" :key="item.label">
        <span :class="['metric-icon', item.color]">
          <i :class="item.icon"></i>
        </span>
        <div>
          <p>{{ item.label }}</p>
          <strong>{{ item.value }}</strong>
          <small>{{ item.note }}</small>
        </div>
      </article>
    </section>

    <section class="content-grid">
      <article class="panel">
        <div class="panel-heading">
          <div>
            <h2>{{ page.listTitle }}</h2>
            <p>{{ page.listDescription }}</p>
          </div>
          <el-tag size="mini" effect="plain">演示数据</el-tag>
        </div>
        <div class="item-list">
          <div v-for="item in page.items" :key="item.title">
            <span class="item-time">{{ item.time }}</span>
            <span class="item-dot" :class="item.color"></span>
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.description }}</p>
            </div>
            <el-tag size="mini" :type="item.tagType || 'info'" effect="plain">
              {{ item.tag }}
            </el-tag>
          </div>
        </div>
      </article>

      <aside class="panel action-panel">
        <div class="panel-heading">
          <div>
            <h2>智能快捷服务</h2>
            <p>自然语言发起查询与办理</p>
          </div>
        </div>
        <button
          v-for="action in page.actions"
          :key="action.text"
          type="button"
          @click="goAction(action)"
        >
          <span><i :class="action.icon"></i></span>
          <div>
            <strong>{{ action.text }}</strong>
            <small>{{ action.description }}</small>
          </div>
          <i class="el-icon-right"></i>
        </button>
      </aside>
    </section>
  </div>
</template>

<script>
const commonActions = {
  schedule: {
    text: '查询本周课表',
    description: '调用教务系统 MCP 获取个人课表',
    icon: 'el-icon-date',
    service: 'schedule',
  },
  leave: {
    text: '发起请假',
    description: '启动请假智能工作流',
    icon: 'el-icon-edit-outline',
    service: 'leave',
  },
  teaching: {
    text: '查看教学安排',
    description: '查询课程、班级与教室安排',
    icon: 'el-icon-notebook-2',
    service: 'teaching',
  },
  feedback: {
    text: '分析学生反馈',
    description: '汇总课程反馈并生成建议',
    icon: 'el-icon-data-line',
    service: 'feedback',
  },
};

const pages = {
  studentCourses: {
    eyebrow: 'STUDENT LEARNING SERVICE',
    title: '我的课程',
    description: '统一查看课表、考试、成绩与培养方案，河小智随时为你解答。',
    metrics: [
      {
        label: '本学期课程',
        value: '8 门',
        note: '总计 22 学分',
        icon: 'el-icon-reading',
        color: 'blue',
      },
      {
        label: '今日课程',
        value: '2 节',
        note: '下一节 14:00',
        icon: 'el-icon-date',
        color: 'cyan',
      },
      {
        label: '待完成任务',
        value: '5 项',
        note: '本周截止 2 项',
        icon: 'el-icon-finished',
        color: 'orange',
      },
      {
        label: '平均成绩',
        value: '87.3',
        note: '较上学期 +2.1',
        icon: 'el-icon-data-analysis',
        color: 'violet',
      },
    ],
    listTitle: '今日课程安排',
    listDescription: '由教务系统 MCP 同步的个人课程数据',
    items: [
      {
        time: '10:10',
        title: '数据结构',
        description: '逸夫楼 302 · 王老师',
        tag: '第3—4节',
        color: 'blue',
      },
      {
        time: '16:10',
        title: '大学英语',
        description: '综合楼 B204 · 李老师',
        tag: '第7—8节',
        color: 'cyan',
      },
      {
        time: '明天',
        title: '人工智能导论',
        description: '综合楼 C301 · 张老师',
        tag: '待预习',
        tagType: 'warning',
        color: 'violet',
      },
    ],
    actions: [
      commonActions.schedule,
      commonActions.leave,
      {
        text: '查询考试安排',
        description: '查看考试时间、地点与座位',
        icon: 'el-icon-tickets',
        service: 'exam',
      },
    ],
  },
  studentAffairs: {
    eyebrow: 'CAMPUS AFFAIRS',
    title: '我的事务',
    description: '请假、宿舍、活动与校园服务集中办理，进度随时可查。',
    metrics: [
      {
        label: '办理中',
        value: '2 项',
        note: '均正常流转',
        icon: 'el-icon-loading',
        color: 'blue',
      },
      {
        label: '本月已完成',
        value: '6 项',
        note: '平均 1.4 天',
        icon: 'el-icon-circle-check',
        color: 'cyan',
      },
      {
        label: '待确认',
        value: '1 项',
        note: '请假审批结果',
        icon: 'el-icon-bell',
        color: 'orange',
      },
      {
        label: '校园活动',
        value: '12 场',
        note: '本周可报名',
        icon: 'el-icon-place',
        color: 'violet',
      },
    ],
    listTitle: '事务办理进度',
    listDescription: '跨系统事务由智能工作流统一跟踪',
    items: [
      {
        time: '今天',
        title: '课程请假申请',
        description: '辅导员已审批，等待任课教师确认',
        tag: '办理中',
        tagType: 'warning',
        color: 'orange',
      },
      {
        time: '昨天',
        title: '宿舍设施报修',
        description: '维修人员预计今日 17:00 前到达',
        tag: '已派单',
        color: 'blue',
      },
      {
        time: '08-18',
        title: '图书续借申请',
        description: '《机器学习》续借成功',
        tag: '已完成',
        tagType: 'success',
        color: 'cyan',
      },
    ],
    actions: [
      commonActions.leave,
      {
        text: '宿舍事务',
        description: '咨询或提交宿舍服务申请',
        icon: 'el-icon-house',
        service: 'repair',
      },
      {
        text: '校园活动查询',
        description: '按兴趣查找近期校园活动',
        icon: 'el-icon-location-outline',
        service: 'library',
      },
    ],
  },
  studentAnalysis: {
    eyebrow: 'AI LEARNING INSIGHT',
    title: '学习分析',
    description: '结合课程与学习记录生成个性化总结、计划和改进建议。',
    metrics: [
      {
        label: '学习投入',
        value: '良好',
        note: '本周 18.5 小时',
        icon: 'el-icon-time',
        color: 'blue',
      },
      {
        label: '知识掌握度',
        value: '82%',
        note: '较上周 +4%',
        icon: 'el-icon-pie-chart',
        color: 'cyan',
      },
      {
        label: '待巩固知识点',
        value: '7 个',
        note: '数据结构 3 个',
        icon: 'el-icon-warning-outline',
        color: 'orange',
      },
      {
        label: '计划完成率',
        value: '91%',
        note: '连续学习 12 天',
        icon: 'el-icon-medal',
        color: 'violet',
      },
    ],
    listTitle: '河小智学习建议',
    listDescription: '根据演示学习记录生成，仅作产品效果展示',
    items: [
      {
        time: '重点',
        title: '加强图与树相关算法练习',
        description: '近期错题主要集中在遍历顺序与复杂度分析',
        tag: '建议练习',
        tagType: 'warning',
        color: 'orange',
      },
      {
        time: '计划',
        title: '完成高等数学阶段复习',
        description: '建议本周安排 3 次、每次 45 分钟的集中复习',
        tag: '已加入计划',
        tagType: 'success',
        color: 'blue',
      },
      {
        time: '进步',
        title: '英语阅读正确率持续提升',
        description: '近三周平均正确率由 76% 提升至 85%',
        tag: '+9%',
        tagType: 'success',
        color: 'cyan',
      },
    ],
    actions: [
      {
        text: '生成学习计划',
        description: '根据目标和空闲时间智能排期',
        icon: 'el-icon-date',
        service: 'score',
      },
      {
        text: '生成知识总结',
        description: '整理课程资料与核心知识点',
        icon: 'el-icon-document',
        service: 'library',
      },
      {
        text: '错题分析',
        description: '定位薄弱知识点并推荐练习',
        icon: 'el-icon-data-analysis',
        service: 'score',
      },
    ],
  },
  teacherTeaching: {
    eyebrow: 'TEACHING SERVICE',
    title: '我的教学',
    description: '课程安排、授课班级与教学任务统一呈现，支持智能备课。',
    metrics: [
      {
        label: '本学期课程',
        value: '2 门',
        note: '3 个教学班',
        icon: 'el-icon-notebook-2',
        color: 'blue',
      },
      {
        label: '授课学生',
        value: '126 人',
        note: '出勤率 96.8%',
        icon: 'el-icon-user',
        color: 'cyan',
      },
      {
        label: '待批作业',
        value: '38 份',
        note: '2 项本周截止',
        icon: 'el-icon-document-checked',
        color: 'orange',
      },
      {
        label: '课程评价',
        value: '4.8',
        note: '满分 5.0',
        icon: 'el-icon-star-off',
        color: 'violet',
      },
    ],
    listTitle: '今日教学安排',
    listDescription: '连接课程系统与教学管理数据',
    items: [
      {
        time: '14:00',
        title: '人工智能导论',
        description: '综合楼 C301 · 计算机科学 2024-1 班',
        tag: '第5—6节',
        color: 'blue',
      },
      {
        time: '16:10',
        title: '毕业设计指导',
        description: '学院楼 410 · 第三指导小组',
        tag: '8 名学生',
        color: 'cyan',
      },
      {
        time: '明天',
        title: '机器学习实验',
        description: '实验楼 205 · 课前材料已准备',
        tag: '材料就绪',
        tagType: 'success',
        color: 'violet',
      },
    ],
    actions: [
      commonActions.teaching,
      {
        text: '生成教学方案',
        description: '根据课程目标智能生成教学设计',
        icon: 'el-icon-document-add',
        service: 'teaching',
      },
      commonActions.feedback,
    ],
  },
  teacherResources: {
    eyebrow: 'AI COURSE RESOURCE',
    title: '教学资源',
    description: '使用知识库和工作流生成、沉淀并管理课程教学资源。',
    metrics: [
      {
        label: '课程资源',
        value: '186 份',
        note: '本月新增 24 份',
        icon: 'el-icon-folder-opened',
        color: 'blue',
      },
      {
        label: 'AI 生成课件',
        value: '32 份',
        note: '节省约 26 小时',
        icon: 'el-icon-picture-outline',
        color: 'cyan',
      },
      {
        label: '题库试题',
        value: '420 道',
        note: '覆盖 12 个章节',
        icon: 'el-icon-edit',
        color: 'orange',
      },
      {
        label: '知识库',
        value: '3 个',
        note: '最近同步 10:20',
        icon: 'el-icon-collection',
        color: 'violet',
      },
    ],
    listTitle: '最近教学资源',
    listDescription: 'AI 生成内容均保留教师审核与编辑环节',
    items: [
      {
        time: '今天',
        title: '机器学习 · 第5章教学方案',
        description: '基于课程目标与上节课反馈生成',
        tag: '待审核',
        tagType: 'warning',
        color: 'orange',
      },
      {
        time: '昨天',
        title: '人工智能导论 · 课堂练习',
        description: '20 道选择题与 3 道案例分析题',
        tag: '已发布',
        tagType: 'success',
        color: 'blue',
      },
      {
        time: '08-20',
        title: '神经网络基础 · 课堂 PPT',
        description: '共 36 页，包含 4 个可视化案例',
        tag: '已保存',
        color: 'cyan',
      },
    ],
    actions: [
      {
        text: '智能生成 PPT',
        description: '从教学方案生成结构化课件',
        icon: 'el-icon-picture-outline',
        service: 'teaching',
      },
      {
        text: '生成课堂练习',
        description: '按知识点和难度自动出题',
        icon: 'el-icon-edit-outline',
        service: 'teaching',
      },
      {
        text: '管理课程知识库',
        description: '同步教材、教案与参考资料',
        icon: 'el-icon-collection',
        service: 'library',
      },
    ],
  },
  teacherAnalysis: {
    eyebrow: 'LEARNING ANALYTICS',
    title: '学情分析',
    description: '汇总出勤、作业、测验与课堂反馈，快速识别教学关注点。',
    metrics: [
      {
        label: '平均出勤率',
        value: '96.8%',
        note: '较上月 +1.2%',
        icon: 'el-icon-circle-check',
        color: 'blue',
      },
      {
        label: '作业完成率',
        value: '92.4%',
        note: '6 人需要提醒',
        icon: 'el-icon-document-checked',
        color: 'cyan',
      },
      {
        label: '平均掌握度',
        value: '81%',
        note: '3 个薄弱知识点',
        icon: 'el-icon-data-analysis',
        color: 'orange',
      },
      {
        label: '积极反馈',
        value: '89%',
        note: '共 78 条反馈',
        icon: 'el-icon-chat-line-square',
        color: 'violet',
      },
    ],
    listTitle: '河小智教学洞察',
    listDescription: '综合多维学习数据生成的班级分析摘要',
    items: [
      {
        time: '关注',
        title: '模型评估章节掌握度偏低',
        description: '28% 的学生在交叉验证相关题目中连续出错',
        tag: '建议讲解',
        tagType: 'warning',
        color: 'orange',
      },
      {
        time: '趋势',
        title: '课堂互动参与度提升',
        description: '近三周主动提问与讨论次数提升 18%',
        tag: '+18%',
        tagType: 'success',
        color: 'cyan',
      },
      {
        time: '提醒',
        title: '6 名学生作业提交不稳定',
        description: '建议通过课程助手发送个性化学习提醒',
        tag: '待跟进',
        color: 'blue',
      },
    ],
    actions: [
      commonActions.feedback,
      {
        text: '生成学情报告',
        description: '自动整理班级学习分析报告',
        icon: 'el-icon-document',
        service: 'teachingAnalysis',
      },
      {
        text: '生成教学建议',
        description: '根据薄弱点调整教学安排',
        icon: 'el-icon-cpu',
        service: 'teachingAnalysis',
      },
    ],
  },
};

export default {
  name: 'CampusPortal',
  computed: {
    page() {
      return pages[this.$route.meta.campusPage] || pages.studentCourses;
    },
  },
  methods: {
    ask() {
      this.$router.push('/smartAssistant');
    },
    goAction(action) {
      this.$router.push({
        path: '/smartAssistant',
        query: { service: action.service },
      });
    },
  },
};
</script>

<style lang="scss" scoped>
.campus-portal {
  min-height: 100%;
  padding: 24px;
  color: #1c3150;
  background: #f4f7fb;
}
.portal-hero,
.metric-grid,
.content-grid {
  width: 100%;
  max-width: 1320px;
  margin: 0 auto 18px;
  box-sizing: border-box;
}
.portal-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 30px 34px;
  color: #fff;
  border-radius: 16px;
  background: linear-gradient(120deg, #123c78, #1768c9 68%, #398fda);
  box-shadow: 0 14px 30px rgba(25, 83, 158, 0.16);
}
.portal-hero span {
  color: rgba(255, 255, 255, 0.66);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.5px;
}
.portal-hero h1 {
  margin: 7px 0;
  font-size: 28px;
}
.portal-hero p {
  margin: 0;
  color: rgba(255, 255, 255, 0.8);
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.metric-grid article {
  display: flex;
  align-items: center;
  min-width: 0;
  padding: 18px;
  border: 1px solid #e4eaf2;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 4px 14px rgba(33, 69, 116, 0.045);
}
.metric-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  margin-right: 12px;
  color: #1768c9;
  border-radius: 11px;
  background: #ebf3ff;
  font-size: 18px;
}
.metric-icon.cyan {
  color: #118898;
  background: #e9f8fa;
}
.metric-icon.orange {
  color: #bd7219;
  background: #fff4e5;
}
.metric-icon.violet {
  color: #7153bd;
  background: #f2effb;
}
.metric-grid p,
.metric-grid small {
  display: block;
  margin: 0;
  color: #8894a5;
  font-size: 11px;
}
.metric-grid strong {
  display: block;
  margin: 4px 0 2px;
  color: #253d5d;
  font-size: 21px;
}
.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(300px, 0.7fr);
  gap: 14px;
}
.panel {
  padding: 22px;
  border: 1px solid #e3e9f1;
  border-radius: 13px;
  background: #fff;
  box-shadow: 0 5px 18px rgba(33, 69, 116, 0.05);
}
.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}
.panel-heading h2 {
  margin: 0 0 4px;
  font-size: 17px;
}
.panel-heading p {
  margin: 0;
  color: #8b96a6;
  font-size: 11px;
}
.item-list > div {
  display: grid;
  grid-template-columns: 50px 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 66px;
  border-bottom: 1px solid #edf0f4;
}
.item-list > div:last-child {
  border-bottom: 0;
}
.item-time {
  color: #74849a;
  font-size: 11px;
  text-align: center;
}
.item-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #1768c9;
  box-shadow: 0 0 0 4px #edf4ff;
}
.item-dot.cyan {
  background: #1b99a7;
  box-shadow: 0 0 0 4px #eaf8fa;
}
.item-dot.orange {
  background: #d08325;
  box-shadow: 0 0 0 4px #fff4e6;
}
.item-dot.violet {
  background: #7859c6;
  box-shadow: 0 0 0 4px #f2effb;
}
.item-list strong {
  color: #304967;
  font-size: 13px;
}
.item-list p {
  margin: 4px 0 0;
  color: #8995a6;
  font-size: 11px;
}
.action-panel button {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-bottom: 9px;
  padding: 12px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  border: 1px solid #e6ebf2;
  border-radius: 9px;
  background: #fbfcfe;
  transition: 0.2s;
}
.action-panel button:hover {
  border-color: #afccef;
  background: #f4f8ff;
  transform: translateX(2px);
}
.action-panel button > span {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: #1768c9;
  border-radius: 9px;
  background: #eaf3ff;
}
.action-panel strong,
.action-panel small {
  display: block;
}
.action-panel strong {
  margin-bottom: 4px;
  font-size: 12px;
}
.action-panel small {
  color: #8a96a6;
  font-size: 10px;
}
.action-panel button > i {
  color: #a8b3c1;
}
@media (max-width: 1020px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .content-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 620px) {
  .campus-portal {
    padding: 12px;
  }
  .portal-hero {
    align-items: flex-start;
    flex-direction: column;
  }
  .metric-grid {
    grid-template-columns: 1fr;
  }
  .item-list > div {
    grid-template-columns: 42px 8px minmax(0, 1fr);
    padding: 10px 0;
  }
  .item-list .el-tag {
    grid-column: 3;
    justify-self: start;
  }
}
</style>
