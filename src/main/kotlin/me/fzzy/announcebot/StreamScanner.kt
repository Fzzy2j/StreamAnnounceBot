package me.fzzy.announcebot

import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class StreamScanner {

    var activeStreams = hashMapOf<Long, Stream>()
    private var pagination: String? = null

    init {
        Stream.loadStreams()
        scheduler.schedulePeriodically({
            nextPage()
        }, 10, 60, TimeUnit.SECONDS)
    }

    private fun nextPage() {
        var json: JSONObject? = null
        try {
            if (pagination == null) markInactiveStreams()
            json = getStreamsRequest(gameId, pagination)
            val array = json.getJSONArray("data")

            for (i in 0 until array.length()) {
                val stream = Stream.getStream(array.getJSONObject(i))
                stream.online()
                activeStreams[stream.twitchId] = stream
            }

            pagination = if (json.getJSONObject("pagination")!!.has("cursor")) {
                json.getJSONObject("pagination").getString("cursor")
            } else null
        } catch (e: Exception) {
            if (json != null) {
                log.error(json.toString(2))
            }
            log.error("Error while trying to scan streams:")
            e.printStackTrace()
        }
    }

    fun markInactiveStreams() {
        for ((userId, stream) in Stream.streams) {
            if ((!activeStreams.containsKey(userId) || !stream.tags.contains(speedRunTagId))) {
                stream.offline()
            }
        }
        activeStreams.clear()
    }

    fun getStreamsRequest(gameId: Int, pagination: String? = null): JSONObject {
        val uriBuilder = URIBuilder("https://api.twitch.tv/helix/streams").addParameter("game_id", gameId.toString())
        if (pagination != null) uriBuilder.addParameter("after", pagination)
        val uri = uriBuilder.build()
        val http = HttpGet(uri)
        http.addHeader("Client-ID", config.twitchToken)
        val response = HttpClients.createDefault().execute(http)
        return JSONObject(EntityUtils.toString(response.entity))
    }
}