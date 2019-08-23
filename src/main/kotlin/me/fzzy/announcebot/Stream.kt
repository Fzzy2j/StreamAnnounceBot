package me.fzzy.announcebot

import org.json.JSONObject

class Stream {

    var username: String
    var userId: Long
    var tags: ArrayList<String>
    var title: String

    var offlineTimestamp: Long = -1

    constructor(title: String, username: String, userId: Long, tags: ArrayList<String>) {
        this.userId = userId
        this.username = username
        this.tags = tags
        this.title = title
    }

    constructor(json: JSONObject) {
        this.title = json.getString("title")
        this.username = json.getString("user_name")
        this.userId = json.getLong("user_id")
        this.tags = arrayListOf()

        try {
            val jsonTags = json.getJSONArray("tag_ids")
            for (i in 0 until jsonTags.length()) {
                tags.add(jsonTags.getString(i))
            }
        } catch (e: Exception) {
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Stream) return false
        return other.userId == this.userId
    }

}