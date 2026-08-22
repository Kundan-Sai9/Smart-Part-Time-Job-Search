import React from "react";

export default function Toast({ notice }) {
  if (!notice) return null;
  return <div className={`toast ${notice.type}`}>{notice.message}</div>;
}
