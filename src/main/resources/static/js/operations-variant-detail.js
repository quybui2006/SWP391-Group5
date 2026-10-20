(() => {
    const config = window.variantReviewConfig;
    const toastHost = document.getElementById('toastHost');
    const dialog = document.getElementById('rejectionDialog');
    const reasonInput = document.getElementById('rejectionReason');
    const rejectForm = document.getElementById('rejectForm');
    let busy = false;

    function toast(message, error = false) {
        if (!toastHost) return;
        const node = document.createElement('div');
        node.className = `toast${error ? ' error' : ''}`;
        node.textContent = message;
        toastHost.appendChild(node);
        window.setTimeout(() => node.remove(), 4200);
    }

    async function submitAction(url, payload) {
        if (busy || !config?.pending) return;
        busy = true;
        const buttons = document.querySelectorAll('#approveVariant, #openRejectDialog, #confirmReject');
        buttons.forEach(button => { button.disabled = true; });
        try {
            const options = { method: 'POST', headers: { 'Accept': 'application/json' }, credentials: 'same-origin' };
            if (payload) {
                options.headers['Content-Type'] = 'application/json';
                options.body = JSON.stringify(payload);
            }
            const response = await fetch(url, options);
            let body = {};
            try { body = await response.json(); } catch (_) { /* non-JSON server error */ }
            if (!response.ok || body.success === false) throw new Error(body.message || 'Không thể xử lý biến thể.');
            toast(body.message || 'Đã xử lý biến thể.');
            window.setTimeout(() => window.location.href = '/operations/variants', 650);
        } catch (error) {
            toast(error.message || 'Có lỗi xảy ra. Vui lòng thử lại.', true);
            buttons.forEach(button => { button.disabled = false; });
            busy = false;
        }
    }

    document.getElementById('approveVariant')?.addEventListener('click', () => {
        if (window.confirm('Duyệt biến thể này và cho phép mở bán?')) submitAction(config.approveUrl);
    });
    document.getElementById('openRejectDialog')?.addEventListener('click', () => dialog?.showModal());
    document.getElementById('closeDialog')?.addEventListener('click', () => dialog?.close());
    document.getElementById('cancelDialog')?.addEventListener('click', () => dialog?.close());
    rejectForm?.addEventListener('submit', event => {
        event.preventDefault();
        const reason = (reasonInput?.value || '').trim();
        if (!reason) {
            reasonInput.setCustomValidity('Vui lòng nhập lý do từ chối.');
            reasonInput.reportValidity();
            return;
        }
        if (reason.length > 1000) {
            reasonInput.setCustomValidity('Lý do từ chối không được quá 1000 ký tự.');
            reasonInput.reportValidity();
            return;
        }
        reasonInput.setCustomValidity('');
        submitAction(config.rejectUrl, { rejectionReason: reason });
    });
    reasonInput?.addEventListener('input', () => reasonInput.setCustomValidity(''));
})();
