import React from "react";
import { Briefcase } from "lucide-react";

export default function JobCard({ job, actionLabel, onAction }) {
  return (
    <article className="job-card">
      <div className="job-card-top">
        <Briefcase size={22} />
        <span>{job.jobType || "Part time"}</span>
      </div>
      <h3>{job.title}</h3>
      <p>{job.company} · {job.location}</p>
      <p className="muted">{job.description}</p>
      <div className="job-meta">
        <span>{job.salary || "Salary not listed"}</span>
        <span>{job.experience || "Open experience"}</span>
      </div>
      {actionLabel && <button className="primary" onClick={onAction}>{actionLabel}</button>}
    </article>
  );
}
