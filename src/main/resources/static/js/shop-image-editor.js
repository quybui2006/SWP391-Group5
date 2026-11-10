document.querySelectorAll('[data-image-input]').forEach((input) => {
  input.addEventListener('change', () => {
    const file = input.files && input.files[0];
    const form = input.closest('form');
    const name = form && form.querySelector('[data-file-name]');
    const wrapper = form && form.querySelector('.image-preview-wrap');
    if (name) name.textContent = file ? file.name : 'Chưa chọn tệp';
    if (!file || !wrapper || !file.type.startsWith('image/')) return;

    let preview = wrapper.querySelector('[data-current-preview]');
    if (!preview) {
      preview = document.createElement('img');
      preview.className = 'image-preview-large';
      preview.dataset.currentPreview = '';
      preview.alt = 'Ảnh xem trước';
      wrapper.replaceChildren(preview);
    }
    if (preview.dataset.objectUrl) URL.revokeObjectURL(preview.dataset.objectUrl);
    preview.dataset.objectUrl = URL.createObjectURL(file);
    preview.src = preview.dataset.objectUrl;
  });
});
