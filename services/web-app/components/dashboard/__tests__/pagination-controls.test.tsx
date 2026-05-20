import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { PaginationControls } from "../pagination-controls";

vi.mock("next/link", () => ({
  default: ({
    href,
    className,
    children,
  }: {
    href: string;
    className?: string;
    children: React.ReactNode;
  }) => React.createElement("a", { href, className }, children),
}));

function html(props: React.ComponentProps<typeof PaginationControls>) {
  const result = renderToStaticMarkup(React.createElement(PaginationControls, props));
  return result;
}

describe("PaginationControls", () => {
  it("renders nothing when totalPages <= 1", () => {
    expect(html({ pageNumber: 0, totalPages: 1, isFirst: true, isLast: true })).toBe("");
  });

  it("shows correct page counter", () => {
    const out = html({ pageNumber: 1, totalPages: 5, isFirst: false, isLast: false });
    expect(out).toContain("Page 2");
    expect(out).toContain("5 total");
  });

  it("disables Prev on the first page (renders span, not anchor)", () => {
    const out = html({ pageNumber: 0, totalPages: 3, isFirst: true, isLast: false });
    expect(out).toContain("<span");
    expect(out).toContain("Prev");
    expect(out).toContain('<a href="?page=1"');
    expect(out).toContain("Next");
  });

  it("disables Next on the last page (renders span, not anchor)", () => {
    const out = html({ pageNumber: 2, totalPages: 3, isFirst: false, isLast: true });
    expect(out).toContain('<a href="?page=1"');
    expect(out).toContain("Prev");
    expect(out).toContain("<span");
    expect(out).toContain("Next");
  });

  it("Prev link href equals pageNumber - 1", () => {
    const out = html({ pageNumber: 3, totalPages: 5, isFirst: false, isLast: false });
    expect(out).toContain('<a href="?page=2"');
  });

  it("Next link href equals pageNumber + 1", () => {
    const out = html({ pageNumber: 3, totalPages: 5, isFirst: false, isLast: false });
    expect(out).toContain('<a href="?page=4"');
  });
});
