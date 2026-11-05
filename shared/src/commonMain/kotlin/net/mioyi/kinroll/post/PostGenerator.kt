package net.mioyi.kinroll.post

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.mioyi.kinroll.data.PhotoDao
import net.mioyi.kinroll.data.PhotoEntity
import net.mioyi.kinroll.llm.LlmService
import net.mioyi.kinroll.ml.clip.PhotoCategory
import kotlin.time.Instant

enum class PostStyle { XIAOHONGSHU, MOMENTS, WEIBO, INSTAGRAM }

class PostPrompt(val system: String, val user: String)

class PostGenerator(private val photoDao: PhotoDao, private val llm: LlmService) {
    suspend fun build(ids: List<String>, style: PostStyle, notes: String, zh: Boolean): PostPrompt {
        val photos = mutableMapOf<String, PhotoEntity>()
        for (batch in ids.distinct().chunked(500)) {
            for (photo in photoDao.getByIds(batch)) photos[photo.id] = photo
        }
        val platform = when (style) {
            PostStyle.XIAOHONGSHU -> if (zh) "小红书" else "Xiaohongshu"
            PostStyle.MOMENTS -> if (zh) "朋友圈" else "WeChat Moments"
            PostStyle.WEIBO -> if (zh) "微博" else "Weibo"
            PostStyle.INSTAGRAM -> "Instagram"
        }
        val instructions = when (style) {
            PostStyle.XIAOHONGSHU -> if (zh) "第一行是带 emoji 的标题，正文分段并适量使用 emoji，结尾 3–5 个 #话题，总长 150–300 字。"
                else "Start with a title containing an emoji. Use short paragraphs and a moderate number of emoji. End with 3–5 hashtags. Write 150–300 words."
            PostStyle.MOMENTS -> if (zh) "1–3 句口语化短文，可用 1–2 个 emoji，不加话题标签。"
                else "Write 1–3 conversational sentences, optionally with 1–2 emoji. Do not add hashtags."
            PostStyle.WEIBO -> if (zh) "140 字以内，可带 1–3 个 #话题#。"
                else "Use no more than 140 characters, optionally with 1–3 #topic# tags."
            PostStyle.INSTAGRAM -> if (zh) "简短配文，空一行后附 5–10 个 hashtag。"
                else "Write a short caption, followed by a blank line and 5–10 hashtags."
        }
        val system = if (zh) "你是一名社交媒体文案助手。根据用户提供的照片信息，写一篇${platform}帖文。只输出帖文正文。$instructions"
            else "You are a social media copywriting assistant. Write a $platform post based on the photo information provided by the user. Write in English. Output only the post text. $instructions"
        val zone = TimeZone.currentSystemDefault()
        val lines = ids.distinct().mapNotNull(photos::get).mapIndexed { index, photo ->
            val parts = mutableListOf<String>()
            photo.takenAt?.let {
                val date = Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date.toString()
                parts += if (zh) "拍摄于 $date" else "Taken on $date"
            }
            val category = PhotoCategory.entries.firstOrNull { it.name == photo.category }
            category?.let { parts += if (zh) "类别：${it.promptLabel(true)}" else "Category: ${it.promptLabel(false)}" }
            photo.ocrText?.takeIf { it.isNotBlank() }?.let {
                val text = it.take(120).replace('\n', ' ').replace('\r', ' ')
                parts += if (zh) "图中文字：$text" else "Text in image: $text"
            }
            "${index + 1}. " + parts.joinToString(if (zh) "，" else ", ")
        }.toMutableList()
        if (notes.isNotBlank()) lines += if (zh) "补充说明：$notes" else "Additional notes: $notes"
        return PostPrompt(system, lines.joinToString("\n"))
    }

    fun generate(ids: List<String>, style: PostStyle, notes: String, zh: Boolean): Flow<String> = flow {
        val prompt = build(ids, style, notes, zh)
        emitAll(llm.generate(prompt.system, prompt.user, maxTokens = 512, params = LlmService.postParams()))
    }
}

private fun PhotoCategory.promptLabel(zh: Boolean): String = when (this) {
    PhotoCategory.PEOPLE -> if (zh) "人物" else "People"
    PhotoCategory.CHILD -> if (zh) "儿童" else "Children"
    PhotoCategory.PET -> if (zh) "宠物" else "Pets"
    PhotoCategory.FOOD -> if (zh) "美食" else "Food"
    PhotoCategory.LANDSCAPE -> if (zh) "风景" else "Landscape"
    PhotoCategory.CITY -> if (zh) "城市" else "City"
    PhotoCategory.NIGHT -> if (zh) "夜景" else "Night"
    PhotoCategory.PLANT -> if (zh) "植物" else "Plants"
    PhotoCategory.VEHICLE -> if (zh) "车辆" else "Vehicles"
    PhotoCategory.DOCUMENT -> if (zh) "文档" else "Documents"
    PhotoCategory.RECEIPT -> if (zh) "票据" else "Receipts"
    PhotoCategory.ART -> if (zh) "艺术" else "Art"
    PhotoCategory.OBJECT -> if (zh) "物品" else "Objects"
    PhotoCategory.OTHER -> if (zh) "其他" else "Other"
}
