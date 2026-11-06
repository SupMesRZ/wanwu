<template>
  <div class="map-workbench">
    <div v-if="drafts.length === 0" class="empty-overlay">
      <div class="empty-content">
        <el-icon class="empty-icon"><MapLocation /></el-icon>
        <h2>暂无地图草稿</h2>
        <p>当前景区还没有创建地图草稿，请创建一个新草稿来开始配置地图。</p>
        <el-button type="primary" size="large" @click="showCreateDraftDialog">创建首个草稿</el-button>
      </div>
    </div>

    <div ref="mapEl" class="map-canvas"></div>

    <div class="map-topbar">
      <div class="map-topbar-left">
        <form class="map-search" @submit.prevent="handleMapSearch">
          <el-icon><Search /></el-icon>
          <input
            v-model.trim="mapSearchKeyword"
            name="mapSearchKeyword"
            placeholder="搜索地点或输入经纬度"
            :disabled="mapSearchBusy"
          />
          <button type="submit" :disabled="mapSearchBusy">定位</button>
          <button type="button" :disabled="mapSearchBusy" @click="locateBrowserPosition">
            <el-icon><Aim /></el-icon>
          </button>
        </form>
        <div class="map-layer-controls">
          <button
            v-for="mode in mapLayerModes"
            :key="mode.key"
            :class="{ active: mapLayerMode === mode.key }"
            type="button"
            @click="setMapLayerMode(mode.key)"
          >
            {{ mode.label }}
          </button>
          <button :class="{ active: !showMapLabels }" type="button" @click="toggleMapLabels">
            {{ showMapLabels ? '隐藏标注' : '显示标注' }}
          </button>
        </div>
      </div>
      <div class="draft-actions">
        <span class="draft-label">地图草稿:</span>
        <el-select
          v-model="currentDraftId"
          placeholder="选择地图草稿"
          size="small"
          style="width: 170px; margin-right: 8px;"
          popper-class="workbench-select-popper draft-select-popper"
          @change="handleDraftChange"
        >
          <el-option
            v-for="d in drafts"
            :key="d.id"
            :label="d.name"
            :value="d.id"
          >
            <div class="draft-option">
              <div class="draft-option__main">
                <div class="draft-option__title">
                  <span>{{ d.name }}</span>
                  <el-tag
                    :type="d.status === 'ONLINE' ? 'success' : 'info'"
                    size="small"
                    effect="plain"
                  >
                    {{ d.status === 'ONLINE' ? 'ONLINE' : '草稿' }}
                  </el-tag>
                </div>
                <div class="draft-option__meta">
                  创建 {{ formatDraftDate(d.createdAt) }}
                  <span v-if="d.lastSavedAt"> · 保存 {{ formatDraftDate(d.lastSavedAt) }}</span>
                </div>
              </div>
              <button
                v-if="d.status !== 'ONLINE'"
                class="draft-option__delete"
                type="button"
                title="删除草稿"
                @click.stop.prevent="confirmDeleteDraft(d)"
              >
                <el-icon><Delete /></el-icon>
              </button>
            </div>
          </el-option>
        </el-select>

        <el-tag
          v-if="currentDraft"
          :type="currentDraft.status === 'ONLINE' ? 'success' : 'warning'"
          effect="dark"
          size="small"
          style="margin-right: 12px;"
        >
          {{ currentDraft.status === 'ONLINE' ? 'ONLINE' : 'DRAFT' }}
        </el-tag>

        <el-button-group style="margin-right: 12px;">
          <el-button size="small" @click="showCreateDraftDialog">新建</el-button>
          <el-button size="small" :disabled="!currentDraft" @click="copyDraft">复制</el-button>
          <el-button size="small" type="danger" :disabled="!currentDraft || currentDraft.status === 'ONLINE'" @click="confirmDeleteDraft()">删除</el-button>
        </el-button-group>

        <span :class="{ dirty: dirtyCount > 0 }" style="margin-right: 8px;">{{ draftStateText }}</span>
        <el-button size="small" type="primary" :disabled="dirtyCount <= 0" @click="saveDraft()">保存地图</el-button>
        <el-button size="small" type="success" :disabled="!currentDraft || currentDraft.status === 'ONLINE'" @click="publishCurrentDraft">发布上线</el-button>
      </div>
    </div>

    <div class="map-status-chip">
      <span>{{ activeModeLabel }}</span>
      <strong>{{ cursorText }}</strong>
    </div>

    <aside class="tool-rail" aria-label="地图工具">
      <button
        v-for="tool in tools"
        :key="tool.key"
        :class="['rail-button', { active: activeMode === tool.key }]"
        @click="setMode(tool.key)"
      >
        <el-icon><component :is="tool.icon" /></el-icon>
        <span>{{ tool.label }}</span>
      </button>
    </aside>

    <aside v-if="activeMode !== 'select' || selectedAsset" class="work-panel">
      <header class="panel-head">
        <div class="panel-title-row">
          <strong>{{ panelTitle }}</strong>
          <button class="panel-close" type="button" @click="closePanel" title="关闭">
            <el-icon><Close /></el-icon>
          </button>
        </div>
      </header>

      <section v-if="activeMode === 'spot'" class="panel-section">
        <label class="field">
          <span>景点名称</span>
          <input v-model="spotDraft.name" name="spotDraftName" class="editor-input" />
        </label>
        <div class="segmented">
          <button :class="{ active: spotDraft.fenceType === 'polygon' }" @click="spotDraft.fenceType = 'polygon'">多边形</button>
          <button :class="{ active: spotDraft.fenceType === 'circle' }" @click="spotDraft.fenceType = 'circle'">圆形</button>
        </div>
        <label v-if="spotDraft.fenceType === 'circle'" class="field">
          <span>半径</span>
          <input v-model.number="spotDraft.radius" name="spotDraftRadius" class="editor-input" type="number" min="5" />
        </label>
      </section>

      <section v-else-if="activeMode === 'facility'" class="panel-section">
        <label class="field">
          <span>服务名称</span>
          <input v-model="facilityDraft.name" name="facilityDraftName" class="editor-input" />
        </label>
        <label class="field">
          <span>服务类型</span>
          <el-select v-model="facilityDraft.facilityType" size="small" popper-class="workbench-select-popper">
            <el-option v-for="option in facilityTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </label>
      </section>

      <section v-else-if="activeMode === 'route'" class="panel-section route-section">
        <div class="route-steps">
          <span :class="{ active: routeDraft.step >= 1 }">1 起点</span>
          <span :class="{ active: routeDraft.step >= 2 }">2 路径</span>
          <span :class="{ active: routeDraft.step >= 3 }">3 保存</span>
        </div>
        <p class="hint">{{ routeHint }}</p>
        <div class="route-field-grid">
          <label class="field">
            <span>起点</span>
            <div class="input-with-action">
              <input :value="routeDraft.from?.name || '-'" name="routeDraftFrom" class="editor-input" readonly />
              <button
                v-if="canSetEndpoint('from')"
                class="btn-action"
                @click="setEndpointToSelected('from')"
              >设为起点</button>
            </div>
          </label>
          <label v-if="routeDraft.from?.hitKind === 'blank' || routeDraft.from?.endpointType === 'MAP_POINT'" class="field">
            <span>起点名称</span>
            <input v-model="routeDraft.from.name" class="editor-input" placeholder="自定义地图点名称" @input="markRouteEndpointNameDirty" />
          </label>
          <label class="field">
            <span>终点</span>
            <div class="input-with-action">
              <input :value="routeDraft.to?.name || '-'" name="routeDraftTo" class="editor-input" readonly />
              <button
                v-if="canSetEndpoint('to')"
                class="btn-action"
                @click="setEndpointToSelected('to')"
              >设为终点</button>
            </div>
          </label>
          <label v-if="routeDraft.to?.hitKind === 'blank' || routeDraft.to?.endpointType === 'MAP_POINT'" class="field">
            <span>终点名称</span>
            <input v-model="routeDraft.to.name" class="editor-input" placeholder="自定义地图点名称" @input="markRouteEndpointNameDirty" />
          </label>
          <label class="field">
            <span>长度</span>
            <input v-model.number="routeDraft.lengthMeter" name="routeDraftLengthMeter" class="editor-input" type="number" min="0" />
          </label>
          <label class="field">
            <span>耗时</span>
            <input v-model.number="routeDraft.durationMin" name="routeDraftDurationMin" class="editor-input" type="number" min="1" />
          </label>
          <label class="field">
            <span>类型</span>
            <el-select
              v-model="routeDraft.routeType"
              size="small"
              filterable
              allow-create
              default-first-option
              popper-class="workbench-select-popper"
            >
              <el-option v-for="option in routeTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </label>
          <label class="field switch-field">
            <span>收费</span>
            <el-switch v-model="routeDraft.paid" size="small" />
          </label>
        </div>
        <label class="field">
          <span>AI 描述</span>
          <textarea v-model="routeDraft.aiNote" name="routeDraftAiNote" class="editor-textarea" rows="4" />
        </label>
        <div class="panel-actions route-actions">
          <button class="btn" @click="undoRoutePoint">撤销</button>
          <button class="btn primary" :disabled="!canCommitRoute" @click="commitRoute">保存路线</button>
        </div>
      </section>

      <section v-else-if="selectedAsset" class="panel-section detail-section">
        <template v-if="selectedAsset.kind === 'spot'">
          <label class="field">
            <span>景点名称</span>
            <input v-model="selectedAsset.name" name="spotName" class="editor-input" @input="markDirtyAndRender" />
          </label>
          <label class="field">
            <span>景点描述</span>
            <textarea v-model="selectedAsset.description" name="spotDescription" class="editor-textarea" rows="3" @input="markDirty" />
          </label>
          <label class="field">
            <span>标签</span>
            <el-select
              v-model="selectedAsset.tags"
              multiple
              collapse-tags
              collapse-tags-tooltip
              clearable
              filterable
              placeholder="从标签词表选择"
              @change="markDirty"
            >
              <el-option
                v-for="tag in tagOptions"
                :key="tag.code"
                :label="tag.name"
                :value="tag.code"
              />
            </el-select>
          </label>
          <div class="route-field-grid">
            <label class="field">
              <span>纬度</span>
              <input :value="Number(selectedAsset.labelPoint?.[0] || 0).toFixed(6)" name="spotLatitude" class="editor-input" readonly />
            </label>
            <label class="field">
              <span>经度</span>
              <input :value="Number(selectedAsset.labelPoint?.[1] || 0).toFixed(6)" name="spotLongitude" class="editor-input" readonly />
            </label>
            <label class="field">
              <span>顶点数</span>
              <input :value="selectedAsset.polygon?.length || 0" name="spotPolygonCount" class="editor-input" readonly />
            </label>
            <label class="field">
              <span>状态</span>
              <el-switch v-model="selectedAsset.status" :active-value="1" :inactive-value="0" size="small" @change="markDirtyAndRender" />
            </label>
          </div>
          <div class="segmented">
            <button
              :class="{ active: selectedAsset.fenceType !== '圆形' }"
              :disabled="isOnlineDraft || !isEditingFence(selectedAsset)"
              @click="setSpotFencePolygon(selectedAsset)"
            >多边形</button>
            <button
              :class="{ active: selectedAsset.fenceType === '圆形' }"
              :disabled="isOnlineDraft || !isEditingFence(selectedAsset)"
              @click="setSpotFenceCircle(selectedAsset)"
            >半径生成</button>
          </div>
          <label v-if="selectedAsset.fenceType === '圆形'" class="field">
            <span>半径（米）</span>
            <input
              v-model.number="selectedAsset.radius"
              name="spotRadius"
              class="editor-input"
              type="number"
              min="5"
              :disabled="isOnlineDraft || !isEditingFence(selectedAsset)"
              @change="regenerateSpotCircle(selectedAsset)"
            />
          </label>
          <div class="panel-actions action-grid">
            <button class="btn primary" :disabled="isOnlineDraft" @click="toggleSpotEdit(selectedAsset)">
              {{ isEditingFence(selectedAsset) && isPositionEditing(selectedAsset) ? '完成编辑' : '编辑' }}
            </button>
            <button class="btn danger" :disabled="isOnlineDraft" @click="deleteAsset(selectedAsset)">删除景点</button>
          </div>
        </template>

        <template v-else-if="selectedAsset.kind === 'facility'">
          <label class="field">
            <span>服务名称</span>
            <input v-model="selectedAsset.name" name="facilityName" class="editor-input" @input="markDirtyAndRender" />
          </label>
          <label class="field">
            <span>服务类型</span>
            <el-select v-model="selectedAsset.facilityType" size="small" popper-class="workbench-select-popper" @change="markDirtyAndRender">
              <el-option v-for="option in facilityTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </label>
          <label class="field">
            <span>服务描述</span>
            <textarea v-model="selectedAsset.description" name="facilityDescription" class="editor-textarea" rows="3" @input="markDirty" />
          </label>
          <div class="route-field-grid">
            <label class="field">
              <span>纬度</span>
              <input :value="Number(selectedAsset.point?.[0] || 0).toFixed(6)" name="facilityLatitude" class="editor-input" readonly />
            </label>
            <label class="field">
              <span>经度</span>
              <input :value="Number(selectedAsset.point?.[1] || 0).toFixed(6)" name="facilityLongitude" class="editor-input" readonly />
            </label>
            <label class="field switch-field">
              <span>状态</span>
              <el-switch v-model="selectedAsset.status" :active-value="1" :inactive-value="0" size="small" @change="markDirtyAndRender" />
            </label>
          </div>
          <div class="panel-actions action-grid">
            <button class="btn" :disabled="isOnlineDraft" @click="togglePositionEdit(selectedAsset)">{{ isPositionEditing(selectedAsset) ? '完成位置' : '编辑位置' }}</button>
            <button class="btn danger" :disabled="isOnlineDraft" @click="deleteAsset(selectedAsset)">删除服务点</button>
          </div>
        </template>

        <div v-else-if="selectedAsset.kind === 'route'" class="route-meta editable-route">
          <label class="field">
            <span>路线名称</span>
            <input v-model="selectedAsset.name" name="routeName" class="editor-input" @input="markDirtyAndRender" />
          </label>
          <div class="route-field-grid">
            <label class="field">
              <span>起点</span>
              <input :value="selectedAsset.from.name" name="routeFrom" class="editor-input" readonly />
            </label>
            <label class="field">
              <span>终点</span>
              <input :value="selectedAsset.to.name" name="routeTo" class="editor-input" readonly />
            </label>
            <label class="field">
              <span>长度</span>
              <input v-model.number="selectedAsset.lengthMeter" name="routeLengthMeter" class="editor-input" type="number" min="0" @change="markDirty" />
            </label>
            <label class="field">
              <span>耗时</span>
              <input v-model.number="selectedAsset.durationMin" name="routeDurationMin" class="editor-input" type="number" min="1" @change="markDirty" />
            </label>
            <label class="field">
              <span>类型</span>
              <el-select
                v-model="selectedAsset.routeType"
                size="small"
                filterable
                allow-create
                default-first-option
                popper-class="workbench-select-popper"
                @change="markDirtyAndRender"
              >
                <el-option v-for="option in routeTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </label>
            <label class="field">
              <span>拥挤度</span>
              <input v-model.number="selectedAsset.crowdLevel" name="routeCrowdLevel" class="editor-input" type="number" min="1" max="5" @change="markDirty" />
            </label>
            <label class="field switch-field">
              <span>收费</span>
              <el-switch v-model="selectedAsset.paid" size="small" @change="markDirty" />
            </label>
            <label class="field switch-field">
              <span>状态</span>
              <el-switch v-model="selectedAsset.status" :active-value="1" :inactive-value="0" size="small" @change="markDirtyAndRender" />
            </label>
          </div>
          <label class="field">
            <span>AI 描述</span>
            <textarea v-model="selectedAsset.aiNote" name="routeAiNote" class="editor-textarea" rows="4" @input="markDirty" />
          </label>
          <div class="panel-actions">
            <button class="btn" :disabled="isOnlineDraft" @click="editAsset(selectedAsset)">编辑折线</button>
            <button class="btn danger" :disabled="isOnlineDraft" @click="deleteAsset(selectedAsset)">删除路线</button>
          </div>
        </div>

        <div v-else-if="selectedAsset.kind === 'preset'" class="preset-meta">
          <div :class="['preset-save-state', `is-${presetSaveState}`]" role="status">
            <span class="preset-save-state__dot"></span>
            {{ presetSaveStateText }}
          </div>
          <label class="field">
            <span>路线名称</span>
            <input v-model="selectedAsset.name" name="presetName" class="editor-input" @input="markPresetDirty" />
          </label>
          <div class="route-field-grid">
            <label class="field">
              <span>适合人群</span>
              <input v-model="selectedAsset.audience" name="presetAudience" class="editor-input" @input="markPresetDirty" />
            </label>
            <label class="field">
              <span>耗时 (分)</span>
              <input v-model.number="selectedAsset.durationMin" name="presetDurationMin" type="number" min="1" class="editor-input" @input="markPresetDirty" />
            </label>
            <label class="field switch-field">
              <span>启用</span>
              <el-switch v-model="selectedAsset.status" :active-value="1" :inactive-value="0" size="small" @change="markPresetDirtyAndRender" />
            </label>
          </div>
          <label class="field">
            <span>路线描述</span>
            <textarea v-model="selectedAsset.description" name="presetDescription" class="editor-textarea" rows="3" @input="markPresetDirty" />
          </label>
          <label class="field">
            <span>推荐说明</span>
            <textarea v-model="selectedAsset.recommendationNote" name="presetRecommendationNote" class="editor-textarea" rows="3" @input="markPresetDirty" />
          </label>

          <div class="preset-spots-container">
            <div class="section-title">景点序列</div>
            
            <div class="preset-spots-list">
              <div v-for="(spotId, idx) in selectedAsset.spotIds" :key="idx" class="preset-spot-item">
                <span class="spot-order">{{ idx + 1 }}</span>
                <span class="spot-name">{{ getSpotNameById(spotId) }}</span>
                <div class="item-actions">
                  <el-button :icon="Close" circle size="small" type="danger" @click="removeSpotFromPreset(selectedAsset, idx)" />
                </div>
              </div>
            </div>

            <div class="add-spot-row" style="margin-top: 10px; display: flex; gap: 8px;">
              <el-select v-model="newPresetSpotId" placeholder="选择添加景点或服务点" size="small" filterable style="flex: 1;" popper-class="workbench-select-popper">
                <el-option-group label="景点">
                  <el-option v-for="s in spots" :key="s.id" :label="s.name" :value="s.id" />
                </el-option-group>
                <el-option-group label="服务点">
                  <el-option v-for="f in facilities" :key="f.id" :label="f.name" :value="f.id" />
                </el-option-group>
              </el-select>
              <el-button type="primary" size="small" :disabled="!newPresetSpotId" @click="addSpotToPreset(selectedAsset)">添加</el-button>
            </div>
          </div>

          <div v-if="selectedAsset.spotIds.length >= 2" class="preset-segments-container">
            <div class="section-title">路线段安排</div>
            <div v-for="(segmentInfo, idx) in presetSegmentOptions" :key="idx" class="preset-segment-item">
              <div class="segment-title">
                <span>{{ getSpotNameById(segmentInfo.fromId) }} → {{ getSpotNameById(segmentInfo.toId) }}</span>
                <el-tag v-if="segmentInfo.reachable" type="success" size="small" effect="light">{{ segmentInfo.currentName || '自动' }}</el-tag>
                <el-tag v-else type="danger" size="small" effect="light">不可达</el-tag>
              </div>
              <el-select
                v-if="segmentInfo.reachable && segmentInfo.candidates.length > 1"
                :model-value="selectedAsset.segmentIds[idx] || ''"
                placeholder="选择具体路线段"
                size="small"
                clearable
                style="width: 100%;"
                popper-class="workbench-select-popper"
                @change="value => updatePresetSegment(selectedAsset, idx, value || '')"
              >
                <el-option label="自动规划" value="" />
                <el-option v-for="c in segmentInfo.candidates" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
              <p v-else-if="segmentInfo.reachable && segmentInfo.candidates.length === 1" class="segment-hint">
                自动：{{ segmentInfo.candidates[0].name }}
              </p>
              <p v-else-if="segmentInfo.reachable" class="segment-hint">
                已自动规划
              </p>
              <p v-else class="segment-hint segment-hint--warn">
                两景点间无可用路线，请先在地图上绘制路线
              </p>
            </div>
          </div>

          <div class="panel-actions preset-actions" style="margin-top: 15px;">
            <button class="btn danger" @click="deletePresetRoute(selectedAsset)">删除预置路线</button>
            <button class="btn primary" :disabled="presetSaveState === 'saving'" @click="savePresetRoute(selectedAsset)">{{ presetSaveState === 'saving' ? '保存中…' : '保存预置路线' }}</button>
          </div>
        </div>
      </section>

      <section v-else class="panel-section empty-section">
        <strong>地图工作台</strong>
      </section>
    </aside>

    <section class="asset-tray">
      <div class="tray-head">
        <div class="tray-tabs">
          <button
            v-for="tab in trayTabs"
            :key="tab.key"
            :class="{ active: trayTab === tab.key }"
            type="button"
            @click="trayTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>
        <label class="tray-search">
          <el-icon><Search /></el-icon>
          <input v-model.trim="assetKeyword" name="assetKeyword" :placeholder="traySearchPlaceholder" />
        </label>
        <div class="tray-head-right">
          <el-button v-if="trayTab === 'preset'" class="preset-new-btn" type="primary" size="default" @click="createPresetRoute">新建预置路线</el-button>
        </div>
      </div>
      <div class="tray-list" @wheel.prevent="handleTrayWheel">
        <button
          v-for="asset in trayAssets"
          :key="`${asset.kind}-${asset.id}`"
          :class="['tray-card', asset.kind, { active: isSelectedAsset(asset), disabled: Number(asset.status ?? 1) === 0 }]"
          type="button"
          @click="selectAssetFromTray(asset)"
        >
          <span class="tray-card-icon">
            <el-icon v-if="asset.kind === 'spot'"><MapLocation /></el-icon>
            <el-icon v-else-if="asset.kind === 'facility'"><Location /></el-icon>
            <el-icon v-else><Share /></el-icon>
          </span>
          <span class="tray-card-main">
            <strong>{{ asset.name }}</strong>
            <em>{{ getAssetKindLabel(asset) }}</em>
          </span>
          <span class="tray-card-status">{{ Number(asset.status ?? 1) === 1 ? '已启用' : '已停用' }}</span>
        </button>
        <div v-if="!trayAssets.length" class="tray-empty">暂无匹配资产</div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage as ElMessageService, ElMessageBox } from 'element-plus'
