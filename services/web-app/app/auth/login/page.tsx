import { AuthShell } from "@/components/auth/auth-shell";
import { LoginForm } from "@/components/auth/login-form";

export default function LoginPage() {
  return (
    <AuthShell
      mode="login"
      eyebrow="Secure access"
      title="Sign in to TradeMindAI"
      description="Use your trading-core credentials to open the dashboard, signals, and portfolio tools."
    >
      <LoginForm />
    </AuthShell>
  );
}
