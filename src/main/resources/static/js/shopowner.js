function filterVariants(){const q=(document.getElementById('variantSearch')?.value||'').toLowerCase();document.querySelectorAll('#variantsTable tbody tr').forEach(row=>{row.style.display=row.innerText.toLowerCase().includes(q)?'':'none'})}
function showMockSave(event){event.preventDefault();const toast=document.getElementById('toast');if(toast){toast.classList.add('show');setTimeout(()=>toast.classList.remove('show'),2200)}}
document.addEventListener('click',event=>{if(event.target.closest('[data-pending]')){event.preventDefault();alert('Tính năng đang phát triển.')}})
