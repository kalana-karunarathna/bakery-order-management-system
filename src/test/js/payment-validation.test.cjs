// Dependency-free form-event tests run with: node --test src/test/js/payment-validation.test.cjs
const {test} = require("node:test");
const assert = require("node:assert/strict");
const {readFileSync} = require("node:fs");
const vm = require("node:vm");
const script = readFileSync("src/main/resources/static/js/payment-validation.js","utf8");
function fixture(kind = "edit", options = {}) {
  const boxes = new Map(), events = {}, inputs = [];
  const classes = () => {
    const set = new Set();
    return {add:n=>set.add(n), toggle:(n,on)=>on ? set.add(n) : set.delete(n), contains:n=>set.has(n)};
  };
  function input(name,value,required=false) {
    const item={name,value,required,type:"text",tagName:"INPUT",classList:classes(),dataset:{},
      setAttribute(){},insertAdjacentElement(_,box){boxes.set("#"+box.id,box);},focus(){item.focused=true;}};
    inputs.push(item); return item;
  }
  const amount=input("amount",options.amount ?? "1000",true);
  const reference=input("reference",options.reference ?? "ref-001");
  inputs.namedItem=name=>inputs.find(i=>i.name===name);
  const form={dataset:{paymentForm:kind,max:"3000.00",method:"CARD",purpose:"PARTIAL",type:"ORDER"},
    elements:inputs,querySelector:sel=>sel===".is-invalid" ? inputs.find(i=>i.classList.contains("is-invalid")) : boxes.get(sel),
    addEventListener:(name,fn)=>events[name]=fn};
  if(kind==="create") {
    const select=(name,value,options)=>{
      const item=input(name,value,true);item.tagName="SELECT";item.options=options;options.forEach(o=>o.dataset ||= {});
      Object.defineProperty(item,"selectedOptions",{get:()=>options.filter(o=>o.value===item.value)});
      return item;
    };
    select("type","ORDER",[{value:"ORDER"},{value:"BOOKING"}]);
    select("targetId","O001",[{value:""},{value:"O001",dataset:{type:"ORDER",remaining:"3000.00"}},
      {value:"B001",dataset:{type:"BOOKING",remaining:"3000.00"}}]);
    select("purpose","PARTIAL",[{value:"PARTIAL"},{value:"FULL"},{value:"ADVANCE"}]);
    select("method","CARD",[{value:""},{value:"CARD"},{value:"CASH"},{value:"BANK_TRANSFER"}]);
  }
  if(options.serverError) boxes.set("#reference-error",{textContent:options.serverError,classList:classes(),setAttribute(){}});
  let confirms=0;
  vm.runInNewContext(script,{document:{
    querySelectorAll:selector=>selector==="form[data-payment-form]" ? [form] : [],
    createElement:()=>({classList:classes(),setAttribute(){},textContent:""})
  }, window:{confirm:()=>{confirms++;return options.confirm !== false;}}});
  const submit=()=>{
    let prevented=false;
    events.submit({preventDefault(){prevented=true;}});
    return !prevented;
  };
  return {form,input,amount,reference,boxes,events,submit,confirms:()=>confirms};
}
test("invalid amount submissions are stopped and corrected input clears the error",()=>{
  for(const value of ["","-1","0","NaN","Infinity","1.001","1e3","4000","999999999999999999999999999999999999"]) {
    const f=fixture("edit",{amount:value});
    assert.equal(f.submit(),false,value);
    assert.equal(f.amount.classList.contains("is-invalid"),true);
    assert.ok(f.boxes.get("#amount-error").textContent);
    f.amount.value="1000.25";f.events.input();
    assert.equal(f.amount.classList.contains("is-invalid"),false);
    assert.equal(f.submit(),true);
  }
});
test("references are optional, restricted, and server duplicate errors persist until corrected",()=>{
  for(const value of ["ab","bad|ref","<script>","bank\nref","CARD-PAY001","transfer-pay12"]) {
    const f=fixture("edit",{reference:value});assert.equal(f.submit(),false,value);
  }
  // Card and bank transfer references may be left blank (an administrator verifies the payment instead).
  assert.equal(fixture("edit",{reference:""}).submit(),true);
  const blankBank=fixture("edit",{reference:""});blankBank.form.dataset.method="BANK_TRANSFER";assert.equal(blankBank.submit(),true);
  const f=fixture("edit",{serverError:"Transaction reference already exists."});
  assert.equal(f.submit(),false);
  f.amount.value="500";f.events.input();assert.equal(f.submit(),false);
  f.reference.value="different-ref";f.events.input();assert.equal(f.submit(),true);
  const cash=fixture("edit",{reference:""});cash.form.dataset.method="CASH";assert.equal(cash.submit(),true);
});
test("card details are refused for card payments and card numbers for bank transfers",()=>{
  for(const value of ["4111 1111 1111 1111","4111-1111-1111-1111","1234567890123456","Exp 12/27","cvv 123","Ref 0045 12/27"]) {
    const f=fixture("edit",{reference:value});
    assert.equal(f.submit(),false,value);
    assert.match(f.boxes.get("#reference-error").textContent,/Never enter a card number/);
  }
  assert.equal(fixture("edit",{reference:"Ref 004521"}).submit(),true);
  assert.equal(fixture("edit",{reference:"123456789012"}).submit(),true, "12-digit terminal reference numbers are allowed");
  const bank=(value)=>{const f=fixture("edit",{reference:value});f.form.dataset.method="BANK_TRANSFER";return f;};
  const card=bank("4111 1111 1111 1111");assert.equal(card.submit(),false);
  assert.match(card.boxes.get("#reference-error").textContent,/looks like a card number/);
  assert.equal(bank("1234567890123456").submit(),true, "long numeric bank references that are not card numbers are allowed");
  assert.equal(bank("TRF-2026-0001").submit(),true);
});
test("admins must tick the verification box before choosing Paid",()=>{
  const f=fixture("status");
  const status=f.input("status","PAID",true);status.tagName="SELECT";status.options=[{value:"PAID"},{value:"FAILED"}];
  const verified=f.input("verified","true");verified.type="checkbox";verified.checked=false;
  assert.equal(f.submit(),false);
  assert.match(f.boxes.get("#verified-error").textContent,/verification box/);
  verified.checked=true;assert.equal(f.submit(),true);
  verified.checked=false;status.value="FAILED";assert.equal(f.submit(),true,"only Paid needs the verification");
});
test("full payment and booking advance constraints match backend rules",()=>{
  const full=fixture();full.form.dataset.purpose="FULL";assert.equal(full.submit(),false);
  full.amount.value="3000.00";assert.equal(full.submit(),true);
  const advance=fixture();advance.form.dataset.purpose="ADVANCE";assert.equal(advance.submit(),false);
  advance.form.dataset.type="BOOKING";assert.equal(advance.submit(),true);
  advance.amount.value="3000";assert.equal(advance.submit(),false);
});
test("dropdowns reject mismatched targets and empty methods",()=>{
  const f=fixture("create");
  assert.equal(f.submit(),true);
  const field=name=>f.form.elements.namedItem(name);
  field("targetId").value="B001";assert.equal(f.submit(),false);
  assert.equal(field("targetId").value,"");
  field("type").value="BOOKING";field("targetId").value="B001";
  field("method").value="";assert.equal(f.submit(),false);
  field("method").value="CARD";assert.equal(f.submit(),true);
});test("delete submission requires a checked confirmation and the dialog approval",()=>{
  const f=fixture("delete",{confirm:false});
  const check=f.input("confirmed","true",true);check.type="checkbox";check.checked=false;
  assert.equal(f.submit(),false);assert.equal(f.confirms(),0);
  check.checked=true;assert.equal(f.submit(),false);assert.equal(f.confirms(),1);
});
