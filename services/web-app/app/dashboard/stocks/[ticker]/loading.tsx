export default function Loading() {
  return (
    <main className="mx-auto max-w-[1280px] space-y-10 px-6 pb-16 pt-8">
      <div className="flex items-center gap-4">
        <div className="h-16 w-16 animate-pulse rounded-lg bg-card" />
        <div className="space-y-2">
          <div className="h-8 w-48 animate-pulse rounded-full bg-card" />
          <div className="h-4 w-32 animate-pulse rounded-full bg-card" />
        </div>
      </div>
      <div className="h-[360px] sm:h-[560px] animate-pulse rounded-xl bg-card" />
      <div className="grid gap-6 lg:grid-cols-[1.5fr_1fr]">
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={`left-${i}`} className="h-24 animate-pulse rounded-xl bg-card" />
          ))}
        </div>
        <div className="space-y-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={`right-${i}`} className="h-24 animate-pulse rounded-xl bg-card" />
          ))}
        </div>
      </div>
    </main>
  );
}
