import AMapLoader from '@amap/amap-jsapi-loader'

const DEFAULT_PLUGINS = [
  'AMap.Scale',
  'AMap.ToolBar',
  'AMap.MapType',
  'AMap.ControlBar',
  'AMap.Geolocation',
  'AMap.PlaceSearch',
  'AMap.AutoComplete',
  'AMap.PolygonEditor',
  'AMap.PolylineEditor'
]

let amapPromise = null

const readGaodeConfig = () => {
  const key = __GAODE_API_KEY__ || import.meta.env.VITE_GAODE_API_KEY || import.meta.env.VITE_AMAP_WEB_SERVICE_KEY
  const securityJsCode = __GAODE_SECRET__ || import.meta.env.VITE_GAODE_SECRET || ''
  return { key, securityJsCode }
}

export const loadAmap = async (plugins = DEFAULT_PLUGINS) => {
  if (amapPromise) return amapPromise
  const { key, securityJsCode } = readGaodeConfig()
  if (!key) {
    throw new Error('缺少 GAODE_API_KEY 或 VITE_GAODE_API_KEY')
  }
  window._AMapSecurityConfig = securityJsCode ? { securityJsCode } : {}
  amapPromise = AMapLoader.load({
    key,
    version: '2.0',
    plugins
  })
  return amapPromise
}

export const gpsToAmap = async (latitude, longitude) => {
  const AMap = await loadAmap()
  return new Promise((resolve, reject) => {
    AMap.convertFrom([Number(longitude), Number(latitude)], 'gps', (status, result) => {
      const point = result?.locations?.[0]
      if (status !== 'complete' || result?.info !== 'ok' || !point) {
        reject(new Error('GPS 坐标转换失败'))
        return
      }
      resolve({
        lat: Number(point.lat ?? point.getLat?.()),
        lng: Number(point.lng ?? point.getLng?.())
      })
    })
  })
}

export const latLngToLngLat = (point) => {
  if (!Array.isArray(point) || point.length < 2) return null
  const lat = Number(point[0])
  const lng = Number(point[1])
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null
  return [lng, lat]
}

export const isValidLatLng = (point) => Boolean(latLngToLngLat(point))

export const lngLatToLatLng = (point) => {
  if (!point) return null
  if (Array.isArray(point)) {
    const lng = Number(point[0])
    const lat = Number(point[1])
    return Number.isFinite(lat) && Number.isFinite(lng) ? [lat, lng] : null
  }
  const lng = Number(point.lng ?? point.getLng?.())
  const lat = Number(point.lat ?? point.getLat?.())
  return Number.isFinite(lat) && Number.isFinite(lng) ? [lat, lng] : null
}

export const latLngsToLngLats = (points = []) => points.map(latLngToLngLat).filter(Boolean)

export const lngLatsToLatLngs = (points = []) => points.map(lngLatToLatLng).filter(Boolean)

export const makeLngLatBounds = (AMap, points = []) => {
  const lngLats = latLngsToLngLats(points)
  if (!lngLats.length) return null
  const lngValues = lngLats.map(point => point[0])
  const latValues = lngLats.map(point => point[1])
  const southWest = new AMap.LngLat(Math.min(...lngValues), Math.min(...latValues))
  const northEast = new AMap.LngLat(Math.max(...lngValues), Math.max(...latValues))
  return new AMap.Bounds(southWest, northEast)
}

export const distanceMeters = (AMap, from, to) => {
  const start = latLngToLngLat(from)
  const end = latLngToLngLat(to)
  if (!start || !end) return 0
  const [lng1, lat1] = start
  const [lng2, lat2] = end
  const rad = Math.PI / 180
  const earthRadius = 6378137
  const dLat = (lat2 - lat1) * rad
  const dLng = (lng2 - lng1) * rad
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2
  return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

export const createHtmlMarkerContent = (className, html) => {
  const el = document.createElement('div')
  el.className = className
  el.innerHTML = html
  return el
}
