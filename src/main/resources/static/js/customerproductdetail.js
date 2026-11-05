/* =========================================================
   FRESHFRUIT - CUSTOMER PRODUCT DETAIL (SHOPEE STYLE JS)
========================================================= */

let cartCount = Number(localStorage.getItem("freshfruit-cart-count") || 0);

document.addEventListener("DOMContentLoaded", function () {
    updateCartCount();
    setupQuantityInput();
});

/* =========================================================
   CART COUNT
========================================================= */
function updateCartCount() {
    const badge = document.getElementById("cartCount");
    if (!badge) return;
    badge.textContent = window.cartCount !== undefined ? window.cartCount : cartCount;
}

/* =========================================================
   QUANTITY STEPPER
========================================================= */
function getQuantity() {
    const input = document.getElementById("quantity");
    if (!input) return 1;
    let quantity = parseInt(input.value, 10);
    if (isNaN(quantity) || quantity < 1) quantity = 1;
    if (quantity > 99) quantity = 99;
    input.value = quantity;
    return quantity;
}

function increaseQuantity() {
    const input = document.getElementById("quantity");
    if (!input) return;
    let quantity = getQuantity();
    if (quantity < 99) quantity++;
    input.value = quantity;
}

function decreaseQuantity() {
    const input = document.getElementById("quantity");
    if (!input) return;
    let quantity = getQuantity();
    if (quantity > 1) quantity--;
    input.value = quantity;
}

function setupQuantityInput() {
    const input = document.getElementById("quantity");
    if (!input) return;
    input.addEventListener("change", function () {
        getQuantity();
    });
}

/* =========================================================
   THUMBNAIL GALLERY & SPEC SELECTION
========================================================= */
function handleImageError(image) {
    if (!image) return;
    image.style.display = "none";
    const placeholder = document.getElementById("imagePlaceholder");
    if (placeholder) {
        placeholder.style.display = "flex";
    }
}

function selectThumbnail(button) {
    if (!button) return;
    document.querySelectorAll(".thumb-box").forEach(function (thumb) {
        thumb.classList.remove("active");
    });
    button.classList.add("active");

    const thumbImg = button.querySelector("img");
    const mainImg = document.getElementById("mainProductImage");
    const placeholder = document.getElementById("imagePlaceholder");

    if (thumbImg && mainImg) {
        mainImg.src = thumbImg.src;
        mainImg.style.display = "block";
        if (placeholder) placeholder.style.display = "none";
    }
}

function selectSpec(button) {
    if (!button) return;
    document.querySelectorAll(".variant-spec-chip").forEach(function (chip) {
        chip.classList.remove("active");
    });
    button.classList.add("active");
}

/* =========================================================
   SOCIAL SHARE & TOAST
========================================================= */
function shareSocial(network) {
    const url = encodeURIComponent(window.location.href);
    const title = encodeURIComponent(document.title);
    let shareUrl = "";

    switch (network) {
        case "facebook":
            shareUrl = `https://www.facebook.com/sharer/sharer.php?u=${url}`;
            break;
        case "messenger":
            shareUrl = `fb-messenger://share/?link=${url}`;
            break;
        case "pinterest":
            shareUrl = `https://pinterest.com/pin/create/button/?url=${url}&description=${title}`;
            break;
        case "twitter":
            shareUrl = `https://twitter.com/intent/tweet?url=${url}&text=${title}`;
            break;
    }

    if (shareUrl) {
        window.open(shareUrl, "_blank", "width=600,height=400");
    }
}

function copyProductLink() {
    if (navigator.clipboard) {
        navigator.clipboard.writeText(window.location.href).then(function () {
            showShopeeToast("Đã sao chép liên kết sản phẩm vào bộ nhớ tạm!");
        }).catch(function () {
            showShopeeToast("Liên kết: " + window.location.href);
        });
    } else {
        showShopeeToast("Liên kết: " + window.location.href);
    }
}

function showShopeeToast(message) {
    const toast = document.getElementById("shopeeToast");
    if (!toast) return;
    toast.textContent = message;
    toast.style.display = "block";
    setTimeout(function () {
        toast.style.display = "none";
    }, 2500);
}

/* =========================================================
   SHOPEE PRODUCT REVIEWS HANDLERS
========================================================= */
const ratingDescriptions = {
    1: "Rất tệ (1 sao)",
    2: "Không hài lòng (2 sao)",
    3: "Bình thường (3 sao)",
    4: "Hài lòng (4 sao)",
    5: "Tuyệt vời (5 sao)"
};

