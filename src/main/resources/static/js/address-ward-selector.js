/**
 * address-ward-selector.js
 * Tự động tải dữ liệu Phường / Xã theo Tỉnh / Thành phố cho kho hàng
 * Hỗ trợ phân nhóm theo Quận / Huyện và chế độ nhập tay khi cần.
 */
(function () {
    let cachedLocations = null;

    async function getLocationsData() {
        if (window.__vietnamAddressesCache) {
            return window.__vietnamAddressesCache;
        }
        if (!cachedLocations) {
            try {
                const res = await fetch('/data/vietnam-addresses-v1.json');
                if (res.ok) {
                    cachedLocations = await res.json();
                    window.__vietnamAddressesCache = cachedLocations;
                }
            } catch (err) {
                console.warn('Không thể tải /data/vietnam-addresses-v1.json:', err);
            }
        }
        return cachedLocations;
    }

    function initAddressWardSelector() {
        const provinceEl = document.getElementById('provinceCode');
        const wardSelect = document.getElementById('wardNameSelect');
        const wardInput = document.getElementById('wardName');

        if (!provinceEl || !wardSelect || !wardInput) {
            return;
        }

        function findProvince(locations, codeOrVal) {
            if (!locations || !codeOrVal) return null;
            const target = String(codeOrVal).trim();

            // 1. Khớp theo mã tỉnh (code: 1, 2, 4...)
            let found = locations.find(p => String(p.code) === target);
            if (found) return found;

            // 2. Khớp theo tên tỉnh hiển thị trên option
            const selectedOpt = provinceEl.options[provinceEl.selectedIndex];
            const optText = selectedOpt ? selectedOpt.textContent.trim() : '';
            if (optText) {
                found = locations.find(p => p.name === optText || optText.includes(p.name) || p.name.includes(optText));
                if (found) return found;
            }

            // 3. Khớp theo tên nếu value là chuỗi tên
            found = locations.find(p => p.name === target || target.includes(p.name) || p.name.includes(target));
            return found || null;
        }

        async function populateWards(initialWardToSelect) {
            const locations = await getLocationsData();
            if (!locations) {
                // Fallback nếu không tải được JSON địa chỉ
                wardSelect.style.display = 'none';
                wardSelect.required = false;
                wardInput.style.display = 'block';
                wardInput.required = true;
                return;
            }

            const prov = findProvince(locations, provinceEl.value);
            wardSelect.innerHTML = '';

            if (!prov || !prov.districts || prov.districts.length === 0) {
                const opt = document.createElement('option');
                opt.value = '';
                opt.textContent = provinceEl.value 
                    ? '-- Không tìm thấy danh sách phường xã (Tự nhập tay) --' 
                    : '-- Vui lòng chọn Tỉnh / Thành phố trước --';
                wardSelect.appendChild(opt);
                wardSelect.disabled = !provinceEl.value;

                if (provinceEl.value) {
                    wardInput.style.display = 'block';
                    wardInput.required = true;
                } else {
                    wardInput.style.display = 'none';
                    wardInput.required = false;
                }
                return;
            }

            wardSelect.disabled = false;
            wardSelect.style.display = '';

            const defaultOpt = document.createElement('option');
            defaultOpt.value = '';
            defaultOpt.textContent = '-- Chọn Phường / Xã --';
            wardSelect.appendChild(defaultOpt);

            // Thu thập toàn bộ Phường / Xã sau sáp nhập trong Tỉnh / Thành phố (không phân nhóm Quận / Huyện)
            const wardMap = new Map();
            prov.districts.forEach(dist => {
                if (dist.wards && dist.wards.length > 0) {
                    dist.wards.forEach(w => {
                        if (!wardMap.has(w.name)) {
                            wardMap.set(w.name, w);
                        }
                    });
                }
            });

            // Sắp xếp danh sách Phường / Xã theo thứ tự bảng chữ cái tiếng Việt A-Z
            const sortedWards = Array.from(wardMap.values()).sort((a, b) => a.name.localeCompare(b.name, 'vi'));

            let matched = false;
            sortedWards.forEach(w => {
                const opt = document.createElement('option');
                opt.value = w.name;
                opt.textContent = w.name;
                if (initialWardToSelect && (w.name === initialWardToSelect || initialWardToSelect === w.name)) {
                    opt.selected = true;
                    matched = true;
                }
                wardSelect.appendChild(opt);
            });

            // Tùy chọn nhập tay linh hoạt nếu thuộc khu vực mới sáp nhập / đổi tên
            const otherOpt = document.createElement('option');
            otherOpt.value = '__OTHER__';
            otherOpt.textContent = '✏️ Phường / Xã khác (Tự nhập tay)...';
            if (initialWardToSelect && !matched) {
                otherOpt.selected = true;
            }
            wardSelect.appendChild(otherOpt);

            if (wardSelect.value === '__OTHER__') {
                wardInput.style.display = 'block';
                wardInput.required = true;
                if (initialWardToSelect && !matched) {
                    wardInput.value = initialWardToSelect;
                }
            } else if (wardSelect.value) {
                wardInput.style.display = 'none';
                wardInput.required = false;
                wardInput.value = wardSelect.value;
            } else {
                wardInput.style.display = 'none';
                wardInput.required = false;
                wardInput.value = '';
            }
        }

        provinceEl.addEventListener('change', function () {
            wardInput.value = '';
            populateWards(null);
        });

        wardSelect.addEventListener('change', function () {
            if (this.value === '__OTHER__') {
                wardInput.style.display = 'block';
                wardInput.required = true;
                wardInput.value = '';
                wardInput.placeholder = 'Nhập chính xác tên Phường / Xã của bạn';
                wardInput.focus();
            } else if (this.value) {
                wardInput.style.display = 'none';
                wardInput.required = false;
                wardInput.value = this.value;
            } else {
                wardInput.style.display = 'none';
                wardInput.required = false;
                wardInput.value = '';
            }
        });

        const form = wardSelect.closest('form');
        if (form) {
            form.addEventListener('submit', function (e) {
                if (wardSelect.value === '__OTHER__') {
                    if (!wardInput.value.trim()) {
                        e.preventDefault();
                        alert('Vui lòng nhập tên Phường / Xã kho hàng.');
                        wardInput.focus();
                    }
                } else if (wardSelect.value) {
                    wardInput.value = wardSelect.value;
                }
            });
        }

        // Khởi tạo ban đầu khi mở trang
        getLocationsData().then(() => {
            const initialWard = wardInput.value ? wardInput.value.trim() : '';
            if (provinceEl.value) {
                populateWards(initialWard);
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAddressWardSelector);
    } else {
        initAddressWardSelector();
    }
})();
