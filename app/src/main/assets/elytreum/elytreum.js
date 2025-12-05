// elytreum.js
document.addEventListener("DOMContentLoaded", () => {
  const SCROLL_OFFSET = 48;

  // ---------- Scroll doux avec offset ----------
  const ensureScrollToWithOffset = (el, offset = SCROLL_OFFSET, attempts = 6) => {
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const targetTop = window.pageYOffset + rect.top - offset;

    window.scrollTo({
      top: targetTop,
      behavior: attempts === 6 ? "smooth" : "auto"
    });

    if (attempts <= 0) return;

    setTimeout(() => {
      const nowTop = window.pageYOffset;
      if (Math.abs(nowTop - targetTop) > 2) {
        requestAnimationFrame(() =>
          ensureScrollToWithOffset(el, offset, attempts - 1)
        );
      }
    }, 80);
  };

  const fmt = (n) =>
    Number(n || 0).toLocaleString("fr-FR", { maximumFractionDigits: 0 });

  const formatStacks = (qty) => {
    const q = Number(qty || 0);
    if (q < 64) return "";

    const stacks = Math.floor(q / 64);
    const rest = q % 64;

    if (rest === 0) {
      // 1 stack → afficher seulement "64"
      return stacks === 1 ? " (64)" : ` (${stacks}x64)`;
    }

    // stacks + reste
    if (stacks === 1) {
      // 1 stack → afficher "64 + reste"
      return ` (64 + ${rest})`;
    }

  return ` (${stacks}x64 + ${rest})`;
};


  // =========================================================
  //  ONGLET PRINCIPAL (Stuffs / Calculateur)
  // =========================================================
  const tabButtons = document.querySelectorAll(".tab-btn");
  const tabPanels = document.querySelectorAll(".tab-panel");

  tabButtons.forEach((btn) => {
    btn.addEventListener("click", () => {
      const tab = btn.dataset.tab;
      if (!tab) return;

      tabButtons.forEach((b) => b.classList.remove("active"));
      tabPanels.forEach((p) => p.classList.remove("active"));

      btn.classList.add("active");
      const panel = document.getElementById(`tab-${tab}`);
      if (panel) {
        panel.classList.add("active");
        ensureScrollToWithOffset(document.querySelector(".page-header"));
      }
    });
  });

  // =========================================================
  //  SOUS-ONGLETS (Armures / Outils)
  // =========================================================
  const subtabButtons = document.querySelectorAll(".subtab-btn");
  const subtabPanels = document.querySelectorAll(".subtab-panel");

  subtabButtons.forEach((btn) => {
    btn.addEventListener("click", () => {
      const sub = btn.dataset.sub;
      if (!sub) return;

      subtabButtons.forEach((b) => b.classList.remove("active"));
      subtabPanels.forEach((p) => p.classList.remove("active"));

      btn.classList.add("active");
      const target = document.getElementById(`sub-${sub}`);
      if (target) {
        target.classList.add("active");
        ensureScrollToWithOffset(document.querySelector(".subtabs"));
      }
    });
  });

  // =========================================================
  //  ACCORDÉONS (ANIMATIONS COMMUNES)
  // =========================================================
  const animating = new WeakSet();

  const smoothOpen = (details) =>
    new Promise((resolve) => {
      if (animating.has(details)) return resolve();
      const body = details.querySelector(".item-body");
      if (!body) {
        details.open = true;
        return resolve();
      }

      animating.add(details);
      details.open = true;

      body.style.overflow = "hidden";
      body.style.maxHeight = "0px";
      body.style.opacity = "0";
      body.style.transition = "max-height 260ms ease, opacity 220ms ease";
      void body.offsetWidth;

      const h = body.scrollHeight;
      body.style.maxHeight = h + "px";
      body.style.opacity = "1";

      const onEnd = (e) => {
        if (e.propertyName !== "max-height") return;
        body.removeEventListener("transitionend", onEnd);
        body.style.maxHeight = "";
        body.style.overflow = "";
        body.style.opacity = "";
        body.style.transition = "";
        animating.delete(details);
        ensureScrollToWithOffset(details);
        resolve();
      };
      body.addEventListener("transitionend", onEnd);
    });

  const smoothClose = (details) =>
    new Promise((resolve) => {
      if (!details.open || animating.has(details)) return resolve();
      const body = details.querySelector(".item-body");
      if (!body) {
        details.open = false;
        return resolve();
      }

      animating.add(details);
      const startHeight = body.scrollHeight;

      body.style.overflow = "hidden";
      body.style.maxHeight = startHeight + "px";
      body.style.opacity = "1";
      body.style.transition = "max-height 260ms ease, opacity 220ms ease";
      void body.offsetWidth;

      body.style.maxHeight = "0px";
      body.style.opacity = "0";

      const onEnd = (e) => {
        if (e.propertyName !== "max-height") return;
        body.removeEventListener("transitionend", onEnd);
        details.open = false;
        body.style.maxHeight = "";
        body.style.overflow = "";
        body.style.opacity = "";
        body.style.transition = "";
        animating.delete(details);
        resolve();
      };
      body.addEventListener("transitionend", onEnd);
    });

  const bindAnimatedDetails = (details) => {
    if (!details || !details.classList.contains("ely-item")) return;
    const summary = details.querySelector("summary");
    const body = details.querySelector(".item-body");
    if (!summary || !body) return;

    if (summary.dataset.bound === "1") return;
    summary.dataset.bound = "1";

    summary.addEventListener("click", async (e) => {
      e.preventDefault();
      if (animating.has(details)) return;

      const parentAcc =
        details.closest(".accordion") || details.parentElement;

      if (details.open) {
        await smoothClose(details);
      } else {
        if (parentAcc) {
          const siblings = parentAcc.querySelectorAll(".ely-item");
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
  };

  const initStaticAccordions = () => {
    document
      .querySelectorAll("#sub-armors .ely-item, #sub-tools .ely-item")
      .forEach(bindAnimatedDetails);
  };

  // =========================================================
  //  TOTAUX (ARMURES / OUTILS + COÛT ORBES)
  // =========================================================
  const ORB_GOLD = 15000;

  const computeTotalsFromDom = () => {
    const armorPanel = document.getElementById("sub-armors");
    const toolsPanel = document.getElementById("sub-tools");
    if (!armorPanel || !toolsPanel) return;

    let armorOrbs = 0;
    let armorGold = 0;
    let toolsOrbs = 0;
    let toolsGold = 0;

    armorPanel.querySelectorAll(".ely-item").forEach((d) => {
      const o = parseInt(d.dataset.orbs || "0", 10) || 0;
      const g = parseInt(d.dataset.gold || "0", 10) || 0;
      armorOrbs += o;
      armorGold += g;
    });

    toolsPanel.querySelectorAll(".ely-item").forEach((d) => {
      const o = parseInt(d.dataset.orbs || "0", 10) || 0;
      const g = parseInt(d.dataset.gold || "0", 10) || 0;
      toolsOrbs += o;
      toolsGold += g;
    });

    const totalOrbs = armorOrbs + toolsOrbs;
    const totalGoldItems = armorGold + toolsGold;
    const orbsGoldCost = totalOrbs * ORB_GOLD;
    const grandTotal = totalGoldItems + orbsGoldCost;

    const elTotalOrbs = document.getElementById("totalOrbs");
    const elTotalGold = document.getElementById("totalGold");
    const elArmorOrbs = document.getElementById("armorOrbs");
    const elArmorGold = document.getElementById("armorGold");
    const elToolsOrbs = document.getElementById("toolsOrbs");
    const elToolsGold = document.getElementById("toolsGold");
    const elOrbsGoldCost = document.getElementById("orbsGoldCost");
    const elGrandTotal = document.getElementById("grandTotal");

    if (elTotalOrbs) elTotalOrbs.textContent = fmt(totalOrbs);
    if (elTotalGold) elTotalGold.textContent = fmt(totalGoldItems) + " PO";
    if (elArmorOrbs) elArmorOrbs.textContent = fmt(armorOrbs);
    if (elArmorGold) elArmorGold.textContent = fmt(armorGold);
    if (elToolsOrbs) elToolsOrbs.textContent = fmt(toolsOrbs);
    if (elToolsGold) elToolsGold.textContent = fmt(toolsGold);
    if (elOrbsGoldCost)
      elOrbsGoldCost.textContent = fmt(orbsGoldCost) + " PO";
    if (elGrandTotal) elGrandTotal.textContent = fmt(grandTotal) + " PO";
  };

  // =========================================================
  //  CALCULATEUR D’ORBES → RESSOURCES BRUTES + CRAFTS
  // =========================================================

  const DIALOG_KEY = "ely_orbs_qty";

  const resources = [
    {
      id: "tungsten",
      name: "Lingot de Tungstène",
      perOrb: 256,
	  icon: "img/ely_tungstene_ingot.png",
      components: {
        "Lingot de fer": 1,
        "Poudre à canon": 1
      },
      craftHtml: `
        <div class="craft-step">
          <div class="craft-label">Étape 1 : Poudre de Tungstène</div>
          <div class="craft-row">
            <div class="craft-grid">
              <div class="craft-slot"><img src="img/mc_iron_ingot.png" alt="Lingot de fer"></div>
              <div class="craft-slot"><img src="img/mc_gunpowder.png" alt="Poudre à canon"></div>
              <div class="craft-slot empty"></div>
              <div class="craft-slot empty"></div>
              <div class="craft-slot empty"></div>
              <div class="craft-slot empty"></div>
              <div class="craft-slot empty"></div>
              <div class="craft-slot empty"></div>
              <div class="craft-slot empty"></div>
            </div>
            <div class="craft-arrow">➜</div>
            <div class="craft-slot">
              <img src="img/ely_tungstene_dust.png" alt="Poudre de Tungstène">
          </div>
          </div>
        </div>
        <div class="craft-step">
          <div class="craft-label">Étape 2 : Fonte</div>
          <div class="craft-row">
            <div class="craft-furnace">
              <div class="craft-slot">
				<img src="img/ely_tungstene_dust.png" alt="Poudre de Tungstène">
			  </div>
			  <span>➜</span>
			  <div class="craft-slot">
				<img src="img/mc_furnace.gif" alt="four">
				<span>23 secondes</span>
			  </div>
			  <span>➜</span>
			  <div class="craft-slot">
				<img src="img/ely_tungstene_ingot.png" alt="Lingot de Tungstène">
              </div>
              
            </div>
          </div>
        </div>
      `
    },
    {
      id: "darkShard",
      name: "Éclat sombre",
      perOrb: 128,
	  icon: "img/ely_dark_shard.png",
      components: {
        "Bloc d’obsidienne": 2,
        "Bloc d’améthyste": 4,
        "Éclat d’améthyste": 1
      },
      craftHtml: `
        <div class="craft-step">
          <div class="craft-label">Étape 1 : Poudre d’obsidienne</div>
          <div class="craft-row">
            <div class="craft-furnace">
              <div class="craft-slot">
				<img src="img/mc_obsidian.png" alt="Obsidienne">
			  </div>
			  <span>➜</span>
			  <div class="craft-slot">
				<img src="img/mc_blast_furnace.gif" alt="Haut fourneau">
				<span>36 secondes</span>
			  </div>
			  <span>➜</span>
			  <div class="craft-slot">
				<img src="img/ely_obsidian_dust.png" alt="Poudre d'obsidienne">
				<span>x2</span>
              </div>
              
            </div>
          </div>
        </div>
        <div class="craft-step">
          <div class="craft-label">Étape 2 : Craft de l’Éclat sombre</div>
          <div class="craft-row">
            <div class="craft-grid">
              <div class="craft-slot"><img src="img/mc_amethyst_block.png" alt="Bloc d'améthyste"></div>
              <div class="craft-slot"><img src="img/ely_obsidian_dust.png" alt="Poudre d'obsidienne"></div>
              <div class="craft-slot"><img src="img/mc_amethyst_block.png" alt="Bloc d'améthyste"></div>
              <div class="craft-slot"><img src="img/ely_obsidian_dust.png" alt="Poudre d'obsidienne"></div>
	  		  <div class="craft-slot"><img src="img/mc_amethyst_shard.png" alt="Éclat d'améthyste"></div>
              <div class="craft-slot"><img src="img/ely_obsidian_dust.png" alt="Poudre d'obsidienne"></div>
              <div class="craft-slot"><img src="img/mc_amethyst_block.png" alt="Bloc d'améthyste"></div>
              <div class="craft-slot"><img src="img/ely_obsidian_dust.png" alt="Poudre d'obsidienne"></div>
              <div class="craft-slot"><img src="img/mc_amethyst_block.png" alt="Bloc d'améthyste"></div>
            </div>
            <div class="craft-arrow">➜</div>
            <div class="craft-slot">
              <img src="img/ely_dark_shard.png" alt="Éclat sombre">
			</div>
          </div>
        </div>
      `
    },
    {
      id: "oxydite",
      name: "Gemme d’Oxydite",
      perOrb: 3,
	  icon: "img/ely_oxydite_gem.png",
      components: {
        "Éclat d’écho": 2,
        "Roche noire dorée": 1,
        "Lingot de Netherite": 2,
        "Cristal de l’End": 1,
        "Bloc d’or brut": 2,
        "Cœur de la mer": 1
      },
      craftHtml: `
        <div class="craft-step">
          <div class="craft-label">Craft de la Gemme d’Oxydite</div>
          <div class="craft-row">
            <div class="craft-grid">
              <div class="craft-slot"><img src="img/mc_echo_shard.png" alt="Éclat d'écho"></div>
              <div class="craft-slot"><img src="img/mc_gilded_blackstone.png" alt="Roche noire dorée"></div>
              <div class="craft-slot"><img src="img/mc_echo_shard.png" alt="Éclat d'écho"></div>
              <div class="craft-slot"><img src="img/mc_raw_gold_block.png" alt="Bloc d'or brut"></div>
              <div class="craft-slot"><img src="img/mc_heart_of_the_sea.png" alt="Cœur de la mer"></div>
              <div class="craft-slot"><img src="img/mc_raw_gold_block.png" alt="Bloc d'or brut"></div>
              <div class="craft-slot"><img src="img/mc_netherite_ingot.png" alt="Lingot de Netherite"></div>
              <div class="craft-slot"><img src="img/mc_end_crystal.gif" alt="Cristal de l'end"></div>
              <div class="craft-slot"><img src="img/mc_netherite_ingot.png" alt="Lingot de Netherite"></div>
            </div>
            <div class="craft-arrow">➜</div>
          <div class="craft-slot">
            <img src="img/ely_oxydite_gem.png" alt="Gemme d’Oxydite">
          </div>
          </div>
        </div>
      `
    },
    {
      id: "etherium",
      name: "Bloc d’Etherium",
      perOrb: 10,
	  icon: "img/ely_etherium_block.png",
      components: {
        "Éclat d’Etherium": 8,
        "Diamant": 1
      },
      craftHtml: `
        <div class="craft-step">
          <div class="craft-label">Étape 1 : Poudre d’Etherium</div>
          <div class="craft-row">
            <div class="craft-grid">
              <div class="craft-slot"><img src="img/mc_mace.png" alt="Masse"></div>
              <div class="craft-slot"><img src="img/ely_etherium_shard.png" alt="Éclat d'Etherium"></div>
		    </div>
            <div class="craft-arrow">➜</div>
            <div class="craft-slot">
              <img src="img/ely_etherium_dust.png" alt="Poudre d'Etherium">
            </div>
            </div>
          </div>
        </div>
        <div class="craft-step">
          <div class="craft-label">Étape 2 : Bloc d’Etherium</div>
          <div class="craft-row">
            <div class="craft-grid">
              <div class="craft-slot"><img src="img/ely_etherium_dust.png" alt="Poudre d'Etherium"></div>
              <div class="craft-slot"><img src="img/ely_etherium_shard.png" alt="Éclat d'Etherium"></div>
              <div class="craft-slot"><img src="img/ely_etherium_dust.png" alt="Poudre d'Etherium"></div>
              <div class="craft-slot"><img src="img/ely_etherium_shard.png" alt="Éclat d'Etherium"></div>
              <div class="craft-slot"><img src="img/mc_diamond.png" alt="Diamant"></div>
              <div class="craft-slot"><img src="img/ely_etherium_shard.png" alt="Éclat d'Etherium"></div>
              <div class="craft-slot"><img src="img/ely_etherium_dust.png" alt="Poudre d'Etherium"></div>
              <div class="craft-slot"><img src="img/ely_etherium_shard.png" alt="Éclat d'Etherium"></div>
              <div class="craft-slot"><img src="img/ely_etherium_dust.png" alt="Poudre d'Etherium"></div>
            </div>
            <div class="craft-arrow">➜</div>
            <div class="craft-slot">
              <img src="img/ely_etherium_block.png" alt="Bloc d'Etherium">
            </div>
          </div>
        </div>
      `
    }
  ];

  const orbsInput = document.getElementById("orbsInput");
  const orbsGoldEl = document.getElementById("orbsGold");
  const resourcesAcc = document.getElementById("resourcesAcc");

  let currentOrbs = parseInt(localStorage.getItem(DIALOG_KEY) || "1", 10);
  if (!Number.isFinite(currentOrbs) || currentOrbs < 1) currentOrbs = 1;

  if (orbsInput) {
    orbsInput.value = String(currentOrbs);
    orbsInput.addEventListener("input", () => {
      let val = parseInt(orbsInput.value, 10);
      if (!Number.isFinite(val) || val < 1) val = 1;
      currentOrbs = val;
      localStorage.setItem(DIALOG_KEY, String(currentOrbs));
      updateCalcUI();
    });
  }

  const updateCalcUI = () => {
    if (orbsGoldEl) {
      orbsGoldEl.textContent = fmt(currentOrbs * ORB_GOLD) + " PO";
    }
    if (!resourcesAcc) return;

    resourcesAcc.innerHTML = "";

    // ---- 1) Récap global de toutes les ressources brutes ----
    const globalTotals = {};

    resources.forEach((res) => {
      const totalCustom = res.perOrb * currentOrbs;
      if (res.components) {
        Object.entries(res.components).forEach(([name, perUnit]) => {
          const qty = perUnit * totalCustom;
          globalTotals[name] = (globalTotals[name] || 0) + qty;
        });
      }
    });

    const recap = document.createElement("details");
    recap.className = "ely-item";
    const recapSummary = document.createElement("summary");
    recapSummary.innerHTML = `
      <div class="item-head">
        <div class="item-name">Récapitulatif complet des ressources</div>
        <div class="item-cost">Pour ${fmt(currentOrbs)} orbe(s) d’Elytreum</div>
      </div>
    `;
    const recapBody = document.createElement("div");
    recapBody.className = "item-body";

        let recapList = "<h4>Total de toutes les ressources brutes</h4>";

    resources.forEach((res, idx) => {
      if (!res.components) return;

      recapList += "<ul>";

      Object.entries(res.components).forEach(([name]) => {
        const qty = globalTotals[name] || 0;
        recapList += `<li><strong>${fmt(qty)}</strong> <em>${formatStacks(qty)}</em><strong> -</strong> ${name}</li>`;
      });

      recapList += "</ul>";

      // Ajoute un séparateur entre chaque groupe, sauf après le dernier
      if (idx < resources.length - 1) {
        recapList += `<div class="recap-separator"></div>`;
      }
    });


    recapBody.innerHTML = recapList;

    recap.appendChild(recapSummary);
    recap.appendChild(recapBody);
    resourcesAcc.appendChild(recap);
    bindAnimatedDetails(recap);

    // ---- 2) Détail par minerai custom + craft visuel ----
    resources.forEach((res) => {
      const totalCustom = res.perOrb * currentOrbs;

      const details = document.createElement("details");
      details.className = "ely-item";

      const summary = document.createElement("summary");
	  summary.innerHTML = `
	    <div class="item-img">
		  <img src="${res.icon}" alt="${res.name}" onerror="this.style.opacity=.3">
	    </div>
	    <div class="item-head">
		  <div class="item-name">${res.name}</div>
		  <div class="item-cost">
		    ${fmt(totalCustom)}${formatStacks(totalCustom)} × ${res.name}
		  </div>
	    </div>
	  `;


      const body = document.createElement("div");
      body.className = "item-body";

      let bodyHtml = "";

      if (res.craftHtml) {
        bodyHtml += res.craftHtml;
      }

      if (res.components) {
        bodyHtml += "<h4>Ressources nécessaires totales</h4><ul>";
        Object.entries(res.components).forEach(([name, perUnit]) => {
          const qty = perUnit * totalCustom;
          bodyHtml += `<li><strong>${fmt(qty)}</strong> <em>${formatStacks(qty)}</em><strong> -</strong> ${name}</li>`;		  
        });
        bodyHtml += "</ul>";
      }

      body.innerHTML = bodyHtml;

      details.appendChild(summary);
      details.appendChild(body);
      resourcesAcc.appendChild(details);
      bindAnimatedDetails(details);
    });
  };


  // =========================================================
  //  MISE EN VALEUR AUTO DES ENCHANTEMENTS (avant ":")
  //  + surbrillance spéciale pour enchants custom Elytreum
  // =========================================================
  const highlightEnchantments = () => {
  const CUSTOM_ENCHANTS = new Set([
    "Branchie",
    "Mastodonte II",
    "Adrénaline II",
    "Vitesse III",
    "Attraction",
    "Détraqueur I",
    "Spadassin III",
    "Beau Jeu I",
    "Filon instantané II",
    "Terrasseur",
    "Bûcheron VI",
    "Bulldozer",
    "Récolte Expérimentée",
  ]);

  const zones = document.querySelectorAll(
    "#sub-armors .item-body li, #sub-tools .item-body li"
  );

  zones.forEach((li) => {
    if (li.dataset.enchDone === "1") return;

    const html = li.innerHTML;
    const idx = html.indexOf(":");
    if (idx <= 0) return;

    const labelRaw = html.slice(0, idx).trim();
    const rest = html.slice(idx + 1);

    const isCustom = CUSTOM_ENCHANTS.has(labelRaw);
    const cls = isCustom ? "ench ench-custom" : "ench";

    li.innerHTML = `<span class="${cls}">${labelRaw}</span> :${rest}`;
    li.dataset.enchDone = "1";
  });
};


  // ========= BOUTON FLOTTANT "REMONTÉE" =========
  function setupScrollTopFab() {
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
		-webkit-tap-highlight-color: transparent;  /* enlève le halo bleu Android */
		outline: none;                             /* pas de contour focus moche */
      }
      .scroll-top-fab:hover {
        transform: translateY(-2px);
      }
	  .scroll-top-fab:focus,
	  .scroll-top-fab:focus-visible {
		outline: none;
}
    `;
    document.head.appendChild(style);

    const btn = document.createElement("button");
    btn.id = "elytreumScrollTopFab";
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
      window.scrollTo({ top: 0, behavior: "smooth" });
    });
  }



  // =========================================================
  //  INIT
  // =========================================================
  initStaticAccordions();   // animations sur armures/outils
  computeTotalsFromDom();   // totaux stuffs
  updateCalcUI();           // construit le calculateur + animations
  highlightEnchantments();  // applique le style sur les enchantements
  setupScrollTopFab(); // activation du bouton
});

