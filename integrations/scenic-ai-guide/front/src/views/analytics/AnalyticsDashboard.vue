<template>
  <div class="analytics-container">
    <!-- 时间筛选 -->
    <div class="filter-bar">
      <div class="time-tabs">
        <button
          v-for="tab in timeTabs"
          :key="tab.value"
          class="tab-btn"
          :class="{ active: activeTimeTab === tab.value }"
          @click="activeTimeTab = tab.value"
        >
          {{ tab.label }}
        </button>
      </div>
      <div class="date-range" v-if="activeTimeTab === 'custom'">
        <input type="date" class="form-control" v-model="dateRange.start" />
        <span>至</span>
        <input type="date" class="form-control" v-model="dateRange.end" />
        <button class="btn btn-primary btn-sm" @click="loadData">查询</button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="metric-grid">
      <div v-for="card in metrics" :key="card.key" class="metric-card">
        <div class="metric-label">{{ card.label }}</div>
        <div class="metric-value">{{ card.value }}</div>
        <div class="metric-trend" :class="card.trend >= 0 ? 'up' : 'down'">
          {{ card.trend >= 0 ? '+' : '' }}{{ card.trend }}% 环比
        </div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="charts-grid">
      <div class="chart-card wide">
        <div class="chart-header">
          <h4>服务人次趋势</h4>
          <span class="chart-sub">单位：人次</span>
        </div>
        <div class="chart-body bar-chart">
          <div v-for="item in trendData" :key="item.date" class="bar-item">
            <div class="bar" :style="{ height: item.percent + '%' }">
              <span class="bar-tip">{{ item.value }}</span>
            </div>
            <span class="bar-label">{{ item.date }}</span>
          </div>
        </div>
      </div>

      <div class="chart-card">
        <div class="chart-header">
          <h4>问答来源分布</h4>
        </div>
        <div class="chart-body pie-chart">
          <svg viewBox="0 0 100 100" class="pie-svg">
            <circle cx="50" cy="50" r="40" fill="transparent" stroke="#e2e8f0" stroke-width="14"/>
            <circle cx="50" cy="50" r="40" fill="transparent" stroke="#10b981" stroke-width="14"
              :stroke-dasharray="`${faqRate * 2.51} 251.2`" stroke-dashoffset="0" transform="rotate(-90 50 50)"/>
            <circle cx="50" cy="50" r="40" fill="transparent" stroke="#2563eb" stroke-width="14"
              :stroke-dasharray="`${ragRate * 2.51} 251.2`" :stroke-dashoffset="`-${faqRate * 2.51}`" transform="rotate(-90 50 50)"/>
            <circle cx="50" cy="50" r="40" fill="transparent" stroke="#f59e0b" stroke-width="14"
              :stroke-dasharray="`${llmRate * 2.51} 251.2`" :stroke-dashoffset="`-${(faqRate + ragRate) * 2.51}`" transform="rotate(-90 50 50)"/>
          </svg>
          <div class="pie-legend">
            <div class="legend-row"><span class="dot" style="background:#10b981"/>FAQ {{ faqRate }}%</div>
            <div class="legend-row"><span class="dot" style="background:#2563eb"/>RAG {{ ragRate }}%</div>
            <div class="legend-row"><span class="dot" style="background:#f59e0b"/>其他/兜底 {{ fallbackRate }}%</div>
          </div>
        </div>
      </div>

      <div class="chart-card">
        <div class="chart-header">
          <h4>情感分布</h4>
        </div>
        <div class="chart-body sentiment-chart">
          <div class="senti-bar">
            <div class="senti-fill positive" :style="{ width: sentiment.positive + '%' }">
              <span class="senti-label">正面 {{ sentiment.positive }}%</span>
            </div>
          </div>
          <div class="senti-bar">
            <div class="senti-fill neutral" :style="{ width: sentiment.neutral + '%' }">
              <span class="senti-label">中性 {{ sentiment.neutral }}%</span>
            </div>
          </div>
          <div class="senti-bar">
            <div class="senti-fill negative" :style="{ width: sentiment.negative + '%' }">
              <span class="senti-label">负面 {{ sentiment.negative }}%</span>
            </div>
          </div>
        </div>
      </div>

      <div class="chart-card">
        <div class="chart-header">
          <h4>游客提问词云</h4>
          <span class="chart-sub">样本 {{ wordCloudCount }} 条</span>
        </div>
        <div class="chart-body cloud-body">
          <template v-if="wordCloud.length">
            <span
              v-for="word in wordCloud"
              :key="word.text"
              class="cloud-word"
              :class="'source-' + word.source.toLowerCase()"
              :style="{ fontSize: wordFontSize(word.weight) + 'px' }"
              :title="`${word.text}：${word.weight} 次`"
            >{{ word.text }}</span>
          </template>
          <span v-else class="cloud-empty">暂无提问数据</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '../../utils/request'

