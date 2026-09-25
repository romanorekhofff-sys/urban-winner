import {requestSchema,resultSchema,valid,validateResult} from './schema.js';
import {SYSTEM_PROMPT} from './prompt.js';
const MODEL='gpt-4.1-mini-2025-04-14';
const reply=(value,status=200)=>new Response(JSON.stringify(value),{status,headers:{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
class HttpError extends Error{constructor(status,code){super(code);this.status=status;}}
export async function readLimited(body,max){
 if(!body)throw new HttpError(400,'INVALID_REQUEST');
 const reader=body.getReader();let size=0;const chunks=[];
 try{while(true){const {value,done}=await reader.read();if(done)break;size+=value.length;if(size>max){await reader.cancel();throw new HttpError(413,'REQUEST_TOO_LARGE');}chunks.push(value);}}finally{reader.releaseLock();}
 const bytes=new Uint8Array(size);let at=0;for(const chunk of chunks){bytes.set(chunk,at);at+=chunk.length;}return new TextDecoder('utf-8',{fatal:true}).decode(bytes);
}
async function authenticate(request,env){
 if(!env.APP_TOKEN||env.APP_TOKEN.length<32||!env.OPENAI_API_KEY||!env.VOICE_LIMIT)throw new HttpError(503,'NOT_CONFIGURED');
 const supplied=request.headers.get('Authorization')||'';
 const digest=async s=>new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)));
 const [a,b]=await Promise.all([digest(supplied),digest('Bearer '+env.APP_TOKEN)]);let diff=0;for(let i=0;i<a.length;i++)diff|=a[i]^b[i];
 if(diff)throw new HttpError(401,'UNAUTHORIZED');
 // Personal principal today; replace with verified account subject for future auth.
 return 'personal';
}
export async function handle(request,env,fetcher=fetch){
 try{
  if(new URL(request.url).pathname!=='/command')return reply({error:'NOT_FOUND'},404);
  if(request.method!=='POST')return reply({error:'METHOD_NOT_ALLOWED'},405);
  const principal=await authenticate(request,env);
  if(!(await env.VOICE_LIMIT.limit({key:principal})).success)return reply({error:'RATE_LIMITED'},429);
  if(!request.headers.get('Content-Type')?.toLowerCase().startsWith('application/json'))return reply({error:'INVALID_REQUEST'},400);
  let data;try{data=JSON.parse(await readLimited(request.body,24576));}catch(e){if(e instanceof HttpError)throw e;throw new HttpError(400,'INVALID_REQUEST');}
  if(!valid(requestSchema,data)||!data.text.trim()||!data.requestId.trim()||!/^\d{4}-\d{2}-\d{2}T.+(?:Z|[+-]\d{2}:\d{2})$/.test(data.localDateTime)||!Number.isFinite(Date.parse(data.localDateTime)))throw new HttpError(400,'INVALID_REQUEST');
  try{new Intl.DateTimeFormat('en',{timeZone:data.timezone}).format();}catch{throw new HttpError(400,'INVALID_REQUEST');}
  if(new Set(data.relevantTasks.map(t=>t.id)).size!==data.relevantTasks.length)throw new HttpError(400,'INVALID_REQUEST');
  const controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),20000);let result;
  try{
   const upstream=await fetcher('https://api.openai.com/v1/responses',{method:'POST',headers:{Authorization:'Bearer '+env.OPENAI_API_KEY,'Content-Type':'application/json'},signal:controller.signal,body:JSON.stringify({model:MODEL,store:false,temperature:0,max_output_tokens:1800,instructions:SYSTEM_PROMPT,input:JSON.stringify({text:data.text,localDateTime:data.localDateTime,timezone:data.timezone,relevantTasks:data.relevantTasks}),text:{format:{type:'json_schema',name:'focus_command_v1',strict:true,schema:resultSchema}}})});
   if(!upstream.ok)throw new HttpError(upstream.status===429?429:502,'UPSTREAM_ERROR');
   const response=JSON.parse(await readLimited(upstream.body,65536));
   if(response.status!=='completed')throw new HttpError(502,'INVALID_RESPONSE');
   const contents=(response.output||[]).flatMap(o=>o.content||[]);
   if(contents.some(c=>c.type==='refusal'))throw new HttpError(422,'NOT_UNDERSTOOD');
   const output=contents.filter(c=>c.type==='output_text');if(output.length!==1)throw new HttpError(502,'INVALID_RESPONSE');
   try{result=JSON.parse(output[0].text);}catch{throw new HttpError(502,'INVALID_RESPONSE');}
   if(!validateResult(result,data))throw new HttpError(502,'INVALID_RESPONSE');
   if(result.command?.action==='CREATE_TASK_WITH_SUBTASKS'){
    const subs=result.command.subtasks;result.command.estimatedEffortMinutes=subs.every(s=>s.estimatedEffortMinutes!==null)?subs.reduce((n,s)=>n+s.estimatedEffortMinutes,0):null;
    if(result.command.estimatedEffortMinutes>525600)throw new HttpError(422,'NOT_UNDERSTOOD');
   }
  }catch(e){if(controller.signal.aborted)throw new HttpError(504,'TIMEOUT');if(e instanceof HttpError)throw e;throw new HttpError(502,'UPSTREAM_ERROR');}finally{clearTimeout(timeout);}
  return reply({version:1,requestId:data.requestId,...result});
 }catch(e){return reply({error:e instanceof HttpError?e.message:'UNAVAILABLE'},e instanceof HttpError?e.status:503);}
}
export default {fetch(request,env){return handle(request,env);}};
