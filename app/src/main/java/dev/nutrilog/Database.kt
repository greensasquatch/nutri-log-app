package dev.nutrilog

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

fun Record.json() = JSONObject().put("id",id).put("kind",kind).put("stamp",stamp).put("fields",JSONObject(fields))
fun decode(obj:JSONObject):Record {
    val fields=obj.getJSONObject("fields")
    return Record(obj.getString("kind"),fields.keys().asSequence().associateWith { fields.getString(it) },obj.getString("id"),obj.getString("stamp")).also { it.validate() }
}
/** Each row owns its nutritional snapshot. Reusing a meal never links its logged values to a template. */
class Journal(context:Context): SQLiteOpenHelper(context,"journal.db",null,1) {
    override fun onCreate(db:SQLiteDatabase) = db.execSQL("CREATE TABLE records(id TEXT PRIMARY KEY, stamp TEXT NOT NULL, payload TEXT NOT NULL)")
    override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int) { error("Unsupported database version") }
    fun all():List<Record> = readableDatabase.rawQuery("SELECT payload FROM records ORDER BY stamp DESC,rowid DESC",null).use { c ->
        buildList { while(c.moveToNext()) add(decode(JSONObject(c.getString(0)))) }
    }
    fun save(rows:List<Record>,replaceAll:Boolean=false) {
        rows.forEach { it.validate() }
        val db=writableDatabase
        // Deletion and insertion share a transaction during restore. Any failed insert rolls both back.
        db.beginTransaction()
        try {
            if(replaceAll) db.delete("records",null,null)
            rows.forEach { r ->
                val v=ContentValues().apply { put("id",r.id); put("stamp",r.stamp); put("payload",r.json().toString()) }
                check(db.insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE) != -1L)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun delete(id:String) { writableDatabase.delete("records","id=?",arrayOf(id)) }
}
private fun checksum(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
fun backup(rows:List<Record>,prefs:Map<String,String>):String {
    val data=JSONObject().put("records",JSONArray(rows.map { it.json() })).put("settings",JSONObject(prefs)).toString()
    return JSONObject().put("format",1).put("database",1).put("version","0.3.0").put("created",now()).put("count",rows.size).put("sha256",checksum(data)).put("payload",data).toString()
}
/** Inspect and decode in memory. The caller asks for confirmation before replacing journal data. */
fun restore(s:String):Pair<List<Record>,Map<String,String>> {
    val obj=JSONObject(s)
    require(obj.getInt("format")==1 && obj.getInt("database")==1) { "Unsupported backup version" }
    val data=obj.getString("payload")
    require(checksum(data)==obj.getString("sha256")) { "Backup integrity check failed" }
    val payload=JSONObject(data)
    val items=payload.getJSONArray("records")
    val rows=(0 until items.length()).map { decode(items.getJSONObject(it)) }
    require(rows.size==obj.getInt("count") && rows.distinctBy { it.id }.size==rows.size)
    val prefs=payload.getJSONObject("settings")
    return rows to prefs.keys().asSequence().filter { it in settingKeys }.associateWith { prefs.getString(it) }
}
val settingKeys=setOf("Plan model","Weight unit","Length unit","Theme","Net")
