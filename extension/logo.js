/* YTM Starcast extension: the logo, drawn in the album's colours (the toolbar button and its popup).
 *
 * The same drawing as the logo Refine puts in YouTube Music's top bar: a deep tile, two arcs and Nova's four-point star,
 * in a 512-unit square. Its colours come from the hue Refine reads from the cover playing; at the logo's own blue they
 * are its usual colours, which it keeps when Refine's album colours are switched off ("brand").
 */
(function (scope) {
  "use strict";
  const hsl = (h, s, l) => `hsl(${Math.round(h)} ${Math.round(s)}% ${Math.round(l)}%)`;
  const BRAND = { a: "#2A3A9A", b: "#111A52", c: "#070B2A", arc: "#8CC8FF", star: "#F2F6FF" };

  /** The logo's colours for a palette as Refine keeps it ({h, s, brand}); the usual ones without one. */
  function colors(p) {
    if (!p || p.brand || !isFinite(p.h) || !isFinite(p.s)) return BRAND;
    const h = Number(p.h), c = Number(p.s) < 0.12 ? 0.3 : 1;
    return { a: hsl(h, 57 * c, 39), b: hsl(h, 66 * c, 19), c: hsl(h, 71 * c, 10), arc: hsl(h - 22, 100 * c, 77), star: hsl(h - 9, 100 * c, 97) };
  }

  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  /** Draws the logo [size] pixels wide on a 2D canvas context, in colours [col] (from colors()). */
  function draw(ctx, size, col) {
    ctx.save();
    ctx.clearRect(0, 0, size, size);
    ctx.scale(size / 512, size / 512);
    const g = ctx.createLinearGradient(16, 16, 16 + 0.35 * 480, 496);
    g.addColorStop(0, col.a); g.addColorStop(0.55, col.b); g.addColorStop(1, col.c);
    roundRect(ctx, 16, 16, 480, 480, 112);
    ctx.fillStyle = g;
    ctx.fill();
    ctx.lineCap = "round";
    ctx.strokeStyle = col.arc;
    for (const [rad, w, o] of [[132, 38, 1], [200, 36, 0.7]]) {
      ctx.globalAlpha = o;
      ctx.lineWidth = w;
      ctx.beginPath();
      ctx.arc(176, 336, rad, -Math.PI / 2, 0);
      ctx.stroke();
    }
    ctx.globalAlpha = 1;
    // the four-point star
    const cx = 176, cy = 336, r = 118, a = 0.07 * r, b = 0.32 * r, c = 0.29 * r, d = 0.09 * r;
    ctx.beginPath();
    ctx.moveTo(cx, cy - r);
    ctx.bezierCurveTo(cx + a, cy - b, cx + c, cy - d, cx + r, cy);
    ctx.bezierCurveTo(cx + c, cy + d, cx + a, cy + b, cx, cy + r);
    ctx.bezierCurveTo(cx - a, cy + b, cx - c, cy + d, cx - r, cy);
    ctx.bezierCurveTo(cx - c, cy - d, cx - a, cy - b, cx, cy - r);
    ctx.closePath();
    ctx.fillStyle = col.star;
    ctx.fill();
    ctx.restore();
  }

  scope.starcastLogo = { colors, draw };
})(typeof globalThis !== "undefined" ? globalThis : self);
