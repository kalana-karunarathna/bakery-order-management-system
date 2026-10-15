// Run Maven tests first to generate real Thymeleaf fixtures under ignored target/.
// Install browser tooling only in target/ui-tools; production has no Node dependencies.
const {chromium}=require(require("node:path").resolve("target/ui-tools/node_modules/playwright"));
const fs=require("node:fs"), path=require("node:path"), assert=require("node:assert/strict");
(async()=>{
  const out=path.resolve("target/payment-ui-preview");
  const browser=await chromium.launch({headless:true,executablePath:process.env.PAYMENT_BROWSER || "C:/Program Files/Google/Chrome/Application/chrome.exe"});
  const context=await browser.newContext();
  const errors=[],posts=[];
  await context.route("**/*",async route=>{
    const request=route.request(),url=new URL(request.url());
    if(request.method()==="POST"){
      posts.push({path:url.pathname,values:Object.fromEntries(new URLSearchParams(request.postData()))});
      await route.fulfill({contentType:"text/html",body:fs.readFileSync(path.join(out,"pending.html"))});return;
    }
    const fixture=/^\/preview\/([a-z]+)$/.exec(url.pathname);
    if(fixture){await route.fulfill({contentType:"text/html",body:fs.readFileSync(path.join(out,fixture[1]+".html"))});return;}
    if(url.pathname==="/favicon.ico"){await route.fulfill({status:204});return;}
    const asset=path.resolve("src/main/resources/static","."+url.pathname);
    const root=path.resolve("src/main/resources/static")+path.sep;
    if(asset.startsWith(root)&&fs.existsSync(asset)&&fs.statSync(asset).isFile()){
      await route.fulfill({contentType:asset.endsWith(".css")?"text/css":"application/javascript",body:fs.readFileSync(asset)});return;
    }
    // Following a GET form keeps its existing endpoint and query parameters.
    if(url.pathname.startsWith("/payments")){
      const name=url.pathname.endsWith("/admin")?"admin":url.pathname.endsWith("/new")?"make":"history";
      await route.fulfill({contentType:"text/html",body:fs.readFileSync(path.join(out,name+".html"))});return;
    }
    await route.fulfill({status:204});
  });
  const page=await context.newPage();
  page.on("pageerror",e=>errors.push(e.message));
  page.on("console",m=>{if(m.type()==="error")errors.push(m.text());});
  const visit=async name=>{await page.goto("http://payment-ui.test/preview/"+name);await page.waitForLoadState("networkidle");};
  let layouts=0;
  try{
    for(const width of [1440,768,390,320]){
      await page.setViewportSize({width,height:900});
      for(const name of ["anonymous","history","admin","make","invoice","pending","delete","empty","error","invalid","download"]){
        await visit(name);
        assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false,name+" overflow at "+width);
        if([1440,390].includes(width)&&["history","admin","make","invoice","anonymous"].includes(name))
          await page.screenshot({path:path.join(out,name+"-"+width+".png"),fullPage:true});
        layouts++;
      }
    }
    console.log("Responsive checks passed:",layouts,"page/viewport combinations.");
    await page.setViewportSize({width:1440,height:1000});
    await visit("history");
    assert.equal(await page.locator("[data-payment-row]:visible").count(),10);
    await page.locator("[data-page-next]").click();assert.equal(await page.locator("[data-payment-row]:visible").count(),2);
    await page.locator("[data-page-prev]").click();assert.equal(await page.locator("[data-payment-row]:visible").count(),10);
    await page.locator("#query").fill("PAY002");
    await page.locator("#method").selectOption("CASH");await page.locator("#status").selectOption("PAID");
    await page.getByRole("button",{name:"Search",exact:true}).click();
    await page.waitForURL(url=>url.searchParams.get("query")==="PAY002");
    assert.equal(new URL(page.url()).searchParams.get("query"),"PAY002");
    assert.equal(new URL(page.url()).searchParams.get("status"),"PAID");

    console.log("Filter and pagination checks passed.");
    await visit("make");
    await page.getByRole("button",{name:"Confirm Payment",exact:true}).click();
    assert.equal(posts.length,0);assert.equal(await page.locator("#type").evaluate(e=>document.activeElement===e),true);
    await page.locator("#type").selectOption("BOOKING");await page.locator("#targetId").selectOption("CAKE-102");
    await page.locator("#purpose").selectOption("ADVANCE");await page.locator("#amount").fill("1000");
    await page.locator("[data-method-choice=CASH]").click();
    assert.equal(await page.locator("[data-method-choice=CASH]").getAttribute("aria-pressed"),"true");
    assert.equal(await page.locator("[data-summary-customer]").innerText(),"U001");
    assert.equal(await page.locator("[data-summary-paid]").innerText(),"LKR 1,000.00");
    assert.equal(await page.locator("[data-summary-remaining]").innerText(),"LKR 4,000.00");
    assert.equal(await page.locator("[data-summary-after]").innerText(),"Available after this payment: LKR 3,000.00");
    await page.locator("[data-method-choice=CARD]").click();
    await page.getByRole("button",{name:"Confirm Payment",exact:true}).click();
    assert.equal(posts.length,0);assert.equal(await page.locator("#reference").getAttribute("aria-invalid"),"true");
    await page.locator("#reference").fill("TERMINAL-UI-001");
    assert.equal(await page.locator("#reference").getAttribute("aria-invalid"),"false");
    await page.getByRole("button",{name:"Confirm Payment",exact:true}).click();await page.waitForLoadState("networkidle");
    assert.equal(posts.at(-1).path,"/payments");assert.equal(posts.at(-1).values.method,"CARD");

    await visit("pending");
    await page.locator("#amount").fill("1200");await page.locator("#reference").fill("UI-EDIT-001");
    await page.getByRole("button",{name:"Save changes",exact:true}).click();await page.waitForLoadState("networkidle");
    assert.equal(posts.at(-1).path,"/payments/PAY001/edit");assert.equal(posts.at(-1).values.amount,"1200");
    await visit("pending");await page.getByRole("button",{name:"Update status",exact:true}).click();await page.waitForLoadState("networkidle");
    assert.equal(posts.at(-1).path,"/payments/PAY001/status");assert.equal(posts.at(-1).values.status,"CANCELLED");

    console.log("Edit and status form checks passed.");
    await visit("delete");const before=posts.length;
    await page.locator("#confirmed").check();await page.getByRole("button",{name:"Delete record",exact:true}).click();
    await page.locator("#payment-delete-dialog.show").waitFor();
    assert.equal(posts.length,before);
    await page.getByRole("button",{name:"Keep record",exact:true}).click();
    await page.locator("#payment-delete-dialog").waitFor({state:"hidden"});
    await page.getByRole("button",{name:"Delete record",exact:true}).click();
    await page.getByRole("button",{name:"Yes, delete record",exact:true}).click();await page.waitForLoadState("networkidle");
    assert.equal(posts.at(-1).path,"/payments/PAY004/delete");assert.equal(posts.at(-1).values.confirmed,"true");

    console.log("Delete modal checks passed.");
    await visit("pending");
    const loading = await page.evaluate(() => {
      const form = document.querySelector('form[data-payment-form="edit"]');
      let result;
      form.addEventListener("submit",event => {
        result = form.getAttribute("aria-busy") === "true" && form.querySelector("button").disabled;
        event.preventDefault();
      },{once:true});
      form.requestSubmit();
      return result;
    });
    assert.equal(loading,true,"Valid submission should disable its button and show loading");
    await visit("invoice");
    await page.evaluate(()=>{window.print=()=>{window.printWasCalled=true;};});
    await page.getByRole("button",{name:"Print invoice",exact:true}).click();
    assert.equal(await page.evaluate(()=>window.printWasCalled),true);
    await page.emulateMedia({media:"print"});
    assert.equal(await page.locator("header").isVisible(),false);
    assert.equal(await page.locator("#invoice-document").isVisible(),true);
    await page.pdf({path:path.join(out,"invoice-print.pdf"),format:"A4",printBackground:true});

    assert.equal(errors.length,0,errors.join("\n"));
    console.log("Browser interactions passed: filters, pagination, validation, live summary, create/edit/status/delete form payloads, modal cancellation, invoice print and PDF.");
    console.log("Console/page errors:",errors.length);
  }finally{await context.unrouteAll({behavior:"ignoreErrors"});await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
