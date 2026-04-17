import type { ReactNode } from "react";

export function AuthShell({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow: string;
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <main className="min-h-screen bg-[#08121f] text-slate-50">
      <div className="relative isolate overflow-hidden">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_right,_rgba(246,208,138,0.18),_transparent_32%),radial-gradient(circle_at_20%_20%,_rgba(84,213,180,0.16),_transparent_28%),linear-gradient(180deg,_#0d1728_0%,_#08121f_100%)]" />
        <div className="pointer-events-none absolute -left-24 top-24 h-72 w-72 rounded-full bg-gold-400/15 blur-3xl" />
        <div className="pointer-events-none absolute right-0 top-1/3 h-80 w-80 rounded-full bg-mint-400/10 blur-3xl" />

        <div className="relative mx-auto grid min-h-screen max-w-7xl gap-10 px-6 py-10 lg:grid-cols-[1.1fr_0.9fr] lg:px-10">
          <section className="flex items-end">
            <div className="max-w-2xl pb-10">
              <p className="text-xs uppercase tracking-[0.45em] text-gold-300/80">{eyebrow}</p>
              <h1 className="mt-6 text-5xl font-semibold leading-tight text-white sm:text-6xl">{title}</h1>
              <p className="mt-6 max-w-xl text-lg leading-8 text-slate-300">{description}</p>
            </div>
          </section>

          <section className="flex items-center justify-end">
            <div className="w-full max-w-xl rounded-[2rem] border border-white/10 bg-white/5 p-8 shadow-glow backdrop-blur">
              {children}
            </div>
          </section>
        </div>
      </div>
    </main>
  );
}
