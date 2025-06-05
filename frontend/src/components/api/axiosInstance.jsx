// axiosInstance.js
import axios from 'axios';

// Axios 인스턴스 생성
const instance = axios.create({
  baseURL: 'http://localhost:8080',
  withCredentials: true,
});

// ✅ 재발급 Promise (동시 요청 막기용)
let isRefreshing = false;
let refreshSubscribers = [];

const onRefreshed = () => {
  refreshSubscribers.forEach(callback => callback());
  refreshSubscribers = [];
};

const addRefreshSubscriber = (callback) => {
  refreshSubscribers.push(callback);
};

// ✅ 응답 인터셉터
// ✅ 재요청을 큐에 담았다가 refresh가 끝나고 다시 실행
instance.interceptors.response.use(
  (response) => response,
  async (error) => {
    const { config, response } = error;
    const originalRequest = config;

    if (response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      originalRequest.withCredentials = true;

      return new Promise((resolve, reject) => {
        addRefreshSubscriber(() => {
          resolve(instance(originalRequest));
        });

        if (!isRefreshing) {
          isRefreshing = true;

          axios.get('http://localhost:8080/token/refresh', {
            withCredentials: true,
          })
            .then(() => {
              onRefreshed(); // 모든 구독된 요청 실행
            })
            .catch((refreshError) => {
              window.location.replace('/login');
              reject(refreshError);
            })
            .finally(() => {
              isRefreshing = false;
            });
        }
      });
    }

    return Promise.reject(error);
  }
);

export default instance;
