import React from "react";

export default function EmptyLogin({ go }) {
  return (
    <section className="empty-state">
      <h1>Please login first.</h1>
      <button className="primary" onClick={() => go("login")}>Login</button>
    </section>
  );
}
