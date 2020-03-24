package me.fzzy.announcebot

import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader

class Stream constructor(
    var title: String,
    var username: String,
    var twitchId: Long,
    var tags: ArrayList<String>
) {

    private var online: Boolean = false
    private var offlineTimestamp: Long = 0

    var verifiedOnline = false

    fun offline() {
        if (!online) return
        online = false
        offlineTimestamp = System.currentTimeMillis()
        log.info("$username is no longer live.")
        handleRole(username)
    }

    fun online() {
        verifiedOnline = true
        if (online) return
        if (!tags.contains(speedRunTagId)) return
        online = true
        log.info("$username is now live.")
        broadcastStream()
        handleRole(username)
    }

    private fun broadcastStream() {
        if (!isOnline()) return
        if (timeSinceOffline() < config.broadcastCooldownMinutes * 60 * 1000) {
            log.info("$username broadcast ignored because of broadcast cooldown")
            return
        }
        if (blacklist.contains(username.toLowerCase())) {
            log.info("$username went live but was ignored because they are on the blacklist.")
            return
        }
        val msg = "$title https://www.twitch.tv/$username"
        val channel = cli.getTextChannelById(config.broadcastChannelId)?:return
        channel.sendMessage(msg).queue()
        offlineTimestamp = System.currentTimeMillis()
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