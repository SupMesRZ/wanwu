<template>
  <div class="analysis-workspace">
    <section class="workspace-main">
      <div class="empty-state">
        <div class="empty-icon"><i class="el-icon-data-analysis"></i></div>
        <h2>{{ $t('publicOpinion.analysis.selectPrompt') }}</h2>
        <p>{{ $t('publicOpinion.analysis.selectDescription') }}</p>
      </div>

      <div class="analysis-grid">
        <div v-for="item in analysisAreas" :key="item" class="analysis-area">
          <span>{{ $t(`publicOpinion.analysis.${item}`) }}</span>
          <div class="area-placeholder"></div>
        </div>
      </div>
    </section>

    <aside class="hermes-panel">
      <div class="hermes-title">
        <span class="hermes-mark">河</span>
        <h3>{{ $t('publicOpinion.analysis.hermesTitle') }}</h3>
      </div>
      <div class="building-state">
        <i class="el-icon-setting"></i>
        <strong>{{ $t('publicOpinion.analysis.building') }}</strong>
        <el-tag size="small" effect="plain">
          {{ $t('publicOpinion.analysis.modelPending') }}
        </el-tag>
        <p>{{ $t('publicOpinion.analysis.modelDescription') }}</p>
      </div>
    </aside>
  </div>
</template>

<script>
export default {
  name: 'AnalysisWorkspace',
  data() {
    return {
      analysisAreas: [
        'eventSummary',
        'coreNeeds',
        'emotionAnalysis',
        'riskJudgement',
        'factCheck',
        'spreadTrend',
        'responseSuggestion',
        'serviceSuggestion',
      ],
    };
  },
};
</script>

<style lang="scss" scoped>
.analysis-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  gap: 16px;
}

.workspace-main,
.hermes-panel {
  min-width: 0;
  background: #fff;
  border: 1px solid #edf0f5;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(22, 52, 156, 0.05);
}

.workspace-main {
  padding: 24px;
}

.empty-state {
  padding: 22px 20px 28px;
  text-align: center;

  .empty-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 52px;
    height: 52px;
    color: $color;
    background: $color_opacity;
    border-radius: 12px;
    font-size: 25px;
  }

  h2 {
    margin: 14px 0 7px;
    color: $color_title;
    font-size: 17px;
  }

  p {
    margin: 0;
    color: #8a91a1;
    font-size: 13px;
  }
}

.analysis-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.analysis-area {
  min-height: 98px;
  padding: 14px;
  background: #fafbfc;
  border: 1px dashed #dfe3eb;
  border-radius: 7px;

  span {
    color: #606778;
    font-size: 13px;
    font-weight: 500;
  }

  .area-placeholder {
    width: 72%;
    height: 7px;
    margin-top: 20px;
    background: #edf0f5;
    border-radius: 4px;

    &::after {
      display: block;
      width: 58%;
      height: 7px;
      margin-top: 12px;
      background: #f0f2f5;
      border-radius: 4px;
      content: '';
    }
  }
}

.hermes-panel {
  padding: 20px;
}

.hermes-title {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eef0f4;

  .hermes-mark {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    color: #fff;
    background: $color;
    border-radius: 8px;
    font-weight: 600;
  }

  h3 {
    margin: 0;
    color: $color_title;
    font-size: 15px;
  }
}

.building-state {
  display: flex;
  align-items: center;
  flex-direction: column;
  padding: 48px 8px;
  text-align: center;

  > i {
    margin-bottom: 14px;
    color: #9fb4ee;
    font-size: 34px;
  }

  strong {
    margin-bottom: 12px;
    color: #606778;
    font-size: 15px;
  }

  p {
    margin: 16px 0 0;
    color: #969dac;
    font-size: 12px;
    line-height: 20px;
  }
}

@media (max-width: 1050px) {
  .analysis-workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .analysis-grid {
    grid-template-columns: 1fr;
  }
}
</style>
