import React from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

export default function Pagination({ currentPage, totalPages, onPageChange }) {
  if (totalPages <= 1) return null;

  const pages = [];
  const delta = 1; // pages around current

  for (let i = 1; i <= totalPages; i++) {
    if (
      i === 1 ||
      i === totalPages ||
      (i >= currentPage - delta && i <= currentPage + delta)
    ) {
      pages.push(i);
    } else if (
      i === currentPage - delta - 1 ||
      i === currentPage + delta + 1
    ) {
      pages.push("...");
    }
  }

  // Deduplicate consecutive "..."
  const dedupedPages = pages.filter(
    (p, i) => !(p === "..." && pages[i - 1] === "...")
  );

  return (
    <div className="pagination">
      <button
        className="pagination-btn"
        onClick={() => onPageChange(currentPage - 1)}
        disabled={currentPage === 1}
        aria-label="Previous page"
      >
        <ChevronLeft size={16} />
      </button>

      {dedupedPages.map((page, idx) =>
        page === "..." ? (
          <span key={`ellipsis-${idx}`} className="pagination-ellipsis">
            …
          </span>
        ) : (
          <button
            key={page}
            className={`pagination-btn${page === currentPage ? " active" : ""}`}
            onClick={() => onPageChange(page)}
            aria-label={`Page ${page}`}
          >
            {page}
          </button>
        )
      )}

      <button
        className="pagination-btn"
        onClick={() => onPageChange(currentPage + 1)}
        disabled={currentPage === totalPages}
        aria-label="Next page"
      >
        <ChevronRight size={16} />
      </button>
    </div>
  );
}
