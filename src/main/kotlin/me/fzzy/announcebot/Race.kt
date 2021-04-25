package me.fzzy.announcebot

import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import me.fzzy.announcebot.util.FileHelper
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.sourceforge.tess4j.Tesseract
import org.json.JSONArray
import java.awt.Color
import java.awt.Image
import java.awt.Rectangle
import java.awt.image.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.*

class Race {

    val times = hashMapOf<Long, Long>()
    var date = 0L

    companion object : ListenerAdapter() {
        val currentRaces = hashMapOf<Long, Race>()
        val pendingTimes = hashMapOf<Long, Long>()
        val watchingForTime = hashMapOf<Long, Long>()
        var pastRaces = arrayListOf<Race>()

        init {
            val racesFile = File("pastRaces.json")
            if (racesFile.exists()) {
                val token = object : TypeToken<List<Race>>() {}
                pastRaces = gson.fromJson(JsonReader(InputStreamReader(racesFile.inputStream())), token.type)
            }
        }

        fun savePastRaces() {
            val file = File("pastRaces.json")
            val bufferWriter = BufferedWriter(FileWriter(file.absoluteFile, false))
            val save = JSONArray(gson.toJson(pastRaces))
            bufferWriter.write(save.toString(2))
            bufferWriter.close()
        }

        override fun onMessageReceived(event: MessageReceivedEvent) {
            cli.getTextChannelById(config.broadcastChannelId) ?: return

            if (event.author.isBot) return
            val member = event.member ?: return
            val channel = event.channel

            if (member.hasPermission(Permission.MANAGE_SERVER) || cli.retrieveApplicationInfo()
                    .complete().owner.idLong == member.idLong
            ) {
                if (event.message.contentRaw == "go epic mode") {
                    currentRaces[channel.idLong] = Race()
                    channel.sendMessage("epic mode commencing").queue()
                }
                if (event.message.contentRaw == "epic ending" && currentRaces.containsKey(channel.idLong)) {
                    val race = currentRaces[channel.idLong]!!
                    race.date = System.currentTimeMillis()
                    pastRaces.add(race)
                    savePastRaces()

                    val embed = EmbedBuilder()
                    embed.setTitle("Race results")
                    embed.setColor(Color.CYAN)
                    var description = ""
                    val sortedMap = race.times.toSortedMap(compareBy { race.times[it] })
                    var place = 0
                    for ((id, raceTime) in sortedMap) {
                        val m = event.guild.getMemberById(id) ?: continue

                        val start = when (place++) {
                            0 -> "\uD83E\uDD47"
                            1 -> "\uD83E\uDD48"
                            2 -> "\uD83E\uDD49"
                            else -> "${place + 1}th"
                        }
                        description += "$start ${m.asMention} - ${formatTime(raceTime)}\n"
                    }
                    embed.setDescription(description)
                    channel.sendMessage(embed.build()).queue()
                    currentRaces.remove(channel.idLong)
                }
            }

            if (currentRaces.containsKey(channel.idLong)) {
                if (watchingForTime.containsKey(event.author.idLong)) {
                    val time = decipherTime(event.message.contentRaw)
                    if (time > 0) {
                        currentRaces[channel.idLong]!!.times[event.author.idLong] = time
                        event.message.delete().queue()
                        channel.retrieveMessageById(watchingForTime[event.author.idLong]!!)
                            .queue { it.delete().queue() }
                    }
                }

                try {
                    val media = FileHelper.getMessageMedia(event.message)
                    if (media != null) {
                        val img = FileHelper.downloadTempFile(media)
                        if (img != null) {
                            var together = ""
                            for (i in 0..2) {
                                val text = crackImage(img, i)
                                together += "$text\n"
                            }
                            val time = decipherTime(together)
                            if (time == 0L) return
                            channel.sendMessage("${event.author.asMention} ${formatTime(time)} is the time i found, is that correct?")
                                .queue { msg ->
                                    run {
                                        msg.addReaction("✅").queue {
                                            msg.addReaction("❌").queue()
                                        }
                                        pendingTimes[msg.idLong] = time
                                    }
                                }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        override fun onGuildMessageReactionAdd(event: GuildMessageReactionAddEvent) {
            if (pendingTimes.containsKey(event.messageIdLong)) {
                event.channel.retrieveMessageById(event.messageIdLong).queue { msg ->
                    run {
                        val who = msg.mentionedMembers[0]
                        if (event.member.idLong == who.idLong) {
                            val race = currentRaces[event.channel.idLong]!!
                            val time = pendingTimes[event.messageIdLong]!!
                            pendingTimes.remove(event.messageIdLong)

                            if (event.reactionEmote.emoji == "✅") {
                                race.times[who.idLong] = time
                                msg.delete().queue()
                            } else if (event.reactionEmote.emoji == "❌") {
                                event.channel.sendMessage("${who.asMention} What was your time?")
                                    .queue { msg -> watchingForTime[who.idLong] = msg.idLong }
                                msg.delete().queue()
                            }
                        }
                    }
                }
            }
        }

        fun resize(img: BufferedImage, newW: Int, newH: Int): BufferedImage {
            val tmp: Image = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH)
            val dimg = BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB)
            val g2d = dimg.createGraphics()
            g2d.drawImage(tmp, 0, 0, null)
            g2d.dispose()
            return dimg
        }

        fun formatTime(time: Long): String {
            val hours = TimeUnit.MILLISECONDS.toHours(time)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(time) - TimeUnit.HOURS.toMinutes(
                TimeUnit.MILLISECONDS.toHours(
                    time
                )
            )
            val seconds = TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(
                TimeUnit.MILLISECONDS.toMinutes(
                    time
                )
            )

            val m = String.format("%02d", minutes)
            val s = String.format("%02d", seconds)

            return "${if (hours > 0) "$hours:" else ""}$m:$s"
        }

        fun decipherTime(text: String): Long {
            var max = 0L
            for (line in text.split("\n")) {
                for (space in line.split(" ")) {
                    if (space.length < 3) continue
                    val colonSplit = space.split(":")
                    if (colonSplit.size != 2 && colonSplit.size != 3) continue

                    try {
                        val seconds = colonSplit[colonSplit.size - 1].split(".")[0].toInt()
                        if (seconds > 59) continue
                        val minutes = colonSplit[colonSplit.size - 2].toInt()
                        if (minutes > 59) continue
                        val hours = if (colonSplit.size == 2) 0 else colonSplit[colonSplit.size - 3].toInt()

                        var millis = 0
                        if (colonSplit[colonSplit.size - 1].contains(".")) {
                            try {
                                millis = colonSplit[colonSplit.size - 1].split(".")[1].toInt()
                            } catch (e: java.lang.NumberFormatException) {
                            }
                        }

                        val total = (hours * 60 * 60 * 1000) + (minutes * 60 * 1000) + (seconds * 1000) + millis
                        if (total > max) max = total.toLong()
                    } catch (e: NumberFormatException) {
                    }
                }
            }
            return max
        }

        fun crackImage(file: File, sector: Int): String {
            val tess = Tesseract()
            var img = ImageIO.read(file)
            img = alterImage(img)
            val sectors = getSectors(img)
            val i = if (sector >= sectors.size) sectors.size - 1 else sector
            if (sectors.size <= i) return ""
            val crop = sectors[i]
            img = img.getSubimage(crop.x, crop.y, crop.width, crop.height)
            img = resize(img, 350, (img.height * 350) / img.width)
            img = addMargin(img, 20)

            /*val bs = ByteArrayOutputStream()
            ImageIO.write(img, "png", bs)
            bs.flush()
            channel.sendFile(bs.toByteArray(), "bruh.png").queue()*/

            tess.setTessVariable("user_defined_dpi", "144")
            tess.setTessVariable("tessedit_char_whitelist", "0123456789:.")
            tess.setLanguage("eng")
            tess.setDatapath(File("tessdata").toString())

            tess.setPageSegMode(11)
            val pass1 = tess.doOCR(img)
            tess.setPageSegMode(7)
            val pass2 = tess.doOCR(img)

            return "$pass1\n$pass2"
        }

        fun addMargin(img: BufferedImage, margin: Int): BufferedImage {
            val fullImage = BufferedImage(img.width + margin * 2, img.height + margin * 2, BufferedImage.TYPE_INT_RGB)

            val g = fullImage.graphics
            g.color = Color.BLACK
            g.fillRect(0, 0, fullImage.width, fullImage.height)
            g.drawImage(img, margin, margin, null)
            g.dispose()

            return fullImage
        }

        fun getSectors(img: BufferedImage): List<Rectangle> {
            val sectors = arrayListOf<Rectangle>()

            var currentSectorY = -1
            var currentSector = 0
            var currentSectorStart = img.width
            var currentSectorEnd = 0
            for (j in 0 until img.height) {
                var opaqueCount = 0
                var conseqCount = 0
                for (i in 0 until img.width) {
                    val c = Color(img.getRGB(i, j))
                    val lum = (c.red + c.green + c.blue) / 3.0 / 255.0
                    if (lum > 0.5) {
                        opaqueCount++
                        conseqCount++
                        if (conseqCount > img.width / 90) {
                            if (i < currentSectorStart) currentSectorStart = i
                            if (i > currentSectorEnd) currentSectorEnd = i
                        }
                    } else conseqCount = 0
                }
                if (opaqueCount > img.width / 20.0) {
                    if (currentSectorY == -1) currentSectorY = j
                    currentSector++
                } else {
                    if (currentSectorY != -1) {
                        val rect = Rectangle(
                            currentSectorStart,
                            currentSectorY,
                            currentSectorEnd - currentSectorStart,
                            currentSector
                        )
                        sectors.add(rect)
                    }
                    currentSectorY = -1
                    currentSector = 0
                    currentSectorStart = img.width
                    currentSectorEnd = 0
                }
            }

            return sectors.sortedByDescending { sector -> sector.height }
        }

        fun alterImage(img: BufferedImage): BufferedImage {
            val imageAltered = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
            val colorMap = hashMapOf<Int, Int>()

            for (i in 0 until img.width) {
                for (j in 0 until img.height) {
                    val c = Color(img.getRGB(i, j))
                    val count = colorMap.getOrDefault(c.rgb, 0)
                    colorMap[c.rgb] = count + 1
                }
            }

            var commonColor = Color(0)
            var commonColorAmt = 0
            for ((rgb, count) in colorMap) {
                if (count > commonColorAmt) {
                    commonColorAmt = count
                    commonColor = Color(rgb)
                }
            }

            var maxDistance = 0.0

            for (i in 0 until img.width) {
                for (j in 0 until img.height) {
                    val c = Color(img.getRGB(i, j))

                    val rDistance = commonColor.red - c.red.toDouble()
                    val gDistance = commonColor.green - c.green.toDouble()
                    val bDistance = commonColor.blue - c.blue.toDouble()
                    val distance = sqrt(rDistance.pow(2) + gDistance.pow(2) + bDistance.pow(2))

                    if (distance > maxDistance) maxDistance = distance
                }
            }

            for (i in 0 until img.width) {
                for (j in 0 until img.height) {
                    val c = Color(img.getRGB(i, j))

                    val r = c.red
                    val g = c.green
                    val b = c.blue

                    val rDistance = commonColor.red - r.toDouble()
                    val gDistance = commonColor.green - g.toDouble()
                    val bDistance = commonColor.blue - b.toDouble()
                    val distance = sqrt(rDistance.pow(2) + gDistance.pow(2) + bDistance.pow(2))

                    val normalized = min(1.0, ((distance / maxDistance) * 2))

                    var rgb = (normalized * 255).roundToInt() - max(0, (300 - (r + g + b)))
                    rgb = max(min(rgb, 255), 0)
                    val color = Color(rgb, rgb, rgb)

                    imageAltered.setRGB(i, j, color.rgb)
                }
            }
            return imageAltered
        }
    }

}