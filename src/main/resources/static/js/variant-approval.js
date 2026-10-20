(() => {
    const dialog = document.getElementById('variantComplaintDialog');
    const form = document.getElementById('variantComplaintForm');
    const name = document.getElementById('variantComplaintName');
    const message = document.getElementById('variantComplaintMessage');
    const toast = document.getElementById('variantApprovalToast');
    if (!dialog || !form || !message) return;

    let activeVariantId = null;
    const storageKey = id => `freshfruit.variantComplaint.${id}`;
    const showToast = text => {
        if (!toast) return;
        toast.hidden = false;
        toast.className = 'alert alert-success';
        toast.textContent = text;
    };

    document.querySelectorAll('.open-variant-complaint').forEach(button => {
        button.addEventListener('click', () => {
            activeVariantId = button.dataset.variantId;
            if (name) name.textContent = button.dataset.variantName || 'Biến thể sản phẩm';
            try {
                const saved = JSON.parse(localStorage.getItem(storageKey(activeVariantId)) || 'null');
                message.value = saved?.message || '';
            } catch (_) {
                message.value = '';
            }
            dialog.showModal();
            message.focus();
        });
    });

    form.addEventListener('submit', event => {
        event.preventDefault();
        const text = message.value.trim();
        if (!activeVariantId || text.length < 10 || text.length > 1000) {
            message.setCustomValidity('Nội dung giải trình phải từ 10 đến 1000 ký tự.');
            message.reportValidity();
            return;
        }
        message.setCustomValidity('');
        try {
            localStorage.setItem(storageKey(activeVariantId), JSON.stringify({
                message: text,
                savedAt: new Date().toISOString()
            }));
            dialog.close();
            showToast('Đã lưu bản nháp khiếu nại trên trình duyệt này.');
        } catch (_) {
            message.setCustomValidity('Không thể lưu bản nháp trên trình duyệt này.');
            message.reportValidity();
        }
    });

    message.addEventListener('input', () => message.setCustomValidity(''));
    document.getElementById('closeVariantComplaint')?.addEventListener('click', () => dialog.close());
    document.getElementById('cancelVariantComplaint')?.addEventListener('click', () => dialog.close());
})();
