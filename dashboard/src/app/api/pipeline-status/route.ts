import { NextResponse } from 'next/server';
import { readExportJson } from '@/lib/exports';

export const dynamic = 'force-dynamic';

export async function GET() {
  const data = readExportJson('pipeline_status.json', {
    status: 'UNKNOWN',
    total_raw_lines: 0,
    clean_records_count: 0,
    rejected_lines_count: 0,
    duplicate_lines_removed: 0,
    last_pipeline_run: 'Never'
  });

  return NextResponse.json(data);
}

