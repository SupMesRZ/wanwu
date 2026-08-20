<template>
  <div class="mcp-management">
    <div class="common_bg">
      <section class="campus-capability-overview">
        <div class="overview-copy">
          <span>CAMPUS MCP CONNECTION</span>
          <strong>校园能力接入概览</strong>
          <small>在原 MCP 管理能力上，逐步连接学校知识与业务系统。</small>
        </div>
        <div class="campus-capability-list">
          <span
            v-for="item in campusCapabilities"
            :key="item.name"
            :class="{ connected: item.connected }"
          >
            <i></i>
            {{ item.name }}
            <small>{{ item.connected ? '已接入' : '规划接入' }}</small>
          </span>
        </div>
      </section>

      <!-- tabs -->
      <div class="tabs tabs-x-top">
        <div :class="['tab', { active: tabActive === 0 }]" @click="tabClick(0)">
          {{ $t('common.button.import') }}MCP
        </div>
        <div :class="['tab', { active: tabActive === 1 }]" @click="tabClick(1)">
          {{ $t('common.button.add') }}MCP
        </div>
      </div>

      <customize ref="customize" v-if="tabActive === 0" />
      <server ref="server" v-if="tabActive === 1" />
    </div>
  </div>
</template>
<script>
import customize from './integrate';
import server from './server';
export default {
  name: 'McpTabs',
  data() {
    return {
      tabActive: 0,
      campusCapabilities: [
        { name: '校园知识服务', connected: true },
        { name: '教务 MCP', connected: false },
        { name: '学工 MCP', connected: false },
        { name: '图书馆 MCP', connected: false },
        { name: '后勤 MCP', connected: false },
      ],
      mcpTabObj: {
        integrate: 0,
        server: 1,
      },
    };
  },
  watch: {
    $route: {
      handler(val) {
        // keep-alive 下组件不会销毁，离开当前页面 watcher 仍然触发触发，需忽略非当前页面的路由
        if (val.path === '/mcpService') this.setInitTab();
      },
      deep: true,
    },
  },
  mounted() {
    this.setInitTab();
  },
  methods: {
    setInitTab() {
      const { mcp } = this.$route.query || {};
      this.tabActive = this.mcpTabObj[mcp] || 0;
    },
    tabClick(status) {
      this.tabActive = status;
    },
  },
  components: {
    customize,
    server,
  },
};
</script>
<style lang="scss" scoped>
.campus-capability-overview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin: 0 20px 12px;
  padding: 14px 18px;
  border: 1px solid #dce9f8;
  border-radius: 12px;
  background: linear-gradient(110deg, #f3f8ff, #eef6ff);
}

.overview-copy {
  display: flex;
  min-width: 250px;
  flex-direction: column;

  > span {
    color: #5480b6;
    font-size: 9px;
    font-weight: 700;
    letter-spacing: 1px;
  }

  strong {
    margin: 3px 0;
    color: #214c82;
    font-size: 16px;
  }

  small {
    color: #70849e;
    font-size: 10px;
  }
}

.campus-capability-list {
  display: flex;
  justify-content: flex-end;
  gap: 7px;
  flex-wrap: wrap;

  > span {
    display: inline-flex;
    align-items: center;
    padding: 6px 8px;
    color: #7b6a4d;
    font-size: 10px;
    border: 1px solid #eadfca;
    border-radius: 7px;
    background: #fffaf1;

    > i {
      width: 6px;
      height: 6px;
      margin-right: 5px;
      border-radius: 50%;
      background: #d49b3d;
    }

    > small {
      margin-left: 5px;
      color: #a48c67;
      font-size: 9px;
    }

    &.connected {
      color: #287359;
      border-color: #cce8dc;
      background: #f1faf6;

      > i {
        background: #25a476;
      }

      > small {
        color: #4b8c73;
      }
    }
  }
}

::v-deep .scroll-card-container {
  max-height: calc(100vh - 250px);
}

@media (max-width: 900px) {
  .campus-capability-overview {
    align-items: flex-start;
    flex-direction: column;
  }

  .campus-capability-list {
    justify-content: flex-start;
  }
}
</style>