import { Aim, Close, Delete, Location, MapLocation, Search, Share } from '@element-plus/icons-vue'
import request from '../../utils/request'
import {
  createHtmlMarkerContent,
  distanceMeters,
  isValidLatLng,
  latLngToLngLat,
  latLngsToLngLats,
  lngLatToLatLng,
  lngLatsToLatLngs,
  loadAmap,
  makeLngLatBounds
} from '../../utils/amap'

const mapEl = ref(null)
const route = useRoute()
const activeMode = ref('select')
const selectedAsset = ref(null)
const tagOptions = ref([])

const loadTagOptions = async () => {
  try {
    const res = await request.get('/admin/scenic/tags/enabled')
    if (res.code === 'OK') tagOptions.value = res.data || []
  } catch (err) {
    // 词表加载失败不阻断编辑：标签列留空，其余功能照常
    console.error('加载标签词表失败', err)
  }
}
const editingRoute = ref(null)
const routeWorkMode = ref('idle')
const cursorLatLng = ref(null)
const dirtyCount = ref(0)
const mapSearchKeyword = ref('')
const mapSearchBusy = ref(false)
const assetKeyword = ref('')
const trayTab = ref('spot')
const fenceEditingSpotId = ref(null)
const positionEditingAsset = ref(null)
const mapLayerMode = ref('standard')
const showMapLabels = ref(true)

const drafts = ref([])
const currentDraftId = ref(null)
const currentDraft = ref(null)
const currentPayloadMeta = ref({ mapCenter: null, defaultZoom: 16 })
const presetRoutes = reactive([])
const persistedPresetRoutes = ref([])
const isOnlineDraft = computed(() => currentDraft.value?.status === 'ONLINE')
const newPresetSpotId = ref(null)
const presetSaveState = ref('saved')
const presetSaveStateText = computed(() => ({
  saved: '预置路线已保存',
  unsaved: '预置路线有未保存修改',
  saving: '正在保存预置路线',
  error: '保存失败，请重试'
}[presetSaveState.value] || '预置路线已保存'))

const spots = reactive([])
const facilities = reactive([])
const routeEndpoints = reactive([])
const routeSegments = reactive([])

let map = null
let draftLayer = null
let editLayer = null
let searchFocusLayer = null
let AMapApi = null
let satelliteLayer = null
let roadNetLayer = null
let activeFenceEditor = null
let editingPolyline = null
let geolocation = null
let routeAssistLayer = null
let routeSnapFocusLayer = null
let routeEditHistory = []
let routeVertexHandles = []
let routeMidpointHandles = []
let draggingRouteVertex = false
let suppressNextMapClick = false
let suppressClickTimer = null
let mapPointerDown = null
let spotDomDragState = null
const BEIJING_CENTER = [39.9042, 116.4074]
const EMPTY_MAP_ZOOM = 12
const ASSET_FOCUS_ZOOM = 17
const SNAP_PIXEL_RADIUS = 12
const SNAP_GEO_MAX_METERS = 80
const MAP_CLICK_DRAG_TOLERANCE = 5
const WORKBENCH_MESSAGE_OFFSET = 82
const WORKBENCH_VIEW_STORAGE_KEY = 'scenic-map-workbench-view'
const layerGroups = {
  spotFences: [],
  spots: [],
  facilities: [],
  routes: []
}

const createWorkbenchMessage = (type) => (options) => {
  if (typeof options === 'string') {
    return ElMessageService[type]({
      message: options,
      offset: WORKBENCH_MESSAGE_OFFSET
    })
  }
  return ElMessageService[type]({
    offset: WORKBENCH_MESSAGE_OFFSET,
    ...options
  })
}

const ElMessage = {
  success: createWorkbenchMessage('success'),
  warning: createWorkbenchMessage('warning'),
  info: createWorkbenchMessage('info'),
  error: createWorkbenchMessage('error')
}

const mapLayerModes = [
  { key: 'standard', label: '标准' },
  { key: 'satellite', label: '卫星' },
  { key: 'satelliteRoad', label: '路网' },
  { key: 'fresh', label: '浅色' },
  { key: 'dark', label: '深色' }
]

const tools = [
  { key: 'select', label: '选择', icon: markRaw(Aim) },
  { key: 'spot', label: '景点', icon: markRaw(MapLocation) },
  { key: 'facility', label: '服务点', icon: markRaw(Location) },
  { key: 'route', label: '路线', icon: markRaw(Share) }
]

const trayTabs = [
  { key: 'spot', label: '景点' },
  { key: 'facility', label: '服务' },
  { key: 'route', label: '路线' },
  { key: 'preset', label: '预置路线' }
]

const facilityTypeOptions = [
  { label: '游客服务', value: 'SERVICE' },
  { label: '卫生间', value: 'TOILET' },
  { label: '餐饮', value: 'DINING' },
  { label: '停车场', value: 'PARKING' },
  { label: '其他', value: 'OTHER' }
]

const routeTypeOptions = [
  { label: '步行', value: 'WALK' },
  { label: '缆车', value: 'CABLEWAY' },
  { label: '观光车', value: 'SIGHTSEEING_BUS' },
  { label: '游船', value: 'BOAT' },
  { label: '台阶', value: 'STAIRS' },
  { label: '其他', value: 'OTHER' }
]

const spotDraft = reactive({
  name: '新景点',
  fenceType: 'polygon',
  point: null,
  radius: 50
})

const facilityDraft = reactive({
  name: '新服务点',
  facilityType: 'SERVICE',
  point: null
})

const routeDraft = reactive({
  step: 1,
  from: null,
  to: null,
  middlePoints: [],
  lengthMeter: null,
  durationMin: null,
  routeType: 'WALK',
  paid: false,
  aiNote: ''
})

const activeModeLabel = computed(() => {
  const found = tools.find(tool => tool.key === activeMode.value)
  return found?.label || '选择'
})

const cursorText = computed(() => {
  if (!cursorLatLng.value) return '移动光标查看坐标'
  const point = Array.isArray(cursorLatLng.value)
    ? cursorLatLng.value
    : [cursorLatLng.value.lat, cursorLatLng.value.lng]
  const lat = Number(point[0])
  const lng = Number(point[1])
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return '移动光标查看坐标'
  return `${lat.toFixed(6)}, ${lng.toFixed(6)}`
})

const draftStateText = computed(() => dirtyCount.value > 0 ? `未保存 ${dirtyCount.value} 项` : (currentDraft.value?.status === 'ONLINE' ? '只读' : '草稿已保存'))

const panelTitle = computed(() => {
  if (activeMode.value === 'spot') return '新建景点'
  if (activeMode.value === 'facility') return '新建服务点'
  if (activeMode.value === 'route') return editingRoute.value ? '编辑路线' : '绘制路线'
  return selectedAsset.value ? selectedAsset.value.name : '地图工作台'
})

const routeHint = computed(() => {
  if (routeWorkMode.value === 'editing') return '拖动中间点调整路线，双击中间点删除，拖动浅色插入点新增弯折'
  if (routeDraft.step === 1) return '点击景点、服务点、已有路线端点或地图空白处作为起点'
  if (routeDraft.step === 2) return '继续点击添加中间路径点，点击景点/服务点作为终点，或直接保存路线'
  return '检查路线属性后保存'
})

const canCommitRoute = computed(() => {
  if (routeDraft.from && routeDraft.to && !sameEndpoint(routeDraft.from, routeDraft.to)) return true
  if (routeDraft.step === 2 && routeDraft.from) {
    const points = getDraftPolyline()
    return points.length >= 2
  }
  return false
})

const traySource = computed(() => {
  if (trayTab.value === 'facility') return facilities
  if (trayTab.value === 'route') return routeSegments
  if (trayTab.value === 'preset') return presetRoutes
  return spots
})

const trayAssets = computed(() => {
  const keyword = assetKeyword.value.trim().toLowerCase()
  if (!keyword) return traySource.value
  return traySource.value.filter(asset => {
    const haystack = [
      asset.name,
      asset.typeLabel,
      asset.facilityType,
      asset.routeType,
      asset.aiNote,
      getAssetCoordinateText(asset)
    ].filter(Boolean).join(' ').toLowerCase()
    return haystack.includes(keyword)
  })
})

const traySearchPlaceholder = computed(() => {
  if (trayTab.value === 'facility') return '搜索服务点名称'
  if (trayTab.value === 'route') return '搜索路线名称'
  if (trayTab.value === 'preset') return '搜索预置路线名称'
  return '搜索景点名称'
})

