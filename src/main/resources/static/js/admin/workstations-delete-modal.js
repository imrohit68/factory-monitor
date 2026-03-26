/**
 * Delete confirmation modal for admin workstation list.
 */
(function () {
    var modal = document.getElementById('ws-delete-modal');
    var backdrop = document.getElementById('ws-delete-backdrop');
    var nameEl = document.getElementById('ws-delete-name');
    var cancelBtn = document.getElementById('ws-delete-cancel');
    var confirmBtn = document.getElementById('ws-delete-confirm');
    var pendingForm = null;

    function openModal(formId, displayName) {
        pendingForm = document.getElementById(formId);
        if (!pendingForm) return;
        nameEl.textContent = displayName || 'this workstation';
        modal.classList.remove('hidden');
        document.body.classList.add('overflow-hidden');
        confirmBtn.focus();
    }

    function closeModal() {
        modal.classList.add('hidden');
        document.body.classList.remove('overflow-hidden');
        pendingForm = null;
    }

    document.querySelectorAll('.js-ws-delete-open').forEach(function (btn) {
        btn.addEventListener('click', function () {
            openModal(btn.getAttribute('data-form-id'), btn.getAttribute('data-ws-name'));
        });
    });

    cancelBtn.addEventListener('click', closeModal);
    backdrop.addEventListener('click', closeModal);

    confirmBtn.addEventListener('click', function () {
        if (pendingForm) pendingForm.submit();
        closeModal();
    });

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && !modal.classList.contains('hidden')) {
            closeModal();
        }
    });
})();
