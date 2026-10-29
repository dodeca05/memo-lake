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

    // Memo viewer: wrap toggle, optional pretty-printed JSON view, copy button.
    // Content is only ever assigned through textContent, so it is never interpreted as HTML.
    function setupViewer() {
        var pre = document.getElementById('memo-content');
        if (!pre) {
            return;
        }
        var original = pre.textContent;

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

    document.addEventListener('DOMContentLoaded', function () {
        localizeTimes();
        setupViewer();
    });
})();
