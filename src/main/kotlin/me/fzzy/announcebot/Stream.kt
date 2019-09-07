package me.fzzy.announcebot

import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import discord4j.core.`object`.entity.TextChannel
import discord4j.core.`object`.util.Snowflake
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader

class Stream private constructor(
    var title: String,
    var username: String,
    var twitchId: Long,
    var tags: ArrayList<String>
) {

    private var online: Boolean = false
    private var offlineTimestamp: Long = 0

    companion object {
        var streams = hashMapOf<Long, Stream>()

        fun getStream(twitchId: Long): Stream? {
            return streams[twitchId]
        }

        fun getStream(userId: Snowflake): Stream? {
            for ((username, id) in streamingPresenceUsers) {
                if (id == userId) return getStream(username)
            }
            return null
        }

        fun getStream(username: String): Stream? {
            for ((_, stream) in streams) {
                if (stream.username.toLowerCase() == username.toLowerCase()) return stream
            }
            return null
        }

        fun getStream(title: String, username: String, twitchId: Long, tags: ArrayList<String>): Stream {
            return if (streams.containsKey(twitchId))
                streams[twitchId]!!
            else {
                val stream = Stream(title, username, twitchId, tags)
                streams[twitchId] = stream
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

        private val file = File("streams.json")

        private fun saveStreams() {
            val bufferWriter = BufferedWriter(FileWriter(file.absoluteFile, false))
            val save = JSONObject(gson.toJson(streams))
            bufferWriter.write(save.toString(2))
            bufferWriter.close()
        }

        fun loadStreams() {
            val token = object : TypeToken<HashMap<Long, Stream>>() {}
            if (file.exists())
                streams = gson.fromJson(JsonReader(InputStreamReader(file.inputStream())), token.type)
        }
    }

    fun offline() {
        if (!online) return
        online = false
        offlineTimestamp = System.currentTimeMillis()
        log.info("$username is no longer live.")
        handleRole(username)
        saveStreams()
    }

    fun online() {
        if (online) return
        if (!tags.contains(speedRunTagId)) return
        online = true
        log.info("$username is now live.")
        broadcastStream()
        handleRole(username)
        saveStreams()
    }

    private fun broadcastStream() {
        if (!isOnline()) return
        if (timeSinceOffline() < config.broadcastCooldownMinutes * 60 * 1000) return
        if (blacklist.contains(username.toLowerCase())) {
            log.info("$username went live but was ignored because they are on the blacklist.")
            return
        }
        val msg = "$title https://www.twitch.tv/$username"
        cli.getChannelById(Snowflake.of(config.broadcastChannelId)).flatMap { channel ->
            (channel as TextChannel).createMessage(msg)
        }.subscribe()
    }

    fun isOnline(): Boolean {
        return online
    }

    fun timeSinceOffline(): Long {
        return System.currentTimeMillis() - offlineTimestamp
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Stream) return false
        return other.twitchId == this.twitchId
    }

    override fun hashCode(): Int {
        var result = username.hashCode()
        result = 31 * result + twitchId.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + online.hashCode()
        result = 31 * result + offlineTimestamp.hashCode()
        return result
    }

}