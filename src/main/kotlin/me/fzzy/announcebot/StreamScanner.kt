package me.fzzy.announcebot

import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.util.Snowflake
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.json.JSONObject

object StreamScanner : Thread() {

    var activeStreams = arrayListOf<Stream>()
    var broadcastChannel: TextChannel? = null

    override fun run() {
        broadcastChannel = cli.getChannelById(Snowflake.of(config.broadcastChannelId)).block() as TextChannel?
        if (broadcastChannel == null) {
            log.error("Could not retrieve broadcast channel, did you enter the correct id in the config?")
            System.exit(0)
            return
        }
        val gameId = try {
            getGameIdRequest(config.game)
        } catch(e: Exception) {
            log.error("Could not retrieve game id from twitch, is the game name exactly as it is on the twitch directory?")
            e.printStackTrace()
            System.exit(0)
            return
        }
        log.info("Game id found: $gameId")
        log.info("Scanning has commenced.")
        while (true) {
            var json: JSONObject? = null
            try {
                json = getStreamsRequest(gameId)
                var array = json.getJSONArray("data")

                // Storing all the pages of streams
                val requestStreams = hashMapOf<Long, Stream>()


                for (i in 0 until array.length())
                    requestStreams[array.getJSONObject(i).getLong("user_id")] = Stream(array.getJSONObject(i))

                while (json!!.getJSONObject("pagination")!!.has("cursor")) {
                    sleep(30 * 1000)
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
                        log.info("${entry.username} is no longer live.")
                        iterator.remove()
                    }
                }

                // Add new streams and broadcast them
                for (requestStream in requestStreams.values) {
                    if (!activeStreams.contains(requestStream)) {
                        if (requestStream.tags.size == 0 || !requestStream.tags.contains(speedRunTagId)) continue

                        activeStreams.add(requestStream)

                        val msg = "${requestStream.title} https://www.twitch.tv/${requestStream.username}"
                        broadcastChannel?.createMessage(msg)?.block()
                        log.info("${requestStream.username} is now live.")
                    }
                }

                // Save the active streams
                saveStreams(activeStreams)

                sleep(60 * 1000 * 3)
            } catch (e: Exception) {
                if (json != null) {
                    log.error(json.toString(2))
                }
                log.error("Error while trying to scan streams:")
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