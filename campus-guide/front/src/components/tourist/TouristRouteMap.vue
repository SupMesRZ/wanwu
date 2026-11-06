<template>
  <div class="route-map" :class="{ 'has-route': routePath.length }">
    <div ref="mapEl" class="amap-container" role="img" :aria-label="routePath.length ? '校园导航路线地图' : '河北大学七一路校区地图'"></div>
    <div v-if="mapError" class="map-message">{{ mapError }}</div>
    <div v-else-if="!mapReady" class="map-message">校园地图加载中…</div>
    <div v-if="locationStatus.visible" class="location-status" :class="locationStatus.tone">
      <span></span>{{ locationStatus.text }}
      <small v-if="visitorLocation?.accuracy">±{{ Math.round(visitorLocation.accuracy) }} 米</small>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { latLngToLngLat, loadAmap, makeLngLatBounds } from '@/utils/amap'

const CAMPUS_CENTER = [38.881949, 115.566197]

const props = defineProps({
  route: { type: Object, default: null },
  scenicMap: { type: Object, default: null },
  visitorLocation: { type: Object, default: null },
  spotLookup: { type: Object, default: () => new Map() },
  locationState: { type: String, default: 'idle' }
})

const mapEl = ref(null)
const mapReady = ref(false)
const mapError = ref('')
const payload = computed(() => props.scenicMap?.payload || {})
let AMapApi = null
let map = null

const toGeoPoint = (source, fallback = {}) => {
  const point = source?.point || source?.labelPoint
  const lat = Array.isArray(point) && point.length >= 2 ? point[0] : source?.latitude
  const lng = Array.isArray(point) && point.length >= 2 ? point[1] : source?.longitude
  if (!Number.isFinite(Number(lat)) || !Number.isFinite(Number(lng))) return null
  return { ...fallback, lat: Number(lat), lng: Number(lng) }
}

const mapSpotLookup = computed(() => new Map((payload.value.spots || []).map(spot => [String(spot.id), spot])))
const resolveEndpoint = endpoint => {
  const direct = toGeoPoint(endpoint)
  if (direct) return direct
  return String(endpoint?.type || '').toUpperCase() === 'SPOT'
    ? toGeoPoint(mapSpotLookup.value.get(String(endpoint.id)))
    : null
}

const spots = computed(() => (payload.value.spots || [])
  .filter(spot => spot.status == null || spot.status === 1)
  .map(spot => toGeoPoint(spot, { id: spot.id, name: spot.name }))
  .filter(Boolean))
const facilities = computed(() => (payload.value.facilities || [])
  .filter(item => item.status == null || item.status === 1)
  .map(item => toGeoPoint(item, { id: item.id, name: item.name }))
  .filter(Boolean))
const segments = computed(() => (payload.value.routeSegments || [])
  .filter(segment => segment.status == null || segment.status === 1)
  .map(segment => ({
    id: String(segment.id),
    path: [
      resolveEndpoint(segment.from),
      ...(segment.middlePoints || []).map(point => toGeoPoint({ point })),
      resolveEndpoint(segment.to)
    ].filter(Boolean)
  }))
  .filter(segment => segment.path.length >= 2))
const routePath = computed(() => {
  const ordered = (props.route?.pathPoints || []).map(toGeoPoint).filter(Boolean)
  if (ordered.length >= 2) return ordered
  return (props.route?.nodes || []).map(node => {
    if (node.latitude != null && node.longitude != null) return toGeoPoint(node)
    return toGeoPoint(props.spotLookup.get(String(node.id)))
  }).filter(Boolean)
})
const activeSegmentIds = computed(() => new Set((props.route?.routeSegmentIds || []).map(String)))

const markerContent = (text, kind) => {
  const element = document.createElement('div')
  element.className = `campus-marker ${kind}`
  element.textContent = String(text || '·').slice(0, 1)
  return element
}