const readWorkbenchViewCache = () => {
  try {
    const raw = localStorage.getItem(WORKBENCH_VIEW_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch (error) {
    return null
  }
}

const saveWorkbenchViewCache = () => {
  if (!map) return
  const center = lngLatToLatLng(map.getCenter())
  const payload = {
    layerMode: mapLayerMode.value,
    showLabels: showMapLabels.value,
    center: isValidLatLng(center) ? center : currentPayloadMeta.value.mapCenter,
    zoom: map.getZoom()
  }
  localStorage.setItem(WORKBENCH_VIEW_STORAGE_KEY, JSON.stringify(payload))
}

const applyReadonlyDraftState = () => {
  if (!isOnlineDraft.value) return
  closePanel()
}

const setMode = (mode) => {
  if (isOnlineDraft.value && ['spot', 'facility', 'route'].includes(mode)) {
    ElMessage.warning('ONLINE 状态下禁止编辑或添加物理拓扑')
    return
  }
  stopFenceEdit()
  stopPositionEdit()
  stopRouteEditor()
  activeMode.value = mode
  selectedAsset.value = null
  editingRoute.value = null
  resetDrafts()
  if (mode === 'route') routeWorkMode.value = 'creating'
  renderAll()
}

const resetDrafts = (options = {}) => {
  stopRouteEditor()
  spotDraft.point = null
  facilityDraft.point = null
  routeDraft.step = 1
  routeDraft.from = null
  routeDraft.to = null
  routeDraft.middlePoints = []
  routeDraft.lengthMeter = null
  routeDraft.durationMin = null
  routeDraft.routeType = 'WALK'
  routeDraft.paid = false
  routeDraft.aiNote = ''
  if (options.resetRouteMode !== false) routeWorkMode.value = 'idle'
  clearDraftLayer()
  clearEditLayer()
  clearRouteSnapFocusLayer()
}

const markDirty = () => {
  dirtyCount.value = Math.max(dirtyCount.value, 1)
}

const markDirtyAndRender = () => {
  markDirty()
  renderAll()
}

const markPresetDirty = () => {
  if (presetSaveState.value !== 'saving') presetSaveState.value = 'unsaved'
}

const markPresetDirtyAndRender = () => {
  markPresetDirty()
  renderAll()
}

const closePanel = () => {
  stopFenceEdit()
  stopPositionEdit()
  stopRouteEditor()
  selectedAsset.value = null
  editingRoute.value = null
  routeWorkMode.value = 'idle'
  activeMode.value = 'select'
  resetDrafts()
  renderAll()
}

const loadWorkbenchData = async () => {
  try {
    if (!currentDraftId.value || drafts.value.length === 0) {
      const resDrafts = await request.get('/admin/map-drafts')
      if (resDrafts.code !== 'OK') {
        ElMessage.warning(resDrafts.message || '加载草稿列表失败')
        return
      }
      drafts.value = resDrafts.data || []
      if (drafts.value.length === 0) {
        replaceList(spots, [])
        replaceList(facilities, [])
        replaceList(routeEndpoints, [])
        replaceList(routeSegments, [])
        replaceList(presetRoutes, [])
        selectedAsset.value = null
        currentDraft.value = null
        dirtyCount.value = 0
        renderAll()
        return
      }
      const requestedDraftId = String(route.query.draftId || '')
      let selected = drafts.value.find(d => String(d.id) === requestedDraftId)
      if (!selected) selected = drafts.value.find(d => d.status === 'ONLINE')
      if (!selected) {
        const draftList = drafts.value.filter(d => d.status === 'DRAFT')
        if (draftList.length > 0) {
          selected = draftList.reduce((max, d) => d.id > max.id ? d : max, draftList[0])
        } else {
          selected = drafts.value[0]
        }
      }
      currentDraftId.value = selected.id
    }

    const res = await request.get(`/admin/map-drafts/${currentDraftId.value}/workbench`)
    if (res.code !== 'OK') {
      ElMessage.warning(res.message || '地图资产加载失败')
      return
    }
    const data = res.data || {}
    currentDraft.value = data.currentDraft || findDraftById(currentDraftId.value)
    drafts.value = data.drafts || drafts.value

    const payload = data.payload || {}
    currentPayloadMeta.value = {
      mapCenter: normalizeMapPoint(payload.mapCenter) || BEIJING_CENTER,
      defaultZoom: payload.defaultZoom || 16
    }
    replaceList(spots, normalizeSpots(payload.spots || []))
    replaceList(facilities, normalizeFacilities(payload.facilities || []))
    replaceList(routeEndpoints, normalizeRouteEndpoints(payload.routeEndpoints || []))
    replaceList(routeSegments, normalizeRouteSegments(payload.routeSegments || []))
    replaceList(presetRoutes, normalizePresetRoutes(payload.presetRoutes || []))
    presetRoutes.forEach(syncPresetSegmentIds)
    persistedPresetRoutes.value = presetRoutes.map(serializePresetRoute)
    presetSaveState.value = 'saved'

    if (!isOnlineDraft.value) {
      cleanupOrphanEndpoints()
    }
    if (isOnlineDraft.value) {
      applyReadonlyDraftState()
    } else {
      selectedAsset.value = null
      stopPositionEdit()
      stopRouteEditor()
    }
    dirtyCount.value = 0

    if (map) {
      const cachedView = readWorkbenchViewCache()
      const center = normalizeMapPoint(cachedView?.center) || normalizeMapPoint(payload.mapCenter)
      const zoom = Number(cachedView?.zoom || payload.defaultZoom || currentPayloadMeta.value.defaultZoom)
      if (center) map.setCenter(latLngToLngLat(center))
      if (Number.isFinite(zoom)) map.setZoom(zoom)
    }
    renderAll()
  } catch (error) {
    console.error('load workbench data failed', error)
    ElMessage.error('地图草稿接口不可用，请确认后端已重启并完成登录')
  }
}

const normalizeMapPoint = (point) => {
  let normalized = null
  if (Array.isArray(point) && point.length >= 2) {
    normalized = [Number(point[0]), Number(point[1])]
  } else if (point && typeof point === 'object') {
    normalized = [Number(point.latitude ?? point.lat), Number(point.longitude ?? point.lng)]
  }
  return isValidLatLng(normalized) ? normalized : null
}

const findDraftById = (draftId) => {
  return drafts.value.find(draft => String(draft.id) === String(draftId))
}

const formatDraftDate = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = number => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const normalizeMapPoints = (points) => (Array.isArray(points) ? points : [])
  .map(normalizeMapPoint)
  .filter(isValidLatLng)

const createMapObjectId = (prefix) => `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`

const validMapPoints = (points = []) => (Array.isArray(points) ? points : [])
  .filter(isValidLatLng)

const formatLatLng = (point) => {
  if (!isValidLatLng(point)) return ''
  return `${Number(point[0]).toFixed(6)}, ${Number(point[1]).toFixed(6)}`
}

const parseJsonArray = (value) => {
  if (Array.isArray(value)) return value
  if (typeof value !== 'string' || !value.trim()) return null
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : null
  } catch (error) {
    return null
  }
}

const normalizeSpots = (items) => items.map(item => {
  const polygon = normalizeMapPoints(item.polygon || parseJsonArray(item.fencePolygon))
  const point = normalizeMapPoint(item.labelPoint || item.point) ||
    normalizeMapPoint([item.labelLatitude || item.latitude || item.lat, item.labelLongitude || item.longitude || item.lng]) ||
    getPolygonCenter(polygon) ||
    BEIJING_CENTER
  return {
    ...item,
    id: String(item.id),
    kind: 'spot',
    typeLabel: item.typeLabel || '景点',
    name: item.name || '未命名景点',
    labelPoint: point,
    polygon: polygon.length ? polygon : makeBoxPolygon(point[0], point[1], 0.00022, 0.00028),
    fenceType: item.fenceType === '圆形' || item.fenceType === 'circle' ? '圆形' : '多边形',
    radius: Number(item.radius || 50),
    description: item.description || '',
    tags: Array.isArray(item.tags) ? item.tags : [],
    status: item.status ?? 1,
    longitude: Number(point[1]).toFixed(6),
    latitude: Number(point[0]).toFixed(6)
  }
})

const normalizeFacilities = (items) => items.map(item => {
  const point = normalizeMapPoint(item.point) ||
    normalizeMapPoint([item.latitude || item.lat, item.longitude || item.lng]) ||
    BEIJING_CENTER
  return {
    ...item,
    id: String(item.id),
    kind: 'facility',
    typeLabel: '服务点',
    facilityType: item.facilityType || item.type || 'SERVICE',
    name: item.name || '未命名服务点',
    point,
    description: item.description || '',
    status: item.status ?? 1,
    longitude: Number(point[1]).toFixed(6),
    latitude: Number(point[0]).toFixed(6)
  }
})

const normalizeRouteEndpoints = (items) => items.map(item => ({
  ...item,
  id: String(item.id),
  kind: 'endpoint',
  point: normalizeMapPoint(item.point) || normalizeMapPoint([item.latitude, item.longitude]),
  name: item.name || '地图点',
  endpointType: item.endpointType || 'MAP_POINT'
}))

const normalizeRouteSegments = (items) => items.map(item => {
  const from = normalizeEndpointRef(item.from || makeEndpointRefFromLegacy(item, 'from'))
  const to = normalizeEndpointRef(item.to || makeEndpointRefFromLegacy(item, 'to'))
  const middlePoints = normalizeMapPoints(item.middlePoints || getMiddlePointsFromLegacyPath(item.path || item.polyline || [], from, to))
  return {
    ...item,
    id: String(item.id),
    kind: 'route',
    typeLabel: '路线',
    name: item.name || `${from.name || '起点'} - ${to.name || '终点'}`,
    from,
    to,
    middlePoints,
    lengthMeter: Number(item.lengthMeter || item.distance || calcDistance([resolveEndpointPoint(from), ...middlePoints, resolveEndpointPoint(to)])),
    durationMin: Number(item.durationMin || item.duration || 1),
    routeType: item.routeType || 'WALK',
    paid: Boolean(item.paid || item.isPaid),
    aiNote: item.aiNote || item.description || '',
    crowdLevel: Number(item.crowdLevel || item.crowd || 1),
    status: item.status ?? 1
  }
})

const normalizePresetRoutes = (items) => items.map(item => ({
  ...item,
  id: String(item.id),
  kind: 'preset',
  typeLabel: '预置路线',
  name: item.name || '未命名预置路线',
  spotIds: (item.spotIds || []).map(String),
  segmentIds: (item.segmentIds || []).map(String),
  description: item.description || '',
  recommendationNote: item.recommendationNote || '',
  audience: item.audience || '',
  durationMin: Number(item.durationMin || 30),
  status: item.status ?? 1
}))

const normalizeEndpointRef = (ref = {}) => ({
  type: (ref.type || ref.targetType || 'ENDPOINT').toUpperCase(),
  id: normalizeEndpointRefId(ref),
  name: ref.name || ref.targetName || '',
  point: normalizeMapPoint(ref.point) || normalizeMapPoint([ref.latitude, ref.longitude])
})

const normalizeEndpointRefId = (ref = {}) => {
  const id = ref.id ?? ref.targetId
  return id == null ? null : String(id)
}

const makeEndpointRefFromLegacy = (edge, key) => ({
  type: 'ENDPOINT',
  id: edge[key],
  name: edge[`${key}Name`] || '地图点',
  point: key === 'from' ? edge.path?.[0] : edge.path?.[edge.path.length - 1]
})

const getMiddlePointsFromLegacyPath = (path, from, to) => {
  if (!Array.isArray(path) || path.length <= 2) return []
  return path.slice(1, -1)
}

const replaceList = (target, source) => {
  target.splice(0, target.length, ...source)
}

const initMap = async () => {
  if (map || !mapEl.value) return
  try {
    AMapApi = await loadAmap()
  } catch (error) {
    ElMessage.error(error.message || '高德地图加载失败')
    return
  }
  const cachedView = readWorkbenchViewCache()
  if (cachedView?.layerMode) {
    mapLayerMode.value = cachedView.layerMode
  }
  if (typeof cachedView?.showLabels === 'boolean') {
    showMapLabels.value = cachedView.showLabels
  }
  const view = getDefaultMapView()
  const initialCenter = normalizeMapPoint(cachedView?.center) || view.center
  const initialZoom = Number(cachedView?.zoom || view.zoom)
  map = new AMapApi.Map(mapEl.value, {
    center: latLngToLngLat(initialCenter),
    zoom: Number.isFinite(initialZoom) ? initialZoom : view.zoom,
    doubleClickZoom: false,
    showLabel: showMapLabels.value,
    mapStyle: 'amap://styles/normal',
    viewMode: '2D'
  })
  map.addControl(new AMapApi.Scale({ position: 'RB' }))
  map.addControl(new AMapApi.ToolBar({ position: 'RB', liteStyle: true }))
  satelliteLayer = new AMapApi.TileLayer.Satellite()
  roadNetLayer = new AMapApi.TileLayer.RoadNet()
  map.on('mousemove', event => {
    const point = lngLatToLatLng(event.lnglat)
    cursorLatLng.value = point
  })
  map.on('mousedown', event => { mapPointerDown = toContainerPixel(lngLatToLatLng(event.lnglat)) })
  map.on('mouseup', event => {
    const point = toContainerPixel(lngLatToLatLng(event.lnglat))
    if (mapPointerDown && point && Math.hypot(point[0] - mapPointerDown[0], point[1] - mapPointerDown[1]) > MAP_CLICK_DRAG_TOLERANCE) {
      suppressNextMapClick = true
    }
    mapPointerDown = null
  })
  map.on('click', event => handleMapClick(lngLatToLatLng(event.lnglat)))
  map.on('dragstart', markMapDrag)
  map.on('dragend', markMapDrag)
  map.on('zoomend', () => {
    updateZoomClass()
    saveWorkbenchViewCache()
    renderAll()
  })
  map.on('moveend', saveWorkbenchViewCache)
  setMapLayerMode(mapLayerMode.value)
  updateZoomClass()
  renderAll()
}

const updateZoomClass = () => {
  if (!map) return
  const container = map.getContainer?.()
  if (!container) return
  container.classList.remove('zoom-minimal', 'zoom-compact', 'zoom-detailed')
  const zoom = map.getZoom()
  if (zoom >= 17) container.classList.add('zoom-detailed')
  else if (zoom >= 15) container.classList.add('zoom-compact')
  else container.classList.add('zoom-minimal')
}

const getZoomLevel = () => {
  if (!map) return 'compact'
  const zoom = map.getZoom()
  if (zoom >= 17) return 'detailed'
  if (zoom >= 15) return 'compact'
  return 'minimal'
}

const renderAll = () => {
  if (!map) return
  clearAmapOverlays()
  routeSegments.forEach(renderRoute)
  spots.forEach(renderSpot)
  facilities.forEach(renderFacility)
  renderDraft()
  if (activeMode.value === 'route' && routeWorkMode.value === 'creating') renderRouteAssistPoints()
  if (editingRoute.value) renderRouteEditor(editingRoute.value)
  if (selectedAsset.value && selectedAsset.value.kind === 'preset') {
    renderSelectedPresetRoute(selectedAsset.value)
  }
}

const renderSpot = (spot) => {
  if (!isValidLatLng(spot.labelPoint)) return
  const polygonPath = validMapPoints(spot.polygon)
  const editingFence = isEditingFence(spot)
  const editingPosition = isPositionEditing(spot)
  if (polygonPath.length >= 3) {
    const polygon = new AMapApi.Polygon({
      path: latLngsToLngLats(polygonPath),
      strokeColor: editingFence ? '#38bdf8' : '#f59e0b',
      strokeWeight: editingFence ? 4 : 3,
      strokeOpacity: editingFence ? 0.98 : 0.92,
      fillColor: editingFence ? '#0ea5e9' : '#f97316',
      fillOpacity: editingFence ? 0.18 : 0.16,
      zIndex: editingFence ? 28 : 18,
      bubble: false
    })
    map.add(polygon)
    layerGroups.spotFences.push(polygon)
    polygon.on('click', event => {
      if (activeMode.value === 'route' && routeWorkMode.value === 'creating') {
        handleRouteClick(lngLatToLatLng(event.lnglat))
      }
      else selectAsset(spot)
    })
    if (editingFence && spot.fenceType !== '圆形') {
      activeFenceEditor = new AMapApi.PolygonEditor(map, polygon)
      activeFenceEditor.open()
      ;['adjust', 'addnode', 'removenode', 'end'].forEach(type => {
        activeFenceEditor.on(type, () => syncSpotPolygonFromLayer(spot, polygon))
      })
    }
  }
  const marker = new AMapApi.Marker({
    position: latLngToLngLat(spot.labelPoint),
    content: makeTextIcon(spot.name, 'spot', spot.labelPoint, null, { editing: editingPosition }),
    offset: new AMapApi.Pixel(-20, -38),
    draggable: editingPosition,
    cursor: editingPosition ? 'move' : 'pointer',
    zIndex: editingPosition ? 10000 : 76,
    bubble: false,
    raiseOnDrag: true,
    topWhenClick: true,
    extData: { kind: 'spot', id: spot.id }
  })
  map.add(marker)
  layerGroups.spots.push(marker)
  if (editingPosition) attachSpotDomDrag(marker, spot)
  marker.on('click', event => {
    event?.originalEvent?.stopPropagation?.()
    if (activeMode.value === 'route' && routeWorkMode.value === 'creating') {
      handleRouteClick(spot.labelPoint, { forcedEndpoint: makeTargetEndpoint('SPOT', spot) })
    }
    else selectAsset(spot)
  })
  marker.on('mousedown', () => {
    prepareSpotPositionDrag(spot)
  })
  marker.on('dragstart', () => {
    beginSpotPositionDrag(spot)
  })
  marker.on('dragging', event => {
    moveSpot(spot, lngLatToLatLng(event.lnglat || event.target.getPosition()))
    syncRoutesForMovedAsset('SPOT', spot.id, spot.name, spot.labelPoint)
  })
  marker.on('dragend', () => {
    finishSpotPositionDrag(true)
  })
}

const attachSpotDomDrag = (marker, spot) => {
  const content = marker.getContent?.()
  if (!content || !content.addEventListener) return
  content.addEventListener('pointerdown', event => {
    if (!beginSpotPositionDrag(spot)) return
    event.preventDefault()
    event.stopPropagation()
    spotDomDragState = {
      spot,
      marker,
      pointerId: event.pointerId,
      moved: false
    }
    content.setPointerCapture?.(event.pointerId)
  })
  content.addEventListener('pointermove', handleSpotDomDragMove)
  content.addEventListener('pointerup', handleSpotDomDragEnd)
  content.addEventListener('pointercancel', handleSpotDomDragEnd)
}

const handleSpotDomDragMove = (event) => {
  if (!spotDomDragState || spotDomDragState.pointerId !== event.pointerId) return
  event.preventDefault()
  event.stopPropagation()
  const point = containerEventToLatLng(event)
  if (!isValidLatLng(point)) return
  const spot = spotDomDragState.spot
  spotDomDragState.moved = true
  moveSpot(spot, point)
  syncRoutesForMovedAsset('SPOT', spot.id, spot.name, spot.labelPoint)
  spotDomDragState.marker?.setPosition(latLngToLngLat(spot.labelPoint))
}

const handleSpotDomDragEnd = (event) => {
  if (!spotDomDragState || spotDomDragState.pointerId !== event.pointerId) return
  event.preventDefault()
  event.stopPropagation()
  const moved = spotDomDragState.moved
  spotDomDragState = null
  finishSpotPositionDrag(moved)
}

const closeActiveFenceEditor = () => {
  if (!activeFenceEditor) return
  activeFenceEditor.close()
  activeFenceEditor = null
}

const prepareSpotPositionDrag = (spot) => {
  if (!isPositionEditing(spot)) return false
  closeActiveFenceEditor()
  return true
}

const beginSpotPositionDrag = (spot) => {
  if (!prepareSpotPositionDrag(spot)) return false
  suppressNextMapClick = true
  map?.setStatus?.({ dragEnable: false })
  return true
}

const finishSpotPositionDrag = (moved) => {
  map?.setStatus?.({ dragEnable: true })
  if (moved) markDirty()
  if (moved || fenceEditingSpotId.value) renderAll()
}

const containerEventToLatLng = (event) => {
  if (!map || !AMapApi) return null
  const rect = map.getContainer().getBoundingClientRect()
  const pixel = new AMapApi.Pixel(event.clientX - rect.left, event.clientY - rect.top)
  return lngLatToLatLng(map.containerToLngLat(pixel))
}

const renderFacility = (facility) => {
  if (!isValidLatLng(facility.point)) return
  const editingPosition = isPositionEditing(facility)
  const marker = new AMapApi.Marker({
    position: latLngToLngLat(facility.point),
    content: makeTextIcon(facility.name, 'facility', facility.point, facility.facilityType, { editing: editingPosition }),
    offset: new AMapApi.Pixel(-20, -38),
    draggable: editingPosition,
    cursor: editingPosition ? 'move' : 'pointer',
    zIndex: editingPosition ? 126 : 74,
    topWhenClick: true,
    extData: { kind: 'facility', id: facility.id }
  })
  map.add(marker)
  layerGroups.facilities.push(marker)
  marker.on('click', event => {
    event?.originalEvent?.stopPropagation?.()
    if (activeMode.value === 'route' && routeWorkMode.value === 'creating') {
      handleRouteClick(facility.point, { forcedEndpoint: makeTargetEndpoint('FACILITY', facility) })
    }
    else selectAsset(facility)
  })
  marker.on('dragging', event => {
    moveFacility(facility, lngLatToLatLng(event.lnglat || event.target.getPosition()))
    syncRoutesForMovedAsset('FACILITY', facility.id, facility.name, facility.point)
  })
  marker.on('dragend', () => {
    markDirty()
    renderAll()
  })
}

const renderRoute = (route) => {
  if (editingRoute.value && String(editingRoute.value.id) === String(route.id)) return
  const points = getRoutePolyline(route)
  if (points.length < 2) return
  const style = getRouteStyle(route)
  const overlays = []
  if (style.outlineWeight) {
    const outline = new AMapApi.Polyline({
      path: latLngsToLngLats(points),
      strokeColor: style.outlineColor,
      strokeWeight: style.outlineWeight,
      strokeOpacity: style.outlineOpacity,
      lineJoin: 'round',
      lineCap: 'round',
      cursor: 'pointer',
      zIndex: style.zIndex - 1
    })
    attachRouteLineEvents(outline, route)
    overlays.push(outline)
  }
  const line = new AMapApi.Polyline({
    path: latLngsToLngLats(points),
    strokeColor: style.color,
    strokeWeight: style.weight,
    strokeOpacity: style.opacity,
    lineJoin: 'round',
    lineCap: 'round',
    cursor: 'pointer',
    zIndex: style.zIndex
  })
  attachRouteLineEvents(line, route)
  overlays.push(line)
  map.add(overlays)
  layerGroups.routes.push(...overlays)
}

const attachRouteLineEvents = (line, route) => {
  line.on('click', event => {
    if (activeMode.value === 'route') return
    selectAsset(route)
  })
}

const getRouteStyle = (route) => {
  const selected = selectedAsset.value?.kind === 'route' && String(selectedAsset.value.id) === String(route.id)
  const routeEditing = activeMode.value === 'route'
  const editingCurrent = editingRoute.value && String(editingRoute.value.id) === String(route.id)
  const zoom = getZoomLevel()
  const baseWeight = zoom === 'detailed' ? 6 : zoom === 'compact' ? 5 : 4
  const typeColor = {
    WALK: '#1d4ed8',
    CABLEWAY: '#6d28d9',
    SIGHTSEEING_BUS: '#c2410c',
    BOAT: '#0e7490',
    STAIRS: '#334155',
    OTHER: '#047857'
  }
  const highlighted = selected || editingCurrent
  const presetFocused = selectedAsset.value?.kind === 'preset'
  const subdued = presetFocused && !highlighted
  return {
    color: highlighted ? '#065f46' : (typeColor[route.routeType] || '#1d4ed8'),
    weight: highlighted ? baseWeight + 2 : (subdued ? Math.max(3, baseWeight - 1) : baseWeight),
    opacity: routeEditing && !editingCurrent ? 0.3 : (subdued ? 0.2 : (highlighted ? 0.98 : 0.82)),
    outlineColor: highlighted ? '#ecfeff' : '#ffffff',
    outlineWeight: highlighted ? baseWeight + 7 : (subdued ? 0 : baseWeight + 3),
    outlineOpacity: routeEditing && !editingCurrent ? 0.18 : (subdued ? 0 : (highlighted ? 0.95 : 0.58)),
    zIndex: editingCurrent ? 92 : selected ? 86 : presetFocused ? 42 : routeEditing ? 52 : 60
  }
}

const renderDraft = () => {
  clearDraftLayer()
  if (!map) return
  const group = []
  if (activeMode.value === 'route' && routeWorkMode.value === 'creating' && routeDraft.from) {
    const points = getDraftPolyline()
    if (points.length >= 2) {
      group.push(new AMapApi.Polyline({
        path: latLngsToLngLats(points),
        strokeColor: '#b45309',
        strokeWeight: 5,
        strokeOpacity: 0.92,
        strokeStyle: 'dashed',
        strokeDasharray: [10, 8],
        lineJoin: 'round',
        lineCap: 'round',
        zIndex: 96
      }))
    }
    points.forEach((point, index) => {
      group.push(new AMapApi.CircleMarker({
        center: latLngToLngLat(point),
        radius: index === 0 || index === points.length - 1 ? 6 : 4,
        strokeColor: '#78350f',
        strokeWeight: 2,
        fillColor: '#f59e0b',
        fillOpacity: 0.9
      }))
    })
  }
  if (group.length) map.add(group)
  draftLayer = group
}

const canSetEndpoint = (key) => {
  if (!selectedAsset.value || !['spot', 'facility'].includes(selectedAsset.value.kind)) return false
  const other = key === 'from' ? routeDraft.to : routeDraft.from
  const target = makeTargetEndpoint(selectedAsset.value.kind === 'spot' ? 'SPOT' : 'FACILITY', selectedAsset.value)
  return !sameEndpoint(other, target)
}

const setEndpointToSelected = (key) => {
  if (!selectedAsset.value) return
  const kind = selectedAsset.value.kind
  const type = kind === 'spot' ? 'SPOT' : 'FACILITY'
  const endpoint = makeTargetEndpoint(type, selectedAsset.value)
  
  routeDraft[key] = endpoint
  
  if (routeWorkMode.value === 'editing' && editingPolyline) {
    const points = getEditingPolylinePoints()
    if (points.length >= 2) {
      if (key === 'from') points[0] = endpoint.point
      else points[points.length - 1] = endpoint.point
      setEditingPolylinePoints(points)
      pushRouteEditHistory()
    }
  } else if (routeWorkMode.value === 'creating') {
    if (routeDraft.from && routeDraft.to) {
      routeDraft.step = 3
    } else if (routeDraft.from) {
      routeDraft.step = 2
    }
    renderDraft()
  }
}

const renderRouteEditor = (route) => {
  if (!map || editingPolyline) return
  const points = getRoutePolyline(route)
  if (points.length < 2) return
  editingPolyline = new AMapApi.Polyline({
    path: latLngsToLngLats(points),
    strokeColor: '#065f46',
    strokeWeight: 7,
    strokeOpacity: 0.98,
    isOutline: true,
    outlineColor: '#ecfeff',
    borderWeight: 4,
    lineJoin: 'round',
    lineCap: 'round',
    zIndex: 108,
    bubble: false
  })
  map.add(editingPolyline)
  editLayer = [editingPolyline]
  editingPolyline.on('dblclick', event => {
    event?.originalEvent?.stopPropagation?.()
    insertRoutePointAtNearestSegment(lngLatToLatLng(event.lnglat))
  })
  routeEditHistory = [getEditingPolylinePoints()]
  syncRouteDraftFromEditingPolyline()
  syncRouteEditOverlays()
}

const syncRouteEditOverlays = () => {
  clearRouteEditHandles()
  if (!map || !editingPolyline || routeWorkMode.value !== 'editing') return
  const points = getEditingPolylinePoints()
  if (points.length < 2) return
  routeVertexHandles = points.map((point, index) => makeRouteVertexHandle(point, index, points.length))
  routeMidpointHandles = points.slice(0, -1).map((point, index) => makeRouteMidpointHandle(getMidpoint(point, points[index + 1]), index + 1))
  const handles = [...routeVertexHandles, ...routeMidpointHandles].filter(Boolean)
  if (handles.length) map.add(handles)
}

const makeRouteVertexHandle = (point, index, total) => {
  const isEndpoint = index === 0 || index === total - 1
  const marker = new AMapApi.Marker({
    position: latLngToLngLat(point),
    content: makeRouteVertexIcon(isEndpoint ? 'endpoint' : 'middle'),
    offset: new AMapApi.Pixel(isEndpoint ? -8 : -7, isEndpoint ? -8 : -7),
    draggable: !isEndpoint,
    cursor: isEndpoint ? 'default' : 'move',
    zIndex: isEndpoint ? 126 : 128,
    bubble: false,
    extData: { routePointIndex: index, routePointKind: isEndpoint ? 'endpoint' : 'middle' }
  })
  marker.on('click', event => {
    event?.originalEvent?.stopPropagation?.()
  })
  marker.on('dblclick', event => {
    event?.originalEvent?.stopPropagation?.()
    if (isEndpoint) return
    deleteRouteMiddlePoint(index)
  })
  if (!isEndpoint) {
    marker.on('dragstart', () => {
      draggingRouteVertex = true
      suppressNextMapClick = true
      map?.setStatus?.({ dragEnable: false })
    })
    marker.on('dragging', event => {
      updateEditingPoint(index, lngLatToLatLng(event.lnglat || event.target.getPosition()))
    })
    marker.on('dragend', event => {
      updateEditingPoint(index, lngLatToLatLng(event.lnglat || event.target.getPosition()))
      draggingRouteVertex = false
      map?.setStatus?.({ dragEnable: true })
      pushRouteEditHistory()
      syncRouteEditOverlays()
    })
  }
  return marker
}

const makeRouteMidpointHandle = (point, insertIndex) => {
  if (!isValidLatLng(point)) return null
  const marker = new AMapApi.Marker({
    position: latLngToLngLat(point),
    content: makeRouteVertexIcon('midpoint'),
    offset: new AMapApi.Pixel(-6, -6),
    draggable: true,
    cursor: 'copy',
    zIndex: 124,
    bubble: false,
    extData: { insertIndex, routePointKind: 'midpoint' }
  })
  marker.on('click', event => {
    event?.originalEvent?.stopPropagation?.()
  })
  marker.on('dblclick', event => {
    event?.originalEvent?.stopPropagation?.()
    insertRouteMiddlePoint(insertIndex, point)
  })
  marker.on('dragstart', () => {
    draggingRouteVertex = true
    suppressNextMapClick = true
    map?.setStatus?.({ dragEnable: false })
  })
  marker.on('dragend', event => {
    const nextPoint = lngLatToLatLng(event.lnglat || event.target.getPosition())
    draggingRouteVertex = false
    map?.setStatus?.({ dragEnable: true })
    insertRouteMiddlePoint(insertIndex, nextPoint)
  })
  return marker
}

const setEditingPolylinePoints = (points, options = {}) => {
  const valid = validMapPoints(points)
  if (!editingPolyline || valid.length < 2) return
  editingPolyline.setPath(latLngsToLngLats(valid))
  syncRouteDraftFromEditingPolyline()
  if (options.refreshHandles !== false) syncRouteEditOverlays()
}

const updateEditingPoint = (index, point) => {
  if (!isValidLatLng(point)) return
  const points = getEditingPolylinePoints()
  if (index <= 0 || index >= points.length - 1) return
  points[index] = [...point]
  setEditingPolylinePoints(points, { refreshHandles: false })
}

const deleteRouteMiddlePoint = (index) => {
  const points = getEditingPolylinePoints()
  if (index <= 0 || index >= points.length - 1) return
  points.splice(index, 1)
  setEditingPolylinePoints(points)
  pushRouteEditHistory()
}

const insertRouteMiddlePoint = (insertIndex, point) => {
  if (!isValidLatLng(point)) return
  const points = getEditingPolylinePoints()
  if (insertIndex <= 0 || insertIndex > points.length) return
  points.splice(insertIndex, 0, [...point])
  setEditingPolylinePoints(points)
  pushRouteEditHistory()
}

const insertRoutePointAtNearestSegment = (point) => {
  if (!isValidLatLng(point)) return
  const points = getEditingPolylinePoints()
  if (points.length < 2) return
  let insertIndex = 1
  let bestDistance = Infinity
  const targetPixel = toContainerPixel(point)
  if (!targetPixel) return
  points.slice(0, -1).forEach((start, index) => {
    const startPixel = toContainerPixel(start)
    const endPixel = toContainerPixel(points[index + 1])
    if (!startPixel || !endPixel) return
    const distance = distanceToPixelSegment(targetPixel, startPixel, endPixel)
    if (distance < bestDistance) {
      bestDistance = distance
      insertIndex = index + 1
    }
  })
  insertRouteMiddlePoint(insertIndex, point)
}

const distanceToPixelSegment = (point, start, end) => {
  const dx = end[0] - start[0]
  const dy = end[1] - start[1]
  if (dx === 0 && dy === 0) return Math.hypot(point[0] - start[0], point[1] - start[1])
  const t = Math.max(0, Math.min(1, ((point[0] - start[0]) * dx + (point[1] - start[1]) * dy) / (dx * dx + dy * dy)))
  const projected = [start[0] + t * dx, start[1] + t * dy]
  return Math.hypot(point[0] - projected[0], point[1] - projected[1])
}

const getMidpoint = (a, b) => {
  if (!isValidLatLng(a) || !isValidLatLng(b)) return null
  return [(Number(a[0]) + Number(b[0])) / 2, (Number(a[1]) + Number(b[1])) / 2]
}

const renderRouteAssistPoints = () => {
  clearRouteAssistLayer()
  if (!map || activeMode.value !== 'route' || routeWorkMode.value !== 'creating') return
  const endpointRefs = getVisibleRouteEndpoints()
    .filter(endpoint => endpoint.type === 'ENDPOINT' && isValidLatLng(endpoint.point))
    .map(endpoint => {
    const marker = new AMapApi.Marker({
      position: latLngToLngLat(endpoint.point),
      content: makeAssistEndpointIcon(),
      offset: new AMapApi.Pixel(-8, -8),
      zIndex: 101
    })
    marker.on('click', event => {
      event?.originalEvent?.stopPropagation?.()
      handleRouteClick(endpoint.point, { forcedEndpoint: { ...endpoint, hitKind: 'endpoint' } })
    })
    return marker
  })
  if (endpointRefs.length) map.add(endpointRefs)
  routeAssistLayer = endpointRefs
}

const handleMapClick = (point) => {
  if (!isValidLatLng(point)) return
  if (isSuppressedMapClick()) return
  if (activeMode.value === 'spot') {
    createSpotAt(point)
    return
  }
  if (activeMode.value === 'facility') {
    createFacilityAt(point)
    return
  }
  if (activeMode.value === 'route') {
    if (routeWorkMode.value === 'creating') handleRouteClick(point)
    return
  }
  selectedAsset.value = null
  editingRoute.value = null
  renderAll()
}

const markMapDrag = () => {
  suppressNextMapClick = true
  if (suppressClickTimer) window.clearTimeout(suppressClickTimer)
  suppressClickTimer = window.setTimeout(() => {
    suppressNextMapClick = false
    suppressClickTimer = null
  }, 180)
}

const isSuppressedMapClick = () => {
  if (!suppressNextMapClick) return false
  suppressNextMapClick = false
  return true
}

const handleRouteClick = (point, options = {}) => {
  if (!isValidLatLng(point)) return
  if (routeWorkMode.value !== 'creating') return
  const endpoint = options.forcedEndpoint || resolveRouteEndpoint(point)
  if (routeDraft.step === 1) {
    const prevName = routeDraft.from?.name?.trim()
    routeDraft.from = endpoint.hitKind === 'blank' ? makeBlankEndpointDraft(point) : endpoint
    if (prevName && routeDraft.from?.hitKind === 'blank') routeDraft.from.name = prevName
    if (!routeDraft.from) return
    routeDraft.step = 2
    renderDraft()
    return
  }
  if (routeDraft.step === 2) {
    if (endpoint.hitKind === 'blank') {
      routeDraft.middlePoints.push([...point])
    } else {
      if (sameEndpoint(routeDraft.from, endpoint)) {
        ElMessage.warning('路线头尾不能相同')
        return
      }
      routeDraft.to = endpoint
      routeDraft.step = 3
      routeDraft.lengthMeter = calcDistance(getDraftPolyline())
      routeDraft.durationMin = Math.max(1, Math.round(routeDraft.lengthMeter / 70))
    }
    renderDraft()
  }
}

const finishRouteAtLastPoint = () => {
  if (routeWorkMode.value !== 'creating' || routeDraft.step !== 2 || !routeDraft.from) return
  const points = getDraftPolyline()
  if (points.length < 2) return
  const point = points[points.length - 1]
  if (!isValidLatLng(point)) return
  const endpoint = makeBlankEndpointDraft(point)
  if (sameEndpoint(routeDraft.from, endpoint)) return
  const prevName = routeDraft.to?.name?.trim()
  if (prevName) endpoint.name = prevName
  if (routeDraft.middlePoints.length > 0) {
    routeDraft.middlePoints.pop()
  }
  routeDraft.to = endpoint
  routeDraft.step = 3
  routeDraft.lengthMeter = calcDistance(getDraftPolyline())
  routeDraft.durationMin = Math.max(1, Math.round(routeDraft.lengthMeter / 70))
  renderDraft()
}

const getEditingPolylinePoints = () => {
  if (!editingPolyline) return []
  return lngLatsToLatLngs(editingPolyline.getPath?.() || [])
}

const syncRouteDraftFromEditingPolyline = () => {
  const points = validMapPoints(getEditingPolylinePoints())
  if (routeWorkMode.value !== 'editing' || points.length < 2) return
  routeDraft.middlePoints = points.slice(1, -1).map(point => [...point])
  routeDraft.step = 3
  routeDraft.lengthMeter = calcDistance(points)
  routeDraft.durationMin = Math.max(1, Math.round(routeDraft.lengthMeter / 70))
}

const makeBlankEndpointDraft = (point) => ({
  type: 'ENDPOINT',
  id: null,
  name: '',
  point: [...point],
  hitKind: 'blank'
})

const materializeRouteEndpoint = (endpoint) => {
  if (!endpoint || endpoint.hitKind !== 'blank') return endpoint
  const name = endpoint.name?.trim() || '地图点'
  const internal = createInternalEndpoint(endpoint.point, name)
  return internal || endpoint
}

const pushRouteEditHistory = () => {
  const points = validMapPoints(getEditingPolylinePoints())
  if (points.length < 2) return
  const last = routeEditHistory[routeEditHistory.length - 1]
  if (samePointList(last, points)) return
  routeEditHistory.push(points.map(point => [...point]))
  if (routeEditHistory.length > 40) routeEditHistory.shift()
}

const undoRouteEditHistory = () => {
  if (!editingPolyline || routeEditHistory.length <= 1) return
  routeEditHistory.pop()
  const previous = routeEditHistory[routeEditHistory.length - 1]
  setEditingPolylinePoints(previous)
  clearRouteSnapFocusLayer()
}

const samePointList = (a = [], b = []) => {
  if (a.length !== b.length) return false
  return a.every((point, index) => Math.abs(Number(point[0]) - Number(b[index][0])) < 1e-8 && Math.abs(Number(point[1]) - Number(b[index][1])) < 1e-8)
}

const cancelRouteWork = () => {
  stopRouteEditor()
  editingRoute.value = null
  activeMode.value = 'select'
  resetDrafts()
  renderAll()
}

const handleWorkbenchKeydown = (event) => {
  if (event.key === 'Enter' && activeMode.value === 'route' && routeWorkMode.value === 'creating' && routeDraft.step === 2) {
    event.preventDefault()
    finishRouteAtLastPoint()
    return
  }
  if (event.key === 'Escape' && activeMode.value === 'route') {
    event.preventDefault()
    cancelRouteWork()
  }
}

const commitRoute = () => {
  if (routeWorkMode.value === 'editing') syncRouteDraftFromEditingPolyline()
  if (routeWorkMode.value === 'creating' && routeDraft.step === 2) {
    finishRouteAtLastPoint()
    if (!routeDraft.to) {
      ElMessage.warning('需要至少两个路径点才能保存路线')
      return
    }
  }
  const from = materializeRouteEndpoint(routeDraft.from)
  const to = materializeRouteEndpoint(routeDraft.to)
  if (!from || !to || sameEndpoint(from, to)) {
    ElMessage.warning('路线头尾不能相同')
    return
  }
  routeDraft.from = from
  routeDraft.to = to
  syncPresetRouteEndpointName()
  const defaultName = `${from.name} - ${to.name}`
  const route = {
    id: editingRoute.value?.id || createMapObjectId('route'),
    kind: 'route',
    typeLabel: '路线',
    name: editingRoute.value?.name || defaultName,
    from: { ...from },
    to: { ...to },
    middlePoints: routeDraft.middlePoints.map(point => [...point]),
    lengthMeter: Number(editingRoute.value?.lengthMeter ?? routeDraft.lengthMeter ?? calcDistance(getDraftPolyline())),
    durationMin: Number(editingRoute.value?.durationMin ?? routeDraft.durationMin ?? 1),
    routeType: routeDraft.routeType,
    paid: routeDraft.paid,
    aiNote: routeDraft.aiNote,
    crowdLevel: editingRoute.value?.crowdLevel || 1,
    status: editingRoute.value?.status ?? 1
  }
  syncRouteEndpointNameToInternal(from)
  syncRouteEndpointNameToInternal(to)
  const index = routeSegments.findIndex(item => String(item.id) === String(route.id))
  if (index >= 0) routeSegments[index] = route
  else routeSegments.push(route)
  selectedAsset.value = route
  stopRouteEditor()
  editingRoute.value = null
  markDirty()
  cleanupOrphanEndpoints()
  resetDrafts()
  activeMode.value = 'select'
  renderAll()
  ElMessage.success('路线已保存')
}

const undoRoutePoint = () => {
  if (routeWorkMode.value === 'editing') {
    undoRouteEditHistory()
    return
  }
  if (routeDraft.step === 3) {
    routeDraft.to = null
    routeDraft.step = 2
  } else if (routeDraft.middlePoints.length) {
    routeDraft.middlePoints.pop()
  } else if (routeDraft.from) {
    routeDraft.from = null
    routeDraft.step = 1
  }
  renderDraft()
}

const commitSpot = () => {
  if (!spotDraft.point) return
  createSpotAt(spotDraft.point)
}

const createSpotAt = (point) => {
  if (!isValidLatLng(point)) return
  const id = createMapObjectId('spot')
  const radius = Number(spotDraft.radius || 50)
  const polygon = spotDraft.fenceType === 'circle'
    ? makeCirclePolygon(point, radius)
    : makeBoxPolygon(point[0], point[1], 0.00022, 0.00028)
  const center = getPolygonCenter(polygon) || point
  const spot = {
    id,
    kind: 'spot',
    typeLabel: '景点',
    name: spotDraft.name || `景点 ${spots.length + 1}`,
    description: '',
    labelPoint: [...center],
    polygon,
    fenceType: spotDraft.fenceType === 'circle' ? '圆形' : '多边形',
    radius,
    status: 1,
    longitude: center[1].toFixed(6),
    latitude: center[0].toFixed(6)
  }
  spots.push(spot)
  markDirty()
  selectedAsset.value = spot
  resetDrafts()
  activeMode.value = 'select'
  renderAll()
}

const commitFacility = () => {
  if (!facilityDraft.point) return
  createFacilityAt(facilityDraft.point)
}

const createFacilityAt = (point) => {
  if (!isValidLatLng(point)) return
  const facility = {
    id: createMapObjectId('facility'),
    kind: 'facility',
    typeLabel: '服务点',
    name: facilityDraft.name || `服务点 ${facilities.length + 1}`,
    facilityType: facilityDraft.facilityType,
    description: '',
    point: [...point],
    status: 1,
    longitude: point[1].toFixed(6),
    latitude: point[0].toFixed(6)
  }
  facilities.push(facility)
  markDirty()
  selectedAsset.value = facility
  resetDrafts()
  activeMode.value = 'select'
  renderAll()
}

const selectAsset = (asset) => {
  if (asset.kind !== 'spot' || String(asset.id) !== String(fenceEditingSpotId.value)) stopFenceEdit()
  if (!isSameAsset(positionEditingAsset.value, asset)) stopPositionEdit()
  if (activeMode.value === 'route') resetDrafts()
  else stopRouteEditor()
  selectedAsset.value = asset
  editingRoute.value = null
  activeMode.value = 'select'
  clearEditLayer()
  renderAll()
}

const isEditingFence = (spot) => spot?.kind === 'spot' && String(fenceEditingSpotId.value) === String(spot.id)

const isSameAsset = (a, b) => Boolean(a && b && a.kind === b.kind && String(a.id) === String(b.id))

const isPositionEditing = (asset) => isSameAsset(positionEditingAsset.value, asset)

const toggleSpotEdit = (spot) => {
  if (isOnlineDraft.value) {
    ElMessage.warning('ONLINE 状态下禁止编辑物理拓扑')
    return
  }
  if (!spot || spot.kind !== 'spot') return
  if (isEditingFence(spot) && isPositionEditing(spot)) {
    stopPositionEdit()
    stopFenceEdit()
    ElMessage.success('景点编辑已完成')
  } else {
    stopRouteEditor()
    activeMode.value = 'select'
    editingRoute.value = null
    selectedAsset.value = spot
    positionEditingAsset.value = { kind: 'spot', id: spot.id }
    fenceEditingSpotId.value = spot.id
    ElMessage.info(
      spot.fenceType === '圆形'
        ? '拖动景点调整位置，通过右侧面板半径值调整围栏范围'
        : '拖动景点调整位置，拖动围栏顶点调整范围'
    )
  }
  renderAll()
}

const togglePositionEdit = (asset) => {
  if (isOnlineDraft.value) {
    ElMessage.warning('ONLINE 状态下禁止编辑物理拓扑')
    return
  }
  if (!asset || !['spot', 'facility'].includes(asset.kind)) return
  if (isPositionEditing(asset)) {
    stopPositionEdit()
    ElMessage.success('位置编辑已完成')
  } else {
    stopFenceEdit()
    stopRouteEditor()
    activeMode.value = 'select'
    editingRoute.value = null
    selectedAsset.value = asset
    positionEditingAsset.value = { kind: asset.kind, id: asset.id }
    ElMessage.info('拖动当前点位调整位置')
  }
  renderAll()
}

const stopPositionEdit = () => {
  positionEditingAsset.value = null
}

const stopFenceEdit = () => {
  fenceEditingSpotId.value = null
}

const syncSpotPolygonFromLayer = (spot, layer) => {
  if (spot.fenceType === '圆形') return
  const polygon = lngLatsToLatLngs(layer.getPath?.() || [])
  if (polygon.length < 3) return
  spot.polygon = polygon
  const center = getPolygonCenter(polygon)
  if (isValidLatLng(center)) {
    spot.labelPoint = center
    spot.longitude = center[1].toFixed(6)
    spot.latitude = center[0].toFixed(6)
  }
  spot.fenceType = '多边形'
  markDirty()
}

const setSpotFencePolygon = async (spot) => {
  if (!spot || spot.fenceType !== '圆形') return
  try {
    await ElMessageBox.confirm(
      '切换为多边形会基于当前景点中心重新生成可编辑范围，当前半径围栏将被重置，是否继续？',
      '切换围栏类型',
      { type: 'warning' }
    )
  } catch (error) {
    return
  }
  if (!isValidLatLng(spot.labelPoint)) return
  spot.fenceType = '多边形'
  spot.polygon = makeBoxPolygon(spot.labelPoint[0], spot.labelPoint[1], 0.00022, 0.00028)
  const center = getPolygonCenter(spot.polygon) || spot.labelPoint
  if (isValidLatLng(center)) {
    spot.labelPoint = center
    spot.longitude = center[1].toFixed(6)
    spot.latitude = center[0].toFixed(6)
  }
  markDirtyAndRender()
}

const setSpotFenceCircle = async (spot) => {
  if (!isValidLatLng(spot?.labelPoint)) return
  if (spot.fenceType === '圆形') return
  try {
    await ElMessageBox.confirm(
      '切换为半径生成会按当前景点中心重新生成圆形近似多边形，原多边形顶点将被重置，是否继续？',
      '切换围栏类型',
      { type: 'warning' }
    )
  } catch (error) {
    return
  }
  spot.fenceType = '圆形'
  spot.radius = Number(spot.radius || 50)
  spot.polygon = makeCirclePolygon(spot.labelPoint, spot.radius)
  const center = getPolygonCenter(spot.polygon) || spot.labelPoint
  if (!isValidLatLng(center)) return
  spot.labelPoint = center
  spot.longitude = center[1].toFixed(6)
  spot.latitude = center[0].toFixed(6)
  markDirtyAndRender()
}

const regenerateSpotCircle = (spot) => {
  if (!spot || spot.fenceType !== '圆形') {
    markDirty()
    return
  }
  if (!isValidLatLng(spot.labelPoint)) return
  spot.polygon = makeCirclePolygon(spot.labelPoint, Number(spot.radius || 50))
  const center = getPolygonCenter(spot.polygon) || spot.labelPoint
  if (!isValidLatLng(center)) return
  spot.labelPoint = center
  spot.longitude = center[1].toFixed(6)
  spot.latitude = center[0].toFixed(6)
  markDirtyAndRender()
}

const selectAssetFromTray = (asset) => {
  selectAsset(asset)
  focusAsset(asset)
}

const handleTrayWheel = (event) => {
  const target = event.currentTarget
  if (!target) return
  const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY
  target.scrollLeft += delta
}

const isSelectedAsset = (asset) => selectedAsset.value &&
  selectedAsset.value.kind === asset.kind &&
  String(selectedAsset.value.id) === String(asset.id)

const focusAsset = (asset) => {
  if (!map || !asset) return
  if (asset.kind === 'route') {
    const points = getRoutePolyline(asset)
    focusBounds(points, asset.name)
    return
  }
  if (asset.kind === 'preset') {
    const pts = buildPresetRoutePath(asset)
    if (pts.length > 0) {
      focusBounds(pts, asset.name)
    }
    return
  }
  const point = getAssetCenter(asset)
  if (point) focusMapPoint(point, asset.name)
}

const getAssetCenter = (asset) => {
  if (!asset) return null
  if (asset.kind === 'spot') return asset.labelPoint
  if (asset.kind === 'facility') return asset.point
  if (asset.kind === 'route') return getPolylineCenter(getRoutePolyline(asset))
  if (asset.kind === 'preset') {
    const pts = buildPresetRoutePath(asset)
    return getPolylineCenter(pts)
  }
  return asset.point || null
}

const getAssetKindLabel = (asset) => {
  if (asset.kind === 'spot') return asset.typeLabel || '景点'
  if (asset.kind === 'facility') return getFacilityTypeLabel(asset.facilityType)
  if (asset.kind === 'route') return `${getRouteTypeLabel(asset.routeType)}路线`
  if (asset.kind === 'preset') return '预置路线'
  return '资产'
}

const getOptionLabel = (options, value, fallback) => {
  if (!value) return fallback
  const option = options.find(item => item.value === value)
  return option?.label || value
}

const getFacilityTypeLabel = (value) => getOptionLabel(facilityTypeOptions, value, '服务点')

const getRouteTypeLabel = (value) => getOptionLabel(routeTypeOptions, value, '步行')

const getAssetCoordinateText = (asset) => {
  const point = getAssetCenter(asset)
  return formatLatLng(point) || '暂无坐标'
}

const editAsset = (asset) => {
  if (isOnlineDraft.value) {
    ElMessage.warning('ONLINE 状态下禁止编辑物理拓扑')
    return
  }
  if (asset.kind === 'route') {
    stopPositionEdit()
    stopFenceEdit()
    stopRouteEditor()
    activeMode.value = 'route'
    routeWorkMode.value = 'editing'
    editingRoute.value = asset
    routeDraft.step = 3
    routeDraft.from = { ...asset.from, hitKind: asset.from?.type === 'ENDPOINT' ? 'endpoint' : 'asset', endpointType: asset.from?.type === 'ENDPOINT' ? 'MAP_POINT' : undefined }
    routeDraft.to = { ...asset.to, hitKind: asset.to?.type === 'ENDPOINT' ? 'endpoint' : 'asset', endpointType: asset.to?.type === 'ENDPOINT' ? 'MAP_POINT' : undefined }
    routeDraft.middlePoints = asset.middlePoints.map(point => [...point])
    routeDraft.lengthMeter = asset.lengthMeter
    routeDraft.durationMin = asset.durationMin
    routeDraft.routeType = asset.routeType
    routeDraft.paid = asset.paid
    routeDraft.aiNote = asset.aiNote || ''
    renderAll()
  }
}

const deleteAsset = async (asset) => {
  if (isOnlineDraft.value) {
    ElMessage.warning('ONLINE 状态下禁止删除物理拓扑')
    return
  }
  stopPositionEdit()
  stopFenceEdit()
  stopRouteEditor()
  if (asset.kind === 'route') {
    await ElMessageBox.confirm(`确认删除路线「${asset.name}」？`, '删除路线', { type: 'warning' })
    removeRoute(asset.id)
    ElMessage.success('路线已删除')
  } else if (asset.kind === 'spot') {
    const related = findRelatedRoutes('SPOT', asset.id)
    const lines = related.map(route => route.name).join('\n')
    await ElMessageBox.confirm(`删除景点「${asset.name}」将同时删除 ${related.length} 条关联路线：\n${lines || '无'}\n是否继续？`, '删除景点', { type: 'warning' })
    removeRoutes(related.map(route => route.id))
    replaceList(spots, spots.filter(item => item.id !== asset.id))
    ElMessage.success('景点已删除')
  } else if (asset.kind === 'facility') {
    const related = findRelatedRoutes('FACILITY', asset.id)
    const lines = related.map(route => route.name).join('\n')
    await ElMessageBox.confirm(`删除服务点「${asset.name}」将同时删除 ${related.length} 条关联路线：\n${lines || '无'}\n是否继续？`, '删除服务点', { type: 'warning' })
    removeRoutes(related.map(route => route.id))
    replaceList(facilities, facilities.filter(item => item.id !== asset.id))
    ElMessage.success('服务点已删除')
  }
  selectedAsset.value = null
  markDirty()
  cleanupOrphanEndpoints()
  renderAll()
}

const moveSpot = (spot, point) => {
  if (!isValidLatLng(point) || !isValidLatLng(spot.labelPoint)) return
  const old = spot.labelPoint
  const next = [...point]
  const delta = [next[0] - old[0], next[1] - old[1]]
  spot.labelPoint = next
  spot.polygon = spot.polygon.map(point => [point[0] + delta[0], point[1] + delta[1]])
  spot.longitude = next[1].toFixed(6)
  spot.latitude = next[0].toFixed(6)
  if (spot.fenceType === '圆形') {
    spot.polygon = makeCirclePolygon(next, Number(spot.radius || 50))
  }
}

const moveFacility = (facility, point) => {
  if (!isValidLatLng(point)) return
  facility.point = [...point]
  facility.longitude = point[1].toFixed(6)
  facility.latitude = point[0].toFixed(6)
}

const syncRoutesForMovedAsset = (type, id, name, point) => {
  if (!isValidLatLng(point)) return
  routeSegments.forEach(route => {
    if (route.from?.type === type && String(route.from.id) === String(id)) {
      route.from = { ...route.from, name, point: [...point] }
    }
    if (route.to?.type === type && String(route.to.id) === String(id)) {
      route.to = { ...route.to, name, point: [...point] }
    }
  })
}

const resolveRouteEndpoint = (point, options = {}) => {
  if (!isValidLatLng(point)) return { type: 'ENDPOINT', id: null, name: '地图点', point: null, hitKind: 'blank' }
  const endpoint = findEndpointHit(point, options)
  if (endpoint) return { ...endpoint, hitKind: 'endpoint' }
  const target = findTargetHit(point)
  if (target) return target
  return { type: 'ENDPOINT', id: null, name: '地图点', point, hitKind: 'blank' }
}

const makeTargetEndpoint = (type, asset) => ({
  type,
  id: asset.id,
  name: asset.name,
  point: type === 'SPOT' ? asset.labelPoint : asset.point,
  hitKind: type === 'SPOT' ? 'spot' : 'facility'
})

const findTargetHit = (point) => {
  const candidates = [
    ...spots.map(spot => makeTargetEndpoint('SPOT', spot)),
    ...facilities.map(facility => makeTargetEndpoint('FACILITY', facility))
  ]
  return findNearestByPixel(candidates, point, SNAP_PIXEL_RADIUS, item => item.point)
}

const findEndpointHit = (point, options = {}) => {
  const endpoints = getVisibleRouteEndpoints(options)
  return findNearestByPixel(endpoints, point, SNAP_PIXEL_RADIUS, endpoint => endpoint.point)
}

const createInternalEndpoint = (point, name = '地图点') => {
  if (!isValidLatLng(point)) return null
  const endpointName = name?.trim() || '地图点'
  const endpoint = {
    id: createMapObjectId('endpoint'),
    kind: 'endpoint',
    type: 'ENDPOINT',
    name: endpointName,
    endpointType: 'MAP_POINT',
    point: [...point],
    longitude: Number(point[1]).toFixed(6),
    latitude: Number(point[0]).toFixed(6)
  }
  routeEndpoints.push(endpoint)
  return { type: 'ENDPOINT', id: endpoint.id, name: endpoint.name, point: endpoint.point, hitKind: 'endpoint' }
}

const getVisibleRouteEndpoints = (options = {}) => {
  const excludedRefs = options.excludeRefs || []
  const isExcluded = endpoint => excludedRefs.some(ref => sameEndpoint(ref, endpoint))
  const endpoints = [...routeEndpoints.map(endpoint => ({
    type: 'ENDPOINT',
    id: endpoint.id,
    name: endpoint.name || '地图点',
    point: endpoint.point
  })).filter(endpoint => isValidLatLng(endpoint.point) && !isExcluded(endpoint))]
  routeSegments.forEach(route => {
    if (options.excludeRouteId && String(route.id) === String(options.excludeRouteId)) return
    ;[route.from, route.to].forEach(ref => {
      if (isExcluded(ref)) return
      if (ref.type === 'ENDPOINT' && !endpoints.some(item => String(item.id) === String(ref.id))) {
        const point = resolveEndpointPoint(ref)
        if (isValidLatLng(point)) endpoints.push({ ...ref, point })
      }
    })
  })
  return endpoints
}

const getAssetPoint = (asset) => {
  if (!asset) return null
  if (asset.kind === 'spot') return asset.labelPoint
  if (asset.kind === 'facility') return asset.point
  return asset.point
}

const resolveEndpointPoint = (ref) => {
  if (!ref) return null
  if (ref.type === 'SPOT') return spots.find(item => String(item.id) === String(ref.id))?.labelPoint || ref.point
  if (ref.type === 'FACILITY') return facilities.find(item => String(item.id) === String(ref.id))?.point || ref.point
  if (ref.type === 'ENDPOINT' && isValidLatLng(ref.point)) return ref.point
  return routeEndpoints.find(item => String(item.id) === String(ref.id))?.point || ref.point
}

const getRoutePolyline = (route) => {
  return validMapPoints([resolveEndpointPoint(route.from), ...(route.middlePoints || []), resolveEndpointPoint(route.to)])
}

const getRoutePolylineOriented = (segment, fromKey, toKey) => {
  const points = getRoutePolyline(segment)
  if (points.length === 0) return []
  const fromRef = segment.from || {}
  const toRef = segment.to || {}
  const actualFrom = endpointKey(fromRef)
  const actualTo = endpointKey(toRef)
  if (actualFrom === fromKey && actualTo === toKey) return points
  if (actualFrom === toKey && actualTo === fromKey) return [...points].reverse()
  // 如果 key 不匹配（比如 via 端点），按最近点判断方向
  const startPoint = resolveEndpointPointByKey(fromKey)
  const endPoint = resolveEndpointPointByKey(toKey)
  if (startPoint && endPoint) {
    const firstIsCloser = distanceBetween(points[0], startPoint) < distanceBetween(points[points.length - 1], startPoint)
    if (!firstIsCloser) return [...points].reverse()
  }
  return points
}

const resolveEndpointPointByKey = (key) => {
  if (!key) return null
  const [type, id] = key.split(':')
  if (!id) return null
  if (type === 'SPOT') return spots.find(s => String(s.id) === id)?.labelPoint || null
  if (type === 'FACILITY') return facilities.find(f => String(f.id) === id)?.point || null
  if (type === 'ENDPOINT') return routeEndpoints.find(e => String(e.id) === id)?.point || null
  return null
}

const distanceBetween = (a, b) => {
  if (!a || !b) return Infinity
  const latDiff = Number(a[0]) - Number(b[0])
  const lngDiff = Number(a[1]) - Number(b[1])
  return latDiff * latDiff + lngDiff * lngDiff
}

const getDraftPolyline = () => {
  const points = []
  if (routeDraft.from) points.push(resolveEndpointPoint(routeDraft.from) || routeDraft.from.point)
  points.push(...routeDraft.middlePoints)
  if (routeDraft.to) points.push(resolveEndpointPoint(routeDraft.to) || routeDraft.to.point)
  return validMapPoints(points)
}

const sameEndpoint = (a, b) => a && b && a.type === b.type && String(a.id) === String(b.id) && a.id !== null

const findNearestByPixel = (items, point, radiusPixels, pointGetter) => {
  if (!AMapApi || !isValidLatLng(point)) return null
  let best = null
  let bestDistance = Infinity
  const anchorPixel = toContainerPixel(point)
  if (!anchorPixel) return null
  const geoLimit = getSnapGeoLimitMeters(point, radiusPixels)
  items.forEach(item => {
    const itemPoint = pointGetter(item)
    if (!isValidLatLng(itemPoint)) return
    if (distanceMeters(AMapApi, point, itemPoint) > geoLimit) return
    const itemPixel = toContainerPixel(itemPoint)
    if (!itemPixel) return
    const distance = Math.hypot(itemPixel[0] - anchorPixel[0], itemPixel[1] - anchorPixel[1])
    if (distance < bestDistance && distance <= radiusPixels) {
      best = item
      bestDistance = distance
    }
  })
  return best
}

const toContainerPixel = (point) => {
  const lngLat = latLngToLngLat(point)
  if (!map || !lngLat) return null
  const pixel = map.lngLatToContainer(new AMapApi.LngLat(lngLat[0], lngLat[1]))
  const x = Number(pixel?.x ?? pixel?.getX?.())
  const y = Number(pixel?.y ?? pixel?.getY?.())
  return Number.isFinite(x) && Number.isFinite(y) ? [x, y] : null
}

const getSnapGeoLimitMeters = (point, radiusPixels) => {
  if (!map || !isValidLatLng(point)) return SNAP_GEO_MAX_METERS
  const pixel = toContainerPixel(point)
  if (!pixel) return SNAP_GEO_MAX_METERS
  const offsetPixel = new AMapApi.Pixel(pixel[0] + radiusPixels, pixel[1])
  const lngLat = map.containerToLngLat(offsetPixel)
  const offsetPoint = lngLatToLatLng(lngLat)
  const meters = isValidLatLng(offsetPoint) ? distanceMeters(AMapApi, point, offsetPoint) : SNAP_GEO_MAX_METERS
  return Math.min(SNAP_GEO_MAX_METERS, Math.max(8, meters * 1.8))
}

const calcDistance = (points) => {
  const valid = validMapPoints(points)
  if (valid.length < 2) return 0
  return Math.round(valid.slice(1).reduce((sum, point, index) => sum + distanceMeters(AMapApi, valid[index], point), 0))
}

const getDefaultMapView = () => {
  const firstSpot = spots.find(spot => isValidLatLng(spot.labelPoint))
  if (firstSpot) {
    return {
      center: firstSpot.labelPoint,
      zoom: 15
    }
  }
  return {
    center: BEIJING_CENTER,
    zoom: EMPTY_MAP_ZOOM
  }
}

const handleMapSearch = async () => {
  if (!map) return
  const keyword = mapSearchKeyword.value.trim()
  if (!keyword) {
    ElMessage.warning('请输入地点名称或经纬度')
    return
  }
  const point = parseCoordinateKeyword(keyword)
  if (point) {
    focusMapPoint(point, '坐标定位')
    return
  }
  const asset = findLocalAsset(keyword)
  if (asset) {
    selectAssetFromTray(asset)
    return
  }
  await searchAmapPlace(keyword)
}

const parseCoordinateKeyword = (keyword) => {
  const match = keyword.match(/(-?\d+(?:\.\d+)?)\s*[,，\s]\s*(-?\d+(?:\.\d+)?)/)
  if (!match) return null
  const first = Number(match[1])
  const second = Number(match[2])
  if (!Number.isFinite(first) || !Number.isFinite(second)) return null
  const firstAsLat = Math.abs(first) <= 90 && Math.abs(second) <= 180
  const firstAsLng = Math.abs(first) <= 180 && Math.abs(second) <= 90
  if (firstAsLat) return [first, second]
  if (firstAsLng) return [second, first]
  return null
}

const parseAmapLocation = (location) => {
  if (typeof location === 'string') {
    const [lng, lat] = location.split(',').map(Number)
    return Number.isFinite(lat) && Number.isFinite(lng) ? [lat, lng] : null
  }
  return lngLatToLatLng(location)
}

const findLocalAsset = (keyword) => {
  const normalized = keyword.toLowerCase()
  const assets = [...spots, ...facilities, ...routeSegments, ...presetRoutes]
  return assets.find(asset => [
    asset.name,
    asset.typeLabel,
    asset.facilityType,
    asset.routeType,
    asset.aiNote
  ].filter(Boolean).join(' ').toLowerCase().includes(normalized))
}

const searchAmapPlace = async (keyword) => {
  if (!AMapApi) return
  mapSearchBusy.value = true
  try {
    const point = await searchAmapPoiPoint(keyword)
    if (!point) {
      ElMessage.warning('没有找到匹配地点')
      return
    }
    focusMapPoint(point, keyword)
  } catch (error) {
    ElMessage.warning('地点搜索失败，请稍后重试')
  } finally {
    mapSearchBusy.value = false
  }
}

const searchAmapPoiPoint = (keyword) => new Promise(resolve => {
  const placeSearch = new AMapApi.PlaceSearch({
    pageSize: 1,
    pageIndex: 1,
    city: '全国',
    extensions: 'base'
  })
  placeSearch.search(keyword, (status, result) => {
    if (status !== 'complete') {
      resolve(null)
      return
    }
    const poi = result?.poiList?.pois?.[0]
    resolve(parseAmapLocation(poi?.location))
  })
})

const locateBrowserPosition = () => {
  if (!map || !AMapApi) return
  mapSearchBusy.value = true
  geolocation = geolocation || new AMapApi.Geolocation({
    enableHighAccuracy: true,
    timeout: 8000,
    showButton: false,
    showMarker: false,
    showCircle: false
  })
  geolocation.getCurrentPosition((status, result) => {
    mapSearchBusy.value = false
    if (status === 'complete') {
      focusMapPoint(lngLatToLatLng(result.position), '当前位置')
      return
    }
    ElMessage.warning('浏览器定位失败，请检查权限或改用经纬度定位')
  })
}

const focusMapPoint = (point, label = '定位点') => {
  if (!map || !isValidLatLng(point)) return
  map.setZoomAndCenter(Math.max(map.getZoom(), ASSET_FOCUS_ZOOM), latLngToLngLat(point), false, 650)
  showSearchFocus(point, label)
}

const focusBounds = (points, label = '路线') => {
  const valid = validMapPoints(points)
  if (!map || !valid.length) return
  if (valid.length === 1) {
    focusMapPoint(valid[0], label)
    return
  }
  const bounds = makeLngLatBounds(AMapApi, valid)
  if (bounds) {
    map.setBounds(bounds, false, [120, 120, 120, 120], ASSET_FOCUS_ZOOM)
  }
  showSearchFocus(getPolylineCenter(valid), label)
}

const showSearchFocus = (point, label) => {
  if (!map || !isValidLatLng(point)) return
  clearOverlayList(searchFocusLayer)
  const marker = new AMapApi.Marker({
    position: latLngToLngLat(point),
    content: createHtmlMarkerContent('search-focus-marker', `<span>${escapeHtml(label)}</span>`),
    offset: new AMapApi.Pixel(-12, -38),
    zIndex: 120
  })
  const circle = new AMapApi.CircleMarker({
    center: latLngToLngLat(point),
    radius: 12,
    strokeColor: '#f59e0b',
    strokeWeight: 3,
    fillColor: '#fbbf24',
    fillOpacity: 0.26
  })
  searchFocusLayer = [marker, circle]
  map.add(searchFocusLayer)
}

const getPolylineCenter = (points) => {
  const valid = validMapPoints(points)
  if (!valid.length) return null
  const lat = valid.reduce((sum, point) => sum + Number(point[0]), 0) / valid.length
  const lng = valid.reduce((sum, point) => sum + Number(point[1]), 0) / valid.length
  return [lat, lng]
}

const findRelatedRoutes = (type, id) => routeSegments.filter(route =>
  (route.from.type === type && String(route.from.id) === String(id)) ||
  (route.to.type === type && String(route.to.id) === String(id))
)

const removeRoute = (routeId) => {
  replaceList(routeSegments, routeSegments.filter(route => String(route.id) !== String(routeId)))
  cleanupOrphanEndpoints()
}

const removeRoutes = (routeIds) => {
  const idSet = new Set(routeIds.map(String))
  replaceList(routeSegments, routeSegments.filter(route => !idSet.has(String(route.id))))
  cleanupOrphanEndpoints()
}

const cleanupOrphanEndpoints = () => {
  const used = new Set()
  routeSegments.forEach(route => {
    if (route.from.type === 'ENDPOINT') used.add(String(route.from.id))
    if (route.to.type === 'ENDPOINT') used.add(String(route.to.id))
  })
  replaceList(routeEndpoints, routeEndpoints.filter(endpoint => used.has(String(endpoint.id))))
}

const saveDraft = async (options = {}) => {
  if (!currentDraftId.value) {
    if (!options.isAuto) {
      ElMessage.warning('没有可保存的地图草稿')
    }
    return
  }
  if (!isOnlineDraft.value) {
    cleanupOrphanEndpoints()
  }
  const mapMeta = isOnlineDraft.value
    ? currentPayloadMeta.value
    : {
        mapCenter: map ? lngLatToLatLng(map.getCenter()) : currentPayloadMeta.value.mapCenter,
        defaultZoom: map ? map.getZoom() : currentPayloadMeta.value.defaultZoom
      }
  const payload = {
    mapCenter: mapMeta.mapCenter || BEIJING_CENTER,
    defaultZoom: mapMeta.defaultZoom || 16,
    spots: spots.map(serializeSpot),
    facilities: facilities.map(serializeFacility),
    routeEndpoints: routeEndpoints.map(serializeEndpoint),
    routeSegments: routeSegments.map(serializeRoute),
    presetRoutes: persistedPresetRoutes.value
  }
  const res = await request.put(`/admin/map-drafts/${currentDraftId.value}/workbench`, payload)
  if (res.code !== 'OK') {
    if (!options.isAuto) {
      ElMessage.warning(res.message || '保存失败')
    }
    return
  }
  const savedAt = res.data?.lastSavedAt || res.data?.updatedAt || new Date().toISOString()
  currentPayloadMeta.value = {
    mapCenter: payload.mapCenter,
    defaultZoom: payload.defaultZoom
  }
  if (currentDraft.value) {
    currentDraft.value = {
      ...currentDraft.value,
      lastSavedAt: savedAt,
      updatedAt: savedAt
    }
  }
  drafts.value = drafts.value.map(draft => {
    if (String(draft.id) !== String(currentDraftId.value)) return draft
    return {
      ...draft,
      lastSavedAt: savedAt,
      updatedAt: savedAt
    }
  })
  dirtyCount.value = 0
  if (!options.isAuto) {
    ElMessage.success('地图草稿已保存')
  }
}

const publishCurrentDraft = async () => {
  if (dirtyCount.value > 0) {
    ElMessage.warning('请先保存草稿后再发布')
    return
  }
  if (!currentDraftId.value || !currentDraft.value) return
  if (currentDraft.value.status === 'ONLINE') {
    ElMessage.info('当前草稿已是线上地图')
    return
  }
  try {
    await ElMessageBox.confirm(`确定将「${currentDraft.value.name}」发布上线吗？上线后该草稿的物理拓扑将被锁定。`, '发布上线', {
      confirmButtonText: '发布上线',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (error) {
    return
  }
  const res = await request.post(`/admin/map-drafts/${currentDraftId.value}/publish`)
  if (res.code !== 'OK') {
    ElMessage.warning(res.message || '发布失败')
    return
  }
  ElMessage.success('地图已发布上线，物理拓扑已锁定')
  currentDraft.value = {
    ...currentDraft.value,
    ...res.data
  }
  await loadWorkbenchData()
}

const makeTextIcon = (text, tone, point, subType = null, options = {}) => {
  let svg = ''
  if (tone === 'spot') {
    // 景点使用官方 MapLocation 图标路径
    svg = `<svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" style="width: 40px; height: 40px; display: block; fill: currentColor;" class="marker-svg marker-svg--spot">
      <path d="M800 416a288 288 0 1 0-576 0c0 118.144 94.528 272.128 288 456.576C705.472 688.128 800 534.144 800 416M512 960C277.312 746.688 160 565.312 160 416a352 352 0 0 1 704 0c0 149.312-117.312 330.688-352 544" fill="currentColor"/>
      <path d="M512 448a64 64 0 1 0 0-128 64 64 0 0 0 0 128m0 64a128 128 0 1 1 0-256 128 128 0 0 1 0 256m345.6 192L960 960H672v-64H352v64H64l102.4-256zm-68.928 0H235.328l-76.8 192h706.944z" fill="currentColor"/>
    </svg>`
  } else {
    // 设施点根据类型使用官方对应的组件路径
    let iconPath = ''
    if (subType === 'TOILET') {
      // 官方 ToiletPaper 双路径
      iconPath = `
        <path d="M595.2 128H320a192 192 0 0 0-192 192v576h384V352c0-90.496 32.448-171.2 83.2-224M736 64c123.712 0 224 128.96 224 288S859.712 640 736 640H576v320H64V320A256 256 0 0 1 320 64zM576 352v224h160c84.352 0 160-97.28 160-224s-75.648-224-160-224-160 97.28-160 224" fill="currentColor"/>
        <path d="M736 448c-35.328 0-64-43.008-64-96s28.672-96 64-96 64 43.008 64 96-28.672 96-64 96" fill="currentColor"/>`
    } else if (subType === 'DINING') {
      // 官方 KnifeFork 单路径
      iconPath = `<path d="M256 410.56V96a32 32 0 0 1 64 0v314.56A96 96 0 0 0 384 320V96a32 32 0 0 1 64 0v224a160 160 0 0 1-128 156.8V928a32 32 0 1 1-64 0V476.8A160 160 0 0 1 128 320V96a32 32 0 0 1 64 0v224a96 96 0 0 0 64 90.56m384-250.24V544h126.72c-3.328-78.72-12.928-147.968-28.608-207.744-14.336-54.528-46.848-113.344-98.112-175.872zM640 608v320a32 32 0 1 1-64 0V64h64c85.312 89.472 138.688 174.848 160 256s32 177.152 32 288z" fill="currentColor"/>`
    } else if (subType === 'PARKING') {
      // 官方 Van 货车/车辆组件单路径
      iconPath = `<path d="M128.896 736H96a32 32 0 0 1-32-32V224a32 32 0 0 1 32-32h576a32 32 0 0 1 32 32v96h164.544a32 32 0 0 1 31.616 27.136l54.144 352A32 32 0 0 1 922.688 736h-91.52a144 144 0 1 1-286.272 0H415.104a144 144 0 1 1-286.272 0zm23.36-64a143.872 143.872 0 0 1 239.488 0H568.32c17.088-25.6 42.24-45.376 71.744-55.808V256H128v416zm655.488 0h77.632l-19.648-128H704v64.896A144 144 0 0 1 807.744 672m48.128-192-14.72-96H704v96zM688 832a80 80 0 1 0 0-160 80 80 0 0 0 0 160m-416 0a80 80 0 1 0 0-160 80 80 0 0 0 0 160" fill="currentColor"/>`
    } else if (subType === 'SERVICE') {
      // 官方 Headset 耳机组件单路径
      iconPath = `<path d="M896 529.152V512a384 384 0 1 0-768 0v17.152A128 128 0 0 1 320 640v128a128 128 0 1 1-256 0V512a448 448 0 1 1 896 0v256a128 128 0 1 1-256 0V640a128 128 0 0 1 192-110.848M896 640a64 64 0 0 0-128 0v128a64 64 0 0 0 128 0zm-768 0v128a64 64 0 0 0 128 0V640a64 64 0 1 0-128 0" fill="currentColor"/>`
    } else {
      // 默认使用官方 UserFilled 用户图标组件
      iconPath = '<path d="M288 320a224 224 0 1 0 448 0 224 224 0 1 0-448 0m544 608H160a32 32 0 0 1-32-32v-96a160 160 0 0 1 160-160h448a160 160 0 0 1 160 160v96a32 32 0 0 1-32 32z" fill="currentColor"/>'
    }
    svg = `<svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" style="width: 40px; height: 40px; display: block; fill: currentColor;" class="marker-svg marker-svg--facility">${iconPath}</svg>`
  }

  return createHtmlMarkerContent(
    `asset-marker asset-marker--${tone}${options.editing ? ' is-editing' : ''}`,
    `<span><i>${svg}</i><b>${escapeHtml(text)}</b></span>`
  )
}

const serializeSpot = (spot) => ({
  id: spot.id,
  name: spot.name,
  description: spot.description,
  tags: Array.isArray(spot.tags) ? spot.tags : [],
  labelPoint: spot.labelPoint,
  polygon: spot.polygon,
  fenceType: spot.fenceType,
  radius: spot.radius,
  status: spot.status,
  longitude: spot.longitude,
  latitude: spot.latitude
})

const serializeFacility = (facility) => ({
  id: facility.id,
  name: facility.name,
  facilityType: facility.facilityType,
  description: facility.description,
  point: facility.point,
  status: facility.status,
  longitude: facility.longitude,
  latitude: facility.latitude
})

const serializeEndpoint = (endpoint) => ({
  id: endpoint.id,
  name: endpoint.name,
  endpointType: endpoint.endpointType,
  point: endpoint.point,
  longitude: endpoint.longitude,
  latitude: endpoint.latitude
})

const serializeRoute = (route) => ({
  id: route.id,
  name: route.name,
  from: route.from,
  to: route.to,
  middlePoints: route.middlePoints,
  lengthMeter: route.lengthMeter,
  durationMin: route.durationMin,
  routeType: route.routeType,
  paid: route.paid,
  aiNote: route.aiNote,
  crowdLevel: route.crowdLevel,
  status: route.status
})

const makeBoxPolygon = (lat, lng, latDelta, lngDelta) => ([
  [lat - latDelta, lng - lngDelta],
  [lat - latDelta, lng + lngDelta],
  [lat + latDelta, lng + lngDelta],
  [lat + latDelta, lng - lngDelta]
])

const makeCirclePolygon = (center, radiusMeters, sides = 24) => {
  if (!isValidLatLng(center)) return []
  const [lat, lng] = center
  const radius = Number(radiusMeters || 50)
  const earthRadius = 6378137
  const latRad = lat * Math.PI / 180
  return Array.from({ length: sides }, (_, index) => {
    const angle = 2 * Math.PI * index / sides
    const dLat = (radius * Math.sin(angle) / earthRadius) * 180 / Math.PI
    const dLng = (radius * Math.cos(angle) / (earthRadius * Math.cos(latRad))) * 180 / Math.PI
    return [lat + dLat, lng + dLng]
  })
}

const getPolygonCenter = (polygon = []) => {
  const points = validMapPoints(polygon)
  if (!points.length) return null
  let area = 0
  let latSum = 0
  let lngSum = 0
  for (let index = 0; index < points.length; index += 1) {
    const [lat1, lng1] = points[index]
    const [lat2, lng2] = points[(index + 1) % points.length]
    const cross = lng1 * lat2 - lng2 * lat1
    area += cross
    lngSum += (lng1 + lng2) * cross
    latSum += (lat1 + lat2) * cross
  }
  if (Math.abs(area) < 1e-12) {
    const lat = points.reduce((sum, point) => sum + Number(point[0]), 0) / points.length
    const lng = points.reduce((sum, point) => sum + Number(point[1]), 0) / points.length
    return [lat, lng]
  }
  return [latSum / (3 * area), lngSum / (3 * area)]
}

const makeAssistEndpointIcon = (active = false) => createHtmlMarkerContent(
  `route-assist-endpoint${active ? ' is-hot' : ''}`,
  '<span></span>'
)

const makeSnapFocusIcon = (type) => createHtmlMarkerContent(
  `route-snap-focus route-snap-focus--${String(type || 'endpoint').toLowerCase()}`,
  '<span></span>'
)

const makeRouteVertexIcon = (type) => createHtmlMarkerContent(
  `route-edit-handle route-edit-handle--${type}`,
  '<span></span>'
)

const escapeHtml = (value) => String(value || '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#039;')

const clearDraftLayer = () => {
  clearOverlayList(draftLayer)
  draftLayer = null
}

const clearEditLayer = () => {
  clearRouteEditHandles()
  clearOverlayList(editLayer)
  editLayer = null
  editingPolyline = null
}

const clearRouteEditHandles = () => {
  clearOverlayList(routeVertexHandles)
  clearOverlayList(routeMidpointHandles)
  routeVertexHandles = []
  routeMidpointHandles = []
}

const clearRouteAssistLayer = () => {
  clearOverlayList(routeAssistLayer)
  routeAssistLayer = null
}

const clearRouteSnapFocusLayer = () => {
  clearOverlayList(routeSnapFocusLayer)
  routeSnapFocusLayer = null
}

const renderRouteSnapFocus = (endpoint) => {
  clearRouteSnapFocusLayer()
  if (!map || !isValidLatLng(endpoint.point)) return
  const marker = new AMapApi.Marker({
    position: latLngToLngLat(endpoint.point),
    content: makeSnapFocusIcon(endpoint.type),
    offset: new AMapApi.Pixel(-9, -9),
    zIndex: 130
  })
  map.add(marker)
  routeSnapFocusLayer = marker
}

const stopRouteEditor = () => {
  map?.setStatus?.({ dragEnable: true })
  draggingRouteVertex = false
  routeEditHistory = []
  clearRouteSnapFocusLayer()
  clearEditLayer()
}

const clearOverlayList = (overlays) => {
  if (!map || !overlays) return
  const items = Array.isArray(overlays) ? overlays : [overlays]
  if (items.length) map.remove(items)
}

const clearAmapOverlays = () => {
  if (activeFenceEditor) {
    activeFenceEditor.close()
    activeFenceEditor = null
  }
  clearDraftLayer()
  if (!(routeWorkMode.value === 'editing' && editingPolyline)) clearEditLayer()
  clearRouteAssistLayer()
  clearRouteSnapFocusLayer()
  Object.keys(layerGroups).forEach(key => {
    clearOverlayList(layerGroups[key])
    layerGroups[key] = []
  })
}

const setMapLayerMode = (mode) => {
  mapLayerMode.value = mode
  if (!map || !AMapApi) return
  map.remove([satelliteLayer, roadNetLayer].filter(Boolean))
  const styleMap = {
    standard: 'amap://styles/normal',
    satellite: 'amap://styles/normal',
    satelliteRoad: 'amap://styles/normal',
    fresh: 'amap://styles/fresh',
    dark: 'amap://styles/dark'
  }
  map.setMapStyle(styleMap[mode] || 'amap://styles/normal')
  if (mode === 'satellite' || mode === 'satelliteRoad') map.add(satelliteLayer)
  if (mode === 'satelliteRoad') map.add(roadNetLayer)
  saveWorkbenchViewCache()
}

const toggleMapLabels = () => {
  showMapLabels.value = !showMapLabels.value
  if (map) map.setFeatures(showMapLabels.value ? ['bg', 'road', 'building', 'point'] : ['bg', 'road', 'building'])
  saveWorkbenchViewCache()
}

const handleDraftChange = async (draftId) => {
  if (dirtyCount.value > 0) {
    try {
      await ElMessageBox.confirm('您有未保存的修改，切换草稿将丢失这些修改。确定要切换吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch (e) {
      currentDraftId.value = currentDraft.value?.id
      return
    }
  }
  currentDraftId.value = draftId
  currentDraft.value = findDraftById(draftId) || currentDraft.value
  await loadWorkbenchData()
}

const showCreateDraftDialog = () => {
  ElMessageBox.prompt('请输入新草稿的名称', '新建地图草稿', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '草稿名称不能为空',
    inputValue: '新地图草稿'
  }).then(async ({ value }) => {
    const res = await request.post('/admin/map-drafts', {
      name: value.trim(),
      copyFromDraftId: null
    })
    if (res.code === 'OK') {
      ElMessage.success('新地图草稿创建成功')
      const newDraft = res.data || {}
      if (newDraft.id) {
        currentDraftId.value = newDraft.id
      }
      await loadWorkbenchData()
    } else {
      ElMessage.warning(res.message || '创建草稿失败')
    }
  }).catch(() => {})
}

const copyDraft = () => {
  if (!currentDraft.value) return
  ElMessageBox.prompt('请输入复制后的草稿名称', '复制当前地图草稿', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '草稿名称不能为空',
    inputValue: `${currentDraft.value.name}-副本`
  }).then(async ({ value }) => {
    const res = await request.post('/admin/map-drafts', {
      name: value.trim(),
      copyFromDraftId: currentDraft.value.id
    })
    if (res.code === 'OK') {
      ElMessage.success('草稿复制成功')
      const newDraft = res.data || {}
      if (newDraft.id) {
        currentDraftId.value = newDraft.id
      }
      await loadWorkbenchData()
    } else {
      ElMessage.warning(res.message || '复制草稿失败')
    }
  }).catch(() => {})
}

const refreshDraftList = async () => {
  const res = await request.get('/admin/map-drafts')
  if (res.code === 'OK') {
    drafts.value = res.data || []
  }
}

const confirmDeleteDraft = (draft = currentDraft.value) => {
  if (!draft) return
  if (draft.status === 'ONLINE') {
    ElMessage.warning('ONLINE 草稿无法删除')
    return
  }
  const createdText = formatDraftDate(draft.createdAt)
  const savedText = draft.lastSavedAt ? `\n最后保存：${formatDraftDate(draft.lastSavedAt)}` : ''
  ElMessageBox.confirm(`确定要删除草稿「${draft.name}」吗？\n创建时间：${createdText}${savedText}\n此操作无法恢复。`, '删除草稿', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    const res = await request.delete(`/admin/map-drafts/${draft.id}`)
    if (res.code === 'OK') {
      ElMessage.success('草稿删除成功')
      if (String(currentDraftId.value) === String(draft.id)) {
        currentDraftId.value = null
        currentDraft.value = null
        await loadWorkbenchData()
      } else {
        await refreshDraftList()
      }
    } else {
      ElMessage.warning(res.message || '删除草稿失败')
    }
  }).catch(() => {})
}

const createPresetRoute = () => {
  const newPreset = {
    id: createMapObjectId('preset'),
    kind: 'preset',
    name: '新建预置路线',
    spotIds: [],
    segmentIds: [],
    description: '',
    durationMin: 30,
    audience: '大众游客',
    recommendationNote: '',
    status: 1
  }
  presetRoutes.push(newPreset)
  markPresetDirty()
  selectAssetFromTray(newPreset)
}

const getSpotNameById = (id) => {
  const asset = findSpotOrFacilityById(id)
  return asset ? asset.name : id
}

const addSpotToPreset = (preset) => {
  if (!newPresetSpotId.value) return
  if (!preset.spotIds) preset.spotIds = []
  if (!preset.segmentIds) preset.segmentIds = []
  preset.spotIds.push(String(newPresetSpotId.value))
  syncPresetSegmentIds(preset)
  newPresetSpotId.value = null
  markPresetDirtyAndRender()
}

const removeSpotFromPreset = (preset, index) => {
  if (!preset.spotIds) return
  preset.spotIds.splice(index, 1)
  syncPresetSegmentIds(preset)
  markPresetDirtyAndRender()
}

const deletePresetRoute = (preset) => {
  let timer = null
  ElMessageBox.confirm(`确定要删除预置路线 "${preset.name}" 吗？\n3 秒后可确认删除，删除将立即生效。`, '删除预置路线', {
    confirmButtonText: '删除（3）',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'preset-delete-confirm-btn',
    customClass: 'preset-delete-confirm-box',
    beforeClose: (action, instance, done) => {
      if (action === 'confirm' && instance.confirmButtonDisabled) return
      if (timer) clearInterval(timer)
      done()
    }
  }).then(async () => {
    const idx = presetRoutes.findIndex(p => String(p.id) === String(preset.id))
    if (idx >= 0) {
      const deleted = presetRoutes[idx]
      presetRoutes.splice(idx, 1)
      selectedAsset.value = null
      markPresetDirtyAndRender()
      const success = await savePresetRoutesSnapshot()
      if (!success) {
        presetRoutes.splice(idx, 0, deleted)
        selectedAsset.value = preset
        markPresetDirtyAndRender()
        ElMessage.error('删除预置路线失败，已恢复')
      } else {
        ElMessage.success('已删除预置路线')
      }
    }
  }).catch(() => {
    if (timer) clearInterval(timer)
  })
  nextTick(() => {
    const button = document.querySelector('.preset-delete-confirm-box .preset-delete-confirm-btn')
    if (!button) return
    button.disabled = true
    let remaining = 3
    timer = setInterval(() => {
      remaining--
      if (remaining > 0) {
        button.textContent = `删除（${remaining}）`
        return
      }
      clearInterval(timer)
      timer = null
      button.disabled = false
      button.textContent = '删除'
    }, 1000)
  })
}

const savePresetRoutesSnapshot = async () => {
  if (!currentDraftId.value) return false
  presetSaveState.value = 'saving'
  try {
    const payload = presetRoutes.map(serializePresetRoute)
    // P1-9：预置路线管理入口已迁到 route 域，存储仍在 scenic 草稿 payload 随地图原子发布
    const res = await request.put(`/admin/routes/preset/${currentDraftId.value}`, payload)
    if (res.code !== 'OK') {
      presetSaveState.value = 'error'
      return false
    }
    await loadWorkbenchData()
    presetSaveState.value = 'saved'
    return true
  } catch (error) {
    console.error('save preset routes snapshot failed', error)
    presetSaveState.value = 'error'
    return false
  }
}

const syncPresetSegmentIds = (preset) => {
  if (!preset) return
  const count = Math.max(0, (preset.spotIds || []).length - 1)
  const next = (preset.segmentIds || []).slice(0, count)
  while (next.length < count) next.push('')
  preset.segmentIds = next
}

const updatePresetSegment = (preset, index, value) => {
  if (!preset || !preset.segmentIds) return
  preset.segmentIds[index] = value ? String(value) : ''
  markPresetDirtyAndRender()
}

const presetSegmentOptions = computed(() => {
  if (!selectedAsset.value || selectedAsset.value.kind !== 'preset') return []
  const preset = selectedAsset.value
  const ids = preset.spotIds || []
  const result = []
  for (let i = 0; i < ids.length - 1; i++) {
    const fromId = String(ids[i])
    const toId = String(ids[i + 1])
    const candidates = findRouteSegmentsBetweenSpots(fromId, toId)
    const selectedId = (preset.segmentIds || [])[i] || ''
    const current = candidates.find(c => String(c.id) === String(selectedId))
    const automaticPath = selectedId ? [] : autoPresetSegmentPath(fromId, toId)
    result.push({
      fromId,
      toId,
      reachable: Boolean(current) || automaticPath.length >= 2,
      candidates,
      currentName: current ? current.name : automaticPath.length >= 2 ? `自动 · ${getPresetPathDistance(fromId, toId)} 米` : ''
    })
  }
  return result
})

const findRouteSegmentsBetweenSpots = (fromId, toId) => {
  const list = []
  const fromAsset = findSpotOrFacilityById(fromId)
  const toAsset = findSpotOrFacilityById(toId)
  const fromKey = fromAsset ? endpointKey({ type: fromAsset.kind === 'facility' ? 'FACILITY' : 'SPOT', id: fromId }) : `SPOT:${fromId}`
  const toKey = toAsset ? endpointKey({ type: toAsset.kind === 'facility' ? 'FACILITY' : 'SPOT', id: toId }) : `SPOT:${toId}`
  for (const segment of routeSegments) {
    if (Number(segment.status ?? 1) === 0) continue
    const from = endpointKey(segment.from)
    const to = endpointKey(segment.to)
    if ((from === fromKey && to === toKey) || (from === toKey && to === fromKey)) {
      list.push(segment)
    }
  }
  return list
}

const endpointKey = (ref) => {
  if (!ref?.id) return ''
  const type = String(ref.type || ref.targetType || 'ENDPOINT').toUpperCase()
  return `${type}:${ref.id}`
}

const findSpotOrFacilityById = (id) => {
  if (!id) return null
  const str = String(id)
  return spots.find(s => String(s.id) === str) || facilities.find(f => String(f.id) === str) || null
}

const getPresetNodeKey = (id) => {
  const asset = findSpotOrFacilityById(id)
  if (!asset) return ''
  return endpointKey({ type: asset.kind === 'facility' ? 'FACILITY' : 'SPOT', id })
}

const buildPresetRoutePath = (preset) => {
  if (!preset) return []
  const ids = preset.spotIds || []
  if (ids.length < 2) return []
  const points = []
  for (let i = 0; i < ids.length - 1; i++) {
    const fromId = String(ids[i])
    const toId = String(ids[i + 1])
    const segmentId = (preset.segmentIds || [])[i] || ''
    const fromAsset = findSpotOrFacilityById(fromId)
    const toAsset = findSpotOrFacilityById(toId)
    if (!fromAsset || !toAsset) continue
    const fromPoint = fromAsset.labelPoint || fromAsset.point
    const toPoint = toAsset.labelPoint || toAsset.point
    if (points.length === 0) points.push(fromPoint)
    const segmentPoints = findPresetSegmentPath(fromId, toId, segmentId)
    if (segmentPoints && segmentPoints.length > 0) {
      for (const point of segmentPoints) {
        if (!samePoint(points[points.length - 1], point)) points.push(point)
      }
    } else {
      if (!samePoint(points[points.length - 1], toPoint)) points.push(toPoint)
    }
  }
  return points.filter(isValidLatLng)
}

const findPresetSegmentPath = (fromSpotId, toSpotId, segmentId) => {
  if (segmentId) {
    const segment = routeSegments.find(s => String(s.id) === String(segmentId))
    if (segment && Number(segment.status ?? 1) !== 0) {
      return getRoutePolylineOriented(segment, getPresetNodeKey(fromSpotId), getPresetNodeKey(toSpotId))
    }
  }
  // 自动规划：在路网上找最短路径
  return autoPresetSegmentPath(fromSpotId, toSpotId)
}

const autoPresetSegmentPath = (fromSpotId, toSpotId) => {
  const fromKey = getPresetNodeKey(fromSpotId)
  const toKey = getPresetNodeKey(toSpotId)
  if (!fromKey || !toKey) return []
  const graph = new Map()
  for (const segment of routeSegments) {
    if (Number(segment.status ?? 1) === 0 || !segment.from || !segment.to) continue
    const from = endpointKey(segment.from)
    const to = endpointKey(segment.to)
    if (!from || !to) continue
    addPresetGraphEdge(graph, from, to, segment)
    addPresetGraphEdge(graph, to, from, segment)
  }
  const distances = new Map([[fromKey, 0]])
  const previous = new Map()
  const pending = [{ key: fromKey, distance: 0 }]
  while (pending.length) {
    pending.sort((left, right) => left.distance - right.distance)
    const current = pending.shift()
    if (current.distance !== distances.get(current.key)) continue
    if (current.key === toKey) break
    for (const edge of graph.get(current.key) || []) {
      const nextDistance = current.distance + getRouteWeight(edge.segment)
      if (nextDistance < (distances.get(edge.toKey) ?? Infinity)) {
        distances.set(edge.toKey, nextDistance)
        previous.set(edge.toKey, { fromKey: current.key, segment: edge.segment })
        pending.push({ key: edge.toKey, distance: nextDistance })
      }
    }
  }
  if (!distances.has(toKey)) return []
  const steps = []
  let currentKey = toKey
  while (currentKey !== fromKey) {
    const step = previous.get(currentKey)
    if (!step) return []
    steps.unshift({ ...step, toKey: currentKey })
    currentKey = step.fromKey
  }
  const points = []
  for (const step of steps) {
    const segmentPoints = getRoutePolylineOriented(step.segment, step.fromKey, step.toKey)
    for (const point of segmentPoints) {
      if (!samePoint(points[points.length - 1], point)) points.push(point)
    }
  }
  return points
}

const addPresetGraphEdge = (graph, from, to, segment) => {
  if (!graph.has(from)) graph.set(from, new Set())
  graph.get(from).add({ toKey: to, segment })
}

const getRouteWeight = (segment) => Math.max(1, Number(segment.lengthMeter) || 1)

const getPresetPathDistance = (fromId, toId) => {
  const fromKey = getPresetNodeKey(fromId)
  const toKey = getPresetNodeKey(toId)
  if (!fromKey || !toKey) return 0
  const graph = new Map()
  for (const segment of routeSegments) {
    if (Number(segment.status ?? 1) === 0) continue
    const from = endpointKey(segment.from)
    const to = endpointKey(segment.to)
    if (from && to) {
      addPresetGraphEdge(graph, from, to, segment)
      addPresetGraphEdge(graph, to, from, segment)
    }
  }
  const distances = new Map([[fromKey, 0]])
  const pending = [{ key: fromKey, distance: 0 }]
  while (pending.length) {
    pending.sort((left, right) => left.distance - right.distance)
    const current = pending.shift()
    if (current.distance !== distances.get(current.key)) continue
    if (current.key === toKey) return Math.round(current.distance)
    for (const edge of graph.get(current.key) || []) {
      const nextDistance = current.distance + getRouteWeight(edge.segment)
      if (nextDistance < (distances.get(edge.toKey) ?? Infinity)) {
        distances.set(edge.toKey, nextDistance)
        pending.push({ key: edge.toKey, distance: nextDistance })
      }
    }
  }
  return 0
}

const samePoint = (a, b) => {
  if (!a || !b) return false
  return Math.abs(Number(a[0]) - Number(b[0])) < 1e-8 && Math.abs(Number(a[1]) - Number(b[1])) < 1e-8
}

const savePresetRoute = async (preset) => {
  if (!preset || !currentDraftId.value) return
  const savedId = preset.id
  const success = await savePresetRoutesSnapshot()
  if (!success) {
    ElMessage.error('保存预置路线失败')
    return
  }
  ElMessage.success(currentDraft.value?.status === 'ONLINE' ? '线上预置路线已热更新' : '预置路线已保存')
  await loadWorkbenchData()
  const restored = presetRoutes.find(p => String(p.id) === String(savedId))
  if (restored) {
    selectedAsset.value = restored
    trayTab.value = 'preset'
  }
}

const serializePresetRoute = (route) => ({
  id: route.id,
  name: route.name,
  spotIds: route.spotIds || [],
  segmentIds: route.segmentIds || [],
  description: route.description || '',
  durationMin: route.durationMin || 30,
  audience: route.audience || '',
  recommendationNote: route.recommendationNote || '',
  status: route.status ?? 1
})

const markRouteEndpointNameDirty = () => {
  if (routeDraft.from?.hitKind === 'blank' || routeDraft.from?.endpointType === 'MAP_POINT' ||
      routeDraft.to?.hitKind === 'blank' || routeDraft.to?.endpointType === 'MAP_POINT') {
    markDirty()
  }
}

const syncRouteEndpointNameToInternal = (ref) => {
  if (!ref || ref.type !== 'ENDPOINT' || !ref.id) return
  const endpoint = routeEndpoints.find(e => String(e.id) === String(ref.id))
  if (endpoint) {
    endpoint.name = ref.name?.trim() || '地图点'
  }
}

const syncPresetRouteEndpointName = () => {
  if (routeDraft.from?.hitKind === 'blank' || routeDraft.from?.endpointType === 'MAP_POINT') {
    routeDraft.from.name = routeDraft.from.name?.trim() || '地图点'
  }
  if (routeDraft.to?.hitKind === 'blank' || routeDraft.to?.endpointType === 'MAP_POINT') {
    routeDraft.to.name = routeDraft.to.name?.trim() || '地图点'
  }
}

const renderSelectedPresetRoute = (preset) => {
  const path = buildPresetRoutePath(preset)
  if (path.length < 2) return
  const polyline = new AMapApi.Polyline({
    path: latLngsToLngLats(path),
    strokeColor: '#f97316',
    strokeOpacity: preset.opacity ?? 0.9,
    strokeWeight: 8,
    strokeStyle: 'solid',
    showDir: true,
    zIndex: 98,
    bubble: true
  })
  map.add(polyline)
  layerGroups.routes.push(polyline)
}

const openPresetRouteFromQuery = () => {
  const presetRouteId = String(route.query.presetRouteId || '')
  if (!presetRouteId) return
  const preset = presetRoutes.find(item => String(item.id) === presetRouteId)
  if (!preset) {
    ElMessage.warning('指定预置路线不存在，已打开当前地图草稿')
    return
  }
  trayTab.value = 'preset'
  selectAssetFromTray(preset)
}

let autoSaveTimer = null

watch(activeMode, () => nextTick(renderAll))

onMounted(async () => {
  window.addEventListener('keydown', handleWorkbenchKeydown)
  window.addEventListener('pointermove', handleSpotDomDragMove, true)
  window.addEventListener('pointerup', handleSpotDomDragEnd, true)
  window.addEventListener('pointercancel', handleSpotDomDragEnd, true)
  await loadWorkbenchData()
  loadTagOptions()
  openPresetRouteFromQuery()
  await nextTick()
  await initMap()

  autoSaveTimer = setInterval(() => {
    if (dirtyCount.value > 0 && currentDraft.value && currentDraft.value.status !== 'ONLINE') {
      saveDraft({ isAuto: true })
    }
  }, 10000)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleWorkbenchKeydown)
  window.removeEventListener('pointermove', handleSpotDomDragMove, true)
  window.removeEventListener('pointerup', handleSpotDomDragEnd, true)
  window.removeEventListener('pointercancel', handleSpotDomDragEnd, true)
  if (autoSaveTimer) {
    clearInterval(autoSaveTimer)
    autoSaveTimer = null
  }
  map?.setStatus?.({ dragEnable: true })
  spotDomDragState = null
  if (suppressClickTimer) window.clearTimeout(suppressClickTimer)
  stopRouteEditor()
  clearOverlayList(searchFocusLayer)
  if (activeFenceEditor) activeFenceEditor.close()
  if (map) {
    map.destroy()
    map = null
  }
})
</script>

<style scoped>
.map-workbench {
  position: relative;
  height: 100%;
  overflow: hidden;
  background: #0f172a;
}

.map-canvas {
  position: absolute;
  inset: 0;
  z-index: 1;
}

.map-topbar,
.map-status-chip,
.tool-rail,
.work-panel,
.asset-tray {
  position: absolute;
  z-index: 500;
}

.map-topbar {
  top: 18px;
  left: 20px;
  right: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  pointer-events: none;
}

.map-topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  pointer-events: none;
}

