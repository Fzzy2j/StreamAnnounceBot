package me.fzzy.announcebot

import sx.blah.discord.Discord4J
import sx.blah.discord.api.ClientBuilder
import sx.blah.discord.api.IDiscordClient
import sx.blah.discord.api.events.EventSubscriber
import sx.blah.discord.handle.impl.events.ReadyEvent

lateinit var twitchToken: String
lateinit var cli: IDiscordClient

var broadcastChannelId = 288805361341693952

fun main(args: Array<String>) {
    val discordToken = args[0]
    twitchToken = args[1]

    cli = ClientBuilder().withToken(discordToken).build()
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
    }
}