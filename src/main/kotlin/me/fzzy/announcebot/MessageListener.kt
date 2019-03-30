package me.fzzy.announcebot

import sx.blah.discord.api.events.EventSubscriber
import sx.blah.discord.handle.impl.events.guild.channel.message.MentionEvent
import sx.blah.discord.util.RequestBuffer

object MessageListener {

    @EventSubscriber
    fun onMention(event: MentionEvent) {
        if (event.guild.owner.longID == event.author.longID) {
            broadcastChannelId = event.channel.longID
            dataNode.getNode("broadcastChannelId").value = broadcastChannelId
            dataManager.save(dataNode)
            RequestBuffer.request { event.channel.sendMessage("I'll broadcast streams in this channel from now on") }
        }
    }

}