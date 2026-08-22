import React, { useState, useEffect, useMemo } from "react";
import { BriefcaseBusiness, MapPin, Search, Sparkles, User } from "lucide-react";
import { api } from "../services/api.js";
import InfoPanel from "../components/InfoPanel.jsx";
import JobCard from "../components/JobCard.jsx";
import Pagination from "../components/Pagination.jsx";

const JOBS_PER_PAGE = 8;

export default function HomePage({ userId, user, notify, go }) {
  const [jobs, setJobs] = useState([]);
  const [query, setQuery] = useState("");
  const [recommendations, setRecommendations] = useState(null);
  const [profileScore, setProfileScore] = useState(null);
  const [resumeJobId, setResumeJobId] = useState(null);
  const [resume, setResume] = useState(null);
  const [busy, setBusy] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);

  useEffect(() => {
    loadJobs();
  }, []);

  async function loadJobs(search = "") {
    setBusy(true);
    setCurrentPage(1);
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
    if (!userId) { go("login"); return; }
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

  const visibleJobs = useMemo(
    () => jobs.filter((job) => Number(job.postedBy) !== Number(userId)),
    [jobs, userId]
  );

  const totalPages = Math.ceil(visibleJobs.length / JOBS_PER_PAGE);
  const paginatedJobs = visibleJobs.slice(
    (currentPage - 1) * JOBS_PER_PAGE,
    currentPage * JOBS_PER_PAGE
  );

  function handlePageChange(page) {
    setCurrentPage(page);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

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
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search jobs by title or description"
            onKeyDown={(e) => e.key === "Enter" && loadJobs(query)}
          />
        </div>
        <button className="primary" onClick={() => loadJobs(query)}>Search</button>
      </section>

      <section className="ai-grid">
        <InfoPanel title="Profile completeness" icon={<User size={22} />} action="Analyze" onAction={analyzeProfile}>
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
              <div className="recommendation-list">
                {recommendations.map((rec, index) => {
                  const matchScore = Number(rec.match_strength ?? rec.match_score ?? 0);
                  const retrievalSimilarity = Number(rec.retrieval_similarity ?? 0);
                  const skillOverlap = Number(rec.skill_overlap ?? 0);
                  const locationMatch = rec.location_match == null ? null : Number(rec.location_match);
                  const jobTypeMatch = rec.job_type_match == null ? null : Number(rec.job_type_match);
                  const reasons = (rec.match_reasons || [])
                    .filter((reason) => reason && !reason.toLowerCase().startsWith("profile completeness:"))
                    .slice(0, 2);

                  return (
                    <article className="recommendation-card" key={rec.id || rec.job_id || `${rec.title}-${index}`}>
                      <div className="recommendation-rank">{String(index + 1).padStart(2, "0")}</div>
                      <div className="recommendation-content">
                        <div className="recommendation-header">
                          <div>
                            <h3>{rec.title || "Recommended opportunity"}</h3>
                            <p>{rec.company || "Company unavailable"}</p>
                          </div>
                          <strong className="match-score">{matchScore}%<small>strength</small></strong>
                        </div>
                        <div className="recommendation-meta">
                          <span><MapPin size={13} />{rec.location || "Location unavailable"}</span>
                          <span><BriefcaseBusiness size={13} />RAG retrieval</span>
                        </div>
                        <div className="match-bar" aria-label={`${matchScore}% match`}>
                          <span style={{ width: `${Math.max(0, Math.min(100, matchScore))}%` }} />
                        </div>
                        {reasons.length > 0 && (
                          <p className="recommendation-reason">{reasons.join(" · ")}</p>
                        )}
                        <div className="recommendation-evidence">
                          <span>Retrieval {Math.round(Math.max(0, Math.min(1, retrievalSimilarity)) * 100)}%</span>
                          <span>Skills {Math.round(Math.max(0, Math.min(1, skillOverlap)) * 100)}%</span>
                          <span>Location {locationMatch == null ? "Unavailable" : locationMatch ? "Match" : "No match"}</span>
                          <span>Type {jobTypeMatch == null ? "Unavailable" : jobTypeMatch ? "Match" : "No match"}</span>
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
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
        {!busy && paginatedJobs.map((job) => (
          <JobCard key={job.id} job={job} actionLabel="Apply" onAction={() => setResumeJobId(job.id)} />
        ))}
      </section>

      {!busy && visibleJobs.length > 0 && (
        <div className="pagination-footer">
          <span className="pagination-info">
            Showing {(currentPage - 1) * JOBS_PER_PAGE + 1}–{Math.min(currentPage * JOBS_PER_PAGE, visibleJobs.length)} of {visibleJobs.length} jobs
          </span>
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            onPageChange={handlePageChange}
          />
        </div>
      )}

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
