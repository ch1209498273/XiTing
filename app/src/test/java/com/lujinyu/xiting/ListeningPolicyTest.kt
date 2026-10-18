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
}
