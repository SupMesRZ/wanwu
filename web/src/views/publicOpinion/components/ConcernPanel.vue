<template>
  <div class="concern-panel">
    <div class="boundary-tip">
      <i class="el-icon-info"></i>
      {{ $t('publicOpinion.concern.boundary') }}
    </div>

    <div class="concern-grid">
      <article
        v-for="item in concernTopics"
        :key="item.name"
        class="concern-card"
      >
        <div class="card-header">
          <h3>{{ item.name }}</h3>
          <el-tag
            :type="sentimentType(item.sentiment)"
            size="small"
            effect="plain"
          >
            {{ $t(`publicOpinion.sentiment.${item.sentiment}`) }}
          </el-tag>
        </div>

        <div class="concern-stats">
          <div class="concern-stat-row">
            <span class="concern-stat-label">
              {{ $t('publicOpinion.concern.discussions') }}
            </span>
            <span class="concern-stat-value">{{ item.discussions }}</span>
          </div>
          <div class="concern-stat-row">
            <span class="concern-stat-label">
              {{ $t('publicOpinion.concern.weeklyChange') }}
            </span>
            <span class="concern-stat-value concern-stat-value--change">
              {{ item.weeklyChange }}
            </span>
          </div>
        </div>

        <div class="service-area">
          <span>{{ $t('publicOpinion.concern.serviceArea') }}</span>
          <b>{{ item.serviceArea }}</b>
        </div>

        <div class="needs">
          <h4>{{ $t('publicOpinion.concern.coreNeeds') }}</h4>
          <ul>
            <li v-for="need in item.needs" :key="need">{{ need }}</li>
          </ul>
        </div>
      </article>
    </div>
  </div>
</template>

<script>
import { concernTopics } from '../mock';

export default {
  name: 'ConcernPanel',
  data() {
    return { concernTopics };
  },
  methods: {
    sentimentType(sentiment) {
      return { positive: 'success', neutral: 'info', negative: 'danger' }[
        sentiment
      ];
    },
  },
};
</script>

<style lang="scss" scoped>
.boundary-tip {
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

.concern-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.concern-card {
  position: relative;
  min-width: 0;
  contain: layout paint;
  padding: 20px;
  background: #fff;
  border: 1px solid #edf0f5;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(22, 52, 156, 0.05);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  h3 {
    margin: 0;
    color: $color_title;
    font-size: 16px;
  }
}

.concern-stats {
  position: relative;
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
  margin: 18px 0;
  padding: 4px 14px;
  background: #f7f8fa;
  border-radius: 6px;
}

.concern-stat-row {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  min-width: 0;
  min-height: 44px;
  align-items: center;
  column-gap: 20px;
  box-sizing: border-box;
  padding: 8px 0;
  border-bottom: 1px solid #e9ecf1;

  &:last-child {
    border-bottom: 0;
  }
}

.concern-stat-label {
  position: static;
  min-width: 0;
  color: #8a91a1;
  font-size: 12px;
  line-height: 20px;
}

.concern-stat-value {
  position: static !important;
  inset: auto !important;
  display: inline-block;
  margin: 0;
  color: $color_title;
  font-size: 19px;
  font-weight: 600;
  line-height: 26px;
  text-align: right;
  transform: none !important;
  white-space: nowrap;
}

.concern-stat-value--change {
  color: #e56a5d;
}

.service-area {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #8a91a1;
  font-size: 13px;

  b {
    color: #4f5668;
    font-weight: 500;
  }
}

.needs {
  margin-top: 18px;
  padding-top: 15px;
  border-top: 1px solid #eef0f4;

  h4 {
    margin: 0 0 9px;
    color: #606778;
    font-size: 13px;
  }

  ul {
    margin: 0;
    padding-left: 18px;
    color: #686f82;
    font-size: 13px;
    line-height: 24px;
  }
}

@media (max-width: 1000px) {
  .concern-grid {
    grid-template-columns: 1fr;
  }
}
</style>
