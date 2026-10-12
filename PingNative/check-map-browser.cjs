// Requires Playwright; optionally set PING_CHROME_PATH to a Chromium executable.
// Makes online tile requests and then simulates an outage without using Firebase.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {chromium} = require('playwright');
(async () => {
 const browser = await chromium.launch({executablePath:process.env.PING_CHROME_PATH,headless:true});
 try {
 const page = await browser.newPage({viewport:{width:412,height:850}, deviceScaleFactor:1, isMobile:true,
 userAgent:'Mozilla/5.0 (Linux; Android 14; Test Device) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.0.0 Mobile Safari/537.36'});
 page.on('pageerror', e => console.log('JS ERROR:', e.message));
 page.on('console', m => console.log('BROWSER:',m.text()));
 page.on('requestfailed', r=>console.log('REQUEST FAILED:',r.url(),r.failure()));
 page.on('response', r=> { if (r.url().includes('tile.openstreetmap.org')) console.log('Tile response:',r.status()); });
 await page.route('https://appassets.androidplatform.net/assets/**', async route => {
  const name = path.basename(new URL(route.request().url()).pathname);
  const contentType = {html:'text/html',css:'text/css',js:'text/javascript'}[name.split('.').pop()];
  await route.fulfill({body:fs.readFileSync(path.join(__dirname,'app/src/main/assets',name)),contentType});
 });
 await page.goto('https://appassets.androidplatform.net/assets/map.html');
 await page.evaluate(()=>updateLocation(12.9716,77.5946));
 await page.waitForFunction(()=>!tiles.isLoading() && document.querySelectorAll('.leaflet-tile-loaded').length > 0,{},{timeout:20000});
 console.log(await page.evaluate(()=>({ready:window.mapReady,size:map.getSize(),marker:marker?.getLatLng(),tiles:document.querySelectorAll('.leaflet-tile-loaded').length,body:document.body.innerText})));
 const state = await page.evaluate(()=>({filter:getComputedStyle(document.querySelector('.leaflet-tile-pane')).filter, width:map.getSize().x, height:map.getSize().y}));
 assert.equal(state.filter,'none');
 assert.equal(state.width,412);
 assert.equal(state.height,850);
 fs.mkdirSync(path.join(__dirname,'build/reports/map'),{recursive:true});
 await page.screenshot({path:path.join(__dirname,'build/reports/map/map-after.png')});
 await page.evaluate(()=>updateLocation(12.975,77.6));
 await page.waitForFunction(()=>Math.abs(map.getCenter().lat-12.975)<0.0001 && Math.abs(marker.getLatLng().lat-12.975)<0.0001);
 await page.setViewportSize({width:850,height:412});
 await page.waitForFunction(()=>map.getSize().x===850);
 await page.waitForFunction(()=>!tiles.isLoading());
 await page.route('https://tile.openstreetmap.org/**', route=>route.abort('internetdisconnected'));
 // Move to uncached tiles: already decoded images may survive a failed redraw.
 await page.evaluate(()=> { updateLocation(28.6139,77.209); });
 await page.locator('#tile-error').waitFor({state:'visible',timeout:5000}).catch(async e=> {
   console.log(await page.evaluate(()=>({error:tileError.outerHTML,tiles:Array.from(document.querySelectorAll('.leaflet-tile')).map(t=>({done:t.complete,width:t.naturalWidth})),loading:tiles.isLoading()})));
   throw e;
 });
 assert.equal(await page.locator('.custom-marker').isVisible(),true);
 await page.unroute('https://tile.openstreetmap.org/**');
 await page.locator('#tile-error').click();
 await page.waitForFunction(()=>!tiles.isLoading() && document.querySelectorAll('.leaflet-tile-loaded').length>0);
 console.log('PASS: readable map tiles, marker, live following, resize, network failure with marker retained, retry recovery');
 } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exitCode=1;});
