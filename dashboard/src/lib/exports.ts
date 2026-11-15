import fs from 'fs';
import path from 'path';

export function readExportJson<T>(filename: string, fallback: T): T {
  try {
    // Check multiple potential paths
    const candidates = [
      path.resolve(process.cwd(), 'exports', filename),
      path.resolve(process.cwd(), '..', 'exports', filename),
      path.resolve(__dirname, '../../../../exports', filename),
    ];

    for (const filePath of candidates) {
      if (fs.existsSync(filePath)) {
        const raw = fs.readFileSync(filePath, 'utf-8');
        return JSON.parse(raw) as T;
      }
    }
  } catch (err) {
    console.error(`Error reading export file ${filename}:`, err);
  }
  return fallback;
}

