/* =========================================================
   FRESHFRUIT - USER MENU DROPDOWN (CLICK TO TOGGLE)
   Dùng chung cho các trang có header user-avatar + user-dropdown
   (home, search, customerproductdetail, customershop)
========================================================= */

(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        initUserMenu();
    });

    function initUserMenu() {
        const container = document.querySelector(".user-menu-container");
        if (!container) return;

        const avatarBtn = container.querySelector(".user-avatar");
        const dropdown = container.querySelector(".user-dropdown");
        if (!avatarBtn || !dropdown) return;

        // Toggle khi click vào avatar
        avatarBtn.addEventListener("click", function (e) {
            e.preventDefault();
            e.stopPropagation();
            container.classList.toggle("open");
        });

        // Click ra ngoài -> đóng dropdown
        document.addEventListener("click", function (e) {
            if (!container.contains(e.target)) {
                closeMenu();
            }
        });

        // Phím Escape -> đóng dropdown
        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape") {
                closeMenu();
            }
        });
    }

    function closeMenu() {
        const container = document.querySelector(".user-menu-container");
        if (container) {
            container.classList.remove("open");
        }
    }
})();