.map-search,
.map-layer-controls,
.draft-actions {
  pointer-events: auto;
}

.map-search {
  width: 100%;
  max-width: 420px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 6px 6px 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(12px);
}

.map-search .el-icon {
  flex: 0 0 auto;
  color: #0f766e;
  font-size: 16px;
}

.map-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
  outline: none;
}

.map-search input::placeholder {
  color: #94a3b8;
}

.map-search button {
  display: inline-grid;
  place-items: center;
  min-width: 42px;
  height: 28px;
  border: 0;
  border-radius: 99px;
  background: rgba(0, 0, 0, 0.04);
  color: #334155;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}

.map-search button:hover {
  background: rgba(0, 0, 0, 0.08);
  color: #0f172a;
}

.map-search button[type="submit"] {
  min-width: 48px;
  background: #0066cc;
  color: #ffffff;
}

.map-search button[type="submit"]:hover {
  background: #0052a3;
}

.map-search button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.map-layer-controls {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(12px);
}

.map-layer-controls button {
  height: 28px;
  border: 0;
  border-radius: 99px;
  padding: 0 9px;
  background: transparent;
  color: #475569;
  font-size: 11px;
  font-weight: 800;
  cursor: pointer;
}

.map-layer-controls button:hover,
.map-layer-controls button.active {
  background: #0f766e;
  color: #ffffff;
}

.draft-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(12px);
}

