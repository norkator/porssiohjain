// Reuse the Chrome harness, running only the equipment and turbine scenarios.
process.env.NUCLEAR_BROWSER_FOCUS = 'equipment';
await import('./browser.mjs');
