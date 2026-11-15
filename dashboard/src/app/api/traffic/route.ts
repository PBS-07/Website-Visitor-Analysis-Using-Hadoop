import { NextResponse } from 'next/server';
import { readExportJson } from '@/lib/exports';

export const dynamic = 'force-dynamic';

export async function GET() {
  const data = readExportJson('traffic_over_time.json', {
    total_traffic: 0,
    hourly: [],
    daily: []
  });

  return NextResponse.json(data);
}

