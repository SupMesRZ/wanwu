<template>
  <div class="route-map" :class="{ 'has-route': routePoints.length }">
    <svg viewBox="0 0 360 720" role="img" :aria-label="routePoints.length ? '推荐路线地图' : '当前景区地图'">
      <defs>
        <linearGradient :id="mapLandId" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#dcefe5" />
          <stop offset="1" stop-color="#f3ead6" />
        </linearGradient>
        <filter :id="routeGlowId" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="2" result="blur" />
          <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
      </defs>
      <rect width="360" height="720" :fill="`url(#${mapLandId})`" />
      <path d="M-10 94 C54 76 104 110 160 94 S274 54 378 72" class="water" />
      <g class="topology-routes">
        <polyline v-for="segment in topologySegments" :key="segment.id" :points="segment.polyline" class="road shadow" />
        <polyline v-for="segment in topologySegments" :key="`${segment.id}-line`" :points="segment.polyline" class="road" />
      </g>
      <g class="topology-spots">
        <circle v-for="spot in topologySpots" :key="spot.id" :cx="spot.x" :cy="spot.y" r="3.5" />
      </g>
      <g class="topology-facilities">
        <rect v-for="facility in topologyFacilities" :key="facility.id" :x="facility.x - 3" :y="facility.y - 3" width="6" height="6" rx="2" />
      </g>
      <g v-if="orderedRoutePoints.length" class="highlighted-segments">
        <polyline :points="orderedRoutePolyline" class="route-halo" />
        <polyline :points="orderedRoutePolyline" class="route-line" :filter="`url(#${routeGlowId})`" />
      </g>
      <g v-else-if="highlightedSegments.length" class="highlighted-segments">
        <polyline v-for="segment in highlightedSegments" :key="`${segment.id}-halo`" :points="segment.polyline" class="route-halo" />
        <polyline v-for="segment in highlightedSegments" :key="`${segment.id}-active`" :points="segment.polyline" class="route-line" :filter="`url(#${routeGlowId})`" />
      </g>
      <polyline v-else-if="routePoints.length" :points="routePolyline" class="route-halo" />
      <polyline v-if="routePoints.length && !orderedRoutePoints.length && !highlightedSegments.length" :points="routePolyline" class="route-line" :filter="`url(#${routeGlowId})`" />
      <g v-for="(point, index) in routePoints" :key="point.id" class="route-stop">
        <circle :cx="point.x" :cy="point.y" r="10" />
        <text :x="point.x" :y="point.y + 4" text-anchor="middle">{{ index + 1 }}</text>
        <text :x="point.x" :y="point.labelY" text-anchor="middle" class="stop-name">{{ point.name }}</text>
      </g>
      <g v-if="visitorPoint" class="visitor-point" :transform="`translate(${visitorPoint.x} ${visitorPoint.y})`">
        <circle r="13" class="pulse" />
        <circle r="7" />
        <circle r="2.5" class="core" />
      </g>
    </svg>
    <div v-if="locationStatus.visible" class="location-status" :class="locationStatus.tone">
      <span></span>{{ locationStatus.text }}
      <small v-if="visitorLocation?.accuracy">±{{ Math.round(visitorLocation.accuracy) }} 米</small>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  route: { type: Object, default: null },
  scenicMap: { type: Object, default: null },
  visitorLocation: { type: Object, default: null },
  spotLookup: { type: Object, default: () => new Map() },
  locationState: { type: String, default: 'idle' }
})

const instanceId = Math.random().toString(36).slice(2, 9)
const mapLandId = `map-land-${instanceId}`
const routeGlowId = `route-glow-${instanceId}`

const payload = computed(() => props.scenicMap?.payload || {})

const toGeoPoint = (source, fallback = {}) => {
  const point = source?.point || source?.labelPoint
  const lat = Array.isArray(point) && point.length >= 2 ? point[0] : source?.latitude
  const lng = Array.isArray(point) && point.length >= 2 ? point[1] : source?.longitude
  if (lat == null || lng == null) return null
  return { ...fallback, lat: Number(lat), lng: Number(lng) }
}

