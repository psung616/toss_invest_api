#!/usr/bin/env node
// PreToolUse 훅: 코드 영역별 필수 문서를 이번 세션에서 Read 했는지 확인하고, 안 읽었으면 편집을 막는다(exit 2).
// 설정은 CLAUDE.md 의 "코드 영역별 필수 문서" 표 하나뿐이다 (이 파일에 매핑을 두지 않는다).
// 외부 통신 없음. 표준 라이브러리만 사용. 오류 시에는 편집을 막지 않고(exit 0) 경고만 남긴다.
import { readFileSync, existsSync } from 'node:fs';
import { resolve, relative, isAbsolute } from 'node:path';

const SECTION = '## 코드 영역별 필수 문서';

function readStdin() {
  try {
    return JSON.parse(readFileSync(0, 'utf8'));
  } catch {
    return null;
  }
}

const norm = (p) => p.replace(/\\/g, '/');

// glob(예: a/** , **/x/** , *.java)을 정규식으로 바꾼다. ** 는 여러 단계 폴더, * 는 한 단계.
function globToRegex(glob) {
  let re = '';
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i];
    if (c === '*') {
      if (glob[i + 1] === '*') {
        i++;
        if (glob[i + 1] === '/') {
          i++;
          re += '(?:.*/)?';
        } else {
          re += '.*';
        }
      } else {
        re += '[^/]*';
      }
    } else if ('.+?^${}()|[]\\'.includes(c)) {
      re += '\\' + c;
    } else {
      re += c;
    }
  }
  return new RegExp('^' + re + '$');
}

/** CLAUDE.md 표에서 [{globs:[RegExp], docs:[string]}] 를 읽는다. */
function loadRules(projectDir) {
  const md = readFileSync(resolve(projectDir, 'CLAUDE.md'), 'utf8');
  const start = md.indexOf(SECTION);
  if (start < 0) return [];
  const rest = md.slice(start + SECTION.length);
  const end = rest.search(/\n## /);
  const section = end < 0 ? rest : rest.slice(0, end);
  const rules = [];
  for (const line of section.split('\n')) {
    const cells = line.split('|').map((s) => s.trim());
    if (cells.length < 4 || !cells[1].includes('`') || !cells[2].includes('`')) continue;
    const ticks = (cell) => [...cell.matchAll(/`([^`]+)`/g)].map((m) => m[1].trim());
    rules.push({ globs: ticks(cells[1]).map(globToRegex), docs: ticks(cells[2]) });
  }
  return rules;
}

/** 트랜스크립트에서 Read 도구로 읽은 파일의 프로젝트 기준 상대 경로 집합. */
function readFiles(transcriptPath, projectDir) {
  const seen = new Set();
  if (!transcriptPath || !existsSync(transcriptPath)) return seen;
  const root = norm(resolve(projectDir)).toLowerCase();
  for (const line of readFileSync(transcriptPath, 'utf8').split('\n')) {
    if (!line.includes('"Read"')) continue;
    let entry;
    try {
      entry = JSON.parse(line);
    } catch {
      continue;
    }
    const content = entry?.message?.content;
    if (!Array.isArray(content)) continue;
    for (const item of content) {
      if (item?.type === 'tool_use' && item.name === 'Read' && typeof item.input?.file_path === 'string') {
        const abs = norm(resolve(item.input.file_path)).toLowerCase();
        if (abs.startsWith(root + '/')) seen.add(abs.slice(root.length + 1));
      }
    }
  }
  return seen;
}

function main() {
  const input = readStdin();
  if (!input) return 0;
  const target = input.tool_input?.file_path ?? input.tool_input?.notebook_path;
  if (typeof target !== 'string') return 0;

  const projectDir = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
  const absTarget = isAbsolute(target) ? target : resolve(projectDir, target);
  const rel = norm(relative(projectDir, absTarget));
  if (rel.startsWith('..')) return 0; // 프로젝트 밖 파일은 대상 아님

  const required = new Set();
  for (const rule of loadRules(projectDir)) {
    if (rule.globs.some((g) => g.test(rel))) rule.docs.forEach((d) => required.add(norm(d)));
  }
  if (required.size === 0) return 0;

  const seen = readFiles(input.transcript_path, projectDir);
  const missing = [...required].filter((d) => !seen.has(d.toLowerCase()));
  if (missing.length === 0) return 0;

  process.stderr.write(
    `[require-docs] '${rel}' 를 편집하기 전에 다음 문서를 Read 도구로 먼저 읽으세요 (CLAUDE.md "코드 영역별 필수 문서"):\n` +
      missing.map((d) => `  - ${d}`).join('\n') +
      '\n읽은 뒤 같은 편집을 다시 시도하면 통과합니다.\n',
  );
  return 2;
}

try {
  process.exit(main());
} catch (e) {
  process.stderr.write(`[require-docs] 훅 내부 오류로 검사를 건너뜁니다: ${e?.message ?? e}\n`);
  process.exit(0);
}
