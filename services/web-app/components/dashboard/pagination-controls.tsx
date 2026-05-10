import Link from "next/link";
import { cn } from "@/lib/utils";

type PaginationControlsProps = {
  pageNumber: number;
  totalPages: number;
  isFirst: boolean;
  isLast: boolean;
};

export function PaginationControls({ pageNumber, totalPages, isFirst, isLast }: PaginationControlsProps) {
  if (totalPages <= 1) return null;

  const displayPage = pageNumber + 1;
  const prevPage = pageNumber - 1;
  const nextPage = pageNumber + 1;

  const btnBase =
    "rounded-full border px-4 py-1.5 text-[10px] uppercase tracking-[0.22em] transition select-none";
  const btnEnabled = "border-border hover:bg-white/[0.025] text-text-3";
  const btnDisabled = "border-border/40 text-text-3/40 cursor-not-allowed pointer-events-none";

  return (
    <div className="flex items-center justify-between pt-4">
      <span className="text-[11px] uppercase tracking-[0.22em] text-text-3">
        Page {displayPage} of {totalPages}
      </span>
      <div className="flex gap-2">
        {isFirst ? (
          <span className={cn(btnBase, btnDisabled)}>Prev</span>
        ) : (
          <Link href={`?page=${prevPage}`} className={cn(btnBase, btnEnabled)}>
            Prev
          </Link>
        )}
        {isLast ? (
          <span className={cn(btnBase, btnDisabled)}>Next</span>
        ) : (
          <Link href={`?page=${nextPage}`} className={cn(btnBase, btnEnabled)}>
            Next
          </Link>
        )}
      </div>
    </div>
  );
}
