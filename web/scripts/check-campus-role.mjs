import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(
  new URL('../src/utils/campusRole.js', import.meta.url),
);
const role = await import(
  `data:text/javascript;base64,${source.toString('base64')}`
);
const permissionSource = await readFile(
  new URL('../src/router/constants.js', import.meta.url),
);
const permission = await import(
  `data:text/javascript;base64,${permissionSource.toString('base64')}`
);
globalThis.__campusTestPerms = permission.PERMS;
const menuSource = await readFile(
  new URL('../src/views/layout/menu.js', import.meta.url),
);
const menu = await import(
  `data:text/javascript;base64,${Buffer.from(
    menuSource
      .toString()
      .replace(
        "import { PERMS } from '@/router/permission';",
        'const PERMS = globalThis.__campusTestPerms;',
      )
      .replace(
        "import { i18n } from '@/lang';",
        'const i18n = { t: key => key };',
      )
      .replace(
        "import { basePath, vegaOrigin } from '@/utils/config';",
        "const basePath = ''; const vegaOrigin = '';",
      ),
  ).toString('base64')}`
);
const routerSource = await readFile(
  new URL('../src/router/index.js', import.meta.url),
  'utf8',
);

const student = role.resolveCampusRoleState({
  roles: [{ name: 'student' }, { name: 'platform_operator' }],
});
assert.equal(student.actualRole, 'student');
assert.equal(student.effectiveRole, 'student');
assert.equal(student.roleStatus, 'resolved');

const unconfigured = role.resolveCampusRoleState({
  roles: [{ name: '学生' }],
});
assert.equal(unconfigured.actualRole, null);
assert.equal(unconfigured.roleStatus, 'unconfigured');

const conflict = role.resolveCampusRoleState({
  roles: [{ name: 'teacher' }, { name: 'student' }],
});
assert.equal(conflict.actualRole, null);
assert.equal(conflict.roleStatus, 'conflict');
assert.equal(
  role.resolveCampusRoleState({
    roles: [{ name: 'student' }, { name: 'teacher' }],
  }).roleStatus,
  'conflict',
);

const adminPreview = role.resolveCampusRoleState({
  roles: [],
  previewRole: 'teacher',
  isAdmin: true,
});
assert.equal(adminPreview.actualRole, null);
assert.equal(adminPreview.previewRole, 'teacher');
assert.equal(adminPreview.effectiveRole, 'teacher');

const adminDefaultPreview = role.resolveCampusRoleState({ isAdmin: true });
assert.equal(adminDefaultPreview.previewRole, 'student');
assert.equal(adminDefaultPreview.effectiveRole, 'student');

const rejectedPreview = role.resolveCampusRoleState({
  roles: [{ name: 'student' }],
  previewRole: 'teacher',
});
assert.equal(rejectedPreview.previewRole, null);
assert.equal(rejectedPreview.effectiveRole, 'student');
assert.equal(role.canAccessCampusRoles(['teacher'], adminPreview), true);

const adminStudentPreview = role.resolveCampusRoleState({
  roles: [],
  previewRole: 'student',
  isAdmin: true,
});
assert.equal(adminStudentPreview.actualRole, null);
assert.equal(adminStudentPreview.effectiveRole, 'student');
assert.equal(role.canAccessCampusRoles(['student'], adminStudentPreview), true);

const { AGENT, WORKFLOW } = permission.PERMS;
const menuGroups = menu.menuList;
delete globalThis.__campusTestPerms;

const canSeeMenu = (campusRole, permissions, path) => {
  const group = menuGroups.find(item =>
    item.children?.some(child => child.path === path),
  );
  const item = group?.children.find(child => child.path === path);
  return Boolean(
    group &&
    item &&
    permission.hasPermission(permissions, group.perm) &&
    role.canAccessCampusRoles(group.roles, campusRole) &&
    permission.hasPermission(permissions, item.perm) &&
    role.canAccessCampusRoles(item.roles, campusRole),
  );
};

