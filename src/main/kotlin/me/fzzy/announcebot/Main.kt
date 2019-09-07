package me.fzzy.announcebot

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import discord4j.core.DiscordClient
import discord4j.core.DiscordClientBuilder
import discord4j.core.`object`.entity.Channel
import discord4j.core.`object`.entity.PrivateChannel
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.TextChannel
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

val streamsFile = File("streams.json")

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

var activeStreams = arrayListOf<Stream>()
val requestStreams = hashMapOf<Long, Stream>()

var pagination: String? = null

var blacklist = arrayListOf<String>()

val scheduler: Scheduler = Schedulers.elastic()

val streamingPresenceUsers: HashMap<String, Snowflake> = hashMapOf()

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

    log.info("Starting Scanner...")

    val token = object : TypeToken<List<Stream>>() {}
    if (streamsFile.exists())
        activeStreams =
            gson.fromJson(JsonReader(InputStreamReader(streamsFile.inputStream())), token.type)

    val gameId = try {
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
            val channel =
                (cli.getChannelById(Snowflake.of(config.broadcastChannelId)).block()!! as TextChannel)
            if (event.guildId == channel.guildId) {
                cli.getMemberById(channel.guildId, event.userId)
                    .flatMap { member ->
                        val roles = member.roleIds
                        roles.remove(Snowflake.of(config.liveRoleId))
                        member.edit { spec ->
                            spec.setRoles(roles)
                        }
                    }.subscribe()
            }
        }
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

    scheduler.schedulePeriodically({
        var json: JSONObject? = null
        try {
            json = getStreamsRequest(gameId, pagination)

            val array = json.getJSONArray("data")

            // Each run stores the next page in the requestStreams variable
            for (i in 0 until array.length())
                requestStreams[array.getJSONObject(i).getLong("user_id")] = Stream(array.getJSONObject(i))

            // Add new streams and broadcast them
            for (requestStream in requestStreams.values) {
                if (!activeStreams.contains(requestStream)) {
                    if (requestStream.tags.size == 0 || !requestStream.tags.contains(speedRunTagId)) continue

                    activeStreams.add(requestStream)

                    val msg = "${requestStream.title} https://www.twitch.tv/${requestStream.username}"
                    scheduler.schedule {
                        val channel =
                            (cli.getChannelById(Snowflake.of(config.broadcastChannelId)).block()!! as TextChannel)
                        if (blacklist.contains(requestStream.username.toLowerCase())) {
                            log.info("${requestStream.username} went live but was ignored because they are on the blacklist.")
                            requestStream.offlineTimestamp = -1
                        } else {
                            channel.createMessage(msg).block()
                            log.info("${requestStream.username} is now live.")
                            if (config.liveRoleId != 0L && streamingPresenceUsers.containsKey(requestStream.username.toLowerCase())) {
                                cli.getMemberById(
                                    channel.guildId,
                                    streamingPresenceUsers[requestStream.username.toLowerCase()]!!
                                ).subscribe { member ->
                                    val roles = member.roleIds
                                    if (roles.contains(Snowflake.of(config.liveRoleRequirementRoleId))) {
                                        roles.add(Snowflake.of(config.liveRoleId))
                                        member.edit { spec ->
                                            spec.setRoles(roles)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (json.getJSONObject("pagination")!!.has("cursor")) {
                // Scrolling through pages
                pagination = json.getJSONObject("pagination").getString("cursor")
            } else {
                // Last page of requests
                pagination = null

                // Mark inactive streams
                for (stream in activeStreams) {
                    if ((!requestStreams.containsKey(stream.userId) || !stream.tags.contains(speedRunTagId)) && stream.offlineTimestamp == -1L) {
                        log.info("${stream.username} is no longer live.")
                        stream.offlineTimestamp = System.currentTimeMillis()
                        if (config.liveRoleId != 0L && streamingPresenceUsers.containsKey(stream.username.toLowerCase())) {
                            val channel =
                                (cli.getChannelById(Snowflake.of(config.broadcastChannelId)).block()!! as TextChannel)
                            cli.getMemberById(channel.guildId, streamingPresenceUsers[stream.username.toLowerCase()]!!)
                                .flatMap { member ->
                                    val roles = member.roleIds
                                    roles.remove(Snowflake.of(config.liveRoleId))
                                    member.edit { spec ->
                                        spec.setRoles(roles)
                                    }
                                }.subscribe()
                        }
                    }
                }
                requestStreams.clear()
            }

            // Remove old inactive streams
            val iterator = activeStreams.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.offlineTimestamp == -1L) continue
                if (System.currentTimeMillis() - entry.offlineTimestamp > 1000 * 60 * config.broadcastCooldownMinutes) {
                    log.info("${entry.username} can now be announced again.")
                    iterator.remove()
                }
            }

            // Save the active streams
            saveStreams(activeStreams)
        } catch (e: Exception) {
            if (json != null) {
                log.error(json.toString(2))
            }
            log.error("Error while trying to scan streams:")
            e.printStackTrace()
        }
    }, 10, 60, TimeUnit.SECONDS)

    log.info("Logging in.")
    cli.login().block()
}

fun saveBlacklist() {
    val file = File("blacklist.json")
    val bufferWriter = BufferedWriter(FileWriter(file.absoluteFile, false))
    val save = JSONArray(gson.toJson(blacklist))
    bufferWriter.write(save.toString(2))
    bufferWriter.close()
}

fun saveStreams(streams: ArrayList<Stream>) {
    val bufferWriter = BufferedWriter(FileWriter(streamsFile.absoluteFile, false))
    val save = JSONArray(gson.toJson(streams))
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