const timeTabs = [
  { label: '今日', value: 'today' },
  { label: '近7天', value: 'week' },
  { label: '近30天', value: 'month' },
  { label: '自定义', value: 'custom' }
]
const activeTimeTab = ref('today')
const dateRange = ref({ start: '', end: '' })

const metrics = ref([
  { key: 'visitors', label: '服务人次', value: '0', trend: 0 },
  { key: 'sessions', label: '会话数', value: '0', trend: 0 },
  { key: 'messages', label: '消息数', value: '0', trend: 0 },
  { key: 'avgResponse', label: '平均响应(ms)', value: '0', trend: 0 },
  { key: 'activeVisitors', label: '活跃游客', value: '0', trend: 0 },
  { key: 'fenceTriggers', label: '围栏触发', value: '0', trend: 0 }
])

const trendData = ref([])
const faqRate = ref(35)
const ragRate = ref(52)
const fallbackRate = ref(0)
const sentiment = ref({ positive: 65, neutral: 28, negative: 7 })
const wordCloud = ref([])
const wordCloudCount = ref(0)

const wordFontSize = (weight) => {
  const max = Math.max(...wordCloud.value.map(item => item.weight), 1)
  return 12 + Math.round((weight / max) * 18)
}

const loadData = async () => {
  try {
    const res = await request.get('/admin/analytics/dashboard', {
      params: buildQueryParams()
    })
    if (res.code === 'OK') {
      const d = res.data
      const kpiMap = Object.fromEntries((d.kpis || []).map(item => [item.key, item]))
      const summary = d.summary || {}
      metrics.value[0].value = kpiMap.visitors?.value || '0'
      metrics.value[1].value = kpiMap.sessions?.value || '0'
      metrics.value[2].value = String(summary.messageCount || 0)
      metrics.value[3].value = kpiMap.response?.value || '0s'
      metrics.value[4].value = String(summary.activeVisitorCount || 0)
      metrics.value[5].value = kpiMap.fences?.value || '0'
      trendData.value = d.trend?.labels?.map((date, i) => ({
        date,
        value: d.trend.values[i],
        percent: Math.max(10, (d.trend.values[i] / Math.max(...d.trend.values, 1)) * 100)
      })) || []
      faqRate.value = d.qaSource?.faq || 0
      ragRate.value = d.qaSource?.rag || 0
      fallbackRate.value = (d.qaSource?.fallback || 0) + (d.qaSource?.guideScript || 0) + (d.qaSource?.other || 0)
      sentiment.value = {
        positive: d.sentiment?.positive || 0,
        neutral: d.sentiment?.neutral || 0,
        negative: d.sentiment?.negative || 0
      }
    }
    const cloudRes = await request.get('/admin/analytics/word-cloud', {
      params: buildQueryParams()
    })
    if (cloudRes.code === 'OK') {
      wordCloud.value = cloudRes.data?.words || []
      wordCloudCount.value = cloudRes.data?.messageCount || 0
    }
  } catch (err) {
    console.error('加载运营数据失败', err)
  }
}

