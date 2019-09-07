package me.fzzy.announcebot

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.*
import discord4j.core.`object`.presence.Activity
import discord4j.core.`object`.presence.Presence
import discord4j.core.`object`.util.Permission
import discord4j.core.`object`.util.Snowflake
import discord4j.core.event.domain.PresenceUpdateEvent
import discord4j.core.event.domain.lifecycle.ReadyEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.json.JSONArray
import org.json.JSONObject
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import reactor.util.Logger
import reactor.util.Loggers
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.lang.RuntimeException
import java.lang.StringBuilder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

lateinit var cli: DiscordClient
val log: Logger = Loggers.getLogger("StreamAnnounce")

val gson = Gson()

const val speedRunTagId = "7cefbf30-4c3e-4aa7-99cd-70aabb662f27"

class Config {
    val broadcastChannelId = 0L
    val game = ""
    val discordToken = ""
    val twitchToken = ""
    val broadcastCooldownMinutes = 60
    val presence = ""
    val presenceType = 0
    val liveRoleId = 0L
    val liveRoleRequirementRoleId = 0L
}

lateinit var config: Config

var gameId: Int = 0
var blacklist = arrayListOf<String>()
val scheduler: Scheduler = Schedulers.elastic()
val streamingPresenceUsers: HashMap<String, Snowflake> = hashMapOf()

var scanner = StreamScanner()

fun main() {

    fun configReq() {

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

        configReq()
    }

    val blacklistFile = File("blacklist.json")
    if (blacklistFile.exists()) {
        val token = object : TypeToken<List<String>>() {}
        blacklist = gson.fromJson(JsonReader(InputStreamReader(blacklistFile.inputStream())), token.type)
    }

    try {
        config = gson.fromJson(JsonReader(InputStreamReader(configFile.inputStream())), Config::class.java)
        if (config.broadcastChannelId == 0L || config.game.isBlank() || config.discordToken.isBlank() || config.twitchToken.isBlank())
            configReq()
    } catch (e: Exception) {
        log.error("Could not load json from config file. Did you edit it improperly?")
        e.printStackTrace()
        exitProcess(0)
    }

    cli = DiscordClientBuilder(config.discordToken).build()

    gameId = try {
        getGameIdRequest(config.game)
    } catch (e: Exception) {
        log.error("Could not retrieve game id from twitch, is the game name exactly as it is on the twitch directory?")
        e.printStackTrace()
        exitProcess(0)
    }
    log.info("Game id found: $gameId")

    // Set presence
    val presence = when (config.presenceType) {
        0 -> Presence.online(Activity.playing(config.presence))
        1 -> Presence.online(Activity.listening(config.presence))
        2 -> Presence.online(Activity.watching(config.presence))
        else -> Presence.online()
    }
    cli.updatePresence(presence).block()

    // Keep track of users that have the streaming presence
    cli.eventDispatcher.on(PresenceUpdateEvent::class.java).subscribe { event ->
        if (event.current.activity.isPresent && event.current.activity.get().type == Activity.Type.STREAMING) {
            val split = event.current.activity.get().streamingUrl.get().split("/")
            val username = split[split.size - 1].toLowerCase()
            streamingPresenceUsers[username] = event.userId
        } else {
            val iter = streamingPresenceUsers.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                if (p.value == event.userId) iter.remove()
            }
        }
        handleRole(event.userId)
    }

    //Blacklist Command
    cli.eventDispatcher.on(MessageCreateEvent::class.java).subscribe { event ->
        run {
            val channel = (cli.getChannelById(Snowflake.of(config.broadcastChannelId)).block()!! as TextChannel)
            val member = try {
                channel.guild.block()!!.getMemberById(event.message.author.get().id).block()
            } catch (e: Exception) {
                null
            }

            if (member != null) {
                if (member.basePermissions.block()!!.contains(Permission.MANAGE_GUILD) || cli.applicationInfo.block()!!.ownerId == member.id && event.message.channel.block() is PrivateChannel) {
                    val content = event.message.content.orElse("")
                    fun helpMsg() {
                        event.message.channel.flatMap {
                            it.createMessage(
                                "```md\n" +
                                        "# blacklist add {username} - adds a twitch user to the blacklist\n" +
                                        "# blacklist remove {username} - removes a twitch user from the blacklist\n" +
                                        "# blacklist list - lists currently blacklisted twitch users\n" +
                                        "```"
                            )
                        }.block()
                    }
                    if (content.startsWith("blacklist")) {
                        val args = content.split(" ")
                        if (args.size == 1) {
                            helpMsg()
                        } else {
                            if (args[1] == "add") {
                                try {
                                    if (!blacklist.contains(args[2].toLowerCase())) {
                                        blacklist.add(args[2].toLowerCase())
                                        saveBlacklist()
                                        log.info("${args[2].toLowerCase()} was added to the blacklist")
                                        event.message.channel.flatMap { it.createMessage("${args[2].toLowerCase()} added to the blacklist") }
                                            .block()
                                    } else event.message.channel.flatMap { it.createMessage("${args[2].toLowerCase()} is already on the blacklist!") }.block()
                                } catch (e: IndexOutOfBoundsException) {
                                    helpMsg()
                                }
                            }
                            if (args[1] == "remove") {
                                try {
                                    if (blacklist.contains(args[2].toLowerCase())) {
                                        blacklist.remove(args[2].toLowerCase())
                                        saveBlacklist()
                                        log.info("${args[2].toLowerCase()} was removed from the blacklist")
                                        event.message.channel.flatMap { it.createMessage("${args[2].toLowerCase()} removed from the blacklist") }
                                            .block()
                                    } else event.message.channel.flatMap { it.createMessage("${args[2].toLowerCase()} isnt on the blacklist!") }.block()
                                } catch (e: IndexOutOfBoundsException) {
                                    helpMsg()
                                }
                            }
                            if (args[1] == "list") {
                                event.message.channel.flatMap {
                                    val builder = StringBuilder("```\n")
                                    for (banned in blacklist) {
                                        builder.append("$banned\n")
                                    }
                                    it.createMessage(builder.append("```").toString())
                                }.block()
                            }
                        }
                    }
                }
            }
        }
    }

    log.info("Logging in.")
    cli.login().block()
}

