<template>
  <div class="page-wrapper public-opinion-page">
    <header class="page-header">
      <div class="page-heading">
        <div class="title-row">
          <h1>{{ $t('publicOpinion.title') }}</h1>
          <el-tag v-if="activeTab !== 'list'" size="mini" effect="plain">
            {{ $t('publicOpinion.demoData') }}
          </el-tag>
        </div>
        <p>{{ $t('publicOpinion.subtitle') }}</p>
      </div>

      <div class="page-actions">
        <div class="range-select">
          <span>{{ $t('publicOpinion.timeRange') }}</span>
          <el-select
            v-model="timeRange"
            size="small"
            @change="handleRangeChange"
          >
            <el-option
              v-for="item in rangeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>
        <el-button
          type="primary"
          size="small"
          icon="el-icon-refresh"
          @click="handleRefresh"
        >
          {{ $t('publicOpinion.refresh') }}
        </el-button>
        <span class="updated-time">
          {{ $t('publicOpinion.lastUpdated') }}：{{ lastUpdated }}
        </span>
      </div>
    </header>

    <el-tabs
      v-model="activeTab"
      class="opinion-tabs"
      @tab-click="handleTabClick"
    >
      <el-tab-pane
        v-for="tab in tabs"
        :key="tab.name"
        :label="tab.label"
        :name="tab.name"
      />
    </el-tabs>

    <main class="tab-content">
      <Overview v-if="activeTab === 'overview'" :refresh-key="refreshKey" />
      <ConcernPanel v-else-if="activeTab === 'concerns'" />
      <AlertPanel v-else-if="activeTab === 'alerts'" />
      <OpinionTable
        v-else-if="activeTab === 'list'"
        :refresh-key="refreshKey"
      />
      <AnalysisWorkspace v-else-if="activeTab === 'analysis'" />
      <ReportPanel v-else-if="activeTab === 'reports'" />
    </main>
  </div>
</template>

<script>
import moment from 'moment';
import Overview from './components/Overview.vue';
import ConcernPanel from './components/ConcernPanel.vue';
import AlertPanel from './components/AlertPanel.vue';
import OpinionTable from './components/OpinionTable.vue';
import AnalysisWorkspace from './components/AnalysisWorkspace.vue';
import ReportPanel from './components/ReportPanel.vue';

const DEFAULT_TAB = 'overview';
const TAB_NAMES = [
  'overview',
  'concerns',
  'alerts',
  'list',
  'analysis',
  'reports',
];

export default {
  name: 'PublicOpinion',
  components: {
    Overview,
    ConcernPanel,
    AlertPanel,
    OpinionTable,
    AnalysisWorkspace,
    ReportPanel,
  },
  data() {
    return {
      activeTab: DEFAULT_TAB,
      timeRange: '7',
      lastUpdated: moment().format('YYYY-MM-DD HH:mm:ss'),
      refreshKey: 0,
    };
  },
  computed: {
    tabs() {
      return TAB_NAMES.map(name => ({
        name,
        label: this.$t(`publicOpinion.tabs.${name}`),
      }));
    },
    rangeOptions() {
      return [
        { value: '7', label: this.$t('publicOpinion.ranges.days7') },
        { value: '30', label: this.$t('publicOpinion.ranges.days30') },
        { value: '90', label: this.$t('publicOpinion.ranges.days90') },
      ];
    },
  },
  watch: {
    '$route.query.tab': {
      immediate: true,
      handler(tab) {
        const normalizedTab = TAB_NAMES.includes(tab) ? tab : DEFAULT_TAB;
        this.activeTab = normalizedTab;
        if (tab !== normalizedTab) this.replaceTabQuery(normalizedTab);
      },
    },
  },
  methods: {
    replaceTabQuery(tab) {
      if (this.$route.query.tab === tab) return;
      this.$router
        .replace({
          path: this.$route.path,
          query: { ...this.$route.query, tab },
        })
        .catch(() => {});
    },
    handleTabClick(tab) {
      this.replaceTabQuery(tab.name);
    },
    handleRangeChange() {
      this.lastUpdated = moment().format('YYYY-MM-DD HH:mm:ss');
      this.refreshKey += 1;
    },
    handleRefresh() {
      this.lastUpdated = moment().format('YYYY-MM-DD HH:mm:ss');
      this.refreshKey += 1;
      this.$message.success(
        this.$t(
          this.activeTab === 'list'
            ? 'publicOpinion.realDataRefreshSuccess'
            : 'publicOpinion.refreshSuccess',
        ),
      );
    },
  },
};
</script>

<style lang="scss" scoped>
.public-opinion-page {
  padding: 0;
  overflow: hidden;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 28px 18px;
  border-bottom: 1px solid #eaeaea;
}

.page-heading {
  min-width: 280px;

  .title-row {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  h1 {
    margin: 0;
    color: $color_title;
    font-size: 20px;
    line-height: 30px;
  }

  p {
    margin: 7px 0 0;
    color: #7a8194;
    font-size: 13px;
    line-height: 20px;
  }
}

.page-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;

  .range-select {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #606778;
    font-size: 13px;

    .el-select {
      width: 112px;
    }
  }

  .updated-time {
    width: 100%;
    color: #9aa0ad;
    font-size: 12px;
    text-align: right;
  }
}

.opinion-tabs {
  padding: 0 28px;

  ::v-deep .el-tabs__header {
    margin: 0;
  }

  ::v-deep .el-tabs__content {
    display: none;
  }

  ::v-deep .el-tabs__item {
    height: 48px;
    line-height: 48px;
    font-size: 14px;
  }

  ::v-deep .el-tabs__item.is-active,
  ::v-deep .el-tabs__item:hover {
    color: $color;
  }

  ::v-deep .el-tabs__active-bar {
    background-color: $color;
  }
}

.tab-content {
  min-height: calc(100vh - 190px);
  padding: 20px 28px 28px;
  background: #f6f7f9;
}

@media (max-width: 1100px) {
  .page-header {
    flex-direction: column;
  }

  .page-actions {
    justify-content: flex-start;

    .updated-time {
      width: auto;
      text-align: left;
    }
  }
}

@media (max-width: 760px) {
  .page-header,
  .tab-content {
    padding-left: 16px;
    padding-right: 16px;
  }

  .opinion-tabs {
    padding: 0 16px;
  }
}
</style>
