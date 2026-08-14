<template>
  <div :class="['opinion-table-panel', { 'simple-mode': simple }]">
    <template v-if="simple">
      <el-table :data="displayRows" style="width: 100%">
        <el-table-column
          prop="title"
          :label="$t('publicOpinion.table.title')"
          min-width="230"
          show-overflow-tooltip
        />
        <el-table-column
          prop="source"
          :label="$t('publicOpinion.table.source')"
          min-width="125"
        />
        <el-table-column
          prop="publishedAt"
          :label="$t('publicOpinion.table.publishedAt')"
          min-width="155"
        />
        <el-table-column
          prop="topic"
          :label="$t('publicOpinion.table.topic')"
          min-width="105"
        />
        <el-table-column
          :label="$t('publicOpinion.table.sentiment')"
          width="90"
        >
          <template slot-scope="scope">
            <el-tag
              :type="sentimentType(scope.row.sentiment)"
              size="mini"
              effect="plain"
            >
              {{ $t(`publicOpinion.sentiment.${scope.row.sentiment}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('publicOpinion.table.risk')" width="95">
          <template slot-scope="scope">
            <el-tag :type="riskType(scope.row.risk)" size="mini" effect="plain">
              {{ $t(`publicOpinion.risk.${scope.row.risk}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="heat"
          :label="$t('publicOpinion.table.heat')"
          width="85"
          sortable
        />
        <el-table-column
          prop="status"
          :label="$t('publicOpinion.table.status')"
          min-width="105"
        />
      </el-table>
    </template>

    <template v-else-if="canView">
      <div class="table-toolbar">
        <div class="filter-area">
          <el-input
            v-model.trim="filters.keyword"
            class="keyword-input"
            size="small"
            clearable
            prefix-icon="el-icon-search"
            :placeholder="$t('publicOpinion.table.keywordPlaceholder')"
            @change="handleFilterChange"
            @clear="handleFilterChange"
            @keyup.enter.native="handleFilterChange"
          />
          <el-date-picker
            v-model="filters.time"
            size="small"
            type="daterange"
            value-format="yyyy-MM-dd"
            range-separator="-"
            :start-placeholder="$t('publicOpinion.table.startTime')"
            :end-placeholder="$t('publicOpinion.table.endTime')"
            @change="handleFilterChange"
          />
          <el-select
            v-model="filters.sourceType"
            size="small"
            clearable
            :placeholder="$t('publicOpinion.table.sourceType')"
            @change="handleFilterChange"
          >
            <el-option
              v-for="sourceType in sourceTypeOptions"
              :key="sourceType"
              :label="sourceType"
              :value="sourceType"
            />
          </el-select>
          <el-input
            v-model.trim="filters.topic"
            class="topic-input"
            size="small"
            clearable
            :placeholder="$t('publicOpinion.table.topicPlaceholder')"
            @change="handleFilterChange"
            @clear="handleFilterChange"
            @keyup.enter.native="handleFilterChange"
          />
          <el-button
            size="small"
            icon="el-icon-refresh-left"
            @click="resetFilters"
          >
            {{ $t('publicOpinion.table.reset') }}
          </el-button>
        </div>

        <div v-if="canManage" class="toolbar-actions">
          <el-button
            size="small"
            icon="el-icon-download"
            :loading="downloading"
            @click="handleDownloadTemplate"
          >
            {{ $t('publicOpinion.downloadTemplate') }}
          </el-button>
          <el-button
            type="primary"
            size="small"
            icon="el-icon-upload2"
            @click="openImportDialog"
          >
            {{ $t('publicOpinion.importData') }}
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" style="width: 100%">
        <el-table-column
          prop="title"
          :label="$t('publicOpinion.table.title')"
          min-width="230"
          show-overflow-tooltip
        />
        <el-table-column
          prop="sourceName"
          :label="$t('publicOpinion.table.sourceName')"
          min-width="125"
          show-overflow-tooltip
        />
        <el-table-column
          prop="sourceType"
          :label="$t('publicOpinion.table.sourceType')"
          min-width="125"
        />
        <el-table-column
          prop="publishedAt"
          :label="$t('publicOpinion.table.publishedAt')"
          min-width="155"
        />
        <el-table-column
          prop="topic"
          :label="$t('publicOpinion.table.topic')"
          min-width="105"
          show-overflow-tooltip
        />
        <el-table-column
          :label="$t('publicOpinion.table.sentiment')"
          width="90"
        >
          <template>
            <el-tag type="info" size="mini" effect="plain">
              {{ $t('publicOpinion.pendingAnalysis') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('publicOpinion.table.risk')" width="90">
          <template>
            <el-tag type="info" size="mini" effect="plain">
              {{ $t('publicOpinion.pendingAnalysis') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('publicOpinion.table.heat')" width="75">
          <template>-</template>
        </el-table-column>
        <el-table-column :label="$t('publicOpinion.table.status')" width="90">
          <template>
            <span class="imported-status">
              {{ $t('publicOpinion.imported') }}
            </span>
          </template>
        </el-table-column>
        <template slot="empty">
          <div class="real-empty-state">
            <p>{{ $t('publicOpinion.table.realEmpty') }}</p>
            <span>{{ $t('publicOpinion.table.realEmptyTip') }}</span>
          </div>
        </template>
      </el-table>

      <div v-if="total > 0" class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-sizes="[8, 20, 50, 100]"
          :page-size="pageSize"
          :current-page="currentPage"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>

      <ImportDialog ref="importDialog" @import-success="handleImportSuccess" />
    </template>

    <el-empty
      v-else
      :description="$t('publicOpinion.table.noViewPermission')"
    />
  </div>
</template>

<script>
import {
  downloadPublicOpinionTemplate,
  listPublicOpinionItems,
} from '@/api/publicOpinion';
import { checkPerm } from '@/router/permission';
import { PERMS } from '@/router/constants';
import { resDownloadFile } from '@/utils/util';
import { opinionList } from '../mock';
import ImportDialog from './ImportDialog.vue';

export default {
  name: 'OpinionTable',
  components: { ImportDialog },
  props: {
    simple: {
      type: Boolean,
      default: false,
    },
    refreshKey: {
      type: Number,
      default: 0,
    },
  },
  data() {
    return {
      rows: [],
      loading: false,
      downloading: false,
      filters: {
        keyword: '',
        time: [],
        sourceType: '',
        topic: '',
      },
      currentPage: 1,
      pageSize: 8,
      total: 0,
      sourceTypeOptions: [
        '学校公开网站',
        '公开新闻',
        '公开论坛',
        '公开社交平台',
        '公开评论区',
        '校园服务反馈',
        '授权数据',
      ],
    };
  },
  computed: {
    canView() {
      return this.simple || checkPerm(PERMS.PUBLIC_OPINION_VIEW);
    },
    canManage() {
      return checkPerm(PERMS.PUBLIC_OPINION_MANAGE);
    },
    displayRows() {
      return this.simple ? opinionList.slice(0, 6) : this.rows;
    },
  },
  watch: {
    refreshKey() {
      if (!this.simple && this.canView) this.fetchRows();
    },
  },
  mounted() {
    if (!this.simple && this.canView) this.fetchRows();
  },
  methods: {
    sentimentType(sentiment) {
      return { positive: 'success', neutral: 'info', negative: 'danger' }[
        sentiment
      ];
    },
    riskType(risk) {
      return {
        low: 'success',
        normal: 'warning',
        high: 'danger',
        major: 'danger',
      }[risk];
    },
    requestParams() {
      const [startDate, endDate] = this.filters.time || [];
      return {
        keyword: this.filters.keyword,
        startTime: startDate ? `${startDate} 00:00:00` : '',
        endTime: endDate ? `${endDate} 23:59:59` : '',
        sourceType: this.filters.sourceType,
        topic: this.filters.topic,
        pageNo: this.currentPage,
        pageSize: this.pageSize,
      };
    },
    async fetchRows() {
      this.loading = true;
      try {
        const response = await listPublicOpinionItems(this.requestParams());
        if (response.code !== 0) return;
        const data = response.data || {};
        this.rows = Array.isArray(data.list) ? data.list : [];
        this.total = Number(data.total) || 0;
        this.currentPage = Number(data.pageNo) || this.currentPage;
        this.pageSize = Number(data.pageSize) || this.pageSize;
      } finally {
        this.loading = false;
      }
    },
    handleFilterChange() {
      this.currentPage = 1;
      this.fetchRows();
    },
    handlePageChange(page) {
      this.currentPage = page;
      this.fetchRows();
    },
    handleSizeChange(size) {
      this.pageSize = size;
      this.currentPage = 1;
      this.fetchRows();
    },
    resetFilters() {
      this.filters = {
        keyword: '',
        time: [],
        sourceType: '',
        topic: '',
      };
      this.currentPage = 1;
      this.fetchRows();
    },
    async handleDownloadTemplate() {
      this.downloading = true;
      try {
        const blob = await downloadPublicOpinionTemplate();
        resDownloadFile(blob, '校园舆情导入模板.xlsx');
      } finally {
        this.downloading = false;
      }
    },
    openImportDialog() {
      this.$refs.importDialog.open();
    },
    handleImportSuccess() {
      this.currentPage = 1;
      this.fetchRows();
    },
  },
};
</script>

<style lang="scss" scoped>
.opinion-table-panel:not(.simple-mode) {
  padding: 20px;
  background: #fff;
  border: 1px solid #edf0f5;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(22, 52, 156, 0.05);
}

.table-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 16px;
}

.filter-area,
.toolbar-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.filter-area {
  min-width: 0;
  flex: 1;

  .keyword-input {
    width: 210px;
  }

  .topic-input,
  .el-select {
    width: 150px;
  }
}

.toolbar-actions {
  flex: 0 0 auto;
}

::v-deep .el-table {
  color: #4f5668;
  font-size: 13px;

  th.el-table__cell {
    color: $color_title;
    background: #f7f8fa;
  }
}

.imported-status {
  color: #67c23a;
}

.real-empty-state {
  padding: 32px 0;

  p {
    margin: 0 0 6px;
    color: #606266;
    font-size: 14px;
  }

  span {
    color: #909399;
    font-size: 12px;
  }
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;

  ::v-deep .el-pager li.active {
    background-color: $color;
  }
}

@media (max-width: 1100px) {
  .table-toolbar {
    flex-direction: column;
  }
}

@media (max-width: 760px) {
  .filter-area,
  .toolbar-actions {
    width: 100%;

    .keyword-input,
    .topic-input,
    .el-select,
    .el-date-editor {
      width: 100%;
    }
  }
}
</style>
