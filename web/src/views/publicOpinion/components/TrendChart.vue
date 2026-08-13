<template>
  <section class="chart-card">
    <h3>{{ $t('publicOpinion.overview.trendTitle') }}</h3>
    <div ref="chart" class="chart"></div>
  </section>
</template>

<script>
import * as echarts from 'echarts';

export default {
  name: 'TrendChart',
  props: {
    chartData: {
      type: Object,
      default: () => ({ dates: [], total: [], negative: [] }),
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
      this.chart.setOption(
        {
          color: ['#5983ff', '#e8877c'],
          tooltip: { trigger: 'axis' },
          legend: {
            top: 0,
            right: 0,
            itemWidth: 14,
            itemHeight: 7,
            data: [
              this.$t('publicOpinion.overview.totalOpinion'),
              this.$t('publicOpinion.overview.negativeOpinion'),
            ],
          },
          grid: {
            top: 42,
            right: 18,
            bottom: 24,
            left: 18,
            containLabel: true,
          },
          xAxis: {
            type: 'category',
            boundaryGap: false,
            data: this.chartData.dates,
            axisLine: { lineStyle: { color: '#dfe3eb' } },
            axisLabel: { color: '#7a8194' },
          },
          yAxis: {
            type: 'value',
            splitLine: { lineStyle: { color: '#eef0f4', type: 'dashed' } },
            axisLabel: { color: '#7a8194' },
          },
          series: [
            {
              name: this.$t('publicOpinion.overview.totalOpinion'),
              type: 'line',
              smooth: true,
              symbolSize: 6,
              data: this.chartData.total,
              areaStyle: { color: 'rgba(89, 131, 255, 0.10)' },
            },
            {
              name: this.$t('publicOpinion.overview.negativeOpinion'),
              type: 'line',
              smooth: true,
              symbolSize: 6,
              data: this.chartData.negative,
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
