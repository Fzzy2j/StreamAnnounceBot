package me.fzzy.announcebot

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.lang.StringBuilder

object BlacklistCommand : ListenerAdapter() {

    override fun onMessageReceived(event: MessageReceivedEvent) {

        val channel = cli.getTextChannelById(config.broadcastChannelId) ?: return
        val member = channel.guild.getMemberById(event.author.idLong) ?: return

        if (member.hasPermission(Permission.MANAGE_SERVER) || cli.retrieveApplicationInfo().complete().owner.idLong == member.idLong && !event.isFromGuild) {
            val content = event.message.contentRaw
            fun helpMsg() {
                event.channel.sendMessage(
                    "```md\n" +
                            "# blacklist add {username} - adds a twitch user to the blacklist\n" +
                            "# blacklist remove {username} - removes a twitch user from the blacklist\n" +
                            "# blacklist list - lists currently blacklisted twitch users\n" +
                            "```"
                ).queue()
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
                                event.channel.sendMessage("${args[2].toLowerCase()} added to the blacklist").queue()
                            } else event.channel.sendMessage("${args[2].toLowerCase()} is already on the blacklist!").queue()
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
                                event.channel.sendMessage("${args[2].toLowerCase()} removed from the blacklist").queue()
                            } else event.channel.sendMessage("${args[2].toLowerCase()} isnt on the blacklist!").queue()
                        } catch (e: IndexOutOfBoundsException) {
                            helpMsg()
                        }
                    }
                    if (args[1] == "list") {
                        val builder = StringBuilder("```\n")
                        for (banned in blacklist) {
                            builder.append("$banned\n")
                        }
                        event.channel.sendMessage(builder.append("```").toString()).queue()
                    }
                }
            }
        }
    }

}