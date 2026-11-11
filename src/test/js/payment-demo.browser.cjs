const {chromium}=require(require("node:path").resolve("target/ui-tools/node_modules/playwright"));
const assert=require("node:assert/strict"), fs=require("node:fs"), path=require("node:path");
(async()=>{
 const base=process.env.PAYMENT_DEMO_URL||"http://127.0.0.1:18085";
 const browser=await chromium.launch({headless:true,executablePath:"C:/Program Files/Google/Chrome/Application/chrome.exe"});
 const context=await browser.newContext(),page=await context.newPage();const errors=[];
 page.on("pageerror",error=>errors.push(error.message));
 const settle=()=>page.waitForLoadState("networkidle");
 const change=async persona=>{
   await page.goto(base+"/payments");await page.selectOption("#demo-persona",persona);
   await page.getByRole("button",{name:"Switch account",exact:true}).click();await settle();
 };
 try {
  for(let i=0;i<30;i++){try{const r=await context.request.get(base+"/payments");if(r.ok())break;}catch{}await new Promise(r=>setTimeout(r,500));}
  await page.goto(base+"/payments/new");await settle();
  assert.ok((await page.textContent("body")).includes("LOCAL DEMO"));
  await page.selectOption("#type","BOOKING");await page.selectOption("#targetId","DEMO-B001");
  await page.selectOption("#purpose","ADVANCE");await page.fill("#amount","1000");
  await page.locator("[data-method-choice=CASH]").click();
  await page.getByRole("button",{name:"Confirm Payment",exact:true}).click();await settle();
  const id=(await page.locator(".pay-invoice-title h2").innerText()).replace(/^INV-/,"");
  assert.ok(/^PAY[0-9]+$/.test(id));
  await page.fill("#amount","1500");await page.getByRole("button",{name:"Save changes",exact:true}).click();await settle();
  assert.ok((await page.textContent("#invoice-document")).includes("LKR 1,500.00"));
  const csrf=await page.locator('input[name="csrf"]').first().inputValue();
  // Spring Security's CSRF token (added to every th:action form) is required as well as the demo form token.
  const _csrf=await page.locator('input[name="_csrf"]').first().inputValue();
  assert.equal((await context.request.post(base+"/payments/"+id+"/status",{form:{csrf,status:"PAID"}})).status(),403);
  assert.equal((await context.request.post(base+"/payments/"+id+"/status",{form:{csrf,_csrf,status:"PAID"}})).status(),400);
  await change("customer2");
  assert.equal((await context.request.get(base+"/payments/"+id)).status(),404);
  assert.equal(await page.locator("[data-payment-row]").count(),0);
  await page.goto(base+"/payments/new");
  assert.ok(await page.locator('#targetId option[value="DEMO-O002"]').count());
  assert.equal(await page.locator('#targetId option[value="DEMO-O001"]').count(),0);
  await change("admin");await page.goto(base+"/payments/"+id);
  await page.selectOption("#status","PAID");await page.check("#verified");await page.getByRole("button",{name:"Update status",exact:true}).click();await settle();
  assert.ok((await page.textContent("#invoice-document")).includes("LKR 7,000.00"));
  assert.ok((await page.textContent("#invoice-document")).includes("LOCAL DEMO - FICTIONAL PAYMENT RECORD"));
  const download=await context.request.get(base+"/payments/"+id+"/download");
  assert.equal(download.status(),200);assert.ok((await download.text()).includes("LOCAL DEMO - FICTIONAL PAYMENT RECORD"));
  await page.selectOption("#status","REFUNDED");await page.getByRole("button",{name:"Update status",exact:true}).click();await settle();
  await change("customer1");await page.goto(base+"/payments/new");
  await page.selectOption("#type","ORDER");await page.selectOption("#targetId","DEMO-O001");
  await page.selectOption("#purpose","FULL");await page.fill("#amount","5000");await page.locator("[data-method-choice=CASH]").click();
  await page.getByRole("button",{name:"Confirm Payment",exact:true}).click();await settle();
  const cancelled=(await page.locator(".pay-invoice-title h2").innerText()).replace(/^INV-/,"");
  await page.getByRole("button",{name:"Update status",exact:true}).click();await settle();
  await change("admin");await page.goto(base+"/payments/"+cancelled);
  await page.check("#confirmed");await page.getByRole("button",{name:"Delete record",exact:true}).click();
  await page.getByRole("button",{name:"Yes, delete record",exact:true}).click();await settle();
  assert.equal((await context.request.get(base+"/payments/"+cancelled)).status(),404);
  const output=path.resolve("target/payment-demo-preview");fs.mkdirSync(output,{recursive:true});
  await page.screenshot({path:path.join(output,"admin.png"),fullPage:true});
  await change("customer1");await page.goto(base+"/payments/new");
  for(const width of [1440,768,390,320]){
    await page.setViewportSize({width,height:900});
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false,"Demo banner overflow at "+width);
  }
  await page.screenshot({path:path.join(output,"make-mobile.png"),fullPage:true});
  assert.deepEqual(errors,[]);
  console.log("Live demo browser checks passed: create, edit, customer confirmation rejection, customer isolation, admin confirm/refund, cancelled deletion, demo invoice download, account switching, and four responsive widths.");
  console.log("Demo data was isolated under target/; normal data files were not used.");
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
