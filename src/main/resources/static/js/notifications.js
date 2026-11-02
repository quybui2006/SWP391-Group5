/* =========================================================
   FRESHFRUIT - SHOPEE STYLE NOTIFICATIONS JS
========================================================= */

(function () {
    "use strict";

    let notificationsData = {
        loggedIn: false,
        unreadCount: 0,
        notifications: []
    };

    document.addEventListener("DOMContentLoaded", function () {
        initNotifications();
    });

    function initNotifications() {
        const notifWrapper = document.getElementById("notifWrapper");
        const bellBtn = document.getElementById("notifBellBtn");
        const notifDropdown = document.getElementById("notifDropdown");

        if (!notifWrapper || !bellBtn || !notifDropdown) {
            return;
        }

        // Toggle on bell click (for mobile and touch devices)
        bellBtn.addEventListener("click", function (e) {
            e.preventDefault();
            e.stopPropagation();
            const isOpen = notifDropdown.classList.contains("show");
            if (isOpen) {
                closeDropdown();
            } else {
                openDropdown();
            }
        });

        // Close when clicking outside
        document.addEventListener("click", function (e) {
            if (!notifWrapper.contains(e.target)) {
                closeDropdown();
            }
        });

        // Escape key closes dropdown
        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape") {
                closeDropdown();
            }
        });

        // Fetch notifications from server
        fetchNotifications();
    }

    function openDropdown() {
        const notifDropdown = document.getElementById("notifDropdown");
        const notifWrapper = document.getElementById("notifWrapper");
        if (notifDropdown) {
            notifDropdown.classList.add("show");
            notifDropdown.setAttribute("aria-hidden", "false");
        }
        if (notifWrapper) {
            notifWrapper.classList.add("active");
        }
    }

    function closeDropdown() {
        const notifDropdown = document.getElementById("notifDropdown");
        const notifWrapper = document.getElementById("notifWrapper");
        if (notifDropdown) {
            notifDropdown.classList.remove("show");
            notifDropdown.setAttribute("aria-hidden", "true");
        }
        if (notifWrapper) {
            notifWrapper.classList.remove("active");
        }
    }

    function fetchNotifications() {
        fetch("/api/notifications")
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("Lỗi khi tải thông báo");
                }
                return response.json();
            })
            .then(function (data) {
                notificationsData = data;
                updateBadge(data.unreadCount || 0);
                renderDropdown(data);
            })
            .catch(function (error) {
                console.warn("Notification load error:", error);
                // Render fallback guest view
                renderDropdown({ loggedIn: false, unreadCount: 0, notifications: [] });
            });
    }

    function updateBadge(count) {
        const badge = document.getElementById("notifBadge");
        if (!badge) return;

        if (count > 0) {
            badge.textContent = count > 99 ? "99+" : count;
            badge.style.display = "inline-block";
        } else {
            badge.style.display = "none";
        }
    }

    window.refreshNotifBadge = updateBadge;

    function renderDropdown(data) {
        const dropdown = document.getElementById("notifDropdown");
        if (!dropdown) return;

        // 1. Guest State (Not Logged In) - Shopee Style
        if (!data.loggedIn) {
            dropdown.innerHTML = `
                <div class="notif-header">
                    <span class="notif-header-title">
                        <span class="header-icon">🔔</span> Thông Báo Mới Nhận
                    </span>
                </div>
                <div class="notif-guest-box">
                    <div class="notif-guest-illustration">🔔</div>
                    <h3>Đăng nhập để xem Thông báo</h3>
                    <p>Đăng nhập để cập nhật tình trạng đơn hàng, nhận ưu đãi trái cây tươi và nhiều phần quà hấp dẫn.</p>
                    <div class="notif-guest-actions">
                        <a href="/login" class="notif-guest-login">Đăng nhập</a>
                        <a href="/register" class="notif-guest-register">Đăng ký</a>
                    </div>
                </div>
                <a href="/login" class="notif-footer">Xem tất cả ➔</a>
            `;
            return;
        }

        // 2. Logged In State
        const list = data.notifications || [];
        const hasUnread = (data.unreadCount || 0) > 0;

        let html = `
            <div class="notif-header">
                <span class="notif-header-title">
                    <span class="header-icon">🔔</span> Thông Báo Mới Nhận
                </span>
                ${hasUnread ? '<button type="button" class="notif-mark-read-all" id="notifMarkAllBtn">✓ Đã đọc tất cả</button>' : ''}
            </div>
        `;

        if (list.length === 0) {
            html += `
                <div class="notif-empty-box">
                    <div class="empty-icon">🔔</div>
                    <p>Hiện chưa có thông báo mới nào dành cho bạn.</p>
                </div>
            `;
        } else {
            html += `<div class="notif-list" id="notifScrollList">`;
            list.forEach(function (n) {
                const isUnread = !n.read;
                html += `
                    <div class="notif-item ${isUnread ? 'unread' : ''}" 
                         data-id="${n.id}" 
                         data-target="${n.targetUrl || '/customer/account/notifications'}">
                        <div class="notif-item-icon ${n.badgeClass || ''}">${n.icon || '🔔'}</div>
                        <div class="notif-item-body">
                            <h4 class="notif-item-title">${escapeHtml(n.title)}</h4>
                            <p class="notif-item-desc">${escapeHtml(n.content)}</p>
                            <span class="notif-item-time">${escapeHtml(n.timeAgo || '')}</span>
                        </div>
                    </div>
                `;
            });
            html += `</div>`;
        }

        html += `<a href="/customer/account/notifications" class="notif-footer">Xem tất cả thông báo ➔</a>`;

        dropdown.innerHTML = html;

        // Attach event listeners to items
        const items = dropdown.querySelectorAll(".notif-item");
        items.forEach(function (item) {
            item.addEventListener("click", function (e) {
                e.preventDefault();
                const id = this.getAttribute("data-id");
                const targetUrl = this.getAttribute("data-target");

                if (this.classList.contains("unread")) {
                    markSingleAsRead(id, this);
                }

                if (targetUrl) {
                    setTimeout(function () {
                        window.location.href = targetUrl;
                    }, 120);
                }
            });
        });

        // Attach event listener for "Mark all read"
        const markAllBtn = dropdown.getElementById ? dropdown.getElementById("notifMarkAllBtn") : dropdown.querySelector("#notifMarkAllBtn");
        if (markAllBtn) {
            markAllBtn.addEventListener("click", function (e) {
                e.preventDefault();
                e.stopPropagation();
                markAllAsRead();
            });
        }
    }

    function markSingleAsRead(id, element) {
        fetch("/api/notifications/" + id + "/read", {
            method: "POST"
        })
            .then(function (res) { return res.json(); })
            .then(function (result) {
                if (result.success) {
                    if (element) {
                        element.classList.remove("unread");
                    }
                    updateBadge(result.unreadCount || 0);
                    // Hide mark all button if unread reaches 0
                    if (result.unreadCount === 0) {
                        const markAllBtn = document.querySelector("#notifMarkAllBtn");
                        if (markAllBtn) markAllBtn.remove();
                    }
                }
            })
            .catch(function (err) {
                console.error("Mark read error:", err);
            });
    }

    function markAllAsRead() {
        fetch("/api/notifications/read-all", {
            method: "POST"
        })
            .then(function (res) { return res.json(); })
            .then(function (result) {
                if (result.success) {
                    const items = document.querySelectorAll(".notif-item.unread");
                    items.forEach(function (item) {
                        item.classList.remove("unread");
                    });
                    updateBadge(0);
                    const markAllBtn = document.querySelector("#notifMarkAllBtn");
                    if (markAllBtn) markAllBtn.remove();
                }
            })
            .catch(function (err) {
                console.error("Mark all read error:", err);
            });
    }

    function escapeHtml(str) {
        if (!str) return "";
        return str
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }
})();
