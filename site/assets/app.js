/* ===== Paper2fig: filtering, search, pagination, lightbox ===== */
(function () {
  "use strict";

  const VENUES = {
    iclr:    { name: "ICLR",    cn: "ICLR" },
    icml:    { name: "ICML",    cn: "ICML" },
    neurips: { name: "NeurIPS", cn: "NeurIPS" },
    cvpr:    { name: "CVPR", cn: "CVPR" },
    acl:     { name: "ACL", cn: "ACL" },
    aaai:    { name: "AAAI", cn: "AAAI" },
    eccv:    { name: "ECCV", cn: "ECCV" },
    miccai:  { name: "MICCAI", cn: "MICCAI" },
    ijcai:   { name: "IJCAI", cn: "IJCAI" },
    icme:    { name: "ICME", cn: "ICME" },
  };
  const PATTERNS = {
    teaser:       "Teaser",
    conceptual:   "Conceptual",
    framework:    "Framework",
    pipeline:     "Pipeline",
    architecture: "Architecture",
    taxonomy:     "Taxonomy / Benchmark",
    results:      "Results",
    comparison:   "Comparison",
  };
  const PATTERN_ORDER = ["teaser", "conceptual", "framework", "pipeline", "architecture", "taxonomy", "results", "comparison"];
  const TIERS = { best: "Best Paper", oral: "Oral", spotlight: "Spotlight" };
  // 管理后台「前端配置」导出的筛选配置（assets/site-config.js）；缺失时沿用内置规则
  const CONFIG = (window.SITE_CONFIG && typeof window.SITE_CONFIG === "object") ? window.SITE_CONFIG : null;
  const PAGE = 24;
  const MAX_IMAGE_LOADS = 4;

  /* ---------- i18n: dynamic labels ---------- */
  GAL_I18N.register({
    en: {
      "chip.all": "All",
      "pat.teaser": "Teaser", "pat.conceptual": "Conceptual", "pat.framework": "Framework",
      "pat.pipeline": "Pipeline", "pat.architecture": "Architecture",
      "pat.taxonomy": "Taxonomy / Benchmark", "pat.results": "Results", "pat.comparison": "Comparison",
      "tier.best": "Best Paper", "tier.oral": "Oral", "tier.spotlight": "Spotlight",
      "rib.best": "★ Best Paper", "rib.honor": "Honorable Mention",
      "rib.oral": "● Oral", "rib.spot": "● Spotlight",
      "card.viewImage": "View image", "card.paper": "paper ↗",
      "card.aria": "View {t}",
      "end": "— End · {n} figures —",
      "page.nav": "Gallery pagination", "page.prev": "Previous", "page.next": "Next", "page.go": "Go to page {n}",
      "page.summary": "{start}–{end} of {total} figures · Page {current} of {pages}",
    },
    zh: {
      "chip.all": "全部",
      "pat.teaser": "主视觉", "pat.conceptual": "概念图", "pat.framework": "框架总览",
      "pat.pipeline": "流程图", "pat.architecture": "架构图",
      "pat.taxonomy": "全景 / 基准", "pat.results": "结果", "pat.comparison": "对比",
      "tier.best": "最佳论文", "tier.oral": "口头报告", "tier.spotlight": "焦点论文",
      "rib.best": "★ 最佳论文", "rib.honor": "荣誉提名",
      "rib.oral": "● 口头报告", "rib.spot": "● 焦点论文",
      "card.viewImage": "查看图片", "card.paper": "论文 ↗",
      "card.aria": "查看 {t}",
      "end": "— 已加载全部 · 共 {n} 张 —",
      "page.nav": "画廊分页", "page.prev": "上一页", "page.next": "下一页", "page.go": "转到第 {n} 页",
      "page.summary": "第 {start}–{end} 张，共 {total} 张 · 第 {current} / {pages} 页",
    },
  });
  // 合并后台配置：新增的会议 / 等级 / 模式在筛选、徽标和搜索里同样可用
  if (CONFIG) {
    (CONFIG.venues || []).forEach((venue) => {
      const value = String(venue.value || "");
      if (!value) return;
      VENUES[value] = {
        name: venue.label || venue.labelEn || value,
        cn: venue.labelZh || venue.label || value,
      };
    });
    const extra = { en: {}, zh: {} };
    (CONFIG.tiers || []).forEach((tier) => {
      const value = String(tier.value || "");
      if (!value) return;
      extra.en["tier." + value] = tier.labelEn || value;
      extra.zh["tier." + value] = tier.labelZh || value;
    });
    (CONFIG.patterns || []).forEach((pattern) => {
      const value = String(pattern.value || "");
      if (!value) return;
      extra.en["pat." + value] = pattern.labelEn || value;
      extra.zh["pat." + value] = pattern.labelZh || value;
    });
    GAL_I18N.register(extra);
  }
  const patLabel = (p) => GAL_I18N.t("pat." + p);
  const tierLabel = (v) => GAL_I18N.t("tier." + v);

  const state = { venue: "all", year: "all", tier: "all", pattern: "all", q: "", sort: "venue", page: 1 };
  const figures = (window.FIGURES || []).slice();
  let filtered = [];

  /* ---------- remote keyword search (Meilisearch via same-origin /search) ---------- */
  // 标题/作者/会议等关键词交给 Meilisearch（支持拼写容错与前缀）；
  // 会议、年份、等级、图类型仍由本地 matches() 过滤，分页与 URL 状态不变。
  const SEARCH_ENDPOINT = "/search";
  const searchState = { q: "", ids: null, order: null };
  let searchAbort = null;
  let searchSeq = 0;

  const $ = (s) => document.querySelector(s);
  const gallery = $("#gallery");
  const empty = $("#empty");
  const pagination = $("#pagination");
  $("#total-figures").textContent = figures.length.toLocaleString();
  const nBest = figures.filter((f) => f.award).length;
  const nOral = figures.filter((f) => (f.award ? false : f.tier === "oral")).length;
  const nSpot = figures.filter((f) => (!f.award && f.tier === "spotlight")).length;
  $("#total-best").textContent = nBest.toLocaleString();
  $("#total-oral").textContent = nOral.toLocaleString();
  $("#total-spotlight").textContent = nSpot.toLocaleString();

  /* ---------- filter chips ---------- */
  const chipBoxes = [];
  function chipText(btn, counts) {
    const label = btn.dataset.lk ? GAL_I18N.t(btn.dataset.lk) : btn.dataset.static;
    // No count on the “All” chip: counts["all"] is undefined and used to render
    // a bogus “0” next to it.
    const n = counts ? counts[btn.dataset.val] : null;
    return label + (n ? ` <span class="n">${n}</span>` : "");
  }
  function makeChips(containerId, key, values, labelKeyFor, counts) {
    const box = $(containerId);
    const all = document.createElement("button");
    all.className = "chip active";
    all.dataset.lk = "chip.all";
    all.dataset.val = "all";
    box.appendChild(all);
    values.forEach((v) => {
      const c = document.createElement("button");
      c.className = "chip";
      c.dataset.val = v;
      const lk = labelKeyFor(v);
      if (lk && lk.startsWith("static:")) c.dataset.static = lk.slice(7);
      else if (lk) c.dataset.lk = lk;
      else c.dataset.static = String(v);
      box.appendChild(c);
    });
    chipBoxes.push({ box, counts });
    box.addEventListener("click", (e) => {
      const btn = e.target.closest(".chip");
      if (!btn) return;
      state[key] = btn.dataset.val;
      box.querySelectorAll(".chip").forEach((x) => x.classList.toggle("active", x === btn));
      render();
    });
  }

  const venueValues = CONFIG && Array.isArray(CONFIG.venues) && CONFIG.venues.length
    ? CONFIG.venues.map((v) => String(v.value)).filter((v) => VENUES[v])
    : Object.keys(VENUES);
  const years = CONFIG && Array.isArray(CONFIG.years) && CONFIG.years.length
    ? CONFIG.years.map((y) => Number(y && typeof y === "object" ? y.value : y)).filter(Number.isFinite).sort((a, b) => a - b)
    : [...new Set(figures.map((f) => f.year))].sort((a, b) => a - b);
  const tierValues = CONFIG && Array.isArray(CONFIG.tiers) && CONFIG.tiers.length
    ? CONFIG.tiers.map((t) => String(t.value)).filter(Boolean)
    : ["best", "oral", "spotlight"];
  const usedPatterns = CONFIG && Array.isArray(CONFIG.patterns) && CONFIG.patterns.length
    ? CONFIG.patterns.map((p) => String(p.value)).filter(Boolean)
    : PATTERN_ORDER.filter((p) => figures.some((f) => f.pattern === p));
  const tierCounts = Object.fromEntries(tierValues.map((value) => [
    value,
    value === "best" ? nBest : figures.filter((f) => !f.award && f.tier === value).length,
  ]));

  makeChips("#venue-chips", "venue", venueValues, (v) => "static:" + VENUES[v].name,
    Object.fromEntries(venueValues.map((v) => [v, figures.filter((f) => f.venue === v).length])));
  makeChips("#year-chips", "year", years, () => null);
  makeChips("#tier-chips", "tier", tierValues, (v) => "tier." + v, tierCounts);
  makeChips("#pattern-chips", "pattern", usedPatterns, (v) => "pat." + v);

  function relabelChips() {
    chipBoxes.forEach(({ box, counts }) => {
      box.querySelectorAll(".chip").forEach((btn) => { btn.innerHTML = chipText(btn, counts); });
    });
  }
  // Chips are built empty above; without this first pass they stay blank until
  // the visitor happens to switch language (langchange calls relabelChips).
  relabelChips();

  /* ---------- search ---------- */
  const searchInput = $("#search");
  const clearBtn = $("#clear-search");
  let qTimer;

  function requestRemoteSearch(q) {
    const seq = ++searchSeq;
    if (searchAbort) searchAbort.abort();
    const controller = typeof AbortController !== "undefined" ? new AbortController() : null;
    searchAbort = controller;
    fetch(SEARCH_ENDPOINT, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ q, limit: 4000, attributesToRetrieve: ["id", "figureId"] }),
      signal: controller ? controller.signal : undefined,
    })
      .then((res) => { if (!res.ok) throw new Error("search " + res.status); return res.json(); })
      .then((data) => {
        if (seq !== searchSeq) return;
        const hits = data.hits || [];
        searchState.q = q;
        searchState.ids = new Set(hits.map((h) => h.figureId || h.id));
        searchState.order = new Map(hits.map((h, i) => [h.figureId || h.id, i]));
        render(false);
      })
      .catch((err) => {
        if (seq !== searchSeq || (err && err.name === "AbortError")) return;
        // 检索服务异常时退回本地子串匹配，不阻断浏览
        searchState.q = "";
        searchState.ids = null;
        searchState.order = null;
        render(false);
      });
  }

  function setQuery(q) {
    state.q = q;
    clearBtn.hidden = !q;
    if (q) {
      requestRemoteSearch(q);
    } else {
      searchSeq++;
      if (searchAbort) searchAbort.abort();
      searchState.q = "";
      searchState.ids = null;
      searchState.order = null;
    }
  }

  searchInput.addEventListener("input", () => {
    clearTimeout(qTimer);
    qTimer = setTimeout(() => {
      setQuery(searchInput.value.trim().toLowerCase());
      render();
    }, 150);
  });
  clearBtn.addEventListener("click", () => {
    clearTimeout(qTimer);
    searchInput.value = "";
    setQuery("");
    render();
  });
  $("#sort").addEventListener("change", (e) => { state.sort = e.target.value; render(); });
  $("#reset-all").addEventListener("click", () => {
    clearTimeout(qTimer);
    state.venue = state.year = state.tier = state.pattern = "all";
    searchInput.value = "";
    setQuery("");
    document.querySelectorAll(".chips").forEach((b) => {
      b.querySelectorAll(".chip").forEach((c) => c.classList.toggle("active", c.dataset.val === "all"));
    });
    render();
  });

  // Browsing controls share the existing filter reset and preserve data state.
  $("#reset-filters").addEventListener("click", () => $("#reset-all").click());
  const filterToggle = $("#toggle-filters");
  function setFiltersOpen(open) {
    $("#filter-options").hidden = !open;
    filterToggle.setAttribute("aria-expanded", String(open));
  }
  filterToggle.addEventListener("click", () => setFiltersOpen($("#filter-options").hidden));
  setFiltersOpen(true);
  function submitSearch() {
    clearTimeout(qTimer);
    setQuery(searchInput.value.trim().toLowerCase());
    render();
    $("#browse").scrollIntoView({ block: "start" });
  }
  $(".header-search").addEventListener("submit", (event) => {
    event.preventDefault();
    submitSearch();
  });  document.querySelectorAll(".view-button").forEach((button) => {
    button.addEventListener("click", () => {
      gallery.classList.toggle("compact", button.dataset.view === "compact");
      document.querySelectorAll(".view-button").forEach((item) => {
        const active = item === button;
        item.classList.toggle("active", active);
        item.setAttribute("aria-pressed", String(active));
      });
    });
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "/" && !event.ctrlKey && !event.metaKey && !event.altKey &&
        !event.target.closest("input, textarea, select, [contenteditable]") && $("#lightbox").hidden &&
        !document.querySelector("dialog[open]")) {
      event.preventDefault();
      searchInput.focus();
    }
  });

  /* ---------- back to top ---------- */
  const backToTop = $("#back-to-top");
  let topButtonFrame = null;
  function syncTopButton() {
    backToTop.hidden = window.scrollY <= 480;
    topButtonFrame = null;
  }
  window.addEventListener("scroll", () => {
    if (topButtonFrame === null) topButtonFrame = requestAnimationFrame(syncTopButton);
  }, { passive: true });
  window.addEventListener("pageshow", syncTopButton);
  syncTopButton();
  backToTop.addEventListener("click", () => {
    $(".brand").focus({ preventScroll: true });
    window.scrollTo({ top: 0, behavior: reducedMotion.matches ? "instant" : "smooth" });
  });

  /* ---------- filtering / sorting ---------- */
  function matches(f) {
    if (state.venue !== "all" && f.venue !== state.venue) return false;
    if (state.year !== "all" && String(f.year) !== String(state.year)) return false;
    if (state.pattern !== "all" && f.pattern !== state.pattern) return false;
    if (state.tier === "best") { if (!f.award) return false; }
    else if (state.tier !== "all") { if (f.award || f.tier !== state.tier) return false; }
    if (state.q) {
      if (searchState.q === state.q && searchState.ids) {
        if (!searchState.ids.has(f.id)) return false;
      } else {
        // 检索请求尚未返回或服务不可用时的本地兜底
        const hay = [f.title, (f.authors || []).join(" "), (VENUES[f.venue] || {}).name || f.venue,
                     f.year, f.pattern, PATTERNS[f.pattern] || "", patLabel(f.pattern),
                     f.tier ? (TIERS[f.tier] || f.tier) : "", f.tier ? tierLabel(f.tier) : "",
                     f.award ? "best paper award outstanding 最佳论文" : ""]
          .join(" ").toLowerCase();
        if (!state.q.split(/\s+/).every((tok) => hay.includes(tok))) return false;
      }
    }
    return true;
  }

  function sorted(list) {
    const venueRank = Object.fromEntries(venueValues.map((value, index) => [value, index]));
    const rank = (venue) => venueRank[venue] ?? 99;
    const l = list.slice();
    // 有关键词时默认按 Meilisearch 相关度排序；手动选择按年份/标题时尊重用户选择。
    if (state.q && state.sort === "venue" && searchState.q === state.q && searchState.order) {
      const pos = searchState.order;
      l.sort((a, b) =>
        (pos.has(a.id) ? pos.get(a.id) : 1e9) - (pos.has(b.id) ? pos.get(b.id) : 1e9) ||
        a.year - b.year || a.id.localeCompare(b.id));
      return l;
    }
    if (state.sort === "year") l.sort((a, b) => a.year - b.year || rank(a.venue) - rank(b.venue) || a.id.localeCompare(b.id));
    else if (state.sort === "title") l.sort((a, b) => a.title.localeCompare(b.title));
    else l.sort((a, b) => rank(a.venue) - rank(b.venue) || a.year - b.year || a.id.localeCompare(b.id));
    return l;
  }

  function escapeHtml(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }
  function escapeRegExp(s) {
    return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }
  // 把当前搜索词（多个 token 的任意一个）在已转义的文本里包成 <mark>。
  function highlightText(text) {
    const raw = String(text == null ? "" : text);
    if (!state.q) return escapeHtml(raw);
    const tokens = state.q.split(/\s+/).filter(Boolean).map(escapeRegExp)
      .sort((a, b) => b.length - a.length);
    if (!tokens.length) return escapeHtml(raw);
    const re = new RegExp("(" + tokens.join("|") + ")", "gi");
    let out = "", last = 0, m;
    while ((m = re.exec(raw)) !== null) {
      if (!m[0]) { re.lastIndex++; continue; }
      out += escapeHtml(raw.slice(last, m.index)) + '<mark class="hl">' + escapeHtml(m[0]) + "</mark>";
      last = m.index + m[0].length;
    }
    return out + escapeHtml(raw.slice(last));
  }
  function authorsText(f) {
    const a = f.authors || [];
    if (!a.length) return "";
    return a.length > 5 ? a.slice(0, 5).join(", ") + " et al." : a.join(", ");
  }

  /* ---------- chunked render ---------- */
  function ribbonInfo(f) {
    if (f.award === "best") return { className: "rb-best", key: "rib.best" };
    if (f.award === "honorable") return { className: "rb-honor", key: "rib.honor" };
    if (f.tier === "oral") return { className: "rb-oral", key: "rib.oral" };
    if (f.tier === "spotlight") return { className: "rb-spotlight", key: "rib.spot" };
    return null;
  }
  function ribbonHtml(f) {
    const info = ribbonInfo(f);
    return info ? `<span class="ribbon ${info.className}">${GAL_I18N.t(info.key)}</span>` : "";
  }
  function thumbFor(f) {
    // Cards and the lightbox preview use a 720px WebP thumbnail; the full JPEG
    // replaces it as soon as it has been decoded.
    return String(f.image).replace("/final/", "/thumb/").replace(/\.jpe?g$/i, ".webp");
  }
  function cardHtml(f, idx) {
    const ratio = (f.w && f.h) ? `aspect-ratio:${f.w} / ${f.h};` : "min-height:170px;";
    // Cards use a 720px WebP thumbnail; the lightbox keeps the full-resolution image.
    const thumb = thumbFor(f);
    const imgAttrs = `data-src="${escapeHtml(thumb)}" data-fallback="${escapeHtml(f.image)}"`;
    return `
    <article class="card${f.award ? " is-award" : f.tier ? " is-" + f.tier : ""}" data-id="${f.id}" tabindex="0" role="button" aria-label="${GAL_I18N.t("card.aria", { t: escapeHtml(f.title) })}">
      <span class="card-close-target" aria-hidden="true"></span>
      <div class="img-slot" style="${ratio}">
        ${ribbonHtml(f)}
        <img class="card-img" ${imgAttrs} alt="${escapeHtml(f.title)} Figure 1" decoding="async">
      </div>
      <div class="card-body">
        <div class="card-badges">
          <span class="badge ${f.venue}">${(VENUES[f.venue] || {}).name || String(f.venue).toUpperCase()}</span>
          <span class="badge year">${f.year}</span>
          <span class="badge pattern">${patLabel(f.pattern)}</span>
        </div>
        <h3 class="card-title">${highlightText(f.title)}</h3>
        <p class="card-authors">${highlightText(authorsText(f))}</p>
        <span class="card-link"><span class="card-link-image">${GAL_I18N.t("card.viewImage")}</span></span>
      </div>
    </article>`;
  }

  /* ---------- Bounded, near-viewport image loading ---------- */
  let lazyIO = null;
  let imageQueue = [];
  const queuedImages = new Set();
  const activeImages = new Set();
  const retryTimers = new Set();
  let imageGeneration = 0;

  function clearPageImages() {
    imageGeneration++;
    if (lazyIO) lazyIO.disconnect();
    retryTimers.forEach(clearTimeout);
    retryTimers.clear();
    imageQueue = [];
    queuedImages.clear();
    gallery.querySelectorAll(".card-img").forEach((img) => {
      img.onload = img.onerror = null;
      img.removeAttribute("src");
    });
    activeImages.clear();
  }

  function enqueueImage(img) {
    if (!img.isConnected || img.classList.contains("loaded") ||
        queuedImages.has(img) || activeImages.has(img)) return;
    queuedImages.add(img);
    imageQueue.push(img);
    drainImageQueue();
  }

  function drainImageQueue() {
    while (activeImages.size < MAX_IMAGE_LOADS && imageQueue.length) {
      const img = imageQueue.shift();
      queuedImages.delete(img);
      if (!img.isConnected) continue;
      const generation = imageGeneration;
      const slot = img.parentElement;
      activeImages.add(img);
      const finish = (loaded) => {
        img.onload = img.onerror = null;
        if (generation !== imageGeneration) return;
        activeImages.delete(img);
        if (!img.isConnected) { drainImageQueue(); return; }
        if (loaded) {
          img.classList.add("loaded");
          slot.classList.add("loaded");
        } else if (img.dataset.fallback && !img.dataset.fallbackUsed) {
          // Thumbnail missing/unavailable: fall back to the full-size original.
          img.dataset.fallbackUsed = "1";
          img.dataset.src = img.dataset.fallback;
          queuedImages.add(img);
          imageQueue.push(img);
        } else if (!img.dataset.retried) {
          img.dataset.retried = "1";
          const timer = setTimeout(() => {
            retryTimers.delete(timer);
            if (generation === imageGeneration && img.isConnected) enqueueImage(img);
          }, 1200);
          retryTimers.add(timer);
        } else {
          slot.classList.add("img-failed");
          slot.addEventListener("click", (event) => {
            event.stopPropagation();
            slot.classList.remove("img-failed");
            delete img.dataset.retried;
            enqueueImage(img);
          }, { once: true });
        }
        drainImageQueue();
      };
      img.onload = () => finish(true);
      img.onerror = () => finish(false);
      const source = img.dataset.src;
      img.src = source + (img.dataset.retried ? (source.includes("?") ? "&" : "?") + "retry=1" : "");
    }
  }

  function wirePageImages() {
    if ("IntersectionObserver" in window) {
      lazyIO = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting && entry.target.isConnected) {
            lazyIO.unobserve(entry.target);
            enqueueImage(entry.target);
          }
        });
      }, { rootMargin: "300px 0px" });
      gallery.querySelectorAll(".card-img").forEach((img) => lazyIO.observe(img));
    } else {
      gallery.querySelectorAll(".card-img").forEach(enqueueImage);
    }
  }

  function renderPagination() {
    const pages = Math.ceil(filtered.length / PAGE);
    pagination.hidden = pages <= 1;
    if (pages <= 1) { pagination.innerHTML = ""; return; }
    const start = (state.page - 1) * PAGE + 1;
    const end = Math.min(state.page * PAGE, filtered.length);
    const button = (page, label, disabled = false, current = false) =>
      `<button type="button" data-page="${page}"${disabled ? " disabled" : ""}${current ? ' aria-current="page"' : ""} aria-label="${escapeHtml(GAL_I18N.t("page.go", { n: page }))}">${label}</button>`;
    const numbers = [...new Set([1, state.page - 1, state.page, state.page + 1, pages])]
      .filter((page) => page >= 1 && page <= pages).sort((a, b) => a - b);
    let previous = 0;
    const links = numbers.map((page) => {
      const gap = previous && page - previous > 1 ? '<span class="page-ellipsis" aria-hidden="true">&hellip;</span>' : "";
      previous = page;
      return gap + button(page, page, false, page === state.page);
    }).join("");
    pagination.innerHTML = `<p class="page-summary" role="status">${GAL_I18N.t("page.summary", { start, end, total: filtered.length.toLocaleString(), current: state.page, pages })}</p><div class="page-buttons">` +
      `<button type="button" data-page="${state.page - 1}"${state.page === 1 ? " disabled" : ""}>${GAL_I18N.t("page.prev")}</button>` + links +
      `<button type="button" data-page="${state.page + 1}"${state.page === pages ? " disabled" : ""}>${GAL_I18N.t("page.next")}</button></div>`;
  }

  pagination.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-page]");
    if (!button || button.disabled) return;
    const page = Number(button.dataset.page);
    if (page === state.page || page < 1 || page > Math.ceil(filtered.length / PAGE)) return;
    state.page = page;
    writeUrl("pushState", location.hash);
    render(false);
    $(".collection").scrollIntoView({ block: "start", behavior: "instant" });
    gallery.querySelector(".card")?.focus({ preventScroll: true });
  });

  function render(resetPage = true) {
    if (resetPage) state.page = 1;
    filtered = sorted(figures.filter(matches));
    state.page = Math.max(1, Math.min(state.page, Math.ceil(filtered.length / PAGE) || 1));
    // Replace the page rather than retaining images from earlier pages.
    writeUrl("replaceState", location.hash);
    clearPageImages();
    const start = (state.page - 1) * PAGE;
    gallery.innerHTML = filtered.slice(start, start + PAGE).map((f, i) => cardHtml(f, start + i)).join("");
    empty.hidden = filtered.length > 0;
    wirePageImages();
    renderPagination();
  }

  /* ---------- shareable URL state (filters in the query, figure in the hash) ----------
     Filters, search, and pagination live in ?v=&y=&t=&p=&q=&s=&page=; the open figure lives in
     #f=<id>. Hash-only writes never reload GitHub Pages, so switching figures or
     clearing the hash costs nothing. */
  const URL_KEYS = ["v", "y", "t", "p", "q", "s", "page"];
  let internalUrlWrite = false;

  function validFilter(key, value) {
    if (key === "v") return value === "all" || Object.prototype.hasOwnProperty.call(VENUES, value);
    if (key === "y") return value === "all" || years.some((x) => String(x) === value);
    if (key === "t") return value === "all" || tierValues.indexOf(value) !== -1;
    if (key === "p") return value === "all" || usedPatterns.indexOf(value) !== -1;
    if (key === "s") return ["venue", "year", "title"].indexOf(value) !== -1;
    if (key === "q") return true;
    return false;
  }

  // Unknown params are preserved so this never strips somebody else's tracking
  // link (?utm_source=…) off a shared URL.
  function queryString() {
    const params = new URLSearchParams(location.search);
    URL_KEYS.forEach((key) => params.delete(key));
    if (state.venue !== "all") params.set("v", state.venue);
    if (state.year !== "all") params.set("y", state.year);
    if (state.tier !== "all") params.set("t", state.tier);
    if (state.pattern !== "all") params.set("p", state.pattern);
    if (state.q) params.set("q", state.q);
    if (state.sort !== "venue") params.set("s", state.sort);
    if (state.page > 1) params.set("page", String(state.page));
    return params.toString();
  }

  function writeUrl(method, hash) {
    const query = queryString();
    const url = location.pathname + (query ? "?" + query : "") + (hash || "");
    internalUrlWrite = true;
    try {
      history[method](history.state, "", url);
    } catch (_) {
      location.hash = hash || "";
    } finally {
      internalUrlWrite = false;
    }
  }

  function figureHash() {
    const match = /^#f=([A-Za-z0-9._-]+)$/.exec(location.hash);
    return match ? match[1] : null;
  }

  /* ---------- lightbox with prev/next over the filtered list ---------- */
  const lb = $("#lightbox");
  const lbImg = $("#lb-img");
  const lbClose = $("#lb-close");
  const lbDialog = lb.querySelector(".lightbox-dialog");
  const lbContent = lb.querySelector(".lightbox-content");
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  const transitionParts = {
    shell: "gallery-card-shell",
    image: "gallery-card-image",
    badges: "gallery-card-badges",
    title: "gallery-card-title",
    authorsOut: "gallery-card-authors-out",
    authorsIn: "gallery-card-authors-in",
    imageAction: "gallery-card-image-action",
    paperAction: "gallery-card-paper-action",
    separator: "gallery-card-link-separator",
    ribbon: "gallery-card-ribbon",
    close: "gallery-lightbox-close",
    swap: "gallery-dialog-swap",
  };
  let currentId = null;
  let lightboxBusy = false;
  let opener = null;
  // Set while a router-driven open/close runs, so the transition's own state
  // changes are not mistaken for a user navigation.
  let routerBusy = false;

  function currentIndex() { return filtered.findIndex((x) => x.id === currentId); }

  function lightboxControls() {
    return Array.from(lbDialog.querySelectorAll("a[href], button:not([disabled])"))
      .filter((element) => getComputedStyle(element).visibility !== "hidden");
  }

  function trapLightboxFocus(e) {
    if (e.key !== "Tab" || lb.hidden) return;
    const controls = lightboxControls();
    if (!controls.length) { e.preventDefault(); return; }
    const first = controls[0];
    const last = controls[controls.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault(); last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault(); first.focus();
    }
  }

  function positionLightboxSeparator() {
    const image = $("#lb-image").getBoundingClientRect();
    const paper = $("#lb-paper").getBoundingClientRect();
    const actions = lbDialog.querySelector(".lb-actions").getBoundingClientRect();
    const target = $("#lb-separator-target");
    target.style.left = `${(image.right + paper.left) / 2 - actions.left}px`;
    target.style.top = `${(image.top + image.bottom + paper.top + paper.bottom) / 4 - actions.top}px`;
  }

  /* ---------- zoomable image inspector ---------- */
  // A fit-to-viewport stage that can be magnified around the cursor and panned,
  // following the interaction model of an image inspector rather than a static
  // lightbox. Zoom is relative to the fit size: 100% means "fits the viewport".
  const LB_ZOOM_MIN = 0.5;
  const LB_ZOOM_MAX = 16;
  const lbViewport = $("#lb-viewport");
  const lbStage = $("#lb-stage");
  const lbZoomLevel = $("#lb-zoom-level");
  const lbZoomOut = $("#lb-zoom-out");
  const lbZoomIn = $("#lb-zoom-in");
  const lbZoomFit = $("#lb-zoom-fit");
  let lbZoom = 1;
  let lbRatio = 1.4;
  let lbStageFit = 0;
  let lbImageToken = 0;
  let lbPan = null;

  function viewportBox() {
    const style = getComputedStyle(lbViewport);
    // Measure the border box: unlike clientWidth/clientHeight this does not
    // change when a scrollbar appears, so zooming cannot feed back into the fit
    // scale and oscillate.
    const rect = lbViewport.getBoundingClientRect();
    const w = rect.width
      - parseFloat(style.paddingLeft || "0") - parseFloat(style.paddingRight || "0");
    const h = rect.height
      - parseFloat(style.paddingTop || "0") - parseFloat(style.paddingBottom || "0");
    return { w: Math.max(0, w), h: Math.max(0, h) };
  }

  function sizeStage() {
    if (lb.hidden) return;
    const { w, h } = viewportBox();
    if (w < 1 || h < 1) return;
    lbStageFit = Math.min(w, h * lbRatio);
    const width = Math.max(1, Math.round(lbStageFit * lbZoom));
    lbStage.style.width = `${width}px`;
    lbStage.style.height = `${Math.max(1, Math.round(width / lbRatio))}px`;
  }

  function syncZoomUi() {
    lbZoomLevel.textContent = `${Math.round(lbZoom * 100)}%`;
    lbZoomOut.disabled = lbZoom <= LB_ZOOM_MIN + 0.001;
    lbZoomIn.disabled = lbZoom >= LB_ZOOM_MAX - 0.001;
    lbViewport.classList.toggle("is-zoomed", lbZoom > 1.001);
  }

  function setZoom(value, clientX, clientY, force = false) {
    const next = Math.min(LB_ZOOM_MAX, Math.max(LB_ZOOM_MIN, Math.round(value * 100) / 100));
    if (!force && Math.abs(next - lbZoom) < 0.005) return;
    const viewport = lbViewport.getBoundingClientRect();
    const previous = lbStage.getBoundingClientRect();
    const x = clientX == null ? viewport.left + viewport.width / 2 : clientX;
    const y = clientY == null ? viewport.top + viewport.height / 2 : clientY;
    const anchorX = previous.width ? Math.min(1, Math.max(0, (x - previous.left) / previous.width)) : 0.5;
    const anchorY = previous.height ? Math.min(1, Math.max(0, (y - previous.top) / previous.height)) : 0.5;
    const scrollLeft = lbViewport.scrollLeft;
    const scrollTop = lbViewport.scrollTop;

    lbZoom = next;
    sizeStage();

    if (lbZoom <= 1.001) {
      lbViewport.scrollLeft = 0;
      lbViewport.scrollTop = 0;
    } else {
      const rect = lbStage.getBoundingClientRect();
      const cursorX = x - viewport.left;
      const cursorY = y - viewport.top;
      lbViewport.scrollLeft = Math.max(
        0,
        rect.left - viewport.left + scrollLeft + anchorX * rect.width - cursorX,
      );
      lbViewport.scrollTop = Math.max(
        0,
        rect.top - viewport.top + scrollTop + anchorY * rect.height - cursorY,
      );
    }
    syncZoomUi();
  }

  function resetZoom() {
    setZoom(1, null, null, true);
    lbViewport.scrollLeft = 0;
    lbViewport.scrollTop = 0;
    syncZoomUi();
  }

  function layoutLightboxStage() {
    sizeStage();
    if (lbZoom <= 1.001) {
      lbViewport.scrollLeft = 0;
      lbViewport.scrollTop = 0;
    }
    syncZoomUi();
  }

  function isLightboxControl(target) {
    return !!(target && target.closest && target.closest("button, a, input, textarea, select"));
  }

  lbZoomOut.addEventListener("click", () => setZoom(lbZoom / 1.5));
  lbZoomIn.addEventListener("click", () => setZoom(lbZoom * 1.5));
  lbZoomFit.addEventListener("click", resetZoom);

  lbViewport.addEventListener("wheel", (event) => {
    if (lb.hidden) return;
    event.preventDefault();
    // ctrlKey is also how browsers report a trackpad pinch gesture.
    const factor = Math.exp(-event.deltaY * (event.ctrlKey ? 0.0085 : 0.0022));
    setZoom(lbZoom * factor, event.clientX, event.clientY);
  }, { passive: false });

  lbViewport.addEventListener("dblclick", (event) => {
    if (isLightboxControl(event.target)) return;
    setZoom(lbZoom < 1.5 ? 2 : 1, event.clientX, event.clientY);
  });

  lbViewport.addEventListener("pointerdown", (event) => {
    if (lbZoom <= 1.001 || event.button !== 0 || isLightboxControl(event.target)) return;
    lbPan = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      scrollLeft: lbViewport.scrollLeft,
      scrollTop: lbViewport.scrollTop,
      moved: false,
    };
    lbViewport.setPointerCapture(event.pointerId);
  });

  lbViewport.addEventListener("pointermove", (event) => {
    if (!lbPan || lbPan.pointerId !== event.pointerId) return;
    const dx = event.clientX - lbPan.startX;
    const dy = event.clientY - lbPan.startY;
    if (!lbPan.moved && Math.hypot(dx, dy) > 4) {
      lbPan.moved = true;
      lbViewport.classList.add("is-panning");
      lbViewport.setPointerCapture(event.pointerId);
    }
    if (!lbPan.moved) return;
    event.preventDefault();
    lbViewport.scrollLeft = lbPan.scrollLeft - dx;
    lbViewport.scrollTop = lbPan.scrollTop - dy;
  });

  function endPan(event) {
    if (!lbPan) return;
    if (lbViewport.hasPointerCapture?.(lbPan.pointerId)) {
      lbViewport.releasePointerCapture(lbPan.pointerId);
    }
    lbPan = null;
    lbViewport.classList.remove("is-panning");
  }
  lbViewport.addEventListener("pointerup", endPan);
  lbViewport.addEventListener("pointercancel", endPan);

  if ("ResizeObserver" in window) {
    new ResizeObserver(() => {
      if (lb.hidden) return;
      layoutLightboxStage();
    }).observe(lbViewport);
  }

  function setFigureContent(f) {
    currentId = f.id;
    lbImageToken++;
    const token = lbImageToken;
    lbRatio = f.w && f.h ? f.w / f.h : 1.4;
    lbStage.style.aspectRatio = String(lbRatio);
    resetZoom();

    // Show the cached card thumbnail immediately, then swap in the full image
    // once it has decoded. Never blank the stage between figures.
    delete lbImg.dataset.previewFallback;
    lbImg.dataset.quality = "preview";
    lbImg.src = thumbFor(f);
    lbImg.alt = f.title;
    lbImg.onerror = () => {
      if (lbImg.dataset.quality === "preview" && !lbImg.dataset.previewFallback) {
        lbImg.dataset.previewFallback = "1";
        lbImg.dataset.quality = "full";
        lbImg.src = f.image;
      }
    };
    const full = new Image();
    full.decoding = "async";
    let revealed = false;
    const reveal = () => {
      if (revealed || token !== lbImageToken) return;
      revealed = true;
      const swap = () => {
        if (token !== lbImageToken) return;
        lbImg.dataset.quality = "full";
        lbImg.src = f.image;
      };
      if (typeof full.decode === "function") full.decode().then(swap, swap);
      else swap();
    };
    full.onload = reveal;
    full.onerror = () => { /* keep the thumbnail */ };
    full.src = f.image;
    if (full.complete && full.naturalWidth > 0) reveal();

    const aBadge = $("#lb-award");
    if (f.award) {
      aBadge.hidden = false;
      aBadge.textContent = GAL_I18N.t(f.award === "best" ? "rib.best" : "rib.honor");
      aBadge.className = "badge tier-badge " + (f.award === "best" ? "rb-best" : "rb-honor");
    } else aBadge.hidden = true;
    const tBadge = $("#lb-tier");
    if (f.tier) {
      tBadge.hidden = false;
      tBadge.textContent = tierLabel(f.tier);
      tBadge.className = "badge tier-badge " + (f.tier === "oral" ? "rb-oral" : "rb-spotlight");
    } else tBadge.hidden = true;
    const vBadge = $("#lb-venue");
    vBadge.textContent = (VENUES[f.venue] || {}).name || String(f.venue).toUpperCase();
    vBadge.className = "badge " + f.venue;
    $("#lb-year").textContent = f.year;
    const pBadge = $("#lb-pattern");
    pBadge.textContent = patLabel(f.pattern);
    const ribbonTarget = $("#lb-ribbon-target");
    const ribbon = ribbonInfo(f);
    ribbonTarget.textContent = ribbon ? GAL_I18N.t(ribbon.key) : "";
    ribbonTarget.className = `ribbon ribbon-target${ribbon ? " " + ribbon.className : ""}`;
    $("#lb-title").innerHTML = highlightText(f.title);
    $("#lb-authors").innerHTML = highlightText((f.authors || []).join(", "));
    $("#lb-paper").href = f.paper || "#";
    $("#lb-image").href = f.image;
  }

  function cardFor(id) {
    return Array.from(gallery.querySelectorAll(".card")).find((card) => card.dataset.id === id) || null;
  }

  function isInViewport(element) {
    if (!element) return false;
    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0 && rect.bottom > 0 && rect.right > 0
      && rect.top < window.innerHeight && rect.left < window.innerWidth;
  }

  function canTransition() {
    return typeof document.startViewTransition === "function" && !reducedMotion.matches;
  }

  async function decodeLightboxImage() {
    if (typeof lbImg.decode !== "function") return;
    try { await lbImg.decode(); } catch (_) { /* Keep the existing image error behavior. */ }
  }

  function componentPairs(card, direction) {
    const cardAuthors = card && card.querySelector(".card-authors");
    const dialogAuthors = $("#lb-authors");
    const cardParts = card ? {
      shell: card,
      image: card.querySelector(".card-img"),
      badges: card.querySelector(".card-badges"),
      title: card.querySelector(".card-title"),
      imageAction: card.querySelector(".card-link-image"),
      paperAction: card.querySelector(".card-link-paper"),
      separator: card.querySelector(".card-link-separator"),
      close: card.querySelector(".card-close-target"),
    } : {};
    const dialogParts = {
      shell: lbDialog,
      image: lbImg,
      badges: lbDialog.querySelector(".lb-badges"),
      title: $("#lb-title"),
      imageAction: $("#lb-image"),
      paperAction: $("#lb-paper"),
      separator: $("#lb-separator-target"),
      close: lbClose,
    };
    const pairs = Object.keys(dialogParts).map((key) => ({
      source: direction === "closing" ? dialogParts[key] : cardParts[key],
      destination: direction === "closing" ? cardParts[key] : dialogParts[key],
      name: transitionParts[key],
    }));
    const ribbon = card && card.querySelector(".ribbon");
    if (ribbon) {
      pairs.push({
        source: direction === "opening" ? ribbon : $("#lb-ribbon-target"),
        destination: direction === "closing" ? ribbon : $("#lb-ribbon-target"),
        name: transitionParts.ribbon,
      });
    }
    // Single-line card text and wrapping dialog text need separate snapshots.
    // Neither snapshot should be resized between those two layouts.
    if (cardAuthors && isInViewport(cardAuthors)) {
      pairs.push(
        { source: direction === "closing" ? dialogAuthors : cardAuthors,
          destination: null, name: transitionParts.authorsOut },
        { source: null,
          destination: direction === "closing" ? cardAuthors : dialogAuthors,
          travelFrom: direction === "closing" ? dialogAuthors : cardAuthors,
          name: transitionParts.authorsIn },
      );
    }
    return pairs;
  }

  async function transitionLightbox(update, options) {
    const { pairs = [], direction } = options;
    if (!canTransition()) {
      await update();
      return;
    }

    const activePairs = pairs.reduce((result, pair) => {
      if (pair.source && !isInViewport(pair.source)) return result;
      const destinationVisible = isInViewport(pair.destination);
      if (direction !== "opening" && !destinationVisible && pair.name !== transitionParts.shell
          && pair.name !== transitionParts.authorsOut) {
        return result;
      }
      const destination = direction === "opening" || destinationVisible
        ? pair.destination
        : null;
      if (!pair.source && !destination) return result;
      result.push({ ...pair, destination });
      return result;
    }, []);
    const authorPair = activePairs.find((pair) => pair.name === transitionParts.authorsIn);
    const authorStart = authorPair && authorPair.travelFrom
      ? authorPair.travelFrom.getBoundingClientRect()
      : null;
    const actionPairs = activePairs.filter((pair) =>
      pair.name === transitionParts.imageAction || pair.name === transitionParts.paperAction);
    // A shared link opens the dialog with no originating card, so `source` can be
    // undefined. Skipping those pairs keeps the transition working instead of
    // throwing, which used to abort the open and leave the dialog hidden.
    const rectOf = (element) => (element ? element.getBoundingClientRect() : null);
    const actionStarts = new Map(actionPairs
      .filter((pair) => rectOf(pair.source) && rectOf(pair.destination))
      .map((pair) => [pair.name, pair.source.getBoundingClientRect()]));
    let updated = false;
    let transition;

    document.documentElement.dataset.figureTransition = direction;
    activePairs.forEach(({ source, name }) => {
      if (source) source.style.viewTransitionName = name;
    });

    try {
      transition = document.startViewTransition(async () => {
        activePairs.forEach(({ source }) => {
          if (source) source.style.viewTransitionName = "";
        });
        updated = true;
        await update();
        activePairs.forEach(({ destination, name }) => {
          if (destination) destination.style.viewTransitionName = name;
        });
        actionPairs.forEach(({ destination, name }) => {
          if (!destination) return;
          const key = name === transitionParts.imageAction ? "image-action" : "paper-action";
          const startRect = actionStarts.get(name);
          const endRect = destination.getBoundingClientRect();
          // No originating card (cold start from a shared link): there is nothing
          // to morph from, so the destination simply keeps its own layout.
          if (!startRect) return;
          for (const [side, rect] of [["from", startRect], ["to", endRect]]) {
            for (const axis of ["width", "height"]) {
              document.documentElement.style.setProperty(`--gallery-${key}-${side}-${axis}`, `${rect[axis]}px`);
            }
          }
        });
        if (authorStart && authorPair.destination) {
          const authorEnd = authorPair.destination.getBoundingClientRect();
          for (const [side, rect] of [["from", authorStart], ["to", authorEnd]]) {
            for (const [axis, value] of [["x", rect.left], ["y", rect.top],
                                         ["width", rect.width], ["height", rect.height]]) {
              document.documentElement.style.setProperty(`--gallery-author-${side}-${axis}`, `${value}px`);
            }
          }
        }
      });
      await transition.finished;
    } catch (_) {
      if (!updated) await update();
    } finally {
      activePairs.forEach(({ source, destination }) => {
        if (source) source.style.viewTransitionName = "";
        if (destination) destination.style.viewTransitionName = "";
      });
      for (const side of ["from", "to"]) {
        for (const axis of ["x", "y", "width", "height"]) {
          document.documentElement.style.removeProperty(`--gallery-author-${side}-${axis}`);
        }
        for (const key of ["image-action", "paper-action"]) {
          for (const axis of ["width", "height"]) {
            document.documentElement.style.removeProperty(`--gallery-${key}-${side}-${axis}`);
          }
        }
      }
      delete document.documentElement.dataset.figureTransition;
    }
  }

  async function openFigure(f, card) {
    if (lightboxBusy || !lb.hidden) return;
    // Written before the transition so a share taken mid-animation already has
    // the right URL. pushState keeps one history entry so Back returns to the grid.
    if (!card) writeUrl("replaceState", "#f=" + f.id);
    else writeUrl("pushState", "#f=" + f.id);
    lightboxBusy = true;
    opener = card || document.activeElement;
    lbDialog.style.height = "";
    setFigureContent(f);
    await decodeLightboxImage();

    try {
      await transitionLightbox(() => {
        lb.hidden = false;
        document.body.style.overflow = "hidden";
        layoutLightboxStage();
        positionLightboxSeparator();
      }, {
        pairs: componentPairs(card, "opening"),
        direction: "opening",
      });
      lbClose.focus({ preventScroll: true });
    } finally {
      lightboxBusy = false;
    }
  }

  async function step(d) {
    if (lightboxBusy) return;
    const i = currentIndex();
    const j = i + d;
    if (j < 0 || j >= filtered.length) return;

    lightboxBusy = true;
    // Stepping replaces the entry instead of stacking one per figure, so Back
    // always returns to the grid rather than walking through the whole list.
    writeUrl("replaceState", "#f=" + filtered[j].id);
    if (!lbDialog.style.height) {
      lbDialog.style.height = `${lbDialog.getBoundingClientRect().height}px`;
    }
    try {
      await transitionLightbox(async () => {
        setFigureContent(filtered[j]);
        await decodeLightboxImage();
      }, {
        pairs: [{ source: lbContent, destination: lbContent, name: transitionParts.swap }],
        direction: d > 0 ? "switch-next" : "switch-prev",
      });
    } finally {
      lightboxBusy = false;
    }
  }

  async function closeLb() {
    if (lightboxBusy || lb.hidden) return;
    lightboxBusy = true;
    let closingAnimation = null;

    try {
      showFigurePage(currentId);
      const targetCard = cardFor(currentId);
      const returnFocus = targetCard || opener;
      // Fade only the fixed overlay. Morphing each dialog part back into the
      // card snapshots briefly replaces the gallery's real text and styling.
      if (!reducedMotion.matches && typeof lb.animate === "function") {
        closingAnimation = lb.animate([{ opacity: 1 }, { opacity: 0 }], {
          duration: 140,
          easing: "ease-out",
          fill: "forwards",
        });
        await closingAnimation.finished.catch(() => {});
      }
      lb.hidden = true;
      document.body.style.overflow = "";
      if (returnFocus?.isConnected && typeof returnFocus.focus === "function") {
        returnFocus.focus({ preventScroll: true });
      }
    } finally {
      lb.hidden = true;
      document.body.style.overflow = "";
      closingAnimation?.cancel();
      lbDialog.style.height = "";
      lightboxBusy = false;
    }
    writeUrl("replaceState", "");
    currentId = null;
    lbImageToken++;
    lbImg.onerror = null;
    lbImg.removeAttribute("src");
    delete lbImg.dataset.quality;
    resetZoom();
  }

  function showFigurePage(id) {
    const index = filtered.findIndex((figure) => figure.id === id);
    if (index < 0) return;
    const page = Math.floor(index / PAGE) + 1;
    if (state.page !== page) {
      state.page = page;
      render(false);
      cardFor(id)?.scrollIntoView({ block: "center", behavior: "instant" });
    }
  }

  /* ---------- URL -> UI ---------- */
  function syncUiFromState() {
    const keyByBox = { "venue-chips": "venue", "year-chips": "year", "tier-chips": "tier", "pattern-chips": "pattern" };
    document.querySelectorAll(".chips").forEach((box) => {
      const key = keyByBox[box.id];
      if (!key) return;
      box.querySelectorAll(".chip").forEach((chip) => {
        chip.classList.toggle("active", chip.dataset.val === state[key]);
      });
    });
    searchInput.value = state.q;
    clearBtn.hidden = !state.q;
    $("#sort").value = state.sort;
  }

  function readUrlState() {
    const params = new URLSearchParams(location.search);
    Object.assign(state, { venue: "all", year: "all", tier: "all", pattern: "all", q: "", sort: "venue", page: 1 });
    const page = params.get("page");
    if (/^[1-9]\d*$/.test(page || "") && Number.isSafeInteger(Number(page))) state.page = Number(page);
    ["v", "y", "t", "p", "s", "q"].forEach((key) => {
      if (!params.has(key)) return;
      const value = params.get(key);
      if (!validFilter(key, value)) return;
      if (key === "v") state.venue = value;
      else if (key === "y") state.year = value;
      else if (key === "t") state.tier = value;
      else if (key === "p") state.pattern = value;
      else if (key === "s") state.sort = value;
      else if (key === "q") state.q = value.trim().toLowerCase();
    });
  }

  // The only place the lightbox is opened or closed without a click.
  async function applyHashToLightbox() {
    const id = figureHash();
    if (id === currentId) return;
    if (id === null) {
      if (!lb.hidden) await closeLb();
      return;
    }
    if (!lb.hidden) {
      // Another entry for a figure already on screen (Back/Forward): swap in place.
      const openF = filtered.find((x) => x.id === id);
      if (openF) {
        setFigureContent(openF);
        await decodeLightboxImage();
      }
      return;
    }
    const f = figures.find((x) => x.id === id);
    if (!f) return; // unknown or stale id: leave the grid alone
    if (!filtered.some((x) => x.id === id)) {
      // The link carries filters that hide its own target — drop only what gets
      // in the way, then re-render, so a shared figure is actually visible.
      if (state.venue !== "all" && f.venue !== state.venue) state.venue = "all";
      if (state.year !== "all" && String(f.year) !== String(state.year)) state.year = "all";
      if (state.tier !== "all" && !matches(f)) state.tier = "all";
      if (state.pattern !== "all" && f.pattern !== state.pattern) state.pattern = "all";
      if (state.q && !matches(f)) state.q = "";
      syncUiFromState();
      render();
    }
    showFigurePage(f.id);
    await openFigure(f, null);
  }

  async function runUrlChange() {
    const before = JSON.stringify(state);
    readUrlState();
    if (JSON.stringify(state) !== before) {
      syncUiFromState();
      render(false);
    }
    await applyHashToLightbox();
  }

  function handleUrlChange() {
    if (internalUrlWrite) return;
    routerBusy = true;
    const attempt = (tries) => {
      // Opening and closing run an image decode plus a View Transition, so
      // lightboxBusy can still be set when Back arrives. Returning immediately
      // used to drop the navigation entirely: the hash cleared but the dialog
      // stayed open and the page stayed scroll-locked, which reads as "Back does
      // nothing". Wait for the in-flight transition instead of discarding it.
      if (lightboxBusy && tries < 40) {
        setTimeout(() => attempt(tries + 1), 120);
        return;
      }
      if (lightboxBusy) { routerBusy = false; return; }
      runUrlChange()
        .catch(() => { /* keep the grid usable if a transition fails */ })
        .then(() => { routerBusy = false; });
    };
    attempt(0);
  }

  window.addEventListener("popstate", handleUrlChange);
  // hashchange is a backstop for engines where a hash-only popstate does not
  // fire; applyHashToLightbox is a no-op when the id already matches.
  window.addEventListener("hashchange", handleUrlChange);

  gallery.addEventListener("click", (e) => {
    const card = e.target.closest(".card");
    if (card) {
      const f = figures.find((x) => x.id === card.dataset.id);
      if (f) openFigure(f, card);
    }
  });
  gallery.addEventListener("keydown", (e) => {
    if (e.key !== "Enter" && e.key !== " ") return;
    const card = e.target.closest(".card");
    if (card) { e.preventDefault(); const f = figures.find((x) => x.id === card.dataset.id); if (f) openFigure(f, card); }
  });
  lbClose.addEventListener("click", closeLb);
  lb.querySelector(".lightbox-backdrop").addEventListener("click", closeLb);
  document.addEventListener("keydown", (e) => {
    if (lb.hidden) return;
    trapLightboxFocus(e);
    if (e.key === "Escape") closeLb();
    else if (e.key === "ArrowLeft") step(-1);
    else if (e.key === "ArrowRight") step(1);
    else {
      const textEntry = e.target.closest?.("input, textarea, select, [contenteditable]");
      const actionable = e.target.closest?.("button, a");
      if (textEntry) return;
      if (e.key === "+" || e.key === "=") {
        e.preventDefault();
        setZoom(lbZoom * 1.5);
      } else if (e.key === "-" || e.key === "_") {
        e.preventDefault();
        setZoom(lbZoom / 1.5);
      } else if (e.key === "0") {
        e.preventDefault();
        resetZoom();
      } else if (e.code === "Space" && !actionable) {
        e.preventDefault();
        setZoom(lbZoom > 1.001 ? 1 : 2);
      }
    }
  });
  window.addEventListener("resize", () => {
    if (!lb.hidden && !lightboxBusy) {
      layoutLightboxStage();
      positionLightboxSeparator();
    }
  });

  /* ---------- language toggle ---------- */
  const langToggle = $("#lang-toggle");
  function syncToggle() {
    langToggle.textContent = GAL_I18N.lang === "zh" ? "EN" : "中";
    langToggle.title = GAL_I18N.t("toggle.title");
  }
  langToggle.addEventListener("click", () => {
    GAL_I18N.setLang(GAL_I18N.lang === "zh" ? "en" : "zh");
  });
  document.addEventListener("langchange", () => {
    syncToggle();
    relabelChips();
    if (!lb.hidden && currentId) {
      const f = figures.find((x) => x.id === currentId);
      if (f) setFigureContent(f);
    }
    render(false);
  });

  syncToggle();
  GAL_I18N.apply();
  // Filters from the URL must be in state before the first render, otherwise the
  // grid paints the unfiltered list for one frame.
  readUrlState();
  syncUiFromState();
  render(false);
  if (state.q) requestRemoteSearch(state.q);
  // Cold start: a shared figure link also selects the page containing its card.
  // `window.__tcLastError` records a failed cold start for debugging; it is never
  // user-visible and never blocks the grid.
  applyHashToLightbox().catch((err) => { window.__tcLastError = String((err && err.stack) || err); });
})();
