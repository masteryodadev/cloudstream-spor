package com.sport.netspor

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

class NetSpor : MainAPI() {
    override var mainUrl = "https://netspor.co"
    override var name = "NetSpor"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    // Sitenin script dosyasındaki dizi adı -> ana sayfa başlığı
    private val sections = listOf(
        "channels" to "Kanallar",
        "futbolMatches" to "Futbol",
        "basketbolMatches" to "Basketbol",
        "voleybolMatches" to "Voleybol",
        "tenisMatches" to "Tenis",
    )

    data class Item(val title: String, val channelId: String)

    private val arrayRegex = Regex("""const\s+(\w+)\s*=\s*\[(.*?)];""", RegexOption.DOT_MATCHES_ALL)
    private val objectRegex = Regex("""\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
    private val fieldRegex = Regex(""""?(\w+)"?\s*:\s*(?:"([^"]*)"|(true|false))""")

    // Maç ve kanal listesi ana sayfadaki harici bir script'te tutuluyor; adresi değişebildiği için sayfadan bulunur.
    private suspend fun fetchDataScript(): String? {
        val doc = app.get("$mainUrl/").document
        for (src in doc.select("script[src]").map { it.absUrl("src") }) {
            if (src.isBlank() || src.contains("cloudflare") || src.contains("googletagmanager")) continue
            val text = runCatching { app.get(src, referer = "$mainUrl/").text }.getOrNull() ?: continue
            if (text.contains("const channels")) return text
        }
        return null
    }

    private suspend fun fetchSections(): List<Pair<String, List<Item>>> {
        val script = fetchDataScript() ?: return emptyList()
        val arrays = arrayRegex.findAll(script).associate { it.groupValues[1] to it.groupValues[2] }
        return sections.map { (arrayName, title) ->
            val items = objectRegex.findAll(arrays[arrayName].orEmpty()).mapNotNull { obj ->
                val fields = fieldRegex.findAll(obj.groupValues[1])
                    .associate { it.groupValues[1] to it.groupValues[2].ifEmpty { it.groupValues[3] } }
                val name = fields["title"]?.trim().orEmpty()
                val channelId = fields["url"]?.substringAfter("id=", "").orEmpty()
                if (name.isEmpty() || channelId.isEmpty()) return@mapNotNull null
                val time = fields["time"].orEmpty()
                val live = if (fields["live"] == "true") "[CANLI] " else ""
                val label = if (time.isEmpty()) name else "$live$time · $name"
                Item(label, channelId)
            }.distinct().toList()
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
        return newLiveStreamLoadResponse(item.title, url, item.channelId)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Oynatıcı sayfası yayın sunucularını sıralı bir liste olarak tutuyor; her biri ayrı kaynak olarak sunulur.
        val eventPage = app.get("$mainUrl/event.html?id=$data", referer = "$mainUrl/").text
        val baseList = Regex("""baseurls\s*=\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            .find(eventPage)?.groupValues?.get(1) ?: return false
        val baseUrls = Regex("""["'](https?://[^"']+)["']""").findAll(baseList).map { it.groupValues[1] }.toList()
        if (baseUrls.isEmpty()) return false

        baseUrls.forEachIndexed { index, baseUrl ->
            callback(
                newExtractorLink(name, "$name ${index + 1}", "$baseUrl$data.m3u8", ExtractorLinkType.M3U8) {
                    referer = "$mainUrl/"
                    quality = Qualities.Unknown.value
                }
            )
        }
        return true
    }
}
