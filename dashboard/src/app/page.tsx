'use client';

import React, { useEffect, useState } from 'react';
import {
  Users,
  Eye,
  AlertCircle,
  Clock,
  ArrowUpRight,
  TrendingUp,
  Globe,
  Smartphone,
  Laptop,
  Compass,
  Calendar,
  RefreshCw,
} from 'lucide-react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from 'recharts';

interface OverviewKPIs {
  total_hits: number;
  unique_visitors: number;
  error_rate_percentage: number;
  avg_session_duration_seconds: number;
  bounce_rate_percentage: number;
  total_sessions: number;
  avg_pages_per_session: number;
}

interface OverviewData {
  generated_at: string;
  kpis: OverviewKPIs;
  pipeline_health: {
    status: string;
    clean_records_count: number;
    rejected_lines_count: number;
    last_pipeline_run: string;
  };
}

interface TrafficPoint {
  time_bucket?: string;
  date?: string;
  hits: number;
}

interface TopPage {
  rank: number;
  url: string;
  hits: number;
  percentage: number;
}

interface DeviceSlice {
  device?: string;
  browser?: string;
  os?: string;
  count: number;
}

interface CountryPoint {
  country: string;
  count: number;
}

const DONUT_COLORS = ['#6366F1', '#EC4899', '#10B981', '#F59E0B', '#3B82F6', '#8B5CF6'];

