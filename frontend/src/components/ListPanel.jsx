import React from "react";
import { CalendarDays, FileText, MapPin } from "lucide-react";
import { api } from "../services/api.js";

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

function resumeName(path) {
  if (!path) return "No resume attached";
  const normalizedPath = path.replaceAll("\\\\", "/");
  return decodeURIComponent(normalizedPath.split("/").pop() || "Resume attached");
}

export default function ListPanel({ title, icon, items, onWithdraw }) {
  return (
    <article className="panel">
      <div className="panel-title">
        {icon}
        <h1>{title}</h1>
      </div>
      <div className="stack">
        {items.length === 0 && <p className="muted">Nothing here yet.</p>}
        {items.map((item, index) => {
          const resume = item.resume_path || item.resumePath;
          const attachedResumeName = item.resume_name || resumeName(resume);
          const appliedDate = item.applied_at || item.accepted_at;
          const status = item.status || "Approved";

          return (
            <div className="list-card application-card" key={item.application_id || `${item.title}-${index}`}>
              <div className="application-card-header">
                <div>
                  <strong className="application-title">{item.title || item.job_title}</strong>
                  <p className="application-company">{item.company || "Company unavailable"}</p>
                </div>
                <span className={`status-pill status-${status.toLowerCase()}`}>{status}</span>
              </div>
              <div className="application-details">
                <span><MapPin size={15} />{item.location || "Location unavailable"}</span>
                <span><CalendarDays size={15} />Applied on {formatDate(appliedDate)}</span>
                <span className="resume-detail" title={attachedResumeName}>
                  <FileText size={15} />
                  {attachedResumeName || "No resume attached"}
                  {resume && item.application_id && (
                    <a className="resume-link" href={`${api.getBaseUrl()}/api/files/resume/${item.application_id}`} target="_blank" rel="noreferrer">View</a>
                  )}
                </span>
              </div>
              {onWithdraw && status.toLowerCase() === "pending" && (
                <button className="withdraw-button" onClick={() => onWithdraw(item.application_id)}>Withdraw application</button>
              )}
            </div>
          );
        })}
      </div>
    </article>
  );
}
