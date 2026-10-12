document.querySelectorAll('[data-pending]').forEach(control => {
    control.addEventListener('click', event => {
        event.preventDefault();
        window.alert('Tính năng đang phát triển.');
    });
});

const avatar = document.getElementById('avatar');
let previewUrl;
avatar?.addEventListener('change', () => {
    const file = avatar.files[0];
    const error = document.getElementById('avatarError');
    error.textContent = '';
    if (!file) return;
    if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 1024 * 1024) {
        error.textContent = 'Vui lòng chọn ảnh JPEG hoặc PNG không quá 1 MB.';
        avatar.value = '';
        return;
    }
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = URL.createObjectURL(file);
    document.getElementById('avatarPreview').src = previewUrl;
});
