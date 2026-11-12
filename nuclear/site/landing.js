// Preserve existing pairing links without mounting the simulation on the landing
// page. Screen selection lives in the URL: reload stays there, Back returns here.
if (new URLSearchParams(location.search).get('screen') === 'companion') {
    location.replace(`/companion/${location.hash}`);
}
