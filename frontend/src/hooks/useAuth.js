import { useState, useEffect } from "react";
import { getStoredUserId, getStoredAccessToken } from "../utils/auth.js";
import { api } from "../services/api.js";

export function useAuth(navigate, notify) {
  const [userId, setUserId] = useState(getStoredUserId);
  const [accessToken, setAccessToken] = useState(getStoredAccessToken);
  const [user, setUser] = useState(null);

  useEffect(() => {
    if (!userId) {
      setUser(null);
      return;
    }
    api
      .get(`/api/profile/user-info?userId=${userId}`)
      .then(setUser)
      .catch(() => {
        sessionStorage.removeItem("user_id");
        setUserId(null);
      });
  }, [userId]);

  function login(userData) {
    sessionStorage.setItem("user_id", userData.user_id);
    if (userData.access_token) sessionStorage.setItem("access_token", userData.access_token);
    setUserId(Number(userData.user_id));
    navigate("/"); // Redirect to home
  }

  function logout() {
    sessionStorage.removeItem("access_token");
    sessionStorage.removeItem("user_id");
    setUserId(null);
    setUser(null);
    notify("Logged out successfully.");
    navigate("/login");
  }

  return { userId, user, login, logout };
}
