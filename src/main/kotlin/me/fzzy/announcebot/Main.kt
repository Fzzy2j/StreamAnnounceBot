package me.fzzy.announcebot

import ninja.leaping.configurate.ConfigurationNode
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.hocon.HoconConfigurationLoader
import ninja.leaping.configurate.loader.ConfigurationLoader
import sx.blah.discord.Discord4J
import sx.blah.discord.api.ClientBuilder
import sx.blah.discord.api.IDiscordClient
import sx.blah.discord.api.events.EventSubscriber
import sx.blah.discord.handle.impl.events.ReadyEvent
import sx.blah.discord.handle.obj.ActivityType
import sx.blah.discord.handle.obj.StatusType
import sx.blah.discord.util.RequestBuffer
import java.io.File

lateinit var twitchToken: String
lateinit var cli: IDiscordClient

var broadcastChannelId: Long? = null

lateinit var dataManager: ConfigurationLoader<CommentedConfigurationNode>
lateinit var dataNode: ConfigurationNode
const val DATA_DIR: String = "data/"

fun main(args: Array<String>) {
    val discordToken = args[0]
    twitchToken = args[1]

    File(DATA_DIR).mkdir()
    val dataFile = File(DATA_DIR + File.separator + "data.conf")

    dataManager = HoconConfigurationLoader.builder().setPath(dataFile.toPath()).build()
    dataNode = dataManager.load()

    broadcastChannelId = dataNode.getNode("broadcastChannelId").long

    cli = ClientBuilder().withToken(discordToken).build()
    cli.dispatcher.registerListener(MessageListener)
    cli.dispatcher.registerListener(ReadyListener)

    Discord4J.LOGGER.info("Logging in.")
    cli.login()

    StreamScanner.start()
}

object ReadyListener {
    @EventSubscriber
    fun onReady(event: ReadyEvent) {
        Discord4J.LOGGER.info("list of current guilds:")
        for (guild in event.client.guilds) {
            Discord4J.LOGGER.info(guild.name)
        }
        RequestBuffer.request { cli.changePresence(StatusType.ONLINE, ActivityType.PLAYING, "Protocol 3") }
    }
}