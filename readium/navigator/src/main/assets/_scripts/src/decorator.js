//
//  Copyright 2021 Readium Foundation. All rights reserved.
//  Use of this source code is governed by the BSD-style license
//  available in the top-level LICENSE file of the project.
//

import {
  getClientRectsNoOverlap,
  rectContainsPoint,
  toNativeRect,
} from "./rect";
import { log, logError, rangeFromLocator } from "./utils";
import { nearestInteractiveElement } from "./dom";

let styles = new Map();
let groups = new Map();
var lastGroupId = 0;

/**
 * Registers a list of additional supported Decoration Templates.
 *
 * Each template object is indexed by the style ID.
 */
export function registerTemplates(newStyles) {
  var stylesheet = "";

  for (const [id, style] of Object.entries(newStyles)) {
    styles.set(id, style);
    if (style.stylesheet) {
      stylesheet += style.stylesheet + "\n";
    }
  }

  if (stylesheet) {
    let styleElement = document.createElement("style");
    styleElement.innerHTML = stylesheet;
    document.getElementsByTagName("head")[0].appendChild(styleElement);
  }
}

/**
 * Returns an instance of DecorationGroup for the given group name.
 */
export function getDecorations(groupName) {
  var group = groups.get(groupName);
  if (!group) {
    let id = "r2-decoration-" + lastGroupId++;
    group = DecorationGroup(id, groupName);
    groups.set(groupName, group);
  }
  return group;
}

/**
 * Handles click events on a Decoration.
 * Returns whether a decoration matched this event.
 */
export function handleDecorationClickEvent(event, clickEvent) {
  /*if (groups.size === 0) {
    return false;
  }*/
  var sel = window.getSelection();
  let temp=sel.toString();
  var range;
  if(temp!=""){    
    range=sel.getRangeAt(0);    
  }
  else{
    //pos=sel.anchorOffset;
    //先向后移一个位置以便选择单字母单词
    sel.modify('extend', 'forward', 'word');
    sel.modify('move', 'backward', 'word');
    sel.modify('extend', 'forward', 'word');
        
    var str = sel.toString();
    let strlen=str.length;
    str=str.trim();

    if(str=="."||str==","||str==":"||str=="!"||str=="'"||str.length==0){
      sel.empty();
      return false;
    }
    if(str=="-"){
      sel.modify('extend', 'forward', 'word');
    }
    if(strlen>str.length){ 
      //有空格    
      sel.modify('extend', 'backward', 'character');
    }
    
    //let posX=event.offsetX;
    range=sel.getRangeAt(0);
    let rect = rectCorrect(range.getBoundingClientRect(), window.innerWidth, window.innerHeight).toJSON(); //range.getBoundingClientRect().toJSON();
    
    if (!rectContainsPoint(rect, event.clientX, event.clientY, 1)) {
      sel.empty();
      return false;
    }        
    
  }

  let left=sel.anchorOffset;
  let right=sel.focusOffset;
  let target =findTarget(range,left,right);
  if(target==null){
    return Android.onWordSelected(
      JSON.stringify({
        word: str,
        //rect: toNativeRect(rectCorrect(target.item.range.getBoundingClientRect(), window.innerWidth, window.innerHeight)),
        //click: clickEvent,
      })
    );
  }

  /*function findTarget() {
    for (const [group, groupContent] of groups) {
      for (const item of groupContent.items.reverse()) {
        if (!item.clickableElements) {
          continue;
        }
        for (const element of item.clickableElements) {
          let rect = element.getBoundingClientRect().toJSON();
          if (rectContainsPoint(rect, event.clientX, event.clientY, 1)) {
            return { group, item, element, rect };
          }
        }
      }
    }
  }*/

  function findTarget(range,left,right) {    
    for (const [group, groupContent] of groups) {
      for (const item of groupContent.items.reverse()) {                
        let storeRange=item.range;
        let rect = storeRange.getBoundingClientRect().toJSON();        
        if(storeRange.isPointInRange(range.startContainer,left)&&storeRange.isPointInRange(range.endContainer,right)){            
          sel.empty();
          let element=item.clickableElements[0];
          return { group, item,element,rect };
        }          
        
      }
    }       
  }
//需要处理rect坐标错误的情况，加上一页坐标
  function inRect(rect, x, y, pageWidth,tolerance) {
    return (
      ((rect.left < x || Math.abs(rect.left - x) <= tolerance) &&
      (rect.right > x || Math.abs(rect.right - x) <= tolerance) )||
      ((rect.left+pageWidth < x || Math.abs(rect.left+pageWidth - x) <= tolerance) &&
      (rect.right+pageWidth > x || Math.abs(rect.right+pageWidth - x) <= tolerance))     
    );
  }

  

  return Android.onDecorationActivated(
    JSON.stringify({
      id: target.item.decoration.id,
      group: target.group,
      rect: toNativeRect(rectCorrect(target.item.range.getBoundingClientRect(), window.innerWidth, window.innerHeight)),
      click: clickEvent,
    })
  );
}

 // 校正rect坐标

