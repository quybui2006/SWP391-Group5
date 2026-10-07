/* =========================================================
   FRESHFRUIT - PRODUCT DETAIL
========================================================= */


/* =========================================================
   CART
========================================================= */

let cartCount = Number(
    localStorage.getItem("freshfruit-cart-count") || 0
);


/* =========================================================
   INIT
========================================================= */

document.addEventListener("DOMContentLoaded", function () {

    updateCartCount();

    setupQuantityInput();

    restoreFavoriteState();

});


/* =========================================================
   CART COUNT
========================================================= */

function updateCartCount() {

    const badge = document.getElementById("cartCount");

    if (!badge) {
        return;
    }

    badge.textContent = cartCount;

}


/* =========================================================
   QUANTITY
========================================================= */

function getQuantity() {

    const input = document.getElementById("quantity");

    if (!input) {
        return 1;
    }

    let quantity = parseInt(input.value, 10);

    if (isNaN(quantity) || quantity < 1) {
        quantity = 1;
    }

    if (quantity > 99) {
        quantity = 99;
    }

    input.value = quantity;

    return quantity;

}


function increaseQuantity() {

    const input = document.getElementById("quantity");

    if (!input) {
        return;
    }

    let quantity = getQuantity();

    if (quantity < 99) {
        quantity++;
    }

    input.value = quantity;

}


function decreaseQuantity() {

    const input = document.getElementById("quantity");

    if (!input) {
        return;
    }

    let quantity = getQuantity();

    if (quantity > 1) {
        quantity--;
    }

    input.value = quantity;

}


function setupQuantityInput() {

    const input = document.getElementById("quantity");

    if (!input) {
        return;
    }

    input.addEventListener("change", function () {

        let quantity = parseInt(input.value, 10);

        if (isNaN(quantity) || quantity < 1) {
            quantity = 1;
        }

        if (quantity > 99) {
            quantity = 99;
        }

        input.value = quantity;

    });

}


/* =========================================================
   ADD TO CART
========================================================= */

function addProductToCart(button) {

    if (!button) {
        return;
    }

    const variantId = button.getAttribute("data-variant-id");

    if (!variantId) {
        return;
    }

    const quantity = getQuantity();

    cartCount += quantity;

    localStorage.setItem(
        "freshfruit-cart-count",
        cartCount
    );

    updateCartCount();


    const oldContent = button.innerHTML;

    button.innerHTML = `
        <span class="cart-btn-icon">✓</span>
        Đã thêm vào giỏ
    `;

    button.classList.add("added");

    button.disabled = true;


    setTimeout(function () {

        button.innerHTML = oldContent;

        button.classList.remove("added");

        button.disabled = false;

    }, 1000);

}


/* =========================================================
   BUY NOW
========================================================= */

function buyNow(button) {

    if (!button) {
        return;
    }

    const variantId = button.getAttribute("data-variant-id");

    if (!variantId) {
        return;
    }

    const quantity = getQuantity();


    /*
     * Lưu sản phẩm đang mua.
     * Khi backend Cart/Checkout được hoàn thiện,
     * có thể thay phần này bằng request tới backend.
     */

    const buyNowData = {
        variantId: Number(variantId),
        quantity: quantity
    };

    localStorage.setItem(
        "freshfruit-buy-now",
        JSON.stringify(buyNowData)
    );


    /*
     * Hiện tại chuyển tới giỏ hàng.
     * Sau này có thể đổi thành /checkout.
     */

    window.location.href = "/cart";

}


/* =========================================================
   FAVORITE
========================================================= */

function toggleFavorite(button) {

    if (!button) {
        return;
    }

    const variantId = button
        .closest(".product-detail-card")
        ?.querySelector("[data-variant-id]")
        ?.getAttribute("data-variant-id");


    if (!variantId) {
        return;
    }


    let favorites = JSON.parse(
        localStorage.getItem("freshfruit-favorites") || "[]"
    );


    const numericId = Number(variantId);

    const index = favorites.indexOf(numericId);


    if (index === -1) {

        favorites.push(numericId);

        button.classList.add("active");

        button.textContent = "♥";

        button.setAttribute(
            "aria-label",
            "Bỏ khỏi yêu thích"
        );

    } else {

        favorites.splice(index, 1);

        button.classList.remove("active");

        button.textContent = "♡";

        button.setAttribute(
            "aria-label",
            "Thêm vào yêu thích"
        );

    }


    localStorage.setItem(
        "freshfruit-favorites",
        JSON.stringify(favorites)
    );

}


/* =========================================================
   RESTORE FAVORITE
========================================================= */

function restoreFavoriteState() {

    const button = document.getElementById("favoriteBtn");

    if (!button) {
        return;
    }


    const productButton = document.querySelector(
        "[data-variant-id]"
    );

    if (!productButton) {
        return;
    }


    const variantId = Number(
        productButton.getAttribute("data-variant-id")
    );


    const favorites = JSON.parse(
        localStorage.getItem("freshfruit-favorites") || "[]"
    );


    if (favorites.includes(variantId)) {

        button.classList.add("active");

        button.textContent = "♥";

        button.setAttribute(
            "aria-label",
            "Bỏ khỏi yêu thích"
        );

    }

}


/* =========================================================
   PRODUCT IMAGE
========================================================= */

function handleImageError(image) {

    if (!image) {
        return;
    }

    image.style.display = "none";


    const placeholder =
        document.getElementById("imagePlaceholder");


    if (placeholder) {
        placeholder.style.display = "flex";
    }

}


/* =========================================================
   THUMBNAIL
========================================================= */

function selectThumbnail(button) {

    if (!button) {
        return;
    }


    document
        .querySelectorAll(".thumbnail")
        .forEach(function (thumbnail) {

            thumbnail.classList.remove("active");

        });


    button.classList.add("active");


    const thumbnailImage =
        button.querySelector("img");


    const mainImage =
        document.getElementById("mainProductImage");


    const placeholder =
        document.getElementById("imagePlaceholder");


    if (!thumbnailImage || !mainImage) {
        return;
    }


    mainImage.src = thumbnailImage.src;

    mainImage.style.display = "block";


    if (placeholder) {
        placeholder.style.display = "none";
    }

}


/* =========================================================
   INFORMATION TABS
========================================================= */

function switchInfoTab(tabId, button) {

    if (!tabId || !button) {
        return;
    }


    document
        .querySelectorAll(".info-tab")
        .forEach(function (tab) {

            tab.classList.remove("active");

        });


    document
        .querySelectorAll(".info-content")
        .forEach(function (content) {

            content.classList.remove("active");

        });


    button.classList.add("active");


    const target =
        document.getElementById(tabId);


    if (target) {

        target.classList.add("active");

    }

}