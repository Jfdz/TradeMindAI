import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement>;

function BaseIcon(props: IconProps) {
  return <svg aria-hidden="true" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} viewBox="0 0 24 24" {...props} />;
}

export function BrandMark({ className }: { className?: string }) {
  return (
    <svg aria-hidden="true" className={className} fill="none" viewBox="0 0 24 24">
      <rect height="24" rx="6" width="24" fill="currentColor" />
      <path d="M6.5 7.5L12 5.5L17.5 7.5L12 12L6.5 7.5Z" fill="#07090e" opacity="0.95" />
      <path d="M8.2 13.1L12 10.1L15.8 13.1L12 18L8.2 13.1Z" fill="#07090e" opacity="0.95" />
    </svg>
  );
}

export function MenuIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M4 7h16M4 12h16M4 17h16" />
    </BaseIcon>
  );
}

export function XIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M6 6l12 12M18 6L6 18" />
    </BaseIcon>
  );
}

export function ArrowRightIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M5 12h13" />
      <path d="M13 6l6 6-6 6" />
    </BaseIcon>
  );
}

export function CheckIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M20 6L9 17l-5-5" />
    </BaseIcon>
  );
}

export function ZapIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M13 2L4 14h6l-1 8 9-12h-6l1-8Z" />
    </BaseIcon>
  );
}

export function TargetIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <circle cx="12" cy="12" r="8" />
      <circle cx="12" cy="12" r="3" />
    </BaseIcon>
  );
}

export function ChartIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M5 19V5" />
      <path d="M5 19h14" />
      <path d="M8 15l3-4 3 2 4-6" />
    </BaseIcon>
  );
}

export function FlaskIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M10 3h4" />
      <path d="M10 3v5l-4.5 7.5A3 3 0 0 0 8 20h8a3 3 0 0 0 2.5-4.5L14 8V3" />
    </BaseIcon>
  );
}

export function BellIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M15 17H5l1.4-1.4A2 2 0 0 0 7 14.2V11a5 5 0 1 1 10 0v3.2c0 .5.2 1 .6 1.4L19 17h-4" />
      <path d="M10 20a2 2 0 0 0 4 0" />
    </BaseIcon>
  );
}

export function LockIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <rect height="9" rx="2" width="14" x="5" y="11" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </BaseIcon>
  );
}

export function CirclePulseIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <circle cx="12" cy="12" r="7" />
      <circle cx="12" cy="12" r="2.2" fill="currentColor" stroke="none" />
    </BaseIcon>
  );
}

export function TrendUpIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M4 16l5-5 4 4 7-8" />
      <path d="M15 7h5v5" />
    </BaseIcon>
  );
}

export function TrendDownIcon(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M4 8l5 5 4-4 7 8" />
      <path d="M15 17h5v-5" />
    </BaseIcon>
  );
}
