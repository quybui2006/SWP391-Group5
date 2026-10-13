(() => {
    const table = document.querySelector('#approvalTable');
    const tbody = document.querySelector('#approvalRows');
    if (!table || !tbody) return;

    const resource = table.dataset.resource;
    const searchInput = document.querySelector('#tableSearch');
    const recordCount = document.querySelector('#recordCount');
    const pageInfo = document.querySelector('#pageInfo');
    const previousButton = document.querySelector('#previousPage');
    const nextButton = document.querySelector('#nextPage');
    const dialog = document.querySelector('#rejectionDialog');
    const rejectForm = document.querySelector('#rejectForm');
    const reasonInput = document.querySelector('#rejectionReason');
    const confirmReject = document.querySelector('#confirmReject');
    const toastHost = document.querySelector('#toastHost');
    const pageSize = 10;
    let currentPage = 0;
    let rejectingId = null;
    let requestInProgress = false;

    function dataRows() {
        return Array.from(tbody.querySelectorAll('tr[data-approval-row]'));
    }

    function toast(message, type = 'success') {
        if (!toastHost) return;
        const item = document.createElement('div');
        item.className = `toast${type === 'error' ? ' error' : type === 'info' ? ' info' : ''}`;
        item.textContent = message;
        toastHost.appendChild(item);
        window.setTimeout(() => item.remove(), 4200);
    }

    function updateEmptyMessage(filteredCount) {
        const existing = tbody.querySelector('.empty-row');
        if (existing) existing.remove();
        if (filteredCount > 0) return;

        const row = document.createElement('tr');
        row.className = 'empty-row';
        const cell = document.createElement('td');
        cell.colSpan = 5;
        cell.textContent = dataRows().length === 0
            ? 'Không còn yêu cầu nào đang chờ duyệt.'
            : 'Không tìm thấy yêu cầu phù hợp với từ khóa.';
        row.appendChild(cell);
        tbody.appendChild(row);
    }

    function renderTable() {
        const allRows = dataRows();
        const query = (searchInput?.value || '').trim().toLocaleLowerCase('vi');
        const filteredRows = allRows.filter(row => row.textContent.toLocaleLowerCase('vi').includes(query));
        const totalPages = Math.max(1, Math.ceil(filteredRows.length / pageSize));
        currentPage = Math.min(currentPage, totalPages - 1);
        const start = currentPage * pageSize;
        const visibleRows = new Set(filteredRows.slice(start, start + pageSize));

        allRows.forEach(row => {
            row.hidden = !visibleRows.has(row);
        });
        updateEmptyMessage(filteredRows.length);

        if (recordCount) {
            const total = allRows.length;
            recordCount.textContent = `${total} yêu cầu`;
        }
        if (pageInfo) {
            if (filteredRows.length === 0) pageInfo.textContent = '0 yêu cầu';
            else pageInfo.textContent = `Hiển thị ${start + 1}–${Math.min(start + pageSize, filteredRows.length)} / ${filteredRows.length}`;
        }
        if (previousButton) previousButton.disabled = currentPage <= 0 || filteredRows.length === 0;
        if (nextButton) nextButton.disabled = currentPage >= totalPages - 1 || filteredRows.length === 0;
    }

    async function sendAction(id, action, rejectionReason) {
        if (requestInProgress) return;
        requestInProgress = true;
        const row = tbody.querySelector(`tr[data-approval-row][data-record-id="${CSS.escape(String(id))}"]`);
        const actionButtons = row ? Array.from(row.querySelectorAll('button')) : [];
        actionButtons.forEach(button => { button.disabled = true; });
        if (confirmReject) confirmReject.disabled = true;

        try {
            const response = await fetch(`/operations/api/${resource}/${encodeURIComponent(id)}/${action}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                credentials: 'same-origin',
                body: action === 'reject' ? JSON.stringify({ rejectionReason }) : undefined
            });
            let payload = {};
            try { payload = await response.json(); } catch (_) { /* Handle non-JSON server errors below. */ }
            if (!response.ok) throw new Error(payload.message || 'Không thể xử lý yêu cầu. Vui lòng thử lại.');

            if (row) row.remove();
            if (dialog?.open) dialog.close();
            if (reasonInput) reasonInput.value = '';
            rejectingId = null;
            currentPage = Math.max(0, currentPage);
            renderTable();
            toast(payload.message || 'Đã cập nhật trạng thái thành công.');
        } catch (error) {
            toast(error.message || 'Có lỗi xảy ra. Vui lòng thử lại.', 'error');
            actionButtons.forEach(button => { button.disabled = false; });
        } finally {
            requestInProgress = false;
            if (confirmReject) confirmReject.disabled = false;
        }
    }

    table.addEventListener('click', event => {
        const button = event.target.closest('button[data-approval-action]');
        if (!button || requestInProgress) return;
        const row = button.closest('tr[data-approval-row]');
        if (!row) return;
        const id = row.dataset.recordId;
        const action = button.dataset.approvalAction;

        if (action === 'approve') {
            sendAction(id, 'approve');
            return;
        }
        if (action === 'reject' && dialog) {
            rejectingId = id;
            if (reasonInput) {
                reasonInput.value = '';
                reasonInput.setCustomValidity('');
            }
            dialog.showModal();
            reasonInput?.focus();
        }
    });

    rejectForm?.addEventListener('submit', event => {
        event.preventDefault();
        if (!rejectingId || requestInProgress) return;
        const reason = reasonInput?.value || '';
        if (!reason.trim()) {
            reasonInput?.setCustomValidity('Vui lòng nhập lý do từ chối.');
            reasonInput?.reportValidity();
            return;
        }
        if (reason.length > 1000) {
            reasonInput?.setCustomValidity('Lý do từ chối không được vượt quá 1000 ký tự.');
            reasonInput?.reportValidity();
            return;
        }
        reasonInput?.setCustomValidity('');
        sendAction(rejectingId, 'reject', reason.trim());
    });

    reasonInput?.addEventListener('input', () => reasonInput.setCustomValidity(''));
    document.querySelector('#closeDialog')?.addEventListener('click', () => dialog?.close());
    document.querySelector('#cancelDialog')?.addEventListener('click', () => dialog?.close());
    dialog?.addEventListener('close', () => {
        if (!requestInProgress) rejectingId = null;
    });
    searchInput?.addEventListener('input', () => { currentPage = 0; renderTable(); });
    previousButton?.addEventListener('click', () => { currentPage = Math.max(0, currentPage - 1); renderTable(); });
    nextButton?.addEventListener('click', () => { currentPage += 1; renderTable(); });

    renderTable();
})();
