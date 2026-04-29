"use client";

import Link from "next/link";
import { useEffect } from "react";
import { Button } from "@/components/ui/button";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("Client error:", error);
  }, [error]);

  return (
    <div className="flex h-screen flex-col items-center justify-center gap-6 bg-bg-0 px-4">
      <div className="max-w-md text-center">
        <h1 className="font-display text-3xl font-bold text-white">Something went wrong</h1>
        <p className="mt-2 text-text-2">{error.message || "An unexpected error occurred"}</p>
      </div>
      <div className="flex gap-3">
        <Button onClick={reset} variant="outlineCyan">
          Try again
        </Button>
        <Button asChild variant="outline">
          <Link href="/">Go home</Link>
        </Button>
      </div>
    </div>
  );
}
