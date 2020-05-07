package me.fzzy.announcebot

import com.github.scribejava.apis.LinkedInApi20
import com.github.scribejava.apis.TwitterApi
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import org.apache.http.client.methods.CloseableHttpResponse
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
import java.net.URI
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
    val twitchClientId = ""
    val twitchClientSecret = ""
    val broadcastCooldownMinutes = 60
    val presence = ""
    val presenceType = 0
    val liveRoleId = 0L
    val liveRoleRequirementRoleId = 0L
    val scanIntervalSeconds = 30L
}

lateinit var config: Config

var blacklist = arrayListOf<String>()
val scheduler = Schedulers.elastic()
val streamingPresenceUsers: HashMap<String, Long> = hashMapOf()
val scanners = arrayListOf<StreamScanner>()

var oauthToken: OAuth2AccessToken? = null

fun twitchRequest(url: URI): JSONObject {
    if (oauthToken == null) requestNewToken()

    fun request(): CloseableHttpResponse {
        val http = HttpGet(url)
        val authorization = String.format("Bearer %s", oauthToken!!.accessToken)
        http.addHeader("Authorization", authorization)
        http.addHeader("Client-ID",config.twitchClientId)

        return HttpClients.createDefault().execute(http)
    }
    var response = request()
    if (response.statusLine.statusCode == 401) {
        log.info("Requesting new oauth token.")
        requestNewToken()
        response = request()
    }
    return JSONObject(EntityUtils.toString(response.entity))
}

private fun requestNewToken() {
    val service = ServiceBuilder(config.twitchClientId).apiSecret(config.twitchClientSecret)
        .build(TwitchApi.instance)

    oauthToken = service.accessTokenClientCredentialsGrant
}

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
        if (config.broadcastChannelId == 0L || config.games.isEmpty() || config.discordToken.isBlank() || config.twitchClientId.isBlank() || config.twitchClientSecret.isBlank())
            configRequest()
    } catch (e: Exception) {
        log.error("Could not load json from config file. Did you edit it improperly?")
        e.printStackTrace()
        exitProcess(0)
    }

    for (name in config.games) {
        scanners.add(StreamScanner(name))
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

    var isLive = false
    for (scanner in scanners) {
        val stream = scanner.getStream(userId)
        if (stream != null) isLive = true
    }
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