const roleState = actualRole =>
  role.resolveCampusRoleState({ roles: [{ name: actualRole }] });
const accessCases = [
  { role: 'student', permissions: [], agent: false, workflow: false },
  {
    role: 'student',
    permissions: [AGENT],
    agent: true,
    workflow: false,
  },
  {
    role: 'student',
    permissions: [WORKFLOW],
    agent: false,
    workflow: true,
  },
  {
    role: 'student',
    permissions: [AGENT, WORKFLOW],
    agent: true,
    workflow: true,
  },
  { role: 'teacher', permissions: [AGENT], agent: true, workflow: false },
  {
    role: 'academic_admin',
    permissions: [],
    agent: false,
    workflow: false,
  },
  { role: 'admin', permissions: [], agent: false, workflow: false },
  { role: 'system', permissions: [WORKFLOW], agent: false, workflow: true },
];

for (const access of accessCases) {
  const state = roleState(access.role);
  assert.equal(
    canSeeMenu(state, access.permissions, '/appSpace/agent'),
    access.agent,
    `${access.role} agent menu access`,
  );
  assert.equal(
    canSeeMenu(state, access.permissions, '/appSpace/workflow'),
    access.workflow,
    `${access.role} workflow menu access`,
  );
}

assert.equal(
  canSeeMenu(roleState('student'), [], '/campus/student/courses'),
  true,
);
assert.equal(
  canSeeMenu(roleState('teacher'), [], '/campus/student/courses'),
  false,
);
assert.equal(
  canSeeMenu(
    roleState('student'),
    [permission.PERMS.MCP_SERVICE],
    '/mcpService',
  ),
  true,
);

const appSpaceRoute = type => ({
  params: { type },
  matched: [{ path: '/portal' }, { path: '/appSpace/:type' }],
  meta: { perm: [permission.PERMS.RAG, AGENT, WORKFLOW] },
});
assert.equal(permission.resolveRoutePermission(appSpaceRoute('agent')), AGENT);
assert.equal(
  permission.resolveRoutePermission(appSpaceRoute('workflow')),
  WORKFLOW,
);
assert.deepEqual(
  permission.resolveRoutePermission(appSpaceRoute('unknown')),
  [],
);

for (const access of accessCases) {
  assert.equal(
    permission.hasPermission(
      access.permissions,
      permission.resolveRoutePermission(appSpaceRoute('agent')),
    ),
    access.agent,
    `${access.role} agent route access`,
  );
  assert.equal(
    permission.hasPermission(
      access.permissions,
      permission.resolveRoutePermission(appSpaceRoute('workflow')),
    ),
    access.workflow,
    `${access.role} workflow route access`,
  );
}

const routeBlock = path => {
  const escaped = path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const block = routerSource.match(
    new RegExp(`\\{\\n\\s+path: '${escaped}',[\\s\\S]*?\\n      \\},`),
  )?.[0];
  assert.ok(block, `route ${path} must exist`);
  return block;
};
const assertRoutePermission = (path, perm) => {
  const block = routeBlock(path);
  assert.match(
    block,
    new RegExp(`meta: \\{ perm: \\[PERMS\\.${perm}\\](?:,| \\})`),
  );
  assert.doesNotMatch(block, /campusRoles/);
};

assertRoutePermission('/agentCenter', 'AGENT');
assertRoutePermission('/agent/test', 'AGENT');
assertRoutePermission('/agent/publishSet', 'AGENT');
assertRoutePermission('/workflow', 'WORKFLOW');
assertRoutePermission('/workflow/publishSet', 'WORKFLOW');
assertRoutePermission('/mcpService', 'MCP_SERVICE');
assertRoutePermission('/modelAccess', 'MODEL_MANAGE');
assert.match(
  routeBlock('/appSpace/:type'),
  /meta: \{ perm: \[PERMS\.RAG, PERMS\.AGENT, PERMS\.WORKFLOW\] \}/,
);

console.log('campus role and IAM permission checks passed');
