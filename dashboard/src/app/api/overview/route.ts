import { NextResponse } from 'next/server';
import { readExportJson } from '@/lib/exports';

export const dynamic = 'force-dynamic';

export async function GET() {
  const data = readExportJson('overview_kpi.json', {
    generated_at: new Date().toISOString(),
    kpis: {
      total_hits: 0,
      unique_visitors: 0,
      error_rate_percentage: 0.0,
      avg_session_duration_seconds: 0.0,
      bounce_rate_percentage: 0.0,
      total_sessions: 0,
      avg_pages_per_session: 0.0
    },
    pipeline_health: {
      status: 'UNKNOWN',
      total_raw_lines: 0,
      clean_records_count: 0,
      rejected_lines_count: 0,
      duplicate_lines_removed: 0,
      last_pipeline_run: 'Never'
    }
  });

  return NextResponse.json(data);
}

