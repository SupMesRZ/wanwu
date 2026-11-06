<template>
  <div class="dashboard-page">
    <section class="dashboard-kpis">
      <article v-for="card in kpiCards" :key="card.key" class="dashboard-kpi">
        <div class="dashboard-kpi__head">
          <span>{{ card.label }}</span>
          <el-icon><component :is="card.icon" /></el-icon>
        </div>
        <strong>{{ card.value }}</strong>
        <div :class="['dashboard-kpi__foot', card.trendType]">{{ card.meta }}</div>
      </article>
    </section>

    <section class="dashboard-grid">
      <article class="dashboard-panel dashboard-panel--wide">
        <div class="dashboard-panel__head">
          <h3>服务人次趋势</h3>
          <div class="panel-actions">
            <el-segmented v-model="trendRange" size="small" :options="rangeOptions" @change="renderTrendChart" />
            <el-button size="small" :icon="Refresh" @click="refreshDashboard">刷新</el-button>
          </div>
        </div>
        <div ref="trendChartRef" class="chart-surface chart-surface--trend"></div>
      </article>

      <article class="dashboard-panel">
        <div class="dashboard-panel__head">
          <h3>活跃景点</h3>
          <span class="panel-metric">围栏触发人次</span>
        </div>
        <div ref="spotChartRef" class="chart-surface"></div>
      </article>

      <article class="dashboard-panel ai-consumption-panel">
        <div class="dashboard-panel__head">
          <h3>AI 服务实况</h3>
          <span class="panel-metric">{{ trendRange }}</span>
        </div>
        <div class="ai-ledger">
          <div class="ai-ledger__main">
            <span>AI 回答消息</span>
            <strong>{{ formatNumber(aiUsage.chatCalls) }}</strong>
            <em>来自真实游客消息记录</em>
          </div>
          <div class="ai-ledger__tiles">
            <div v-for="item in aiUsageTiles" :key="item.label" class="ai-usage-tile">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </div>
        <div class="ai-perf-metrics">
          <div class="ai-perf-item">
            <span class="ai-perf-label">TTS 缓存节省</span>
            <div class="ai-perf-value-row">
              <strong>{{ formatNumber(aiUsage.ttsCacheHits) }}</strong>
              <span class="ai-perf-unit">次命中</span>
              <span class="ai-perf-badge ai-perf-badge--good">缓存率 {{ aiUsage.ttsCacheRate }}%</span>
            </div>
            <div class="ai-perf-bar">
              <i :style="{ width: aiUsage.ttsCacheRate + '%', background: '#d97706' }"></i>
            </div>
            <span class="ai-perf-hint">减少 TTS 重复合成，降低接口调用成本</span>
          </div>
        </div>
      </article>

      <article class="dashboard-panel">
        <div class="dashboard-panel__head">
          <h3>游客问题类型</h3>
          <span class="panel-metric">真实提问规则分类</span>
        </div>
        <div ref="sentimentChartRef" class="chart-surface"></div>
      </article>
    </section>

  </div>
</template>

<script setup>
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

// 按需注册，避免全量 echarts 把路由块撑到 1MB+
echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
import { ChatDotRound, Finished, LocationFilled, Refresh, Timer } from '@element-plus/icons-vue'
import request from '../utils/request'

const trendRange = ref('近7天')
const trendChartRef = ref()
const spotChartRef = ref()
const sentimentChartRef = ref()
let trendChart
let spotChart
let sentimentChart

const rangeOptions = ['近7天', '近30天']
const iconMap = {
  visitors: markRaw(LocationFilled),
  sessions: markRaw(ChatDotRound),
  response: markRaw(Timer),
  fences: markRaw(Finished)
}

const kpiCards = ref([
  { key: 'visitors', label: '今日服务人次', icon: iconMap.visitors, value: '0', meta: '持平', trendType: 'neutral' },
  { key: 'sessions', label: '当前活跃会话', icon: iconMap.sessions, value: '0', meta: '导览中 0', trendType: 'neutral' },
  { key: 'response', label: '平均 AI 响应耗时', icon: iconMap.response, value: '0s', meta: '目标内', trendType: 'up' },
  { key: 'fences', label: '围栏触发次数', icon: iconMap.fences, value: '0', meta: '持平', trendType: 'neutral' }
])

