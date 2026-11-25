#!/usr/bin/env node
// Stop 훅: 작업 트리에서 HANDOFF.md 보다 나중에 바뀐 파일이 있으면, 응답을 끝내기 전에 docs/HANDOFF.md 를 갱신하게 한다.
// - 같은 턴에서 이미 한 번 되돌려 보냈으면(stop_hook_active) 통과시켜 무한 반복을 막는다.
// - 외부 통신 없음. 오류 시에는 막지 않는다(exit 0).
import { readFileSync, statSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';

const HANDOFF = 'docs/HANDOFF.md';
// HANDOFF 갱신을 요구하지 않는 파일 (빌드 산출물 등은 git 이 이미 무시)
const IGNORE = [/^docs\/HANDOFF\.md$/, /^docs\/qa\/reports\//];

function main() {
  let input = {};
  try {
    input = JSON.parse(readFileSync(0, 'utf8') || '{}');
  } catch {
    input = {};
  }
  if (input.stop_hook_active) return;

  const projectDir = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
  const handoffPath = resolve(projectDir, HANDOFF);
  if (!existsSync(handoffPath)) return;
  const handoffTime = statSync(handoffPath).mtimeMs;

  const porcelain = execFileSync('git', ['status', '--porcelain', '-uall'], {
    cwd: projectDir,
    encoding: 'utf8',
  });
  const newer = [];
  for (const raw of porcelain.split('\n')) {
    if (!raw.trim()) continue;
    let path = raw.slice(3).trim();
    if (path.includes(' -> ')) path = path.split(' -> ')[1];
    path = path.replace(/^"|"$/g, '');
    if (IGNORE.some((re) => re.test(path))) continue;
    const abs = resolve(projectDir, path);
    if (!existsSync(abs)) {
      newer.push(path); // 삭제된 파일도 변경으로 본다
      continue;
    }
    if (statSync(abs).mtimeMs > handoffTime + 1000) newer.push(path);
  }
  if (newer.length === 0) return;

  const shown = newer.slice(0, 10).map((p) => `  - ${p}`).join('\n');
  const more = newer.length > 10 ? `\n  ... 외 ${newer.length - 10}개` : '';
  process.stdout.write(
    JSON.stringify({
      decision: 'block',
      reason:
        `[require-handoff] HANDOFF.md 보다 나중에 바뀐 파일이 있습니다:\n${shown}${more}\n` +
        '응답을 끝내기 전에 docs/HANDOFF.md 의 "마지막 갱신"·"현재 상태"·"다음 할 일"·"작업 로그"를 ' +
        '이번 작업 기준으로 갱신하세요 (어디까지 했는지, 남은 것, 사람이 할 일). 작업 ID 가 있으면 docs/TASKS.md 체크도 확인.',
    }),
  );
}

try {
  main();
} catch (e) {
  process.stderr.write(`[require-handoff] 훅 내부 오류로 검사를 건너뜁니다: ${e?.message ?? e}\n`);
}
process.exit(0);
