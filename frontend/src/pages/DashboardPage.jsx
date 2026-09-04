import React, { useState, useEffect } from "react";
import { CalendarDays, ClipboardList, FileText, Pencil, Users } from "lucide-react";
import { api } from "../services/api.js";
import EmptyLogin from "../components/EmptyLogin.jsx";

export default function DashboardPage({ userId, notify, go }) {
  const [jobs, setJobs] = useState([]);
  const [editingJob, setEditingJob] = useState(null);

  async function loadJobs() {
    api.get(`/api/applied-jobs/posted-applications?userId=${userId}`).then(setJobs).catch((e) => notify(e.message, "error"));
  }

  useEffect(() => {
    if (!userId) return;
    loadJobs();
  }, [userId]);

  async function approve(applicationId, jobId) {
    try {
      await api.json("/api/applied-jobs/approve", { applicationId, jobId, userId });
      notify("Application approved.");
      loadJobs();
    } catch (error) {
      notify(error.message, "error");
    }
  }

  async function reject(applicationId, jobId) {
    if(!window.confirm("Are you sure you want to reject this applicant?")) return;
    try {
      await api.json("/api/applied-jobs/reject", { applicationId, jobId, userId });
      notify("Application rejected.");
      loadJobs();
    } catch (error) {
      notify(error.message, "error");
    }
  }

  async function updateJob(event) {
    event.preventDefault();
    try {
      await api.json(`/api/jobs/${editingJob.job_id}`, {
        title: editingJob.job_title,
        description: editingJob.description,
        company: editingJob.company,
        location: editingJob.location,
        salary: editingJob.salary,
      }, "PUT");
      notify("Job updated.");
      setEditingJob(null);
      loadJobs();
    } catch (error) {
      notify(error.message, "error");
    }
  }

  function formatDate(value) {
    if (!value) return "Date unavailable";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "Date unavailable";
    return new Intl.DateTimeFormat(undefined, {
      day: "numeric",
      month: "short",
      year: "numeric",
    }).format(date);
  }

  if (!userId) return <EmptyLogin go={go} />;

  return (
    <section className="dashboard">
      <div className="section-title">
        <ClipboardList size={24} />
        <h1>Posted Jobs</h1>
      </div>
      {jobs.map((job) => (
        <article className="panel dashboard-job" key={job.job_id}>
          <div className="dashboard-job-header">
            <div>
              <div className="dashboard-job-title-row">
                <h2>{job.job_title}</h2>
                {job.job_type && <span className="job-type-badge">{job.job_type}</span>}
              </div>
              <p className="dashboard-job-location">{job.company} · {job.location}</p>
            </div>
            <button className="small edit-job-button" onClick={() => setEditingJob(job)}>
              <Pencil size={14} /> Edit job
            </button>
          </div>
          <div className="applicant-section-heading">
            <span><Users size={16} /> Applicants</span>
            <strong>{(job.applicants || []).length}</strong>
          </div>
          <div className="applicant-list">
            {(job.applicants || []).length === 0 && <p className="muted empty-applicants">No applications yet.</p>}
            {(job.applicants || []).map((app) => (
              <div className="applicant-card" key={app.application_id}>
                <div className="applicant-main">
                  <div className="applicant-avatar">{(app.username || "U").slice(0, 1).toUpperCase()}</div>
                  <div>
                    <strong>{app.username || "Unknown applicant"}</strong>
                    <div className="applicant-meta">
                      <span><CalendarDays size={14} />Applied on {formatDate(app.applied_at)}</span>
                      <a href={`${api.getBaseUrl()}/api/files/resume/${app.application_id}`} target="_blank" rel="noreferrer">
                        <FileText size={14} />View resume
                      </a>
                    </div>
                  </div>
                </div>
                <div className="applicant-actions">
                  <span className={`status-pill status-${(app.status || "Pending").toLowerCase()}`}>{app.status || "Pending"}</span>
                  {app.status === "Pending" && (
                    <div className="review-actions">
                      <button className="small approve-button" onClick={() => approve(app.application_id, job.job_id)}>Approve</button>
                      <button className="small reject-button" onClick={() => reject(app.application_id, job.job_id)}>Reject</button>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        </article>
      ))}
      {editingJob && (
        <div className="modal-backdrop">
          <form className="panel modal" onSubmit={updateJob}>
            <h2>Edit Job</h2>
            <input value={editingJob.job_title || ""} onChange={(e) => setEditingJob({...editingJob, job_title: e.target.value})} placeholder="Job Title" required />
            <input value={editingJob.company || ""} onChange={(e) => setEditingJob({...editingJob, company: e.target.value})} placeholder="Company" required />
            <input value={editingJob.location || ""} onChange={(e) => setEditingJob({...editingJob, location: e.target.value})} placeholder="Location" required />
            <input value={editingJob.salary || ""} onChange={(e) => setEditingJob({...editingJob, salary: e.target.value})} placeholder="Salary" required />
            <textarea value={editingJob.description || ""} onChange={(e) => setEditingJob({...editingJob, description: e.target.value})} placeholder="Description" required />
            <div className="modal-actions">
              <button type="button" onClick={() => setEditingJob(null)}>Cancel</button>
              <button className="primary" type="submit">Save</button>
            </div>
          </form>
        </div>
      )}
    </section>
  );
}
