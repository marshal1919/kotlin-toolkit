
//set NODE_OPTIONS=--openssl-legacy-provider

if (!CSS.highlights) {
  article.textContent = "CSS Custom Highlight API not supported.";

}
const el = document.querySelector("k");
const text = document.body.textContent;
const range1 = new Range();
range1.setStart(el.firstChild, 0);
range1.setEnd(el.firstChild, 5);
const range2 = new Range();
range2.setStart(el.firstChild, 12);
range2.setEnd(el.firstChild, 17);
const highlight = new Highlight();
CSS.highlights.set("user-1-highlight", highlight);
highlight.add(range1);
highlight.add(range2);

const el1 = document.getElementById("gg");
el1.addEventListener("click", modifyText, false);

function modifyText(event) {
  var s = window.getSelection();
  val=s.toString();

  if(val!=""){
    return;
  }


  pos=s.anchorOffset;
  //先向后移一个位置以便选择单字母单词
  //s.modify('move', 'backward', 'word');
  s.modify('extend', 'forward', 'word');
  s.modify('move', 'backward', 'word');
  s.modify('extend', 'forward', 'word');


  var str = s.toString();
  strlen=str.length;
  str=str.trim();

  if(str=="."||str==","||str==":"||str=="!"||str=="'"||str.length==0){
    s.empty();
    return;
  }
  if(str=="-"){
    s.modify('extend', 'forward', 'word');
  }
  if(strlen>str.length){
    s.modify('extend', 'backward', 'character');
  }

  var e = event ;
  posX=e.offsetX;
  range=s.getRangeAt(0);
  rect=range.getBoundingClientRect();
  console.log(rect.left,rect.right,e.clientX,e.offsetX);
  left=s.anchorOffset;
  right=s.focusOffset;
  if((posX>=rect.left&&posX<=rect.right)){

    console.log(right-left,left,right,pos);

  }
  else{
    console.log(right-left,left,right,pos);
    s.empty();
  }

  if(range1.isPointInRange(range.endContainer,right)){
    console.log("ok");
    s.empty();
  }

  //window.alert("hello");

};
<div xmlns="http://www.w3.org/1999/xhtml" class="r2-underline-4" style="border-bottom: 2px solid rgb(249, 239, 125); pointer-events: none; position: absolute; width: 8.00586px; height: 18.9091px; left: 4408.32px; top: 195.807px;"></div>