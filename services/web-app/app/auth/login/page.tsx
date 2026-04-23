import { AuthShell } from "@/components/auth/auth-shell";
import { LoginForm } from "@/components/auth/login-form";

export default function LoginPage() {
  return (
    <AuthShell
      eyebrow="Secure access"
      title="Sign in to TradeMindAI."
      description="Use your trading-core credentials to open the dashboard, signals, and portfolio tools."
    >
      <div className="mb-8">
        <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Welcome back</p>
        <h2 className="mt-3 text-2xl font-semibold text-slate-900 dark:text-white">Access your trading workspace</h2>
        <p className="mt-2 text-sm leading-7 text-slate-600 dark:text-slate-300">
          Credentials are routed through NextAuth so the session is stored in an HTTP-only JWT cookie.
        </p>
      </div>
      <LoginForm />
    </AuthShell>
  );
}
