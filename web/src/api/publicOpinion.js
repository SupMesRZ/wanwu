import request from '@/utils/request';
import { USER_API } from '@/utils/requestConstants';

const PUBLIC_OPINION_API = `${USER_API}/public-opinion`;

export const downloadPublicOpinionTemplate = () => {
  return request({
    url: `${PUBLIC_OPINION_API}/import/template`,
    method: 'get',
    responseType: 'blob',
  });
};

export const importPublicOpinion = file => {
  const data = new FormData();
  data.append('file', file);
  return request({
    url: `${PUBLIC_OPINION_API}/import`,
    method: 'post',
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

export const getPublicOpinionImportTask = taskId => {
  return request({
    url: `${PUBLIC_OPINION_API}/import/${taskId}`,
    method: 'get',
  });
};

export const listPublicOpinionItems = data => {
  return request({
    url: `${PUBLIC_OPINION_API}/item/list`,
    method: 'post',
    data,
  });
};

export const getPublicOpinionItem = itemId => {
  return request({
    url: `${PUBLIC_OPINION_API}/item/${itemId}`,
    method: 'get',
  });
};
