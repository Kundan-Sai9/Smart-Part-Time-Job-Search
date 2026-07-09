import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Briefcase,
  CheckCircle2,
  ClipboardList,
  LogOut,
  Moon,
  Search,
  Send,
  Sparkles,
  Sun,
  User,
  UserRound,
} from "lucide-react";
import "./styles.css";
import partTimeLogo from "./part-time.png";

const api = {
  async get(path) {
    const response = await fetch(path, {
      headers: buildAuthHeaders(path),
    });
    return parseResponse(response);
  },

  async json(path, body, method = "POST") {
    const response = await fetch(path, {
      method,
      headers: {
        "Content-Type": "application/json",
        ...buildAuthHeaders(path),
      },
      body: JSON.stringify(body),
    });
    return parseResponse(response);
  },

  async form(path, formData) {
    const response = await fetch(path, {
      method: "POST",
      headers: buildAuthHeaders(path),
      body: formData,
    });
    return parseResponse(response);
  },

  async delete(path) {
    const response = await fetch(path, {
      method: "DELETE",
      headers: buildAuthHeaders(path),
    });
    return parseResponse(response);
  },
};

function buildAuthHeaders(path) {
  try {
    // Only protect /api/** except /api/auth/**
    if (typeof path !== "string") return {};
    const isApi = path.startsWith("/api/");
    const isAuthApi = path.startsWith("/api/auth/");
    if (!isApi || isAuthApi) return {};

    const token = sessionStorage.getItem("access_token");
    if (!token) return {};
    return { Authorization: `Bearer ${token}` };
  } catch {
    return {};
  }
}


async function parseResponse(response) {
  const text = await response.text();
  const data = text ? JSON.parse(text) : {};
  if (!response.ok || data.error) {
    throw new Error(data.error || data.message || "Request failed");
  }
  return data;
}

function getStoredUserId() {
  const value = sessionStorage.getItem("user_id");
  return value && !Number.isNaN(Number(value)) ? Number(value) : null;
}

function getStoredAccessToken() {
  return sessionStorage.getItem("access_token");
}

function App() {
  const [userId, setUserId] = useState(getStoredUserId);
  const [accessToken, setAccessToken] = useState(getStoredAccessToken);
  const [theme, setTheme] = useState(() => localStorage.getItem("theme") || "dark");

  const [user, setUser] = useState(null);
  const [page, setPage] = useState(() => window.location.pathname.replace("/", "") || "home");
  const [notice, setNotice] = useState(null);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("theme", theme);
  }, [theme]);

  function toggleTheme() {
    setTheme(t => t === "dark" ? "light" : "dark");
  }

  useEffect(() => {
    const onPop = () => setPage(window.location.pathname.replace("/", "") || "home");
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, []);

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

  function go(nextPage) {
    window.history.pushState({}, "", `/${nextPage === "home" ? "" : nextPage}`);
    setPage(nextPage);
  }

  function notify(message, type = "success") {
    setNotice({ message, type });
    window.setTimeout(() => setNotice(null), 3200);
  }

  function login(userData) {
    sessionStorage.setItem("user_id", userData.user_id);
    if (userData.access_token) sessionStorage.setItem("access_token", userData.access_token);
    setUserId(Number(userData.user_id));
    go("home");
  }

  function logout() {
    sessionStorage.removeItem("access_token");
    sessionStorage.removeItem("user_id");
    setUserId(null);
    setUser(null);
    notify("Logged out successfully.");
    go("login");
  }

  const context = { userId, user, login, logout, notify, go };
  const currentPage = page.endsWith(".html") ? page.replace(".html", "") : page;

  return (
    <>
      <Header page={currentPage} user={user} go={go} logout={logout} theme={theme} toggleTheme={toggleTheme} />
      {notice && <Toast notice={notice} />}
      <main>
        {currentPage === "login" && <LoginPage {...context} />}
        {currentPage === "post-job" && <PostJobPage {...context} />}
        {currentPage === "profile" && <ProfilePage {...context} />}
        {currentPage === "applied-jobs" && <AppliedJobsPage {...context} />}
        {currentPage === "dashboard" && <DashboardPage {...context} />}
        {!["login", "post-job", "profile", "applied-jobs", "dashboard"].includes(currentPage) && (
          <HomePage {...context} />
        )}
      </main>
    </>
  );
}

