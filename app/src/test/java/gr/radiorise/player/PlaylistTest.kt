package gr.radiorise.player
import org.junit.Assert.*
import org.junit.Test
class PlaylistTest {
 @Test fun m3uTitlesAndDeduplication() {
  val s=Playlist.parse("#EXTM3U\n#EXTINF:-1,Alpha\nhttps://example.com/a\nhttps://example.com/a\n#EXTINF:-1,Beta\nhttps://example.com/b")
  assertEquals(2,s.size);assertEquals("Alpha",s[0].name);assertEquals("Beta",s[1].name)
 }
 @Test fun plsOrderedAndRelative() {
  val s=Playlist.parse("[playlist]\nTitle2=Beta\nFile2=b.mp3\nFile1=https://example.com/a.mp3\nTitle1=Alpha", "https://example.com/list.pls")
  assertEquals("Alpha",s[0].name);assertEquals("https://example.com/b.mp3",s[1].url)
 }
 @Test fun invalidSchemesRejected() { assertFalse(Playlist.valid("file:///sdcard/a"));assertFalse(Playlist.valid("javascript:alert(1)"));assertFalse(Playlist.valid("https:///a"));assertTrue(Playlist.valid("http://example.com/a?token=1")) }
 @Test(expected=IllegalArgumentException::class) fun hlsNotImportedAsSegments() { Playlist.parse("#EXTM3U\n#EXT-X-TARGETDURATION:8\nsegment.aac", "https://example.com/live.m3u8") }
 @Test fun exportRoundTrip() { val a=listOf(Station(name="Alpha",url="https://example.com/a"));val b=Playlist.parse(Playlist.export(a));assertEquals(a[0].name,b[0].name);assertEquals(a[0].url,b[0].url) }
 @Test fun blankList() { assertTrue(Playlist.parse("#EXTM3U\n").isEmpty()) }
 @Test fun customRadioTxtAndLogo() {
  val s=Playlist.parse("####Stations####\nVanilla Deep,https://example.com/live,https://example.com/logo.png\nLibero,http://example.com/live?type=http&amp;nocache=1,https://example.com/b.png")
  assertEquals(2,s.size);assertEquals("Vanilla Deep",s[0].name);assertEquals("https://example.com/logo.png",s[0].logo);assertTrue(s[1].url.contains("&nocache=1"));assertFalse(s[1].url.contains("&amp;"))
 }
 @Test fun quotedCsvAndTxtRoundTrip() {
  val a=listOf(Station(name="Radio, \"Best\"",url="https://example.com/live",logo="https://example.com/logo.png"))
  val b=Playlist.parse(Playlist.exportTxt(a));assertEquals(a[0].name,b[0].name);assertEquals(a[0].logo,b[0].logo)
 }
 @Test fun suppliedTwentyStations() {
  val text=javaClass.classLoader!!.getResourceAsStream("customradio-stations.txt")!!.bufferedReader().use { it.readText() }
  val s=Playlist.parse(text);assertEquals(20,s.size);assertEquals("Vanilla Deep",s.first().name);assertEquals("Ράδιο Λέχοβο (97.1)",s.last().name);assertTrue(s.all { it.logo.isNotBlank() })
 }
 @Test fun playlistStationLinksDetected() {
  assertTrue(Playlist.isPlaylistUrl("http://example.com/live.pls"));assertTrue(Playlist.isPlaylistUrl("https://example.com/radio.M3U?x=1"))
  assertFalse(Playlist.isPlaylistUrl("https://example.com/live.m3u8"));assertFalse(Playlist.isPlaylistUrl("https://example.com/stream.mp3"))
 }
 @Test fun themeSeedParsing() {
  assertEquals(210f to 0.5f,Themes.parseSeed("210.0,0.500"));assertNull(Themes.parseSeed(""));assertNull(Themes.parseSeed("x,1"))
 }
}
