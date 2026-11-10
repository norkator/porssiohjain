import { pages, siteUrl } from './pages.mjs';

const escape = value => value.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const preview = `${siteUrl}/images/nuclear-power-control-room.png`;

export function renderHead(page) {
    const url = siteUrl + page.path;
    const home = page.path === '/';
    const graph = [
        { '@type': 'WebSite', '@id': `${siteUrl}/#website`, url: `${siteUrl}/`, name: 'Nuclear Power', alternateName: 'Pörssiohjain Nuclear Power', inLanguage: 'en' },
        {
            '@type': 'WebApplication', '@id': `${siteUrl}/#app`, name: 'Nuclear Power', url: `${siteUrl}/`,
            description: pages[0].description, applicationCategory: 'EntertainmentApplication',
            operatingSystem: 'Any', browserRequirements: 'JavaScript enabled; WebGL 2 optional',
            isAccessibleForFree: true, inLanguage: 'en', image: preview, screenshot: preview,
            offers: { '@type': 'Offer', price: '0', priceCurrency: 'EUR', url: `${siteUrl}/` },
            featureList: ['Interactive 3D reactor and SVG schematic', 'Individual control rods', 'Two coolant loops', 'Automatic and manual regulation', 'Equipment faults and maintenance', 'Backup diesel power', 'Fuel handling and spent-fuel pool'],
        },
        {
            '@type': 'WebPage', '@id': `${url}#webpage`, url, name: page.title, description: page.description,
            inLanguage: 'en', isPartOf: { '@id': `${siteUrl}/#website` }, about: { '@id': `${siteUrl}/#app` },
            primaryImageOfPage: { '@type': 'ImageObject', url: preview, width: 1200, height: 630 },
            ...(home ? { mainEntity: { '@id': `${siteUrl}/#app` } } : { breadcrumb: { '@id': `${url}#breadcrumb` } }),
        },
    ];
    if (!home) graph.push({
        '@type': 'BreadcrumbList', '@id': `${url}#breadcrumb`, itemListElement: [
            { '@type': 'ListItem', position: 1, name: 'Nuclear Power simulator', item: `${siteUrl}/` },
            { '@type': 'ListItem', position: 2, name: page.heading, item: url },
        ],
    });

    return `<title>${escape(page.title)}</title>
    <meta name="description" content="${escape(page.description)}" />
    <meta name="robots" content="index, follow, max-image-preview:large" />
    <link rel="canonical" href="${url}" />
    <meta property="og:type" content="website" />
    <meta property="og:site_name" content="Nuclear Power" />
    <meta property="og:locale" content="en_US" />
    <meta property="og:url" content="${url}" />
    <meta property="og:title" content="${escape(page.title)}" />
    <meta property="og:description" content="${escape(page.description)}" />
    <meta property="og:image" content="${preview}" />
    <meta property="og:image:secure_url" content="${preview}" />
    <meta property="og:image:type" content="image/png" />
    <meta property="og:image:width" content="1200" />
    <meta property="og:image:height" content="630" />
    <meta property="og:image:alt" content="Nuclear Power simulator with a 3D reactor, cooling circuits, instruments and operator controls" />
    <meta name="twitter:card" content="summary_large_image" />
    <meta name="twitter:title" content="${escape(page.title)}" />
    <meta name="twitter:description" content="${escape(page.description)}" />
    <meta name="twitter:image" content="${preview}" />
    <meta name="twitter:image:alt" content="Nuclear Power reactor control room" />
    <script type="application/ld+json">${JSON.stringify({ '@context': 'https://schema.org', '@graph': graph }).replaceAll('<', '\\u003c')}</script>`;
}

const footer = `<footer class="site-footer"><p>Nuclear Power by <a href="https://porssiohjain.fi/">Pörssiohjain</a>. A fictional browser simulation. All signals and commands stay local to the page session.</p></footer>`;

export function renderContent(page) {
    if (page.path === '/') return `<article id="about" class="site-content" aria-label="About the nuclear reactor simulator">${page.body}${footer}</article>`;
    return `<article class="site-content site-guide">
        <nav class="site-breadcrumb" aria-label="Breadcrumb"><a href="/">Nuclear Power simulator</a><span aria-hidden="true">/</span><span>${escape(page.heading)}</span></nav>
        <p class="site-eyebrow">NUCLEAR POWER · CONTROL-ROOM GUIDE</p>
        <h1>${escape(page.heading)}</h1>
        <a class="site-button" href="/">Open the simulator</a>
        <p class="site-note">Already running a plant in another tab? Keep that tab open: reloading the simulator creates a new session.</p>
        ${page.body}
        <nav class="site-guide-links" aria-label="More simulator guides">${pages.filter(item => item.path !== '/' && item.path !== page.path).map(item => `<a href="${item.path}">${escape(item.heading)}</a>`).join('')}</nav>
        ${footer}
    </article>`;
}

export function renderPage(html, path) {
    const page = pages.find(item => item.path === path.replace(/index\.html$/, ''));
    if (!page) throw new Error(`Missing search content for ${path}`);
    return html.replace('<!-- site:head -->', renderHead(page)).replace('<!-- site:content -->', renderContent(page));
}
