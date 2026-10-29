import axiosInstance from '../components/api/axiosInstance';

// 일정 API. 시각은 모두 오프셋 없는 KST 문자열이다
export const fetchOccurrences = (from, to) =>
  axiosInstance.get('/schedules', { params: { from, to } }).then((r) => r.data);

export const fetchUpcoming = (limit = 20) =>
  axiosInstance.get('/schedules/upcoming', { params: { limit } }).then((r) => r.data);

export const fetchSchedule = (id) => axiosInstance.get(`/schedules/${id}`).then((r) => r.data);

export const createSchedule = (body) => axiosInstance.post('/schedules', body).then((r) => r.data);

export const updateSchedule = (id, body) => axiosInstance.put(`/schedules/${id}`, body).then((r) => r.data);

export const deleteSchedule = (id) => axiosInstance.delete(`/schedules/${id}`);

// date: 'YYYY-MM-DD' (뺄 회차의 시작 날짜)
export const excludeOccurrence = (id, date) => axiosInstance.post(`/schedules/${id}/exclusions`, { date });

export const leaveSchedule = (id) => axiosInstance.delete(`/schedules/${id}/participants/me`);

export const fetchFeedUrl = () => axiosInstance.get('/calendar/feed-url').then((r) => r.data);

export const regenerateFeedUrl = () => axiosInstance.post('/calendar/feed-url/regenerate').then((r) => r.data);

// 서버 표준 오류 JSON의 message, 없으면 기본 문구
export const errorMessage = (err, fallback) => err?.response?.data?.message || fallback;

// 일정이 바뀌었다고 달력·사이드바에 알린다
export const notifySchedulesChanged = () => window.dispatchEvent(new Event('schedules:changed'));
