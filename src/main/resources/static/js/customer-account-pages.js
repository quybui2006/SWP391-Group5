document.querySelectorAll('[data-pending]').forEach(control => {
    control.addEventListener('click', event => {
        event.preventDefault();
        window.alert('Tính năng đang phát triển.');
    });
});

// Avatar size/type validation and preview are handled by /js/upload-validation.js.
