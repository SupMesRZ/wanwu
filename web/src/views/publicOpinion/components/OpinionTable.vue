<template>
  <div :class="['opinion-table-panel', { 'simple-mode': simple }]">
    <div v-if="!simple" class="table-toolbar">
      <el-input
        v-model.trim="filters.keyword"
        class="keyword-input"
        size="small"
        clearable
        prefix-icon="el-icon-search"
        :placeholder="$t('publicOpinion.table.keywordPlaceholder')"
        @input="resetPage"
      />
      <el-date-picker
        v-model="filters.time"
        size="small"
        type="daterange"
        value-format="yyyy-MM-dd"
        range-separator="-"
        :start-placeholder="$t('publicOpinion.table.time')"
        :end-placeholder="$t('publicOpinion.table.time')"
        @change="resetPage"
      />
      <el-select
        v-model="filters.topic"
        size="small"
        clearable
        :placeholder="$t('publicOpinion.table.topic')"
        @change="resetPage"
      >
        <el-option
          v-for="topic in topicOptions"
          :key="topic"
          :label="topic"
          :value="topic"
        />
      </el-select>
      <el-select
        v-model="filters.sentiment"
        size="small"
        clearable
        :placeholder="$t('publicOpinion.table.sentiment')"
        @change="resetPage"
      >
        <el-option
          v-for="sentiment in sentimentOptions"
          :key="sentiment"
          :label="$t(`publicOpinion.sentiment.${sentiment}`)"
          :value="sentiment"
        />
      </el-select>
      <el-select
        v-model="filters.risk"
        size="small"
        clearable
        :placeholder="$t('publicOpinion.table.risk')"
        @change="resetPage"
      >
        <el-option
          v-for="risk in riskOptions"
          :key="risk"
          :label="$t(`publicOpinion.risk.${risk}`)"
          :value="risk"
        />
      </el-select>
      <el-button size="small" icon="el-icon-refresh-left" @click="resetFilters">
        {{ $t('publicOpinion.table.reset') }}
      </el-button>
      <el-tag size="small" effect="plain">
        {{ $t('publicOpinion.demoData') }}
      </el-tag>
    </div>

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
      <el-table-column :label="$t('publicOpinion.table.sentiment')" width="90">
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
      <template slot="empty">
        <el-empty :description="$t('publicOpinion.table.empty')" />
      </template>
    </el-table>

    <div v-if="!simple && filteredRows.length" class="pagination-wrap">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="filteredRows.length"
        :page-size="pageSize"
        :current-page="currentPage"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script>
import { opinionList } from '../mock';

export default {
  name: 'OpinionTable',
  props: {
    simple: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      rows: opinionList,
      filters: {
        keyword: '',
        time: [],
        topic: '',
        sentiment: '',
        risk: '',
      },
      currentPage: 1,
      pageSize: 8,
      sentimentOptions: ['positive', 'neutral', 'negative'],
      riskOptions: ['low', 'normal', 'high', 'major'],
    };
  },
  computed: {
    topicOptions() {
      return [...new Set(this.rows.map(item => item.topic))];
    },
    filteredRows() {
      if (this.simple) return this.rows;
      const keyword = this.filters.keyword.toLowerCase();
      const [startDate, endDate] = this.filters.time || [];
      return this.rows.filter(item => {
        const matchesKeyword =
          !keyword ||
          item.title.toLowerCase().includes(keyword) ||
          item.topic.toLowerCase().includes(keyword);
        const publishedDate = item.publishedAt.slice(0, 10);
        const matchesTime =
          (!startDate || publishedDate >= startDate) &&
          (!endDate || publishedDate <= endDate);
        return (
          matchesKeyword &&
          matchesTime &&
          (!this.filters.topic || item.topic === this.filters.topic) &&
          (!this.filters.sentiment ||
            item.sentiment === this.filters.sentiment) &&
          (!this.filters.risk || item.risk === this.filters.risk)
        );
      });
    },
    displayRows() {
      if (this.simple) return this.rows.slice(0, 6);
      const start = (this.currentPage - 1) * this.pageSize;
      return this.filteredRows.slice(start, start + this.pageSize);
    },
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
    resetPage() {
      this.currentPage = 1;
    },
    handlePageChange(page) {
      this.currentPage = page;
    },
    resetFilters() {
      this.filters = {
        keyword: '',
        time: [],
        topic: '',
        sentiment: '',
        risk: '',
      };
      this.resetPage();
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
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;

  .keyword-input {
    width: 220px;
  }

  .el-select {
    width: 132px;
  }
}

::v-deep .el-table {
  color: #4f5668;
  font-size: 13px;

  th.el-table__cell {
    color: $color_title;
    background: #f7f8fa;
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

@media (max-width: 760px) {
  .table-toolbar {
    align-items: stretch;

    .keyword-input,
    .el-select,
    .el-date-editor {
      width: 100%;
    }
  }
}
</style>
