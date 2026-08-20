import { useQiankun } from './qiankunUtil';
import Vue from 'vue';
import App from './App.vue';
import router from './router';
import { store } from './store';
import { i18n } from './lang';
import './router/permission';
import './assets/icons';
import '@/assets/icons/iconfont.js';
import 'core-js/stable';
import 'regenerator-runtime/runtime';

// gxd-file-preview 文件预览插件
import vueFilePreview from 'gxd-file-preview';
Vue.use(vueFilePreview, {
  pdf: 'https://cdn.jsdelivr.net/npm/pdfjs-dist@2.0.288/build/pdf.min.js',
  worker:
    'https://cdn.jsdelivr.net/npm/pdfjs-dist@2.0.288/build/pdf.worker.min.js',
});

import ElementUi from 'element-ui';
import moment from 'moment';
import 'element-ui/lib/theme-chalk/index.css';
import '@/style/index.scss';
import { config, basePath } from './utils/config';
import { guid, copy, replaceTitle } from '@/utils/util';

Vue.use(ElementUi, {
  i18n: (key, value) => i18n.t(key, value), // 根据选的语言切换 Element-ui 的语言
});

Vue.prototype.$config = config || {};
Vue.prototype.$basePath = basePath;
Vue.prototype.$guid = guid;
Vue.prototype.$copy = copy;

Vue.config.productionTip = false;

replaceTitle(i18n.t('header.title'));

// 定义时间格式全局过滤器
Vue.filter('dateFormat', function (daraStr, pattern = 'YYYY-MM-DD HH:mm:ss') {
  return moment(daraStr).format(pattern);
});

const vueApp = new Vue({
  router,
  store,
  i18n,
  render: function (h) {
    return h(App);
  },
}).$mount('#app');

/*vueApp.$nextTick(() => {
    useQiankun()
})*/