const trendSeries = {
  '近7天': {
    labels: [],
    values: []
  },
  '近30天': {
    labels: [],
    values: []
  }
}

const activeSpots = ref([])

const sentimentData = ref([])
const aiUsage = ref({
  totalTokens: 0,
  promptTokens: 0,
  completionTokens: 0,
  estimatedCost: '0.00',
  chatCalls: 0,
  ragSearches: 0,
  ttsChars: 0,
  asrMinutes: 0,
  ttsCacheHits: 0,
  ttsCacheRate: 0,
  p95FirstTokenSeconds: 0
})
const dashboardSummary = ref({ routeRequestCount: 0 })

const aiUsageTiles = computed(() => [
  { label: '知识检索回答', value: formatNumber(aiUsage.value.ragSearches) },
  { label: 'TTS 字符', value: formatCompact(aiUsage.value.ttsChars) },
  { label: 'TTS 缓存命中', value: formatNumber(aiUsage.value.ttsCacheHits) },
  { label: '路线推荐', value: formatNumber(dashboardSummary.value.routeRequestCount) }
])

const renderTrendChart = () => {
  if (!trendChartRef.value) return
  trendChart ||= echarts.init(trendChartRef.value)
  const current = trendSeries[trendRange.value] || { labels: [], values: [] }
  trendChart.setOption({
    grid: { left: 36, right: 18, top: 28, bottom: 34 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: current.labels,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#d7dde8' } },
      axisTick: { show: false },
      axisLabel: { color: '#64748b' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#eef2f7' } },
      axisLabel: { color: '#64748b' }
    },
    series: [
      {
        name: '服务人次',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: current.values,
        lineStyle: { width: 3, color: '#0f766e' },
        itemStyle: { color: '#0f766e' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(15, 118, 110, 0.24)' },
            { offset: 1, color: 'rgba(15, 118, 110, 0.02)' }
          ])
        }
      }
    ]
  })
}

const renderSpotChart = () => {
  if (!spotChartRef.value) return
  spotChart ||= echarts.init(spotChartRef.value)
  spotChart.setOption({
    grid: { left: 72, right: 20, top: 18, bottom: 22 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#eef2f7' } },
      axisLabel: { color: '#64748b' }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: activeSpots.value.map(item => item.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#334155', fontWeight: 600 }
    },
    series: [
      {
        name: '热度',
        type: 'bar',
        data: activeSpots.value.map(item => item.value),
        barWidth: 14,
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#0f766e' },
            { offset: 1, color: '#2563eb' }
          ])
        },
        label: {
          show: true,
          position: 'right',
          color: '#111827',
          fontWeight: 700,
          formatter: '{c} 人次'
        }
      }
    ]
  })
}

const renderSentimentChart = () => {
  if (!sentimentChartRef.value) return
  sentimentChart ||= echarts.init(sentimentChartRef.value)
  sentimentChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}%' },
    legend: {
      bottom: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#64748b' }
    },
    series: [
      {
        name: '游客问题类型',
        type: 'pie',
        radius: ['48%', '72%'],
        center: ['50%', '42%'],
        avoidLabelOverlap: true,
        label: { formatter: '{b}\n{c}%', color: '#334155', fontWeight: 600 },
        labelLine: { length: 10, length2: 8 },
        data: sentimentData.value
      }
    ]
  })
}

const renderCharts = () => {
  renderTrendChart()
  renderSpotChart()
  renderSentimentChart()
}

const resizeCharts = () => {
  trendChart?.resize()
  spotChart?.resize()
  sentimentChart?.resize()
}

const refreshDashboard = async () => {
  try {
    const res = await request.get('/admin/analytics/dashboard', {
      params: { range: trendRange.value === '近30天' ? 'month' : 'week' }
    })
    if (res.code === 'OK') {
      applyDashboardData(res.data)
    }
  } finally {
    await nextTick()
    renderCharts()
  }
}

