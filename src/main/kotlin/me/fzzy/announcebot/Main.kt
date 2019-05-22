package me.fzzy.announcebot

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import org.json.JSONObject
import sx.blah.discord.Discord4J
import sx.blah.discord.api.ClientBuilder
import sx.blah.discord.api.IDiscordClient
import sx.blah.discord.api.events.EventSubscriber
import sx.blah.discord.handle.impl.events.ReadyEvent
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader

lateinit var cli: IDiscordClient

val gson = Gson()

val streamsFile = File("streams.json")

const val speedRunTagId = "7cefbf30-4c3e-4aa7-99cd-70aabb662f27"

class Config {
    val broadcastChannelId = 0L
    val game = ""
    val discordToken = ""
    val twitchToken = ""
}

lateinit var config: Config

fun main() {

    fun configReq() {
        Discord4J.LOGGER.error("===================================")
        Discord4J.LOGGER.error("For this bot to work you must set values in config.json, instructions can be found here: https://github.com/Fzzy2j/StreamAnnounceBot")
        Discord4J.LOGGER.error("===================================")
        System.exit(0)
    }

    val configFile = File("config.json")
    if (!configFile.exists()) {
        val bufferWriter = BufferedWriter(FileWriter(configFile.absoluteFile, false))
        val save = JSONObject(gson.toJson(Config()))
        bufferWriter.write(save.toString(2))
        bufferWriter.close()

        configReq()
    }

    try {
        config = gson.fromJson(JsonReader(InputStreamReader(configFile.inputStream())), Config::class.java)
        if (config.broadcastChannelId == 0L || config.game.isBlank() || config.discordToken.isBlank() || config.twitchToken.isBlank())
            configReq()
    } catch(e: Exception) {
        Discord4J.LOGGER.error("Could not load json from config file. Did you edit it improperly?")
        e.printStackTrace()
        System.exit(0)
    }

    cli = ClientBuilder().withToken(config.discordToken).build()
    cli.dispatcher.registerListener(ReadyListener)

    Discord4J.LOGGER.info("Logging in.")
    cli.login()
}

fun saveStreams(streams: ArrayList<Stream>) {
    val bufferWriter = BufferedWriter(FileWriter(streamsFile.absoluteFile, false))
    val save = gson.toJson(streams)
    bufferWriter.write(save)
    bufferWriter.close()
}

object ReadyListener {
    @EventSubscriber
    fun onReady(event: ReadyEvent) {
        Discord4J.LOGGER.info("list of current guilds:")
        for (guild in event.client.guilds) {
            Discord4J.LOGGER.info(guild.name)
        }

        Discord4J.LOGGER.info("Starting Scanner...")

        val token = object : TypeToken<List<Stream>>() {}
        if (streamsFile.exists())
            StreamScanner.activeStreams =
                gson.fromJson(JsonReader(InputStreamReader(streamsFile.inputStream())), token.type)
        StreamScanner.start()
    }
}