// One versioned, closed contract; no executable model instructions.
const str=(max=300)=>({type:'string',maxLength:max});
const num=(min,max)=>({type:'integer',minimum:min,maximum:max});
const nullable=s=>({anyOf:[s,{type:'null'}]});
const list=(items,max=20)=>({type:'array',items,maxItems:max});
const obj=properties=>({type:'object',properties,required:Object.keys(properties),additionalProperties:false});
const enumeration=values=>({type:'string',enum:values});
export const subSchema=obj({title:str(200),estimatedEffortMinutes:nullable(num(1,525600))});
export const commandSchema=obj({
 action:enumeration(['CREATE_TASK','CREATE_TASK_WITH_SUBTASKS','UPDATE_TASK','SET_PROGRESS','COMPLETE_TASK','CHANGE_DEADLINE','ADD_SUBTASK','COMPLETE_SUBTASK','QUERY_TASKS']),
 taskId:nullable(str(80)), title:nullable(str(200)), deadlineDate:nullable(str(10)), deadlineMinute:nullable(num(-1,1439)),
 importance:nullable(num(1,10)), progress:nullable(num(0,100)), estimatedEffortMinutes:nullable(num(1,525600)), note:nullable(str(1000)),
 subtasks:list(subSchema), subtaskIds:list(str(80)),
 query:nullable(obj({dateFrom:nullable(str(10)),dateTo:nullable(str(10)),minImportance:nullable(num(1,10)),maxMinutes:nullable(num(1,525600)),sort:enumeration(['FOCUS','IMPORTANCE','URGENCY'])}))
});
export const resultSchema=obj({status:enumeration(['READY','NEEDS_CLARIFICATION','UNSUPPORTED']),confidence:{type:'number',minimum:0,maximum:1},message:str(200),candidateIds:list(str(80),12),command:nullable(commandSchema)});
const taskSchema=obj({id:str(80),title:str(200),deadline:str(40),importance:num(1,10),progress:num(0,100),estimatedEffortMinutes:nullable(num(1,525600)),subtasks:list(obj({id:str(80),title:str(200),completed:{type:'boolean'}}),20)});
export const requestSchema=obj({text:str(2000),localDateTime:str(45),timezone:str(80),relevantTasks:list(taskSchema,12),appVersion:str(30),requestId:str(80)});
export function valid(schema,v){
 if(schema.anyOf)return schema.anyOf.some(s=>valid(s,v));
 if(schema.enum&&!schema.enum.includes(v))return false;
 switch(schema.type){
 case 'null':return v===null;
 case 'object':return v!==null&&typeof v==='object'&&!Array.isArray(v)&&Object.keys(v).every(k=>k in schema.properties)&&schema.required.every(k=>k in v&&valid(schema.properties[k],v[k]));
 case 'array':return Array.isArray(v)&&v.length<=schema.maxItems&&v.every(x=>valid(schema.items,x));
 case 'string':return typeof v==='string'&&v.length<=(schema.maxLength??Infinity);
 case 'boolean':return typeof v==='boolean';
 case 'integer':case 'number':return typeof v==='number'&&Number.isFinite(v)&&(schema.type!=='integer'||Number.isInteger(v))&&v>=schema.minimum&&v<=schema.maximum;
 default:return false;
 }
}
export function dateValid(s){return s===null||(/^\d{4}-\d{2}-\d{2}$/.test(s)&&Number.isFinite(Date.parse(s))&&new Date(s).toISOString().slice(0,10)===s);}
export function validateResult(r,req){
 if(!valid(resultSchema,r))return false;
 const ids=new Set(req.relevantTasks.map(t=>t.id));
 if(r.candidateIds.some(id=>!ids.has(id)))return false;
 if(r.status!=='READY')return r.command===null;
 const c=r.command;if(!c||!dateValid(c.deadlineDate)||c.title!==null&&!c.title.trim()||c.subtasks.some(s=>!s.title.trim()))return false;
 if(c.action.startsWith('CREATE_TASK'))return !!c.title&&!!c.deadlineDate&&c.deadlineMinute!==null&&c.taskId===null&&(c.action==='CREATE_TASK'?c.subtasks.length===0:c.subtasks.length>0);
 if(c.action==='QUERY_TASKS')return c.query!==null&&dateValid(c.query.dateFrom)&&dateValid(c.query.dateTo)&&(!c.query.dateFrom||!c.query.dateTo||c.query.dateFrom<=c.query.dateTo);
 const task=req.relevantTasks.find(t=>t.id===c.taskId);if(!task)return false;
 if(c.action==='SET_PROGRESS')return c.progress!==null&&task.subtasks.length===0;
 if(c.action==='CHANGE_DEADLINE')return !!c.deadlineDate&&c.deadlineMinute!==null;
 if(c.action==='ADD_SUBTASK')return c.subtasks.length>0;
 if(c.action==='COMPLETE_SUBTASK')return c.subtaskIds.length>0&&new Set(c.subtaskIds).size===c.subtaskIds.length&&c.subtaskIds.every(id=>task.subtasks.some(s=>s.id===id));
 if(c.action==='UPDATE_TASK')return c.title!==null||c.importance!==null||c.note!==null||c.estimatedEffortMinutes!==null;
 return c.action==='COMPLETE_TASK';
}
