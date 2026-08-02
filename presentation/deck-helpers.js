// deck-helpers.js — design system & helpers for the DDD + OTel workshop deck
// Teal accent, neutral/professional theme — no vendor branding.
//   - 13.333 x 7.5 LAYOUT_WIDE (16:9)
//   - Eyebrow 12pt bold teal #0891B2, charSpacing 4
//   - Title 30pt Calibri Bold #1E293B (slate-900)
//   - Content 17pt Calibri #334155 (slate-700)
//   - Code box fill #0F172A (slate-900), code 11pt Consolas, fg #E2E8F0
//   - Section dividers: bg #0E7490 (darker teal) with white text
//   - Caption 13pt italic #64748B (slate-500); page number 10pt #94A3B8

"use strict";

const PptxGenJS = require("pptxgenjs");

const COLOR = {
  teal:       "0891B2",
  tealDark:   "0E7490",
  tealDeep:   "155E75",
  tealLight:  "F0FDFA",   // teal-50 — light content background
  ink:        "1E293B",   // slate-900
  body:       "334155",   // slate-700
  caption:    "64748B",   // slate-500
  pageNum:    "94A3B8",   // slate-400
  grid:       "CBD5E1",   // slate-300
  panel:      "F1F5F9",   // slate-100
  codeBg:     "0F172A",   // slate-900
  codeFg:     "E2E8F0",   // slate-200
  codeComment:"86EFAC",   // green-300
  codeKey:    "FDE68A",   // amber-200
  codeStr:    "BAE6FD",   // sky-200
  white:      "FFFFFF",
  rule:       "0891B2",
  // accent palette for diagram elements
  order:      "0891B2",   // teal (primary)
  inventory:  "7C3AED",   // violet
  payment:    "059669",   // emerald
  shipping:   "D97706",   // amber
  notify:     "DC2626",   // red
};

const FONT = {
  title:   "Calibri",
  body:    "Calibri",
  mono:    "Consolas",
};

// Slide dimensions in inches (LAYOUT_WIDE)
const W = 13.333;
const H = 7.5;

// ----- helpers -----
function newDeck() {
  const pres = new PptxGenJS();
  pres.layout = "LAYOUT_WIDE";
  return pres;
}

// Footer: page number bottom-left, thin teal rule across bottom.
function addFooter(slide, pageNum) {
  // thin accent rule
  slide.addShape("line", {
    x: 0.62, y: 6.90, w: 12.09, h: 0,
    line: { color: COLOR.teal, width: 0.75 },
  });
  // page number
  slide.addText(String(pageNum), {
    x: 0.62, y: 6.96, w: 1.0, h: 0.30,
    fontFace: FONT.body, fontSize: 10, color: COLOR.pageNum,
    align: "left", valign: "middle",
  });
}

function addContentTitle(slide, eyebrow, title, opts = {}) {
  slide.addText(eyebrow, {
    x: 0.62, y: 0.42, w: opts.eyebrowW ?? 12.09, h: 0.32,
    fontFace: FONT.title, fontSize: 12, bold: true, color: COLOR.teal,
    charSpacing: 4,
    align: "left", valign: "middle",
  });
  slide.addText(title, {
    x: 0.62, y: 0.74, w: opts.w ?? 12.09, h: opts.h ?? 1.10,
    fontFace: FONT.title, fontSize: opts.fontSize ?? 30, bold: true, color: COLOR.ink,
    align: "left", valign: "top",
  });
}

function addBullets(slide, lines, opts = {}) {
  const x = opts.x ?? 0.62;
  const y = opts.y ?? 1.85;
  const w = opts.w ?? 12.09;
  const h = opts.h ?? 4.85;
  const fontSize = opts.fontSize ?? 17;
  const items = lines.map((ln) => {
    if (typeof ln === "string") {
      return { text: ln, options: { bullet: { code: "25CF" }, paraSpaceAfter: 6, breakLine: true } };
    }
    return {
      text: ln.text,
      options: {
        bullet: ln.sub ? { indent: 8, code: "25E6" } : { code: "25CF" },
        paraSpaceAfter: 4,
        breakLine: true,
        indentLevel: ln.sub ? 1 : 0,
        ...(ln.options || {}),
      },
    };
  });
  slide.addText(items, {
    x, y, w, h,
    fontFace: FONT.body, fontSize, color: COLOR.body,
    align: "left", valign: "top",
    paraSpaceAfter: 6,
    lineSpacingMultiple: 1.15,
  });
}

