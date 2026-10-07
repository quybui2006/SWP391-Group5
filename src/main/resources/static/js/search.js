/* =========================================================
   FRESHFRUIT SEARCH
========================================================= */

let cartCount = Number(
    localStorage.getItem("freshfruit-cart-count") || 0
);


/* =========================================================
   PAGE LOAD
========================================================= */

document.addEventListener("DOMContentLoaded", function () {

    updateCartCount();

    setupProductCardKeyboard();

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
   PRODUCT DETAIL
========================================================= */

function openProductDetail(variantId) {

    if (!variantId) {
        return;
    }

    window.location.href = "/product/" + variantId;

}


/* =========================================================
   KEYBOARD SUPPORT
========================================================= */

function setupProductCardKeyboard() {

    const cards = document.querySelectorAll(".product-card");

    cards.forEach(function (card) {

        card.addEventListener("keydown", function (event) {

            /*
             * Enter / Space
             */
            if (
                event.key !== "Enter" &&
                event.key !== " "
            ) {
                return;
            }


            /*
             * Không xử lý nếu đang focus button
             */
            if (
                event.target.tagName === "BUTTON" ||
                event.target.tagName === "INPUT" ||
                event.target.tagName === "A"
            ) {
                return;
            }


            event.preventDefault();


            const onclickValue =
                card.getAttribute("onclick");


            if (!onclickValue) {
                return;
            }


            const match =
                onclickValue.match(
                    /openProductDetail\((\d+)\)/
                );


            if (match) {

                openProductDetail(
                    match[1]
                );

            }

        });

    });

}


/* =========================================================
   IMAGE ERROR
========================================================= */

function handleImageError(image) {

    if (!image) {
        return;
    }


    image.style.display = "none";


    const placeholder =
        image.nextElementSibling;


    if (placeholder) {

        placeholder.style.display = "grid";

    }

}


/* =========================================================
   ADD TO CART
========================================================= */

function addToCart(event, button) {

    /*
     * Không cho click truyền lên product-card.
     */
    if (event) {

        event.stopPropagation();

    }


    if (!button) {
        return;
    }


    const variantId =
        button.getAttribute("data-variant-id");


    if (!variantId) {
        return;
    }


    /*
     * Hiện tại chỉ cập nhật cart badge.
     *
     * Khi backend Cart hoàn thiện,
     * thay phần này bằng API POST.
     */
    cartCount += 1;


    localStorage.setItem(
        "freshfruit-cart-count",
        cartCount
    );


    updateCartCount();


    /*
     * Button feedback
     */
    const oldText =
        button.innerHTML;


    button.innerHTML = "✓";

    button.classList.add("added");

    button.disabled = true;


    setTimeout(function () {

        button.innerHTML = oldText;

        button.classList.remove("added");

        button.disabled = false;

    }, 800);

}


/* =========================================================
   FAVORITE
========================================================= */

function toggleFavorite(event, button) {

    /*
     * Không mở Product Detail.
     */
    if (event) {

        event.stopPropagation();

    }


    if (!button) {
        return;
    }


    const liked =
        button.classList.toggle("liked");


    if (liked) {

        button.textContent = "♥";

        button.setAttribute(
            "aria-label",
            "Bỏ khỏi yêu thích"
        );

    } else {

        button.textContent = "♡";

        button.setAttribute(
            "aria-label",
            "Thêm vào yêu thích"
        );

    }

}