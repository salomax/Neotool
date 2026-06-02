/** @type {import('next-sitemap').IConfig} */
const siteUrl = process.env.SITE_URL || 'http://localhost:3000';
module.exports = {
  siteUrl,
  generateRobotsTxt: true,
  exclude: ['/api/*','/og'],
  transform: async (config, path) => ({
    loc: path,
    changefreq: 'weekly',
    priority: path === '/' ? 1.0 : 0.7,
    lastmod: new Date().toISOString(),
    alternateRefs: [],
  }),
};
