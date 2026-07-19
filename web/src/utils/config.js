export const basePath =
  window.APP_BASE_PATH || process.env.VUE_APP_BASE_PATH || '';
export const hexiaozhiRagAppId =
  typeof window.HEXIAOZHI_RAG_APP_ID === 'string'
    ? window.HEXIAOZHI_RAG_APP_ID
    : '';
export const config = {
  backgroundColor: '#F7F8FA',
  commonTextReg: /^(?!_)[a-zA-Z0-9-_.\u4e00-\u9fa5]+$/,
};
