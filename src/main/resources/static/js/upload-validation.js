/*
 * Validate image files in the browser before a multipart form is submitted.
 * This is a UX validation layer; server-side limits and image decoding remain mandatory.
 */
(() => {
    const selector = 'input[type="file"][data-upload-max-bytes]';
    const inputs = Array.from(document.querySelectorAll(selector));
    if (!inputs.length) return;

    const allowedExtensions = {
        'image/jpeg': ['.jpg', '.jpeg'],
        'image/png': ['.png']
    };

    function getErrorElement(input) {
        const id = input.dataset.uploadErrorId;
        return id ? document.getElementById(id) : null;
    }

    function showError(input, message) {
        input.setAttribute('aria-invalid', 'true');
        const error = getErrorElement(input);
        if (error) {
            error.textContent = message;
            error.hidden = false;
        }
    }

    function clearError(input) {
        input.removeAttribute('aria-invalid');
        const error = getErrorElement(input);
        if (error) {
            error.textContent = '';
            error.hidden = true;
        }
    }

    function validate(input) {
        const file = input.files && input.files[0];
        if (!file) {
            clearError(input);
            return true;
        }

        const maxBytes = Number(input.dataset.uploadMaxBytes);
        const maxMb = maxBytes / (1024 * 1024);
        const label = input.dataset.uploadLabel || 'Tệp tải lên';
        const allowedTypes = (input.dataset.uploadAllowedTypes || 'image/jpeg,image/png')
            .split(',').map(type => type.trim().toLowerCase()).filter(Boolean);
        let message = '';

        if (file.size > maxBytes) {
            message = `${label} không được vượt quá ${maxMb} MB. Vui lòng chọn tệp nhỏ hơn.`;
        } else {
            let type = (file.type || '').toLowerCase();
            if (!type) {
                const name = file.name.toLowerCase();
                type = Object.keys(allowedExtensions).find(candidate =>
                    allowedExtensions[candidate].some(ext => name.endsWith(ext))
                ) || '';
            }
            if (!allowedTypes.includes(type)) {
                message = `${label} chỉ chấp nhận ảnh JPG hoặc PNG.`;
            }
        }

        if (message) {
            showError(input, message);
            return false;
        }

        clearError(input);
        return true;
    }

    function updatePreview(input) {
        const previewId = input.dataset.uploadPreviewId;
        if (!previewId) return;
        const preview = document.getElementById(previewId);
        const file = input.files && input.files[0];
        if (!preview) return;
        if (input._uploadOriginalPreviewSrc === undefined) {
            input._uploadOriginalPreviewSrc = preview.getAttribute('src');
        }

        if (!file || input.getAttribute('aria-invalid') === 'true') {
            if (input._uploadPreviewUrl) {
                URL.revokeObjectURL(input._uploadPreviewUrl);
                input._uploadPreviewUrl = null;
            }
            if (previewId === 'productImagePreview') {
                preview.removeAttribute('src');
                preview.style.display = 'none';
            } else if (input._uploadOriginalPreviewSrc) {
                preview.src = input._uploadOriginalPreviewSrc;
            }
            return;
        }

        if (input._uploadPreviewUrl) URL.revokeObjectURL(input._uploadPreviewUrl);
        input._uploadPreviewUrl = URL.createObjectURL(file);
        preview.src = input._uploadPreviewUrl;
        preview.hidden = false;
        if (preview.style.display === 'none') preview.style.display = 'block';
    }

    inputs.forEach(input => {
        input.addEventListener('change', () => {
            validate(input);
            updatePreview(input);
        });

        const form = input.form;
        if (form && form.dataset.uploadValidationBound !== 'true') {
            form.dataset.uploadValidationBound = 'true';
            form.addEventListener('submit', event => {
                const formInputs = Array.from(form.querySelectorAll(selector));
                const invalid = formInputs.filter(candidate => !validate(candidate));
                formInputs.forEach(updatePreview);
                if (invalid.length) {
                    event.preventDefault();
                    event.stopPropagation();
                    const first = invalid[0];
                    const error = getErrorElement(first);
                    (error || first).scrollIntoView({ behavior: 'smooth', block: 'center' });
                    if (!first.classList.contains('visually-hidden')) first.focus({ preventScroll: true });
                }
            });
        }
    });
})();
