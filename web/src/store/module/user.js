import {
  login,
  getPermission,
  getCommonInfo,
  login2FA2new,
  login2FA2exist,
  login2FA1,
} from '@/api/user';
import { fetchOrgs } from '@/api/permission/org';
import { jumpOAuth, redirectUserInfoPage, deepMerge } from '@/utils/util';
import { formatPerms } from '@/router/permission';
import { replaceRouter } from '@/router';
import {
  createCampusRoleState,
  resolveCampusRoleState,
} from '@/utils/campusRole';

const campusRoleFromPermission = (permission = {}, previewRole = null) =>
  resolveCampusRoleState({
    roles: permission.roles,
    previewRole,
    isAdmin: permission.isAdmin,
    isSystem: permission.isSystem,
  });

const processLogin = (res, commit, params) => {
  const orgs = res.data.orgs || [];
  const orgPermission = res.data.orgPermission || {};
  const orgId = orgPermission.org ? orgPermission.org.id : '';
  const { isAdmin, isSystem } = orgPermission || {};

  let permission = {};
  permission.orgPermission = formatPerms(orgPermission.permissions);
  permission.roles = orgPermission.roles || [];

  if (res.code === 0) {
    commit('setUserInfo', {
      uid: res.data.uid,
      userName: res.data.username,
      orgId,
      userCategory: res.data.userCategory,
    });
    commit('setOrgInfo', { orgs });
    commit('setToken', res.data.token);
    commit('setExpiresAt', res.data.expiresAt);
    commit('setPermission', {
      ...permission,
      isAdmin,
      isSystem,
      isUpdatePassword: res.data.isUpdatePassword,
    });
    commit(
      'setCampusRole',
      campusRoleFromPermission({ ...permission, isAdmin, isSystem }),
    );
    //配置导航用户logo和名称以及欢迎文字
    commit('setCommonInfo', { data: res.data.custom || {} });

    commit('setIs2FA', false);

    if (params.client_id) {
      // 重定向到OAuth页面
      jumpOAuth(params);
      return;
    }

    // 更新权限路由
    replaceRouter(permission.orgPermission);
    // 重定向到修改密码或者有权限的页面
    redirectUserInfoPage(res.data.isUpdatePassword, null, true);
  }
};

