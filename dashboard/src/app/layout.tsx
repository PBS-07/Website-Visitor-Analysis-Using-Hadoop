import type { Metadata } from 'next';
import './globals.css';
import Link from 'next/link';
import { Activity, BarChart3, AlertTriangle, Database, Moon, Sun, Layers } from 'lucide-react';
import React from 'react';

export const metadata: Metadata = {
  title: 'Website Visitor Analysis | Hadoop & Hive',
  description: 'Scalable big-data website visitor analytics dashboard powered by Hadoop MapReduce & Hive',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="dark">
      <body className="min-h-screen bg-slate-50 dark:bg-[#0B0F19] text-slate-900 dark:text-slate-100 antialiased flex flex-col md:flex-row">
        {/* Sidebar */}
        <aside className="w-full md:w-64 border-b md:border-b-0 md:border-r border-slate-200 dark:border-slate-800 bg-white/70 dark:bg-slate-900/70 backdrop-blur-xl p-4 flex flex-col justify-between shrink-0">
          <div>
            {/* Logo */}
            <div className="flex items-center gap-3 px-2 py-3 mb-6">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-500 to-indigo-700 flex items-center justify-center text-white shadow-lg shadow-indigo-500/30">
                <Layers className="w-5 h-5" />
              </div>
              <div>
                <h1 className="font-bold text-sm tracking-tight leading-none text-slate-900 dark:text-white">
                  Visitor Analysis
                </h1>
                <span className="text-[10px] font-semibold tracking-wider uppercase text-indigo-600 dark:text-indigo-400">
                  Hadoop 3.3 &bull; Hive
                </span>
              </div>
            </div>

            {/* Navigation links */}
            <nav className="space-y-1 text-sm font-medium">
              <Link
                href="/"
                className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/60 hover:text-indigo-600 dark:hover:text-indigo-400 transition"
              >
                <BarChart3 className="w-4 h-4" />
                <span>Overview & KPIs</span>
              </Link>
              <Link
                href="/errors"
                className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/60 hover:text-indigo-600 dark:hover:text-indigo-400 transition"
              >
                <AlertTriangle className="w-4 h-4" />
                <span>Error Analysis</span>
              </Link>
              <Link
                href="/status"
                className="flex items-center gap-3 px-3 py-2.5 rounded-lg text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/60 hover:text-indigo-600 dark:hover:text-indigo-400 transition"
              >
                <Database className="w-4 h-4" />
                <span>Pipeline & Cluster</span>
              </Link>
            </nav>
          </div>

          {/* Cluster Status badge in sidebar */}
          <div className="mt-6 pt-4 border-t border-slate-200 dark:border-slate-800/80 px-2">
            <div className="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400 mb-2">
              <span className="flex items-center gap-1.5 font-medium">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                Pseudo-Cluster
              </span>
              <span className="text-[10px] font-mono bg-emerald-500/10 text-emerald-400 px-1.5 py-0.5 rounded border border-emerald-500/20">
                HEALTHY
              </span>
            </div>
            <p className="text-[11px] text-slate-400 dark:text-slate-500 leading-relaxed">
              HDFS, YARN & Hive Metastore running locally via Docker.
            </p>
          </div>
        </aside>

        {/* Main Content Area */}
        <main className="flex-1 flex flex-col min-w-0">
          {/* Topbar */}
          <header className="h-16 border-b border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-md px-6 flex items-center justify-between sticky top-0 z-30">
            <div className="flex items-center gap-2">
              <span className="text-xs font-medium text-slate-500 dark:text-slate-400">Environment:</span>
              <span className="text-xs font-semibold px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700">
                Single-Node HDFS/YARN
              </span>
            </div>

            <div className="flex items-center gap-3">
              <a
                href="http://localhost:9870"
                target="_blank"
                rel="noreferrer"
                className="text-xs font-medium text-slate-600 dark:text-slate-300 hover:text-indigo-600 dark:hover:text-indigo-400 px-2.5 py-1 rounded-md border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50"
              >
                NameNode UI :9870
              </a>
              <a
                href="http://localhost:8088"
                target="_blank"
                rel="noreferrer"
                className="text-xs font-medium text-slate-600 dark:text-slate-300 hover:text-indigo-600 dark:hover:text-indigo-400 px-2.5 py-1 rounded-md border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50"
              >
                YARN RM UI :8088
              </a>
            </div>
          </header>

          {/* Page Body */}
          <div className="p-6 md:p-8 flex-1 overflow-y-auto">
            {children}
          </div>
        </main>
      </body>
    </html>
  );
}

