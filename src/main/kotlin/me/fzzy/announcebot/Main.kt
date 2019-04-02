package me.fzzy.announcebot

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import sx.blah.discord.Discord4J
import sx.blah.discord.api.ClientBuilder
import sx.blah.discord.api.IDiscordClient
import sx.blah.discord.api.events.EventSubscriber
import sx.blah.discord.handle.impl.events.ReadyEvent
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader

lateinit var twitchToken: String
lateinit var cli: IDiscordClient

var broadcastChannelId = 288805361341693952

val gson = Gson()

val streamsFile = File("streams.json")

fun main(args: Array<String>) {
    val discordToken = args[0]
    twitchToken = args[1]

    cli = ClientBuilder().withToken(discordToken).build()
    cli.dispatcher.registerListener(ReadyListener)

    Discord4J.LOGGER.info("Logging in.")
    cli.login()
    val token = object : TypeToken<List<Stream>>() {}
    if (streamsFile.exists())
        StreamScanner.activeStreams =
            gson.fromJson(JsonReader(InputStreamReader(streamsFile.inputStream())), token.type)
    StreamScanner.start()
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
    }
}