.draft-actions span {
  font-size: 12px;
  color: #475569;
  font-weight: 600;
}

.draft-actions span.dirty {
  color: #fb923c;
}

.draft-actions button,
.btn {
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 8px 12px;
  background: #ffffff;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}

.draft-actions button:hover:not(:disabled) {
  background: #f8fafc;
  color: #0f172a;
}

.draft-actions button:disabled,
.btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn.primary {
  border-color: transparent;
  background: #0066cc;
  color: #ffffff;
}

.btn.primary:hover:not(:disabled) {
  background: #0052a3;
}

.btn.danger {
  border-color: transparent;
  background: #dc2626;
  color: #ffffff;
}

.btn.danger:hover:not(:disabled) {
  background: #b91c1c;
}

.map-status-chip {
  left: 20px;
  bottom: 160px;
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 14px;
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.85);
  color: #0f172a;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(12px);
}

.map-status-chip span {
  color: #0f766e;
  font-weight: 800;
}

.map-status-chip strong {
  font-size: 12px;
  color: #475569;
}

:deep(.amap-scalecontrol) {
  bottom: 160px !important;
  right: 20px !important;
  left: auto !important;
}

:deep(.amap-toolbar) {
  bottom: 190px !important;
  right: 20px !important;
}

.tool-rail {
  top: 94px;
  left: 20px;
  display: grid;
  gap: 6px;
}

