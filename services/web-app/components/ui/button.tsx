import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex items-center justify-center whitespace-nowrap rounded-full text-sm font-semibold transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan focus-visible:ring-offset-2 focus-visible:ring-offset-bg-0 disabled:pointer-events-none disabled:opacity-50",
  {
    variants: {
      variant: {
        default: "bg-cyan text-bg-0 shadow-glow hover:translate-y-[-1px] hover:bg-cyan/90",
        cyan: "bg-cyan text-bg-0 shadow-glow hover:translate-y-[-1px] hover:bg-cyan/90",
        gold: "bg-gold text-bg-0 shadow-goldGlow hover:translate-y-[-1px] hover:bg-gold/90",
        secondary: "bg-bg-2 text-text-1 hover:bg-bg-3",
        ghost: "text-text-2 hover:bg-white/5 hover:text-text-1",
        outline: "border border-border bg-transparent text-text-1 hover:border-border-strong hover:bg-white/5",
        outlineCyan: "border border-cyan/35 bg-transparent text-cyan hover:border-cyan hover:bg-cyan-dim",
        outlineGold: "border border-gold/35 bg-transparent text-gold hover:border-gold hover:bg-gold-dim",
      },
      size: {
        default: "h-11 px-6",
        sm: "h-9 px-4",
        lg: "h-12 px-8",
        xl: "h-14 px-8 text-sm",
        icon: "h-11 w-11",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(({ className, variant, size, asChild = false, ...props }, ref) => {
  const Comp = asChild ? Slot : "button";
  return <Comp className={cn(buttonVariants({ variant, size, className }))} ref={ref} {...props} />;
});
Button.displayName = "Button";

export { Button, buttonVariants };