const applyDashboardData = data => {
  kpiCards.value = (data.kpis || []).map(card => ({
    ...card,
    icon: iconMap[card.key] || iconMap.visitors
  }))
  trendSeries[trendRange.value] = data.trend || { labels: [], values: [] }
  activeSpots.value = data.activeSpots || []
  const sentiment = data.sentiment || {}
  sentimentData.value = [
    { name: '正向反馈', value: sentiment.positive || 0, itemStyle: { color: '#10b981' } },
    { name: '一般咨询', value: sentiment.neutral || 0, itemStyle: { color: '#2563eb' } },
    { name: '问路疑惑', value: sentiment.confused || 0, itemStyle: { color: '#d97706' } },
    { name: '抱怨投诉', value: sentiment.negative || 0, itemStyle: { color: '#dc2626' } }
  ]
  aiUsage.value = { ...aiUsage.value, ...(data.aiUsage || {}) }
  dashboardSummary.value = data.summary || { routeRequestCount: 0 }
}

const formatNumber = value => Number(value || 0).toLocaleString('zh-CN')

const formatCompact = value => {
  const number = Number(value || 0)
  if (number >= 1000) {
    return `${Math.round(number / 100) / 10}K`
  }
  return formatNumber(number)
}

watch(trendRange, () => refreshDashboard())

onMounted(() => {
  refreshDashboard()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  trendChart?.dispose()
  spotChart?.dispose()
  sentimentChart?.dispose()
})
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dashboard-panel,
.dashboard-kpi {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.dashboard-panel h3 {
  margin: 0;
  color: var(--text-main);
  letter-spacing: 0;
}

.dashboard-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 16px;
}

.dashboard-kpi {
  min-height: 100px;
  padding: 12px;
}

.dashboard-kpi__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-secondary);
  font-size: 13px;
}

.dashboard-kpi__head .el-icon {
  color: var(--primary);
}

.dashboard-kpi strong {
  display: block;
  margin-top: 8px;
  color: #06111f;
  font-size: 24px;
  line-height: 1;
  letter-spacing: 0;
}

