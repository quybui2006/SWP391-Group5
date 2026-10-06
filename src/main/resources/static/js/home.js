/* ================= CART ================= */

let cartCount =
    Number(
        localStorage.getItem(
            "freshfruit-cart-count"
        ) || 0
    );


/* ================= PAGE LOAD ================= */

document.addEventListener(
    "DOMContentLoaded",
    function () {

        updateCartCount();


        /*
         * Heart / Favorite
         */

        document
            .querySelectorAll(".heart-btn")
            .forEach(function (button) {

                button.addEventListener(
                    "click",
                    function () {

                        button.classList.toggle(
                            "liked"
                        );


                        if (
                            button.classList.contains(
                                "liked"
                            )
                        ) {

                            button.textContent = "♥";

                        } else {

                            button.textContent = "♡";

                        }

                    }
                );

            });

    }
);


/* ================= UPDATE CART ================= */

function updateCartCount() {

    const badge =
        document.getElementById(
            "cartCount"
        );


    if (badge) {

        badge.textContent =
            cartCount;

    }

}


/* ================= ADD TO CART ================= */

function addToCart(button) {

    /*
     * Lấy ID ProductVariant
     *
     * Sau này khi bạn làm Cart backend,
     * có thể dùng:
     *
     * button.dataset.variantId
     */

    const variantId =
        button.dataset.variantId;


    console.log(
        "Add ProductVariant:",
        variantId
    );


    /*
     * Tạm thời tăng số lượng giỏ hàng
     */

    cartCount++;


    localStorage.setItem(
        "freshfruit-cart-count",
        cartCount
    );


    updateCartCount();


    /*
     * Animation button
     */

    const oldText =
        button.textContent;


    button.textContent = "✓";

    button.disabled = true;


    setTimeout(
        function () {

            button.textContent =
                oldText;

            button.disabled = false;

        },
        700
    );

}


/* ================= SEARCH ================= */

function filterProducts() {

    const searchInput =
        document.getElementById(
            "productSearch"
        );


    if (!searchInput) {
        return;
    }


    const query =
        searchInput.value
            .trim()
            .toLowerCase();


    const cards =
        document.querySelectorAll(
            ".product-card"
        );


    let visibleCount = 0;


    cards.forEach(
        function (card) {

            const productName =
                (
                    card.dataset.name ||
                    ""
                ).toLowerCase();


            const match =
                !query ||
                productName.includes(
                    query
                );


            if (match) {

                card.style.display = "";

                visibleCount++;

            } else {

                card.style.display = "none";

            }

        }
    );


    /*
     * Hiển thị thông báo
     * nếu không tìm thấy sản phẩm
     */

    const noResult =
        document.getElementById(
            "noResult"
        );


    if (noResult) {

        if (
            cards.length > 0 &&
            visibleCount === 0
        ) {

            noResult.style.display =
                "block";

        } else {

            noResult.style.display =
                "none";

        }

    }

}


/* ================= HEADER SEARCH ================= */

function focusSearch() {

    const searchInput =
        document.getElementById(
            "productSearch"
        );


    if (!searchInput) {
        return;
    }


    const productsSection =
        document.getElementById(
            "products"
        );


    if (productsSection) {

        productsSection.scrollIntoView({
            behavior: "smooth"
        });

    }


    setTimeout(
        function () {

            searchInput.focus();

        },
        350
    );

}