let currentSelectedRating = 5;

function setRating(val) {
    currentSelectedRating = val;
    const input = document.getElementById("ratingInput");
    if (input) input.value = val;
    renderStars(val);
    const hint = document.getElementById("ratingTextHint");
    if (hint) hint.textContent = ratingDescriptions[val] || (val + " sao");
}

function previewRating(val) {
    renderStars(val);
    const hint = document.getElementById("ratingTextHint");
    if (hint) hint.textContent = ratingDescriptions[val] || (val + " sao");
}

function restoreRating() {
    renderStars(currentSelectedRating);
    const hint = document.getElementById("ratingTextHint");
    if (hint) hint.textContent = ratingDescriptions[currentSelectedRating] || (currentSelectedRating + " sao");
}

function renderStars(val) {
    const stars = document.querySelectorAll("#starPickerGroup .interactive-star");
    stars.forEach(function (star) {
        const starVal = Number(star.getAttribute("data-val"));
        if (starVal <= val) {
            star.classList.add("active");
        } else {
            star.classList.remove("active");
        }
    });
}

function addReviewTag(text) {
    const textarea = document.getElementById("reviewContentInput");
    if (!textarea) return;
    if (textarea.value.trim().length > 0) {
        textarea.value = textarea.value.trim() + " " + text;
    } else {
        textarea.value = text;
    }
    updateCharCount(textarea);
    textarea.focus();
}

function updateCharCount(textarea) {
    const countEl = document.getElementById("charCount");
    if (countEl && textarea) {
        countEl.textContent = textarea.value.length + "/2000";
    }
}

function filterReviews(ratingVal, button) {
    document.querySelectorAll(".filter-tab-chip").forEach(function (chip) {
        chip.classList.remove("active");
    });
    if (button) button.classList.add("active");

    const items = document.querySelectorAll("#reviewsList .shopee-review-item");
    let visibleCount = 0;

    items.forEach(function (item) {
        const itemStar = item.getAttribute("data-star");
        if (ratingVal === 'all' || itemStar === String(ratingVal)) {
            item.style.display = "flex";
            visibleCount++;
        } else {
            item.style.display = "none";
        }
    });

    const emptyBox = document.getElementById("reviewsEmpty");
    if (emptyBox) {
        if (visibleCount === 0) {
            emptyBox.style.display = "block";
            const emptySub = emptyBox.querySelector("p");
            if (emptySub) {
                emptySub.textContent = ratingVal === 'all'
                    ? "Chưa có đánh giá nào cho sản phẩm này."
                    : "Chưa có đánh giá nào mức " + ratingVal + " sao.";
            }
        } else {
            emptyBox.style.display = "none";
        }
    }
}

// AJAX review form submission
document.addEventListener("DOMContentLoaded", function () {
    const reviewForm = document.getElementById("reviewForm");
    if (reviewForm) {
        reviewForm.addEventListener("submit", function (e) {
            e.preventDefault();

            const ratingInput = document.getElementById("ratingInput");
            const rating = ratingInput ? Number(ratingInput.value) : 5;
            const contentInput = document.getElementById("reviewContentInput");
            const content = contentInput ? contentInput.value.trim() : "";
            const submitBtn = document.getElementById("submitReviewBtn");

            if (!content) {
                alert("Vui lòng nhập cảm nhận đánh giá của bạn.");
                return;
            }

            const pathParts = window.location.pathname.split("/").filter(Boolean);
            const productId = pathParts[pathParts.length - 1];

            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.innerHTML = 'Đang gửi...';
            }

            fetch("/api/products/" + productId + "/reviews", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    rating: rating,
                    content: content
                })
            })
            .then(function (res) {
                if (res.status === 401) {
                    window.location.href = "/login?continueTo=" + encodeURIComponent(window.location.pathname);
                    return null;
                }
                return res.json();
            })
            .then(function (data) {
                if (!data) return;
                if (data.success) {
                    window.location.reload();
                } else {
                    alert(data.message || "Có lỗi xảy ra khi gửi đánh giá.");
                    if (submitBtn) {
                        submitBtn.disabled = false;
                        submitBtn.innerHTML = 'Hoàn Thành';
                    }
                }
            })
            .catch(function () {
                reviewForm.submit();
            });
        });
    }
});
