(function () {
  var select = document.getElementById("ui-refresh");
  if (!select) {
    return;
  }

  var DEFAULT_SECONDS = 3;
  var timer = null;
  var url = new URL(window.location.href);
  var fromQuery = url.searchParams.get("refresh");
  var initial = fromQuery !== null ? parseInt(fromQuery, 10) : DEFAULT_SECONDS;
  if (isNaN(initial) || initial < 0) {
    initial = DEFAULT_SECONDS;
  }

  if ([0, 1, 2, 3, 5, 10, 30].indexOf(initial) < 0) {
    initial = DEFAULT_SECONDS;
  }
  select.value = String(initial);

  function clearTimer() {
    if (timer !== null) {
      clearInterval(timer);
      timer = null;
    }
  }

  function syncUrl(seconds) {
    var next = new URL(window.location.href);
    if (seconds === DEFAULT_SECONDS) {
      next.searchParams.delete("refresh");
    } else {
      next.searchParams.set("refresh", String(seconds));
    }
    var nextUrl = next.pathname + next.search + next.hash;
    var current = window.location.pathname + window.location.search + window.location.hash;
    if (nextUrl !== current) {
      window.history.replaceState(null, "", nextUrl);
    }
  }

  function apply() {
    clearTimer();
    var seconds = parseInt(select.value, 10);
    if (isNaN(seconds) || seconds < 0) {
      seconds = 0;
    }
    syncUrl(seconds);
    if (seconds > 0) {
      timer = setInterval(function () {
        window.location.reload();
      }, seconds * 1000);
    }
  }

  select.addEventListener("change", apply);
  apply();
})();
