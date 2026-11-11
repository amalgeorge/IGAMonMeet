import test from 'node:test';
import assert from 'node:assert/strict';
import { moveItem } from '../src/dragSort.js';

test('moves an item down and up, leaving the rest in order', () => {
  assert.deepEqual(moveItem(['a', 'b', 'c', 'd'], 0, 2), ['b', 'c', 'a', 'd']);
  assert.deepEqual(moveItem(['a', 'b', 'c', 'd'], 3, 1), ['a', 'd', 'b', 'c']);
});

test('returns the same list for a move to itself or out of range', () => {
  const list = ['a', 'b'];
  assert.equal(moveItem(list, 1, 1), list);
  assert.equal(moveItem(list, 0, 5), list);
  assert.equal(moveItem(list, -1, 0), list);
});
