import React, { useState } from "react";
import { api } from "../services/api.js";

export default function LoginPage({ login, notify }) {
  const [mode, setMode] = useState("login");
  const [form, setForm] = useState({});
  const [busy, setBusy] = useState(false);

  function update(key, value) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    try {
      if (mode === "signup") {
        if (form.password !== form.confirmPassword) throw new Error("Passwords do not match.");
        const data = await api.json("/api/auth/signup", form);
        notify(data.message || "Signup successful.");
        login(data);
      } else {
        const data = await api.json("/api/auth/login", {
          userInput: form.userInput,
          password: form.password,
        });
        notify(data.message || "Login successful.");
        login(data);
      }
    } catch (error) {
      notify(error.message, "error");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="auth-shell">
      <form className="panel auth-panel" onSubmit={submit}>
        <div className="segmented">
          <button type="button" className={mode === "login" ? "active" : ""} onClick={() => setMode("login")}>
            Login
          </button>
          <button type="button" className={mode === "signup" ? "active" : ""} onClick={() => setMode("signup")}>
            Sign Up
          </button>
        </div>
        <h1>{mode === "login" ? "Welcome Back" : "Create Account"}</h1>
        {mode === "signup" && (
          <>
            <input placeholder="Full name" onChange={(e) => update("fullName", e.target.value)} required />
            <input placeholder="Username" onChange={(e) => update("username", e.target.value)} required />
            <input type="email" placeholder="Email" onChange={(e) => update("email", e.target.value)} required />
          </>
        )}
        {mode === "login" && <input placeholder="Username or email" onChange={(e) => update("userInput", e.target.value)} required />}
        <input type="password" placeholder="Password" onChange={(e) => update("password", e.target.value)} required />
        {mode === "signup" && (
          <input type="password" placeholder="Confirm password" onChange={(e) => update("confirmPassword", e.target.value)} required />
        )}
        <button className="primary" disabled={busy}>
          {busy ? "Please wait..." : mode === "login" ? "Login" : "Sign Up"}
        </button>
      </form>
    </section>
  );
}
