import { NextResponse } from 'next/server';
import { readExportJson } from '@/lib/exports';

export const dynamic = 'force-dynamic';

export async function GET() {
  const data = readExportJson('errors.json', {
    error_rate_percentage: 0.0,
    total_errors: 0,
    status_classes: [],
    status_codes: [],
    top_failing_urls: []
  });

  return NextResponse.json(data);
}

