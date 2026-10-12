document.querySelectorAll('[data-pending]').forEach(control => {
    control.addEventListener('click', event => {
        event.preventDefault();
        window.alert('Tính năng đang phát triển.');
    });
});
