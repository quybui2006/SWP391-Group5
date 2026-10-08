// Preview the selected image locally; the file is only uploaded when the form is submitted.
const imageInput = document.getElementById("imageFile");
const imagePreview = document.getElementById("imagePreview");

if (imageInput && imagePreview) {
    imageInput.addEventListener("change", () => {
        const file = imageInput.files[0];
        if (!file) {
            imagePreview.hidden = true;
            imagePreview.removeAttribute("src");
            return;
        }

        imagePreview.src = URL.createObjectURL(file);
        imagePreview.hidden = false;
    });
}
