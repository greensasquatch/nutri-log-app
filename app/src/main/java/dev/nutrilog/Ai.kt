package dev.nutrilog

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Only ciphertext and its IV enter preferences. The AES key stays in Android Keystore. */
class Keys(ctx:Context) {
    private val prefs=ctx.getSharedPreferences("credentials",0)
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("nutrilog",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES","AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("nutrilog",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(provider:String,raw:String) {
        if(raw.isBlank()) { prefs.edit().remove(provider).apply(); return }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val bytes=cipher.doFinal(raw.trim().toByteArray())
        prefs.edit().putString(provider,Base64.encodeToString(cipher.iv+bytes,Base64.NO_WRAP)).apply()
    }
    fun read(provider:String):String {
        val bytes=prefs.getString(provider,null)?.let { Base64.decode(it,Base64.NO_WRAP) } ?: return ""
        return String(Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            doFinal(bytes,12,bytes.size-12)
        },Charsets.UTF_8)
    }
}
private fun obj(vararg pairs:Pair<String,Any>)=JSONObject(mapOf(*pairs))
private fun schema(fields:Map<String,JSONObject>)=obj("type" to "object","additionalProperties" to false,"required" to JSONArray(fields.keys.toList()),"properties" to JSONObject(fields))
private val stringType=obj("type" to "string")
private val numberType=obj("type" to "number")
private val itemFields=mapOf("kind" to obj("type" to "string","enum" to JSONArray(listOf("Food","Exercise"))),"Name" to stringType,"Meal" to obj("type" to "string","enum" to JSONArray(mealTypes)),"Unit" to stringType,"Intensity" to stringType) + (macros+listOf("Quantity","Minutes","Distance","Burned","Sets","Reps")).associateWith { numberType }
private val resultSchema=schema(mapOf("message" to stringType,"items" to obj("type" to "array","items" to schema(itemFields))))
private const val guide="Interpret food and exercise as NEW draft entries only. Separate each item. For requests to edit or delete saved records return empty items and explain manual editing. For draft corrections return the entire revised draft. Ask a question in message and return no items if essential details are missing. Numeric totals apply to the given quantity. Distance in km, duration in minutes, energy in kcal, macros in g. Estimate activity expenditure. Use zero or empty string for irrelevant fields. Use relevant saved label data where possible."
/** Plan-authorized inference only. A completed stream is required before exposing an editable draft. */
fun interpret(model:String,token:String,prompt:String,time:String,context:String):List<Record> {
    val request=obj(
        "model" to model,"instructions" to guide,"store" to false,"stream" to true,
        "input" to JSONArray(listOf(obj("role" to "user","content" to (time+"\n"+context+"\n"+prompt)))),
        "text" to obj("format" to obj("type" to "json_schema","name" to "draft","strict" to true,"schema" to resultSchema))
    )
    val conn=connect("https://api.openai.com/v1/responses",token)
    try {
        conn.requestMethod="POST"
        conn.doOutput=true
        conn.setRequestProperty("Content-Type","application/json")
        conn.setRequestProperty("Accept","text/event-stream")
        conn.outputStream.use { it.write(request.toString().toByteArray()) }
        check(conn.responseCode in 200..299) { "ChatGPT request failed (HTTP "+conn.responseCode+"). Check sign-in and plan limits." }
        val output=StringBuilder()
        var completed=false
        conn.inputStream.bufferedReader().useLines { lines ->
            for(line in lines) {
                if(!line.startsWith("data:")) continue
                val data=line.substringAfter(':').trim()
                if(data=="[DONE]") break
                val event=JSONObject(data)
                when(event.optString("type")) {
                    "response.output_text.delta" -> output.append(event.getString("delta"))
                    "response.completed" -> {completed=true;break}
                    "response.failed","error","response.incomplete" -> error("ChatGPT could not complete this estimate. Check plan usage and retry.")
                }
                require(output.length<250000)
            }
        }
        check(completed) { "Connection interrupted. Retry the estimate." }
        val result=JSONObject(output.toString())
        val items=result.getJSONArray("items")
        require(items.length() in 1..100) { result.optString("message").ifBlank { "Please describe the new entry in more detail." } }
        return (0 until items.length()).map { i ->
            val o=items.getJSONObject(i)
            val kind=o.getString("kind")
            require(kind=="Food" || kind=="Exercise")
            val fields=itemFields.keys.filter { it!="kind" }.associateWith { o.get(it).toString() }
            Record(kind,fields+("Source" to "ChatGPT"),stamp=time).also { it.validate() }
        }
    } finally { conn.disconnect() }
}
