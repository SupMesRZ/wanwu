<template>
  <div class="overview-cards">
    <div v-for="item in cards" :key="item.key" class="metric-card">
      <div class="metric-label">{{ item.label }}</div>
      <div class="metric-value">{{ item.value }}</div>
      <div class="metric-change">
        <span>{{ item.compareLabel }}</span>
        <strong :class="{ decrease: item.change.startsWith('-') }">
          {{ item.change }}
        </strong>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'OverviewCards',
  props: {
    items: {
      type: Array,
      default: () => [],
    },
  },
  computed: {
    cards() {
      return this.items.map(item => ({
        ...item,
        label: this.$t(`publicOpinion.overview.${item.key}`),
        compareLabel: this.$t(
          item.compare === 'lastWeek'
            ? 'publicOpinion.overview.comparedLastWeek'
            : 'publicOpinion.overview.comparedYesterday',
        ),
      }));
    },
  },
};
</script>

<style lang="scss" scoped>
.overview-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  min-width: 0;
  padding: 20px;
  background: #fff;
  border: 1px solid #edf0f5;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(22, 52, 156, 0.05);
}

.metric-label {
  color: #686f82;
  font-size: 13px;
}

.metric-value {
  margin: 10px 0 12px;
  color: $color_title;
  font-size: 28px;
  font-weight: 600;
  line-height: 36px;
}

.metric-change {
  display: flex;
  gap: 8px;
  color: #9aa0ad;
  font-size: 12px;

  strong {
    color: #e56a5d;
    font-weight: 500;
  }

  .decrease {
    color: #42a579;
  }
}

@media (max-width: 1100px) {
  .overview-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .overview-cards {
    grid-template-columns: 1fr;
  }
}
</style>
