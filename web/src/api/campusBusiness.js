import service from '@/utils/request';
import { SERVICE_API } from '@/utils/requestConstants';

const rolePath = role => (role === 'academic_admin' ? 'academic' : role);
const base = role => `${SERVICE_API}/campus/${rolePath(role)}/assistant`;

export const getCampusRoleAssistantBinding = role =>
  service({ url: `${base(role)}/binding`, method: 'get' });
export const getCampusRoleAssistant = role =>
  service({ url: base(role), method: 'get' });
export const createCampusRoleAssistantConversation = (role, message) =>
  service({
    url: `${base(role)}/conversation`,
    method: 'post',
    data: { message },
  });
export const campusRoleAssistantChatUrl = role => `${base(role)}/chat`;
