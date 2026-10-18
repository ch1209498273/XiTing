package com.lujinyu.xiting

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SessionOutcomeStoreTest {
    @Test fun `local clock rollback does not apply external future filter`() {
        val original=1_800_000_000_000L
        val rows=JSONArray().put(JSONObject().put("id","clock").put("startedAt",original)
            .put("endedAt",JSONObject.NULL).put("scene","sleep").put("reason","timer").put("pause","none"))
        assertEquals(1,SessionOutcomeCodec.decode(rows,original-3_600_000,true).size)
        assertEquals(0,SessionOutcomeCodec.decode(rows,original-3_600_000,false).size)
    }
    private val now=1_800_000_000_000L
    private fun row(id:String="one",start:Long=now-1000,end:Any=now)=JSONObject()
        .put("id",id).put("startedAt",start).put("endedAt",end)
        .put("scene","sleep").put("reason","timer").put("pause","unconfirmed")
    private fun sanitize(rows:JSONArray):JSONArray {
        val type=try { Class.forName("com.lujinyu.xiting.SessionOutcomeCodec") }
            catch(_:ClassNotFoundException) { fail("Session outcome validation is not implemented");throw AssertionError() }
        return type.getMethod("sanitize",JSONArray::class.java,Long::class.javaPrimitiveType)
            .invoke(type.getField("INSTANCE").get(null),rows,now) as JSONArray
    }
    @Test fun `unknown end stays null and only public fields survive`() {
        val out=sanitize(JSONArray().put(row(end=JSONObject.NULL).put("title","private").put("token","secret")))
        assertEquals(1,out.length());assertTrue(out.getJSONObject(0).isNull("endedAt"))
        assertEquals(setOf("id","startedAt","endedAt","scene","reason","pause"),out.getJSONObject(0).keys().asSequence().toSet())
    }
    @Test fun `invalid categories time order and coerced timestamps are rejected`() {
        val rows=JSONArray().put(row().put("scene","video")).put(row().put("reason","played"))
            .put(row().put("pause","success")).put(row(end=now-2000)).put(row().put("startedAt","1800000000000"))
            .put(row().put("id",""))
        assertEquals(0,sanitize(rows).length())
    }
    @Test fun `id is idempotent and first end result wins`() {
        val out=sanitize(JSONArray().put(row()).put(row().put("pause","confirmed")))
        assertEquals(1,out.length());assertEquals("unconfirmed",out.getJSONObject(0).getString("pause"))
    }
    @Test fun `retention uses known end or unknown start and keeps newest hundred`() {
        val rows=JSONArray().put(row("expired",now-61L*86400000,now-61L*86400000))
            .put(row("oldUnknown",now-61L*86400000,JSONObject.NULL))
        for(i in 0..104)rows.put(row("id$i",now-1000-i,now-i))
        val out=sanitize(rows)
        assertEquals(100,out.length());assertEquals("id0",out.getJSONObject(0).getString("id"))
        assertEquals("id99",out.getJSONObject(99).getString("id"))
    }
    @Test fun `all documented categories round trip`() {
        val rows=JSONArray();var i=0
        for(scene in listOf("sleep","episode","continuous"))for(reason in listOf("user","timer","interrupted","unknown"))
            rows.put(row("id${i++}").put("scene",scene).put("reason",reason))
        for(pause in listOf("none","requested","confirmed","already_paused","unavailable","failed","unconfirmed"))
            rows.put(row("id${i++}").put("pause",pause))
        assertEquals(19,sanitize(rows).length())
    }
    @Test fun `pause feedback refines one id without changing end or regressing confirmation`() {
        val type=Class.forName("com.lujinyu.xiting.SessionOutcomeCodec")
        val method=try { type.getMethod("upsert",JSONArray::class.java,JSONObject::class.java,Long::class.javaPrimitiveType) }
            catch(_:NoSuchMethodException) { fail("Outcome idempotent feedback refinement is not implemented");throw AssertionError() }
        val instance=type.getField("INSTANCE").get(null)
        val first=JSONArray().put(row().put("pause","requested"))
        val confirmed=method.invoke(instance,first,row(end=now+1000).put("pause","confirmed"),now) as JSONArray
        assertEquals(1,confirmed.length());assertEquals(now,confirmed.getJSONObject(0).getLong("endedAt"))
        val repeated=method.invoke(instance,confirmed,row().put("pause","requested"),now) as JSONArray
        assertEquals("confirmed",repeated.getJSONObject(0).getString("pause"))
    }
    @Test fun `far future rows cannot occupy retained history`() {
        assertEquals(0,sanitize(JSONArray().put(row("future",now+86400000,now+86401000))).length())
    }
}
