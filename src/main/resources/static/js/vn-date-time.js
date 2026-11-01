(() => {
    const init = () => {
        const locale = window.flatpickr?.l10ns?.vn || 'default';

        document.querySelectorAll('[data-vn-date]').forEach(input => {
            const hidden = document.getElementById(input.dataset.target);
            if (!hidden || !window.flatpickr) return;

            const timeInput = input.dataset.timeTarget
                ? document.getElementById(input.dataset.timeTarget)
                : null;
            const displayFormat = input.dataset.mode === 'datetime' ? 'd/m/Y H:i' : 'd/m/Y';
            const stored = /^(\d{4}-\d{2}-\d{2})(?:T(\d{2}:\d{2}))?/.exec(hidden.value || '');
            if (timeInput && stored?.[2]) timeInput.value ||= stored[2];

            const defaultDate = input.value || (stored
                ? stored[1].split('-').reverse().join('/') +
                  (input.dataset.mode === 'datetime' && stored[2] ? ` ${stored[2]}` : '')
                : undefined);

            const picker = flatpickr(input, {
                enableTime: input.dataset.mode === 'datetime',
                time_24hr: true,
                dateFormat: displayFormat,
                locale,
                // Put the calendar outside the wrapping label so clicking a day selects it reliably.
                appendTo: document.body,
                clickOpens: true,
                allowInput: true,
                disableMobile: true,
                minDate: input.dataset.minDate || undefined,
                defaultDate,
                onChange: dates => {
                    if (!dates.length) {
                        hidden.value = '';
                        return;
                    }
                    const dateValue = flatpickr.formatDate(dates[0], 'Y-m-d');
                    const timeValue = timeInput?.value ||
                        (input.dataset.mode === 'datetime' ? flatpickr.formatDate(dates[0], 'H:i') : '');
                    hidden.value = timeInput
                        ? (timeValue ? `${dateValue}T${timeValue}` : '')
                        : (input.dataset.mode === 'datetime' ? `${dateValue}T${timeValue}` : dateValue);
                    input.setCustomValidity('');
                }
            });

            const syncValue = () => {
                const selected = picker.parseDate(input.value.trim(), displayFormat);
                if (!selected || flatpickr.formatDate(selected, displayFormat) !== input.value.trim()) {
                    hidden.value = '';
                    return false;
                }
                const dateValue = flatpickr.formatDate(selected, 'Y-m-d');
                const timeValue = timeInput?.value ||
                    (input.dataset.mode === 'datetime' ? flatpickr.formatDate(selected, 'H:i') : '');
                if (timeInput && !timeValue) {
                    hidden.value = '';
                    return false;
                }
                hidden.value = timeInput
                    ? `${dateValue}T${timeValue}`
                    : (input.dataset.mode === 'datetime' ? `${dateValue}T${timeValue}` : dateValue);
                return true;
            };

            input.addEventListener('input', () => {
                hidden.value = '';
                input.setCustomValidity('');
            });
            input.addEventListener('change', syncValue);
            timeInput?.addEventListener('input', () => {
                hidden.value = '';
                timeInput.setCustomValidity('');
            });
            timeInput?.addEventListener('change', syncValue);

            input.form?.addEventListener('submit', event => {
                if (!syncValue()) {
                    input.setCustomValidity(`Nhập ngày theo định dạng ${displayFormat === 'd/m/Y' ? 'dd/MM/yyyy' : 'dd/MM/yyyy HH:mm'}.`);
                    input.reportValidity();
                    event.preventDefault();
                    return;
                }
                input.setCustomValidity('');
                timeInput?.setCustomValidity('');
            });
        });
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init, { once: true });
    } else {
        init();
    }
})();
