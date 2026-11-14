package dev.nutrilog

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

private const val issuer = "https://auth.openai.com"
private const val resource = "https://api.openai.com/v1"
private const val tokenEndpoint = "$issuer/api/accounts/oauth/token"
private const val scope = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
private fun decode64(value: String) = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP)
private fun randomValue() = b64(ByteArray(32).also { SecureRandom().nextBytes(it) })
private fun form(values: Map<String,String>) = values.entries.joinToString("&") {
    URLEncoder.encode(it.key,"UTF-8")+"="+URLEncoder.encode(it.value,"UTF-8")
}

/** All remote destinations are selected by the app. Never echo server bodies or credentials in errors. */
internal fun connect(url: String, bearer: String? = null): HttpsURLConnection {
    val endpoint = URL(url)
    require(endpoint.protocol == "https" && endpoint.host in setOf("auth.openai.com","api.openai.com"))
    return (endpoint.openConnection() as HttpsURLConnection).apply {
        connectTimeout=15000
        readTimeout=60000
        instanceFollowRedirects=false
        bearer?.let { setRequestProperty("Authorization","Bearer "+it) }
    }
}
private fun fetch(url:String, fields:Map<String,String>?=null, bearer:String?=null):JSONObject {
    val c=connect(url,bearer)
    try {
        if(fields!=null) {
            c.requestMethod="POST"
            c.doOutput=true
            c.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
            c.outputStream.use { it.write(form(fields).toByteArray()) }
        }
        check(c.responseCode in 200..299) { "ChatGPT authorization request failed (HTTP "+c.responseCode+"). Try signing in again." }
        val body=c.inputStream.bufferedReader().use { it.readText() }
        return if(body.isBlank()) JSONObject() else JSONObject(body)
    } finally { c.disconnect() }
}

class PlanSession(context:Context, private val vault:Keys) {
    private val installation=context.getSharedPreferences("plan-installation",0)
    private val sessionKey="ChatGPT session"
    fun signedIn()=vault.read(sessionKey).isNotEmpty()
    private fun load()=JSONObject(vault.read(sessionKey).ifBlank { error("Continue with ChatGPT in Settings first.") })
    private fun host():String = installation.getString("host",null) ?: ("urn:uuid:"+UUID.randomUUID()).also {
        check(installation.edit().putString("host",it).commit())
    }

