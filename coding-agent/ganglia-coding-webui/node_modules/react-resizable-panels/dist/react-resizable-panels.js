"use client";
import { jsx as oe } from "react/jsx-runtime";
import { useState as ie, useCallback as te, useId as pt, useLayoutEffect as Be, useEffect as de, useRef as T, createContext as ht, useImperativeHandle as We, useMemo as Ue, useSyncExternalStore as Ke, useContext as mt } from "react";
function gt(e, t) {
  const n = getComputedStyle(e), o = parseFloat(n.fontSize);
  return t * o;
}
function St(e, t) {
  const n = getComputedStyle(e.ownerDocument.body), o = parseFloat(n.fontSize);
  return t * o;
}
function yt(e) {
  return e / 100 * window.innerHeight;
}
function vt(e) {
  return e / 100 * window.innerWidth;
}
function zt(e) {
  switch (typeof e) {
    case "number":
      return [e, "px"];
    case "string": {
      const t = parseFloat(e);
      return e.endsWith("%") ? [t, "%"] : e.endsWith("px") ? [t, "px"] : e.endsWith("rem") ? [t, "rem"] : e.endsWith("em") ? [t, "em"] : e.endsWith("vh") ? [t, "vh"] : e.endsWith("vw") ? [t, "vw"] : [t, "%"];
    }
  }
}
function le({
  groupSize: e,
  panelElement: t,
  styleProp: n
}) {
  let o;
  const [r, a] = zt(n);
  switch (a) {
    case "%": {
      o = r / 100 * e;
      break;
    }
    case "px": {
      o = r;
      break;
    }
    case "rem": {
      o = St(t, r);
      break;
    }
    case "em": {
      o = gt(t, r);
      break;
    }
    case "vh": {
      o = yt(r);
      break;
    }
    case "vw": {
      o = vt(r);
      break;
    }
  }
  return o;
}
function D(e) {
  return parseFloat(e.toFixed(3));
}
function Q({
  group: e
}) {
  const { orientation: t, panels: n } = e;
  return n.reduce((o, r) => (o += t === "horizontal" ? r.element.offsetWidth : r.element.offsetHeight, o), 0);
}
function ge(e) {
  const { panels: t } = e, n = Q({ group: e });
  return n === 0 ? t.map((o) => ({
    groupResizeBehavior: o.panelConstraints.groupResizeBehavior,
    collapsedSize: 0,
    collapsible: o.panelConstraints.collapsible === !0,
    defaultSize: void 0,
    disabled: o.panelConstraints.disabled,
    minSize: 0,
    maxSize: 100,
    panelId: o.id
  })) : t.map((o) => {
    const { element: r, panelConstraints: a } = o;
    let u = 0;
    if (a.collapsedSize !== void 0) {
      const c = le({
        groupSize: n,
        panelElement: r,
        styleProp: a.collapsedSize
      });
      u = D(c / n * 100);
    }
    let i;
    if (a.defaultSize !== void 0) {
      const c = le({
        groupSize: n,
        panelElement: r,
        styleProp: a.defaultSize
      });
      i = D(c / n * 100);
    }
    let s = 0;
    if (a.minSize !== void 0) {
      const c = le({
        groupSize: n,
        panelElement: r,
        styleProp: a.minSize
      });
      s = D(c / n * 100);
    }
    let l = 100;
    if (a.maxSize !== void 0) {
      const c = le({
        groupSize: n,
        panelElement: r,
        styleProp: a.maxSize
      });
      l = D(c / n * 100);
    }
    return {
      groupResizeBehavior: a.groupResizeBehavior,
      collapsedSize: u,
      collapsible: a.collapsible === !0,
      defaultSize: i,
      disabled: a.disabled,
      minSize: s,
      maxSize: l,
      panelId: o.id
    };
  });
}
function L(e, t = "Assertion error") {
  if (!e)
    throw Error(t);
}
function Se(e, t) {
  return Array.from(t).sort(
    e === "horizontal" ? bt : xt
  );
}
function bt(e, t) {
  const n = e.element.offsetLeft - t.element.offsetLeft;
  return n !== 0 ? n : e.element.offsetWidth - t.element.offsetWidth;
}
function xt(e, t) {
  const n = e.element.offsetTop - t.element.offsetTop;
  return n !== 0 ? n : e.element.offsetHeight - t.element.offsetHeight;
}
function Xe(e) {
  return e !== null && typeof e == "object" && "nodeType" in e && e.nodeType === Node.ELEMENT_NODE;
}
function qe(e, t) {
  return {
    x: e.x >= t.left && e.x <= t.right ? 0 : Math.min(
      Math.abs(e.x - t.left),
      Math.abs(e.x - t.right)
    ),
    y: e.y >= t.top && e.y <= t.bottom ? 0 : Math.min(
      Math.abs(e.y - t.top),
      Math.abs(e.y - t.bottom)
    )
  };
}
function wt({
  orientation: e,
  rects: t,
  targetRect: n
}) {
  const o = {
    x: n.x + n.width / 2,
    y: n.y + n.height / 2
  };
  let r, a = Number.MAX_VALUE;
  for (const u of t) {
    const { x: i, y: s } = qe(o, u), l = e === "horizontal" ? i : s;
    l < a && (a = l, r = u);
  }
  return L(r, "No rect found"), r;
}
let ue;
function Pt() {
  return ue === void 0 && (typeof matchMedia == "function" ? ue = !!matchMedia("(pointer:coarse)").matches : ue = !1), ue;
}
function Ye(e) {
  const { element: t, orientation: n, panels: o, separators: r } = e, a = Se(
    n,
    Array.from(t.children).filter(Xe).map((x) => ({ element: x }))
  ).map(({ element: x }) => x), u = [];
  let i = !1, s = !1, l = -1, c = -1, m = 0, p, v = [];
  {
    let x = -1;
    for (const f of a)
      f.hasAttribute("data-panel") && (x++, f.ariaDisabled === null && (m++, l === -1 && (l = x), c = x));
  }
  if (m > 1) {
    let x = -1;
    for (const f of a)
      if (f.hasAttribute("data-panel")) {
        x++;
        const h = o.find(
          (g) => g.element === f
        );
        if (h) {
          if (p) {
            const g = p.element.getBoundingClientRect(), y = f.getBoundingClientRect();
            let z;
            if (s) {
              const S = n === "horizontal" ? new DOMRect(
                g.right,
                g.top,
                0,
                g.height
              ) : new DOMRect(
                g.left,
                g.bottom,
                g.width,
                0
              ), d = n === "horizontal" ? new DOMRect(y.left, y.top, 0, y.height) : new DOMRect(y.left, y.top, y.width, 0);
              switch (v.length) {
                case 0: {
                  z = [
                    S,
                    d
                  ];
                  break;
                }
                case 1: {
                  const P = v[0], R = wt({
                    orientation: n,
                    rects: [g, y],
                    targetRect: P.element.getBoundingClientRect()
                  });
                  z = [
                    P,
                    R === g ? d : S
                  ];
                  break;
                }
                default: {
                  z = v;
                  break;
                }
              }
            } else
              v.length ? z = v : z = [
                n === "horizontal" ? new DOMRect(
                  g.right,
                  y.top,
                  y.left - g.right,
                  y.height
                ) : new DOMRect(
                  y.left,
                  g.bottom,
                  y.width,
                  y.top - g.bottom
                )
              ];
            for (const S of z) {
              let d = "width" in S ? S : S.element.getBoundingClientRect();
              const P = Pt() ? e.resizeTargetMinimumSize.coarse : e.resizeTargetMinimumSize.fine;
              if (d.width < P) {
                const C = P - d.width;
                d = new DOMRect(
                  d.x - C / 2,
                  d.y,
                  d.width + C,
                  d.height
                );
              }
              if (d.height < P) {
                const C = P - d.height;
                d = new DOMRect(
                  d.x,
                  d.y - C / 2,
                  d.width,
                  d.height + C
                );
              }
              const R = x <= l || x > c;
              !i && !R && u.push({
                group: e,
                groupSize: Q({ group: e }),
                panels: [p, h],
                separator: "width" in S ? void 0 : S,
                rect: d
              }), i = !1;
            }
          }
          s = !1, p = h, v = [];
        }
      } else if (f.hasAttribute("data-separator")) {
        f.ariaDisabled !== null && (i = !0);
        const h = r.find(
          (g) => g.element === f
        );
        h ? v.push(h) : (p = void 0, v = []);
      } else
        s = !0;
  }
  return u;
}
class Je {
  #e = {};
  addListener(t, n) {
    const o = this.#e[t];
    return o === void 0 ? this.#e[t] = [n] : o.includes(n) || o.push(n), () => {
      this.removeListener(t, n);
    };
  }
  emit(t, n) {
    const o = this.#e[t];
    if (o !== void 0)
      if (o.length === 1)
        o[0].call(null, n);
      else {
        let r = !1, a = null;
        const u = Array.from(o);
        for (let i = 0; i < u.length; i++) {
          const s = u[i];
          try {
            s.call(null, n);
          } catch (l) {
            a === null && (r = !0, a = l);
          }
        }
        if (r)
          throw a;
      }
  }
  removeAllListeners() {
    this.#e = {};
  }
  removeListener(t, n) {
    const o = this.#e[t];
    if (o !== void 0) {
      const r = o.indexOf(n);
      r >= 0 && o.splice(r, 1);
    }
  }
}
let A = /* @__PURE__ */ new Map();
const Ze = new Je();
function Lt(e) {
  A = new Map(A), A.delete(e);
}
function Me(e, t) {
  for (const [n] of A)
    if (n.id === e)
      return n;
}
function $(e, t) {
  for (const [n, o] of A)
    if (n.id === e)
      return o;
  if (t)
    throw Error(`Could not find data for Group with id ${e}`);
}
function U() {
  return A;
}
function ye(e, t) {
  return Ze.addListener("groupChange", (n) => {
    n.group.id === e && t(n);
  });
}
function F(e, t) {
  const n = A.get(e);
  A = new Map(A), A.set(e, t), Ze.emit("groupChange", {
    group: e,
    prev: n,
    next: t
  });
}
function Ct(e, t, n) {
  let o, r = {
    x: 1 / 0,
    y: 1 / 0
  };
  for (const a of t) {
    const u = qe(n, a.rect);
    switch (e) {
      case "horizontal": {
        u.x <= r.x && (o = a, r = u);
        break;
      }
      case "vertical": {
        u.y <= r.y && (o = a, r = u);
        break;
      }
    }
  }
  return o ? {
    distance: r,
    hitRegion: o
  } : void 0;
}
function Rt(e) {
  return e !== null && typeof e == "object" && "nodeType" in e && e.nodeType === Node.DOCUMENT_FRAGMENT_NODE;
}
function Mt(e, t) {
  if (e === t) throw new Error("Cannot compare node with itself");
  const n = {
    a: ke(e),
    b: ke(t)
  };
  let o;
  for (; n.a.at(-1) === n.b.at(-1); )
    o = n.a.pop(), n.b.pop();
  L(
    o,
    "Stacking order can only be calculated for elements with a common ancestor"
  );
  const r = {
    a: Ie(Ee(n.a)),
    b: Ie(Ee(n.b))
  };
  if (r.a === r.b) {
    const a = o.childNodes, u = {
      a: n.a.at(-1),
      b: n.b.at(-1)
    };
    let i = a.length;
    for (; i--; ) {
      const s = a[i];
      if (s === u.a) return 1;
      if (s === u.b) return -1;
    }
  }
  return Math.sign(r.a - r.b);
}
const Et = /\b(?:position|zIndex|opacity|transform|webkitTransform|mixBlendMode|filter|webkitFilter|isolation)\b/;
function It(e) {
  const t = getComputedStyle(Qe(e) ?? e).display;
  return t === "flex" || t === "inline-flex";
}
function kt(e) {
  const t = getComputedStyle(e);
  return !!(t.position === "fixed" || t.zIndex !== "auto" && (t.position !== "static" || It(e)) || +t.opacity < 1 || "transform" in t && t.transform !== "none" || "webkitTransform" in t && t.webkitTransform !== "none" || "mixBlendMode" in t && t.mixBlendMode !== "normal" || "filter" in t && t.filter !== "none" || "webkitFilter" in t && t.webkitFilter !== "none" || "isolation" in t && t.isolation === "isolate" || Et.test(t.willChange) || t.webkitOverflowScrolling === "touch");
}
function Ee(e) {
  let t = e.length;
  for (; t--; ) {
    const n = e[t];
    if (L(n, "Missing node"), kt(n)) return n;
  }
  return null;
}
function Ie(e) {
  return e && Number(getComputedStyle(e).zIndex) || 0;
}
function ke(e) {
  const t = [];
  for (; e; )
    t.push(e), e = Qe(e);
  return t;
}
function Qe(e) {
  const { parentNode: t } = e;
  return Rt(t) ? t.host : t;
}
function Dt(e, t) {
  return e.x < t.x + t.width && e.x + e.width > t.x && e.y < t.y + t.height && e.y + e.height > t.y;
}
function Tt({
  groupElement: e,
  hitRegion: t,
  pointerEventTarget: n
}) {
  if (!Xe(n) || n.contains(e) || e.contains(n))
    return !0;
  if (Mt(n, e) > 0) {
    let o = n;
    for (; o; ) {
      if (o.contains(e))
        return !0;
      if (Dt(o.getBoundingClientRect(), t))
        return !1;
      o = o.parentElement;
    }
  }
  return !0;
}
function ve(e, t) {
  const n = [];
  return t.forEach((o, r) => {
    if (r.disabled)
      return;
    const a = Ye(r), u = Ct(r.orientation, a, {
      x: e.clientX,
      y: e.clientY
    });
    u && u.distance.x <= 0 && u.distance.y <= 0 && Tt({
      groupElement: r.element,
      hitRegion: u.hitRegion.rect,
      pointerEventTarget: e.target
    }) && n.push(u.hitRegion);
  }), n;
}
function Ot(e, t) {
  if (e.length !== t.length)
    return !1;
  for (let n = 0; n < e.length; n++)
    if (e[n] != t[n])
      return !1;
  return !0;
}
function I(e, t, n = 0) {
  return Math.abs(D(e) - D(t)) <= n;
}
function G(e, t) {
  return I(e, t) ? 0 : e > t ? 1 : -1;
}
function Y({
  overrideDisabledPanels: e,
  panelConstraints: t,
  prevSize: n,
  size: o
}) {
  const {
    collapsedSize: r = 0,
    collapsible: a,
    disabled: u,
    maxSize: i = 100,
    minSize: s = 0
  } = t;
  if (u && !e)
    return n;
  if (G(o, s) < 0)
    if (a) {
      const l = (r + s) / 2;
      G(o, l) < 0 ? o = r : o = s;
    } else
      o = s;
  return o = Math.min(i, o), o = D(o), o;
}
function re({
  delta: e,
  initialLayout: t,
  panelConstraints: n,
  pivotIndices: o,
  prevLayout: r,
  trigger: a
}) {
  if (I(e, 0))
    return t;
  const u = a === "imperative-api", i = Object.values(t), s = Object.values(r), l = [...i], [c, m] = o;
  L(c != null, "Invalid first pivot index"), L(m != null, "Invalid second pivot index");
  let p = 0;
  switch (a) {
    case "keyboard": {
      {
        const f = e < 0 ? m : c, h = n[f];
        L(
          h,
          `Panel constraints not found for index ${f}`
        );
        const {
          collapsedSize: g = 0,
          collapsible: y,
          minSize: z = 0
        } = h;
        if (y) {
          const S = i[f];
          if (L(
            S != null,
            `Previous layout not found for panel index ${f}`
          ), I(S, g)) {
            const d = z - S;
            G(d, Math.abs(e)) > 0 && (e = e < 0 ? 0 - d : d);
          }
        }
      }
      {
        const f = e < 0 ? c : m, h = n[f];
        L(
          h,
          `No panel constraints found for index ${f}`
        );
        const {
          collapsedSize: g = 0,
          collapsible: y,
          minSize: z = 0
        } = h;
        if (y) {
          const S = i[f];
          if (L(
            S != null,
            `Previous layout not found for panel index ${f}`
          ), I(S, z)) {
            const d = S - g;
            G(d, Math.abs(e)) > 0 && (e = e < 0 ? 0 - d : d);
          }
        }
      }
      break;
    }
    default: {
      const f = e < 0 ? m : c, h = n[f];
      L(
        h,
        `Panel constraints not found for index ${f}`
      );
      const g = i[f], { collapsible: y, collapsedSize: z, minSize: S } = h;
      if (y && G(g, S) < 0)
        if (e > 0) {
          const d = S - z, P = d / 2, R = g + e;
          G(R, S) < 0 && (e = G(e, P) <= 0 ? 0 : d);
        } else {
          const d = S - z, P = 100 - d / 2, R = g - e;
          G(R, S) < 0 && (e = G(100 + e, P) > 0 ? 0 : -d);
        }
      break;
    }
  }
  {
    const f = e < 0 ? 1 : -1;
    let h = e < 0 ? m : c, g = 0;
    for (; ; ) {
      const z = i[h];
      L(
        z != null,
        `Previous layout not found for panel index ${h}`
      );
      const d = Y({
        overrideDisabledPanels: u,
        panelConstraints: n[h],
        prevSize: z,
        size: 100
      }) - z;
      if (g += d, h += f, h < 0 || h >= n.length)
        break;
    }
    const y = Math.min(Math.abs(e), Math.abs(g));
    e = e < 0 ? 0 - y : y;
  }
  {
    let h = e < 0 ? c : m;
    for (; h >= 0 && h < n.length; ) {
      const g = Math.abs(e) - Math.abs(p), y = i[h];
      L(
        y != null,
        `Previous layout not found for panel index ${h}`
      );
      const z = y - g, S = Y({
        overrideDisabledPanels: u,
        panelConstraints: n[h],
        prevSize: y,
        size: z
      });
      if (!I(y, S) && (p += y - S, l[h] = S, p.toFixed(3).localeCompare(Math.abs(e).toFixed(3), void 0, {
        numeric: !0
      }) >= 0))
        break;
      e < 0 ? h-- : h++;
    }
  }
  if (Ot(s, l))
    return r;
  {
    const f = e < 0 ? m : c, h = i[f];
    L(
      h != null,
      `Previous layout not found for panel index ${f}`
    );
    const g = h + p, y = Y({
      overrideDisabledPanels: u,
      panelConstraints: n[f],
      prevSize: h,
      size: g
    });
    if (l[f] = y, !I(y, g)) {
      let z = g - y, d = e < 0 ? m : c;
      for (; d >= 0 && d < n.length; ) {
        const P = l[d];
        L(
          P != null,
          `Previous layout not found for panel index ${d}`
        );
        const R = P + z, C = Y({
          overrideDisabledPanels: u,
          panelConstraints: n[d],
          prevSize: P,
          size: R
        });
        if (I(P, C) || (z -= C - P, l[d] = C), I(z, 0))
          break;
        e > 0 ? d-- : d++;
      }
    }
  }
  const v = Object.values(l).reduce(
    (f, h) => h + f,
    0
  );
  if (!I(v, 100, 0.1))
    return r;
  const x = Object.keys(r);
  return l.reduce((f, h, g) => (f[x[g]] = h, f), {});
}
function j(e, t) {
  if (Object.keys(e).length !== Object.keys(t).length)
    return !1;
  for (const n in e)
    if (t[n] === void 0 || G(e[n], t[n]) !== 0)
      return !1;
  return !0;
}
function B({
  layout: e,
  panelConstraints: t
}) {
  const n = Object.values(e), o = [...n], r = o.reduce(
    (i, s) => i + s,
    0
  );
  if (o.length !== t.length)
    throw Error(
      `Invalid ${t.length} panel layout: ${o.map((i) => `${i}%`).join(", ")}`
    );
  if (!I(r, 100) && o.length > 0)
    for (let i = 0; i < t.length; i++) {
      const s = o[i];
      L(s != null, `No layout data found for index ${i}`);
      const l = 100 / r * s;
      o[i] = l;
    }
  let a = 0;
  for (let i = 0; i < t.length; i++) {
    const s = n[i];
    L(s != null, `No layout data found for index ${i}`);
    const l = o[i];
    L(l != null, `No layout data found for index ${i}`);
    const c = Y({
      overrideDisabledPanels: !0,
      panelConstraints: t[i],
      prevSize: s,
      size: l
    });
    l != c && (a += l - c, o[i] = c);
  }
  if (!I(a, 0))
    for (let i = 0; i < t.length; i++) {
      const s = o[i];
      L(s != null, `No layout data found for index ${i}`);
      const l = s + a, c = Y({
        overrideDisabledPanels: !0,
        panelConstraints: t[i],
        prevSize: s,
        size: l
      });
      if (s !== c && (a -= c - s, o[i] = c, I(a, 0)))
        break;
    }
  const u = Object.keys(e);
  return o.reduce((i, s, l) => (i[u[l]] = s, i), {});
}
function et({
  groupId: e,
  panelId: t
}) {
  const n = () => {
    const i = U();
    for (const [
      s,
      {
        defaultLayoutDeferred: l,
        derivedPanelConstraints: c,
        layout: m,
        groupSize: p,
        separatorToPanels: v
      }
    ] of i)
      if (s.id === e)
        return {
          defaultLayoutDeferred: l,
          derivedPanelConstraints: c,
          group: s,
          groupSize: p,
          layout: m,
          separatorToPanels: v
        };
    throw Error(`Group ${e} not found`);
  }, o = () => {
    const i = n().derivedPanelConstraints.find(
      (s) => s.panelId === t
    );
    if (i !== void 0)
      return i;
    throw Error(`Panel constraints not found for Panel ${t}`);
  }, r = () => {
    const i = n().group.panels.find((s) => s.id === t);
    if (i !== void 0)
      return i;
    throw Error(`Layout not found for Panel ${t}`);
  }, a = () => {
    const i = n().layout[t];
    if (i !== void 0)
      return i;
    throw Error(`Layout not found for Panel ${t}`);
  }, u = (i) => {
    const s = a();
    if (i === s)
      return;
    const {
      defaultLayoutDeferred: l,
      derivedPanelConstraints: c,
      group: m,
      groupSize: p,
      layout: v,
      separatorToPanels: x
    } = n(), f = m.panels.findIndex((z) => z.id === t), h = f === m.panels.length - 1, g = re({
      delta: h ? s - i : i - s,
      initialLayout: v,
      panelConstraints: c,
      pivotIndices: h ? [f - 1, f] : [f, f + 1],
      prevLayout: v,
      trigger: "imperative-api"
    }), y = B({
      layout: g,
      panelConstraints: c
    });
    j(v, y) || F(m, {
      defaultLayoutDeferred: l,
      derivedPanelConstraints: c,
      groupSize: p,
      layout: y,
      separatorToPanels: x
    });
  };
  return {
    collapse: () => {
      const { collapsible: i, collapsedSize: s } = o(), { mutableValues: l } = r(), c = a();
      i && c !== s && (l.expandToSize = c, u(s));
    },
    expand: () => {
      const { collapsible: i, collapsedSize: s, minSize: l } = o(), { mutableValues: c } = r(), m = a();
      if (i && m === s) {
        let p = c.expandToSize ?? l;
        p === 0 && (p = 1), u(p);
      }
    },
    getSize: () => {
      const { group: i } = n(), s = a(), { element: l } = r(), c = i.orientation === "horizontal" ? l.offsetWidth : l.offsetHeight;
      return {
        asPercentage: s,
        inPixels: c
      };
    },
    isCollapsed: () => {
      const { collapsible: i, collapsedSize: s } = o(), l = a();
      return i && I(s, l);
    },
    resize: (i) => {
      if (a() !== i) {
        let l;
        switch (typeof i) {
          case "number": {
            const { group: c } = n(), m = Q({ group: c });
            l = D(i / m * 100);
            break;
          }
          case "string": {
            l = parseFloat(i);
            break;
          }
        }
        u(l);
      }
    }
  };
}
function De(e) {
  if (e.defaultPrevented)
    return;
  const t = U();
  ve(e, t).forEach((o) => {
    if (o.separator) {
      const r = o.panels.find(
        (a) => a.panelConstraints.defaultSize !== void 0
      );
      if (r) {
        const a = r.panelConstraints.defaultSize, u = et({
          groupId: o.group.id,
          panelId: r.id
        });
        u && a !== void 0 && (u.resize(a), e.preventDefault());
      }
    }
  });
}
function fe(e) {
  const t = U();
  for (const [n] of t)
    if (n.separators.some(
      (o) => o.element === e
    ))
      return n;
  throw Error("Could not find parent Group for separator element");
}
function tt({
  groupId: e
}) {
  const t = () => {
    const n = U();
    for (const [o, r] of n)
      if (o.id === e)
        return { group: o, ...r };
    throw Error(`Could not find Group with id "${e}"`);
  };
  return {
    getLayout() {
      const { defaultLayoutDeferred: n, layout: o } = t();
      return n ? {} : o;
    },
    setLayout(n) {
      const {
        defaultLayoutDeferred: o,
        derivedPanelConstraints: r,
        group: a,
        groupSize: u,
        layout: i,
        separatorToPanels: s
      } = t(), l = B({
        layout: n,
        panelConstraints: r
      });
      return o ? i : (j(i, l) || F(a, {
        defaultLayoutDeferred: o,
        derivedPanelConstraints: r,
        groupSize: u,
        layout: l,
        separatorToPanels: s
      }), l);
    }
  };
}
function V(e, t) {
  const n = fe(e), o = $(n.id, !0), r = n.separators.find(
    (m) => m.element === e
  );
  L(r, "Matching separator not found");
  const a = o.separatorToPanels.get(r);
  L(a, "Matching panels not found");
  const u = a.map((m) => n.panels.indexOf(m)), s = tt({ groupId: n.id }).getLayout(), l = re({
    delta: t,
    initialLayout: s,
    panelConstraints: o.derivedPanelConstraints,
    pivotIndices: u,
    prevLayout: s,
    trigger: "keyboard"
  }), c = B({
    layout: l,
    panelConstraints: o.derivedPanelConstraints
  });
  j(s, c) || F(n, {
    defaultLayoutDeferred: o.defaultLayoutDeferred,
    derivedPanelConstraints: o.derivedPanelConstraints,
    groupSize: o.groupSize,
    layout: c,
    separatorToPanels: o.separatorToPanels
  });
}
function Te(e) {
  if (e.defaultPrevented)
    return;
  const t = e.currentTarget, n = fe(t);
  if (!n.disabled)
    switch (e.key) {
      case "ArrowDown": {
        e.preventDefault(), n.orientation === "vertical" && V(t, 5);
        break;
      }
      case "ArrowLeft": {
        e.preventDefault(), n.orientation === "horizontal" && V(t, -5);
        break;
      }
      case "ArrowRight": {
        e.preventDefault(), n.orientation === "horizontal" && V(t, 5);
        break;
      }
      case "ArrowUp": {
        e.preventDefault(), n.orientation === "vertical" && V(t, -5);
        break;
      }
      case "End": {
        e.preventDefault(), V(t, 100);
        break;
      }
      case "Enter": {
        e.preventDefault();
        const o = fe(t), r = $(o.id, !0), { derivedPanelConstraints: a, layout: u, separatorToPanels: i } = r, s = o.separators.find(
          (p) => p.element === t
        );
        L(s, "Matching separator not found");
        const l = i.get(s);
        L(l, "Matching panels not found");
        const c = l[0], m = a.find(
          (p) => p.panelId === c.id
        );
        if (L(m, "Panel metadata not found"), m.collapsible) {
          const p = u[c.id], v = m.collapsedSize === p ? o.mutableState.expandedPanelSizes[c.id] ?? m.minSize : m.collapsedSize;
          V(t, v - p);
        }
        break;
      }
      case "F6": {
        e.preventDefault();
        const r = fe(t).separators.map(
          (s) => s.element
        ), a = Array.from(r).findIndex(
          (s) => s === e.currentTarget
        );
        L(a !== null, "Index not found");
        const u = e.shiftKey ? a > 0 ? a - 1 : r.length - 1 : a + 1 < r.length ? a + 1 : 0;
        r[u].focus({
          preventScroll: !0
        });
        break;
      }
      case "Home": {
        e.preventDefault(), V(t, -100);
        break;
      }
    }
}
let J = {
  cursorFlags: 0,
  state: "inactive"
};
const ze = new Je();
function W() {
  return J;
}
function Gt(e) {
  return ze.addListener("change", e);
}
function At(e) {
  const t = J, n = { ...J };
  n.cursorFlags = e, J = n, ze.emit("change", {
    prev: t,
    next: n
  });
}
function Z(e) {
  const t = J;
  J = e, ze.emit("change", {
    prev: t,
    next: e
  });
}
function Oe(e) {
  if (e.defaultPrevented)
    return;
  if (e.pointerType === "mouse" && e.button > 0)
    return;
  const t = U(), n = ve(e, t), o = /* @__PURE__ */ new Map();
  let r = !1;
  n.forEach((a) => {
    a.separator && (r || (r = !0, a.separator.element.focus({
      preventScroll: !0
    })));
    const u = t.get(a.group);
    u && o.set(a.group, u.layout);
  }), Z({
    cursorFlags: 0,
    hitRegions: n,
    initialLayoutMap: o,
    pointerDownAtPoint: { x: e.clientX, y: e.clientY },
    state: "active"
  }), n.length && e.preventDefault();
}
const Nt = (e) => e, he = () => {
}, nt = 1, ot = 2, it = 4, rt = 8, Ge = 3, Ae = 12;
let ce;
function Ne() {
  return ce === void 0 && (ce = !1, typeof window < "u" && (window.navigator.userAgent.includes("Chrome") || window.navigator.userAgent.includes("Firefox")) && (ce = !0)), ce;
}
function Ft({
  cursorFlags: e,
  groups: t,
  state: n
}) {
  let o = 0, r = 0;
  switch (n) {
    case "active":
    case "hover":
      t.forEach((a) => {
        if (!a.mutableState.disableCursor)
          switch (a.orientation) {
            case "horizontal": {
              o++;
              break;
            }
            case "vertical": {
              r++;
              break;
            }
          }
      });
  }
  if (!(o === 0 && r === 0)) {
    switch (n) {
      case "active": {
        if (e && Ne()) {
          const a = (e & nt) !== 0, u = (e & ot) !== 0, i = (e & it) !== 0, s = (e & rt) !== 0;
          if (a)
            return i ? "se-resize" : s ? "ne-resize" : "e-resize";
          if (u)
            return i ? "sw-resize" : s ? "nw-resize" : "w-resize";
          if (i)
            return "s-resize";
          if (s)
            return "n-resize";
        }
        break;
      }
    }
    return Ne() ? o > 0 && r > 0 ? "move" : o > 0 ? "ew-resize" : "ns-resize" : o > 0 && r > 0 ? "grab" : o > 0 ? "col-resize" : "row-resize";
  }
}
const Fe = /* @__PURE__ */ new WeakMap();
function be(e) {
  if (e.defaultView === null || e.defaultView === void 0)
    return;
  let { prevStyle: t, styleSheet: n } = Fe.get(e) ?? {};
  n === void 0 && (n = new e.defaultView.CSSStyleSheet(), e.adoptedStyleSheets && e.adoptedStyleSheets.push(n));
  const o = W();
  switch (o.state) {
    case "active":
    case "hover": {
      const r = Ft({
        cursorFlags: o.cursorFlags,
        groups: o.hitRegions.map((u) => u.group),
        state: o.state
      }), a = `*, *:hover {cursor: ${r} !important; }`;
      if (t === a)
        return;
      t = a, r ? n.cssRules.length === 0 ? n.insertRule(a) : n.replaceSync(a) : n.cssRules.length === 1 && n.deleteRule(0);
      break;
    }
    case "inactive": {
      t = void 0, n.cssRules.length === 1 && n.deleteRule(0);
      break;
    }
  }
  Fe.set(e, {
    prevStyle: t,
    styleSheet: n
  });
}
function at({
  document: e,
  event: t,
  hitRegions: n,
  initialLayoutMap: o,
  mountedGroups: r,
  pointerDownAtPoint: a,
  prevCursorFlags: u
}) {
  let i = 0;
  n.forEach((l) => {
    const { group: c, groupSize: m } = l, { orientation: p, panels: v } = c, { disableCursor: x } = c.mutableState;
    let f = 0;
    a ? p === "horizontal" ? f = (t.clientX - a.x) / m * 100 : f = (t.clientY - a.y) / m * 100 : p === "horizontal" ? f = t.clientX < 0 ? -100 : 100 : f = t.clientY < 0 ? -100 : 100;
    const h = o.get(c), g = r.get(c);
    if (!h || !g)
      return;
    const {
      defaultLayoutDeferred: y,
      derivedPanelConstraints: z,
      groupSize: S,
      layout: d,
      separatorToPanels: P
    } = g;
    if (z && d && P) {
      const R = re({
        delta: f,
        initialLayout: h,
        panelConstraints: z,
        pivotIndices: l.panels.map((C) => v.indexOf(C)),
        prevLayout: d,
        trigger: "mouse-or-touch"
      });
      if (j(R, d)) {
        if (f !== 0 && !x)
          switch (p) {
            case "horizontal": {
              i |= f < 0 ? nt : ot;
              break;
            }
            case "vertical": {
              i |= f < 0 ? it : rt;
              break;
            }
          }
      } else
        F(l.group, {
          defaultLayoutDeferred: y,
          derivedPanelConstraints: z,
          groupSize: S,
          layout: R,
          separatorToPanels: P
        });
    }
  });
  let s = 0;
  t.movementX === 0 ? s |= u & Ge : s |= i & Ge, t.movementY === 0 ? s |= u & Ae : s |= i & Ae, At(s), be(e);
}
function _e(e) {
  const t = U(), n = W();
  switch (n.state) {
    case "active":
      at({
        document: e.currentTarget,
        event: e,
        hitRegions: n.hitRegions,
        initialLayoutMap: n.initialLayoutMap,
        mountedGroups: t,
        prevCursorFlags: n.cursorFlags
      });
  }
}
function $e(e) {
  if (e.defaultPrevented)
    return;
  const t = W(), n = U();
  switch (t.state) {
    case "active": {
      if (
        // Skip this check for "pointerleave" events, else Firefox triggers a false positive (see #514)
        e.buttons === 0
      ) {
        Z({
          cursorFlags: 0,
          state: "inactive"
        }), t.hitRegions.forEach((o) => {
          const r = $(o.group.id, !0);
          F(o.group, r);
        });
        return;
      }
      at({
        document: e.currentTarget,
        event: e,
        hitRegions: t.hitRegions,
        initialLayoutMap: t.initialLayoutMap,
        mountedGroups: n,
        pointerDownAtPoint: t.pointerDownAtPoint,
        prevCursorFlags: t.cursorFlags
      });
      break;
    }
    default: {
      const o = ve(e, n);
      o.length === 0 ? t.state !== "inactive" && Z({
        cursorFlags: 0,
        state: "inactive"
      }) : Z({
        cursorFlags: 0,
        hitRegions: o,
        state: "hover"
      }), be(e.currentTarget);
      break;
    }
  }
}
function He(e) {
  if (e.relatedTarget instanceof HTMLIFrameElement)
    switch (W().state) {
      case "hover":
        Z({
          cursorFlags: 0,
          state: "inactive"
        });
    }
}
function Ve(e) {
  if (e.defaultPrevented)
    return;
  if (e.pointerType === "mouse" && e.button > 0)
    return;
  const t = W();
  switch (t.state) {
    case "active":
      Z({
        cursorFlags: 0,
        state: "inactive"
      }), t.hitRegions.length > 0 && (be(e.currentTarget), t.hitRegions.forEach((n) => {
        const o = $(n.group.id, !0);
        F(n.group, o);
      }), e.preventDefault());
  }
}
function je(e) {
  let t = 0, n = 0;
  const o = {};
  for (const a of e)
    if (a.defaultSize !== void 0) {
      t++;
      const u = D(a.defaultSize);
      n += u, o[a.panelId] = u;
    } else
      o[a.panelId] = void 0;
  const r = e.length - t;
  if (r !== 0) {
    const a = D((100 - n) / r);
    for (const u of e)
      u.defaultSize === void 0 && (o[u.panelId] = a);
  }
  return o;
}
function _t(e, t, n) {
  if (!n[0])
    return;
  const r = e.panels.find((l) => l.element === t);
  if (!r || !r.onResize)
    return;
  const a = Q({ group: e }), u = e.orientation === "horizontal" ? r.element.offsetWidth : r.element.offsetHeight, i = r.mutableValues.prevSize, s = {
    asPercentage: D(u / a * 100),
    inPixels: u
  };
  r.mutableValues.prevSize = s, r.onResize(s, r.id, i);
}
function $t(e, t) {
  if (Object.keys(e).length !== Object.keys(t).length)
    return !1;
  for (const o in e)
    if (e[o] !== t[o])
      return !1;
  return !0;
}
function Ht({
  group: e,
  nextGroupSize: t,
  prevGroupSize: n,
  prevLayout: o
}) {
  if (n <= 0 || t <= 0 || n === t)
    return o;
  let r = 0, a = 0, u = !1;
  const i = /* @__PURE__ */ new Map(), s = [];
  for (const m of e.panels) {
    const p = o[m.id] ?? 0;
    switch (m.panelConstraints.groupResizeBehavior) {
      case "preserve-pixel-size": {
        u = !0;
        const v = p / 100 * n, x = D(
          v / t * 100
        );
        i.set(m.id, x), r += x;
        break;
      }
      case "preserve-relative-size":
      default: {
        s.push(m.id), a += p;
        break;
      }
    }
  }
  if (!u || s.length === 0)
    return o;
  const l = 100 - r, c = { ...o };
  if (i.forEach((m, p) => {
    c[p] = m;
  }), a > 0)
    for (const m of s) {
      const p = o[m] ?? 0;
      c[m] = D(
        p / a * l
      );
    }
  else {
    const m = D(
      l / s.length
    );
    for (const p of s)
      c[p] = m;
  }
  return c;
}
function Vt(e, t) {
  const n = e.map((r) => r.id), o = Object.keys(t);
  if (n.length !== o.length)
    return !1;
  for (const r of n)
    if (!o.includes(r))
      return !1;
  return !0;
}
const q = /* @__PURE__ */ new Map();
function jt(e) {
  let t = !0;
  L(
    e.element.ownerDocument.defaultView,
    "Cannot register an unmounted Group"
  );
  const n = e.element.ownerDocument.defaultView.ResizeObserver, o = /* @__PURE__ */ new Set(), r = /* @__PURE__ */ new Set(), a = new n((f) => {
    for (const h of f) {
      const { borderBoxSize: g, target: y } = h;
      if (y === e.element) {
        if (t) {
          const z = Q({ group: e });
          if (z === 0)
            return;
          const S = $(e.id);
          if (!S)
            return;
          const d = ge(e), P = S.defaultLayoutDeferred ? je(d) : S.layout, R = Ht({
            group: e,
            nextGroupSize: z,
            prevGroupSize: S.groupSize,
            prevLayout: P
          }), C = B({
            layout: R,
            panelConstraints: d
          });
          if (!S.defaultLayoutDeferred && j(S.layout, C) && $t(
            S.derivedPanelConstraints,
            d
          ) && S.groupSize === z)
            return;
          F(e, {
            defaultLayoutDeferred: !1,
            derivedPanelConstraints: d,
            groupSize: z,
            layout: C,
            separatorToPanels: S.separatorToPanels
          });
        }
      } else
        _t(e, y, g);
    }
  });
  a.observe(e.element), e.panels.forEach((f) => {
    L(
      !o.has(f.id),
      `Panel ids must be unique; id "${f.id}" was used more than once`
    ), o.add(f.id), f.onResize && a.observe(f.element);
  });
  const u = Q({ group: e }), i = ge(e), s = e.panels.map(({ id: f }) => f).join(",");
  let l = e.mutableState.defaultLayout;
  l && (Vt(e.panels, l) || (l = void 0));
  const c = e.mutableState.layouts[s] ?? l ?? je(i), m = B({
    layout: c,
    panelConstraints: i
  }), p = e.element.ownerDocument;
  q.set(
    p,
    (q.get(p) ?? 0) + 1
  );
  const v = /* @__PURE__ */ new Map();
  return Ye(e).forEach((f) => {
    f.separator && v.set(f.separator, f.panels);
  }), F(e, {
    defaultLayoutDeferred: u === 0,
    derivedPanelConstraints: i,
    groupSize: u,
    layout: m,
    separatorToPanels: v
  }), e.separators.forEach((f) => {
    L(
      !r.has(f.id),
      `Separator ids must be unique; id "${f.id}" was used more than once`
    ), r.add(f.id), f.element.addEventListener("keydown", Te);
  }), q.get(p) === 1 && (p.addEventListener("dblclick", De, !0), p.addEventListener("pointerdown", Oe, !0), p.addEventListener("pointerleave", _e), p.addEventListener("pointermove", $e), p.addEventListener("pointerout", He), p.addEventListener("pointerup", Ve, !0)), function() {
    t = !1, q.set(
      p,
      Math.max(0, (q.get(p) ?? 0) - 1)
    ), Lt(e), e.separators.forEach((h) => {
      h.element.removeEventListener("keydown", Te);
    }), q.get(p) || (p.removeEventListener(
      "dblclick",
      De,
      !0
    ), p.removeEventListener(
      "pointerdown",
      Oe,
      !0
    ), p.removeEventListener("pointerleave", _e), p.removeEventListener("pointermove", $e), p.removeEventListener("pointerout", He), p.removeEventListener("pointerup", Ve, !0)), a.disconnect();
  };
}
function Bt() {
  const [e, t] = ie({}), n = te(() => t({}), []);
  return [e, n];
}
function xe(e) {
  const t = pt();
  return `${e ?? t}`;
}
const K = typeof window < "u" ? Be : de;
function ne(e) {
  const t = T(e);
  return K(() => {
    t.current = e;
  }, [e]), te(
    (...n) => t.current?.(...n),
    [t]
  );
}
function we(...e) {
  return ne((t) => {
    e.forEach((n) => {
      if (n)
        switch (typeof n) {
          case "function": {
            n(t);
            break;
          }
          case "object": {
            n.current = t;
            break;
          }
        }
    });
  });
}
function Pe(e) {
  const t = T({ ...e });
  return K(() => {
    for (const n in e)
      t.current[n] = e[n];
  }, [e]), t.current;
}
const st = ht(null);
function Wt(e, t) {
  const n = T({
    getLayout: () => ({}),
    setLayout: Nt
  });
  We(t, () => n.current, []), K(() => {
    Object.assign(
      n.current,
      tt({ groupId: e })
    );
  });
}
function Ut({
  children: e,
  className: t,
  defaultLayout: n,
  disableCursor: o,
  disabled: r,
  elementRef: a,
  groupRef: u,
  id: i,
  onLayoutChange: s,
  onLayoutChanged: l,
  orientation: c = "horizontal",
  resizeTargetMinimumSize: m = {
    coarse: 20,
    fine: 10
  },
  style: p,
  ...v
}) {
  const x = T({
    onLayoutChange: {},
    onLayoutChanged: {}
  }), f = ne((b) => {
    j(x.current.onLayoutChange, b) || (x.current.onLayoutChange = b, s?.(b));
  }), h = ne((b) => {
    j(x.current.onLayoutChanged, b) || (x.current.onLayoutChanged = b, l?.(b));
  }), g = xe(i), y = T(null), [z, S] = Bt(), d = T({
    lastExpandedPanelSizes: {},
    layouts: {},
    panels: [],
    resizeTargetMinimumSize: m,
    separators: []
  }), P = we(y, a);
  Wt(g, u);
  const R = ne(
    (b, w) => {
      const E = W(), M = Me(b), k = $(b);
      if (k) {
        let O = !1;
        switch (E.state) {
          case "active": {
            O = E.hitRegions.some(
              (H) => H.group === M
            );
            break;
          }
        }
        return {
          flexGrow: k.layout[w] ?? 1,
          pointerEvents: O ? "none" : void 0
        };
      }
      return {
        flexGrow: n?.[w] ?? 1
      };
    }
  ), C = Pe({
    defaultLayout: n,
    disableCursor: o
  }), X = Ue(
    () => ({
      get disableCursor() {
        return !!C.disableCursor;
      },
      getPanelStyles: R,
      id: g,
      orientation: c,
      registerPanel: (b) => {
        const w = d.current;
        return w.panels = Se(c, [
          ...w.panels,
          b
        ]), S(), () => {
          w.panels = w.panels.filter(
            (E) => E !== b
          ), S();
        };
      },
      registerSeparator: (b) => {
        const w = d.current;
        return w.separators = Se(c, [
          ...w.separators,
          b
        ]), S(), () => {
          w.separators = w.separators.filter(
            (E) => E !== b
          ), S();
        };
      },
      togglePanelDisabled: (b, w) => {
        const M = d.current.panels.find(
          (H) => H.id === b
        );
        M && (M.panelConstraints.disabled = w);
        const k = Me(g), O = $(g);
        k && O && F(k, {
          ...O,
          derivedPanelConstraints: ge(k)
        });
      },
      toggleSeparatorDisabled: (b, w) => {
        const M = d.current.separators.find(
          (k) => k.id === b
        );
        M && (M.disabled = w);
      }
    }),
    [R, g, S, c, C]
  ), _ = T(null);
  return K(() => {
    const b = y.current;
    if (b === null)
      return;
    const w = d.current;
    let E;
    if (C.defaultLayout !== void 0 && Object.keys(C.defaultLayout).length === w.panels.length) {
      E = {};
      for (const ee of w.panels) {
        const ae = C.defaultLayout[ee.id];
        ae !== void 0 && (E[ee.id] = ae);
      }
    }
    const M = {
      disabled: !!r,
      element: b,
      id: g,
      mutableState: {
        defaultLayout: E,
        disableCursor: !!C.disableCursor,
        expandedPanelSizes: d.current.lastExpandedPanelSizes,
        layouts: d.current.layouts
      },
      orientation: c,
      panels: w.panels,
      resizeTargetMinimumSize: w.resizeTargetMinimumSize,
      separators: w.separators
    };
    _.current = M;
    const k = jt(M), { defaultLayoutDeferred: O, derivedPanelConstraints: H, layout: Ce } = $(M.id, !0);
    !O && H.length > 0 && (f(Ce), h(Ce));
    const lt = ye(g, (ee) => {
      const { defaultLayoutDeferred: ae, derivedPanelConstraints: Re, layout: se } = ee.next;
      if (ae || Re.length === 0)
        return;
      const ut = M.panels.map(({ id: N }) => N).join(",");
      M.mutableState.layouts[ut] = se, Re.forEach((N) => {
        if (N.collapsible) {
          const { layout: pe } = ee.prev ?? {};
          if (pe) {
            const ft = I(
              N.collapsedSize,
              se[N.panelId]
            ), dt = I(
              N.collapsedSize,
              pe[N.panelId]
            );
            ft && !dt && (M.mutableState.expandedPanelSizes[N.panelId] = pe[N.panelId]);
          }
        }
      });
      const ct = W().state !== "active";
      f(se), ct && h(se);
    });
    return () => {
      _.current = null, k(), lt();
    };
  }, [
    r,
    g,
    h,
    f,
    c,
    z,
    C
  ]), de(() => {
    const b = _.current;
    b && (b.mutableState.defaultLayout = n, b.mutableState.disableCursor = !!o);
  }), /* @__PURE__ */ oe(st.Provider, { value: X, children: /* @__PURE__ */ oe(
    "div",
    {
      ...v,
      className: t,
      "data-group": !0,
      "data-testid": g,
      id: g,
      ref: P,
      style: {
        height: "100%",
        width: "100%",
        overflow: "hidden",
        ...p,
        display: "flex",
        flexDirection: c === "horizontal" ? "row" : "column",
        flexWrap: "nowrap",
        // Inform the browser that the library is handling touch events for this element
        // but still allow users to scroll content within panels in the non-resizing direction
        // NOTE This is not an inherited style
        // See github.com/bvaughn/react-resizable-panels/issues/662
        touchAction: c === "horizontal" ? "pan-y" : "pan-x"
      },
      children: e
    }
  ) });
}
Ut.displayName = "Group";
function me(e, t) {
  return `react-resizable-panels:${[e, ...t].join(":")}`;
}
function nn({
  debounceSaveMs: e = 100,
  panelIds: t,
  storage: n = localStorage,
  ...o
}) {
  const r = t !== void 0, a = "id" in o ? o.id : o.groupId, u = me(a, t ?? []), i = Ke(
    Kt,
    () => n.getItem(u),
    () => n.getItem(u)
  ), s = Ue(
    () => i ? JSON.parse(i) : void 0,
    [i]
  ), l = T(null), c = te(() => {
    const v = l.current;
    v && (l.current = null, clearTimeout(v));
  }, []);
  Be(() => () => {
    c();
  }, [c]);
  const m = te(
    (v) => {
      c();
      let x;
      r ? x = me(a, Object.keys(v)) : x = me(a, []);
      try {
        n.setItem(x, JSON.stringify(v));
      } catch (f) {
        console.error(f);
      }
    },
    [c, r, a, n]
  ), p = te(
    (v) => {
      c(), e === 0 ? m(v) : l.current = setTimeout(() => {
        m(v);
      }, e);
    },
    [c, e, m]
  );
  return {
    /**
     * Pass this value to `Group` as the `defaultLayout` prop.
     */
    defaultLayout: s,
    /**
     * Attach this callback on the `Group` as the `onLayoutChange` prop.
     *
     * @deprecated Use the {@link onLayoutChanged} prop instead.
     */
    onLayoutChange: p,
    /**
     * Attach this callback on the `Group` as the `onLayoutChanged` prop.
     */
    onLayoutChanged: m
  };
}
function Kt() {
  return function() {
  };
}
function on() {
  return ie(null);
}
function rn() {
  return T(null);
}
function Le() {
  const e = mt(st);
  return L(
    e,
    "Group Context not found; did you render a Panel or Separator outside of a Group?"
  ), e;
}
function Xt(e, t) {
  const { id: n } = Le(), o = T({
    collapse: he,
    expand: he,
    getSize: () => ({
      asPercentage: 0,
      inPixels: 0
    }),
    isCollapsed: () => !1,
    resize: he
  });
  We(t, () => o.current, []), K(() => {
    Object.assign(
      o.current,
      et({ groupId: n, panelId: e })
    );
  });
}
function qt({
  children: e,
  className: t,
  collapsedSize: n = "0%",
  collapsible: o = !1,
  defaultSize: r,
  disabled: a,
  elementRef: u,
  groupResizeBehavior: i = "preserve-relative-size",
  id: s,
  maxSize: l = "100%",
  minSize: c = "0%",
  onResize: m,
  panelRef: p,
  style: v,
  ...x
}) {
  const f = !!s, h = xe(s), g = Pe({
    disabled: a
  }), y = T(null), z = we(y, u), {
    getPanelStyles: S,
    id: d,
    orientation: P,
    registerPanel: R,
    togglePanelDisabled: C
  } = Le(), X = m !== null, _ = ne(
    (w, E, M) => {
      m?.(w, s, M);
    }
  );
  K(() => {
    const w = y.current;
    if (w !== null) {
      const E = {
        element: w,
        id: h,
        idIsStable: f,
        mutableValues: {
          expandToSize: void 0,
          prevSize: void 0
        },
        onResize: X ? _ : void 0,
        panelConstraints: {
          groupResizeBehavior: i,
          collapsedSize: n,
          collapsible: o,
          defaultSize: r,
          disabled: g.disabled,
          maxSize: l,
          minSize: c
        }
      };
      return R(E);
    }
  }, [
    i,
    n,
    o,
    r,
    X,
    h,
    f,
    l,
    c,
    _,
    R,
    g
  ]), de(() => {
    C(h, !!a);
  }, [a, h, C]), Xt(h, p);
  const b = Ke(
    (w) => ye(d, w),
    // useSyncExternalStore does not support a custom equality check
    // stringify avoids re-rendering when the style value hasn't changed
    () => JSON.stringify(S(d, h)),
    () => JSON.stringify(S(d, h))
  );
  return /* @__PURE__ */ oe(
    "div",
    {
      ...x,
      "aria-disabled": a || void 0,
      "data-panel": !0,
      "data-testid": h,
      id: h,
      ref: z,
      style: {
        ...Yt,
        display: "flex",
        flexBasis: 0,
        flexShrink: 1,
        overflow: "visible",
        ...JSON.parse(b)
      },
      children: /* @__PURE__ */ oe(
        "div",
        {
          className: t,
          style: {
            maxHeight: "100%",
            maxWidth: "100%",
            flexGrow: 1,
            overflow: "auto",
            ...v,
            // Inform the browser that the library is handling touch events for this element
            // but still allow users to scroll content within panels in the non-resizing direction
            // NOTE This is not an inherited style
            // See github.com/bvaughn/react-resizable-panels/issues/662
            touchAction: P === "horizontal" ? "pan-y" : "pan-x"
          },
          children: e
        }
      )
    }
  );
}
qt.displayName = "Panel";
const Yt = {
  minHeight: 0,
  maxHeight: "100%",
  height: "auto",
  minWidth: 0,
  maxWidth: "100%",
  width: "auto",
  border: "none",
  borderWidth: 0,
  padding: 0,
  margin: 0
};
function an() {
  return ie(null);
}
function sn() {
  return T(null);
}
function Jt({
  layout: e,
  panelConstraints: t,
  panelId: n,
  panelIndex: o
}) {
  let r, a;
  const u = e[n], i = t.find(
    (s) => s.panelId === n
  );
  if (i) {
    const s = i.maxSize, l = i.collapsible ? i.collapsedSize : i.minSize, c = [o, o + 1];
    a = B({
      layout: re({
        delta: l - u,
        initialLayout: e,
        panelConstraints: t,
        pivotIndices: c,
        prevLayout: e
      }),
      panelConstraints: t
    })[n], r = B({
      layout: re({
        delta: s - u,
        initialLayout: e,
        panelConstraints: t,
        pivotIndices: c,
        prevLayout: e
      }),
      panelConstraints: t
    })[n];
  }
  return {
    valueControls: n,
    valueMax: r,
    valueMin: a,
    valueNow: u
  };
}
function Zt({
  children: e,
  className: t,
  disabled: n,
  elementRef: o,
  id: r,
  style: a,
  ...u
}) {
  const i = xe(r), s = Pe({
    disabled: n
  }), [l, c] = ie({}), [m, p] = ie("inactive"), v = T(null), x = we(v, o), {
    disableCursor: f,
    id: h,
    orientation: g,
    registerSeparator: y,
    toggleSeparatorDisabled: z
  } = Le(), S = g === "horizontal" ? "vertical" : "horizontal";
  K(() => {
    const P = v.current;
    if (P !== null) {
      const R = {
        disabled: s.disabled,
        element: P,
        id: i
      }, C = y(R), X = Gt(
        (b) => {
          p(
            b.next.state !== "inactive" && b.next.hitRegions.some(
              (w) => w.separator === R
            ) ? b.next.state : "inactive"
          );
        }
      ), _ = ye(
        h,
        (b) => {
          const { derivedPanelConstraints: w, layout: E, separatorToPanels: M } = b.next, k = M.get(R);
          if (k) {
            const O = k[0], H = k.indexOf(O);
            c(
              Jt({
                layout: E,
                panelConstraints: w,
                panelId: O.id,
                panelIndex: H
              })
            );
          }
        }
      );
      return () => {
        X(), _(), C();
      };
    }
  }, [h, i, y, s]), de(() => {
    z(i, !!n);
  }, [n, i, z]);
  let d;
  return n && !f && (d = "not-allowed"), /* @__PURE__ */ oe(
    "div",
    {
      ...u,
      "aria-controls": l.valueControls,
      "aria-disabled": n || void 0,
      "aria-orientation": S,
      "aria-valuemax": l.valueMax,
      "aria-valuemin": l.valueMin,
      "aria-valuenow": l.valueNow,
      children: e,
      className: t,
      "data-separator": n ? "disabled" : m,
      "data-testid": i,
      id: i,
      ref: x,
      role: "separator",
      style: {
        flexBasis: "auto",
        cursor: d,
        ...a,
        flexGrow: 0,
        flexShrink: 0,
        // Inform the browser that the library is handling touch events for this element
        // See github.com/bvaughn/react-resizable-panels/issues/662
        touchAction: "none"
      },
      tabIndex: n ? void 0 : 0
    }
  );
}
Zt.displayName = "Separator";
export {
  Ut as Group,
  qt as Panel,
  Zt as Separator,
  Pt as isCoarsePointer,
  nn as useDefaultLayout,
  on as useGroupCallbackRef,
  rn as useGroupRef,
  an as usePanelCallbackRef,
  sn as usePanelRef
};
//# sourceMappingURL=react-resizable-panels.js.map
