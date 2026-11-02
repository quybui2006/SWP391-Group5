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

    const charCounter = document.getElementById('rejectionCharCount');
    function updateCharCount() {
        if (!charCounter || !reasonInput) return;
        const len = reasonInput.value.length;
        charCounter.textContent = `${len} / 1000 ký tự`;
        if (len > 900) {
            charCounter.style.color = '#dc2626';
            charCounter.style.fontWeight = '700';
        } else {
            charCounter.style.color = '';
            charCounter.style.fontWeight = '';
        }
    }

    document.querySelectorAll('.reason-chip').forEach(chip => {
        chip.addEventListener('click', () => {
            const reason = chip.dataset.reason || chip.textContent.trim();
            if (!reasonInput) return;
            if (reasonInput.value.trim().length > 0) {
                reasonInput.value = reasonInput.value.trim() + '; ' + reason;
            } else {
                reasonInput.value = reason;
            }
            reasonInput.dispatchEvent(new Event('input'));
            reasonInput.focus();
        });
    });

    document.getElementById('approveVariant')?.addEventListener('click', () => {
        if (window.confirm('Duyệt biến thể này và cho phép mở bán?')) submitAction(config.approveUrl);
    });
    document.getElementById('openRejectDialog')?.addEventListener('click', () => {
        if (reasonInput) {
            reasonInput.value = '';
            reasonInput.setCustomValidity('');
            updateCharCount();
        }
        dialog?.showModal();
        reasonInput?.focus();
    });
    document.getElementById('closeDialog')?.addEventListener('click', () => dialog?.close());
    document.getElementById('cancelDialog')?.addEventListener('click', () => dialog?.close());
    dialog?.addEventListener('close', () => {
        if (!busy && reasonInput) {
            reasonInput.value = '';
            updateCharCount();
        }
    });

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
    reasonInput?.addEventListener('input', () => {
        reasonInput.setCustomValidity('');
        updateCharCount();
    });
})();
