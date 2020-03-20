package me.fzzy.announcebot

import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class StreamScanner(game: String) {

    var activeStreams = hashMapOf<Long, Stream>()
    private var pagination: String? = null
    var gameId: Int = 0
    private val file = File("$game.json")

    init {
        try {
            gameId = getGameIdRequest(game)
        } catch (e: Exception) {
            log.error("Could not retrieve game id from twitch, is the game name exactly as it is on the twitch directory?")
            e.printStackTrace()
        }
        if (gameId != 0) {
            log.info("$game id found: $gameId")
            loadStreams()
            scheduler.schedulePeriodically({
                nextPage()
            }, 10, 60, TimeUnit.SECONDS)
        }
    }

    private fun nextPage() {
        var json: JSONObject? = null
        try {
            if (pagination == null) markInactiveStreams()
            json = getStreamsRequest(gameId, pagination)
            val array = json.getJSONArray("data")

            for (i in 0 until array.length()) {
                val stream = getStream(array.getJSONObject(i))
                stream.online()
                activeStreams[stream.twitchId] = stream
                saveStreams()
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
        for ((userId, stream) in activeStreams) {
            if ((!activeStreams.containsKey(userId) || !stream.tags.contains(speedRunTagId))) {
                stream.offline()
                saveStreams()
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

    fun getGameIdRequest(name: String): Int {
        val uriBuilder = URIBuilder("https://api.twitch.tv/helix/games").addParameter("name", name)
        val uri = uriBuilder.build()
        val http = HttpGet(uri)
        http.addHeader("Client-ID", config.twitchToken)
        val response = HttpClients.createDefault().execute(http)
        val json = JSONObject(EntityUtils.toString(response.entity))
        return json.getJSONArray("data").getJSONObject(0).getInt("id")
    }

    fun getStreamByTwitchId(twitchId: Long): Stream? {
        return activeStreams[twitchId]
    }

    fun getStream(userId: Long): Stream? {
        for ((username, id) in streamingPresenceUsers) {
            if (id == userId) return getStream(username)
        }
        return null
    }

    fun getStream(username: String): Stream? {
        for ((_, stream) in activeStreams) {
            if (stream.username.toLowerCase() == username.toLowerCase()) return stream
        }
        return null
    }

    fun getStream(title: String, username: String, twitchId: Long, tags: ArrayList<String>): Stream {
        return if (activeStreams.containsKey(twitchId)) {
            val stream = activeStreams[twitchId]!!
            stream.title = title
            stream.tags = tags
            stream.username = username
            stream
        } else {
            val stream = Stream(title, username, twitchId, tags)
            activeStreams[twitchId] = stream
            stream
        }
    }

    fun getStream(json: JSONObject): Stream {
        val title = json.getString("title")
        val username = json.getString("user_name")
        val twitchId = json.getLong("user_id")
        val tags = arrayListOf<String>()
        try {
            val jsonTags = json.getJSONArray("tag_ids")
            for (i in 0 until jsonTags.length()) {
                tags.add(jsonTags.getString(i))
            }
        } catch (e: Exception) {
        }
        return getStream(title, username, twitchId, tags)
    }

    private fun saveStreams() {
        val bufferWriter = BufferedWriter(FileWriter(file.absoluteFile, false))
        val save = JSONObject(gson.toJson(activeStreams))
        bufferWriter.write(save.toString(2))
        bufferWriter.close()
    }

    fun loadStreams() {
        val token = object : TypeToken<HashMap<Long, Stream>>() {}
        if (file.exists()) activeStreams = gson.fromJson(JsonReader(InputStreamReader(file.inputStream())), token.type)
    }
}