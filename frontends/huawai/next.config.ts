import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'standalone',
  agentRules: false,

  images: {
    unoptimized: true,
    formats: ['image/avif', 'image/webp'],
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'img.mnnu.eu.org',
        port: '',
        pathname: '/**',
      },
      { protocol: 'https', hostname: 'imgwolong.com' },
      { protocol: 'http', hostname: 'imgwolong.com' },
      { protocol: 'https', hostname: '*.imgwolong.com' },
      { protocol: 'https', hostname: 'image.tmdb.org' },
      { protocol: 'https', hostname: 'm.media-amazon.com' },
      { protocol: 'https', hostname: 't.mwm.moe' },
      { protocol: 'https', hostname: 'img.liangzipic.com' },
      { protocol: 'https', hostname: 'img.ffzyimg.com' },
      { protocol: 'https', hostname: 'pic.rmb.bdstatic.com' },
      { protocol: 'https', hostname: 'img.kuaikanzy.net' },
      { protocol: 'https', hostname: 'img1.doubanio.com' },
      { protocol: 'https', hostname: 'img2.doubanio.com' },
      { protocol: 'https', hostname: 'img3.doubanio.com' },
      { protocol: 'https', hostname: 'img9.doubanio.com' },
    ],
    deviceSizes: [640, 750, 1080, 1200, 1920],
    minimumCacheTTL: 3600 * 24,
  },

  allowedDevOrigins: [
    "localhost:*",
    "127.0.0.1:*",
    "192.168.*.*"
  ],

};

export default nextConfig;
