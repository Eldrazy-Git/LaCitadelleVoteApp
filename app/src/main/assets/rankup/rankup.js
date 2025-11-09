document.addEventListener("DOMContentLoaded", function () {
  const STORAGE_KEY = "rankup_progress_v1";

  // ---- OFFSET de scroll pour éviter la barre de statut ----
  const SCROLL_OFFSET = 48;

  // ===== Scroll fluide (remplace l'ancien ensureScrollToWithOffset) =====
  const easeInOutCubic = (t) =>
    t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;

  let _activeScrollAnim = null;

  const smoothScrollToTop = (top, duration = 450) => {
    // annule une éventuelle anim en cours
    if (_activeScrollAnim && _activeScrollAnim.cancel) {
      _activeScrollAnim.cancel();
    }
    const startY = window.pageYOffset;
    const delta = top - startY;
    if (Math.abs(delta) < 1) return Promise.resolve();

    let rafId = 0;
    let done = false;
    const start = performance.now();

    const cancel = () => {
      if (!done) {
        done = true;
        cancelAnimationFrame(rafId);
        _activeScrollAnim = null;
      }
    };
    _activeScrollAnim = { cancel };

    return new Promise((resolve) => {
      const tick = (now) => {
        if (done) return;
        const t = Math.min(1, (now - start) / duration);
        const eased = easeInOutCubic(t);
        window.scrollTo(0, startY + delta * eased);
        if (t < 1) {
          rafId = requestAnimationFrame(tick);
        } else {
          done = true;
          _activeScrollAnim = null;
          resolve();
        }
      };
      rafId = requestAnimationFrame(tick);
    });
  };

  // Scroll "assuré" : animation fluide + petite correction si le layout bouge
  const ensureScrollToWithOffset = async (el, offset = SCROLL_OFFSET) => {
    const targetTop = window.pageYOffset + el.getBoundingClientRect().top - offset;
    await smoothScrollToTop(targetTop, 450);

    // correction finale si l'ouverture/fermeture a décalé la page
    const newTarget = window.pageYOffset + el.getBoundingClientRect().top - offset;
    if (Math.abs(window.pageYOffset - newTarget) > 3) {
      await smoothScrollToTop(newTarget, 220);
    }
  };

  // 1) State
  let state = {};
  try { state = JSON.parse(localStorage.getItem(STORAGE_KEY)) || {}; } catch (e) { state = {}; }
  const saveState = () => { try { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)); } catch (e) {} };

  // 2) Nodes
  const detailsList = document.querySelectorAll("details.rank");

  // Helpers
  const getAllItemCheckboxes = (details) =>
    Array.from(details.querySelectorAll('.rank-checkline input[type="checkbox"]'));

  const setRankCompletedClass = (details, completed) => {
    details.classList.toggle("rank-completed", completed);
  };

  const isRankCompleted = (details) => {
    const headerCb = details.querySelector('.rank-summary-check input[type="checkbox"]');
    if (headerCb) return !!headerCb.checked;
    const items = getAllItemCheckboxes(details);
    return items.length > 0 && items.every(cb => cb.checked);
  };

  // ----- Animations d'ouverture/fermeture PROMISES + anti-rebonds -----
  const animating = new WeakSet();

  const smoothOpen = (details) => {
    return new Promise((resolve) => {
      if (animating.has(details)) return resolve();
      const content = details.querySelector(".rank-content");
      if (!content) { details.open = true; return resolve(); }

      animating.add(details);
      details.open = true;

      content.style.transition = "max-height 260ms ease, opacity 220ms ease";
      content.style.overflow = "hidden";
      content.style.maxHeight = "0px";
      content.style.opacity = "0";

      content.classList.remove("fadein"); void content.offsetWidth; content.classList.add("fadein");

      requestAnimationFrame(() => {
        const h = content.scrollHeight;
        content.style.maxHeight = h + "px";
        content.style.opacity = "1";
      });

      const onEnd = (e) => {
        if (e.propertyName !== "max-height") return;
        content.removeEventListener("transitionend", onEnd);
        content.style.transition = "";
        content.style.overflow = "";
        content.style.maxHeight = "";
        content.style.opacity = "";
        animating.delete(details);
        resolve();
      };
      content.addEventListener("transitionend", onEnd);
    });
  };

  const smoothClose = (details) => {
    return new Promise((resolve) => {
      if (!details.open || animating.has(details)) { resolve(); return; }
      const content = details.querySelector(".rank-content");
      if (!content) { details.open = false; resolve(); return; }

      animating.add(details);
      content.style.transition = "max-height 260ms ease, opacity 220ms ease";
      const startHeight = content.scrollHeight;

      content.style.overflow = "hidden";
      content.style.maxHeight = startHeight + "px";
      content.style.opacity = "1";

      void content.offsetWidth;
      content.style.maxHeight = "0px";
      content.style.opacity = "0";

      const onEnd = (e) => {
        if (e.propertyName !== "max-height") return;
        content.removeEventListener("transitionend", onEnd);
        details.open = false;
        content.style.transition = "";
        content.style.overflow = "";
        content.style.maxHeight = "";
        content.style.opacity = "";
        animating.delete(details);
        resolve();
      };
      content.addEventListener("transitionend", onEnd);
    });
  };

  // Ouvrir le prochain rang incomplet (appelé SEULEMENT si le rang courant était ouvert)
  const openNextIncompleteFrom = async (currentDetails) => {
    const arr = Array.from(detailsList);
    const idx = arr.indexOf(currentDetails);
    if (idx === -1) return;
    const next = arr.slice(idx + 1).find(d => !isRankCompleted(d));
    if (!next) return;

    // fermer proprement tout autre rang ouvert
    for (const d of arr) {
      if (d !== next && d.open) await smoothClose(d);
    }

    await smoothOpen(next);
    requestAnimationFrame(() => ensureScrollToWithOffset(next));
  };

  // Met à jour le checkbox de rang selon l'état des items
  const syncHeaderFromItems = (details, headerCb, rankDoneKey) => {
    const allItems = getAllItemCheckboxes(details);
    const allChecked = allItems.length > 0 && allItems.every(cb => cb.checked);
    headerCb.checked = allChecked;
    state[rankDoneKey] = headerCb.checked;
    setRankCompletedClass(details, headerCb.checked);
    saveState();

    if (allChecked && details.open) {
      smoothClose(details).then(() => openNextIncompleteFrom(details));
    }
  };

  // 3) Clic sur SUMMARY : accordéon + scroll offset (avec promises)
  detailsList.forEach((details, idx) => {
    if (!details.dataset.rankId) details.dataset.rankId = "rank_" + idx;

    const summary = details.querySelector("summary");
    if (summary) {
      summary.addEventListener("click", async (e) => {
        e.preventDefault();
        if (animating.has(details)) return;

        if (details.open) {
          await smoothClose(details);
        } else {
          // fermer les autres ouverts
          for (const other of Array.from(detailsList)) {
            if (other !== details && other.open) await smoothClose(other);
          }
          await smoothOpen(details);
          requestAnimationFrame(() => ensureScrollToWithOffset(details));
        }
      });
    }
  });

  // 4) Injection des cases à cocher dans Requis + Objets
  detailsList.forEach((details) => {
    const rankId = details.dataset.rankId;
    const content = details.querySelector(".rank-content") || details;

    const targets = content.querySelectorAll("h3, h4, p.sub-title");
    targets.forEach((titleEl) => {
      const txt = titleEl.textContent.trim();
      const isRequis = txt.startsWith("📌 Requis");
      const isObjets = txt.includes("Objets à apporter");
      if (!isRequis && !isObjets) return;

      let ul = titleEl.nextElementSibling;
      if (!ul || ul.tagName.toLowerCase() !== "ul") return;

      const lis = ul.querySelectorAll("li");
      lis.forEach((li, index) => {
        const key = rankId + "::" + txt + "::" + index;
        const original = li.innerHTML;

        const label = document.createElement("label");
        label.className = "rank-checkline";

        const cb = document.createElement("input");
        cb.type = "checkbox";

        const checked = !!state[key];
        cb.checked = checked;
        if (checked) label.classList.add("rank-item-done");

        cb.addEventListener("change", function () {
          state[key] = cb.checked;
          if (cb.checked) label.classList.add("rank-item-done");
          else label.classList.remove("rank-item-done");
          saveState();

          const headerCb = details.querySelector('.rank-summary-check input[type="checkbox"]');
          if (headerCb) {
            const rankDoneKey = rankId + "::__rank_done";
            syncHeaderFromItems(details, headerCb, rankDoneKey);
          }
        });

        const span = document.createElement("span");
        span.innerHTML = original;

        li.innerHTML = "";
        label.appendChild(cb);
        label.appendChild(span);
        li.appendChild(label);
      });
    });

    // 5) Checkbox "Rang terminé" dans le SUMMARY (à droite de l’image)
    const summary = details.querySelector("summary");
    if (summary) {
      const rankDoneKey = rankId + "::__rank_done";

      const wrap = document.createElement("label");
      wrap.className = "rank-summary-check";
      wrap.title = "Marquer tout le rang comme terminé";

      const headerCb = document.createElement("input");
      headerCb.type = "checkbox";
      headerCb.checked = !!state[rankDoneKey];

      const txt = document.createElement("span");
      txt.className = "label";
      txt.textContent = "Rang terminé";

      wrap.addEventListener("click", (e) => e.stopPropagation());
      headerCb.addEventListener("click", (e) => e.stopPropagation());

      headerCb.addEventListener("change", function () {
        state[rankDoneKey] = headerCb.checked;
        saveState();
        setRankCompletedClass(details, headerCb.checked);

        const allItems = getAllItemCheckboxes(details);
        allItems.forEach((itemCb) => {
          if (itemCb.checked !== headerCb.checked) {
            itemCb.checked = headerCb.checked;
            const label = itemCb.closest("label.rank-checkline");
            if (label) {
              if (headerCb.checked) label.classList.add("rank-item-done");
              else label.classList.remove("rank-item-done");
            }
          }
        });

        const headings = content.querySelectorAll("h3, h4, p.sub-title");
        headings.forEach((h) => {
          const t = h.textContent.trim();
          const isR = t.startsWith("📌 Requis");
          const isO = t.includes("Objets à apporter");
          if (!isR && !isO) return;
          const ul = h.nextElementSibling;
          if (!ul || ul.tagName.toLowerCase() !== "ul") return;
          const lis = ul.querySelectorAll("li");
          lis.forEach((_, index) => {
            const key = rankId + "::" + t + "::" + index;
            state[key] = headerCb.checked;
          });
        });
        saveState();

        if (headerCb.checked && details.open) {
          smoothClose(details).then(() => openNextIncompleteFrom(details));
        }
      });

      wrap.appendChild(headerCb);
      wrap.appendChild(txt);

      const img = summary.querySelector("img");
      if (img && img.nextSibling) summary.insertBefore(wrap, img.nextSibling);
      else summary.appendChild(wrap);

      setRankCompletedClass(details, headerCb.checked);

      requestAnimationFrame(() => {
        const allItems = getAllItemCheckboxes(details);
        if (allItems.length > 0) {
          const allChecked = allItems.every(cb => cb.checked);
          if (allChecked !== headerCb.checked) {
            headerCb.checked = allChecked;
            state[rankDoneKey] = allChecked;
            setRankCompletedClass(details, allChecked);
            saveState();
          }
        }
      });
    }

    // 6) reset par rang
    const resetBtn = document.createElement("button");
    resetBtn.textContent = "Remise à zéro de ce rang";
    resetBtn.className = "rank-reset-btn";
    resetBtn.addEventListener("click", function () {
      const newState = {};
      Object.keys(state).forEach((k) => { if (!k.startsWith(rankId + "::")) newState[k] = state[k]; });
      state = newState;
      saveState();

      details.querySelectorAll('.rank-checkline input[type="checkbox"]').forEach((cb) => { cb.checked = false; });
      details.querySelectorAll('.rank-checkline').forEach((lbl) => { lbl.classList.remove("rank-item-done"); });

      const headerCb = details.querySelector('.rank-summary-check input[type="checkbox"]');
      if (headerCb) headerCb.checked = false;
      setRankCompletedClass(details, false);

      resetBtn.classList.remove("is-animating");
      void resetBtn.offsetWidth;
      resetBtn.classList.add("is-animating");
    });

    content.appendChild(resetBtn);
  });

  // 7) reset global
  const resetAllBtn = document.getElementById("reset-all");
  if (resetAllBtn) {
    resetAllBtn.addEventListener("click", function () {
      state = {};
      saveState();
      document.querySelectorAll('.rank-checkline input[type="checkbox"]').forEach((cb) => { cb.checked = false; });
      document.querySelectorAll('.rank-checkline').forEach((lbl) => { lbl.classList.remove("rank-item-done"); });
      document.querySelectorAll('.rank-summary-check input[type="checkbox"]').forEach((cb) => { cb.checked = false; });
      document.querySelectorAll('details.rank').forEach(d => d.classList.remove('rank-completed'));

      resetAllBtn.classList.remove("is-animating");
      void resetAllBtn.offsetWidth;
      resetAllBtn.classList.add("is-animating");
    });
  }

  // 8) Auto-ouvrir le premier rang incomplet au chargement (avec scroll assuré)
  (function autoOpenFirstIncomplete() {
    const MAX_RETRIES = 8, RETRY_DELAY = 80;
    let tries = 0;
    const hasInjected = () => document.querySelector('.rank-checkline') !== null;

    const run = async () => {
      const anyOpen = Array.from(detailsList).some(d => d.open);
      if (anyOpen) return;
      if (!hasInjected()) { if (tries++ < MAX_RETRIES) return void setTimeout(run, RETRY_DELAY); return; }

      const target = Array.from(detailsList).find(d => !isRankCompleted(d));
      if (target) {
        await smoothOpen(target);
        requestAnimationFrame(() => ensureScrollToWithOffset(target));
      }
    };

    requestAnimationFrame(() => setTimeout(run, 0));
  })();

}); // fin DOMContentLoaded
