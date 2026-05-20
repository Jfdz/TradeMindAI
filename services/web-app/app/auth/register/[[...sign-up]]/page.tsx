import { SignUp } from "@clerk/nextjs";

export default function RegisterPage() {
  if (!process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-bg-0">
        <p className="text-sm text-text-2">
          Auth not configured. Add <code>NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY</code> and{" "}
          <code>CLERK_SECRET_KEY</code> to <code>services/web-app/.env.local</code> and restart.
        </p>
      </div>
    );
  }
  return (
    <div className="flex min-h-screen items-center justify-center bg-bg-0">
      <SignUp
        path="/auth/register"
        signInUrl="/auth/login"
        fallbackRedirectUrl="/dashboard"
      />
    </div>
  );
}
