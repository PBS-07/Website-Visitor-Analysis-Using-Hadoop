'use client';

import React, { useEffect, useState } from 'react';
import { AlertTriangle, ShieldAlert, CheckCircle2, ServerCrash, ArrowDownRight } from 'lucide-react';
import { ResponsiveContainer, BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, Cell } from 'recharts';

interface ErrorPayload {
  error_rate_percentage: number;
  total_errors: number;
  status_classes: { class: string; count: number }[];
  status_codes: { code: number; count: number }[];
  top_failing_urls: { url: string; count: number }[];
}

export default function ErrorAnalysisPage() {
  const [data, setData] = useState<ErrorPayload | null>(null);

  useEffect(() => {
    fetch('/api/errors')
      .then((res) => res.json())
      .then((json) => setData(json))
      .catch((err) => console.error(err));
  }, []);

  const errors = data || {
    error_rate_percentage: 12.56,
    total_errors: 30720,
    status_classes: [
      { class: '2xx', count: 205600 },
      { class: '3xx', count: 8219 },
      { class: '4xx', count: 22000 },
      { class: '5xx', count: 8720 },
    ],
    status_codes: [
      { code: 200, count: 205600 },
      { code: 301, count: 4100 },
      { code: 304, count: 4119 },
      { code: 400, count: 2450 },
      { code: 403, count: 2450 },
      { code: 404, count: 17100 },
      { code: 500, count: 7120 },
      { code: 502, count: 800 },
      { code: 503, count: 800 },
    ],
    top_failing_urls: [
      { url: '/admin/login.php [Status 404]', count: 6800 },
      { url: '/wp-login.php [Status 404]', count: 5400 },
      { url: '/.env [Status 404]', count: 3200 },
      { url: '/legacy/catalog-v1 [Status 500]', count: 2900 },
      { url: '/hidden/backup.zip [Status 403]', count: 1800 },
    ],
  };

  return (
    <div className="space-y-8 max-w-7xl mx-auto">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-2">
          <AlertTriangle className="w-6 h-6 text-amber-500" />
          HTTP Status & Error Analysis (FR-12, FR-29)
        </h2>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Aggregated by Hadoop StatusCodeJob. Identifies client 4xx errors, server 5xx faults, and malicious path probes.
        </p>
      </div>

      {/* Top Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-xs font-semibold uppercase">Overall Error Rate</span>
            <AlertTriangle className="w-4 h-4 text-amber-500" />
          </div>
          <div className="text-3xl font-bold text-amber-500">
            {errors.error_rate_percentage.toFixed(2)}%
          </div>
          <span className="text-xs text-slate-500 mt-1 block">Total 4xx + 5xx failures</span>
        </div>

        <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-xs font-semibold uppercase">Total Failing Requests</span>
            <ServerCrash className="w-4 h-4 text-rose-500" />
          </div>
          <div className="text-3xl font-bold text-slate-900 dark:text-white">
            {errors.total_errors.toLocaleString()}
          </div>
          <span className="text-xs text-slate-500 mt-1 block">Across all date partitions</span>
        </div>

        <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-xs font-semibold uppercase">Successful Requests (2xx)</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-500" />
          </div>
          <div className="text-3xl font-bold text-emerald-500">
            {errors.status_classes.find((c) => c.class === '2xx')?.count.toLocaleString() || '0'}
          </div>
          <span className="text-xs text-slate-500 mt-1 block">Healthy response delivery</span>
        </div>
      </div>

      {/* Status Codes Distribution Chart */}
      <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm">
        <h3 className="text-base font-bold text-slate-900 dark:text-white mb-2">
          HTTP Status Code Distribution
        </h3>
        <p className="text-xs text-slate-500 dark:text-slate-400 mb-6">
          Distribution across 200 (OK), 301/304 (Redirects), 400/403/404 (Client Errors), and 500/502/503 (Server Errors).
        </p>

        <div className="h-64 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={errors.status_codes} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#334155" opacity={0.3} />
              <XAxis dataKey="code" tick={{ fontSize: 11, fill: '#94A3B8' }} />
              <YAxis tick={{ fontSize: 11, fill: '#94A3B8' }} />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#0F172A',
                  borderColor: '#334155',
                  borderRadius: '12px',
                  fontSize: '12px',
                  color: '#fff',
                }}
              />
              <Bar dataKey="count" radius={[6, 6, 0, 0]}>
                {errors.status_codes.map((entry) => {
                  let color = '#10B981'; // 2xx green
                  if (entry.code >= 300 && entry.code < 400) color = '#3B82F6';
                  if (entry.code >= 400 && entry.code < 500) color = '#F59E0B';
                  if (entry.code >= 500) color = '#EF4444';
                  return <Cell key={`code-${entry.code}`} fill={color} />;
                })}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Top Failing URLs Table */}
      <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm">
        <h3 className="text-base font-bold text-slate-900 dark:text-white mb-2">
          Top Failing URLs (4xx &amp; 5xx)
        </h3>
        <p className="text-xs text-slate-500 dark:text-slate-400 mb-6">
          Identifies scanning activity, broken endpoints, and server application crashes.
        </p>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800 text-slate-400 uppercase tracking-wider font-semibold">
                <th className="pb-3">Rank</th>
                <th className="pb-3">Target URL &amp; Status</th>
                <th className="pb-3 text-right">Error Frequency</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800/60 font-medium">
              {errors.top_failing_urls.map((item, idx) => (
                <tr key={idx} className="hover:bg-slate-50 dark:hover:bg-slate-800/30">
                  <td className="py-3 text-slate-400 font-mono">#{idx + 1}</td>
                  <td className="py-3 font-mono text-rose-500 dark:text-rose-400">{item.url}</td>
                  <td className="py-3 text-right text-slate-900 dark:text-white font-semibold">
                    {item.count.toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

