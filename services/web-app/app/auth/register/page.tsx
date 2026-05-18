import { AuthShell } from "@/components/auth/auth-shell";
import { RegisterForm } from "@/components/auth/register-form";

export default function RegisterPage() {
  return (
    <AuthShell
      mode="register"
      eyebrow="Open an account"
      title="Create your trading profile"
      description="Create your account and get instant access to signals, strategies, and portfolio tools."
    >
      <RegisterForm />
    </AuthShell>
  );
}
