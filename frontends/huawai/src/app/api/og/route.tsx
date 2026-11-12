import { NextRequest } from 'next/server';

export const runtime = 'nodejs';

function escapeXml(value: string) {
    return value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&apos;');
}

function normalizeText(value: string | null, fallback: string, maxLength: number) {
    const text = (value || fallback).replace(/\s+/g, ' ').trim();
    return escapeXml(text.length > maxLength ? `${text.slice(0, maxLength - 1)}...` : text);
}

function buildOgSvg(req: NextRequest) {
    const { searchParams } = new URL(req.url);
    const title = normalizeText(searchParams.get('title'), '画外 Huawai', 34);
    const rating = normalizeText(searchParams.get('rating'), '', 12);
    const year = normalizeText(searchParams.get('year'), '', 12);
    const category = normalizeText(searchParams.get('category'), 'Movie', 18);
    const titleSize = title.length > 18 ? 58 : 76;
    const ratingBadge = rating
        ? `<rect x="548" y="434" width="156" height="48" rx="14" fill="rgba(245,158,11,0.18)" stroke="rgba(245,158,11,0.4)"/>
           <text x="626" y="466" text-anchor="middle" font-size="24" font-weight="800" fill="#fbbf24">Rating ${rating}</text>`
        : '';

    return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630" role="img" aria-label="${title}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#030305"/>
      <stop offset="45%" stop-color="#111827"/>
      <stop offset="100%" stop-color="#18181b"/>
    </linearGradient>
    <linearGradient id="title" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#ffffff"/>
      <stop offset="100%" stop-color="#a1a1aa"/>
    </linearGradient>
    <filter id="glow" x="-30%" y="-30%" width="160%" height="160%">
      <feGaussianBlur stdDeviation="26" result="blur"/>
      <feColorMatrix in="blur" type="matrix" values="0 0 0 0 0.39 0 0 0 0 0.4 0 0 0 0 0.95 0 0 0 .35 0"/>
      <feBlend in="SourceGraphic"/>
    </filter>
  </defs>
  <rect width="1200" height="630" fill="url(#bg)"/>
  <circle cx="196" cy="126" r="220" fill="#4f46e5" opacity=".22" filter="url(#glow)"/>
  <circle cx="980" cy="520" r="260" fill="#0f766e" opacity=".18" filter="url(#glow)"/>
  <rect x="54" y="54" width="1092" height="522" rx="34" fill="rgba(255,255,255,0.045)" stroke="rgba(255,255,255,0.12)"/>
  <rect x="94" y="96" width="330" height="438" rx="28" fill="rgba(255,255,255,0.08)" stroke="rgba(255,255,255,0.12)"/>
  <text x="259" y="304" text-anchor="middle" font-size="118" font-weight="900" fill="#ffffff">画</text>
  <text x="259" y="360" text-anchor="middle" font-size="32" font-weight="800" letter-spacing="7" fill="#a1a1aa">HUAWAI</text>
  <text x="508" y="146" font-size="28" font-weight="800" letter-spacing="5" fill="#a1a1aa">HUAWAI ORIGINAL</text>
  <text x="508" y="284" font-size="${titleSize}" font-weight="900" fill="url(#title)">
    <tspan x="508" dy="0">${title}</tspan>
  </text>
  ${ratingBadge}
  <rect x="724" y="434" width="132" height="48" rx="14" fill="rgba(99,102,241,0.18)" stroke="rgba(99,102,241,0.4)"/>
  <text x="790" y="466" text-anchor="middle" font-size="24" font-weight="800" fill="#a5b4fc">4K HDR</text>
  <text x="884" y="466" font-size="24" font-weight="700" fill="#a1a1aa">${year}</text>
  <text x="884" y="510" font-size="22" font-weight="700" fill="#71717a">${category}</text>
  <rect x="508" y="518" width="174" height="56" rx="28" fill="#ffffff"/>
  <text x="595" y="554" text-anchor="middle" font-size="25" font-weight="900" fill="#050505">立即观看</text>
</svg>`;
}

export async function GET(req: NextRequest) {
    const svg = buildOgSvg(req);

    return new Response(svg, {
        headers: {
            'Content-Type': 'image/svg+xml; charset=utf-8',
            'Cache-Control': 'public, max-age=300, s-maxage=3600, stale-while-revalidate=3600',
        },
    });
}
