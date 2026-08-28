<template>
  <div class="mcp-content-box customize">
    <div class="mcp-content">
      <div class="card-search card-search-cust">
        <div>
          <p class="card-search-des" style="display: flex; align-items: center">
            <span>{{ $t('tool.server.slogan') }}</span>
          </p>
        </div>
        <div>
          <search-input
            :placeholder="$t('tool.server.search')"
            ref="searchInput"
            @handleSearch="fetchList"
          />
        </div>
      </div>

      <div class="card-box scroll-card-container">
        <div class="card card-item-create">
          <div class="app-card-create" @click="handleAddServer('')">
            <div class="create-img-wrap">
              <img
                class="create-img"
                src="@/assets/imgs/card_create_icon_mcp.svg"
                alt=""
              />
            </div>
            <span>{{ $t('tool.server.create') }}</span>
          </div>
        </div>
        <div
          v-if="list && list.length"
          class="card"
          v-for="(item, index) in list"
          :key="index"
          @click.stop="handleClick(item.mcpServerId)"
        >
          <div class="card-title">
            <img
              class="common-card-logo"
              :src="
                item.avatar && item.avatar.path
                  ? avatarSrc(item.avatar.path)
                  : defaultAvatar
              "
              alt=""
            />
            <div class="mcp_detailBox">
              <span class="mcp_name">{{ item.name }}</span>
              <span class="mcp_from from_tag">
                <label>
                  {{ $t('tool.server.count', { count: item.toolNum }) }}
                </label>
                <label v-if="item.kind === 'campus'">
                  校园业务 MCP ·
                  {{
                    item.authMode === 'campus_execution_context'
                      ? '用户上下文认证'
                      : ''
                  }}
                </label>
              </span>
            </div>
            <el-dropdown placement="bottom">
              <span class="el-dropdown-link">
                <i class="el-icon-more" @click.stop />
              </span>
              <el-dropdown-menu slot="dropdown" style="margin-top: -10px">
                <el-dropdown-item @click.native="handleAddServer(item)">
                  {{ $t('common.button.edit') }}
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="item.kind !== 'campus'"
                  @click.native="handleDelete(item)"
                >
                  {{ $t('common.button.delete') }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </el-dropdown>
          </div>
          <div class="card-des">{{ item.desc }}</div>
        </div>
      </div>

      <el-empty
        class="noData"
        v-if="!(list && list.length)"
        :description="$t('common.noData')"
      />
    </div>
    <addDialog ref="addDialog" @handleFetch="fetchList()" />
  </div>
</template>
<script>
import addDialog from './addDialog.vue';
import SearchInput from '@/components/searchInput.vue';
import { getServerList, deleteServer } from '@/api/mcp';
import { avatarSrc } from '@/utils/util';
export default {
  name: 'McpServer',
  components: { SearchInput, addDialog },
  data() {
    return {
      defaultAvatar: require('@/assets/imgs/mcp_active.svg'),
      list: [],
    };
  },
  mounted() {
    this.fetchList();
  },
  methods: {
    avatarSrc,
    fetchList() {
      const searchInput = this.$refs.searchInput;
      const params = {
        name: searchInput.value,
      };
      getServerList(params).then(res => {
        this.list = res.data.list || [];
      });
    },
    handleClick(mcpServerId) {
      this.$router.push({
        path: `/mcpService/detail/server?mcpServerId=${mcpServerId}`,
      });
    },
    handleAddServer(item) {
      this.$refs.addDialog.showDialog(item);
    },
    handleDelete(item) {
      this.$confirm(
        this.$t('tool.server.deleteHint', { name: item.name }),
        this.$t('common.confirm.title'),
        {
          confirmButtonText: this.$t('common.confirm.confirm'),
          cancelButtonText: this.$t('common.confirm.cancel'),
          dangerouslyUseHTMLString: true,
          type: 'warning',
          center: true,
        },
      ).then(async () => {
        deleteServer({
          mcpServerId: item.mcpServerId,
        }).then(res => {
          if (res.code === 0) {
            this.$message.success(this.$t('common.info.delete'));
            this.fetchList();
          } else {
            this.$message.error(res.msg || this.$t('common.info.deleteErr'));
          }
        });
      });
    },
  },
};
</script>
<style lang="scss">
.mcp-content-box .noData {
  width: 100%;
  text-align: center;
  margin-top: -60px;
  ::v-deep .el-empty__description p {
    color: #b3b1bc;
  }
}
</style>
