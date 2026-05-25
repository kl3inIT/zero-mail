#!/usr/bin/env node

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { basename, extname, join, relative, resolve } from 'node:path';
import { TextDecoder } from 'node:util';

const ROOT = process.cwd();
const decoder = new TextDecoder('utf-8', { fatal: true });

const skippedDirectories = new Set([
  '.git',
  '.gradle',
  '.next',
  '.turbo',
  '.codex-runs',
  'build',
  'coverage',
  'dist',
  'node_modules',
  'out',
]);

const textExtensions = new Set([
  '.cjs',
  '.css',
  '.html',
  '.java',
  '.js',
  '.json',
  '.jsx',
  '.kt',
  '.kts',
  '.md',
  '.mjs',
  '.properties',
  '.scss',
  '.sql',
  '.toml',
  '.ts',
  '.tsx',
  '.txt',
  '.xml',
  '.yaml',
  '.yml',
]);

const textFileNames = new Set([
  '.editorconfig',
  '.gitattributes',
  '.gitignore',
  'Dockerfile',
]);

const mojibakePattern =
  /\u00c3[\u0080-\u00bf]|\u00c2[\u0080-\u00bf]|\u00c6[\u0080-\u00bf]|\u00c4[\u0080-\u00bf]|\u00e1[\u00ba-\u00bf]|\u00e2\u20ac|\ufffd/u;

function shouldSkipFile(filePath) {
  const fileName = basename(filePath);
  // Skip all env files (operator secrets) regardless of suffix: .env, .env.local,
  // .env.local.*, .env.production, etc. Their content is operator-controlled and may
  // legitimately contain non-UTF-8 characters in secrets — encoding policy doesn't apply.
  return fileName === '.env' || fileName.startsWith('.env.');
}

function shouldScanFile(filePath) {
  const fileName = basename(filePath);
  return textFileNames.has(fileName) || textExtensions.has(extname(filePath));
}

function collectFiles(inputPath, output) {
  let fileStat;
  try {
    fileStat = statSync(inputPath);
  } catch {
    return;
  }

  if (fileStat.isDirectory()) {
    if (skippedDirectories.has(basename(inputPath))) {
      return;
    }

    for (const childName of readdirSync(inputPath)) {
      collectFiles(join(inputPath, childName), output);
    }
    return;
  }

  if (fileStat.isFile() && !shouldSkipFile(inputPath) && shouldScanFile(inputPath)) {
    output.push(inputPath);
  }
}

function printableExcerpt(line) {
  return line.trim().replace(/\s+/g, ' ').slice(0, 160);
}

function checkFile(filePath) {
  const failures = [];
  const bytes = readFileSync(filePath);
  const repoRelativePath = relative(ROOT, filePath).replaceAll('\\', '/');

  if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    failures.push(`${repoRelativePath}: UTF-8 BOM is not allowed`);
  }

  let text;
  try {
    text = decoder.decode(bytes);
  } catch (error) {
    failures.push(`${repoRelativePath}: invalid UTF-8 byte sequence`);
    return failures;
  }

  const lines = text.split(/\r?\n/);
  for (let lineIndex = 0; lineIndex < lines.length; lineIndex += 1) {
    const line = lines[lineIndex];
    if (line.includes('encoding-allow')) {
      continue;
    }
    if (mojibakePattern.test(line)) {
      failures.push(
        `${repoRelativePath}:${lineIndex + 1}: mojibake-looking text: ${printableExcerpt(line)}`,
      );
    }
  }

  return failures;
}

const inputPaths = process.argv.slice(2).map((inputPath) => resolve(ROOT, inputPath));
const scanRoots = inputPaths.length > 0 ? inputPaths : [ROOT];
const files = [];

for (const scanRoot of scanRoots) {
  collectFiles(scanRoot, files);
}

const failures = files.flatMap(checkFile);

if (failures.length > 0) {
  console.error(`encoding:check FAILED with ${failures.length} issue(s):`);
  for (const failure of failures) {
    console.error(failure);
  }
  process.exit(1);
}

console.log(`encoding:check OK - ${files.length} UTF-8 text file(s), no mojibake patterns.`);

