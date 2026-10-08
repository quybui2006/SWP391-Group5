// Thymeleaf renders database records. JavaScript only manages the form and location selects.
const modal = document.getElementById('addressModal');
const form = document.getElementById('addressForm');
const field = name => form.elements.namedItem(name);
let locations;
let previousFocus;

function options(select, items, label, value = item => item.name) {
    select.replaceChildren(new Option(label, ''));
    items.forEach(item => select.add(new Option(item.name, value(item))));
    select.disabled = items.length === 0;
}
function province() { return locations?.find(item => String(item.code) === field('provinceCode').value); }
function districts() {
    options(field('districtName'), province()?.districts || [], 'Chọn quận / huyện');
    wards();
}
function wards() {
    const district = province()?.districts.find(item => item.name === field('districtName').value);
    options(field('wardName'), district?.wards || [], 'Chọn phường / xã');
}
field('provinceCode').addEventListener('change', districts);
field('districtName').addEventListener('change', wards);

async function openAddressModal(button = null) {
    previousFocus = document.activeElement;
    form.reset();
    field('recipientPhone').setCustomValidity('');
    field('id').value = '';
    const data = button?.dataset;
    document.getElementById('modalTitle').textContent = data?.id ? 'Chỉnh sửa địa chỉ' : 'Thêm địa chỉ mới';
    modal.classList.add('open');
    modal.setAttribute('aria-hidden', 'false');
    document.querySelector('.customer-shell').inert = true;
    document.body.style.overflow = 'hidden';
    field('recipientName').focus();
    form.querySelector('[type="submit"]').disabled = true;
    const error = document.getElementById('locationError');
    error.hidden = true;
    if (data) {
        field('id').value = data.id || '';
        field('recipientName').value = data.name || '';
        field('recipientPhone').value = data.phone || '';
        field('addressDetail').value = data.detail || '';
        field('defaultAddress').checked = data.default === 'true';
    }
    try {
        if (!locations) {
            const response = await fetch(form.dataset.locationsUrl);
            if (!response.ok) throw new Error('Không tải được danh mục địa chỉ. Đóng và mở lại form để thử lại.');
            locations = await response.json();
        }
        options(field('provinceCode'), locations, 'Chọn tỉnh / thành phố', item => item.code);
        if (data) field('provinceCode').value = data.code || locations.find(p => p.name === data.province)?.code || '';
        districts();
        if (data) {
            const split = (data.ward || '').split(', ');
            field('districtName').value = data.district || split.slice(1).join(', ');
            wards();
            field('wardName').value = data.district ? data.ward : split[0];
        }
        form.querySelector('[type="submit"]').disabled = false;
    } catch (ex) { error.textContent = ex.message; error.hidden = false; }
}
function closeAddressModal() {
    modal.classList.remove('open');
    modal.setAttribute('aria-hidden', 'true');
    document.querySelector('.customer-shell').inert = false;
    document.body.style.overflow = '';
    previousFocus?.focus();
}
function validatePhone() {
    const input = field('recipientPhone');
    const normalized = input.value.replace(/[\s.-]/g, '').replace(/^\+84/, '0');
    input.setCustomValidity(/^0[35789]\d{8}$/.test(normalized) ? '' : 'Nhập số di động Việt Nam hợp lệ: 10 số bắt đầu 03, 05, 07, 08, 09 hoặc +84 tương ứng.');
}
field('recipientPhone').addEventListener('input', validatePhone);
form.addEventListener('submit', event => {
    validatePhone();
    if (!form.reportValidity()) event.preventDefault();
    else form.querySelector('[type="submit"]').disabled = true;
});
modal.addEventListener('click', event => { if (event.target === modal) closeAddressModal(); });
modal.addEventListener('keydown', event => {
    if (event.key === 'Escape') closeAddressModal();
    if (event.key !== 'Tab') return;
    const controls = [...modal.querySelectorAll('button, input:not([type="hidden"]), select, textarea')].filter(el => !el.disabled);
    const first = controls[0], last = controls[controls.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
});
if (document.getElementById('failedAddress')) openAddressModal(document.getElementById('failedAddress'));

let toastTimer;
document.querySelectorAll('[data-pending]').forEach(control => {
    control.addEventListener('click', event => {
        event.preventDefault();
        const toast = document.getElementById('accountToast');
        toast.textContent = 'Chức năng này đang được phát triển.';
        toast.classList.add('show');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => toast.classList.remove('show'), 3000);
    });
});
