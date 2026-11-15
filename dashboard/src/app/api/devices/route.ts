import { NextResponse } from 'next/server';
import { readExportJson } from '@/lib/exports';

export const dynamic = 'force-dynamic';

export async function GET() {
  const data = readExportJson('devices.json', {
    device_types: [],
    browsers: [],
    os: []
  });

  return NextResponse.json(data);
}