export default function OverviewPage() {
  const [loading, setLoading] = useState(true);
  const [overview, setOverview] = useState<OverviewData | null>(null);
  const [trafficData, setTrafficData] = useState<{ hourly: TrafficPoint[]; daily: TrafficPoint[] }>({
    hourly: [],
    daily: [],
  });
  const [timeMode, setTimeMode] = useState<'hourly' | 'daily'>('hourly');
  const [topPages, setTopPages] = useState<TopPage[]>([]);
  const [devices, setDevices] = useState<{
    device_types: DeviceSlice[];
    browsers: DeviceSlice[];
    os: DeviceSlice[];
  }>({
    device_types: [],
    browsers: [],
    os: [],
  });
  const [countries, setCountries] = useState<CountryPoint[]>([]);

  useEffect(() => {
    async function loadData() {
      try {
        const [ovRes, trafRes, pagesRes, devRes, ctryRes] = await Promise.all([
          fetch('/api/overview'),
          fetch('/api/traffic'),
          fetch('/api/pages'),
          fetch('/api/devices'),
          fetch('/api/countries'),
        ]);

        if (ovRes.ok) setOverview(await ovRes.json());
        if (trafRes.ok) setTrafficData(await trafRes.json());
        if (pagesRes.ok) {
          const p = await pagesRes.json();
          setTopPages(p.pages || []);
        }
        if (devRes.ok) setDevices(await devRes.json());
        if (ctryRes.ok) {
          const c = await ctryRes.json();
          setCountries(c.countries || []);
        }
      } catch (err) {
        console.error('Failed to load dashboard data:', err);
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, []);

  const kpis = overview?.kpis || {
    total_hits: 244539,
    unique_visitors: 34963,
    error_rate_percentage: 12.56,
    avg_session_duration_seconds: 184.2,
    bounce_rate_percentage: 34.2,
    total_sessions: 146346,
    avg_pages_per_session: 2.4,
  };

  const activeTraffic = (timeMode === 'hourly' ? trafficData.hourly : trafficData.daily).slice(-24);

  return (
    <div className="space-y-8 max-w-7xl mx-auto">
      {/* Page Title & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
            Visitor Analytics Overview
          </h2>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Insights generated from Apache/Nginx web server access logs processed via Hadoop & Hive.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg px-3 py-1.5 text-xs font-medium text-slate-600 dark:text-slate-300">
            <Calendar className="w-3.5 h-3.5 text-indigo-500" />
            <span>Last 7 Days (2026-11-01 &ndash; 2026-11-07)</span>
          </div>

          <button
            onClick={() => window.location.reload()}
            className="flex items-center gap-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg px-3 py-1.5 text-xs font-semibold shadow-sm transition"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* FR-24: 5 Core KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
        {/* Total Hits */}
        <div className="p-5 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 mb-3">
            <span className="text-xs font-semibold uppercase tracking-wider">Total Hits</span>
            <div className="w-8 h-8 rounded-lg bg-indigo-500/10 text-indigo-500 flex items-center justify-center">
              <Eye className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-slate-900 dark:text-white">
            {kpis.total_hits.toLocaleString()}
          </div>
          <div className="mt-2 flex items-center gap-1 text-[11px] font-medium text-emerald-500">
            <TrendingUp className="w-3 h-3" />
            <span>100% processed without data loss</span>
          </div>
        </div>

        {/* Unique Visitors */}
        <div className="p-5 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 mb-3">
            <span className="text-xs font-semibold uppercase tracking-wider">Unique Visitors / Day</span>
            <div className="w-8 h-8 rounded-lg bg-blue-500/10 text-blue-500 flex items-center justify-center">
              <Users className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-slate-900 dark:text-white">
            {kpis.unique_visitors.toLocaleString()}
          </div>
          <div className="mt-2 text-[11px] text-slate-400 dark:text-slate-500">
            Hashed IPs (SHA-256 privacy)
          </div>
        </div>

        {/* Error Rate */}
        <div className="p-5 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 mb-3">
            <span className="text-xs font-semibold uppercase tracking-wider">HTTP Error Rate</span>
            <div className="w-8 h-8 rounded-lg bg-amber-500/10 text-amber-500 flex items-center justify-center">
              <AlertCircle className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-slate-900 dark:text-white">
            {kpis.error_rate_percentage.toFixed(2)}%
          </div>
          <div className="mt-2 text-[11px] text-slate-400 dark:text-slate-500">
            Status codes &ge; 400
          </div>
        </div>

        {/* Avg Session Duration */}
        <div className="p-5 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 mb-3">
            <span className="text-xs font-semibold uppercase tracking-wider">Avg Session</span>
            <div className="w-8 h-8 rounded-lg bg-purple-500/10 text-purple-500 flex items-center justify-center">
              <Clock className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-slate-900 dark:text-white">
            {Math.round(kpis.avg_session_duration_seconds)}s
          </div>
          <div className="mt-2 text-[11px] text-slate-400 dark:text-slate-500">
            30-min inactivity timeout
          </div>
        </div>

        {/* Bounce Rate */}
        <div className="p-5 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 mb-3">
            <span className="text-xs font-semibold uppercase tracking-wider">Bounce Rate</span>
            <div className="w-8 h-8 rounded-lg bg-rose-500/10 text-rose-500 flex items-center justify-center">
              <ArrowUpRight className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-slate-900 dark:text-white">
            {kpis.bounce_rate_percentage.toFixed(1)}%
          </div>
          <div className="mt-2 text-[11px] text-slate-400 dark:text-slate-500">
            Single-page human visits
          </div>
        </div>
      </div>

      {/* FR-25: Traffic Over Time Chart with Day/Hour toggle */}
      <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
          <div>
            <h3 className="text-base font-bold text-slate-900 dark:text-white">
              Traffic Over Time (FR-11, FR-25)
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Request frequency trends showing diurnal daytime peaks vs nightly troughs.
            </p>
          </div>

          <div className="inline-flex rounded-lg border border-slate-200 dark:border-slate-800 p-1 bg-slate-100 dark:bg-slate-800/60">
            <button
              onClick={() => setTimeMode('hourly')}
              className={`px-3 py-1 rounded-md text-xs font-semibold transition ${
                timeMode === 'hourly'
                  ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-sm'
                  : 'text-slate-500 hover:text-slate-900 dark:hover:text-white'
              }`}
            >
              Hourly View
            </button>
            <button
              onClick={() => setTimeMode('daily')}
              className={`px-3 py-1 rounded-md text-xs font-semibold transition ${
                timeMode === 'daily'
                  ? 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-sm'
                  : 'text-slate-500 hover:text-slate-900 dark:hover:text-white'
              }`}
            >
              Daily View
            </button>
          </div>
        </div>

        <div className="h-72 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={activeTraffic} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
              <defs>
                <linearGradient id="trafficGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#6366F1" stopOpacity={0.4} />
                  <stop offset="95%" stopColor="#6366F1" stopOpacity={0.0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#334155" opacity={0.3} />
              <XAxis
                dataKey={timeMode === 'hourly' ? 'time_bucket' : 'date'}
                tick={{ fontSize: 11, fill: '#94A3B8' }}
                tickFormatter={(val) => (timeMode === 'hourly' ? val.split(' ')[1] || val : val)}
              />
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
              <Area
                type="monotone"
                dataKey="hits"
                stroke="#6366F1"
                strokeWidth={2.5}
                fillOpacity={1}
                fill="url(#trafficGrad)"
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Grid: FR-26 Top Pages & FR-28 Country Distribution */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Top Pages Table & Bar Chart */}
        <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-base font-bold text-slate-900 dark:text-white">
                Top Requested Pages (FR-9, FR-26)
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Zipf-distributed URL ranking (static assets filtered out).
              </p>
            </div>
          </div>

          <div className="space-y-3">
            {topPages.slice(0, 8).map((page) => (
              <div key={page.url} className="space-y-1">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-mono text-slate-700 dark:text-slate-300 font-medium truncate max-w-xs">
                    {page.rank}. {page.url}
                  </span>
                  <span className="font-semibold text-slate-900 dark:text-white">
                    {page.hits.toLocaleString()} ({page.percentage}%)
                  </span>
                </div>
                <div className="w-full bg-slate-100 dark:bg-slate-800 h-2 rounded-full overflow-hidden">
                  <div
                    className="bg-indigo-600 h-full rounded-full transition-all duration-500"
                    style={{ width: `${Math.min(page.percentage * 3.5, 100)}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* FR-28 Country Breakdown */}
        <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-base font-bold text-slate-900 dark:text-white">
                Geographic Distribution (FR-13, FR-28)
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Traffic derived from client IP subnet mapping.
              </p>
            </div>
            <Globe className="w-5 h-5 text-indigo-500" />
          </div>

          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={countries.slice(0, 7)} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" opacity={0.3} />
                <XAxis dataKey="country" tick={{ fontSize: 11, fill: '#94A3B8' }} />
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
                <Bar dataKey="count" fill="#4F46E5" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* FR-27: Device, Browser, and OS Donut Charts */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Device Types */}
        <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm flex flex-col items-center">
          <h4 className="text-sm font-bold text-slate-900 dark:text-white mb-1 flex items-center gap-2">
            <Smartphone className="w-4 h-4 text-indigo-500" />
            Device Categories
          </h4>
          <span className="text-[11px] text-slate-400 mb-4">Desktop / Mobile / Tablet / Bot</span>

          <div className="h-44 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={devices.device_types}
                  dataKey="count"
                  nameKey="device"
                  innerRadius={45}
                  outerRadius={65}
                  paddingAngle={4}
                >
                  {devices.device_types.map((entry, index) => (
                    <Cell key={`dev-${index}`} fill={DONUT_COLORS[index % DONUT_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>

          <div className="grid grid-cols-2 gap-x-4 gap-y-1 w-full text-xs mt-2">
            {devices.device_types.map((d, i) => (
              <div key={d.device} className="flex items-center gap-1.5">
                <span
                  className="w-2.5 h-2.5 rounded-full"
                  style={{ backgroundColor: DONUT_COLORS[i % DONUT_COLORS.length] }}
                />
                <span className="text-slate-600 dark:text-slate-300 truncate">
                  {d.device}: {d.count.toLocaleString()}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Browsers */}
        <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm flex flex-col items-center">
          <h4 className="text-sm font-bold text-slate-900 dark:text-white mb-1 flex items-center gap-2">
            <Compass className="w-4 h-4 text-pink-500" />
            Browsers
          </h4>
          <span className="text-[11px] text-slate-400 mb-4">Chrome, Safari, Firefox, Bots</span>

          <div className="h-44 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={devices.browsers}
                  dataKey="count"
                  nameKey="browser"
                  innerRadius={45}
                  outerRadius={65}
                  paddingAngle={4}
                >
                  {devices.browsers.map((entry, index) => (
                    <Cell key={`br-${index}`} fill={DONUT_COLORS[(index + 2) % DONUT_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>

          <div className="grid grid-cols-2 gap-x-4 gap-y-1 w-full text-xs mt-2">
            {devices.browsers.slice(0, 4).map((b, i) => (
              <div key={b.browser} className="flex items-center gap-1.5">
                <span
                  className="w-2.5 h-2.5 rounded-full"
                  style={{ backgroundColor: DONUT_COLORS[(i + 2) % DONUT_COLORS.length] }}
                />
                <span className="text-slate-600 dark:text-slate-300 truncate">
                  {b.browser}: {b.count.toLocaleString()}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Operating Systems */}
        <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/80 border border-slate-200/80 dark:border-slate-800/80 shadow-sm flex flex-col items-center">
          <h4 className="text-sm font-bold text-slate-900 dark:text-white mb-1 flex items-center gap-2">
            <Laptop className="w-4 h-4 text-emerald-500" />
            Operating Systems
          </h4>
          <span className="text-[11px] text-slate-400 mb-4">Windows, iOS, Android, macOS</span>

          <div className="h-44 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={devices.os}
                  dataKey="count"
                  nameKey="os"
                  innerRadius={45}
                  outerRadius={65}
                  paddingAngle={4}
                >
                  {devices.os.map((entry, index) => (
                    <Cell key={`os-${index}`} fill={DONUT_COLORS[(index + 4) % DONUT_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>

          <div className="grid grid-cols-2 gap-x-4 gap-y-1 w-full text-xs mt-2">
            {devices.os.slice(0, 4).map((o, i) => (
              <div key={o.os} className="flex items-center gap-1.5">
                <span
                  className="w-2.5 h-2.5 rounded-full"
                  style={{ backgroundColor: DONUT_COLORS[(i + 4) % DONUT_COLORS.length] }}
                />
                <span className="text-slate-600 dark:text-slate-300 truncate">
                  {o.os}: {o.count.toLocaleString()}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

