/* =========================================================
   FRESHFRUIT - CUSTOMER SHOP PROFILE JS
   Xử lý tương tác trang Cửa Hàng (Follow shop, Lưu voucher, Chat)
========================================================= */

document.addEventListener("DOMContentLoaded", function () {
    initFollowButton();
    initSavedVouchers();
    updateCartCount();
});

/* =========================================================
   1. CART COUNT SYNC
========================================================= */
function updateCartCount() {
    const badge = document.getElementById("cartCount");
    if (!badge) return;
    const cartCount = Number(localStorage.getItem("freshfruit-cart-count") || 0);
    badge.textContent = window.cartCount !== undefined ? window.cartCount : cartCount;
}

/* =========================================================
   2. FOLLOW SHOP
========================================================= */
function initFollowButton() {
    const btn = document.getElementById("followShopBtn");
    if (!btn) return;
    const shopId = window.location.pathname.split("/").pop();
    const isFollowed = localStorage.getItem("freshfruit_followed_shop_" + shopId) === "true";

    if (isFollowed) {
        btn.classList.add("following");
        btn.innerHTML = '<span>✓</span><span class="follow-text">Đã Theo Dõi</span>';
    }
}

function toggleFollowShop(btn) {
    if (!btn) return;
    const shopId = window.location.pathname.split("/").pop();
    const isFollowing = btn.classList.contains("following");

    if (isFollowing) {
        btn.classList.remove("following");
        btn.innerHTML = '<span class="follow-icon">+</span><span class="follow-text">Theo Dõi</span>';
        localStorage.setItem("freshfruit_followed_shop_" + shopId, "false");
        showShopToast("Đã hủy theo dõi cửa hàng");
    } else {
        btn.classList.add("following");
        btn.innerHTML = '<span>✓</span><span class="follow-text">Đã Theo Dõi</span>';
        localStorage.setItem("freshfruit_followed_shop_" + shopId, "true");
        showShopToast("Đã theo dõi cửa hàng thành công! Bạn sẽ nhận được thông báo khi shop có ưu đãi mới.");
    }
}

/* =========================================================
   3. SAVE VOUCHER
========================================================= */
function initSavedVouchers() {
    const savedCodes = JSON.parse(localStorage.getItem("freshfruit_saved_vouchers") || "[]");
    document.querySelectorAll(".btn-save-voucher").forEach(function (btn) {
        const code = btn.getAttribute("data-code");
        if (code && savedCodes.includes(code)) {
            btn.classList.add("saved");
            btn.textContent = "✓ Đã lưu";
            btn.disabled = true;
        }
    });
}

function saveVoucher(btn) {
    if (!btn || btn.classList.contains("saved")) return;
    const code = btn.getAttribute("data-code") || "Voucher";

    btn.classList.add("saved");
    btn.textContent = "✓ Đã lưu";
    btn.disabled = true;

    // Lưu vào LocalStorage
    let savedCodes = JSON.parse(localStorage.getItem("freshfruit_saved_vouchers") || "[]");
    if (!savedCodes.includes(code)) {
        savedCodes.push(code);
        localStorage.setItem("freshfruit_saved_vouchers", JSON.stringify(savedCodes));
    }

    showShopToast(`Đã lưu mã ${code}. Nhập mã này tại bước xác nhận đơn hàng.`);
}

/* =========================================================
   4. CHAT SHOP
========================================================= */
function openShopChat() {
    const shopNameEl = document.querySelector(".shop-profile-name");
    const shopName = shopNameEl ? shopNameEl.textContent.trim() : "cửa hàng";
    showShopToast(`Đang kết nối tới nhân viên tư vấn của ${shopName}...`);
}

/* =========================================================
   5. TOAST NOTIFICATION
========================================================= */
function showShopToast(message) {
    const toast = document.getElementById("shopToast");
    if (!toast) {
        alert(message);
        return;
    }
    toast.textContent = message;
    toast.style.display = "block";

    if (window.shopToastTimeout) {
        clearTimeout(window.shopToastTimeout);
    }

    window.shopToastTimeout = setTimeout(function () {
        toast.style.display = "none";
    }, 2800);
}
