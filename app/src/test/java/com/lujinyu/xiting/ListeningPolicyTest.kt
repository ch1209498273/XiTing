package com.lujinyu.xiting
import com.lujinyu.xiting.listening.*
import org.junit.Assert.*
import org.junit.Test
class ListeningPolicyTest {
 @Test fun acceptsLinksFromAnyPlatform() {
  assertEquals(listOf("https://new.example/video?id=5"),SharedContentParser.parse("A new platform https://new.example/video?id=5").urls)
 }
 @Test fun rejectsDangerousEmptyAndOversizedInput() {
  assertTrue(SharedContentParser.parse("javascript:alert(1)").urls.isEmpty())
  assertTrue(SharedContentParser.parse("").urls.isEmpty())
  assertNotNull(SharedContentParser.parse("x".repeat(65537)).error)
  assertTrue(SharedContentParser.parse("https://x.example/"+"x".repeat(8192)).urls.isEmpty())
 }
 @Test fun requiresExplicitChoiceForMultipleLinks() {
  assertEquals(2,SharedContentParser.parse("https://a.example/v https://b.example/v").urls.size)
 }
 @Test fun expirationRespectsBoundaryAndProtection() {
  val now=100L*86_400_000
  assertTrue(RetentionPolicy.expired(10L*86_400_000,now,90,false))
  assertFalse(RetentionPolicy.expired(10L*86_400_000+1,now,90,false))
  assertFalse(RetentionPolicy.expired(1,now,90,true))
  assertFalse(RetentionPolicy.expired(1,now,0,false))
 }
 @Test fun identityNeverMatchesByTitleAlone() {
  assertFalse(ContentIdentityPolicy.same("a",null,"a",null))
  assertTrue(ContentIdentityPolicy.same("a","id1","a","id1"))
  assertFalse(ContentIdentityPolicy.same("a","id1","b","id1"))
 }
 @Test fun titleWithoutIdentityStillRecords() {
  // 只给标题、不给 media id 的播放器是多数，不能因此整条不记。
  val nul=Character.toString(0)
  val t=ListeningCapturePolicy.resolve(ContentSnapshot(pkg="a",app="某视频",title="第3集",identity=null,position=1,duration=2))!!
  assertEquals(nul+"t第3集",t.first)
  assertEquals("第3集",t.second.title)
  assertEquals("id1",ListeningCapturePolicy.resolve(ContentSnapshot(pkg="a",app="某视频",title="第3集",identity="id1",position=1,duration=2))!!.first)
 }
 @Test fun noUsableTitleWhilePlayingFallsBackToSourceRow() {
  // 档 2：系统没给可用标题但确实在播 → 记一条来源条目，标题写空，列表按现有口径显示「未识别内容」。
  val nul=Character.toString(0)
  val none=ListeningCapturePolicy.resolve(ContentSnapshot(pkg="a",app="某视频",title=null,identity=null,position=1,duration=2,playing=true))!!
  assertEquals(nul+"pa",none.first)
  assertNull(none.second.title)
  // 标题只是 App 自己的名字，同样按来源条目处理，不产出「某某视频」当成内容。
  val same=ListeningCapturePolicy.resolve(ContentSnapshot(pkg="a",app="某视频",title="某视频",identity=null,position=1,duration=2,playing=true))!!
  assertEquals(nul+"pa",same.first)
  assertNull(same.second.title)
  // 给了 media id 说明确有这条内容，同名也按内容记。
  val withId=ListeningCapturePolicy.resolve(ContentSnapshot(pkg="a",app="某视频",title="某视频",identity="id9",position=1,duration=2))!!
  assertEquals("id9",withId.first)
  assertEquals("某视频",withId.second.title)
 }
 @Test fun idleOrBlankPackageIsNotRecordable() {
  // 挂着暂停会话、又没标题：不该产出记录，否则列表会被"没在听的App"占满。
  assertNull(ListeningCapturePolicy.resolve(ContentSnapshot(pkg="a",app="某视频",title=null,identity=null,position=1,duration=2,playing=false)))
  assertNull(ListeningCapturePolicy.resolve(ContentSnapshot(pkg="a",app="某视频",title="   ",identity=null,position=1,duration=2,playing=false)))
  assertNull(ListeningCapturePolicy.resolve(ContentSnapshot(pkg="",app="某视频",title="第3集",identity="id1",position=1,duration=2)))
 }
 @Test fun sourceRowCannotCrossPackages() {
  val a=ListeningCapturePolicy.resolve(ContentSnapshot(pkg="a",app="A",title=null,identity=null,position=1,duration=2,playing=true))!!.first
  val b=ListeningCapturePolicy.resolve(ContentSnapshot(pkg="b",app="B",title=null,identity=null,position=1,duration=2,playing=true))!!.first
  assertTrue(ContentIdentityPolicy.same("a",a,"a",a))
  assertFalse(ContentIdentityPolicy.same("a",a,"b",b))
 }
}