const mapSpotLookup = computed(() => new Map((payload.value.spots || []).map(spot => [String(spot.id), spot])))
const resolveEndpoint = endpoint => {
  if (!endpoint) return null
  const direct = toGeoPoint(endpoint)
  if (direct) return direct
  if (String(endpoint.type || '').toUpperCase() === 'SPOT') {
    return toGeoPoint(mapSpotLookup.value.get(String(endpoint.id)))
  }
  return null
}

const routeGeoPoints = computed(() => (props.route?.nodes || []).map(node => {
  if (node.latitude != null && node.longitude != null) {
    return { id: node.id, name: node.name, lat: Number(node.latitude), lng: Number(node.longitude) }
  }
  const spot = props.spotLookup.get(String(node.id))
  const source = spot?.labelPoint
  return Array.isArray(source) && source.length >= 2
    ? { id: node.id, name: node.name, lat: Number(source[0]), lng: Number(source[1]) }
    : null
}).filter(Boolean))
const orderedRouteGeoPoints = computed(() => (props.route?.pathPoints || [])
  .map(point => toGeoPoint(point, { segmentId: point.segmentId, sequence: point.sequence }))
  .filter(Boolean))

const topologyGeoSpots = computed(() => (payload.value.spots || [])
  .filter(spot => spot.status == null || spot.status === 1)
  .map(spot => toGeoPoint(spot, { id: spot.id, name: spot.name }))
  .filter(Boolean))
const topologyGeoFacilities = computed(() => (payload.value.facilities || [])
  .filter(facility => facility.status == null || facility.status === 1)
  .map(facility => toGeoPoint(facility, { id: facility.id, name: facility.name }))
  .filter(Boolean))
const topologyGeoSegments = computed(() => (payload.value.routeSegments || [])
  .filter(segment => segment.status == null || segment.status === 1)
  .map(segment => ({
    id: segment.id,
    points: [
      resolveEndpoint(segment.from),
      ...(segment.middlePoints || []).map(point => toGeoPoint({ point })),
      resolveEndpoint(segment.to)
    ].filter(Boolean)
  }))
  .filter(segment => segment.points.length >= 2))

const bounds = computed(() => {
  const all = [
    ...routeGeoPoints.value,
    ...topologyGeoSpots.value,
    ...topologyGeoFacilities.value,
    ...topologyGeoSegments.value.flatMap(segment => segment.points)
  ]
  if (!all.length) return null
  const lats = all.map(point => point.lat)
  const lngs = all.map(point => point.lng)
  return {
    minLat: Math.min(...lats), maxLat: Math.max(...lats),
    minLng: Math.min(...lngs), maxLng: Math.max(...lngs)
  }
})

const project = point => {
  if (!bounds.value) return { x: 180, y: 120 }
  const lngRange = Math.max(0.000001, bounds.value.maxLng - bounds.value.minLng)
  const latRange = Math.max(0.000001, bounds.value.maxLat - bounds.value.minLat)
  return {
    x: Math.round(38 + ((point.lng - bounds.value.minLng) / lngRange) * 284),
    y: Math.round(390 - ((point.lat - bounds.value.minLat) / latRange) * 260)
  }
}

const routePoints = computed(() => routeGeoPoints.value.map((point, index) => {
  const projected = project(point)
  return { ...point, ...projected, labelY: projected.y + (index % 2 ? 28 : -18) }
}))
const routePolyline = computed(() => routePoints.value.map(point => `${point.x},${point.y}`).join(' '))
const orderedRoutePoints = computed(() => orderedRouteGeoPoints.value.map(point => ({ ...point, ...project(point) })))
const orderedRoutePolyline = computed(() => orderedRoutePoints.value.map(point => `${point.x},${point.y}`).join(' '))
const topologySpots = computed(() => topologyGeoSpots.value.map(point => ({ ...point, ...project(point) })))
const topologyFacilities = computed(() => topologyGeoFacilities.value.map(point => ({ ...point, ...project(point) })))
const topologySegments = computed(() => topologyGeoSegments.value.map(segment => ({
  id: segment.id,
  polyline: segment.points.map(point => {
    const projected = project(point)
    return `${projected.x},${projected.y}`
  }).join(' ')
})))
const highlightedSegmentIds = computed(() => new Set((props.route?.routeSegmentIds || []).map(String)))
const highlightedSegments = computed(() => topologySegments.value
  .filter(segment => highlightedSegmentIds.value.has(String(segment.id))))
