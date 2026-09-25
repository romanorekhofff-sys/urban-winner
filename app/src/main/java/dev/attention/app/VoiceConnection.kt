package dev.attention.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.*
import org.json.*
import java.net.*
import java.security.KeyStore
import java.time.*
import java.util.UUID
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

data class VoiceConnection(val url:String="",val token:String="",val language:String="ru-RU")
// User-provided backend token only, encrypted with Android Keystore. Excluded from backup.
class VoiceCredentials(context:Context){
 private val prefs=context.getSharedPreferences("voice_connection",Context.MODE_PRIVATE)
 private fun key():javax.crypto.SecretKey {val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};return (store.getKey("focus.voice",null) as? javax.crypto.SecretKey)?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder("focus.voice",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()}
 fun load():VoiceConnection=runCatching{val encrypted=prefs.getString("token",null);val token=if(encrypted==null)"" else Cipher.getInstance("AES/GCM/NoPadding").let{c->c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(prefs.getString("iv",null),Base64.NO_WRAP)));String(c.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),Charsets.UTF_8)};VoiceConnection(prefs.getString("url","")?:"",token,prefs.getString("language","ru-RU")?:"ru-RU")}.getOrDefault(VoiceConnection())
 fun save(c:VoiceConnection){val url=endpoint(c.url);require(c.token.length in 32..256&&!c.token.startsWith("sk-")&&!c.token.any{it.isWhitespace()}){"Нужен APP_TOKEN, не ключ OpenAI"};require(c.language.matches(Regex("[a-zA-Z]{2,3}(-[a-zA-Z]{2,4})?")));val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());val encrypted=cipher.doFinal(c.token.toByteArray());check(prefs.edit().putString("url",url.toString().removeSuffix("/command")).putString("language",c.language).putString("token",Base64.encodeToString(encrypted,Base64.NO_WRAP)).putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).commit())}
 fun clear(){prefs.edit().clear().commit()}
 companion object {fun endpoint(raw:String):URL {val url=URI(raw.trim().trimEnd('/').removeSuffix("/command")+"/command");require(url.scheme=="https"&&!url.host.isNullOrBlank()&&url.userInfo==null&&url.query==null&&url.fragment==null&&url.path=="/command"){"Введите HTTPS-адрес Worker"};return url.toURL()}}
}
class VoiceFailure(val label:String):Exception(label)
object VoiceApi {
 suspend fun understand(context:Context,connection:VoiceConnection,text:String,candidates:List<Task>):VoiceResult=withContext(Dispatchers.IO){
  val requestId=UUID.randomUUID().toString();val json=JSONObject().put("text",text).put("localDateTime",ZonedDateTime.now().toOffsetDateTime().toString()).put("timezone",ZoneId.systemDefault().id).put("appVersion","1.2.0").put("requestId",requestId)
  json.put("relevantTasks",JSONArray().apply{candidates.take(12).forEach{t->put(JSONObject().put("id",t.id).put("title",t.title.take(200)).put("deadline",Instant.ofEpochMilli(t.due).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()).put("importance",t.importance).put("progress",t.progress).put("estimatedEffortMinutes",t.estimatedEffortMinutes?:JSONObject.NULL).put("subtasks",JSONArray().apply{t.subtasks.take(20).forEach{s->put(JSONObject().put("id",s.id).put("title",s.title.take(200)).put("completed",s.completed))}}))}})
  var connectionHttp:HttpsURLConnection?=null
  try {
   val bytes=json.toString().toByteArray(Charsets.UTF_8);if(bytes.size>24576)throw VoiceFailure("Слишком длинная команда. Сократите текст")
   val http=VoiceCredentials.endpoint(connection.url).openConnection() as HttpsURLConnection;connectionHttp=http;http.requestMethod="POST";http.connectTimeout=10000;http.readTimeout=25000;http.instanceFollowRedirects=false;http.doOutput=true;http.setRequestProperty("Authorization","Bearer "+connection.token);http.setRequestProperty("Content-Type","application/json");http.setFixedLengthStreamingMode(bytes.size)
   http.outputStream.use{it.write(bytes)};ensureActive()
   if(http.responseCode!=200)throw VoiceFailure(when(http.responseCode){401,403->"Проверьте токен подключения";429->"Лимит запросов. Повторите через минуту";504->"Сервер не ответил вовремя";503->"Сервер не настроен или временно недоступен";else->"Не удалось обработать команду"})
   val output=java.io.ByteArrayOutputStream();http.inputStream.use{input->val buf=ByteArray(4096);while(true){ensureActive();val n=input.read(buf);if(n<0)break;if(output.size()+n>32768)throw VoiceFailure("Некорректный ответ сервера");output.write(buf,0,n)}};ensureActive()
   val schema=context.assets.open("voice-result.schema.json").bufferedReader().use{JSONObject(it.readText())};val result=VoiceContract.parse(JSONObject(output.toString("UTF-8")),schema,requestId)
   require(result.candidates.all{id->candidates.any{it.id==id}});result.command?.let{VoiceContract.validate(it,candidates)};result
  }catch(e:CancellationException){throw e}catch(e:VoiceFailure){throw e}catch(e:SocketTimeoutException){throw VoiceFailure("Сервер не ответил вовремя")}catch(e:UnknownHostException){throw VoiceFailure("Нет соединения")}catch(e:java.io.IOException){throw VoiceFailure("Нет соединения")}catch(e:Exception){throw VoiceFailure("Некорректный ответ сервера. Исправьте текст и повторите")}finally{connectionHttp?.disconnect()}
 }
}
