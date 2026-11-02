/**
 * In-web Notification Center Dropdown Script
 */
document.addEventListener('DOMContentLoaded', function () {
    const bellBtn = document.getElementById('globalNotifBellBtn');
    const dropdownMenu = document.getElementById('globalNotifDropdownMenu');
    const badgeCount = document.getElementById('globalNotifBadge');
    const dropdownList = document.getElementById('globalNotifList');
    const markAllReadBtn = document.getElementById('globalNotifMarkAllRead');

    if (!bellBtn) return;

    // Fetch initial notification state
    loadNotifications();

    // Toggle dropdown
    bellBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        if (dropdownMenu) {
            const isShown = dropdownMenu.classList.contains('show');
            if (!isShown) {
                dropdownMenu.classList.add('show');
                loadNotifications();
            } else {
                dropdownMenu.classList.remove('show');
            }
        }
    });

    // Close on outside click
    document.addEventListener('click', function (e) {
        if (dropdownMenu && !dropdownMenu.contains(e.target) && !bellBtn.contains(e.target)) {
            dropdownMenu.classList.remove('show');
        }
        document.querySelectorAll('.user-dropdown.show').forEach(d => {
            d.classList.remove('show');
        });
    });

    // Support click/tap toggle for user avatar menu so it never vanishes prematurely
    const userContainers = document.querySelectorAll('.user-menu-container');
    userContainers.forEach(container => {
        const avatar = container.querySelector('.user-avatar');
        const userDropdown = container.querySelector('.user-dropdown');
        if (avatar && userDropdown) {
            avatar.addEventListener('click', function (e) {
                e.stopPropagation();
                if (dropdownMenu) dropdownMenu.classList.remove('show');
                userDropdown.classList.toggle('show');
            });
            userDropdown.addEventListener('click', function (e) {
                e.stopPropagation();
            });
        }
    });

    // Mark all as read
    if (markAllReadBtn) {
        markAllReadBtn.addEventListener('click', function (e) {
            e.stopPropagation();
            fetch('/api/notifications/read-all', { method: 'POST' })
                .then(res => res.json())
                .then(data => {
                    if (data.success) {
                        updateBadge(0);
                        document.querySelectorAll('.notif-dropdown-item.unread').forEach(item => {
                            item.classList.remove('unread');
                        });
                    }
                })
                .catch(console.error);
        });
    }

    function loadNotifications() {
        fetch('/api/notifications')
            .then(res => {
                if (!res.ok) throw new Error('Not logged in or error');
                return res.json();
            })
            .then(data => {
                if (!data.loggedIn) {
                    if (badgeCount) badgeCount.style.display = 'none';
                    return;
                }

                updateBadge(data.unreadCount || 0);

                if (dropdownList) {
                    renderDropdownItems(data.notifications || []);
                }
            })
            .catch(() => {
                if (badgeCount) badgeCount.style.display = 'none';
            });
    }

    function updateBadge(count) {
        if (!badgeCount) return;
        if (count > 0) {
            badgeCount.textContent = count > 99 ? '99+' : count;
            badgeCount.style.display = 'flex';
        } else {
            badgeCount.textContent = '0';
            badgeCount.style.display = 'none';
        }
    }

    function renderDropdownItems(notifications) {
        if (notifications.length === 0) {
            dropdownList.innerHTML = `
                <div class="notif-empty-box">
                    <div class="icon">🔔</div>
                    <p style="margin:0;font-size:13px;">Bạn chưa có thông báo nào</p>
                </div>
            `;
            return;
        }

        dropdownList.innerHTML = notifications.slice(0, 6).map(n => `
            <a href="${n.targetUrl || '/customer/account/notifications'}" 
               class="notif-dropdown-item ${!n.read ? 'unread' : ''}" 
               data-id="${n.id}">
                <div class="notif-item-icon-box ${n.badgeClass || 'notif-badge-system'}">
                    ${n.icon || '🔔'}
                </div>
                <div class="notif-item-content">
                    <h5 class="notif-item-title">${escapeHtml(n.title)}</h5>
                    <p class="notif-item-desc">${escapeHtml(n.content)}</p>
                    <span class="notif-item-meta">${n.timeAgo || ''}</span>
                </div>
            </a>
        `).join('');

        // Add click listener to mark read
        dropdownList.querySelectorAll('.notif-dropdown-item').forEach(item => {
            item.addEventListener('click', function () {
                const id = this.getAttribute('data-id');
                if (id && this.classList.contains('unread')) {
                    fetch('/api/notifications/' + id + '/read', { method: 'POST' }).catch(console.error);
                }
            });
        });
    }

    function escapeHtml(text) {
        if (!text) return '';
        const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
        return text.replace(/[&<>"']/g, m => map[m]);
    }
});
