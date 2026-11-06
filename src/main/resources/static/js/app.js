// Memo Lake - small progressive enhancements. Pages work without JavaScript.
(function () {
    'use strict';

    function pad(n) {
        return String(n).padStart(2, '0');
    }

    // Show <time datetime="..."> values (rendered in UTC) in the browser's local time.
    function localizeTimes() {
        document.querySelectorAll('time[datetime]').forEach(function (el) {
            var iso = el.getAttribute('datetime');
            var d = new Date(iso);
            if (!iso || isNaN(d.getTime())) {
                return;
            }
            el.title = iso;
            el.textContent = d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' '
                + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
        });
    }

    // Memo viewer: view switch (raw / Markdown / HTML), wrap toggle, optional pretty-printed JSON view, copy button.
    // Raw content is only ever assigned through textContent, so it is never interpreted as HTML.
    // The Markdown pane is rendered and sanitized on the server; the HTML pane is a sandboxed frame.
    function setupViewer() {
        var pre = document.getElementById('memo-content');
        if (!pre) {
            return;
        }
        var original = pre.textContent;

        setupViewSwitch(pre);

        var wrapButton = document.getElementById('toggle-wrap');
        wrapButton.addEventListener('click', function () {
            var wrapped = pre.classList.toggle('nowrap') === false;
            wrapButton.setAttribute('aria-pressed', String(wrapped));
        });

        var jsonButton = document.getElementById('toggle-json');
        var trimmed = original.trim();
        if (trimmed.charAt(0) === '{' || trimmed.charAt(0) === '[') {
            try {
                var pretty = JSON.stringify(JSON.parse(trimmed), null, 2);
                jsonButton.hidden = false;
                jsonButton.addEventListener('click', function () {
                    var showPretty = jsonButton.getAttribute('aria-pressed') !== 'true';
                    pre.textContent = showPretty ? pretty : original;
                    jsonButton.setAttribute('aria-pressed', String(showPretty));
                });
            } catch (e) {
                // Not JSON: keep the raw text only.
            }
        }

        var copyButton = document.getElementById('copy-content');
        if (!navigator.clipboard) {
            copyButton.hidden = true;
            return;
        }
        copyButton.addEventListener('click', function () {
            navigator.clipboard.writeText(original).then(function () {
                copyButton.textContent = '복사됨';
                setTimeout(function () { copyButton.textContent = '복사'; }, 1500);
            });
        });
    }

    // The view links reload the page with ?view=...; with JavaScript the panes are switched in place
    // and the address bar is updated so a reload or a shared link keeps the chosen view.
    function setupViewSwitch(pre) {
        var panes = {
            raw: pre,
            markdown: document.getElementById('memo-markdown'),
            html: document.getElementById('memo-html')
        };
        var rawTools = document.getElementById('raw-tools');
        var links = document.querySelectorAll('#memo-viewer .segmented a[data-view]');

        function select(view, href) {
            Object.keys(panes).forEach(function (key) {
                panes[key].hidden = key !== view;
            });
            if (view === 'html' && !panes.html.getAttribute('src')) {
                panes.html.setAttribute('src', panes.html.getAttribute('data-src'));
            }
            rawTools.hidden = view !== 'raw';
            links.forEach(function (link) {
                var active = link.getAttribute('data-view') === view;
                link.classList.toggle('is-active', active);
                if (active) {
                    link.setAttribute('aria-current', 'page');
                } else {
                    link.removeAttribute('aria-current');
                }
            });
            if (href && window.history && window.history.replaceState) {
                window.history.replaceState(null, '', href);
            }
        }

        links.forEach(function (link) {
            link.addEventListener('click', function (event) {
                if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0) {
                    return; // open in a new tab etc.
                }
                event.preventDefault();
                select(link.getAttribute('data-view'), link.getAttribute('href'));
            });
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        localizeTimes();
        setupViewer();
    });
})();