export function rectCorrect(rect,pageWidth,pageHeight) {
  //表格有多列的情况，取几个点的坐标
  let r1 = document.caretRangeFromPoint(20, 1);
  let r2 = document.caretRangeFromPoint(100, 1);     
  let rect1=r1.getBoundingClientRect();
  let rect2=r2.getBoundingClientRect();
  
  if(rect1.y<0||rect2.y<0){
    //坐标被上移了
    var r3;
    if(rect2.y<0)
      r3=r2;
    else
      r3=r1;    
    //加上真实行高与range高度的差
    let head3=parseFloat(window.getComputedStyle(r3.startContainer.parentNode).lineHeight)-r3.getBoundingClientRect().height;    
    rect.y+=Math.abs(Math.min(rect1.y,rect2.y)+head3/2);
    return rect;
  }
    
  var rangeCompare;
  var rectCompare;
  //选择不为空的取值
  if(rect2.x<0){
    rangeCompare=r2;    
    rectCompare=rect2;
  }
  else if(rect1.x<0){
    rangeCompare=r1;    
    rectCompare=rect1;
  }
  else
    return rect;

  let lineH=parseFloat(window.getComputedStyle(rangeCompare.startContainer.parentNode).lineHeight);
  let paddingTop=0;
  let paddingBottom=0;
  //使用右侧单元格数据或右侧单元格数据更长时
  if(rectCompare.x!=rect1.x||r1.startContainer.length>r2.startContainer.length){
    paddingTop=parseFloat(window.getComputedStyle(rangeCompare.startContainer.parentNode).paddingTop);
    paddingBottom=parseFloat(window.getComputedStyle(rangeCompare.startContainer.parentNode).paddingBottom);
  }
    
  //将range设为本段第一个字
  let rangeBegin = document.createRange();
  rangeBegin.setStart(rangeCompare.startContainer,0);
  rangeBegin.setEnd(rangeCompare.startContainer,0); 
  let rectBegin=rangeBegin.getBoundingClientRect();    
  //将range设为本段最后一个字
  let rangeEnd = document.createRange();
  rangeEnd.setStart(rangeCompare.endContainer,rangeCompare.endContainer.length-1);
  rangeEnd.setEnd(rangeCompare.endContainer,rangeCompare.endContainer.length-1);
  let rectEnd=rangeEnd.getBoundingClientRect();

  //待处理坐标被错误分到上页
  //rect取得的top是height的开始位置，比lineHeight小
  let head=(lineH-rectCompare.height);
  if(rect.x<0){
    rect.x+=pageWidth;
    rect.y=head/2+rect.y-rectCompare.y;//有多段坐标为负值的情况
    //比较的坐标是本段第一行
    if(rectCompare.y==rectBegin.y)
      rect.y+=paddingTop;
    return rect;
  }

  
  //待处理坐标在本页，但首行坐标被分到上一页了  
  let diffY=head+pageHeight-rectCompare.y;
  let lineCount=1+Math.max(Math.round((diffY-lineH)/lineH),0);
  let correctY=rect.y;
  if(rectCompare.y!=rectBegin.y){
    //比较的坐标不是本段第一行              
    rect.y+=lineCount*lineH;
    if(rectCompare.y==rectEnd.y){          
      //比较的坐标是本段最后一行
      rect.y+=paddingBottom;
    }    
  }
  else{
    //比较的坐标是本段第一行
    //let lineCount=1+Math.max(Math.round((diffY-lineH-paddingTop)/lineH),0);
    rect.y+=lineCount*lineH+paddingTop;
    if(rectCompare.y==rectEnd.y){
      //比较的坐标是本段最后一行
      rect.y+=paddingBottom;
    }
  }

  //错误坐标的第一行top值定位有可能不正确，还需校正
  correctY=rect.y-correctY;
  //取错误坐标的第一行的坐标
  let r4 = document.caretRangeFromPoint(rectCompare.x+pageWidth,correctY+head);  
  //未取到正确的坐标
  if(r4==null||r4.getBoundingClientRect().x<0)
    return rect;
  let rect4=r4.getBoundingClientRect();
    
  //在同一段的情况
  if(r4.startContainer==rangeCompare.startContainer)
    correctY=head/2-rect4.y;
  else{
    let pTop=parseFloat(window.getComputedStyle(r4.startContainer.parentNode).paddingTop);
    correctY=head/2+pTop-rect4.y;
  }
  rect.y+=correctY;  
    
  return rect;
  
  
}

