import { SignIn } from "@clerk/nextjs";

export default function LoginPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-bg-0">
      <SignIn
        path="/auth/login"
        signUpUrl="/auth/register"
        fallbackRedirectUrl="/dashboard"
      />
    </div>
  );
}
