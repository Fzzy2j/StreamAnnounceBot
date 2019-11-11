package me.fzzy.announcebot

import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.user.update.GenericUserPresenceEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

object PresenceListener : ListenerAdapter() {

    override fun onGenericUserPresence(event: GenericUserPresenceEvent) {

        for (activity in event.member.activities) {
            if (activity.type == Activity.ActivityType.STREAMING) {
                val split = (activity.url ?: continue).split("/")
                val username = split[split.size - 1].toLowerCase()
                streamingPresenceUsers[username] = event.member.idLong
                handleRole(event.member.idLong)
                return
            }
        }
        val iter = streamingPresenceUsers.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            if (p.value == event.member.idLong) iter.remove()
        }
        handleRole(event.member.idLong)
    }

}