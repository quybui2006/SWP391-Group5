
// Xử lý giỏ hàng riêng cho trang chi tiết sản phẩm FreshFruit.

async function refreshCartCount() {
    try {
        const response = await fetch('/cart/count', {
            method: 'GET',
            credentials: 'same-origin',
            headers: {
                'Accept': 'application/json, text/plain'
            }
        });

        if (!response.ok) {
            return;
        }

        const result = await response.text();
        const count = Number.parseInt(result.trim(), 10);
        const badge = document.getElementById('cartCount');

        if (badge && Number.isFinite(count)) {
            badge.textContent = count;
        }

        if (Number.isFinite(count)) {
            window.cartCount = count;
            localStorage.setItem('freshfruit-cart-count', count);
        }
    } catch (error) {
        console.error('Không thể cập nhật số lượng giỏ hàng:', error);
    }
}

// Gửi yêu cầu thêm sản phẩm lên CartController.
async function sendAddToCartRequest(variantId, quantity) {
    if (!variantId) {
        throw new Error('Không tìm thấy mã sản phẩm.');
    }

    const formData = new URLSearchParams();
    formData.append('variantId', variantId);
    formData.append('quantity', quantity);

    const response = await fetch('/cart/items', {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
            'X-Requested-With': 'XMLHttpRequest',
            'Accept': 'application/json, text/plain'
        },
        body: formData.toString()
    });

    if (response.status === 401) {
        const currentUrl =
            window.location.pathname + window.location.search;

        window.location.href =
            '/login?continueTo=' + encodeURIComponent(currentUrl);

        return null;
    }

    if (!response.ok) {
        const message = await response.text();

        if (response.status === 403) {
            throw new Error(
                'Yêu cầu bị từ chối. Hãy kiểm tra đăng nhập hoặc cấu hình bảo vệ CSRF.'
            );
        }

        throw new Error(
            message || 'Không thể thêm sản phẩm vào giỏ hàng.'
        );
    }

    // CartController có thể trả số lượng dưới dạng JSON hoặc text.
    const result = (await response.text()).trim();
    const count = Number.parseInt(result, 10);

    if (Number.isFinite(count)) {
        window.cartCount = count;
        localStorage.setItem('freshfruit-cart-count', count);

        const badge = document.getElementById('cartCount');

        if (badge) {
            badge.textContent = count;
        }
    } else {
        await refreshCartCount();
    }

    return true;
}

// Thêm sản phẩm ở trang chi tiết.
async function addProductToCart(event, button) {
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }

    if (!button || button.disabled) {
        return;
    }

    const variantId = button.getAttribute('data-variant-id');
    const quantityInput = document.getElementById('quantity');

    const quantity = Math.max(
        1,
        Math.min(
            99,
            Number.parseInt(quantityInput?.value, 10) || 1
        )
    );

    const oldContent = button.innerHTML;
    button.disabled = true;

    try {
        const success = await sendAddToCartRequest(variantId, quantity);

        if (success) {
            if (typeof showToast === 'function') {
                showToast('Đã thêm sản phẩm vào giỏ hàng!');
            } else {
                alert('Đã thêm sản phẩm vào giỏ hàng!');
            }
        }
    } catch (error) {
        console.error('Lỗi thêm vào giỏ hàng:', error);
        alert(error.message || 'Không thể thêm sản phẩm vào giỏ hàng.');
    } finally {
        button.disabled = false;
        button.innerHTML = oldContent;
    }
}

// Thêm sản phẩm từ danh sách gợi ý.
async function addRelatedToCart(event, button) {
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }

    if (!button || button.disabled) {
        return;
    }

    const variantId = button.getAttribute('data-variant-id');
    const oldContent = button.innerHTML;

    button.disabled = true;

    try {
        const success = await sendAddToCartRequest(variantId, 1);

        if (success) {
            button.innerHTML = '✓';

            setTimeout(() => {
                button.innerHTML = oldContent;
            }, 1000);
        }
    } catch (error) {
        console.error('Lỗi thêm sản phẩm gợi ý:', error);
        alert(error.message || 'Không thể thêm sản phẩm vào giỏ hàng.');
        button.innerHTML = oldContent;
    } finally {
        button.disabled = false;
    }
}

// Thêm sản phẩm rồi chuyển sang giỏ hàng.
async function buyNow(event, button) {
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }

    if (!button || button.disabled) {
        return;
    }

    const variantId = button.getAttribute('data-variant-id');
    const quantityInput = document.getElementById('quantity');

    const quantity = Math.max(
        1,
        Math.min(
            99,
            Number.parseInt(quantityInput?.value, 10) || 1
        )
    );

    button.disabled = true;

    try {
        const success = await sendAddToCartRequest(variantId, quantity);

        if (success) {
            window.location.href =
                '/cart?selected=' + encodeURIComponent(variantId);
        }
    } catch (error) {
        console.error('Lỗi mua ngay:', error);
        alert(error.message || 'Không thể thực hiện mua ngay.');
        button.disabled = false;
    }
}

document.addEventListener('DOMContentLoaded', refreshCartCount);