import { AuthShell } from "@/components/auth/auth-shell";
import { RegisterForm } from "@/components/auth/register-form";

export default function RegisterPage() {
  return (
    <AuthShell
      mode="register"
      eyebrow="Open an account"
      title="Create your trading profile"
      description="Register once and move directly into the dashboard with a user-owned session and JWT flow."
    >
      <RegisterForm />
    </AuthShell>
  );
}
