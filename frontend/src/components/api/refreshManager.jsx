import axios from 'axios';

let isRefreshing = false;
let refreshPromise = null;

export const safeRefreshToken = async () => {
  if (isRefreshing) return refreshPromise;

  isRefreshing = true;
  refreshPromise = axios.post(
    `${process.env.REACT_APP_BASE_API_URL}/token/refresh`,
    {},
    { withCredentials: true }
  )
    .catch((err) => {
      throw err;
    })
    .finally(() => {
      isRefreshing = false;
      refreshPromise = null;
    });

  return refreshPromise;
};