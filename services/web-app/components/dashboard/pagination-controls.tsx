import Link from "next/link";
import { cn } from "@/lib/utils";

type PaginationControlsProps = {
  readonly pageNumber: number;
  readonly totalPages: number;
  readonly isFirst: boolean;
  readonly isLast: boolean;
};

export function PaginationControls({ pageNumber, totalPages, isFirst, isLast }: PaginationControlsProps) {
  if (totalPages <= 1) return null;

  const displayPage = pageNumber + 1;
  const prevPage = pageNumber - 1;
  const nextPage = pageNumber + 1;

  const btnBase =
    "rounded-full border px-4 py-1.5 text-[10px] uppercase tracking-[0.22em] transition-all select-none";
  const btnEnabled =
    "border-cyan/40 bg-cyan/[0.06] text-cyan shadow-neon-soft " +
    "hover:bg-cyan/[0.12] hover:shadow-neon hover:-translate-y-px";
  const btnDisabled = "border-border/30 bg-bg-2/40 text-text-3/40 cursor-not-allowed pointer-events-none";

  return (
    <div className="flex items-center justify-between pt-4">
      <span className="text-[11px] uppercase tracking-[0.22em] text-text-3">
        Page {displayPage}
        <span className="mx-1.5 text-cyan/40">·</span>
        {totalPages} total
      </span>
      <div className="flex gap-2">
        {isFirst ? (
          <span className={cn(btnBase, btnDisabled)} aria-label="Previous page">← Prev</span>
        ) : (
          <Link href={`?page=${prevPage}`} className={cn(btnBase, btnEnabled)} aria-label="Previous page">
            ← Prev
          </Link>
        )}
        {isLast ? (
          <span className={cn(btnBase, btnDisabled)} aria-label="Next page">Next →</span>
        ) : (
          <Link href={`?page=${nextPage}`} className={cn(btnBase, btnEnabled)} aria-label="Next page">
            Next →
          </Link>
        )}
      </div>
    </div>
  );
}