function Header({ page, user, go, logout, theme, toggleTheme }) {
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

function Toast({ notice }) {
  return <div className={`toast ${notice.type}`}>{notice.message}</div>;
}

function LoginPage({ login, notify }) {
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

function HomePage({ userId, user, notify, go }) {
  const [jobs, setJobs] = useState([]);
  const [query, setQuery] = useState("");
  const [recommendations, setRecommendations] = useState(null);
  const [profileScore, setProfileScore] = useState(null);
  const [resumeJobId, setResumeJobId] = useState(null);
  const [resume, setResume] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    loadJobs();
  }, []);

  async function loadJobs(search = "") {
    setBusy(true);
    try {
      const data = await api.get(`/api/jobs${search ? `?search=${encodeURIComponent(search)}` : ""}`);
      setJobs(data);
    } catch (error) {
      notify(error.message, "error");
    } finally {
      setBusy(false);
    }
  }

  async function apply(jobId) {
    if (!userId) {
      go("login");
      return;
    }
    const form = new FormData();
    form.append("userId", userId);
    form.append("jobId", jobId);
    if (resume) form.append("resume", resume);
    try {
      const data = await api.form("/api/applied-jobs/apply", form);
      notify(data.message || "Application submitted.");
      setResumeJobId(null);
      setResume(null);
    } catch (error) {
      notify(error.message, "error");
    }
  }

  async function analyzeProfile() {
    if (!userId) return go("login");
    try {
      setProfileScore(await api.get(`/api/profile/score?userId=${userId}`));
    } catch (error) {
      notify(error.message, "error");
    }
  }

  async function loadRecommendations() {
    if (!userId) return go("login");
    try {
      const data = await api.get(`/api/jobs/ai/recommendations?userId=${userId}&limit=4`);
      setRecommendations(data.recommendations || []);
    } catch (error) {
      notify(error.message, "error");
    }
  }

  const visibleJobs = useMemo(() => jobs.filter((job) => Number(job.postedBy) !== Number(userId)), [jobs, userId]);

  return (
    <>
      <section className="hero">
        <div>
          <p className="eyebrow">Part-time job matching</p>
          <h1>Find work that fits your skills and schedule.</h1>
          <p>Browse openings, apply with a resume, and use profile-based recommendations from the existing backend.</p>
        </div>
        <div className="hero-panel">
          <Sparkles size={28} />
          <strong>{user ? `Hi, ${user.full_name || user.username}` : "Start with a profile"}</strong>
          <span>Sharper profiles create better matches.</span>
        </div>
      </section>

      <section className="toolbar">
        <div className="searchbox">
          <Search size={18} />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search jobs by title or description" />
        </div>
        <button className="primary" onClick={() => loadJobs(query)}>Search</button>
      </section>

      <section className="ai-grid">
        <InfoPanel title="Profile Score" icon={<User size={22} />} action="Analyze" onAction={analyzeProfile}>
          {profileScore ? (
            <>
              <strong>{profileScore.score}/100</strong>
              <p>{profileScore.suggestion || profileScore.analysis || "Keep your profile fresh."}</p>
            </>
          ) : (
            <p>Check completeness and improvement guidance.</p>
          )}
        </InfoPanel>
        <InfoPanel title="Recommendations" icon={<Sparkles size={22} />} action="Load" onAction={loadRecommendations}>
          {recommendations ? (
            recommendations.length ? (
              <ul className="compact-list">
                {recommendations.map((rec) => (
                  <li key={rec.id || rec.job_id}>{rec.title} · {rec.match_score || 0}%</li>
                ))}
              </ul>
            ) : (
              <p>No recommendations yet.</p>
            )
          ) : (
            <p>Get matches from your profile and job history.</p>
          )}
        </InfoPanel>
      </section>

      <section className="job-grid">
        {busy && <p>Loading jobs...</p>}
        {!busy && visibleJobs.map((job) => (
          <JobCard key={job.id} job={job} actionLabel="Apply" onAction={() => setResumeJobId(job.id)} />
        ))}
      </section>

      {resumeJobId && (
        <div className="modal-backdrop">
          <div className="panel modal">
            <h2>Submit Application</h2>
            <input type="file" accept=".pdf,.doc,.docx" onChange={(e) => setResume(e.target.files?.[0] || null)} />
            <div className="modal-actions">
              <button onClick={() => setResumeJobId(null)}>Cancel</button>
              <button className="primary" onClick={() => apply(resumeJobId)}>Submit</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

function InfoPanel({ title, icon, action, onAction, children }) {
  return (
    <article className="panel info-panel">
      <div className="panel-title">
        {icon}
        <h2>{title}</h2>
      </div>
      <div>{children}</div>
      <button onClick={onAction}>{action}</button>
    </article>
  );
}

function JobCard({ job, actionLabel, onAction }) {
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

function PostJobPage({ userId, notify, go }) {
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

function ProfilePage({ userId, user, notify, go }) {
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

  return (
    <FormShell title="Profile" icon={<User size={24} />}>
      <form className="form-grid" onSubmit={submit}>
        <input value={form.full_name || form.fullName || ""} placeholder="Full name" onChange={(e) => update("fullName", e.target.value)} />
        <input value={form.username || ""} placeholder="Username" onChange={(e) => update("username", e.target.value)} />
        <input value={form.email || ""} placeholder="Email" onChange={(e) => update("email", e.target.value)} />
        <input value={form.job_title || form.jobTitle || ""} placeholder="Desired job title" onChange={(e) => update("jobTitle", e.target.value)} />
        <input value={form.preferred_location || form.preferredLocation || ""} placeholder="Preferred location" onChange={(e) => update("preferredLocation", e.target.value)} />
        <input value={form.preferred_job_type || form.preferredJobType || ""} placeholder="Preferred job type" onChange={(e) => update("preferredJobType", e.target.value)} />
        <textarea value={form.skills || ""} placeholder="Skills" onChange={(e) => update("skills", e.target.value)} />
        <textarea value={form.experience || ""} placeholder="Experience" onChange={(e) => update("experience", e.target.value)} />
        <textarea value={form.bio || ""} placeholder="Bio" onChange={(e) => update("bio", e.target.value)} />
        <button className="primary">Save Profile</button>
      </form>
    </FormShell>
  );
}

function AppliedJobsPage({ userId, notify, go }) {
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

function DashboardPage({ userId, notify, go }) {
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

  if (!userId) return <EmptyLogin go={go} />;

  return (
    <section className="dashboard">
      <div className="section-title">
        <ClipboardList size={24} />
        <h1>Posted Jobs</h1>
      </div>
      {jobs.map((job) => (
        <article className="panel" key={job.job_id}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <h2>{job.job_title}</h2>
              <p>{job.company} · {job.location}</p>
            </div>
            <button className="small" onClick={() => setEditingJob(job)}>Edit Job</button>
          </div>
          <div className="table-list">
            {(job.applicants || []).map((app) => (
              <div key={app.application_id}>
                <span>{app.username}</span>
                <span>{app.status}</span>
                <a href={`/api/files/resume/${app.application_id}`} target="_blank" rel="noreferrer">Resume</a>
                {app.status === "Pending" && (
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="small" onClick={() => approve(app.application_id, job.job_id)}>Approve</button>
                    <button className="small outline" onClick={() => reject(app.application_id, job.job_id)}>Reject</button>
                  </div>
                )}
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

function ListPanel({ title, icon, items, onWithdraw }) {
  return (
    <article className="panel">
      <div className="panel-title">
        {icon}
        <h1>{title}</h1>
      </div>
      <div className="stack">
        {items.length === 0 && <p className="muted">Nothing here yet.</p>}
        {items.map((item, index) => (
          <div className="list-card" key={item.application_id || `${item.title}-${index}`}>
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '4px' }}>
              <strong style={{ fontSize: '1.1em' }}>{item.title || item.job_title}</strong>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--text-secondary)' }}>
                <span>{item.company}</span>
                <span style={{ opacity: 0.5 }}>•</span>
                <span>{item.location || "Location unavailable"}</span>
              </div>
              <small style={{ color: 'var(--primary)', fontWeight: '600' }}>{item.status || item.salary || ""}</small>
            </div>
            {onWithdraw && item.status === "Pending" && (
              <button className="small" onClick={() => onWithdraw(item.application_id)}>Withdraw</button>
            )}
          </div>
        ))}
      </div>
    </article>
  );
}

function FormShell({ title, icon, children }) {
  return (
    <section className="form-shell">
      <div className="panel form-panel">
        <div className="panel-title">
          {icon}
          <h1>{title}</h1>
        </div>
        {children}
      </div>
    </section>
  );
}

function EmptyLogin({ go }) {
  return (
    <section className="empty-state">
      <h1>Please login first.</h1>
      <button className="primary" onClick={() => go("login")}>Login</button>
    </section>
  );
}

createRoot(document.getElementById("root")).render(<App />);

