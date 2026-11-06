/**
 * 浏览器录音转 16k 单声道 WAV
 * <p>
 * MediaRecorder 产出的是 webm/opus，阿里云 NLS 只认原始 PCM/WAV 流，
 * 所以必须经 Web Audio 解码后重采样再手工封 WAV 头
 */

const TARGET_SAMPLE_RATE = 16000

/**
 * 录音 Blob → 16k 单声道 WAV 的 ArrayBuffer
 *
 * @param {Blob} blob MediaRecorder 产出的音频
 * @returns {Promise<ArrayBuffer>}
 */
export const blobToWav16k = async (blob) => {
  const arrayBuffer = await blob.arrayBuffer()
  const AudioContextClass = window.AudioContext || window.webkitAudioContext
  if (!AudioContextClass) throw new Error('当前浏览器不支持音频解码')
  const context = new AudioContextClass()
  try {
    const audioBuffer = await context.decodeAudioData(arrayBuffer)
    const mono = mixToMono(audioBuffer)
    const resampled = resample(mono, audioBuffer.sampleRate, TARGET_SAMPLE_RATE)
    return encodeWav(resampled, TARGET_SAMPLE_RATE)
  } finally {
    context.close().catch(() => {})
  }
}

const mixToMono = (audioBuffer) => {
  const { numberOfChannels, length } = audioBuffer
  if (numberOfChannels === 1) return audioBuffer.getChannelData(0)
  const mono = new Float32Array(length)
  for (let channel = 0; channel < numberOfChannels; channel++) {
    const data = audioBuffer.getChannelData(channel)
    for (let i = 0; i < length; i++) mono[i] += data[i] / numberOfChannels
  }
  return mono
}

/**
 * 线性插值重采样。对语音识别场景足够，不值得为听感引入更重的算法
 */
const resample = (samples, fromRate, toRate) => {
  if (fromRate === toRate) return samples
  const ratio = fromRate / toRate
  const targetLength = Math.round(samples.length / ratio)
  const result = new Float32Array(targetLength)
  for (let i = 0; i < targetLength; i++) {
    const position = i * ratio
    const index = Math.floor(position)
    const fraction = position - index
    const current = samples[index] || 0
    const next = samples[index + 1] ?? current
    result[i] = current + (next - current) * fraction
  }
  return result
}

const encodeWav = (samples, sampleRate) => {
  const dataLength = samples.length * 2
  const buffer = new ArrayBuffer(44 + dataLength)
  const view = new DataView(buffer)
  const writeString = (offset, text) => {
    for (let i = 0; i < text.length; i++) view.setUint8(offset + i, text.charCodeAt(i))
  }
  writeString(0, 'RIFF')
  view.setUint32(4, 36 + dataLength, true)
  writeString(8, 'WAVE')
  writeString(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeString(36, 'data')
  view.setUint32(40, dataLength, true)
  for (let i = 0; i < samples.length; i++) {
    const clamped = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(44 + i * 2, clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff, true)
  }
  return buffer
}
