const {readFileSync} = require('node:fs');
const {before, after, beforeEach, test} = require('node:test');
const assert = require('node:assert/strict');
const {initializeTestEnvironment, assertSucceeds, assertFails} = require('@firebase/rules-unit-testing');
let env;
const pin = '123456';
const session = {owner_uid:'owner', session_id:'session-1', battery_percent:75, lat:12, lng:77};
const claim = uid => ({uid, session_id:'session-1', connection_id:`connection-${uid}`});
const db = uid => env.authenticatedContext(uid).database();
const ref = uid => db(uid).ref(`active_sessions/${pin}`);
before(async () => {
  env = await initializeTestEnvironment({projectId:'demo-ping-tests', database:{rules:readFileSync(`${__dirname}/database.rules.json`, 'utf8')}});
});
after(async () => { if (env) await env.cleanup(); });
beforeEach(async () => { await env.clearDatabase(); await assertSucceeds(ref('owner').set(session)); });
test('owner updates heartbeat, battery and SOS after observer joins', async () => {
  await assertSucceeds(ref('alice').child('observer').set(claim('alice')));
  await assertSucceeds(ref('owner').update({heartbeat_at:Date.now(), battery_percent:74, sos_active:true, sos:{active:true,event_id:'event-1',session_id:'session-1',at:Date.now()}}));
  assert.equal((await assertSucceeds(ref('alice').once('value'))).val().battery_percent, 74);
  await assertSucceeds(ref('owner').remove());
});
test('one observer; release permits a new observer', async () => {
  await assertSucceeds(ref('alice').child('observer').set(claim('alice')));
  await assertFails(ref('bob').once('value'));
  await assertFails(ref('bob').child('observer').set(claim('bob')));
  await assertFails(ref('bob').child('observer').remove());
  await assertSucceeds(ref('alice').child('observer').remove());
  await assertSucceeds(ref('bob').child('observer').set(claim('bob')));
});
test('observer cannot alter broadcaster data or claim a nonexistent session', async () => {
  await assertFails(ref('alice').child('observer').set({...claim('alice'),session_id:'old-session'}));
  await assertSucceeds(ref('alice').child('observer').set(claim('alice')));
  for (const patch of [{lat:13},{battery_percent:1},{sos_active:true}]) await assertFails(ref('alice').update(patch));
  await assertFails(ref('alice').remove());
  await assertFails(db('alice').ref('active_sessions/654321/observer').set(claim('alice')));
});
test('unauthenticated access and session enumeration are denied', async () => {
  const anon = env.unauthenticatedContext().database();
  await assertFails(anon.ref(`active_sessions/${pin}`).once('value'));
  await assertFails(anon.ref(`active_sessions/${pin}`).set(session));
  await assertFails(db('alice').ref('active_sessions').once('value'));
  await assertFails(db('alice').ref('active_sessions/not-a-pin').set({...session,owner_uid:'alice'}));
});
test('concurrent claims allow exactly one winner', async () => {
  const results = await Promise.allSettled(['alice','bob'].map(uid => ref(uid).child('observer').transaction(value => value === null ? claim(uid) : undefined)));
  assert.equal(results.filter(r => r.status === 'fulfilled' && r.value.committed).length, 1);
  const value = (await ref('owner').child('observer').once('value')).val();
  assert.ok(['alice','bob'].includes(value.uid));
});
test('invalid coordinates, battery, unknown fields and changed identity are denied', async () => {
  for (const patch of [{lat:91},{lng:-181},{battery_percent:101},{battery_percent:0.5},{owner_uid:'alice'},{session_id:'other'},{unexpected:true}]) await assertFails(ref('owner').update(patch));
});
