import React from "react";
import { Routes, Route, Navigate } from "react-router-dom";

// Pages
import HomePage from "./pages/HomePage.jsx";
import LoginPage from "./pages/LoginPage.jsx";
import PostJobPage from "./pages/PostJobPage.jsx";
import ProfilePage from "./pages/ProfilePage.jsx";
import AppliedJobsPage from "./pages/AppliedJobsPage.jsx";
import DashboardPage from "./pages/DashboardPage.jsx";

export default function AppRouter({ context }) {
  return (
    <Routes>
      <Route path="/" element={<HomePage {...context} />} />
      <Route path="/login" element={<LoginPage {...context} />} />
      <Route path="/post-job" element={<PostJobPage {...context} />} />
      <Route path="/profile" element={<ProfilePage {...context} />} />
      <Route path="/applied-jobs" element={<AppliedJobsPage {...context} />} />
      <Route path="/dashboard" element={<DashboardPage {...context} />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
