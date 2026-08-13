<template>
  <div class="alert-panel">
    <header class="panel-heading">
      <div>
        <h2>{{ $t('publicOpinion.alert.title') }}</h2>
        <p>{{ $t('publicOpinion.alert.description') }}</p>
      </div>
      <el-tag size="small" effect="plain">
        {{ $t('publicOpinion.demoData') }}
      </el-tag>
    </header>

    <div class="alert-list">
      <article v-for="item in alerts" :key="item.id" class="alert-card">
        <div class="alert-title">
          <h3>{{ item.name }}</h3>
          <el-tag :type="riskType(item.risk)" size="small" effect="plain">
            {{ $t('publicOpinion.alert.risk') }}：
            {{ $t(`publicOpinion.risk.${item.risk}`) }}
          </el-tag>
        </div>

        <div class="alert-metrics">
          <div>
            <span>{{ $t('publicOpinion.alert.heat') }}</span>
            <strong>{{ item.heat }}</strong>
          </div>
          <div>
            <span>{{ $t('publicOpinion.alert.growth24h') }}</span>
            <strong>{{ item.growth24h }}</strong>
          </div>
          <div>
            <span>{{ $t('publicOpinion.alert.negativeRatio') }}</span>
            <strong>{{ item.negativeRatio }}</strong>
          </div>
          <div>
            <span>{{ $t('publicOpinion.alert.status') }}</span>
            <strong>{{ item.status }}</strong>
          </div>
        </div>

        <div class="alert-reason">
          <span>{{ $t('publicOpinion.alert.reason') }}</span>
          <p>{{ item.reason }}</p>
        </div>
        <div class="alert-time">
          {{ $t('publicOpinion.alert.updatedAt') }}：{{ item.updatedAt }}
        </div>
      </article>
    </div>
  </div>
</template>

<script>
import { alerts } from '../mock';

export default {
  name: 'AlertPanel',
  data() {
    return { alerts };
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
.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;

  h2 {
    margin: 0;
    color: $color_title;
    font-size: 17px;
  }

  p {
    margin: 7px 0 0;
    color: #8a91a1;
    font-size: 13px;
  }
}

.alert-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.alert-card {
  min-width: 0;
  padding: 20px;
  background: #fff;
  border: 1px solid #edf0f5;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(22, 52, 156, 0.05);
}

.alert-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  h3 {
    margin: 0;
    color: $color_title;
    font-size: 15px;
  }
}

.alert-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 18px 0;
  padding: 14px 0;
  background: #f7f8fa;
  border-radius: 6px;

  div {
    min-width: 0;
    padding: 0 12px;
    border-right: 1px solid #e4e7ed;

    &:last-child {
      border-right: 0;
    }
  }

  span,
  strong {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    margin-bottom: 7px;
    color: #8a91a1;
    font-size: 12px;
  }

  strong {
    color: #4f5668;
    font-size: 14px;
  }
}

.alert-reason {
  span {
    color: #8a91a1;
    font-size: 12px;
  }

  p {
    margin: 6px 0 0;
    color: #606778;
    font-size: 13px;
    line-height: 21px;
  }
}

.alert-time {
  margin-top: 13px;
  color: #a0a6b3;
  font-size: 12px;
  text-align: right;
}

@media (max-width: 1180px) {
  .alert-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .alert-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));

    div:nth-child(2) {
      border-right: 0;
    }

    div:nth-child(-n + 2) {
      margin-bottom: 12px;
    }
  }
}
</style>
