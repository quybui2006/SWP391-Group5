(() => {
    const buttons = [...document.querySelectorAll('[data-favorite-id]')];
    const render = (button, saved) => {
        button.dataset.saved = String(saved);
        button.classList.toggle('active', saved);
        button.classList.toggle('liked', saved);
        button.textContent = saved ? '♥' : '♡';
        button.setAttribute('aria-pressed', String(saved));
        button.setAttribute('aria-label', saved ? 'Bỏ khỏi yêu thích' : 'Thêm vào yêu thích');
    };
    const ready = fetch('/customer/account/favorites/ids', {cache: 'no-store'})
        .then(async response => {
            if (response.status === 401) return [];
            if (!response.ok) throw new Error();
            return response.json();
        }).then(ids => buttons.forEach(button => render(button, ids.includes(Number(button.dataset.favoriteId)))))
        .catch(() => {});
    window.toggleFavorite = async (eventOrButton, target) => {
        const button = target || eventOrButton;
        if (target) { eventOrButton.preventDefault(); eventOrButton.stopPropagation(); }
        if (!button || button.disabled) return;
        button.disabled = true;
        try {
            await ready;
            const response = await fetch('/customer/account/favorites/save', {
                method: 'POST',
                body: new URLSearchParams({variantId: button.dataset.favoriteId, favorite: button.dataset.saved !== 'true'})
            });
            if (response.status === 401) {
                window.location.href = '/login?continueTo=' + encodeURIComponent(window.location.pathname + window.location.search);
                return;
            }
            if (!response.ok) throw new Error();
            const result = await response.json();
            buttons.filter(other => other.dataset.favoriteId === button.dataset.favoriteId)
                .forEach(other => render(other, result.favorite));
        } catch (_) {
            window.alert('Không thể cập nhật yêu thích. Vui lòng thử lại.');
        } finally { button.disabled = false; }
    };
})();
