export default function PasswordSettingsPage() {
  return (
    <div className="space-y-8">
      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Settings</div>
        <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
          Password change
        </h2>
        <p className="mt-3 max-w-xl text-sm leading-7 text-text-2">
          Password management is coming soon. For now, use your identity provider to update credentials.
        </p>
      </section>
    </div>
  );
}