.dashboard-kpi__foot {
  width: fit-content;
  min-height: 24px;
  display: inline-flex;
  align-items: center;
  margin-top: 10px;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.dashboard-kpi__foot.up {
  color: #0f766e;
  background: #e6f4f1;
}

.dashboard-kpi__foot.neutral {
  color: #1d4ed8;
  background: #eff6ff;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 0.85fr);
  gap: 16px;
}

.dashboard-panel {
  min-width: 0;
  padding: 16px;
}

.dashboard-panel--wide {
  min-height: 280px;
}

.dashboard-panel__head {
  min-height: 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.dashboard-panel h3 {
  font-size: 15px;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.panel-metric {
  min-height: 24px;
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 999px;
  color: #0f172a;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  font-size: 12px;
  font-weight: 700;
}

.chart-surface {
  width: 100%;
  height: 200px;
}

.chart-surface--trend {
  height: 220px;
}

.ai-consumption-panel {
  min-height: 270px;
}

.ai-ledger {
  display: grid;
  grid-template-columns: minmax(150px, 0.72fr) minmax(0, 1.28fr);
  gap: 14px;
}

.ai-ledger__main {
  min-height: 110px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 10px;
  border: 1px solid #d8eee9;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, #f4fffc, #f8fbff);
}

.ai-ledger__main span,
.ai-usage-tile span,
.ai-stage-row__meta span {
  color: #475569;
  font-size: 12px;
  font-weight: 600;
}

.ai-ledger__main strong {
  margin-top: 6px;
  color: #06111f;
  font-size: 26px;
  line-height: 1;
}

.ai-ledger__main em {
  margin-top: 10px;
  color: #0f766e;
  font-size: 13px;
  font-style: normal;
  font-weight: 700;
}

.ai-ledger__tiles {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.ai-usage-tile {
  min-height: 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 6px 10px;
  border: 1px solid #edf2f7;
  border-radius: var(--radius-sm);
  background: #ffffff;
}

.ai-usage-tile strong {
  margin-top: 4px;
  color: #0f172a;
  font-size: 16px;
}

.ai-token-flow {
  margin-top: 16px;
}

.ai-flow-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.ai-flow-header > span {
  color: #475569;
  font-size: 12px;
  font-weight: 600;
}

.ai-flow-total {
  color: #94a3b8 !important;
  font-size: 11px !important;
}

.ai-flow-bar {
  display: flex;
  height: 32px;
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.ai-flow-segment {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  transition: width 0.6s ease;
}

.ai-flow-segment span {
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding: 0 8px;
}

.ai-flow-in {
  background: #0f766e;
}

.ai-flow-out {
  background: #2563eb;
}

.ai-perf-metrics {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  margin-top: 16px;
  padding: 12px;
  border: 1px solid #eef2f7;
  border-radius: var(--radius-sm);
  background: #fbfdff;
}

.ai-perf-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ai-perf-label {
  color: #475569;
  font-size: 12px;
  font-weight: 600;
}

.ai-perf-value-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.ai-perf-value-row strong {
  color: #0f172a;
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
}

.ai-perf-unit {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 600;
}

.ai-perf-badge {
  margin-left: 4px;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
}

.ai-perf-badge--good {
  color: #0f766e;
  background: #e6f4f1;
}

.ai-perf-badge--fair {
  color: #d97706;
  background: #fff7ed;
}

.ai-perf-badge--poor {
  color: #dc2626;
  background: #fef2f2;
}

.ai-perf-bar {
  height: 6px;
  border-radius: 999px;
  background: #eef2f7;
  overflow: hidden;
}

.ai-perf-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  transition: width 0.6s ease;
}

.ai-perf-hint {
  color: #94a3b8;
  font-size: 11px;
}

.ai-perf-divider {
  background: #e2e8f0;
}

.ai-perf-ticks {
  position: relative;
  display: flex;
  justify-content: space-between;
  padding-top: 4px;
}

.ai-tick {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.ai-tick i {
  width: 2px;
  height: 8px;
  display: block;
  border-radius: 1px;
}

.ai-tick span {
  color: #94a3b8;
  font-size: 10px;
  font-weight: 600;
}

.ai-tick--poor i {
  background: #94a3b8;
}

.ai-tick--fair i {
  background: #d97706;
}

.ai-tick--good i {
  background: #0f766e;
}

.ai-tick-marker {
  position: absolute;
  top: -2px;
  width: 3px;
  height: 16px;
  background: #0f172a;
  border-radius: 999px;
  transform: translateX(-50%);
}

.route-heat-panel {
  min-height: 340px;
}

.route-legend {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  color: #0f172a;
  font-size: 12px;
  font-weight: 700;
}

.route-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.route-legend i {
  width: 24px;
  height: 5px;
  display: inline-block;
  border-radius: 999px;
}

.route-legend .hot {
  background: #dc2626;
}

.route-legend .warm {
  background: #d97706;
}

.route-legend .cool {
  background: #2563eb;
}

.route-heatmap {
  overflow: hidden;
  border: 1px solid #eef2f7;
  border-radius: var(--radius-md);
  background:
    linear-gradient(90deg, rgba(15, 118, 110, 0.04) 1px, transparent 1px),
    linear-gradient(0deg, rgba(37, 99, 235, 0.04) 1px, transparent 1px),
    #fbfdff;
  background-size: 40px 40px;
}

.route-heatmap svg {
  width: 100%;
  min-height: 240px;
  display: block;
}

.route-edge {
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
  opacity: 0.92;
}

.route-edge.hot {
  stroke: #dc2626;
  filter: url(#softGlow);
}

.route-edge.warm {
  stroke: #d97706;
}

.route-edge.cool {
  stroke: #2563eb;
  opacity: 0.6;
}

.route-node circle {
  fill: #ffffff;
  stroke: #0f172a;
  stroke-width: 4;
}

.route-node circle.hot {
  stroke: #dc2626;
}

.route-node circle.spot {
  stroke: #0f766e;
}

.route-node circle.service {
  stroke: #d97706;
}

.route-node circle.entry {
  stroke: #2563eb;
}

.route-node text {
  fill: #0f172a;
  font-size: 14px;
  font-weight: 700;
  text-anchor: middle;
}

.route-node__meta {
  fill: #64748b !important;
  font-size: 11px !important;
  font-weight: 600 !important;
}

@media (max-width: 1280px) {
  .dashboard-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 980px) {
  .dashboard-kpis,
  .dashboard-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-panel--wide,
  .ai-consumption-panel {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .dashboard-kpis,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .ai-ledger {
    grid-template-columns: 1fr;
  }
}
</style>
