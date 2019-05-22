package me.fzzy.announcebot

import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.json.JSONObject
import sx.blah.discord.Discord4J
import sx.blah.discord.handle.obj.IChannel
import sx.blah.discord.util.RequestBuffer

object StreamScanner : Thread() {

    var activeStreams = arrayListOf<Stream>()
    var broadcastChannel: IChannel? = null

    override fun run() {
        broadcastChannel = cli.getChannelByID(config.broadcastChannelId)
        if (broadcastChannel == null) {
            Discord4J.LOGGER.error("Could not retrieve broadcast channel, did you enter the correct id in the config?")
            System.exit(0)
            return
        }
        val gameId = try {
            getGameIdRequest(config.game)
        } catch(e: Exception) {
            Discord4J.LOGGER.error("Could not retrieve game id from twitch, is the game name exactly as it is on the twitch directory?")
            e.printStackTrace()
            System.exit(0)
            return
        }
        Discord4J.LOGGER.info("Game id found: $gameId")
        Discord4J.LOGGER.info("Scanning has commenced.")
        while (true) {
            try {
                var json = getStreamsRequest(gameId)
                var array = json.getJSONArray("data")

                // Storing all the pages of streams
                val requestStreams = hashMapOf<Long, Stream>()


                for (i in 0 until array.length())
                    requestStreams[array.getJSONObject(i).getLong("user_id")] = Stream(array.getJSONObject(i))

                while (json.getJSONObject("pagination").has("cursor")) {
                    sleep(3000)
                    json = getStreamsRequest(gameId, json.getJSONObject("pagination").getString("cursor"))
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
                        RequestBuffer.request { broadcastChannel?.sendMessage(msg) }
                        Discord4J.LOGGER.info("${requestStream.username} is now live.")
                    }
                }

                // Save the active streams
                saveStreams(activeStreams)

                sleep(60 * 1000)
            } catch (e: Exception) {
                Discord4J.LOGGER.info("Error while trying to scan streams:")
                e.printStackTrace()
            }
        }
    }

    fun getGameIdRequest(name: String): Int {
        val uriBuilder = URIBuilder("https://api.twitch.tv/helix/games").addParameter("name", name)
        val uri = uriBuilder.build()
        val http = HttpGet(uri)
        http.addHeader("Client-ID", config.twitchToken)
        val response = HttpClients.createDefault().execute(http)
        val json = JSONObject(EntityUtils.toString(response.entity))
        return json.getJSONArray("data").getJSONObject(0).getInt("id")
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

    fun getTagRequest(broadcasterId: Long): JSONObject {
        val uriBuilder = URIBuilder("https://api.twitch.tv/helix/streams/tags").addParameter(
            "broadcaster_id",
            broadcasterId.toString()
        )
        val uri = uriBuilder.build()
        val http = HttpGet(uri)
        http.addHeader("Client-ID", config.twitchToken)
        val response = HttpClients.createDefault().execute(http)
        return JSONObject(EntityUtils.toString(response.entity))
    }

}