// Run with node check-map.cjs. Exercises the map's location and retry behavior.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const html = fs.readFileSync(path.join(__dirname, 'app/src/main/assets/map.html'), 'utf8');
const code = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const events = {}, windowEvents = {}, frames = [];
const error = {style:{}};
let position, redraws = 0;
const map = {setView(p,z){this.center=p;this.zoom=z;return this;}, panTo(p){this.center=p;}, getZoom(){return this.zoom;}, invalidateSize(){}};
const tiles = {on(n,f){events[n]=f;return this;},addTo(){return this;},redraw(){redraws++;}};
const context = {
  window:{addEventListener(n,f){windowEvents[n]=f;}},
  document:{getElementById(){return error;}},
  L:{map(){return map;},tileLayer(){return tiles;},divIcon(o){return o;},
    marker(p){position=p;return {addTo(){return this;},setLatLng(p){position=p;},getLatLng(){return {lat:position[0],lng:position[1]};}};}},
  requestAnimationFrame(f){frames.push(f);return frames.length;},cancelAnimationFrame(){}
};
vm.createContext(context);
vm.runInContext(code, context);
assert.equal(context.window.mapReady,true);
assert.equal(map.zoom,2,'Map must show a world view before GPS arrives');
context.updateLocation(12.9,77.6);
assert.equal(map.zoom,16);
assert.deepEqual(Array.from(position),[12.9,77.6]);
context.updateLocation(NaN,77);
assert.deepEqual(Array.from(position),[12.9,77.6]);
context.updateLocation(13,78);
assert.deepEqual(Array.from(map.center),[13,78], 'Map must follow subsequent location updates');
frames.shift()(100);
frames.shift()(2600);
assert.deepEqual(Array.from(position),[13,78]);
map.center = [0,0];
context.recenterLocation();
assert.deepEqual(Array.from(map.center),[13,78]);
events.tileerror();
assert.equal(error.style.display,'block');
error.onclick();
assert.equal(redraws,1);
windowEvents.online();
assert.equal(redraws,2);
assert(!html.includes('unpkg.com'), 'Leaflet must be bundled');
assert(!html.includes('invert(100%)'), 'Map must not be darkened into an unreadable black image');
const kotlin = fs.readFileSync(path.join(__dirname,'app/src/main/java/com/ping/app/MainActivity.kt'),'utf8');
assert(!/[\u00c2\u00e2\ufffd]/.test(kotlin), 'Corrupted UI text');
console.log('PASS: startup, first fix, invalid coordinates, movement, tile errors/retry, offline assets, text encoding');