.rail-button {
  width: 64px;
  min-height: 54px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  color: #334155;
  box-shadow: 0 16px 38px rgba(15, 23, 42, 0.14);
  cursor: pointer;
}

.rail-button.active {
  border-color: #0f766e;
  background: #0f766e;
  color: #ffffff;
}

.rail-button span {
  display: block;
  margin-top: 3px;
  font-size: 11px;
  font-weight: 800;
}

.work-panel {
  top: 94px;
  right: 20px;
  width: 316px;
  max-height: calc(100vh - 270px);
  overflow-y: auto;
  overflow-x: hidden;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(16px);
  color: #334155;
  transition: all 0.3s ease;
}

.work-panel::-webkit-scrollbar {
  width: 4px;
}

.work-panel::-webkit-scrollbar-track {
  background: transparent;
}

.work-panel::-webkit-scrollbar-thumb {
  background: rgba(15, 23, 42, 0.1);
  border-radius: 99px;
}

.panel-head {
  padding: 16px 18px 12px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.panel-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.panel-head strong {
  display: block;
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.panel-close {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}

.panel-close:hover {
  background: rgba(0, 0, 0, 0.04);
  color: #0f172a;
}

.panel-section {
  display: grid;
  gap: 12px;
  padding: 14px 18px 16px;
}

.field {
  display: grid;
  gap: 6px;
}

.field span {
  color: #475569;
  font-size: 11px;
  font-weight: 700;
}

.editor-input,
.editor-textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 7px 10px;
  background: #ffffff;
  color: #0f172a;
  font-size: 12px;
  outline: none;
  transition: border-color 0.2s;
}

.editor-input {
  height: 32px;
}

.input-with-action {
  display: flex;
  gap: 8px;
  width: 100%;
}

.input-with-action .editor-input {
  flex: 1;
  min-width: 0;
}

.btn-action {
  padding: 0 10px;
  height: 32px;
  line-height: 30px;
  background: #0f766e;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  transition: opacity 0.2s;
  font-weight: 500;
}

.btn-action:hover {
  opacity: 0.9;
}

.editor-input:focus,
.editor-textarea:focus {
  border-color: #0066cc;
}

.editor-textarea {
  resize: vertical;
  font-family: inherit;
}

.segmented,
.panel-actions,
.route-steps {
  display: flex;
  gap: 8px;
}

.panel-actions .btn {
  flex: 1;
}

.panel-actions.route-actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(82px, 1fr));
}

