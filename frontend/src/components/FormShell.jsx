import React from "react";

export default function FormShell({ title, icon, children }) {
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