export const user = {
  namespaced: true,
  state: {
    userInfo: { uid: '', userName: '', orgId: '' },
    campusRole: createCampusRoleState(),
    orgInfo: { orgs: [] },
    token: '',
    expiresAt: 0,
    is2FA: false,
    permission: {},
    commonInfo: {
      data: {
        login: {
          logo: {},
          loginButtonColor: '#5983FF',
        },
        home: {},
        tab: {},
        loginEmail: {
          email: {
            status: false,
          },
        },
        register: {
          email: {
            status: false,
          },
        },
        resetPassword: {
          email: {
            status: false,
          },
        },
      },
    },
    lang: '',
    defaultIcons: {
      agentIcon: '',
      ragIcon: '',
    },
    userAvatar: '',
  },

  mutations: {
    setUserAvatar(state, userAvatar) {
      state.userAvatar = userAvatar;
    },
    setDefaultIcons(state, defaultIcons) {
      state.defaultIcons = { ...state.defaultIcons, ...defaultIcons };
    },
    setUserInfo(state, userInfo) {
      state.userInfo = { ...state.userInfo, ...userInfo };
    },
    setCampusRole(state, campusRole) {
      state.campusRole = campusRole;
    },
    setOrgInfo(state, orgInfo) {
      state.orgInfo = { ...state.orgInfo, ...orgInfo };
    },
    setToken(state, token) {
      state.token = token;
    },
    setExpiresAt(state, expiresAt) {
      state.expiresAt = expiresAt;
    },
    setIs2FA(state, is2FA) {
      state.is2FA = is2FA;
    },
    setLang(state, lang) {
      if (lang.code) {
        state.lang = lang.code;
        localStorage.setItem('locale', lang.code);
      }
    },
    setPermission(state, permission) {
      state.permission = permission;
    },
    LoginOut(state) {
      state.userInfo = {};
      state.campusRole = createCampusRoleState();
      state.token = '';
      state.permission = {};
      localStorage.setItem('access_cert', JSON.stringify(state));
      location.reload();
    },
    setCommonInfo(state, commonInfo) {
      state.commonInfo = deepMerge(
        structuredClone(state.commonInfo),
        commonInfo,
      );
    },
  },
  actions: {
    async LoginIn({ commit }, { loginInfo, params }) {
      const res = await login(loginInfo);
      processLogin(res, commit, params);
    },

    async LoginIn2FA1({ commit }, loginInfo) {
      const res = await login2FA1(loginInfo);
      if (res.code === 0) {
        commit('setToken', res.data.token);
        commit('setExpiresAt', 0);
        return res.data;
      }
    },

    async LoginIn2FA2({ commit }, { loginInfo, params }) {
      const res = await ('newPassword' in loginInfo &&
      'oldPassword' in loginInfo
        ? login2FA2new(loginInfo)
        : login2FA2exist(loginInfo));
      processLogin(res, commit, params);
    },

    // 获取权限
    async getPermissionInfo({ commit, state }) {
      return new Promise(async (resolve, reject) => {
        let res = await getPermission();
        const orgPermission = res.data.orgPermission || {};
        const { isAdmin, isSystem } = orgPermission || {};
        const permissions = {};
        permissions.orgPermission = formatPerms(orgPermission.permissions);
        permissions.roles = orgPermission.roles || [];

        const permission = {
          ...permissions,
          isAdmin,
          isSystem,
          isUpdatePassword: res.data.isUpdatePassword,
        };
        if (res.code === 0) {
          commit('setUserAvatar', res.data.avatar.path);
          commit('setPermission', permission);
          commit(
            'setCampusRole',
            campusRoleFromPermission(permission, state.campusRole?.previewRole),
          );
          if (res.data.language) commit('setLang', res.data.language);
          replaceRouter(permission.orgPermission || []);
          resolve(permission);
        } else {
          commit('setPermission', {});
          commit('setCampusRole', createCampusRoleState());
          replaceRouter([]);
          reject();
        }

        const orgRes = (await fetchOrgs()) || {};
        if (orgRes.code === 0)
          commit('setOrgInfo', { orgs: orgRes.data.select || [] });
      });
    },

    async LoginOut({ commit }) {
      commit('LoginOut');
    },

    setCampusPreviewRole({ commit, state }, previewRole) {
      const campusRole = campusRoleFromPermission(
        state.permission,
        previewRole,
      );
      commit('setCampusRole', campusRole);
      return campusRole;
    },

    async getOrgInfo({ commit }) {
      const res = (await fetchOrgs()) || {};
      if (res.code === 0) {
        commit('setOrgInfo', { orgs: res.data.select || [] });
      }
    },
    async getCommonInfo({ commit }) {
      const res = (await getCommonInfo()) || {};
      if (res.code === 0) {
        commit('setCommonInfo', { data: res.data || {} });
        // 存储默认图标信息
        const defaultIcons = {
          agentIcon: res.data.defaultIcon.agentIcon || '',
          ragIcon: res.data.defaultIcon.ragIcon || '',
        };
        commit('setDefaultIcons', defaultIcons);
      }
    },
  },
  getters: {
    commonInfo(state) {
      return state.commonInfo;
    },
    lang(state) {
      return state.lang;
    },
    userInfo(state) {
      return state.userInfo;
    },
    campusRole(state) {
      return state.campusRole || createCampusRoleState();
    },
    orgInfo(state) {
      return state.orgInfo;
    },
    token(state) {
      return state.token;
    },
    expiresAt(state) {
      return state.expiresAt;
    },
    permission(state) {
      return state.permission;
    },
    defaultIcons(state) {
      return state.defaultIcons;
    },
    userAvatar(state) {
      return state.userAvatar;
    },
  },
};
