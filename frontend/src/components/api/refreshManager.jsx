// refreshManager.js
import axios from 'axios';

let isRefreshing = false;
let refreshPromise = null;

export const safeRefreshToken = async () => {
  if (isRefreshing) return refreshPromise;

  isRefreshing = true;
  refreshPromise = axios.get('http://localhost:8080/token/refresh', {
    withCredentials: true,
  })
    .catch((err) => {
      throw err;
    })
    .finally(() => {
      isRefreshing = false;
    });

  return refreshPromise;
};
