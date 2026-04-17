import { AuthShell } from "@/components/auth/auth-shell";
import { RegisterForm } from "@/components/auth/register-form";

export default function RegisterPage() {
  return (
    <AuthShell
      eyebrow="Open an account"
      title="Create your trading profile."
      description="Register once and move directly into the dashboard with a user-owned session and the same JWT flow used by the app."
    >
      <div className="mb-8">
        <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Start here</p>
        <h2 className="mt-3 text-2xl font-semibold text-white">Create a new trading account</h2>
        <p className="mt-2 text-sm leading-7 text-slate-300">
          The registration form validates client-side and then creates the account through trading-core.
        </p>
      </div>
      <RegisterForm />
    </AuthShell>
  );
}
