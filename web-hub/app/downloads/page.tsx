"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, isAuthenticated } from "@/lib/api";
import {
  Download,
  Loader2,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";

interface PackDownloadRecord {
  id: string;
  packId: string;
  versionNumber: number;
  apiKeyPrefix: string;
  tenantName: string;
  downloadedAt: string;
  componentsDownloaded: string[];
}

interface PagedResponse {
  content: PackDownloadRecord[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

function formatType(type: string): string {
  return type
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export default function DownloadsPage() {
  const router = useRouter();
  const [downloads, setDownloads] = useState<PackDownloadRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  useEffect(() => {
    if (!isAuthenticated()) {
      router.push("/");
      return;
    }
    loadDownloads();
  }, [page, router]);

  async function loadDownloads() {
    setLoading(true);
    try {
      const data = await api.get<PagedResponse>(
        `/api/hub/admin/downloads?page=${page}&size=20&sort=downloadedAt,desc`
      );
      if (data && data.content) {
        setDownloads(data.content);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
      } else if (Array.isArray(data)) {
        // Fallback if the API returns a plain array
        setDownloads(data as unknown as PackDownloadRecord[]);
        setTotalPages(1);
        setTotalElements((data as unknown as PackDownloadRecord[]).length);
      } else {
        setDownloads([]);
        setTotalPages(0);
        setTotalElements(0);
      }
    } catch {
      setDownloads([]);
    } finally {
      setLoading(false);
    }
  }

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
      </div>
    );
  }

  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">
          Download Audit Log
        </h1>
        <p className="mt-1 text-sm text-gray-500">
          Track all pack downloads by tenants
          {totalElements > 0 && (
            <span className="ml-1">
              &middot; {totalElements.toLocaleString()} total records
            </span>
          )}
        </p>
      </div>

      {downloads.length === 0 ? (
        <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
          <Download className="mx-auto h-10 w-10 text-gray-300" />
          <p className="mt-3 text-sm font-medium text-gray-500">
            No downloads recorded yet
          </p>
          <p className="mt-1 text-sm text-gray-400">
            Downloads will appear here as tenants fetch governance packs.
          </p>
        </div>
      ) : (
        <>
          <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                    Pack
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                    Version
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                    Tenant
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                    Components
                  </th>
                  <th className="px-5 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                    Downloaded
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {downloads.map((dl) => (
                  <tr key={dl.id} className="hover:bg-gray-50">
                    <td className="px-5 py-3.5 text-sm text-gray-900">
                      {dl.packId}
                    </td>
                    <td className="px-5 py-3.5">
                      <span className="inline-flex rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700">
                        v{dl.versionNumber}
                      </span>
                    </td>
                    <td className="px-5 py-3.5">
                      <p className="text-sm font-medium text-gray-900">
                        {dl.tenantName}
                      </p>
                      <p className="font-mono text-xs text-gray-500">
                        {dl.apiKeyPrefix}...
                      </p>
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex flex-wrap gap-1">
                        {(dl.componentsDownloaded || []).map((c, i) => (
                          <span
                            key={i}
                            className="inline-flex rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-medium text-gray-600"
                          >
                            {formatType(c)}
                          </span>
                        ))}
                      </div>
                    </td>
                    <td className="px-5 py-3.5 text-sm text-gray-500">
                      {dl.downloadedAt
                        ? new Date(dl.downloadedAt).toLocaleString(
                            "en-GB",
                            {
                              day: "numeric",
                              month: "short",
                              year: "numeric",
                              hour: "2-digit",
                              minute: "2-digit",
                            }
                          )
                        : "--"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between">
              <p className="text-sm text-gray-500">
                Page {page + 1} of {totalPages}
              </p>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setPage(Math.max(0, page - 1))}
                  disabled={page === 0}
                  className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-40"
                >
                  <ChevronLeft className="h-4 w-4" />
                  Previous
                </button>
                <button
                  onClick={() =>
                    setPage(Math.min(totalPages - 1, page + 1))
                  }
                  disabled={page >= totalPages - 1}
                  className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-40"
                >
                  Next
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
