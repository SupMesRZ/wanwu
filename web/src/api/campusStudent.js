import service from '@/utils/request';
import { SERVICE_API } from '@/utils/requestConstants';

const BASE_URL = `${SERVICE_API}/campus/student`;

const get = (path, params) =>
  service({ url: `${BASE_URL}${path}`, method: 'get', params });

export const getStudentSummary = () => get('/summary');
export const getStudentCourses = () => get('/courses');
export const getStudentTodayCourses = () => get('/courses/today');
export const getStudentWeekCourses = () => get('/courses/week');
export const getStudentExams = () => get('/exams');
export const getStudentScores = params => get('/scores', params);
export const getStudentScoreOverview = params =>
  get('/scores/overview', params);
export const getStudentLearningAnalysis = params =>
  get('/learning-analysis', params);
export const getStudentLeaveRecords = () => get('/leave-records');

export const getStudentAssistantBinding = () => get('/assistant/binding');
export const getStudentAssistant = () => get('/assistant');
export const createStudentAssistantConversation = message =>
  service({
    url: `${BASE_URL}/assistant/conversation`,
    method: 'post',
    data: { message },
  });

export const STUDENT_ASSISTANT_CHAT_URL = `${BASE_URL}/assistant/chat`;
