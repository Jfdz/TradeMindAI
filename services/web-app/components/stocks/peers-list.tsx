import Link from "next/link";

type Props = {
  readonly peers: string[];
};

const CARD_CHROME =
  "rounded-xl border border-cyan-500/30 bg-card shadow-[0_0_20px_rgba(6,182,212,0.08)]";

export function PeersList({ peers }: Props) {
  if (!peers.length) return null;
  return (
    <div className={`${CARD_CHROME} space-y-3 p-4`}>
      <h3 className="text-xs font-semibold uppercase tracking-wider text-cyan-400">
        Peers
      </h3>
      <div className="flex flex-wrap gap-2">
        {peers.map((peer) => (
          <Link
            key={peer}
            href={`/dashboard/stocks/${peer}`}
            className="inline-flex items-center rounded-full border border-cyan-500/35 bg-cyan-500/8 px-3 py-1 text-xs font-semibold text-cyan-100 transition-colors hover:border-cyan-400 hover:bg-cyan-400/15 hover:text-white"
          >
            {peer}
          </Link>
        ))}
      </div>
    </div>
  );
}
