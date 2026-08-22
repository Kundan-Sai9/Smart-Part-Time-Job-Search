import React from "react";

export default function InfoPanel({ title, icon, action, onAction, children }) {
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
