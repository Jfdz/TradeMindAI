"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { signIn } from "next-auth/react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { Button } from "@/components/ui/button";

const registerSchema = z
  .object({
    firstName: z.string().min(2, "First name is required"),
    lastName: z.string().min(2, "Last name is required"),
    email: z.string().email("Enter a valid email address"),
    password: z.string().min(8, "Password must be at least 8 characters"),
    confirmPassword: z.string().min(8, "Confirm your password"),
  })
  .refine((value) => value.password === value.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords do not match",
  });

type RegisterFormValues = z.infer<typeof registerSchema>;

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8082";

export function RegisterForm() {
  const router = useRouter();
  const [serverError, setServerError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      firstName: "",
      lastName: "",
      email: "",
      password: "",
      confirmPassword: "",
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setIsSubmitting(true);
    setServerError(null);

    const response = await fetch(`${API_BASE_URL}/api/v1/auth/register`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify({
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        password: values.password,
      }),
    });

    if (!response.ok) {
      setIsSubmitting(false);
      setServerError("Registration failed. Please check your details and try again.");
      return;
    }

    const loginResult = await signIn("credentials", {
      redirect: false,
      email: values.email,
      password: values.password,
    });

    setIsSubmitting(false);

    if (loginResult?.error) {
      router.push("/auth/login?registered=1");
      return;
    }

    router.push("/dashboard");
    router.refresh();
  });

  return (
    <form className="space-y-5" onSubmit={onSubmit}>
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label className="mb-2 block text-sm font-medium text-slate-200" htmlFor="firstName">
            First name
          </label>
          <input
            id="firstName"
            className="w-full rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white outline-none ring-0 transition placeholder:text-slate-500 focus:border-gold-300/60"
            {...register("firstName")}
          />
          {errors.firstName ? <p className="mt-2 text-sm text-rose-300">{errors.firstName.message}</p> : null}
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-slate-200" htmlFor="lastName">
            Last name
          </label>
          <input
            id="lastName"
            className="w-full rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white outline-none ring-0 transition placeholder:text-slate-500 focus:border-gold-300/60"
            {...register("lastName")}
          />
          {errors.lastName ? <p className="mt-2 text-sm text-rose-300">{errors.lastName.message}</p> : null}
        </div>
      </div>

      <div>
        <label className="mb-2 block text-sm font-medium text-slate-200" htmlFor="email">
          Email
        </label>
        <input
          id="email"
          type="email"
          autoComplete="email"
          className="w-full rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white outline-none ring-0 transition placeholder:text-slate-500 focus:border-gold-300/60"
          {...register("email")}
        />
        {errors.email ? <p className="mt-2 text-sm text-rose-300">{errors.email.message}</p> : null}
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label className="mb-2 block text-sm font-medium text-slate-200" htmlFor="password">
            Password
          </label>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            className="w-full rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white outline-none ring-0 transition placeholder:text-slate-500 focus:border-gold-300/60"
            {...register("password")}
          />
          {errors.password ? <p className="mt-2 text-sm text-rose-300">{errors.password.message}</p> : null}
        </div>
        <div>
          <label className="mb-2 block text-sm font-medium text-slate-200" htmlFor="confirmPassword">
            Confirm password
          </label>
          <input
            id="confirmPassword"
            type="password"
            autoComplete="new-password"
            className="w-full rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white outline-none ring-0 transition placeholder:text-slate-500 focus:border-gold-300/60"
            {...register("confirmPassword")}
          />
          {errors.confirmPassword ? (
            <p className="mt-2 text-sm text-rose-300">{errors.confirmPassword.message}</p>
          ) : null}
        </div>
      </div>

      {serverError ? <p className="text-sm text-rose-300">{serverError}</p> : null}

      <Button className="w-full" type="submit" disabled={isSubmitting}>
        {isSubmitting ? "Creating account..." : "Create account"}
      </Button>
    </form>
  );
}
