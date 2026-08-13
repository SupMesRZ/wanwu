<template>
  <div class="report-panel">
    <div class="report-tip">
      <i class="el-icon-info"></i>
      {{ $t('publicOpinion.report.description') }}
    </div>

    <div class="report-grid">
      <article v-for="item in reports" :key="item.id" class="report-card">
        <div class="report-icon"><i class="el-icon-document"></i></div>
        <div class="report-content">
          <div class="report-heading">
            <h3>{{ item.name }}</h3>
            <el-tag size="mini" :type="statusType(item.status)" effect="plain">
              {{ item.status }}
            </el-tag>
          </div>
          <dl>
            <div>
              <dt>{{ $t('publicOpinion.report.type') }}</dt>
              <dd>{{ item.type }}</dd>
            </div>
            <div>
              <dt>{{ $t('publicOpinion.report.generatedAt') }}</dt>
              <dd>{{ item.generatedAt }}</dd>
            </div>
          </dl>
          <div class="report-actions">
            <el-button size="small" @click="showPreviewTip">
              {{ $t('publicOpinion.report.preview') }}
            </el-button>
            <el-button
              size="small"
              type="primary"
              plain
              @click="showGenerateTip"
            >
              {{ $t('publicOpinion.report.generate') }}
            </el-button>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<script>
import { reports } from '../mock';

export default {
  name: 'ReportPanel',
  data() {
    return { reports };
  },
  methods: {
    statusType(status) {
      return { 已生成: 'success', 草稿: 'warning', 待生成: 'info' }[status];
    },
    showPreviewTip() {
      this.$message.info(this.$t('publicOpinion.report.previewTip'));
    },
    showGenerateTip() {
      this.$message.info(this.$t('publicOpinion.report.generateTip'));
    },
  },
};
</script>

<style lang="scss" scoped>
.report-tip {
  padding: 12px 16px;
  color: #5e6f92;
  background: $color_opacity;
  border: 1px solid #dfe8ff;
  border-radius: 7px;
  font-size: 13px;

  i {
    margin-right: 6px;
    color: $color;
  }
}

.report-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.report-card {
  display: flex;
  min-width: 0;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border: 1px solid #edf0f5;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(22, 52, 156, 0.05);
}

.report-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  color: $color;
  background: $color_opacity;
  border-radius: 9px;
  font-size: 22px;
}

.report-content {
  min-width: 0;
  flex: 1;
}

.report-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;

  h3 {
    margin: 2px 0 0;
    overflow: hidden;
    color: $color_title;
    font-size: 15px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

dl {
  margin: 16px 0;

  div {
    display: flex;
    gap: 12px;
    margin-top: 9px;
    font-size: 12px;
  }

  dt {
    width: 70px;
    flex: 0 0 70px;
    color: #969dac;
  }

  dd {
    margin: 0;
    color: #606778;
  }
}

.report-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 14px;
  border-top: 1px solid #eef0f4;
}

@media (max-width: 950px) {
  .report-grid {
    grid-template-columns: 1fr;
  }
}
</style>
