package me.fzzy.announcebot

import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.json.JSONObject
import sx.blah.discord.Discord4J
import sx.blah.discord.util.RequestBuffer

object StreamScanner : Thread() {

    private var activeStreams = hashMapOf<Long, JSONObject>()

    private val speedRunTagId = "7cefbf30-4c3e-4aa7-99cd-70aabb662f27"
    private val tf2Id = "489201"

    override fun run() {
        while (true) {
            Thread.sleep(3 * 60 * 1000)

            var json = getTitanfallRequest()
            var array = json.getJSONArray("data")

            // Storing all the pages of streams
            val requestStreams = hashMapOf<Long, JSONObject>()

            for (i in 0 until array.length())
                requestStreams[array.getJSONObject(i).getLong("user_id")] = array.getJSONObject(i)

            while (json.getJSONObject("pagination").has("cursor")) {
                json = getTitanfallRequest(json.getJSONObject("pagination").getString("cursor"))
                array = json.getJSONArray("data")
                for (i in 0 until array.length())
                    requestStreams[array.getJSONObject(i).getLong("user_id")] = array.getJSONObject(i)
            }

            // Remove inactive streams
            val iterator = activeStreams.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (!requestStreams.containsKey(entry.key)) {
                    Discord4J.LOGGER.info("${entry.value.getString("user_name")} is no longer live.")
                    iterator.remove()
                }
            }

            // Add new streams and broadcast them
            for (requestStream in requestStreams.values) {
                if (!activeStreams.contains(requestStream.getLong("user_id"))) {
                    try {
                        val tags = requestStream.getJSONArray("tag_ids")
                        var isSpeedrun = false
                        for (i in 0 until tags.length()) {
                            if (tags.getString(i) == speedRunTagId) isSpeedrun = true
                        }
                        if (!isSpeedrun) continue
                    } catch (e: Exception) {
                        continue
                    }

                    activeStreams[requestStream.getLong("user_id")] = requestStream

                    val msg =
                        "${requestStream.getString("title")} https://www.twitch.tv/${requestStream.getString("user_name")}"
                    RequestBuffer.request { cli.getChannelByID(broadcastChannelId).sendMessage(msg) }
                    Discord4J.LOGGER.info("${requestStream.getString("user_name")} is now live.")
                }
            }
        }
    }

    fun getTitanfallRequest(pagination: String? = null): JSONObject {
        val uriBuilder = URIBuilder("https://api.twitch.tv/helix/streams").addParameter("game_id", tf2Id)
        if (pagination != null) uriBuilder.addParameter("after", pagination)
        val uri = uriBuilder.build()
        val http = HttpGet(uri)
        http.addHeader("Client-ID", twitchToken)
        val response = HttpClients.createDefault().execute(http)
        return JSONObject(EntityUtils.toString(response.entity))
    }

    fun getTagRequest(broadcasterId: Long): JSONObject {
        val uriBuilder = URIBuilder("https://api.twitch.tv/helix/streams/tags").addParameter(
            "broadcaster_id",
            broadcasterId.toString()
        )
        val uri = uriBuilder.build()
        val http = HttpGet(uri)
        http.addHeader("Client-ID", twitchToken)
        val response = HttpClients.createDefault().execute(http)
        return JSONObject(EntityUtils.toString(response.entity))
    }

}