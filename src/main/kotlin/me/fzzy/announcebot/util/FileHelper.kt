package me.fzzy.announcebot.util

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.TextChannel
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Files
import java.util.regex.Pattern
import javax.imageio.ImageIO

object FileHelper {

    val URL_PATTERN: Pattern = Pattern.compile("(?:^|[\\W])((ht|f)tp(s?):\\/\\/)"
            + "(([\\w\\-]+\\.){1,}?([\\w\\-.~]+\\/?)*"
            + "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]\\*$~@!:/{};']*)")

    fun downloadTempFile(url: URL): File? {
        val suffixFinder = url.toString().split(".")
        var suffix = ".${suffixFinder[suffixFinder.size - 1]}"
        if (suffix.length > 4) suffix = ".png"

        File("cache").mkdirs()
        val fileName = "cache/${System.currentTimeMillis()}.$suffix"
        try {
            val openConnection = url.openConnection()
            openConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11")
            openConnection.connect()

            val inputStream = BufferedInputStream(openConnection.getInputStream())
            val outputStream = BufferedOutputStream(FileOutputStream(fileName))

            for (out in inputStream.iterator()) {
                outputStream.write(out.toInt())
            }
            inputStream.close()
            outputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        return File(fileName)
    }

    fun createTempFile(file: File?): File? {
        if (file == null) return null
        val new = File("cache/${System.currentTimeMillis()}.${file.extension}")
        Files.copy(file.toPath(), new.toPath())
        return new
    }

    fun getFirstUrl(string: String): URL? {
        val matcher = URL_PATTERN.matcher(string)

        if (matcher.find()) {
            val url = string.substring(matcher.start(1), matcher.end())
            return URL(url)
        }
        return null
    }

    fun isMedia(url: URL?): Boolean {
        return try {
            val image = ImageIO.read(url)
            image != null
        } catch (e: Exception) {
            false
        }
    }

    fun getMessageMedia(message: Message): URL? {
        if (message.attachments.size == 1) {
            val attach = message.attachments.single()
            if (attach.isImage) return URL(attach.url)
        }
        val url = getFirstUrl(message.contentRaw)
        return if (url != null && isMedia(url)) url else null
    }
}