const renderMap = (fit = false) => {
  if (!map || !AMapApi) return
  map.clearMap()
  const overlays = []
  for (const segment of segments.value) {
    const active = activeSegmentIds.value.has(segment.id)
    overlays.push(new AMapApi.Polyline({
      path: segment.path.map(point => [point.lng, point.lat]),
      strokeColor: active ? '#167d69' : '#91a8a0',
      strokeWeight: active ? 7 : 4,
      strokeOpacity: active ? 1 : 0.72,
      lineJoin: 'round',
      lineCap: 'round',
      zIndex: active ? 30 : 20
    }))
  }
  if (routePath.value.length >= 2) {
    overlays.push(new AMapApi.Polyline({
      path: routePath.value.map(point => [point.lng, point.lat]),
      strokeColor: '#167d69',
      strokeWeight: 7,
      strokeOpacity: 1,
      showDir: true,
      lineJoin: 'round',
      lineCap: 'round',
      zIndex: 40
    }))
  }
  spots.value.forEach(spot => overlays.push(new AMapApi.Marker({
    position: [spot.lng, spot.lat],
    title: spot.name,
    content: markerContent(spot.name, 'spot'),
    anchor: 'center',
    zIndex: 50
  })))
  facilities.value.forEach(item => overlays.push(new AMapApi.Marker({
    position: [item.lng, item.lat],
    title: item.name,
    content: markerContent(item.name, 'facility'),
    anchor: 'center',
    zIndex: 50
  })))
  if (props.visitorLocation) {
    overlays.push(new AMapApi.Marker({
      position: [Number(props.visitorLocation.lng), Number(props.visitorLocation.lat)],
      title: '我的位置',
      content: markerContent('我', 'visitor'),
      anchor: 'center',
      zIndex: 80
    }))
  }
  if (overlays.length) map.add(overlays)
  if (!fit) return
  const points = [...spots.value, ...facilities.value, ...segments.value.flatMap(segment => segment.path), ...routePath.value]
    .map(point => [point.lat, point.lng])
  const bounds = makeLngLatBounds(AMapApi, points)
  if (bounds && points.length > 1) map.setBounds(bounds, false, [100, 40, 150, 40])
}

const initMap = async () => {
  try {
    AMapApi = await loadAmap()
    const area = props.scenicMap?.currentArea || {}
    const center = payload.value.mapCenter || (
      area.centerLatitude != null && area.centerLongitude != null
        ? [area.centerLatitude, area.centerLongitude]
        : CAMPUS_CENTER
    )
    map = new AMapApi.Map(mapEl.value, {
      center: latLngToLngLat(center),
      zoom: Number(payload.value.defaultZoom || area.defaultZoom || 17),
      mapStyle: 'amap://styles/fresh',
      viewMode: '2D'
    })
    map.addControl(new AMapApi.Scale({ position: 'RB' }))
    mapReady.value = true
    await nextTick()
    renderMap(true)
  } catch (error) {
    mapError.value = error?.message || '高德地图加载失败'
  }
}

const locationStatus = computed(() => ({
  idle: { visible: false, text: '', tone: 'neutral' },
  watching: { visible: true, text: '正在获取位置', tone: 'pending' },
  located: { visible: true, text: '位置已更新', tone: 'success' },
  inside: { visible: true, text: '已在校园内', tone: 'success' },
  outside: { visible: true, text: '当前不在校园地点附近', tone: 'neutral' },
  denied: { visible: true, text: '定位权限未开启', tone: 'warning' },
  unsupported: { visible: true, text: '浏览器不支持定位', tone: 'warning' },
  error: { visible: true, text: '定位失败，请重试', tone: 'warning' }
}[props.locationState] || { visible: false, text: '', tone: 'neutral' }))

watch(() => [props.scenicMap, props.route], () => renderMap(true), { deep: true })
watch(() => props.visitorLocation, () => renderMap(false), { deep: true })
onMounted(initMap)
onBeforeUnmount(() => {
  map?.destroy()
  map = null
})
</script>

<style scoped>
.route-map { position:absolute; inset:0; overflow:hidden; background:#dcebe3 }
.amap-container { width:100%; height:100% }
.map-message { position:absolute; inset:0; display:grid; place-items:center; padding:24px; background:#e7f0eb; color:#496259; font-size:13px; text-align:center }
.route-map :deep(.campus-marker) { width:25px; height:25px; display:grid; place-items:center; border:2px solid #fff; border-radius:50%; background:#167d69; box-shadow:0 3px 10px rgba(22,75,61,.28); color:#fff; font-size:11px; font-weight:900 }
.route-map :deep(.campus-marker.facility) { border-radius:8px; background:#d98746 }
.route-map :deep(.campus-marker.visitor) { width:30px; height:30px; background:#2878ff; box-shadow:0 0 0 8px rgba(40,120,255,.2),0 3px 10px rgba(22,75,61,.3) }
.location-status { position:absolute; top:116px; left:50%; z-index:3; display:flex; align-items:center; gap:6px; max-width:calc(100% - 32px); padding:7px 11px; border-radius:999px; background:rgba(248,252,250,.9); box-shadow:0 5px 18px rgba(41,77,66,.12); color:#36564c; font-size:11px; font-weight:750; white-space:nowrap; backdrop-filter:blur(12px); transform:translateX(-50%) }
.location-status > span { width:7px; height:7px; border-radius:50%; background:#8ba098 }
.location-status small { color:#6f837c; font-size:10px; font-weight:600 }
.location-status.success > span { background:#16836d; box-shadow:0 0 0 4px rgba(22,131,109,.12) }
.location-status.pending > span { background:#e49245; animation:statusPulse 1.4s ease-in-out infinite }
.location-status.warning > span { background:#d36a55 }
@keyframes statusPulse { 50% { opacity:.35 } }
</style>
