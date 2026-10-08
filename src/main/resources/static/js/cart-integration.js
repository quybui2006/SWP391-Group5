window.addProductToCart = function (button) {
    if (!button) return;
    const variantId = button.getAttribute("data-variant-id");
    const quantity = document.getElementById("quantity")?.value || 1;
    persistCartItem(variantId, quantity).then(count => {
        const previous = button.innerHTML;
        button.innerHTML = "✓ Đã thêm vào giỏ";
        button.disabled = true;
        window.cartCount = count;
        localStorage.setItem("freshfruit-cart-count", count);
        if (typeof updateCartCount === "function") updateCartCount();
        setTimeout(() => { button.innerHTML = previous; button.disabled = false; }, 1000);
    }).catch(showCartError);
};

window.buyNow = function (button) {
    if (!button) return;
    const variantId = button.getAttribute("data-variant-id");
    const quantity = document.getElementById("quantity")?.value || 1;
    persistCartItem(variantId, quantity).then(() => { window.location.href = "/cart"; }).catch(showCartError);
};

function persistCartItem(variantId, quantity) {
    return fetch("/cart/items", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ variantId, quantity })
    }).then(response => {
        if (response.status === 401) { window.location.href = "/login"; throw new Error("login-required"); }
        if (!response.ok) return response.text().then(message => { throw new Error(message); });
        return response.json();
    });
}

function showCartError(error) {
    if (error.message !== "login-required") window.alert(error.message);
}
