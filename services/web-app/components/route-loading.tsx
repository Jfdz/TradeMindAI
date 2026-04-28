type RouteLoadingProps = {
  eyebrow: string;
  title: string;
  description: string;
};

export function RouteLoading({ eyebrow, title, description }: RouteLoadingProps) {
  return (
    <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
      <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">{eyebrow}</div>
      <div className="mt-3 h-10 w-72 animate-pulse rounded-full bg-bg-2" aria-label={title} />
      <p className="mt-4 max-w-2xl text-sm leading-7 text-text-2">{description}</p>
      <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: 3 }, (_, index) => (
          <div key={index} className="h-32 animate-pulse rounded-[20px] bg-bg-2" />
        ))}
      </div>
    </section>
  );
}