// Two-column bullets
function addTwoColBullets(slide, left, right, opts = {}) {
  const y = opts.y ?? 1.85;
  const h = opts.h ?? 4.85;
  const fontSize = opts.fontSize ?? 17;

  function mk(items) {
    return items.map((ln) => {
      if (typeof ln === "string") {
        return { text: ln, options: { bullet: { code: "25CF" }, paraSpaceAfter: 8, breakLine: true } };
      }
      return {
        text: ln.text,
        options: {
          bullet: { code: "25CF" },
          paraSpaceAfter: 8,
          breakLine: true,
          ...(ln.options || {}),
        },
      };
    });
  }

  slide.addText(mk(left), {
    x: 0.62, y, w: 6.00, h,
    fontFace: FONT.body, fontSize, color: COLOR.body,
    align: "left", valign: "top",
    paraSpaceAfter: 8, lineSpacingMultiple: 1.20,
  });
  slide.addText(mk(right), {
    x: 7.02, y, w: 6.00, h,
    fontFace: FONT.body, fontSize, color: COLOR.body,
    align: "left", valign: "top",
    paraSpaceAfter: 8, lineSpacingMultiple: 1.20,
  });
}

// Three-column layout
function addThreeColBullets(slide, col1, col2, col3, opts = {}) {
  const y = opts.y ?? 1.85;
  const h = opts.h ?? 4.85;
  const fontSize = opts.fontSize ?? 16;
  const colW = 3.90;
  const gap = 0.20;

  function mk(items) {
    return items.map((ln) => {
      if (typeof ln === "string") {
        return { text: ln, options: { bullet: { code: "25CF" }, paraSpaceAfter: 6, breakLine: true } };
      }
      return {
        text: ln.text,
        options: {
          bullet: { code: "25CF" },
          paraSpaceAfter: 6,
          breakLine: true,
          ...(ln.options || {}),
        },
      };
    });
  }

  const x1 = 0.62;
  const x2 = x1 + colW + gap;
  const x3 = x2 + colW + gap;

  slide.addText(mk(col1), {
    x: x1, y, w: colW, h,
    fontFace: FONT.body, fontSize, color: COLOR.body,
    align: "left", valign: "top",
    paraSpaceAfter: 6, lineSpacingMultiple: 1.15,
  });
  slide.addText(mk(col2), {
    x: x2, y, w: colW, h,
    fontFace: FONT.body, fontSize, color: COLOR.body,
    align: "left", valign: "top",
    paraSpaceAfter: 6, lineSpacingMultiple: 1.15,
  });
  slide.addText(mk(col3), {
    x: x3, y, w: colW, h,
    fontFace: FONT.body, fontSize, color: COLOR.body,
    align: "left", valign: "top",
    paraSpaceAfter: 6, lineSpacingMultiple: 1.15,
  });
}

// Table: multi-column data layout
function addTable(slide, rows, opts = {}) {
  const y = opts.y ?? 1.85;
  const h = opts.h ?? 4.85;

  const tableRows = rows.map((r) => {
    return r.map((cell) => ({
      text: cell.text,
      options: {
        bold: cell.bold ?? false,
        color: cell.color || COLOR.body,
        fontFace: cell.mono ? FONT.mono : FONT.body,
        fontSize: cell.fontSize ?? 14,
        align: cell.align || "left",
        valign: "middle",
        ...(cell.options || {}),
      },
    }));
  });

  slide.addTable(tableRows, {
    x: 0.62, y, w: 12.09, h,
    fontFace: FONT.body,
    color: COLOR.body,
    border: { type: "solid", color: COLOR.grid, pt: 0.5 },
    valign: "middle",
    colW: opts.colW ?? undefined,
    rowH: opts.rowH ?? 0.50,
  });
}

function addCaption(slide, text, y) {
  slide.addText(text, {
    x: 0.62, y: y ?? 6.50, w: 12.09, h: 0.34,
    fontFace: FONT.body, fontSize: 13, italic: true, color: COLOR.caption,
    align: "center", valign: "middle",
  });
}

// Section divider with teal background
function addSectionDivider(slide, code, title, subtitle) {
  slide.background = { color: COLOR.tealDark };
  slide.addText(code, {
    x: 1.00, y: 2.32, w: 11.33, h: 0.50,
    fontFace: FONT.title, fontSize: 22, bold: true, color: COLOR.white,
    charSpacing: 6,
    align: "left", valign: "middle",
  });
  slide.addText(title, {
    x: 1.00, y: 2.84, w: 11.33, h: 1.60,
    fontFace: FONT.title, fontSize: 44, bold: true, color: COLOR.white,
    align: "left", valign: "top",
  });
  if (subtitle) {
    slide.addText(subtitle, {
      x: 1.00, y: 4.55, w: 11.00, h: 0.80,
      fontFace: FONT.body, fontSize: 16, italic: true, color: "CCFBF1",
      align: "left", valign: "top",
    });
  }
}

function addNotes(slide, text) {
  slide.addNotes(text);
}

module.exports = {
  PptxGenJS, COLOR, FONT, W, H,
  newDeck,
  addFooter, addContentTitle, addBullets, addTwoColBullets, addThreeColBullets,
  addTable, addCaption, addSectionDivider, addNotes,
};
