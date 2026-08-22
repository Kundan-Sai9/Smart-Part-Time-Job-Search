import React, { useState } from "react";
import { Send } from "lucide-react";
import { api } from "../services/api.js";
import FormShell from "../components/FormShell.jsx";
import EmptyLogin from "../components/EmptyLogin.jsx";

export default function PostJobPage({ userId, notify, go }) {
  const [form, setForm] = useState({ jobType: "Part Time" });

  function update(key, value) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function submit(event) {
    event.preventDefault();
    if (!userId) return go("login");
    try {
      await api.json("/api/jobs", { ...form, postedBy: Number(userId) });
      notify("Job posted successfully.");
      go("dashboard");
    } catch (error) {
      notify(error.message, "error");
    }
  }

  if (!userId) return <EmptyLogin go={go} />;

  return (
    <FormShell title="Post a Job" icon={<Send size={24} />}>
      <form className="form-grid" onSubmit={submit}>
        <input placeholder="Job title" onChange={(e) => update("title", e.target.value)} required />
        <input placeholder="Company" onChange={(e) => update("company", e.target.value)} required />
        <input placeholder="Location" onChange={(e) => update("location", e.target.value)} required />
        <input placeholder="Salary" onChange={(e) => update("salary", e.target.value)} required />
        <select onChange={(e) => update("jobType", e.target.value)} value={form.jobType}>
          <option>Part Time</option>
          <option>Technical</option>
          <option>Remote</option>
          <option>Internship</option>
        </select>
        <input placeholder="Experience" onChange={(e) => update("experience", e.target.value)} />
        <textarea placeholder="Required skills" onChange={(e) => update("skills", e.target.value)} />
        <textarea placeholder="Description" onChange={(e) => update("description", e.target.value)} required />
        <button className="primary">Publish Job</button>
      </form>
    </FormShell>
  );
}
