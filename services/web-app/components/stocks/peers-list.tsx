import Link from "next/link";

type Props = {
  readonly peers: string[];
};

export function PeersList({ peers }: Props) {
  if (!peers.length) return null;
  return (
    <div className="rounded-xl border bg-card p-4 space-y-3">
      <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
        Peers
      </h3>
      <div className="flex flex-wrap gap-2">
        {peers.map((peer) => (
          <Link
            key={peer}
            href={`/dashboard/stocks/${peer}`}
            className="inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold hover:bg-accent hover:text-accent-foreground transition-colors"
          >
            {peer}
          </Link>
        ))}
      </div>
    </div>
  );
}