const visitorPoint = computed(() => {
  if (!props.visitorLocation || !bounds.value) return null
  const latitudePadding = Math.max((bounds.value.maxLat - bounds.value.minLat) * 0.08, 0.00001)
  const longitudePadding = Math.max((bounds.value.maxLng - bounds.value.minLng) * 0.08, 0.00001)
  const insideMap = props.visitorLocation.lat >= bounds.value.minLat - latitudePadding
    && props.visitorLocation.lat <= bounds.value.maxLat + latitudePadding
    && props.visitorLocation.lng >= bounds.value.minLng - longitudePadding
    && props.visitorLocation.lng <= bounds.value.maxLng + longitudePadding
  return insideMap ? project(props.visitorLocation) : null
})
const locationStatus = computed(() => ({
  idle: { visible: false, text: '', tone: 'neutral' },
  watching: { visible: true, text: '正在获取位置', tone: 'pending' },
  located: { visible: true, text: '位置已更新', tone: 'success' },
  inside: { visible: true, text: '已在景区内', tone: 'success' },
  outside: { visible: true, text: '当前不在景区范围', tone: 'neutral' },
  denied: { visible: true, text: '定位权限未开启', tone: 'warning' },
  unsupported: { visible: true, text: '浏览器不支持定位', tone: 'warning' },
  error: { visible: true, text: '定位失败，请重试', tone: 'warning' }
}[props.locationState] || { visible: false, text: '', tone: 'neutral' }))
</script>

<style scoped>
.route-map { position: absolute; inset: 0; overflow: hidden; background: #dcefe5; }
.route-map svg { width: 100%; height: 100%; display: block; }
.water { fill: none; stroke: rgba(130, 197, 201, .58); stroke-width: 22; stroke-linecap: round; }
.road { fill: none; stroke: rgba(255,255,255,.9); stroke-width: 5; stroke-linecap: round; stroke-linejoin: round; }
.road.shadow { stroke: rgba(72, 96, 84, .15); stroke-width: 8; }
.has-route .topology-routes .road { opacity: .48; }
.topology-spots circle { fill: rgba(34, 111, 91, .72); stroke: rgba(255,255,255,.9); stroke-width: 1.5; }
.topology-facilities rect { fill: #d98746; stroke: rgba(255,255,255,.9); stroke-width: 1.5; }
.route-halo, .route-line { fill: none; stroke-linecap: round; stroke-linejoin: round; }
.route-halo { stroke: rgba(255,255,255,.9); stroke-width: 10; }
.route-line { stroke: #167d69; stroke-width: 5; }
.route-stop circle { fill: #fff; stroke: #167d69; stroke-width: 3; }
.route-stop text { fill: #167d69; font-size: 10px; font-weight: 900; }
.route-stop .stop-name { fill: #273a33; font-size: 9px; paint-order: stroke; stroke: rgba(255,255,255,.92); stroke-width: 3px; }
.visitor-point .pulse { fill: rgba(245, 130, 49, .2); animation: pulse 1.8s ease-out infinite; }
.visitor-point circle { fill: #f58231; stroke: #fff; stroke-width: 2; }
.visitor-point .core { fill: #fff; stroke: 0; }
.location-status { position:absolute; top:116px; left:50%; transform:translateX(-50%); z-index:3; display:flex; align-items:center; gap:6px; max-width:calc(100% - 32px); padding:7px 11px; border-radius:999px; background:rgba(248,252,250,.88); box-shadow:0 5px 18px rgba(41,77,66,.12); color:#36564c; font-size:11px; font-weight:750; white-space:nowrap; backdrop-filter:blur(12px) }
.location-status > span { width:7px; height:7px; border-radius:50%; background:#8ba098 }
.location-status small { color:#6f837c; font-size:10px; font-weight:600 }
.location-status.success > span { background:#16836d; box-shadow:0 0 0 4px rgba(22,131,109,.12) }
.location-status.pending > span { background:#e49245; animation:statusPulse 1.4s ease-in-out infinite }
.location-status.warning > span { background:#d36a55 }
@keyframes pulse { from { r: 8; opacity: .8; } to { r: 20; opacity: 0; } }
@keyframes statusPulse { 50% { opacity:.35 } }
</style>