fun handleRole(userId: Snowflake) {
    if (config.liveRoleId == 0L) return

    val isLive = Stream.getStream(userId)?.isOnline() ?: false
    val isStreaming = streamingPresenceUsers.containsValue(userId)
    cli.getChannelById(Snowflake.of(config.broadcastChannelId)).flatMap { channel ->
        cli.getMemberById((channel as TextChannel).guildId, userId)
    }.onErrorResume { Mono.empty() }.subscribe { member ->
        if (member != null) {
            if (isLive && isStreaming) {
                val roles = member.roleIds
                if (config.liveRoleRequirementRoleId != 0L && roles.contains(Snowflake.of(config.liveRoleRequirementRoleId))) {
                    roles.add(Snowflake.of(config.liveRoleId))
                    member.edit { spec ->
                        spec.setRoles(roles)
                    }.subscribe()
                }
            } else {
                val roles = member.roleIds
                roles.remove(Snowflake.of(config.liveRoleId))
                member.edit { spec ->
                    spec.setRoles(roles)
                }.subscribe()
            }
        }
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

fun getGameIdRequest(name: String): Int {
    val uriBuilder = URIBuilder("https://api.twitch.tv/helix/games").addParameter("name", name)
    val uri = uriBuilder.build()
    val http = HttpGet(uri)
    http.addHeader("Client-ID", config.twitchToken)
    val response = HttpClients.createDefault().execute(http)
    val json = JSONObject(EntityUtils.toString(response.entity))
    return json.getJSONArray("data").getJSONObject(0).getInt("id")
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