document.addEventListener("DOMContentLoaded", () => {
    const toast = document.getElementById("approvalToast");

    function showToast(message, success) {
        if (!toast) return;
        toast.hidden = false;
        toast.className = success ? "alert alert-success" : "alert alert-error";
        toast.textContent = message;
    }

    document.querySelectorAll(".submit-approval-button").forEach((button) => {
        button.addEventListener("click", async () => {
            if (button.disabled) return;
            const originalText = button.textContent;
            button.disabled = true;
            button.textContent = "Đang gửi...";

            try {
                const response = await fetch(button.dataset.url, {
                    method: "POST",
                    headers: { "Accept": "application/json" }
                });
                const body = await response.json();
                if (!response.ok || !body.success) {
                    throw new Error(body.message || "Không thể gửi duyệt sản phẩm.");
                }

                const row = button.closest("tr");
                const badge = row ? row.querySelector(".approval-badge") : null;
                if (badge) {
                    badge.textContent = "Chờ duyệt";
                    badge.classList.remove("badge-active", "badge-danger");
                    badge.classList.add("badge-draft");
                }
                button.remove();
                showToast(body.message, true);
            } catch (error) {
                button.disabled = false;
                button.textContent = originalText;
                showToast(error.message || "Có lỗi xảy ra. Vui lòng thử lại.", false);
            }
        });
    });
});