/**
 * Creates a DecorationGroup object from a unique HTML ID and its name.
 */
export function DecorationGroup(groupId, groupName) {
  var items = [];
  var lastItemId = 0;
  var container = null;
  var highlights=new Map();  

  /**
   * Adds a new decoration to the group.
   */
  function add(decoration) {
    let id = groupId + "-" + lastItemId++;

    let range = rangeFromLocator(decoration.locator);
    if (!range) {
      log("Can't locate DOM range for decoration", decoration);
      return;
    }

    let item = { id, decoration, range };
    items.push(item);
    layout(item);
  }

  /**
   * Removes the decoration with given ID from the group.
   */
  function remove(decorationId) {
    let index = items.findIndex((i) => i.decoration.id === decorationId);
    if (index === -1) {
      return;
    }

    let item = items[index];
    items.splice(index, 1);
    if(item.cssName=="note"){
      deleteNote(item.id);
    }
    else
      highlights.get(item.cssName).delete(item.range);
    item.clickableElements = null;
    if (item.container) {
      item.container.remove();
      item.container = null;
    }
  }

  /**
   * Notifies that the given decoration was modified and needs to be updated.
   */
  function update(decoration) {
    remove(decoration.id);
    add(decoration);
  }

  /**
   * Removes all decorations from this group.
   */
  function clear() {
    for (const item of items) {
      if(item.cssName=="note"){
        deleteNote(item.id);
      }
      else
        highlights.get(item.cssName).delete(item.range);
    }
    highlights.clear();
    clearContainer();
    items.length = 0;
  }

  /**
   * Recreates the decoration elements.
   *
   * To be called after reflowing the resource, for example.
   */
  function requestLayout() {
    clearContainer();
    items.forEach((item) => layout(item));
  }
//get value of rgb in css content and convert to string
function getRGBColor(rgbStr){
  let pos=rgbStr.indexOf("rgb");
  if(pos==-1)
      return "wrong";
  else{
      let len=rgbStr.length;
      pos+=5;
      let sign="";        
      for(;pos<len;++pos){
          if(rgbStr[pos]==")"){//只取rgb后面的数字，不包括括号
              break;
          }                
          else if(rgbStr[pos]!=" "){                              
              sign+=rgbStr[pos];                                        
          }                 
      }
      return sign;
  }
}

function colo2Sign(color){
  let len=color.length;    
  let sign="";   
  for(var pos=0;pos<len;++pos){
      if(color[pos]!=","&&color[pos]!="."){//只取rgb后面的数字，不包括括号
          sign+=color[pos]; 
      }                                        
  }
  return sign;
}

  //function to set css content of hightlight
  function setHighlightCSS(cssName,cssContent,range) {    
    if(highlights.has(cssName)){
      if(!highlights.get(cssName).has(range)){
        highlights.get(cssName).add(range);
      }      
    }
    else{        
      let styleSheet = document.styleSheets[1];              
      styleSheet.insertRule(cssContent);
      const highlight = new Highlight();
      CSS.highlights.set(cssName, highlight);
      highlights.set(cssName,highlight);
      highlights.get(cssName).add(range);
    }
  }

  /**
   * Layouts a single Decoration item.
   */
  function layout(item) {
    let groupContainer = requireContainer();

    let style = styles.get(item.decoration.style);
    if (!style) {
      logError(`Unknown decoration style: ${item.decoration.style}`);
      return;
    }

    let itemContainer = document.createElement("div");
    itemContainer.setAttribute("id", item.id);
    itemContainer.setAttribute("data-style", item.decoration.style);
    itemContainer.style.setProperty("pointer-events", "none");

    let range = item.range;
    let strs=item.decoration.style.split(".");
    let sort=strs[strs.length-1];
    let format=item.decoration.element.trim();
    let colorValue=getRGBColor(format);
    if(colorValue=="wrong")
      return;
    
    let colorName=colo2Sign(colorValue);
    let cssName="";
    if(sort=="Highlight"){
      cssName=`highlight-${colorName}`;
      let cssContent=`::highlight(${cssName}) {background-color: rgb(${colorValue});}`;
      setHighlightCSS(cssName,cssContent,range);      
    }
    else if(sort=="Underline"){
      cssName=`underline-${colorName}`;
      let cssContent=`::highlight(${cssName}) {text-underline-offset: 25%;text-decoration: underline rgb(${colorValue}) 2px;}`;
      setHighlightCSS(cssName,cssContent,range);      
    }
    else if(sort=="DecorationStyleAnnotationMark"){
      cssName=`note`;
      addNote(item,range);
    }
    item.cssName=cssName;

    let elementTemplate;
    try {
      let template = document.createElement("template");
      template.innerHTML = item.decoration.element.trim();
      elementTemplate = template.content.firstElementChild;
    } catch (error) {
      logError(
        `Invalid decoration element "${item.decoration.element}": ${error.message}`
      );
      return;
    }

    const line = elementTemplate.cloneNode(true);
    line.style.setProperty("pointer-events", "none");        
    itemContainer.append(line);    

    //groupContainer.append(itemContainer);
    item.container = itemContainer;
        
    item.clickableElements = Array.from(
      itemContainer.querySelectorAll("[data-activable='1']")
    );
    if (item.clickableElements.length === 0) {
      item.clickableElements = Array.from(itemContainer.children);
    }
  }
//增加笔记注释
  function addNote(item,range){
    //let range=window.getSelection().getRangeAt(0);
    let el=document.getElementById(item.id);
    if(el)
      return;
    let newNode=document.createElement("span");
    newNode.classList.add("edit-icon");
    newNode.id=item.id;
    //num+=1;
    //newNode.addEventListener("click", outMsg("hello"));
    newNode.onclick=event=>{showNote(item,event)};
    range.insertNode(newNode);
    //console.log("yes");
}
//删除笔记注释
function deleteNote(id){
    let el=document.getElementById(id);
    if(el)
        el.remove();
}
//调用android的方法
function showNote(item,event){
  var pixelRatio = window.devicePixelRatio;
  let clickEvent = {
    defaultPrevented: event.defaultPrevented,
    x: event.clientX * pixelRatio,
    y: event.clientY * pixelRatio,
    targetElement: event.target.outerHTML,
    interactiveElement: nearestInteractiveElement(event.target),
  };

  Android.onDecorationActivated(
    JSON.stringify({
      id: item.decoration.id,
      group: "highlights",
      rect: toNativeRect(rectCorrect(item.range.getBoundingClientRect(), window.innerWidth, window.innerHeight)),
      click: clickEvent,
    })
  );

  event.stopPropagation();
  event.preventDefault();
  //console.log(msg);
  //window.alert(msg);
}
  /*function _layout(item) {
    let groupContainer = requireContainer();

    let style = styles.get(item.decoration.style);
    if (!style) {
      logError(`Unknown decoration style: ${item.decoration.style}`);
      return;
    }

    let itemContainer = document.createElement("div");
    itemContainer.setAttribute("id", item.id);
    itemContainer.setAttribute("data-style", item.decoration.style);
    itemContainer.style.setProperty("pointer-events", "none");

    let viewportWidth = window.innerWidth;
    let columnCount = parseInt(
      getComputedStyle(document.documentElement).getPropertyValue(
        "column-count"
      )
    );
    let pageWidth = viewportWidth / (columnCount || 1);
    let scrollingElement = document.scrollingElement;
    let xOffset = scrollingElement.scrollLeft;
    let yOffset = scrollingElement.scrollTop;

    function positionElement(element, rect, boundingRect) {
      element.style.position = "absolute";

      if (style.width === "wrap") {
        element.style.width = `${rect.width}px`;
        element.style.height = `${rect.height}px`;
        element.style.left = `${rect.left + xOffset}px`;
        element.style.top = `${rect.top + yOffset}px`;
      } else if (style.width === "viewport") {
        element.style.width = `${viewportWidth}px`;
        element.style.height = `${rect.height}px`;
        let left = Math.floor(rect.left / viewportWidth) * viewportWidth;
        element.style.left = `${left + xOffset}px`;
        element.style.top = `${rect.top + yOffset}px`;
      } else if (style.width === "bounds") {
        element.style.width = `${boundingRect.width}px`;
        element.style.height = `${rect.height}px`;
        element.style.left = `${boundingRect.left + xOffset}px`;
        element.style.top = `${rect.top + yOffset}px`;
      } else if (style.width === "page") {
        element.style.width = `${pageWidth}px`;
        element.style.height = `${rect.height}px`;
        let left = Math.floor(rect.left / pageWidth) * pageWidth;
        element.style.left = `${left + xOffset}px`;
        element.style.top = `${rect.top + yOffset}px`;
      }
    }

    let boundingRect = item.range.getBoundingClientRect();

    let elementTemplate;
    try {
      let template = document.createElement("template");
      template.innerHTML = item.decoration.element.trim();
      elementTemplate = template.content.firstElementChild;
    } catch (error) {
      logError(
        `Invalid decoration element "${item.decoration.element}": ${error.message}`
      );
      return;
    }

    if (style.layout === "boxes") {
      let doNotMergeHorizontallyAlignedRects = true;
      let clientRects = getClientRectsNoOverlap(
        item.range,
        doNotMergeHorizontallyAlignedRects
      );

      clientRects = clientRects.sort((r1, r2) => {
        if (r1.top < r2.top) {
          return -1;
        } else if (r1.top > r2.top) {
          return 1;
        } else {
          return 0;
        }
      });

      for (let clientRect of clientRects) {
        const line = elementTemplate.cloneNode(true);
        line.style.setProperty("pointer-events", "none");
        positionElement(line, clientRect, boundingRect);
        itemContainer.append(line);
      }
    } else if (style.layout === "bounds") {
      const bounds = elementTemplate.cloneNode(true);
      bounds.style.setProperty("pointer-events", "none");
      positionElement(bounds, boundingRect, boundingRect);

      itemContainer.append(bounds);
    }

    groupContainer.append(itemContainer);
    item.container = itemContainer;
    item.clickableElements = Array.from(
      itemContainer.querySelectorAll("[data-activable='1']")
    );
    if (item.clickableElements.length === 0) {
      item.clickableElements = Array.from(itemContainer.children);
    }
  }*/

  /**
   * Returns the group container element, after making sure it exists.
   */
  function requireContainer() {
    if (!container) {
      container = document.createElement("div");
      container.setAttribute("id", groupId);
      container.setAttribute("data-group", groupName);
      container.style.setProperty("pointer-events", "none");
      document.body.append(container);
    }
    return container;
  }

  /**
   * Removes the group container.
   */
  function clearContainer() {
    if (container) {
      container.remove();
      container = null;
    }
  }

  return { add, remove, update, clear, items, requestLayout };
}

window.addEventListener(
  "load",
  function () {
    // Will relayout all the decorations when the document body is resized.
    const body = document.body;
    var lastSize = { width: 0, height: 0 };
    const observer = new ResizeObserver(() => {
      if (
        lastSize.width === body.clientWidth &&
        lastSize.height === body.clientHeight
      ) {
        return;
      }
      lastSize = {
        width: body.clientWidth,
        height: body.clientHeight,
      };

      groups.forEach(function (group) {
        group.requestLayout();
      });
    });
    observer.observe(body);
  },
  false
);
