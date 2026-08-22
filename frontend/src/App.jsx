import React from "react";
import { useNavigate, useLocation } from "react-router-dom";
import Header from "./components/Header.jsx";
import Toast from "./components/Toast.jsx";
import AppRouter from "./AppRouter.jsx";
import { useAuth } from "./hooks/useAuth.js";
import { useTheme } from "./hooks/useTheme.js";
import { useNotification } from "./hooks/useNotification.js";

export default function App() {
  const navigate = useNavigate();
  const location = useLocation();
  const page = location.pathname.replace("/", "") || "home";

  const { theme, toggleTheme } = useTheme();
  const { notice, notify } = useNotification();
  const { userId, user, login, logout } = useAuth(navigate, notify);

  function go(nextPage) {
    navigate(`/${nextPage === "home" ? "" : nextPage}`);
  }

  const context = { userId, user, login, logout, notify, go };
  const currentPage = page.endsWith(".html") ? page.replace(".html", "") : page;

  return (
    <>
      <Header page={currentPage} user={user} go={go} logout={logout} theme={theme} toggleTheme={toggleTheme} />
      <Toast notice={notice} />
      <main>
        <AppRouter context={context} />
      </main>
    </>
  );
}
