#!/usr/bin/env node
/**
 * Convention checks for Angular templates.
 *
 *   A. Every interactive element carries `data-testid`            (FE-ADR-009 §1)
 *   B. No user-visible text is written directly in a template     (FE-ADR-012 §b)
 *
 * WHY A SCRIPT AND NOT AN ESLINT RULE
 * FE-ADR-009 §Consequences rejected an ESLint rule, and that reasoning still
 * holds: a general rule cannot tell an interactive element from decorative
 * markup, so it fires on read-only nodes and the predictable outcome is a spread
 * of `eslint-disable` comments, after which the rule enforces nothing.
 *
 * This check answers that in two ways:
 *   1. It is deliberately NARROW. It only looks at markup whose interactivity is
 *      unambiguous — <button>, <input>, <select>, <textarea>, an <a> that
 *      actually navigates, or any element carrying a (click) handler. Decorative
 *      markup is never examined, so there is nothing to false-positive on.
 *   2. It has NO inline suppression. There is no comment that silences it,
 *      because per FE-ADR-009 §1 "an element without a data-testid is an
 *      incomplete element" — the fix is always to add the attribute, never to
 *      exempt the element.
 *
 * It is a convention check, not a compiler: it reads templates textually. That
 * is enough for the two rules above and keeps it dependency-free.
 *
 * Usage: npm run check:conventions
 */
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(HERE, '..');
const APP_DIR = join(PROJECT_ROOT, 'src', 'app');

/** Tags that are interactive by definition. */
const INTERACTIVE_TAGS = new Set(['button', 'input', 'select', 'textarea']);

/** Attributes whose literal value is read out to a user (incl. screen readers). */
const TRANSLATABLE_ATTRS = ['title', 'aria-label', 'aria-placeholder', 'placeholder', 'alt'];

/** Matches an opening tag, tolerating `>` inside quoted attribute values. */
const TAG_RE = /<([a-zA-Z][\w-]*)((?:"[^"]*"|'[^']*'|[^>'"])*)>/g;

const findings = [];

function walk(dir, out = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full, out);
    } else {
      out.push(full);
    }
  }
  return out;
}

/** Blank a region but keep newlines, so reported line numbers stay accurate. */
function blank(source, regex) {
  return source.replace(regex, (match) => match.replace(/[^\n]/g, ' '));
}

function lineOf(source, index) {
  return source.slice(0, index).split('\n').length;
}

function report(file, line, rule, message) {
  findings.push({ file: relative(PROJECT_ROOT, file), line, rule, message });
}

/**
 * Templates live either in a .html file or inline in a component's `template:`
 * backtick literal. `templateUrl:` is not matched (no backtick follows it).
 */
function templatesOf(file) {
  const source = readFileSync(file, 'utf8');
  if (file.endsWith('.html')) {
    return [{ html: source, lineOffset: 0 }];
  }
  const found = [];
  const inline = /template:\s*`([\s\S]*?)`/g;
  let match;
  while ((match = inline.exec(source)) !== null) {
    found.push({ html: match[1], lineOffset: lineOf(source, match.index) - 1 });
  }
  return found;
}

/** A. interactive element without data-testid */
function checkTestIds(file, html, lineOffset) {
  TAG_RE.lastIndex = 0;
  let match;
  while ((match = TAG_RE.exec(html)) !== null) {
    const [, rawName, attrs] = match;
    const name = rawName.toLowerCase();

    const navigates = name === 'a' && /\s(href|routerLink|\[routerLink\])[\s=]/.test(attrs);
    const clickable = /\(click\)\s*=/.test(attrs);
    if (!INTERACTIVE_TAGS.has(name) && !navigates && !clickable) {
      continue;
    }
    if (/(^|\s)(data-testid|\[attr\.data-testid\])\s*=/.test(attrs)) {
      continue;
    }
    report(
      file,
      lineOffset + lineOf(html, match.index),
      'testid',
      `<${name}> is interactive but has no data-testid (FE-ADR-009 §1)`,
    );
  }
}

/** Angular control-flow block headers (`@for (x of xs; track x) {`, `@if (…) {`,
 *  `@let x = …;`) sit between tags and would otherwise read as text nodes. */
const CONTROL_FLOW_RE =
  /@(?:if|else|for|empty|switch|case|default|defer|placeholder|loading|error)\b[^{}<]*\{|@let\b[^;<]*;/g;

/** B. hardcoded user-visible text */
function checkHardcodedText(file, html, lineOffset) {
  // Comments, <svg> subtrees and control-flow headers carry no user-visible copy.
  const scannable = blank(
    blank(blank(html, /<!--[\s\S]*?-->/g), /<svg[\s\S]*?<\/svg>/gi),
    CONTROL_FLOW_RE,
  );

  // Text nodes between tags.
  const textNode = />([^<]*)</g;
  let match;
  while ((match = textNode.exec(scannable)) !== null) {
    const withoutBindings = match[1].replace(/\{\{[\s\S]*?\}\}/g, '');
    if (/\p{L}/u.test(withoutBindings)) {
      report(
        file,
        lineOffset + lineOf(scannable, match.index),
        'i18n',
        `hardcoded text "${withoutBindings.trim().slice(0, 40)}" — use a catalogue key (FE-ADR-012 §b)`,
      );
    }
  }

  // Literal values of user-visible attributes. A bound form such as
  // [attr.aria-label]="'KEY' | t" cannot match: the `]` breaks the pattern.
  for (const attr of TRANSLATABLE_ATTRS) {
    const literal = new RegExp(`(^|\\s)${attr}\\s*=\\s*"([^"]*)"`, 'g');
    let attrMatch;
    while ((attrMatch = literal.exec(scannable)) !== null) {
      if (/\p{L}/u.test(attrMatch[2])) {
        report(
          file,
          lineOffset + lineOf(scannable, attrMatch.index),
          'i18n',
          `hardcoded ${attr}="${attrMatch[2].slice(0, 40)}" — bind it to a catalogue key (FE-ADR-012 §b)`,
        );
      }
    }
  }
}

const files = walk(APP_DIR).filter(
  (file) => file.endsWith('.html') || (file.endsWith('.ts') && !file.endsWith('.spec.ts')),
);

for (const file of files) {
  for (const { html, lineOffset } of templatesOf(file)) {
    checkTestIds(file, html, lineOffset);
    checkHardcodedText(file, html, lineOffset);
  }
}

if (findings.length === 0) {
  console.log(`✔ conventions: ${files.length} files scanned, no violations.`);
  process.exit(0);
}

findings.sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line);
for (const finding of findings) {
  console.error(`${finding.file}:${finding.line}  [${finding.rule}]  ${finding.message}`);
}
console.error(`\n✖ conventions: ${findings.length} violation(s).`);
process.exit(1);
