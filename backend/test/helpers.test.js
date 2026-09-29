const { describe, it } = require('node:test');
const assert = require('node:assert');
const { generateUserId, sanitizeUser } = require('../utils/helpers');

describe('generateUserId', () => {
  it('returns a 32-character hex string', () => {
    const id = generateUserId();
    assert.strictEqual(typeof id, 'string');
    assert.strictEqual(id.length, 32);
    assert.match(id, /^[0-9a-f]{32}$/);
  });

  it('returns different values on consecutive calls', () => {
    const first = generateUserId();
    const second = generateUserId();
    assert.notStrictEqual(first, second);
  });
});

describe('sanitizeUser', () => {
  it('does not return password / passwordHash even if present in input', () => {
    const input = {
      id: 1,
      username: 'demo',
      displayName: 'Demo User',
      email: 'demo@example.com',
      phone: '0123456789',
      avatarUrl: 'https://example.com/avatar.png',
      avatarId: 'avatar123',
      bio: 'hello',
      githubUrl: 'https://github.com/demo',
      linkedinUrl: 'https://linkedin.com/in/demo',
      websiteUrl: 'https://demo.example.com',
      verified: true,
      createdAt: '2024-01-01',
      updatedAt: '2024-01-02',
      password: 'secret-password',
      passwordHash: 'hashed-secret',
    };
    const output = sanitizeUser(input);
    assert.strictEqual('password' in output, false);
    assert.strictEqual('passwordHash' in output, false);
    assert.strictEqual(output.username, 'demo');
    assert.strictEqual(output.email, 'demo@example.com');
  });
});
