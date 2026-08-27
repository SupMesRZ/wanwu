import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(
  new URL('../src/utils/campusRole.js', import.meta.url),
);
const role = await import(
  `data:text/javascript;base64,${source.toString('base64')}`
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

const rejectedPreview = role.resolveCampusRoleState({
  roles: [{ name: 'student' }],
  previewRole: 'teacher',
});
assert.equal(rejectedPreview.previewRole, null);
assert.equal(rejectedPreview.effectiveRole, 'student');
assert.equal(role.canAccessCampusRoles(['teacher'], adminPreview), false);

console.log('campus role check passed');
