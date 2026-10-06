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


        document
            .querySelectorAll(".heart-btn")
            .forEach(function (button) {

                button.addEventListener(
                    "click",
                    function () {

                        button.classList.toggle(
                            "liked"
                        );

                        button.textContent =
                            button.classList.contains(
                                "liked"
                            )
                                ? "♥"
                                : "♡";

                    }
                );

            });

    }
);


/* ================= CART COUNT ================= */

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

    const variantId =
        button.dataset.variantId;

    console.log(
        "Add ProductVariant:",
        variantId
    );


    cartCount++;


    localStorage.setItem(
        "freshfruit-cart-count",
        cartCount
    );


    updateCartCount();


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