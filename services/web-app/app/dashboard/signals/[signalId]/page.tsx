import { SignalDetailClient } from "@/components/dashboard/signal-detail-client";

type SignalDetailPageProps = {
  params: Promise<{
    signalId: string;
  }>;
};

export default async function SignalDetailPage({ params }: SignalDetailPageProps) {
  const { signalId } = await params;
  return <SignalDetailClient signalId={signalId} />;
}
