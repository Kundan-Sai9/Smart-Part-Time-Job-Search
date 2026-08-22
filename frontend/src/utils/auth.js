export function getStoredUserId() {
  const value = sessionStorage.getItem("user_id");
  return value && !Number.isNaN(Number(value)) ? Number(value) : null;
}

export function getStoredAccessToken() {
  return sessionStorage.getItem("access_token");
}

export function buildAuthHeaders(path) {
  try {
    if (typeof path !== "string") return {};
    const isApi = path.startsWith("/api/");
    const isAuthApi = path.startsWith("/api/auth/");
    if (!isApi || isAuthApi) return {};

    const token = getStoredAccessToken();
    if (!token) return {};
    return { Authorization: `Bearer ${token}` };
  } catch {
    return {};
  }
}
