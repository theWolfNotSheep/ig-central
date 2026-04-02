import Link from "next/link";

export default function HomePage() {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div className="text-center">
        <h1 className="text-4xl md:text-5xl font-bold text-gray-900 mb-4">
          Governance-Led Storage
        </h1>
        <p className="text-lg text-gray-600 mb-8 max-w-md mx-auto">
          Secure, governed data storage platform.
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
