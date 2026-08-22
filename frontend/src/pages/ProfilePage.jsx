import React, { useState, useEffect } from "react";
import { User } from "lucide-react";
import { api } from "../services/api.js";
import FormShell from "../components/FormShell.jsx";
import EmptyLogin from "../components/EmptyLogin.jsx";

export default function ProfilePage({ userId, user, notify, go }) {
  const [form, setForm] = useState({});

  useEffect(() => {
    if (user) setForm(user);
  }, [user]);

  function update(key, value) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function submit(event) {
    event.preventDefault();
    if (!userId) return go("login");
    try {
      await api.json("/api/profile/", { ...form, userId: Number(userId) }, "PUT");
      notify("Profile updated.");
    } catch (error) {
      notify(error.message, "error");
    }
  }

  if (!userId) return <EmptyLogin go={go} />;

  const displayName = form.full_name || form.fullName || form.username || "Your profile";

  return (
    <FormShell title="Profile" icon={<User size={24} />}>
      <div className="profile-intro">
        <div className="profile-avatar">{displayName.slice(0, 1).toUpperCase()}</div>
        <div>
          <strong>{displayName}</strong>
          <p>Keep your details current so recommendations fit you better.</p>
        </div>
      </div>
      <form className="form-grid profile-form" onSubmit={submit}>
        <section className="profile-section">
          <div className="profile-section-heading">
            <div>
              <h2>Personal details</h2>
              <p>How employers and the portal identify you.</p>
            </div>
          </div>
          <div className="profile-fields">
            <label>Full name<input value={form.full_name || form.fullName || ""} placeholder="e.g. Random Person" onChange={(e) => update("fullName", e.target.value)} /></label>
            <label>Username<input value={form.username || ""} placeholder="e.g. random" onChange={(e) => update("username", e.target.value)} /></label>
            <label className="profile-field-wide">Email<input type="email" value={form.email || ""} placeholder="you@example.com" onChange={(e) => update("email", e.target.value)} /></label>
          </div>
        </section>

        <section className="profile-section">
          <div className="profile-section-heading">
            <div>
              <h2>Job preferences</h2>
              <p>Use these details to improve your job matches.</p>
            </div>
          </div>
          <div className="profile-fields">
            <label>Preferred location<input value={form.preferred_location || form.preferredLocation || ""} placeholder="e.g. Hyderabad" onChange={(e) => update("preferredLocation", e.target.value)} /></label>
            <label>Preferred job type<input value={form.preferred_job_type || form.preferredJobType || ""} placeholder="e.g. Technical, flexible hours" onChange={(e) => update("preferredJobType", e.target.value)} /></label>
          </div>
        </section>

        <section className="profile-section">
          <div className="profile-section-heading">
            <div>
              <h2>Professional summary</h2>
              <p>Give employers a quick sense of your strengths and background.</p>
            </div>
          </div>
          <div className="profile-fields profile-summary-fields">
            <label>Skills<textarea value={form.skills || ""} placeholder="Java, Python, SQL, communication..." onChange={(e) => update("skills", e.target.value)} /></label>
            <label>Experience<textarea value={form.experience || ""} placeholder="Summarize your experience, projects, or education." onChange={(e) => update("experience", e.target.value)} /></label>
            <label className="profile-field-wide">About you<textarea value={form.bio || ""} placeholder="Write a short introduction about your goals and strengths." onChange={(e) => update("bio", e.target.value)} /></label>
          </div>
        </section>

        <button className="primary profile-save-button">Save Profile</button>
      </form>
    </FormShell>
  );
}
