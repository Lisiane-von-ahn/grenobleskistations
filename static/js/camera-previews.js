document.querySelectorAll('[data-camera-preview]').forEach((preview) => {
    const hidePreview = () => { preview.closest('.camera-preview').hidden = true; };
    preview.addEventListener('error', hidePreview, { once: true });
    if (preview.complete && preview.naturalWidth === 0) hidePreview();
});
