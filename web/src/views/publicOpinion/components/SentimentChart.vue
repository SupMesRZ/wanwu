<template>
  <section class="chart-card">
    <h3>{{ $t('publicOpinion.overview.sentimentTitle') }}</h3>
    <div ref="chart" class="chart"></div>
  </section>
</template>

<script>
import * as echarts from 'echarts';

export default {
  name: 'SentimentChart',
  props: {
    chartData: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return { chart: null };
  },
  watch: {
    chartData: {
      deep: true,
      handler() {
        this.renderChart();
      },
    },
  },
  mounted() {
    this.chart = echarts.init(this.$refs.chart);
    this.renderChart();
    window.addEventListener('resize', this.handleResize);
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.handleResize);
    if (this.chart) {
      this.chart.dispose();
      this.chart = null;
    }
  },
  methods: {
    handleResize() {
      if (this.chart) this.chart.resize();
    },
    renderChart() {
      if (!this.chart) return;
      const data = this.chartData.map(item => ({
        value: item.value,
        name: this.$t(`publicOpinion.sentiment.${item.key}`),
      }));
      this.chart.setOption(
        {
          color: ['#58b58b', '#8da2c8', '#e8877c'],
          tooltip: { trigger: 'item', formatter: '{b}: {c}%' },
          legend: {
            bottom: 8,
            left: 'center',
            itemWidth: 10,
            itemHeight: 10,
          },
          series: [
            {
              type: 'pie',
              radius: ['45%', '68%'],
              center: ['50%', '45%'],
              avoidLabelOverlap: true,
              label: { formatter: '{b}\n{d}%', color: '#606778' },
              itemStyle: { borderColor: '#fff', borderWidth: 3 },
              data,
            },
          ],
        },
        true,
      );
    },
  },
};
</script>

<style lang="scss" scoped>
.chart-card {
  min-width: 0;
  padding: 18px 20px 12px;
  background: #fff;
  border: 1px solid #edf0f5;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(22, 52, 156, 0.05);

  h3 {
    position: relative;
    margin: 0;
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

.chart {
  width: 100%;
  height: 300px;
}
</style>
