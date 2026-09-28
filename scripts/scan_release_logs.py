"""Scan release logs / staged source without echoing any matching secret value."""
import argparse
import json
from pathlib import Path
import re
import subprocess

PATTERNS = {
    'compact_jwt_or_jws': re.compile(r'eyJ[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{20,}'),
    'private_key_material': re.compile(r'-----BEGIN (?:RSA |EC )?PRIVATE KEY-----\s+[A-Za-z0-9+/=]{32,}'),
    'unmasked_mobile': re.compile(r'(?<![0-9])1[3-9][0-9]{9}(?![0-9])'),
    'probe_canary': re.compile(r'synthetic-(?:body|header)-secret-927'),
}


def scan(label, content, rules):
    # Findings include type and line only; never include the matching value.
    return [{'source': label, 'kind': name, 'line': content.count('\n', 0, match.start()) + 1}
            for name, pattern in rules.items() for match in pattern.finditer(content)]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--logs', nargs='*', default=[])
    parser.add_argument('--docker', nargs='*', default=[])
    parser.add_argument('--staged', action='store_true')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    findings, sources = [], []
    for name in args.logs:
        content = Path(name).read_text(encoding='utf-8', errors='replace')
        sources.append(name)
        findings.extend(scan(name, content, PATTERNS))
    for container in args.docker:
        run = subprocess.run(['docker', 'logs', '--since', '2h', container], capture_output=True, check=True)
        label = 'docker:' + container
        sources.append(label)
        findings.extend(scan(label, (run.stdout + run.stderr).decode('utf-8', errors='replace'), PATTERNS))
    if args.staged:
        names = subprocess.check_output(['git', 'diff', '--cached', '--name-only', '--diff-filter=ACM', '-z']).decode().split('\0')
        for name in filter(None, names):
            content = subprocess.check_output(['git', 'show', ':' + name]).decode('utf-8', errors='replace')
            label = 'staged:' + name
            sources.append(label)
            # Explicit synthetic test numbers and canary strings are legitimate test source.
            rules = {k: v for k, v in PATTERNS.items() if k in ('compact_jwt_or_jws', 'private_key_material')}
            findings.extend(scan(label, content, rules))
    result = {'sources': sources, 'findings': findings, 'passed': not findings,
              'scope': 'Pattern scan plus dedicated rejected-message canary regression; not a complete DLP audit.'}
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    Path(args.output).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'sources': len(sources), 'findings': len(findings), 'passed': not findings}))
    raise SystemExit(bool(findings))


if __name__ == '__main__':
    main()
