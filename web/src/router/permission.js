import router from './index';
import { store } from '@/store/index';
import { fetchPermFirPath } from '@/utils/util';
import {
  PERMS as menuPerms,
  hasPermission,
  resolveRoutePermission,
} from './constants';
import { basePath } from '@/utils/config';
import { canAccessCampusRoles } from '@/utils/campusRole';

const white_list = [
  basePath + '/aibase',
  '/oauth',
  '/login',
  '/webChat',
  '/register',
  '/reset',
  '/templateSquare',
];
export const PERMS = menuPerms;

export const checkPerm = perm => {
  const permission = store.getters['user/permission'];
  return hasPermission(permission.orgPermission, perm);
};

export const formatPerms = perms => {
  return perms && perms.length ? perms.map(item => item.perm) : [];
};

router.beforeEach(async (to, from, next) => {
  let token = '';
  let access_cert =
    localStorage.getItem('access_cert') &&
    JSON.parse(localStorage.getItem('access_cert'));
  if (access_cert) {
    token = access_cert.user.token;
  }
  if (token && !access_cert.user.is2FA) {
    if (to.path === '/') {
      const { path } = fetchPermFirPath();
      next({ path });
    } else if (
      !checkPerm(resolveRoutePermission(to)) ||
      !canAccessCampusRoles(
        to.meta?.campusRoles,
        store.getters['user/campusRole'],
      )
    ) {
      next({ path: '/smartAssistant', replace: true });
    } else {
      next();
    }
  } else {
    if (
      white_list.some(item => {
        return to.path.indexOf(item) > -1;
      })
    ) {
      next();
    } else {
      window.location.href =
        window.location.origin + basePath + '/aibase/login';
    }
  }
});
