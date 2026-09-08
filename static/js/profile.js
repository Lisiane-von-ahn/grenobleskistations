(() => {
  const deletionForm = document.querySelector('[data-delete-account]');
  if (deletionForm) {
    deletionForm.addEventListener('submit', (event) => {
      if (!window.confirm(deletionForm.dataset.confirmMessage)) event.preventDefault();
    });
  }

  const photoInput = document.getElementById('profile_picture');
  const avatar = document.querySelector('.profile-avatar');
  if (!photoInput || !avatar) return;
  const originalPhoto = avatar.src;
  let previewUrl;
  photoInput.addEventListener('change', () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    const photo = photoInput.files[0];
    previewUrl = photo && photo.type.startsWith('image/') ? URL.createObjectURL(photo) : null;
    avatar.src = previewUrl || originalPhoto;
  });
  avatar.addEventListener('error', () => {
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
      previewUrl = null;
      avatar.src = originalPhoto;
    }
  });
})();
