"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { signIn } from "next-auth/react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { Button } from "@/components/ui/button";

const loginSchema = z.object({
  email: z.string().email("Enter a valid email address"),
  password: z.string().min(8, "Password must be at least 8 characters"),
});

type LoginFormValues = z.infer<typeof loginSchema>;

export function LoginForm() {
  const router = useRouter();
  const [serverError, setServerError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: "",
      password: "",
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setIsSubmitting(true);
    setServerError(null);

    const result = await signIn("credentials", {
      redirect: false,
      email: values.email,
      password: values.password,
    });

    setIsSubmitting(false);

    if (result?.error) {
      setServerError("Invalid email or password");
      return;
    }

    router.push("/dashboard");
    router.refresh();
  });

  return (
    <form className="space-y-5" onSubmit={onSubmit}>
      <div>
        <label className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3" htmlFor="email">
          Email
        </label>
        <input
          id="email"
          type="email"
          autoComplete="email"
          className="w-full rounded-2xl border border-border bg-bg-2 px-4 py-3 text-sm text-white outline-none transition placeholder:text-text-3 focus:border-cyan/40"
          {...register("email")}
        />
        {errors.email ? <p className="mt-2 text-sm text-red">{errors.email.message}</p> : null}
      </div>

      <div>
        <div className="mb-2 flex items-center justify-between gap-3">
          <label className="block text-xs uppercase tracking-[0.22em] text-text-3" htmlFor="password">
            Password
          </label>
          <Link className="text-xs text-cyan transition hover:text-white" href="/auth/login">
            Forgot password?
          </Link>
        </div>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          className="w-full rounded-2xl border border-border bg-bg-2 px-4 py-3 text-sm text-white outline-none transition placeholder:text-text-3 focus:border-cyan/40"
          {...register("password")}
        />
        {errors.password ? <p className="mt-2 text-sm text-red">{errors.password.message}</p> : null}
      </div>

      {serverError ? <p className="text-sm text-red">{serverError}</p> : null}

      <Button className="w-full" size="xl" type="submit" variant="cyan" disabled={isSubmitting}>
        {isSubmitting ? "Signing in..." : "Sign in"}
      </Button>

      <div className="flex items-center gap-4 py-2">
        <div className="h-px flex-1 bg-border" />
        <span className="text-[11px] uppercase tracking-[0.22em] text-text-3">or continue with</span>
        <div className="h-px flex-1 bg-border" />
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Button type="button" variant="outline" size="lg" className="w-full">
          Google
        </Button>
        <Button type="button" variant="outline" size="lg" className="w-full">
          GitHub
        </Button>
      </div>
    </form>
  );
}
