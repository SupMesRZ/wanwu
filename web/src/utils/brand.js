export const PLATFORM_NAME = '河小智——河北大学智能体服务平台';

const LEGACY_BRAND_PATTERN = /万悟|元景|wanwu|hermes/i;

export const normalizeBrandText = (value, fallback) => {
  if (typeof value !== 'string' || !value.trim()) return fallback;
  return LEGACY_BRAND_PATTERN.test(value) ? fallback : value;
};
