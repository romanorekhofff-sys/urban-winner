import test from 'node:test';import assert from 'node:assert/strict';
import {handle} from '../src/worker.js';import {commandSchema,validateResult} from '../src/schema.js';
const env={APP_TOKEN:'x'.repeat(40),OPENAI_API_KEY:'test-only-placeholder',VOICE_LIMIT:{limit:async()=>({success:true})}};
const blank=()=>Object.fromEntries(Object.keys(commandSchema.properties).map(k=>[k,['subtasks','subtaskIds'].includes(k)?[]:null]));
const task={id:'lecture',title:'Лекция',deadline:'2026-09-28',importance:8,progress:0,estimatedEffortMinutes:180,subtasks:[]};
const data=(text='Добавь завтра позвонить Иванову, важность семь')=>({text,localDateTime:'2026-09-25T22:00:00+03:00',timezone:'Europe/Moscow',relevantTasks:[task],appVersion:'1.2.0',requestId:'request-1'});
const ready=command=>({status:'READY',confidence:.98,message:'Готово',candidateIds:[],command:{...blank(),...command}});
const create=ready({action:'CREATE_TASK',title:'Позвонить Иванову',deadlineDate:'2026-09-26',deadlineMinute:-1,importance:7});
const req=(d=data(),headers={})=>new Request('https://focus.test/command',{method:'POST',headers:{Authorization:'Bearer '+env.APP_TOKEN,'Content-Type':'application/json',...headers},body:JSON.stringify(d)});
const model=r=>async(url,options)=>{assert.equal(url,'https://api.openai.com/v1/responses');const body=JSON.parse(options.body);assert.equal(body.store,false);assert.equal(body.text.format.strict,true);assert.equal(body.temperature,0);return Response.json({status:'completed',output:[{content:[{type:'output_text',text:JSON.stringify(r)}]}]});};
test('create travels through strict Responses API and keeps request id',async()=>{const r=await handle(req(),env,model(create));assert.equal(r.status,200);assert.equal((await r.json()).requestId,'request-1');});
test('rejects auth, method, unknown fields and oversized bodies before model',async()=>{
 assert.equal((await handle(req(data(),{Authorization:'Bearer wrong'}),env)).status,401);
 assert.equal((await handle(new Request('https://focus.test/command'),env)).status,405);
 assert.equal((await handle(req({...data(),system:'evil'}),env)).status,400);
 assert.equal((await handle(req(data('a'.repeat(25000))),env)).status,413);
 assert.equal((await handle(req(),{...env,VOICE_LIMIT:{limit:async()=>({success:false})}})).status,429);
});
test('rejects fabricated IDs, invalid ranges and calendar dates',()=>{
 for(const c of [{action:'SET_PROGRESS',taskId:'missing',progress:70},{action:'SET_PROGRESS',taskId:'lecture',progress:101},{action:'UPDATE_TASK',taskId:'lecture',importance:11},{action:'CHANGE_DEADLINE',taskId:'lecture',deadlineDate:'2026-02-30',deadlineMinute:-1}])assert.equal(validateResult(ready(c),data()),false);
});
test('clarification cannot carry a hidden executable command',async()=>{const r=await handle(req(),env,model({...create,status:'NEEDS_CLARIFICATION'}));assert.equal(r.status,502);});
test('handles OpenAI failures and malformed structured response',async()=>{
 assert.equal((await handle(req(),env,async()=>new Response('bad',{status:500}))).status,502);
 assert.equal((await handle(req(),env,model({oops:true}))).status,502);
 assert.equal((await handle(req(),env,async()=>{throw new Error('offline');})).status,502);
});
test('composite total uses children',async()=>{const r=await handle(req(),env,model(ready({action:'CREATE_TASK_WITH_SUBTASKS',title:'Лекция',deadlineDate:'2026-09-28',deadlineMinute:-1,importance:8,estimatedEffortMinutes:999,subtasks:[{title:'Статьи',estimatedEffortMinutes:60},{title:'Слайды',estimatedEffortMinutes:90},{title:'Случаи',estimatedEffortMinutes:30}]})));assert.equal((await r.json()).command.estimatedEffortMinutes,180);});
test('all Russian acceptance phrases have valid expected contracts (mock, not live semantics)',async()=>{
 const cases=[
 ['Добавь завтра позвонить Иванову, важность семь',create.command],
 ['До пятницы закончить статью, важность девять, часа три',{...create.command,title:'Закончить статью',deadlineDate:'2026-09-25',importance:9,estimatedEffortMinutes:180}],
 ['Добавь сегодня купить молоко',{...create.command,title:'Купить молоко',deadlineDate:'2026-09-25',importance:null}],
 ['По лекции готово процентов семьдесят',{action:'SET_PROGRESS',taskId:'lecture',progress:70}],
 ['Закрой задачу отправить статью',{action:'COMPLETE_TASK',taskId:'lecture'}],
 ['Перенеси диссертацию на следующий вторник',{action:'CHANGE_DEADLINE',taskId:'lecture',deadlineDate:'2026-09-29',deadlineMinute:-1}],
 ['Добавь в лекцию подзадачу найти новые исследования на сорок минут',{action:'ADD_SUBTASK',taskId:'lecture',subtasks:[{title:'Найти новые исследования',estimatedEffortMinutes:40}]}],
 ['Что у меня сегодня?',{action:'QUERY_TASKS',query:{dateFrom:'2026-09-25',dateTo:'2026-09-25',minImportance:null,maxMinutes:null,sort:'FOCUS'}}],
 ['Что самое срочное?',{action:'QUERY_TASKS',query:{dateFrom:null,dateTo:null,minImportance:null,maxMinutes:null,sort:'URGENCY'}}],
 ['Что я успею за час?',{action:'QUERY_TASKS',query:{dateFrom:null,dateTo:null,minImportance:null,maxMinutes:60,sort:'FOCUS'}}]
 ];
 for(const [phrase,c] of cases)assert.equal((await handle(req(data(phrase)),env,model(ready(c)))).status,200,phrase);
});
