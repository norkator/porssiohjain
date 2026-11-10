import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { pages, siteUrl } from '../site/pages.mjs';
import { renderPage } from '../site/render.mjs';

const read = path => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

test('every indexable page has static content, a unique title and matching canonical and social URLs', () => {
    const titles = new Set();
    const descriptions = new Set();
    for (const page of pages) {
        const source = read(`${page.path.slice(1)}index.html`);
        const html = renderPage(source, `${page.path}index.html`);
        assert.doesNotMatch(html, /<!-- site:/);
        assert.equal((html.match(/<h1>/g) || []).length, 1);
        assert.match(html, /<html lang="en">/);
        assert.match(html, /<main[>\s]/);
        assert.ok(html.includes(`<h1>${page.heading}</h1>`));
        assert.ok(html.includes(`<link rel="canonical" href="${siteUrl}${page.path}"`));
        assert.ok(html.includes(`<meta property="og:url" content="${siteUrl}${page.path}"`));
        assert.match(html, /name="robots" content="index, follow, max-image-preview:large"/);
        assert.doesNotMatch(html, /noindex|name="keywords"/);
        assert.ok(page.body.split(/\s+/).length > 400, 'Each page must contain a useful guide or introduction before JavaScript runs');
        titles.add(page.title);
        descriptions.add(page.description);
        const graph = JSON.parse(html.match(/<script type="application\/ld\+json">([^]*?)<\/script>/)[1]);
        const app = graph['@graph'].find(item => item['@type'] === 'WebApplication');
        assert.equal(app.url, `${siteUrl}/`);
        assert.equal(app.offers.price, '0');
        assert.equal(app.isAccessibleForFree, true);
        assert.equal(app.aggregateRating, undefined, 'Never invent ratings to qualify for rich results');
        assert.equal(app.review, undefined);
        const webpage = graph['@graph'].find(item => item['@type'] === 'WebPage');
        assert.equal(webpage.url, `${siteUrl}${page.path}`);
        assert.equal(webpage.name, page.title);
        if (page.path !== '/') {
            assert.doesNotMatch(html, /<script type="module"/);
            assert.equal(graph['@graph'].find(item => item['@type'] === 'BreadcrumbList').itemListElement.at(-1).item, webpage.url);
        }
        for (const [, href] of html.matchAll(/href="(\/[^"#]*)(?:#[^"]*)?"/g)) {
            if (href === '/favicon.svg' || href === '/src/styles.css') continue;
            assert.ok(pages.some(item => item.path === href), `Broken internal link: ${href}`);
        }
    }
    assert.equal(titles.size, pages.length);
    assert.equal(descriptions.size, pages.length);
});

test('robots.txt permits crawling and the XML sitemap covers exactly the canonical pages', () => {
    const robots = read('public/robots.txt');
    assert.match(robots, /^User-agent: \*\nAllow: \/\n/m);
    assert.ok(robots.includes(`Sitemap: ${siteUrl}/sitemap.xml`));
    assert.doesNotMatch(robots, /Disallow:\s*\//);
    const sitemap = read('public/sitemap.xml');
    assert.match(sitemap, /^<\?xml version="1.0" encoding="UTF-8"\?>/);
    assert.match(sitemap, /xmlns="http:\/\/www.sitemaps.org\/schemas\/sitemap\/0.9"/);
    const urls = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map(match => match[1]);
    assert.deepEqual(urls.sort(), pages.map(page => siteUrl + page.path).sort());
    assert.doesNotMatch(sitemap, /<lastmod>/, 'Do not claim a made-up modification date on every build');
    const config = read('nginx.conf');
    assert.match(config, /try_files \$uri \$uri\/ =404;/);
    assert.doesNotMatch(config, /try_files[^;]*\/index\.html;/, 'Unknown routes must not become duplicate simulator pages');
});
