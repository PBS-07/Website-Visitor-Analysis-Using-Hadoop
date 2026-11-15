import { NextResponse } from 'next/server';
import { readExportJson } from '@/lib/exports';

export const dynamic = 'force-dynamic';

export async function GET() {
  const data = readExportJson('countries.json', {
    countries: []
  });

  return NextResponse.json(data);
}

