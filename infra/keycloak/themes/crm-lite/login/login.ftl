<#--
  CRM Lite — Keycloak login page (FR-AUTH-01).
  Standalone template (does not use the parent registrationLayout macro) so the
  markup matches the analyst "Login v2" mock exactly. All auth plumbing still
  comes from Keycloak: url.loginAction (carries the auth-session/CSRF), messages,
  messagesPerField, locale. Angular never renders this page (ADR-006/007).
-->
<#assign lang = (locale.currentLanguageTag)!'en'>
<!DOCTYPE html>
<html lang="${lang}" class="eds-html">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex, nofollow">
  <title>${msg("edsLoginTitle")}</title>
  <link rel="icon" type="image/svg+xml" href="${url.resourcesPath}/img/favicon.svg">
  <link rel="stylesheet" href="${url.resourcesPath}/css/login.css">
</head>
<body class="eds-body">
  <div class="eds-login" data-testid="login-page">

    <#-- Brand panel (decorative): white Etiya wordmark centered on navy -->
    <aside class="eds-brand" aria-hidden="true">
      <img class="eds-brand__logo" src="${url.resourcesPath}/img/etiya-logo.svg" alt="Etiya" width="200" height="49">
    </aside>

    <#-- Form panel -->
    <main class="eds-form-panel">
      <div class="eds-card">

        <#if realm.internationalizationEnabled && locale?? && (locale.supported?size > 1)>
          <nav class="eds-locale" aria-label="${msg("edsLanguage")}" data-testid="login-locale-switcher">
            <#list locale.supported as l>
              <a class="eds-locale__item<#if l.languageTag == lang> is-active</#if>"
                 href="${l.url}" hreflang="${l.languageTag}" lang="${l.languageTag}"
                 data-testid="login-locale-${l.languageTag}">${l.label}</a>
            </#list>
          </nav>
        </#if>

        <header class="eds-head">
          <h1 class="eds-title" data-testid="login-title">${msg("edsLoginHeading")}</h1>
          <p class="eds-subtitle">${msg("edsLoginSubtitle")}</p>
        </header>

        <#-- SINGLE error surface (no duplicate inline field text): prefer the global
             message (invalid credentials, disabled user — Keycloak's localized,
             non-revealing text), else fall back to the first field validation error. -->
        <#assign topError = "">
        <#assign topType = "error">
        <#if message?? && message.summary?has_content && message.type != 'success'>
          <#assign topError = message.summary>
          <#assign topType = message.type>
        <#elseif messagesPerField.existsError('username','password')>
          <#assign topError = messagesPerField.getFirstError('username','password')>
        </#if>
        <#if topError?has_content>
          <div class="eds-alert eds-alert--${topType}" role="alert" data-testid="login-alert">
            <span class="eds-alert__icon" aria-hidden="true">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            </span>
            <span class="eds-alert__text">${kcSanitize(topError)?no_esc}</span>
          </div>
        </#if>

        <form id="kc-form-login" class="eds-form" action="${url.loginAction}" method="post" novalidate>

          <div class="eds-field">
            <label class="eds-label" for="username">${msg("edsUsernameLabel")}<span class="eds-req" aria-hidden="true">*</span></label>
            <div class="eds-input<#if messagesPerField.existsError('username','password')> is-invalid</#if>">
              <span class="eds-input__icon eds-input__icon--lead" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
              </span>
              <input id="username" name="username" type="text" class="eds-input__control has-lead"
                     value="${(login.username)!''}" autofocus autocomplete="username"
                     spellcheck="false" maxlength="64" dir="ltr"
                     aria-invalid="${messagesPerField.existsError('username')?c}"
                     data-testid="login-username-input">
            </div>
          </div>

          <div class="eds-field">
            <label class="eds-label" for="password">${msg("edsPasswordLabel")}<span class="eds-req" aria-hidden="true">*</span></label>
            <div class="eds-input<#if messagesPerField.existsError('username','password')> is-invalid</#if>">
              <input id="password" name="password" type="password" class="eds-input__control has-trail"
                     placeholder="${msg('edsPasswordPlaceholder')}"
                     autocomplete="current-password" maxlength="64"
                     aria-invalid="${messagesPerField.existsError('password')?c}"
                     data-testid="login-password-input">
              <button type="button" class="eds-input__toggle" data-testid="login-password-toggle"
                      aria-label="${msg('edsShowPassword')}" aria-pressed="false" tabindex="-1">
                <svg class="eds-eye" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z"/><circle cx="12" cy="12" r="3"/></svg>
                <svg class="eds-eye-off" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
              </button>
            </div>
          </div>

          <input type="hidden" id="id-hidden-input" name="credentialId" value="${(auth.selectedCredential)!''}">

          <button type="submit" name="login" id="kc-login" class="eds-btn eds-btn--primary" data-testid="login-submit">
            ${msg("edsLoginButton")}
          </button>
        </form>

        <footer class="eds-foot" data-testid="login-footer">
          <span>${msg("edsLoginFooter")}</span>
        </footer>
      </div>
    </main>
  </div>

  <script src="${url.resourcesPath}/js/login.js"></script>
</body>
</html>