.panel-actions.action-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.panel-actions.preset-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.preset-save-state {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: -2px 0 12px;
  padding: 8px 10px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
  color: #1e40af;
  font-size: 12px;
  font-weight: 700;
}

.preset-save-state__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.preset-save-state.is-unsaved {
  border-color: #fed7aa;
  background: #fff7ed;
  color: #c2410c;
}

.preset-save-state.is-saving {
  color: #0369a1;
}

.preset-save-state.is-saving .preset-save-state__dot {
  animation: preset-saving-pulse 1s ease-in-out infinite;
}

.preset-save-state.is-error {
  border-color: #fecaca;
  background: #fff7f7;
  color: #b91c1c;
}

.preset-delete-confirm-box .preset-delete-confirm-btn:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

@keyframes preset-saving-pulse {
  50% { opacity: 0.28; transform: scale(0.72); }
}

.preset-new-btn {
  font-size: 13px;
  font-weight: 700;
  padding: 0 18px;
  height: 36px;
  border-radius: 8px;
}

.panel-actions.danger-row {
  justify-content: flex-end;
}

.panel-actions.danger-row .btn {
  flex: 0 0 auto;
  min-width: 108px;
}

.preset-segments-container {
  margin-top: 15px;
  border-top: 1px solid var(--border);
  padding-top: 15px;
}

