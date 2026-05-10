import { AuthShell } from "@/components/auth/auth-shell";
import { LoginForm } from "@/components/auth/login-form";

export default function LoginPage() {
  return (
    <AuthShell
      mode="login"
      eyebrow="Secure access"
      title="Sign in to TradeMindAI"
      description="Sign in with your email and password to access the dashboard, signals, and portfolio tools."
    >
      <LoginForm />
    </AuthShell>
  );
}
