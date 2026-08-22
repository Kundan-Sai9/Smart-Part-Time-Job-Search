import React, { useState, useEffect } from "react";
import { ClipboardList, CheckCircle2 } from "lucide-react";
import { api } from "../services/api.js";
import ListPanel from "../components/ListPanel.jsx";
import EmptyLogin from "../components/EmptyLogin.jsx";

export default function AppliedJobsPage({ userId, notify, go }) {
  const [jobs, setJobs] = useState([]);
  const [approved, setApproved] = useState([]);

  async function loadJobs() {
    api.get(`/api/applied-jobs?userId=${userId}`).then((data) => setJobs(data.jobs || [])).catch((e) => notify(e.message, "error"));
    api.get(`/api/applied-jobs/approved?userId=${userId}`).then(setApproved).catch(() => {});
  }

  useEffect(() => {
    if (!userId) return;
    loadJobs();
  }, [userId]);

  async function withdraw(applicationId) {
    if(!window.confirm("Are you sure you want to withdraw this application?")) return;
    try {
      await api.delete(`/api/applied-jobs/${applicationId}?userId=${userId}`);
      notify("Application withdrawn.");
      loadJobs();
    } catch(error) {
      notify(error.message, "error");
    }
  }

  if (!userId) return <EmptyLogin go={go} />;

  return (
    <section className="split-layout">
      <ListPanel title="Applications" icon={<ClipboardList size={22} />} items={jobs} onWithdraw={withdraw} />
      <ListPanel title="Approved Jobs" icon={<CheckCircle2 size={22} />} items={approved} />
    </section>
  );
}
