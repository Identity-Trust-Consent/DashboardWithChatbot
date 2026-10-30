(function () {
    'use strict';
    var modal = document.getElementById('givenConsentModal');
    if (!modal) return;
    var status = document.getElementById('givenConsentStatus');
    var records = document.getElementById('givenConsentRecords');
    var retry = document.getElementById('retryGivenConsent');
    var pending;

    function date(value) {
        if (!value) return 'Not specified';
        var parsed = new Date(value);
        return isNaN(parsed.getTime()) ? 'Not specified' : parsed.toLocaleString(undefined, {
            day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit'
        });
    }

    function element(tag, className, text) {
        var node = document.createElement(tag);
        if (className) node.className = className;
        if (text) node.textContent = text;
        return node;
    }

    function detail(list, label, value) {
        var term = document.createElement('dt');
        var description = document.createElement('dd');
        term.textContent = label;
        description.textContent = value || 'Not specified';
        list.appendChild(term);
        list.appendChild(description);
    }

    async function load() {
        if (pending) pending.abort();
        var controller = new AbortController();
        pending = controller;
        records.replaceChildren();
        status.textContent = 'Loading your given consents...';
        status.className = 'text-muted';
        records.setAttribute('aria-busy', 'true');
        retry.hidden = true;
        try {
            var response = await fetch(modal.dataset.consentUrl, {
                cache: 'no-store', credentials: 'same-origin', signal: controller.signal,
                headers: { Accept: 'application/json' }
            });
            if (response.redirected) throw new Error('Your session has expired. Sign in again to view your consents.');
            var data = await response.json();
            if (!response.ok) throw new Error(data.message || 'Unable to load your consents. Please try again.');
            if (!Array.isArray(data.consents)) throw new Error('Unable to load your consents. Please try again.');
            status.textContent = data.consents.length ? data.consents.length + (data.consents.length === 1 ? ' consent for this application' : ' consents for this application') : 'You have not given any consent for this application yet.';
            data.consents.forEach(function (consent) {
                var card = document.createElement('article');
                card.className = 'consent-record';
                var rule = consent.businessProcessRule || {};
                var header = element('div', 'consent-card-header');
                var titleRow = element('div', 'consent-card-title-row');
                var title = element('div');
                title.appendChild(element('h6', 'consent-card-title', consent.consentPurpose.name));
                title.appendChild(element('p', 'consent-card-org', consent.businessUnit.name));
                titleRow.appendChild(title);
                var state = consent.status || 'unknown';
                var stateLabel = state === 'accepted' ? 'Active' : state.replace(/_/g, ' ').replace(/^./, function (letter) { return letter.toUpperCase(); });
                titleRow.appendChild(element('span', 'consent-status ' + (state === 'accepted' ? 'active' : 'inactive'), stateLabel));
                header.appendChild(titleRow);
                var policies = element('div', 'consent-policy-tags');
                policies.appendChild(element('span', 'consent-policy-tag', typeof rule.mandatory === 'boolean' ? (rule.mandatory ? 'Mandatory consent' : 'Optional consent') : 'Consent type not specified'));
                policies.appendChild(element('span', 'consent-policy-tag', typeof rule.reconsentableByPrincipal === 'boolean' ? (rule.reconsentableByPrincipal ? 'Reconsentable' : 'Not reconsentable') : 'Reconsent policy not specified'));
                policies.appendChild(element('span', 'consent-policy-tag', typeof rule.revocableByPrincipal === 'boolean' ? (rule.revocableByPrincipal ? 'Revocable' : 'Not revocable') : 'Revocation policy not specified'));
                header.appendChild(policies);
                card.appendChild(header);

                var body = element('div', 'consent-card-body');
                body.appendChild(element('h6', 'consent-section-label', 'Purpose & processing'));
                var list = document.createElement('dl');
                detail(list, 'Notice / process', consent.businessProcess.name + ' · Version ' + consent.businessProcess.version);
                detail(list, 'Consent purpose', consent.consentPurpose.description);
                detail(list, 'Processing purpose', consent.processingPurpose.name);
                detail(list, 'Processing details', consent.processingPurpose.description);
                body.appendChild(list);
                body.appendChild(element('h6', 'consent-section-label', 'Data you have shared'));
                var attributes = element('div', 'consent-data-tags');
                (consent.userAttributeNames && consent.userAttributeNames.length ? consent.userAttributeNames : ['Not specified']).forEach(function (name) {
                    attributes.appendChild(element('span', 'consent-data-tag', name));
                });
                body.appendChild(attributes);
                var dates = element('div', 'consent-dates');
                function dateItem(label, value) {
                    var item = element('div');
                    item.appendChild(element('span', 'consent-date-label', label));
                    item.appendChild(element('span', 'consent-date-value', date(value)));
                    dates.appendChild(item);
                }
                dateItem('Given on', consent.insertedAt);
                dateItem('Expires on', consent.expiresAt);
                body.appendChild(dates);
                card.appendChild(body);
                var actions = consent.actions || {};
                if (actions.unavailableMessage) {
                    var notice = document.createElement('p');
                    notice.className = 'consent-card-actions text-muted mb-0';
                    notice.textContent = actions.unavailableMessage;
                    card.appendChild(notice);
                }
                if (actions.updateUrl || actions.revokeUrl) {
                    var links = document.createElement('div');
                    links.className = 'consent-card-actions';
                    links.appendChild(element('span', 'consent-action-note', 'Manage your permissions · Opens in a new tab'));
                    function actionLink(url, label, style) {
                        if (!url) return;
                        var target;
                        try { target = new URL(url); } catch (error) { return; }
                        if (target.protocol !== 'http:' && target.protocol !== 'https:') return;
                        var link = document.createElement('a');
                        link.href = target.href;
                        link.textContent = label;
                        link.className = 'btn ' + style;
                        link.target = '_blank';
                        link.rel = 'noopener noreferrer';
                        link.referrerPolicy = 'no-referrer';
                        links.appendChild(link);
                    }
                    actionLink(actions.updateUrl, 'Update consent', 'btn-primary');
                    actionLink(actions.revokeUrl, 'Revoke consent', 'btn-outline-danger');
                    card.appendChild(links);
                }
                records.appendChild(card);
            });
        } catch (error) {
            if (controller.signal.aborted) return;
            records.replaceChildren();
            status.textContent = error instanceof Error ? error.message : 'Unable to load your consents. Please try again.';
            status.className = 'text-danger';
            retry.hidden = false;
        } finally {
            if (pending === controller) {
                pending = null;
                records.setAttribute('aria-busy', 'false');
            }
        }
    }
    $(modal).on('show.bs.modal', load);
    $(modal).on('hidden.bs.modal', function () {
        if (pending) pending.abort();
    });
    retry.addEventListener('click', load);
    // Refresh statuses after the user returns from the consent action page.
    window.addEventListener('focus', function () {
        if (modal.classList.contains('show') && !pending) load();
    });
})();
