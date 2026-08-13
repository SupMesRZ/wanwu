<template>
  <div class="overview-panel">
    <OverviewCards :items="overviewStats" />

    <div class="chart-grid section-gap">
      <TrendChart :key="`trend-${refreshKey}`" :chart-data="trendData" />
      <SentimentChart
        :key="`sentiment-${refreshKey}`"
        :chart-data="sentimentData"
      />
    </div>

    <div class="ranking-grid section-gap">
      <section class="content-card">
        <h3>{{ $t('publicOpinion.overview.hotTopics') }}</h3>
        <ol class="ranking-list">
          <li v-for="(item, index) in hotTopics" :key="item.name">
            <span class="rank">{{ index + 1 }}</span>
            <span class="ranking-name">{{ item.name }}</span>
            <span class="trend">{{ item.change }}</span>
            <span class="heat">
              {{ $t('publicOpinion.overview.heat') }} {{ item.heat }}
            </span>
          </li>
        </ol>
      </section>

      <section class="content-card">
        <h3>{{ $t('publicOpinion.overview.riskEvents') }}</h3>
        <ol class="ranking-list">
          <li v-for="(item, index) in riskEvents" :key="item.name">
            <span class="rank">{{ index + 1 }}</span>
            <span class="ranking-name">{{ item.name }}</span>
            <el-tag :type="riskType(item.risk)" size="mini" effect="plain">
              {{ $t(`publicOpinion.risk.${item.risk}`) }}
            </el-tag>
            <span class="heat">
              {{ $t('publicOpinion.overview.heat') }} {{ item.heat }}
            </span>
          </li>
        </ol>
      </section>
    </div>

    <section class="content-card latest-card section-gap">
      <h3>{{ $t('publicOpinion.overview.latestOpinion') }}</h3>
      <OpinionTable simple />
    </section>
  </div>
</template>

<script>
import OverviewCards from './OverviewCards.vue';
import TrendChart from './TrendChart.vue';
import SentimentChart from './SentimentChart.vue';
import OpinionTable from './OpinionTable.vue';
import {
  overviewStats,
  trendData,
  sentimentData,
  hotTopics,
  riskEvents,
} from '../mock';

export default {
  name: 'Overview',
  components: { OverviewCards, TrendChart, SentimentChart, OpinionTable },
  props: {
    refreshKey: {
      type: Number,
      default: 0,
    },
  },
  data() {
    return { overviewStats, trendData, sentimentData, hotTopics, riskEvents };
  },
  methods: {
    riskType(risk) {
      return {
        low: 'success',
        normal: 'warning',
        high: 'danger',
        major: 'danger',
      }[risk];
    },
  },
};
</script>

<style lang="scss" scoped>
.section-gap {
  margin-top: 16px;
}

.chart-grid,
.ranking-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
  gap: 16px;
}

.ranking-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.content-card {
  min-width: 0;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid #edf0f5;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(22, 52, 156, 0.05);

  h3 {
    position: relative;
    margin: 0 0 14px;
    padding-left: 10px;
    color: $color_title;
    font-size: 14px;

    &::before {
      position: absolute;
      top: 2px;
      bottom: 2px;
      left: 0;
      width: 3px;
      background: $color;
      content: '';
    }
  }
}

.ranking-list {
  margin: 0;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    align-items: center;
    min-height: 43px;
    border-bottom: 1px solid #f0f2f5;
    gap: 10px;
    font-size: 13px;

    &:last-child {
      border-bottom: 0;
    }
  }

  .rank {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    flex: 0 0 22px;
    color: $color;
    background: $color_opacity;
    border-radius: 5px;
    font-size: 12px;
  }

  .ranking-name {
    min-width: 0;
    flex: 1;
    overflow: hidden;
    color: #4f5668;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .trend {
    color: #e56a5d;
  }

  .heat {
    color: #9aa0ad;
    font-size: 12px;
    white-space: nowrap;
  }
}

.latest-card {
  padding-bottom: 8px;
}

@media (max-width: 1100px) {
  .chart-grid,
  .ranking-grid {
    grid-template-columns: 1fr;
  }
}
</style>
