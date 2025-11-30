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

  // --- Barre de progression (contenu placé en premier dans .rank-content) ---
  const ensureRankProgressBar = (details) => {
    const content = details.querySelector(".rank-content");
    if (!content) return null;

    // Si déjà présente, on la renvoie
    let progress = content.querySelector(".rank-progress");
    if (progress) return progress;

    // Sinon, on la crée et on la met juste au début du contenu
    progress = document.createElement("div");
    progress.className = "rank-progress";
    progress.innerHTML = `
      <div class="rank-progress-bar">
        <div class="rank-progress-bar-inner"></div>
      </div>
      <span class="rank-progress-label"></span>
    `;

    content.insertBefore(progress, content.firstChild);

    return progress;
  };

  const updateRankProgressUI = (details) => {
    const items = getAllItemCheckboxes(details);
    const done = items.filter((cb) => cb.checked).length;
    const total = items.length;

    const progress = ensureRankProgressBar(details);
    if (!progress) return;

    const barInner = progress.querySelector(".rank-progress-bar-inner");
    const label = progress.querySelector(".rank-progress-label");

    const pct = total ? (done / total) * 100 : 0;

    if (barInner) barInner.style.width = `${pct}%`;
    if (label) label.textContent = `${done} / ${total || 0}`;
  };

  // ----- Animations d'ouverture/fermeture PROMISES + anti-rebonds -----
  const animating = new WeakSet();

  const smoothOpen = (details) =>
    new Promise((resolve) => {
      if (animating.has(details)) return resolve();
      const content = details.querySelector(".rank-content");
      if (!content) {
        details.open = true;
        return resolve();
      }

      animating.add(details);
      details.open = true;

      content.style.overflow = "hidden";
      content.style.maxHeight = "0px";
      content.style.opacity = "0";
      content.style.transition = "max-height 260ms ease, opacity 220ms ease";
      void content.offsetWidth;

      const h = content.scrollHeight;
      content.style.maxHeight = h + "px";
      content.style.opacity = "1";

      const onEnd = (e) => {
        if (e.propertyName !== "max-height") return;
        content.removeEventListener("transitionend", onEnd);
        content.style.maxHeight = "";
        content.style.overflow = "";
        content.style.opacity = "";
        content.style.transition = "";
        animating.delete(details);

        // comme sur Elytreum : on recentre après ouverture
        ensureScrollToWithOffset(details);
        resolve();
      };
      content.addEventListener("transitionend", onEnd);
    });

  const smoothClose = (details) =>
    new Promise((resolve) => {
      if (!details.open || animating.has(details)) return resolve();
      const content = details.querySelector(".rank-content");
      if (!content) {
        details.open = false;
        return resolve();
      }

      animating.add(details);
      const startHeight = content.scrollHeight;

      content.style.overflow = "hidden";
      content.style.maxHeight = startHeight + "px";
      content.style.opacity = "1";
      content.style.transition = "max-height 260ms ease, opacity 220ms ease";
      void content.offsetWidth;

      content.style.maxHeight = "0px";
      content.style.opacity = "0";

      const onEnd = (e) => {
        if (e.propertyName !== "max-height") return;
        content.removeEventListener("transitionend", onEnd);
        details.open = false;
        content.style.maxHeight = "";
        content.style.overflow = "";
        content.style.opacity = "";
        content.style.transition = "";
        animating.delete(details);
        resolve();
      };
      content.addEventListener("transitionend", onEnd);
    });

  // Ouvrir le prochain rang incomplet (appelé SEULEMENT si le rang courant était ouvert)
  const openNextIncompleteFrom = async (currentDetails) => {
    const arr = Array.from(detailsList);
    const idx = arr.indexOf(currentDetails);
    if (idx === -1) return;
    const next = arr.slice(idx + 1).find((d) => !isRankCompleted(d));
    if (!next) return;

    // fermer proprement les autres
    for (const d of arr) {
      if (d !== next && d.open) {
        // eslint-disable-next-line no-await-in-loop
        await smoothClose(d);
      }
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

    // 🔁 mettre à jour la barre de progression quand on recalcule l'état du rang
    updateRankProgressUI(details);

    saveState();

    if (allChecked && details.open) {
      smoothClose(details).then(() => openNextIncompleteFrom(details));
    }
  };

  // 3) Clic sur SUMMARY : accordéon + scroll offset (même logique qu'Elytreum)
  detailsList.forEach((details, idx) => {
    if (!details.dataset.rankId) details.dataset.rankId = "rank_" + idx;

    const summary = details.querySelector("summary");
    if (!summary) return;

    if (summary.dataset.bound === "1") return;
    summary.dataset.bound = "1";

    summary.addEventListener("click", async (e) => {
      e.preventDefault();
      if (animating.has(details)) return;

      // parent "groupe" comme .accordion sur Elytreum
      const parentAcc = details.parentElement;

      if (details.open) {
        await smoothClose(details);
      } else {
        if (parentAcc) {
          const siblings = parentAcc.querySelectorAll("details.rank");
          for (const other of siblings) {
            if (other !== details && other.open) {
              // eslint-disable-next-line no-await-in-loop
              await smoothClose(other);
            }
          }
        }
        await smoothOpen(details);
      }
    });
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
          } else {
            // pas de checkbox de rang -> on met juste à jour la barre
            updateRankProgressUI(details);
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

        // 🔁 mettre à jour la barre de progression quand on coche/décoche tout
        updateRankProgressUI(details);

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

        // après avoir synchronisé l'état, on initialise la barre
        updateRankProgressUI(details);
      });
    } else {
      // s'il n'y a pas de summary / checkbox de rang, on initialise quand même la barre
      updateRankProgressUI(details);
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

      // 🔁 réinitialise la barre de progression du rang
      updateRankProgressUI(details);

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
      document.querySelectorAll('details.rank').forEach(d => {
        d.classList.remove('rank-completed');
        // 🔁 maj barre pour chaque rang après reset global
        updateRankProgressUI(d);
      });

      resetAllBtn.classList.remove("is-animating");
      void resetAllBtn.offsetWidth;
      resetAllBtn.classList.add("is-animating");
    });
  }

  // ========= RECHERCHE RANKUP =========

  // Normalisation pour ignorer accents / casse / ligatures (é → e, œ → oe)
  const normalizeRankup = (str) =>
    String(str ?? "")
      .replace(/œ/g, "oe")
      .replace(/Œ/g, "oe")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase();

  const escapeRegex = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

  function setupRankupSearch() {
    const input = document.getElementById("rankupSearch");
    const btn = document.getElementById("rankupSearchBtn");
    const resetBtn = document.getElementById("rankupSearchReset");   // span ⌁
    const resultsBox = document.getElementById("rankupSearchResults");

    if (!input || !btn || !resetBtn || !resultsBox) return;

    // récupère le nom du rang à partir du texte, à droite de "→" si présent
    const extractRankNameFromText = (raw) => {
      if (!raw) return "";
      const arrowIdx = raw.indexOf("→");
      let target = arrowIdx >= 0 ? raw.slice(arrowIdx + 1) : raw;
      return target.trim().replace(/\s+/g, " ");
    };

    // index de tous les rangs
    const index = Array.from(detailsList).map((details) => {
      const summary = details.querySelector("summary");

      // 1) Nom principal = alt de l'image (ce que tu as dans le HTML)
      const img = summary?.querySelector("img[alt]");
      const altName = img?.getAttribute("alt")?.trim() || "";

      // 2) Texte brut du summary (peut contenir "Visiteur → Citoyen")
      const rawTitle = summary?.textContent || "";

      // 3) Nom d'affichage = alt si dispo, sinon partie droite du texte
      const displayName =
        altName || extractRankNameFromText(rawTitle);

      const nameNorm = normalizeRankup(displayName);

      // 4) Texte des objets / contenu du rang (pour recherche d’items)
      const itemsText = Array.from(
        details.querySelectorAll(".rank-content li")
      )
        .map((li) => li.textContent || "")
        .join(" ");
      const itemsNorm = normalizeRankup(itemsText);

      return {
        details,
        displayName, // ce qu'on affichera dans les résultats
        nameNorm,    // nom de rang normalisé
        itemsNorm,   // texte des objets normalisé
      };
    });

    const updateClearVisibility = () => {
      resetBtn.style.display = input.value.trim() ? "block" : "none";
    };

    const runSearch = () => {
      const qRaw = input.value.trim();
      const q = normalizeRankup(qRaw);

      resultsBox.innerHTML = "";
      if (!q) {
        updateClearVisibility();
        return;
      }

      const isSingleToken = !/\s/.test(q);
      let testFn;

      if (isSingleToken && q.length <= 3) {
        // mots très courts → match par mot entier (évite blé -> sable)
        const re = new RegExp("\\b" + escapeRegex(q) + "\\b", "i");
        testFn = (textNorm) => re.test(textNorm);
      } else {
        // sinon substring
        testFn = (textNorm) => textNorm.includes(q);
      }

      // 1) On cherche d'abord sur les NOMS de rang (alt)
      const nameMatches = index.filter(
        (node) => node.nameNorm && testFn(node.nameNorm)
      );

      let matches;
      if (nameMatches.length > 0) {
        // Si au moins un nom de rang match, on ignore les descriptions
        matches = nameMatches;
      } else {
        // Sinon, on fait une recherche dans les objets / contenu
        matches = index.filter(
          (node) => node.itemsNorm && testFn(node.itemsNorm)
        );
      }

      if (matches.length === 0) {
        resultsBox.innerHTML =
          '<p class="search-empty">Aucun rang trouvé.</p>';
        updateClearVisibility();
        return;
      }

      const ul = document.createElement("ul");
      ul.className = "search-result-list";

      matches.forEach((node) => {
        const li = document.createElement("li");
        const btnResult = document.createElement("button");
        btnResult.type = "button";
        btnResult.className = "search-result";

        // Texte du résultat : toujours le nom du rang (alt)
        const labelText = node.displayName || "Rang";
        btnResult.textContent = labelText;

        btnResult.addEventListener("click", () => {
          smoothOpen(node.details).then(() =>
            ensureScrollToWithOffset(node.details)
          );
        });

        li.appendChild(btnResult);
        ul.appendChild(li);
      });

      resultsBox.appendChild(ul);
      updateClearVisibility();
    };

    btn.addEventListener("click", runSearch);

    input.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        runSearch();
      }
    });

    resetBtn.addEventListener("click", () => {
      input.value = "";
      resultsBox.innerHTML = "";
      updateClearVisibility();
      input.focus();
    });

    updateClearVisibility();
  }


  // ========= BOUTON FLOTTANT "REMONTÉE" =========
  function setupScrollTopFab() {
    // petit style de base injecté (tu peux le déplacer en .css si tu préfères)
    const style = document.createElement("style");
    style.textContent = `
      .scroll-top-fab {
        position: fixed;
        right: 16px;
        bottom: 60px;
        width: 40px;
        height: 40px;
        border-radius: 999px;
        border: 1px solid #aba36d;
        background: #40516de0;
        color: #ead27b;
        font-size: 20px;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        z-index: 9999;
        box-shadow: 0 2px 8px rgba(0,0,0,0.5);
        opacity: 0;
        pointer-events: none;
        transition: opacity 0.2s ease, transform 0.2s ease;
      }
      .scroll-top-fab:hover {
        transform: translateY(-2px);
      }
    `;
    document.head.appendChild(style);

    const btn = document.createElement("button");
    btn.id = "rankupScrollTopFab";
    btn.className = "scroll-top-fab";
    btn.type = "button";
    btn.setAttribute("aria-label", "Remonter en haut");
    btn.textContent = "↑";

    document.body.appendChild(btn);

    const toggleVisibility = () => {
      if (window.pageYOffset > 120) {
        btn.style.opacity = "1";
        btn.style.pointerEvents = "auto";
      } else {
        btn.style.opacity = "0";
        btn.style.pointerEvents = "none";
      }
    };

    toggleVisibility();
    window.addEventListener("scroll", toggleVisibility);

    btn.addEventListener("click", () => {
      smoothScrollToTop(0, 450);
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

  // 🔍 Initialisation de la recherche Rankup
  setupRankupSearch();

  // ⬆️ Bouton flottant de remontée
  setupScrollTopFab();

}); // fin DOMContentLoaded
