/* =========================================================
   FRESHFRUIT HOME
========================================================= */

window.cartCount = Number(
    localStorage.getItem("freshfruit-cart-count") || 0
);


/* =========================================================
   INITIALIZE
========================================================= */

document.addEventListener("DOMContentLoaded", function () {

    updateCartCount();

    setupProductCardKeyboard();

});


/* =========================================================
   CART COUNT
========================================================= */

function updateCartCount() {

    const cartBadge = document.getElementById("cartCount");

    if (!cartBadge) {
        return;
    }

    cartBadge.textContent = window.cartCount;

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
   PRODUCT CARD KEYBOARD SUPPORT
========================================================= */

function setupProductCardKeyboard() {

    const cards = document.querySelectorAll(".product-card");

    cards.forEach(function (card) {

        card.addEventListener("keydown", function (event) {

            /*
             * Enter / Space -> Product Detail
             */
            if (event.key === "Enter" || event.key === " ") {

                /*
                 * Không xử lý nếu focus đang ở button
                 */
                if (
                    event.target.tagName === "BUTTON" ||
                    event.target.tagName === "A" ||
                    event.target.tagName === "INPUT"
                ) {
                    return;
                }

                event.preventDefault();

                const onclickValue = card.getAttribute("onclick");

                if (!onclickValue) {
                    return;
                }

                /*
                 * Extract variant ID từ:
                 * openProductDetail(123)
                 */
                const match = onclickValue.match(
                    /openProductDetail\((\d+)\)/
                );

                if (match) {
                    openProductDetail(match[1]);
                }

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

    const placeholder = image.nextElementSibling;

    if (placeholder) {
        placeholder.style.display = "grid";
    }

}


/* =========================================================
   SEARCH PRODUCT ON HOME
========================================================= */

function filterProducts() {

    const input = document.getElementById("productSearch");

    if (!input) {
        return;
    }

    const keyword = input.value
        .trim()
        .toLowerCase();

    const cards = document.querySelectorAll(".product-card");

    const noResult = document.getElementById("noResult");

    let visibleCount = 0;


    cards.forEach(function (card) {

        const productName =
            card.getAttribute("data-name") || "";

        const normalizedName =
            productName.toLowerCase();


        const matched =
            normalizedName.includes(keyword);


        if (matched) {

            card.style.display = "";

            visibleCount++;

        } else {

            card.style.display = "none";

        }

    });


    /*
     * Show "no result"
     */
    if (noResult) {

        if (cards.length > 0 && visibleCount === 0) {

            noResult.style.display = "block";

        } else {

            noResult.style.display = "none";

        }

    }

}


/* =========================================================
   ADD TO CART
========================================================= */

function addToCart(event, button) {

    /*
     * QUAN TRỌNG:
     * Không cho click button truyền lên product-card.
     */
    if (event) {
        event.stopPropagation();
    }


    if (!button) {
        return;
    }


    // Tan PTH integration: persist the item through the shared cart endpoint.
    if (window.addProductToCart) window.addProductToCart(button, 1);

}


/* =========================================================
   FAVORITE
========================================================= */

function toggleFavorite(event, button) {

    /*
     * Không mở Product Detail
     */
    if (event) {
        event.stopPropagation();
    }


    if (!button) {
        return;
    }


    const isFavorite =
        button.classList.toggle("active");


    if (isFavorite) {

        button.innerHTML = "♥";

        button.setAttribute(
            "aria-label",
            "Bỏ khỏi yêu thích"
        );

    } else {

        button.innerHTML = "♡";

        button.setAttribute(
            "aria-label",
            "Thêm vào yêu thích"
        );

    }

}
