import { auth, currentUser } from "@clerk/nextjs/server";
import Link from "next/link";
import { redirect } from "next/navigation";

import { ReasoningAuditExplorer } from "@/components/admin/reasoning-audit-explorer";

export const metadata = {
  title: "Reasoning audit — admin",
};

export default async function ReasoningAuditPage() {
  const { userId } = await auth();
  if (!userId) {
    redirect("/auth/login?callbackUrl=/dashboard/admin/reasoning-audit");
  }

  const user = await currentUser();
  const isAdmin = (user?.publicMetadata as { role?: string } | null)?.role === "admin";
  if (!isAdmin) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-12">
        <h1 className="text-2xl font-semibold text-text-1">Forbidden</h1>
        <p className="mt-4 text-text-2">
          This admin tool is restricted to designated administrators. If you
          believe you should have access, contact the workspace owner.
        </p>
        <Link
          href="/dashboard"
          className="mt-6 inline-block text-cyan hover:text-cyan-bright"
        >
          ← Back to dashboard
        </Link>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-7xl space-y-6 px-6 py-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold text-text-1">Reasoning audit</h1>
        <p className="text-sm text-text-2">
          Admin-only view of the AI reasoning pipeline. Filter by ticker, click
          a signal to inspect its full audit: outcome, refusal reason,
          validator violations, citations, and the captured facts snapshot.
        </p>
      </header>
      <ReasoningAuditExplorer />
    </main>
  );
}
