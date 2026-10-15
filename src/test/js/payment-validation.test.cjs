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
test("references are required, restricted, and server duplicate errors persist until corrected",()=>{
  for(const value of ["","ab","bad|ref","<script>","bank\nref"]) {
    const f=fixture("edit",{reference:value});assert.equal(f.submit(),false,value);
  }
  const f=fixture("edit",{serverError:"Transaction reference already exists."});
  assert.equal(f.submit(),false);
  f.amount.value="500";f.events.input();assert.equal(f.submit(),false);
  f.reference.value="different-ref";f.events.input();assert.equal(f.submit(),true);
  const cash=fixture("edit",{reference:""});cash.form.dataset.method="CASH";assert.equal(cash.submit(),true);
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
