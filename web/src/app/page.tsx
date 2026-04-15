import Link from "next/link";
import Image from "next/image";

export default function HomePage() {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div className="text-center">
        <Image src="/logo.svg" alt="IG Central" width={80} height={80} className="mx-auto mb-6" />
        <h1 className="text-4xl md:text-5xl font-bold text-gray-900 mb-2">
          IG Central
        </h1>
        <p className="text-sm text-blue-600 font-medium tracking-wide uppercase mb-4">
          Information Governance Central
        </p>
        <p className="text-lg text-gray-600 mb-8 max-w-md mx-auto">
          Classify, protect, and manage your documents. Powered by AI (Actual Intelligence) supported by Large and Small Language Models (LLMs, SLMs).
        </p>
        <Link
          href="/login"
          className="inline-flex items-center px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-semibold"
        >
          Log In
        </Link>
      </div>
    </div>
  );
}
