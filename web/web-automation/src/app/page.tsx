import { Suspense } from "react";
import { RunConsole } from "@/components/RunConsole";

export const dynamic = "force-dynamic";

export default function Home() {
  return (
    <div className="flex flex-1 flex-col px-3 py-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:px-6 sm:py-8 lg:px-8">
      <Suspense fallback={<p className="text-sm text-slate-500">Loading console…</p>}>
        <RunConsole />
      </Suspense>
    </div>
  );
}