    // The system browser handles the password. A short-lived loopback listener only receives the code.
    fun signIn(openBrowser:(Uri)->Unit) {
        val state=randomValue()
        val nonce=randomValue()
        val verifier=randomValue()
        val existing=installation.getString("client",null)
        val callback=ServerSocket(0,4,InetAddress.getByName("127.0.0.1")).use { server ->
            val redirect="http://127.0.0.1:"+server.localPort+"/auth/callback"
            val params=mutableMapOf("client_id" to (existing ?: "dynamic_agent_client"),
                "ext_agent_host_id" to host(),"response_type" to "code","redirect_uri" to redirect,
                "scope" to scope,"resource" to resource,"state" to state,"nonce" to nonce,
                "code_challenge_method" to "S256","code_challenge" to b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())))
            if(existing==null) params["agent_name_hint"]="NutriLog"
            openBrowser(Uri.parse("$issuer/api/accounts/authorize?"+form(params)))
            val deadline=android.os.SystemClock.elapsedRealtime()+180000
            var answer:Uri?=null
            while(answer==null) {
                val left=deadline-android.os.SystemClock.elapsedRealtime()
                check(left>0) { "Sign-in timed out." }
                server.soTimeout=left.coerceAtMost(180000).toInt()
                server.accept().use { socket ->
                    socket.soTimeout=3000
                    val line=socket.getInputStream().bufferedReader().readLine().orEmpty()
                    val path=line.split(" ").getOrNull(1).orEmpty()
                    val uri=Uri.parse("http://127.0.0.1"+path)
                    val valid=line.startsWith("GET /auth/callback?") && uri.path=="/auth/callback" && uri.getQueryParameter("state")==state
                    val status=if(valid) "200 OK" else "400 Bad Request"
                    socket.getOutputStream().write(("HTTP/1.1 "+status+"\r\nConnection: close\r\nContent-Type: text/plain\r\nCache-Control: no-store\r\n\r\nReturn to NutriLog.").toByteArray())
                    if(valid) answer=uri
                }
            }
            redirect to requireNotNull(answer)
        }
        val uri=callback.second
        check(uri.getQueryParameter("error")==null) { "ChatGPT sign-in or plan access was declined." }
        val client=uri.getQueryParameter("client_id") ?: existing
        require(!client.isNullOrBlank() && client!="dynamic_agent_client" && (existing==null || existing==client))
        val tokens=fetch(tokenEndpoint,mapOf("grant_type" to "authorization_code","client_id" to client,
            "code" to requireNotNull(uri.getQueryParameter("code")), "code_verifier" to verifier,
            "redirect_uri" to callback.first,"resource" to resource))
        val subject=verifyIdentity(tokens.getString("id_token"),client,nonce)
        require(installation.getString("subject",subject)==subject) { "Sign out before switching accounts." }
        authorize(tokens)
        tokens.put("client",client)
        store(tokens)
        installation.edit().putString("client",client).putString("subject",subject).apply()
    }

    private fun authorize(tokens:JSONObject) {
        val grants=tokens.getString("scope").split(" ")
        require("chatgpt.tokens.use.direct" in grants && "resource.invoke" in grants) { "This account did not grant ChatGPT plan usage." }
        require(tokens.getString("access_token").isNotBlank() && tokens.getString("refresh_token").isNotBlank())
    }
    private fun store(tokens:JSONObject) {
        val lifetime=tokens.getLong("expires_in")
        require(lifetime in 1..31536000)
        tokens.put("expires",System.currentTimeMillis()+lifetime*1000)
        vault.save(sessionKey,tokens.toString())
    }
    @Synchronized fun access():String {
        val session=load()
        if(session.getLong("expires")>System.currentTimeMillis()+60000) return session.getString("access_token")
        val renewed=fetch(tokenEndpoint,mapOf("grant_type" to "refresh_token","resource" to resource,
            "client_id" to session.getString("client"),"refresh_token" to session.getString("refresh_token")))
        renewed.keys().forEach { session.put(it,renewed.get(it)) }
        authorize(session)
        store(session)
        return session.getString("access_token")
    }
    fun models():List<String> {
        val rows=fetch("$resource/models",bearer=access()).getJSONArray("models")
        return (0 until rows.length()).map { rows.getJSONObject(it) }.filter { it.optString("visibility")=="list" }.map { it.getString("slug") }
    }
    @Synchronized fun signOut():Boolean {
        val revoked=runCatching {
            val s=load()
            val discovery=fetch("$issuer/.well-known/openid-configuration")
            fetch(discovery.getString("revocation_endpoint"),mapOf("token" to s.getString("refresh_token"),
                "token_type_hint" to "refresh_token","client_id" to s.getString("client")))
        }.isSuccess
        forget()
        return revoked
    }
    fun forget() {
        vault.save(sessionKey,"")
        installation.edit().remove("client").remove("subject").apply()
    }

    private fun verifyIdentity(jwt:String, client:String, nonce:String):String {
        val parts=jwt.split('.')
        require(parts.size==3)
        val header=JSONObject(String(decode64(parts[0]),Charsets.UTF_8))
        require(header.getString("alg")=="RS256")
        val metadata=fetch("$issuer/.well-known/openid-configuration")
        require(metadata.getString("issuer")==issuer)
        val keys=fetch(metadata.getString("jwks_uri")).getJSONArray("keys")
        val jwk=(0 until keys.length()).map { keys.getJSONObject(it) }.single {
            it.optString("kid")==header.getString("kid") && it.optString("kty")=="RSA"
        }
        val key=KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(
            BigInteger(1,decode64(jwk.getString("n"))),BigInteger(1,decode64(jwk.getString("e")))))
        val verifier=Signature.getInstance("SHA256withRSA")
        verifier.initVerify(key)
        verifier.update((parts[0]+"."+parts[1]).toByteArray())
        require(verifier.verify(decode64(parts[2]))) { "Identity signature check failed." }
        val claims=JSONObject(String(decode64(parts[1]),Charsets.UTF_8))
        val audiences=claims.optJSONArray("aud")
        val audienceOkay=if(audiences==null) claims.getString("aud")==client else
            (0 until audiences.length()).any { audiences.getString(it)==client } && (audiences.length()==1 || claims.optString("azp")==client)
        require(audienceOkay && claims.getString("iss")==issuer && claims.getString("nonce")==nonce &&
            claims.getLong("exp")>System.currentTimeMillis()/1000)
        return claims.getString("sub").also { require(it.isNotBlank()) }
    }
}
