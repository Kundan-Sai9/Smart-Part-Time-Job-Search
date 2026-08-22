import React from "react";
import { LogOut, Moon, Sun, UserRound } from "lucide-react";
import partTimeLogo from "../assets/part-time.png";

export default function Header({ page, user, go, logout, theme, toggleTheme }) {
  const items = [
    ["home", "Jobs"],
    ["post-job", "Post Job"],
    ["profile", "Profile"],
    ["applied-jobs", "Applied"],
    ["dashboard", "Dashboard"],
  ];
  return (
    <header className="topbar">
      <button className="brand" onClick={() => go("home")}>
        <img src={partTimeLogo} alt="" />
        <span>Smart Part Time Job Search</span>
      </button>
      <nav>
        {items.map(([key, label]) => (
          <button key={key} className={page === key ? "active" : ""} onClick={() => go(key)}>
            {label}
          </button>
        ))}
      </nav>
      <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
        <button className="user-chip" onClick={toggleTheme} title={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`} style={{ padding: '0.5rem' }}>
          {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
        </button>
        {user ? (
          <button className="user-chip" onClick={logout} title="Logout">
            <UserRound size={18} />
            <span>{user.username || user.full_name}</span>
            <LogOut size={16} />
          </button>
        ) : (
          <button className="primary small" onClick={() => go("login")}>
            Login
          </button>
        )}
      </div>
    </header>
  );
}
