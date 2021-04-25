package me.fzzy.announcebot

import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.tsunderebug.speedrun4j.game.Category
import com.tsunderebug.speedrun4j.game.Leaderboard
import com.tsunderebug.speedrun4j.game.run.PlacedRun
import net.dv8tion.jda.api.entities.Role
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class RoleUpdater {

    fun start() {
        scheduler.schedulePeriodically({
            try {
                updateRolesTick()
            } catch (e: Exception) {
                e.printStackTrace();
            }
        }, 10, 10, TimeUnit.SECONDS)
    }

    private var srcDiscordCache: HashMap<String, Long> = if (File("srcCache.json").exists()) {
        val token = object : TypeToken<HashMap<String, Long>>() {}
        gson.fromJson(JsonReader(InputStreamReader(File("srcCache.json").inputStream())), token.type)
    } else hashMapOf()

    fun saveSrcDiscordCache() {
        val cacheFile = File("srcCache.json")
        val bufferWriter = BufferedWriter(FileWriter(cacheFile.absoluteFile, false))
        val save = JSONObject(gson.toJson(srcDiscordCache))
        bufferWriter.write(save.toString(2))
        bufferWriter.close()
    }

    fun getSrcDiscord(user: String): Long? {
        if (srcDiscordCache.containsKey(user)) return srcDiscordCache[user]!!

        val u = URL("https://www.speedrun.com/user/$user")
        val conn = u.openConnection() as HttpURLConnection
        val lines: List<String>
        try {
            val r = InputStreamReader(conn.inputStream)
            lines = r.readLines()
            r.close()
        } catch (e: FileNotFoundException) {
            return null
        }
        val regex = Regex("(?<=src=\"/images/socialmedia/discord.png\" data-id=\").*?(?=\")")
        for (s in lines) {
            val reg = regex.find(s) ?: continue
            val tag = reg.groupValues[0]
            val channel = cli.getTextChannelById(config.broadcastChannelId) ?: return null
            if (!tag.contains("#")) return null
            val member = channel.guild.getMemberByTag(tag.split("#")[0], tag.split("#")[1]) ?: return null
            srcDiscordCache[user] = member.idLong
            saveSrcDiscordCache()
            return member.idLong
        }
        return null
    }

    private var iterator: Iterator<PlacedRun>? = null
    private var runnerRole: Role? = null

    private fun updateRolesTick() {
        val channel = cli.getTextChannelById(config.broadcastChannelId) ?: return
        if (runnerRole == null) runnerRole = channel.guild.getRoleById(config.runnerRole)

        if (iterator == null || !iterator!!.hasNext()) {
            val board = Leaderboard.forCategory(Category.fromID("wdmq1xe2"), "?var-5lyj7d2l=jq6yp5j1")
            iterator = board.runs.iterator()
            channel.guild.loadMembers()
        } else {
            val run = iterator!!.next()
            if (runnerRole == null && run.place > config.topRoles.keys.max()!!) {
                iterator = null
                return
            }
            val srcUser = run.run.players[0].name
            val discord = getSrcDiscord(srcUser) ?: return
            val member = channel.guild.getMemberById(discord) ?: return
            val currentRoles = member.roles

            if (runnerRole != null && !currentRoles.contains(runnerRole)) {
                channel.guild.addRoleToMember(member, runnerRole!!).queue()
                log.info("${runnerRole!!.name} role added to ${member.effectiveName}")
            }

            val roles = config.topRoles.keys.toIntArray()
            for (i in 1 until roles.size) {
                val role = channel.guild.getRoleById(config.topRoles[i]!!) ?: continue
                if (run.place in roles[i]..roles[i - 1]) {
                    if (!currentRoles.contains(role)) {
                        channel.guild.addRoleToMember(member, role).queue()
                        log.info("top $i role added to ${member.effectiveName}")
                    }
                } else {
                    if (currentRoles.contains(role)) {
                        channel.guild.removeRoleFromMember(member, role).queue()
                        log.info("top $i role removed from ${member.effectiveName}")
                    }
                }
            }
        }
    }
}