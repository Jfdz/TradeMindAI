"use client";

import { useEffect } from "react";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("Global error:", error);
  }, [error]);

  return (
    <html lang="en">
      <body style={{ background: "#07090e", color: "#dce8f0", display: "flex", alignItems: "center", justifyContent: "center", minHeight: "100vh", margin: 0, fontFamily: "sans-serif" }}>
        <div style={{ textAlign: "center" }}>
          <h1 style={{ fontSize: "2rem", fontWeight: "bold" }}>Something went wrong</h1>
          <p style={{ marginTop: "0.5rem", color: "#7a90a8" }}>{error.message || "An unexpected error occurred"}</p>
          <button
            onClick={reset}
            style={{ marginTop: "1.5rem", padding: "0.5rem 1.5rem", border: "1px solid rgba(0,200,212,0.3)", borderRadius: "9999px", background: "rgba(0,200,212,0.15)", color: "#00c8d4", cursor: "pointer" }}
          >
            Try again
          </button>
        </div>
      </body>
    </html>
  );
}
