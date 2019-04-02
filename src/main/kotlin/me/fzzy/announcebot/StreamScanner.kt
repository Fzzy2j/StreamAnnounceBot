package me.fzzy.announcebot

import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.json.JSONObject
import sx.blah.discord.Discord4J
import sx.blah.discord.util.RequestBuffer

object StreamScanner : Thread() {

    var activeStreams = arrayListOf<Stream>()

    private val speedRunTagId = "7cefbf30-4c3e-4aa7-99cd-70aabb662f27"
    private val tf2Id = "489201"

    override fun run() {
        while (true) {
            try {
                Thread.sleep(60 * 1000)

                var json = getTitanfallRequest()
                var array = json.getJSONArray("data")

                // Storing all the pages of streams
                val requestStreams = hashMapOf<Long, Stream>()

                for (i in 0 until array.length())
                    requestStreams[array.getJSONObject(i).getLong("user_id")] = Stream(array.getJSONObject(i))

                while (json.getJSONObject("pagination").has("cursor")) {
                    json = getTitanfallRequest(json.getJSONObject("pagination").getString("cursor"))
                    array = json.getJSONArray("data")
                    for (i in 0 until array.length())
                        requestStreams[array.getJSONObject(i).getLong("user_id")] = Stream(array.getJSONObject(i))
                }

                // Remove inactive streams
                val iterator = activeStreams.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (!requestStreams.containsKey(entry.userId) || !entry.tags.contains(speedRunTagId)) {
                        Discord4J.LOGGER.info("${entry.username} is no longer live.")
                        iterator.remove()
                    }
                }

                // Add new streams and broadcast them
                for (requestStream in requestStreams.values) {
                    if (!activeStreams.contains(requestStream)) {
                        if (requestStream.tags.size == 0 || !requestStream.tags.contains(speedRunTagId)) continue

                        activeStreams.add(requestStream)

                        val msg = "${requestStream.title} https://www.twitch.tv/${requestStream.username}"
                        RequestBuffer.request { cli.getChannelByID(broadcastChannelId).sendMessage(msg) }
                        Discord4J.LOGGER.info("${requestStream.username} is now live.")
                    }
                }

                // Save the active streams
                saveStreams(activeStreams)
            } catch (e: Exception) {
                Discord4J.LOGGER.info("Error while trying to scan streams:")
                e.printStackTrace()
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