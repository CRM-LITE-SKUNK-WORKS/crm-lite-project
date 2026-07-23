/* CRM Lite login — progressive enhancement only.
 * The server (Keycloak) remains the sole authority: it validates credentials and
 * re-renders field errors. This script only improves UX and never blocks a submit
 * when JavaScript is unavailable (the button starts enabled in the markup).
 *
 * FR-AUTH-01 UI acceptance criteria handled here:
 *   AC-01-02  login button passive (disabled) while username OR password is empty
 *   AC-01-06  surfaced errors clear as soon as the user edits a field
 *   AC-01-08  password show/hide toggle (masking is the default; this reveals)
 */
(function () {
  'use strict';

  var form = document.getElementById('kc-form-login');
  if (!form) { return; }

  var username = document.getElementById('username');
  var password = document.getElementById('password');
  var submit = document.getElementById('kc-login');
  var card = form.parentNode;
  var alertBox = card ? card.querySelector('.eds-alert') : null;

  // --- AC-01-02: disable submit until both fields have content ------------
  function syncSubmit() {
    if (!submit || !username || !password) { return; }
    var ready = username.value.trim().length > 0 && password.value.length > 0;
    submit.disabled = !ready;
    submit.classList.toggle('is-disabled', !ready);
  }

  // --- AC-01-06: clear the surfaced error(s) once the user edits a field --
  // A credential error highlights both inputs and shows one banner, so editing
  // either field clears the banner and both red borders.
  function clearErrors() {
    var invalids = form.querySelectorAll('.eds-input.is-invalid');
    for (var i = 0; i < invalids.length; i++) { invalids[i].classList.remove('is-invalid'); }
    if (username) { username.setAttribute('aria-invalid', 'false'); }
    if (password) { password.setAttribute('aria-invalid', 'false'); }
    if (alertBox) { alertBox.style.display = 'none'; }
  }

  function onInput() {
    syncSubmit();
    clearErrors();
  }

  if (username) { username.addEventListener('input', onInput); }
  if (password) { password.addEventListener('input', onInput); }
  syncSubmit();

  // --- AC-01-08: password visibility toggle ------------------------------
  var toggle = form.querySelector('.eds-input__toggle');
  if (toggle && password) {
    toggle.addEventListener('click', function () {
      var revealed = password.getAttribute('type') === 'text';
      password.setAttribute('type', revealed ? 'password' : 'text');
      toggle.setAttribute('aria-pressed', revealed ? 'false' : 'true');
      toggle.classList.toggle('is-on', !revealed);
    });
  }

  // Focus username on load if it is empty (keeps caret behaviour natural).
  if (username && !username.value) {
    try { username.focus(); } catch (e) { /* ignore */ }
  }
})();
