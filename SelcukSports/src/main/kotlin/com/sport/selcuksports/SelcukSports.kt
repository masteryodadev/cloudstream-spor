package com.sport.selcuksports

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class SelcukSports : MainAPI() {
    // Giriş sayfası. Asıl site alan adı sık değiştiği için her oturumda buradan çözülür.
    override var mainUrl = "https://www.selcuksportshd.is"
    override var name = "SelçukSports"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    private var siteUrl: String? = null

    // Sitedeki sekme id'si -> ana sayfa başlığı. tab4 (Çoklu Ekran) tab1'in kopyası olduğu için alınmaz.
    private val sections = listOf(
        "tab5" to "7/24 TV",
        "tab1" to "Futbol",
        "tab2" to "Basketbol",
        "tab3" to "Tenis",
    )

    data class Item(val title: String, val playerUrl: String)

    private suspend fun resolveSiteUrl(): String {
        siteUrl?.let { return it }
        val doc = app.get(mainUrl).document
        val buttons = doc.select("a.site-button")
        val link = buttons.firstOrNull { it.selectFirst("img[alt=Site Giriş]") != null }?.attr("href")
            ?: buttons.map { it.attr("href") }.firstOrNull { it.contains("selcuksports") }
            ?: throw ErrorLoadingException("SelçukSports site adresi bulunamadı")
        return link.trimEnd('/').also { siteUrl = it }
    }

    private suspend fun fetchSections(): List<Pair<String, List<Item>>> {
        val site = resolveSiteUrl()
        val doc = app.get("$site/", referer = "$mainUrl/").document
        return sections.map { (tabId, title) ->
            val items = doc.select("#$tabId a[data-url]").mapNotNull { a ->
                val name = a.selectFirst(".name")?.text()?.trim().orEmpty()
                val time = a.selectFirst(".time")?.text()?.trim().orEmpty()
                val playerUrl = a.attr("data-url").substringBefore('#')
                if (name.isEmpty() || playerUrl.isEmpty()) return@mapNotNull null
                val label = if (time.isEmpty() || time == "7/24") name else "$time · $name"
                Item(label, playerUrl)
            }.distinct()
            title to items
        }
    }

    private fun Item.toSearchResponse(): SearchResponse =
        newLiveSearchResponse(title, toJson(), TvType.Live)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val lists = fetchSections()
            .filter { it.second.isNotEmpty() }
            .map { (title, items) ->
                HomePageList(title, items.map { it.toSearchResponse() }, isHorizontalImages = true)
            }
        return newHomePageResponse(lists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> =
        fetchSections()
            .flatMap { it.second }
            .distinct()
            .filter { it.title.contains(query, ignoreCase = true) }
            .map { it.toSearchResponse() }

    override suspend fun load(url: String): LoadResponse {
        val item = parseJson<Item>(url)
        return newLiveStreamLoadResponse(item.title, url, item.playerUrl)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val site = resolveSiteUrl()
        val playerOrigin = Regex("""^https?://[^/]+""").find(data)?.value ?: return false
        val channelId = Regex("""[?&]id=([^&#]+)""").find(data)?.groupValues?.get(1) ?: return false

        // Yayın sunucusu oynatıcı sayfasında sabit yazılı ve değişebiliyor; her seferinde sayfadan okunur.
        val playerPage = app.get(data, referer = "$site/").text
        val baseUrl = Regex("""baseStreamUrl\s*=\s*['"]([^'"]+)['"]""")
            .find(playerPage)?.groupValues?.get(1) ?: return false

        callback(
            newExtractorLink(name, name, "$baseUrl$channelId/playlist.m3u8", ExtractorLinkType.M3U8) {
                referer = "$playerOrigin/"
                quality = Qualities.Unknown.value
                headers = mapOf("Origin" to playerOrigin)
            }
        )
        return true
    }
}
