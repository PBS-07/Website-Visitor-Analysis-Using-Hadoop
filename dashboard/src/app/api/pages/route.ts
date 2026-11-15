import { NextResponse } from 'next/server';
import { readExportJson } from '@/lib/exports';

export const dynamic = 'force-dynamic';

export async function GET() {
  const data = readExportJson('top_pages.json', {
    total_page_views: 0,
    pages: []
  });

  return NextResponse.json(data);
}

