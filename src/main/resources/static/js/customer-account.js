// UI demo only: addresses live in memory and reset when the page reloads.
const addressGrid = document.querySelector('.address-grid');
const modal = document.getElementById('addressModal');
const form = modal.querySelector('form');
const fields = form.querySelectorAll('input:not([type="checkbox"]), select, textarea');
const defaultCheckbox = form.querySelector('[type="checkbox"]');
const cardTemplate = addressGrid.querySelector('.address-card').cloneNode(true);
let addresses = [
    { id: 1, name: 'Nguyễn Minh Lan', phone: '0912456780', city: 'Hà Nội', ward: 'Phường Yên Hòa', detail: 'Số 18, ngõ 76 Nguyễn Khang', isDefault: true },
    { id: 2, name: 'Trần Hoàng An', phone: '0983765421', city: 'Thành phố Hồ Chí Minh', ward: 'Phường An Khánh', detail: 'Căn hộ B12, số 28 đường Mai Chí Thọ', isDefault: false }
];
let editingId = null;
let previousFocus;
let toastTimer;

function notify(message) {
    const toast = document.getElementById('accountToast');
    clearTimeout(toastTimer);
    toast.textContent = message;
    toast.classList.add('show');
    toastTimer = setTimeout(() => toast.classList.remove('show'), 3500);
}

function renderAddresses() {
    addressGrid.querySelectorAll('.address-card, .empty-addresses').forEach(card => card.remove());
    const addCard = addressGrid.querySelector('.add-card');
    addresses.forEach(address => {
        const card = cardTemplate.cloneNode(true);
        card.classList.toggle('is-default', address.isDefault);
        card.querySelector('.address-label').textContent = address.isDefault ? '⌖ Địa chỉ mặc định' : '⌖ Địa chỉ nhận hàng';
        card.querySelector('.address-label').classList.toggle('muted-label', !address.isDefault);
        card.querySelector('.more-button')?.remove();
        card.querySelector('.recipient strong').textContent = address.name;
        card.querySelector('.recipient span').textContent = address.phone;
        card.querySelector('.address-text').textContent = `${address.detail}\n${address.ward}\n${address.city}`;
        const footer = card.querySelector('.card-footer');
        footer.replaceChildren();
        const defaultAction = document.createElement(address.isDefault ? 'span' : 'button');
        defaultAction.className = address.isDefault ? 'default-pill' : 'set-default';
        defaultAction.textContent = address.isDefault ? '✓ Mặc định' : 'Đặt làm mặc định';
        if (!address.isDefault) {
            defaultAction.type = 'button';
            defaultAction.addEventListener('click', () => {
                addresses.forEach(item => { item.isDefault = item.id === address.id; });
                renderAddresses();
                notify('Đã đổi địa chỉ mặc định trong bản xem thử.');
            });
        }
        const actions = document.createElement('div');
        const edit = document.createElement('button');
        edit.type = 'button';
        edit.className = 'edit-link';
        edit.textContent = 'Chỉnh sửa';
        edit.addEventListener('click', () => openAddressModal(address.id));
        actions.append(edit);
        if (!address.isDefault) {
            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'delete-link';
            remove.textContent = 'Xóa';
            remove.addEventListener('click', () => {
                if (window.confirm(`Xóa địa chỉ của ${address.name} khỏi bản xem thử?`)) {
                    addresses = addresses.filter(item => item.id !== address.id);
                    renderAddresses();
                    notify('Đã xóa địa chỉ trong bản xem thử.');
                }
            });
            actions.append(remove);
        }
        footer.append(defaultAction, actions);
        addressGrid.insertBefore(card, addCard);
    });
    document.querySelector('.address-count').textContent = `${addresses.length} địa chỉ mẫu`;
}

function openAddressModal(id = null) {
    previousFocus = document.activeElement;
    editingId = typeof id === 'number' ? id : null;
    form.reset();
    const address = addresses.find(item => item.id === editingId);
    document.getElementById('modalTitle').textContent = address ? 'Chỉnh sửa địa chỉ' : 'Thêm địa chỉ mới';
    if (address) {
        [address.name, address.phone, address.city, address.ward, address.detail].forEach((value, index) => { fields[index].value = value; });
    }
    defaultCheckbox.checked = Boolean(address?.isDefault);
    defaultCheckbox.disabled = Boolean(address?.isDefault);
    modal.classList.add('open');
    modal.setAttribute('aria-hidden', 'false');
    document.querySelector('.customer-shell').inert = true;
    document.body.style.overflow = 'hidden';
    fields[0].focus();
}

function closeAddressModal() {
    modal.classList.remove('open');
    modal.setAttribute('aria-hidden', 'true');
    document.querySelector('.customer-shell').inert = false;
    document.body.style.overflow = '';
    if (previousFocus?.isConnected) previousFocus.focus();
    else document.querySelector('.page-intro .primary-button').focus();
}

function saveMockAddress(event) {
    event.preventDefault();
    const values = Array.from(fields, field => field.value.trim());
    if (values.some(value => !value)) {
        notify('Vui lòng nhập đầy đủ thông tin địa chỉ.');
        return;
    }
    const address = {
        id: editingId ?? Date.now(), name: values[0], phone: values[1],
        city: values[2], ward: values[3], detail: values[4],
        isDefault: defaultCheckbox.checked || addresses.length === 0
    };
    if (address.isDefault) addresses.forEach(item => { item.isDefault = false; });
    if (editingId !== null) addresses = addresses.map(item => item.id === editingId ? address : item);
    else addresses.push(address);
    renderAddresses();
    closeAddressModal();
    notify('Đã cập nhật bản xem thử. Dữ liệu sẽ đặt lại khi tải lại trang.');
}

modal.addEventListener('click', event => {
    if (event.target === modal) closeAddressModal();
});
modal.addEventListener('keydown', event => {
    if (event.key === 'Escape') closeAddressModal();
    if (event.key !== 'Tab') return;
    const focusable = Array.from(modal.querySelectorAll('button, input, select, textarea')).filter(element => !element.disabled);
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
});
document.querySelectorAll('a[href="#"], .top-icon').forEach(control => {
    control.addEventListener('click', event => {
        event.preventDefault();
        notify('Màn hình này sẽ được bổ sung sau.');
    });
});
renderAddresses();
