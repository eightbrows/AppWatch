package io.github.eightbrows.appwatch.data.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareParserTest {

    // ---- パッケージ名の形 ----

    @Test
    fun パッケージ名の形() {
        assertTrue(ShareParser.isValidPackageName("jp.example.freecoin"))
        assertTrue(ShareParser.isValidPackageName("com.Example_1.app2"))
        assertFalse(ShareParser.isValidPackageName("freecoin")) // 部分が1つだけ
        assertFalse(ShareParser.isValidPackageName("1jp.example")) // 数字で始まる
        assertFalse(ShareParser.isValidPackageName("jp.1example")) // 部分が数字で始まる
        assertFalse(ShareParser.isValidPackageName("jp..example")) // 空の部分
        assertFalse(ShareParser.isValidPackageName("jp.example.")) // 最後がドット
        assertFalse(ShareParser.isValidPackageName("jp.example-app")) // 使えない文字
        assertFalse(ShareParser.isValidPackageName("jp.例え.app")) // 英字以外
        assertFalse(ShareParser.isValidPackageName(""))
    }

    // ---- パッケージ名の取り出し ----

    @Test
    fun GooglePlayのURLから取り出す() {
        assertEquals("jp.example.freecoin", ShareParser.extractPackageName("https://play.google.com/store/apps/details?id=jp.example.freecoin"))
        assertEquals("jp.example.freecoin", ShareParser.extractPackageName("http://play.google.com/store/apps/details?id=jp.example.freecoin"))
        // 他の項目が付いている
        assertEquals("jp.example.freecoin", ShareParser.extractPackageName("https://play.google.com/store/apps/details?hl=ja&id=jp.example.freecoin&gl=JP"))
        assertEquals("jp.example.freecoin", ShareParser.extractPackageName("https://play.google.com/store/apps/details?id=jp.example.freecoin&pcampaignid=web_share"))
        // 前後に空白
        assertEquals("jp.example.freecoin", ShareParser.extractPackageName("  https://play.google.com/store/apps/details?id=jp.example.freecoin \n"))
    }

    @Test
    fun marketのURLから取り出す() {
        assertEquals("com.example.booster", ShareParser.extractPackageName("market://details?id=com.example.booster"))
    }

    @Test
    fun パッケージ名だけ() {
        assertEquals("com.example.booster", ShareParser.extractPackageName("com.example.booster"))
        assertEquals("com.example.booster", ShareParser.extractPackageName(" com.example.booster "))
    }

    @Test
    fun 共有の文面の途中のURLから取り出す() {
        val text = "\"フリーコイン無限ゲット\" をチェック https://play.google.com/store/apps/details?id=jp.example.freecoin"
        assertEquals("jp.example.freecoin", ShareParser.extractPackageName(text))
        // URL の直後に句読点や改行が続く
        assertEquals("jp.example.freecoin", ShareParser.extractPackageName("見て下さい：https://play.google.com/store/apps/details?id=jp.example.freecoin。"))
        assertEquals("jp.example.freecoin", ShareParser.extractPackageName("https://play.google.com/store/apps/details?id=jp.example.freecoin\n他の文"))
    }

    @Test
    fun 取り出せない例() {
        assertNull(ShareParser.extractPackageName(""))
        assertNull(ShareParser.extractPackageName("   "))
        assertNull(ShareParser.extractPackageName("フリーコイン無限ゲット")) // アプリ名だけ
        assertNull(ShareParser.extractPackageName("https://play.google.com/store/apps/details")) // id がない
        assertNull(ShareParser.extractPackageName("https://play.google.com/store/apps/details?hl=ja")) // id がない
        assertNull(ShareParser.extractPackageName("https://play.google.com/store/apps/details?id=")) // id が空
        assertNull(ShareParser.extractPackageName("https://play.google.com/store/apps/details?id=freecoin")) // 形が違う
        assertNull(ShareParser.extractPackageName("https://example.com/details?id=jp.example.freecoin")) // Google Play 以外
        assertNull(ShareParser.extractPackageName("com.example.booster を入れないで")) // パッケージ名の後に文がある
        assertNull(ShareParser.extractPackageName("https://play.google.com/store/apps/dev?id=123456")) // 開発者のページ
    }

    @Test
    fun 入力欄に入れるURL() {
        val text = "\"天気ナビ\" をチェック https://play.google.com/store/apps/details?id=jp.example.tenki"
        assertEquals("https://play.google.com/store/apps/details?id=jp.example.tenki", ShareParser.findUrl(text))
        assertNull(ShareParser.findUrl("jp.example.tenki"))
    }

    // ---- アプリ名の取り出し ----

    @Test
    fun 引用符やかぎかっこで囲まれたアプリ名を取り出す() {
        val url = "https://play.google.com/store/apps/details?id=jp.example.freecoin"
        assertEquals("フリーコイン無限ゲット", ShareParser.extractAppName("\"フリーコイン無限ゲット\" をチェック $url"))
        assertEquals("フリーコイン無限ゲット", ShareParser.extractAppName("「フリーコイン無限ゲット」をチェック\n$url"))
        assertEquals("Free Coin Unlimited", ShareParser.extractAppName("Check out \"Free Coin Unlimited\" $url"))
        assertEquals("Free Coin", ShareParser.extractAppName("Check out “Free Coin” $url"))
        // URL の後ろの引用符は使わない
        assertNull(ShareParser.extractAppName("$url \"別の文\""))
    }

    @Test
    fun 件名をアプリ名に使う() {
        val url = "https://play.google.com/store/apps/details?id=jp.example.freecoin"
        assertEquals("フリーコイン無限ゲット", ShareParser.extractAppName(url, subject = "フリーコイン無限ゲット"))
        assertEquals("フリーコイン無限ゲット", ShareParser.extractAppName(url, subject = "「フリーコイン無限ゲット」をチェック"))
        // 件名が URL やパッケージ名なら使わない
        assertNull(ShareParser.extractAppName(url, subject = url))
        assertNull(ShareParser.extractAppName(url, subject = "jp.example.freecoin"))
    }

    @Test
    fun アプリ名が取り出せない例() {
        assertNull(ShareParser.extractAppName("https://play.google.com/store/apps/details?id=jp.example.freecoin"))
        assertNull(ShareParser.extractAppName("jp.example.freecoin"))
        assertNull(ShareParser.extractAppName("\"\" をチェック https://play.google.com/store/apps/details?id=jp.example.freecoin"))
        assertNull(ShareParser.extractAppName(""))
    }
}
