package me.fzzy.announcebot

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.json.JSONArray
import org.json.JSONObject
import reactor.core.scheduler.Schedulers
import reactor.util.Loggers
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.HashMap
import kotlin.system.exitProcess

lateinit var cli: JDA
val log = Loggers.getLogger("StreamAnnounce")

val gson = Gson()

const val speedRunTagId = "7cefbf30-4c3e-4aa7-99cd-70aabb662f27"

class Config {
    val broadcastChannelId = 0L
    val games = arrayListOf<String>()
    val discordToken = ""
    val twitchToken = ""
    val broadcastCooldownMinutes = 60
    val presence = ""
    val presenceType = 0
    val liveRoleId = 0L
    val liveRoleRequirementRoleId = 0L
}

lateinit var config: Config

var blacklist = arrayListOf<String>()
val scheduler = Schedulers.elastic()
val streamingPresenceUsers: HashMap<String, Long> = hashMapOf()

fun main() {
    fun configRequest() {
        log.error("===================================")
        log.error("For this bot to work you must set values in config.json, instructions can be found here: https://github.com/Fzzy2j/StreamAnnounceBot")
        log.error("===================================")
        exitProcess(0)
    }

    val configFile = File("config.json")
    if (!configFile.exists()) {
        val bufferWriter = BufferedWriter(FileWriter(configFile.absoluteFile, false))
        val save = JSONObject(gson.toJson(Config()))
        bufferWriter.write(save.toString(2))
        bufferWriter.close()

        configRequest()
    }

    val blacklistFile = File("blacklist.json")
    if (blacklistFile.exists()) {
        val token = object : TypeToken<List<String>>() {}
        blacklist = gson.fromJson(JsonReader(InputStreamReader(blacklistFile.inputStream())), token.type)
    }

    try {
        config = gson.fromJson(JsonReader(InputStreamReader(configFile.inputStream())), Config::class.java)
        if (config.broadcastChannelId == 0L || config.games.isEmpty() || config.discordToken.isBlank() || config.twitchToken.isBlank())
            configRequest()
    } catch (e: Exception) {
        log.error("Could not load json from config file. Did you edit it improperly?")
        e.printStackTrace()
        exitProcess(0)
    }

    for (name in config.games) {
        StreamScanner(name)
    }

    val presence = if (config.presence.isEmpty()) null else when (config.presenceType) {
        0 -> Activity.playing(config.presence)
        1 -> Activity.listening(config.presence)
        2 -> Activity.watching(config.presence)
        else -> null
    }
    cli = JDABuilder()
        .setActivity(presence)
        .setToken(config.discordToken)
        .addEventListeners(PresenceListener, BlacklistCommand)
        .build()
}

fun handleRole(userId: Long) {
    if (config.liveRoleId == 0L) return

    val isLive = Stream.getStream(userId)?.isOnline() ?: false
    val isStreaming = streamingPresenceUsers.containsValue(userId)
    val channel = cli.getGuildChannelById(config.broadcastChannelId) ?: return
    val member = channel.guild.getMemberById(userId) ?: return

    if (isLive && isStreaming) {
        val roles = member.roles
        for (role in roles) {
            if (config.liveRoleRequirementRoleId != 0L && role.idLong == config.liveRoleRequirementRoleId) {
                channel.guild.modifyMemberRoles(
                    member,
                    Collections.singletonList(channel.guild.getRoleById(config.liveRoleId)),
                    null
                ).queue()
            }
        }
    } else {
        channel.guild.modifyMemberRoles(
            member,
            null,
            Collections.singletonList(channel.guild.getRoleById(config.liveRoleId))
        ).queue()
    }
}

fun handleRole(username: String) {
    if (streamingPresenceUsers.containsKey(username)) handleRole(streamingPresenceUsers[username]!!)
}

fun saveBlacklist() {
    val file = File("blacklist.json")
    val bufferWriter = BufferedWriter(FileWriter(file.absoluteFile, false))
    val save = JSONArray(gson.toJson(blacklist))
    bufferWriter.write(save.toString(2))
    bufferWriter.close()
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