.preset-segment-item {
  display: grid;
  gap: 8px;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  margin-bottom: 8px;
}

.preset-segment-item .segment-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

.preset-segment-item .segment-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.segment-hint {
  margin: 0;
  font-size: 11px;
  color: #64748b;
  line-height: 1.5;
}

.segment-hint--warn {
  color: #dc2626;
}

.segmented {
  display: flex;
  gap: 4px;
  background: rgba(0, 0, 0, 0.03);
  padding: 3px;
  border-radius: 8px;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.segmented button {
  flex: 1;
  border: 0;
  border-radius: 6px;
  padding: 6px;
  background: transparent;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}

.segmented button.active {
  background: #0066cc;
  color: #ffffff;
}

.segmented button:hover:not(.active) {
  color: #0f172a;
  background: rgba(0, 0, 0, 0.04);
}

.route-steps {
  display: flex;
  gap: 4px;
  background: rgba(0, 0, 0, 0.03);
  padding: 3px;
  border-radius: 8px;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.route-steps span {
  flex: 1;
  border: 0;
  border-radius: 6px;
  padding: 6px;
  background: transparent;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  text-align: center;
}

.route-steps span.active {
  background: rgba(13, 148, 136, 0.1);
  color: #0f766e;
}

.hint {
  margin: 0;
  color: #64748b;
  font-size: 11px;
  line-height: 1.6;
}

.route-field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.switch-field {
  align-content: end;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  font-size: 12px;
}

.detail-row span {
  color: #64748b;
}

.detail-row strong {
  color: #0f172a;
  font-weight: 600;
}

.route-meta p,
.empty-section p {
  margin: 10px 0 0;
  color: #475569;
  font-size: 11px;
  line-height: 1.6;
}

.work-panel :deep(.el-select) {
  width: 100%;
}

.work-panel :deep(.el-select .el-input__wrapper) {
  background-color: #ffffff !important;
  box-shadow: 0 0 0 1px #cbd5e1 inset !important;
  border-radius: 8px;
  min-height: 32px;
  padding: 3px 9px;
  transition: box-shadow 0.18s ease, background-color 0.18s ease;
}

.work-panel :deep(.el-select .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #94a3b8 inset !important;
}

.work-panel :deep(.el-select .el-input__inner) {
  color: #0f172a !important;
  font-size: 12px !important;
  font-weight: 700;
}

.work-panel :deep(.el-select .el-select__placeholder) {
  color: #0f172a !important;
  font-size: 12px !important;
  font-weight: 700;
}

.work-panel :deep(.el-select .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #0066cc inset !important;
}

.work-panel :deep(.el-select .el-input__suffix) {
  color: #64748b;
}

.work-panel :deep(.el-switch) {
  --el-switch-on-color: #0066cc;
}

.asset-tray {
  left: 0;
  right: 0;
  bottom: 0;
  display: grid;
  gap: 10px;
  padding: 12px 20px 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-bottom: 0;
  border-left: 0;
  border-right: 0;
  border-radius: 16px 16px 0 0;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 -8px 30px rgba(0, 0, 0, 0.05);
  backdrop-filter: blur(16px);
}

.tray-head {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 14px;
}

.tray-tabs {
  display: flex;
  align-items: center;
  gap: 18px;
}

.tray-tabs button {
  position: relative;
  border: 0;
  padding: 0 0 8px;
  background: transparent;
  color: #64748b;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.tray-tabs button::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  border-radius: 999px;
  background: transparent;
}

.tray-tabs button.active {
  color: #0f766e;
}

.tray-tabs button.active::after {
  background: #0f766e;
}

.tray-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  max-width: 420px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 99px;
  background: rgba(0, 0, 0, 0.03);
}

.tray-search .el-icon {
  color: #64748b;
}

.tray-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  color: #0f172a;
  font-size: 12px;
  font-weight: 600;
  outline: none;
}

.tray-search input::placeholder {
  color: #94a3b8;
}

.tray-head-right {
  display: flex;
  justify-content: flex-end;
}

.tray-list {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 0;
  scroll-behavior: smooth;
}

.tray-list::-webkit-scrollbar {
  height: 3px;
}

.tray-list::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.05);
}

.tray-list::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.15);
}

.tray-card {
  flex: 0 0 206px;
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  min-height: 56px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 8px;
  padding: 8px 10px;
  background: #ffffff;
  color: #0f172a;
  text-align: left;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
}

.tray-card:hover,
.tray-card.active {
  border-color: rgba(14, 116, 144, 0.44);
  background: #ecfeff;
}

.tray-card.disabled {
  opacity: 0.6;
  background: #f1f5f9;
}

.tray-card.route.active {
  border-color: rgba(194, 65, 12, 0.48);
  background: #fff7ed;
}

.tray-card-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #047857;
  color: #ffffff;
  font-size: 16px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.22);
}

.tray-card.facility .tray-card-icon {
  background: #1d4ed8;
}

.tray-card.route .tray-card-icon {
  background: #c2410c;
}

.tray-card-main {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.tray-card-main strong,
.tray-card-main em {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tray-card-main strong {
  font-size: 13px;
  font-style: normal;
  font-weight: 900;
}

.tray-card-main em {
  color: #64748b;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
}

.tray-card-status {
  align-self: center;
  border-radius: 999px;
  padding: 2px 6px;
  background: rgba(22, 101, 52, 0.08);
  color: #166534;
  font-size: 10px;
  font-weight: 900;
}

.tray-empty {
  display: grid;
  place-items: center;
  min-width: 220px;
  min-height: 64px;
  border: 1px dashed rgba(148, 163, 184, 0.26);
  border-radius: 8px;
  color: #94a3b8;
  font-size: 13px;
  font-weight: 800;
}

:deep(.route-assist-endpoint) {
  width: 16px;
  height: 16px;
  display: grid;
  place-items: center;
  border: 2px solid rgba(255, 255, 255, 0.94);
  border-radius: 50%;
  background: #0f766e;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.16);
}

:deep(.route-assist-endpoint span) {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #ffffff;
}

:deep(.route-assist-endpoint.is-hot) {
  border-color: #f97316;
  background: rgba(255, 247, 237, 0.68);
  box-shadow: 0 0 0 4px rgba(249, 115, 22, 0.18), 0 10px 22px rgba(15, 23, 42, 0.2);
}

:deep(.route-assist-endpoint.is-hot span) {
  background: #f97316;
}

:deep(.route-snap-focus) {
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  border: 3px solid rgba(255, 255, 255, 0.96);
  border-radius: 50%;
  background: #f97316;
  box-shadow: 0 0 0 5px rgba(249, 115, 22, 0.16), 0 12px 24px rgba(15, 23, 42, 0.22);
}

:deep(.route-snap-focus span) {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ffffff;
}

:deep(.route-edit-handle) {
  display: grid;
  place-items: center;
  border-radius: 50%;
  pointer-events: auto;
  user-select: none;
}

:deep(.route-edit-handle span) {
  display: block;
  border-radius: 50%;
}

:deep(.route-edit-handle--endpoint) {
  width: 16px;
  height: 16px;
  border: 2px solid #ffffff;
  background: #065f46;
  box-shadow: 0 0 0 3px rgba(6, 95, 70, 0.2), 0 10px 18px rgba(15, 23, 42, 0.22);
  cursor: default;
}

:deep(.route-edit-handle--endpoint span) {
  width: 5px;
  height: 5px;
  background: #ffffff;
}

:deep(.route-edit-handle--middle) {
  width: 14px;
  height: 14px;
  border: 2px solid #ffffff;
  background: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.18), 0 8px 16px rgba(15, 23, 42, 0.2);
  cursor: move;
}

:deep(.route-edit-handle--middle span) {
  width: 4px;
  height: 4px;
  background: #ffffff;
}

:deep(.route-edit-handle--midpoint) {
  width: 10px;
  height: 10px;
  border: 1px dashed rgba(15, 23, 42, 0.38);
  background: rgba(255, 255, 255, 0.42);
  box-shadow: 0 0 0 5px rgba(255, 255, 255, 0.18);
  cursor: copy;
}

:deep(.route-edit-handle--midpoint span) {
  width: 2px;
  height: 2px;
  background: rgba(15, 23, 42, 0.58);
}

@media (max-width: 1180px) {
  .map-topbar {
    flex-wrap: wrap;
    gap: 12px;
  }

  .map-topbar-left {
    flex-basis: 100%;
    max-width: 100%;
  }

  .work-panel {
    width: 320px;
  }

  .tray-head {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .map-workbench {
    min-height: 780px;
  }

  .map-topbar {
    top: 12px;
    left: 12px;
    right: 12px;
    gap: 10px;
  }

  .map-topbar-left {
    flex-basis: 100%;
    max-width: 100%;
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .map-search,
  .map-layer-controls,
  .draft-actions {
    width: 100%;
    min-width: 0;
  }

  .map-layer-controls {
    justify-content: space-between;
  }

  .draft-actions {
    justify-content: space-between;
  }

  .tool-rail {
    top: 184px;
    left: 12px;
  }

  .work-panel {
    left: 94px;
    right: 12px;
    top: 184px;
    width: auto;
    max-height: 300px;
  }

  .map-status-chip {
    left: 12px;
    bottom: 156px;
  }

  .asset-tray {
    left: 0;
    right: 0;
    bottom: 0;
    padding: 12px 12px;
    border-radius: 12px 12px 0 0;
  }

  .tray-tabs {
    gap: 16px;
  }

  .tray-card {
    flex-basis: 198px;
    min-height: 56px;
  }
}
</style>

<style>
.asset-marker {
  background: transparent !important;
  border: 0 !important;
  pointer-events: auto !important;
  user-select: none !important;
  -webkit-user-select: none !important;
}

.search-focus-marker {
  position: relative;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 3px solid #ffffff;
  border-radius: 50%;
  background: #f59e0b;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.24);
}

.search-focus-marker span {
  position: absolute;
  left: 50%;
  bottom: 30px;
  transform: translateX(-50%);
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-radius: 999px;
  padding: 5px 10px;
  background: rgba(15, 23, 42, 0.9);
  color: #ffffff;
  font-size: 12px;
  font-weight: 800;
}

.asset-marker span {
  min-width: 128px;
  min-height: 40px;
  display: grid;
  grid-template-columns: 34px 1fr;
  column-gap: 7px;
  align-items: center;
  padding: 0;
  border: 0;
  color: #ffffff;
  font-size: 12px;
  font-weight: 900;
  line-height: 1.1;
  white-space: nowrap;
  text-shadow: 0 2px 5px rgba(0, 0, 0, 0.9);
  pointer-events: none !important;
}

.vertex-handle-icon {
  background: transparent !important;
  border: 0 !important;
  user-select: none !important;
  -webkit-user-select: none !important;
}

.vertex-handle-icon div {
  position: relative;
  transition: transform 0.15s ease;
}

.vertex-handle-icon div::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 32px;
  height: 32px;
  transform: translate(-50%, -50%);
  background: transparent;
  pointer-events: auto;
}

.zoom-compact .vertex-handle-icon div {
  transform: scale(1.4);
}

.zoom-minimal .vertex-handle-icon div {
  transform: scale(1.8);
}

.asset-marker i {
  width: 34px;
  height: 34px;
  display: flex !important;
  align-items: center;
  justify-content: center;
  border: 2px solid rgba(255, 255, 255, 0.92) !important;
  border-radius: 10px;
  background: #047857 !important;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.24);
  filter: none;
}

.asset-marker b {
  overflow: hidden;
  color: #ffffff;
  font-size: 13px;
  font-weight: 900;
  text-overflow: ellipsis;
}

.marker-svg {
  width: 22px !important;
  height: 22px !important;
  display: block;
  color: #ffffff;
}

.asset-marker--spot i {
  background: #047857 !important;
  color: #ffffff !important;
}

.asset-marker--facility i {
  background: #1d4ed8 !important;
  color: #ffffff !important;
}

.marker-svg--spot,
.marker-svg--facility {
  color: #ffffff !important;
}

.asset-marker.is-editing i {
  border-color: #fef3c7 !important;
  box-shadow: 0 0 0 4px rgba(245, 158, 11, 0.22), 0 12px 26px rgba(15, 23, 42, 0.28);
}

.asset-marker.is-editing {
  position: relative;
  z-index: 9999 !important;
  cursor: move !important;
}

.asset-marker.is-editing b {
  color: #fef3c7;
}

.route-network-line {
  cursor: pointer;
}

.workbench-select-popper.el-popper {
  overflow: hidden;
  background: #ffffff !important;
  border: 1px solid rgba(15, 23, 42, 0.1) !important;
  border-radius: 10px !important;
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.16) !important;
}

.workbench-select-popper.el-popper .el-popper__arrow::before {
  background: #ffffff !important;
  border-color: rgba(15, 23, 42, 0.1) !important;
}

.workbench-select-popper .el-select-dropdown {
  padding: 4px;
}

.workbench-select-popper .el-select-dropdown__list {
  padding: 0 !important;
}

.workbench-select-popper .el-select-dropdown__item {
  height: 30px !important;
  margin: 1px 0;
  border-radius: 7px;
  padding: 0 10px !important;
  color: #334155 !important;
  font-size: 12px !important;
  font-weight: 700;
  line-height: 30px !important;
}

.workbench-select-popper .el-select-dropdown__item.hover,
.workbench-select-popper .el-select-dropdown__item:hover {
  background: #f1f5f9 !important;
  color: #0f172a !important;
}

.workbench-select-popper .el-select-dropdown__item.selected {
  background: #e0f2fe !important;
  color: #0369a1 !important;
  font-weight: 900 !important;
}

.draft-select-popper {
  min-width: 420px !important;
}

.draft-select-popper .el-select-dropdown__item {
  height: 64px !important;
  margin: 2px 0 !important;
  padding: 0 !important;
  line-height: normal !important;
}

.draft-select-popper .el-select-dropdown__item > span {
  display: block;
  height: 100%;
}

.draft-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  height: 100%;
  padding: 8px 10px;
  box-sizing: border-box;
}

.draft-option__main {
  min-width: 0;
  flex: 1;
}

.draft-option__title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.draft-option__title > span {
  overflow: hidden;
  color: #334155;
  font-size: 13px;
  font-weight: 800;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.draft-option__meta {
  margin-top: 6px;
  overflow: hidden;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 600;
  line-height: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.draft-option__delete {
  width: 28px;
  height: 28px;
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
}

.draft-option__delete:hover {
  background: #fef2f2;
  color: #dc2626;
}

/* 隐藏地图缩小时的图标文字，保持界面清爽 */
.zoom-minimal .asset-marker span b {
  display: none !important;
}

.zoom-minimal .asset-marker span {
  min-width: 34px;
  grid-template-columns: 34px;
}

/* 预置路线样式 */
.preset-spots-container {
  margin-top: 15px;
  border-top: 1px solid var(--border);
  padding-top: 15px;
}
.preset-spots-container .section-title {
  font-size: 13px;
  font-weight: 800;
  color: #334155;
  margin-bottom: 10px;
}
.preset-spots-list {
  display: grid;
  gap: 8px;
  max-height: 240px;
  overflow-y: auto;
  padding-right: 4px;
}
.preset-spot-item {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}
.preset-spot-item .spot-order {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  background: #e2e8f0;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 900;
  color: #475569;
}
.preset-spot-item .spot-name {
  font-size: 12px;
  font-weight: 800;
  color: #0f172a;
}
.preset-spot-item .item-actions {
  display: flex;
  justify-content: flex-end;
}
.empty-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}
.empty-content {
  text-align: center;
  max-width: 400px;
  padding: 30px;
}
.empty-icon {
  font-size: 64px;
  color: #0f766e;
  margin-bottom: 16px;
}
.empty-content h2 {
  font-size: 20px;
  color: #0f172a;
  margin-bottom: 10px;
}
.empty-content p {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 20px;
  line-height: 1.6;
}
.draft-label {
  font-size: 12px;
  font-weight: 800;
  color: #475569;
  margin-right: 6px;
}
</style>
