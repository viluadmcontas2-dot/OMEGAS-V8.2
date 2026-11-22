const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const moduleRoot = path.join(__dirname, '..');
const read = file => fs.readFileSync(path.join(moduleRoot, file), 'utf8');

test('a identidade Android não pode mudar em atualizações in-place', () => {
  const gradle = read('build.gradle.kts');
  assert.match(gradle, /applicationId\s*=\s*"com\.omegas\.dyno\.lab"/);
  assert.match(gradle, /versionCode\s*=\s*\d+/);
  assert.match(gradle, /versionName\s*=\s*"[^"]+"/);
});

test('banco e exportação não podem apagar sessões durante atualização', () => {
  const vault = read('src/main/java/com/omegas/dyno/lab/VaultStore.kt');
  const capture = read('src/main/java/com/omegas/dyno/lab/CaptureVault.kt');
  assert.match(vault, /SQLiteOpenHelper\(context,\s*"dyno-vault\.db"/);
  for (const table of ['sessions', 'observations', 'events', 'notes']) {
    assert.match(vault, new RegExp('CREATE TABLE ' + table));
    assert.match(vault, new RegExp('"'+table+'"'));
  }
  assert.match(vault, /dyno-vault\.json/);
  assert.doesNotMatch(vault, /\bDROP\s+TABLE\b|\bDELETE\s+FROM\s+observations\b/i);
  assert.match(capture, /recoverInterrupted/);
  assert.match(capture, /exportZip/);
});

test('não aumentar schema sem escrever migração não destrutiva antes', () => {
  const vault = read('src/main/java/com/omegas/dyno/lab/VaultStore.kt');
  const version = Number(vault.match(/private\s+const\s+val\s+VERSION\s*=\s*(\d+)/)?.[1] ?? 0);
  assert.ok(version >= 1, 'VERSAO do schema ausente');
  if (version > 1) {
    assert.doesNotMatch(vault, /onUpgrade\([^]*?error\("Migração/, 'Schema > 1 exige onUpgrade real');
    assert.match(vault, /onUpgrade/);
  }
});

test('nenhuma chave ou senha de assinatura é parte do código publicável', () => {
  const gradle = read('build.gradle.kts');
  const manifest = read('src/main/AndroidManifest.xml');
  assert.doesNotMatch(gradle + manifest, /dyno-local-signing\.pwd|BEGIN PRIVATE KEY|storePassword\s*=/);
});