const buildQueryParams = () => {
  if (activeTimeTab.value === 'custom') {
    return {
      range: 'custom',
      startDate: dateRange.value.start,
      endDate: dateRange.value.end
    }
  }
  const rangeMap = {
    today: 'today',
    week: 'week',
    month: 'month'
  }
  return { range: rangeMap[activeTimeTab.value] || 'today' }
}

onMounted(loadData)
</script>

<style scoped>
.analytics-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.time-tabs {
  display: flex;
  gap: 4px;
  background-color: var(--bg-hover);
  padding: 4px;
  border-radius: var(--radius-sm);
}

.tab-btn {
  padding: 6px 14px;
  border: none;
  background: none;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn.active {
  background-color: #fff;
  color: var(--text-main);
  box-shadow: var(--shadow-sm);
}

.date-range {
  display: flex;
  align-items: center;
  gap: 8px;
}

.date-range .form-control {
  width: 140px;
  padding: 6px 10px;
}

/* 统计卡片 */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
}

.metric-card {
  background-color: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 18px 16px;
  box-shadow: var(--shadow-sm);
}

.metric-label {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 500;
  margin-bottom: 8px;
}

.metric-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1;
  margin-bottom: 8px;
}

.metric-trend {
  font-size: 11.5px;
  font-weight: 600;
}

.metric-trend.up {
  color: var(--primary);
}

.metric-trend.down {
  color: var(--danger);
}

/* 图表区 */
.charts-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 20px;
}

.chart-card {
  background-color: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.chart-header h4 {
  font-size: 14px;
  font-weight: 700;
}

.chart-sub {
  font-size: 11px;
  color: var(--text-muted);
}

/* 柱状图 */
.bar-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  height: 200px;
  gap: 12px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--border);
}

.bar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-width: 40px;
}

.bar {
  width: 28px;
  background: linear-gradient(180deg, var(--primary) 0%, rgba(16, 185, 129, 0.35) 100%);
  border-radius: 4px 4px 0 0;
  position: relative;
  transition: all 0.3s;
  min-height: 4px;
}

.bar:hover {
  background: var(--primary-hover);
}

.bar-tip {
  position: absolute;
  top: -24px;
  left: 50%;
  transform: translateX(-50%);
  background-color: var(--text-main);
  color: #fff;
  padding: 2px 6px;
  font-size: 10px;
  border-radius: 4px;
  white-space: nowrap;
  opacity: 0;
  transition: opacity 0.2s;
  pointer-events: none;
  font-weight: 600;
}

.bar:hover .bar-tip {
  opacity: 1;
}

.bar-label {
  font-size: 11px;
  color: var(--text-secondary);
}

/* 饼图 */
.pie-chart {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.pie-svg {
  width: 130px;
  height: 130px;
}

.pie-legend {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* 情感图 */
.sentiment-chart {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.senti-bar {
  height: 32px;
  background-color: var(--bg-hover);
  border-radius: 6px;
  overflow: hidden;
}

.senti-fill {
  height: 100%;
  border-radius: 6px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  transition: width 0.6s ease;
  min-width: 60px;
}

.senti-fill.positive {
  background: linear-gradient(90deg, #10b981, #34d399);
}

.senti-fill.neutral {
  background: linear-gradient(90deg, #94a3b8, #cbd5e1);
}

.senti-fill.negative {
  background: linear-gradient(90deg, #ef4444, #f87171);
}

.senti-label {
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
}

/* 词云 */
.cloud-body {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 10px 14px;
  min-height: 200px;
  align-content: center;
}

.cloud-word {
  font-weight: 600;
  line-height: 1.2;
  cursor: default;
  transition: transform 0.2s;
}

.cloud-word:hover {
  transform: scale(1.12);
}

.cloud-word.source-spot {
  color: var(--primary);
}

.cloud-word.source-facility {
  color: #2563eb;
}

.cloud-word.source-tag {
  color: #d97706;
}

.cloud-word.source-ngram {
  color: var(--text-secondary);
}

.cloud-empty {
  font-size: 12px;
  color: var(--text-muted);
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  .charts-grid {
    grid-template-columns: 1fr;
  }
}
</style>
