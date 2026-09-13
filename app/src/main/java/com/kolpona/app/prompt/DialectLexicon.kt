package com.kolpona.app.prompt

/**
 * Deterministic dialect / colloquial / mixed-language rewrite.
 * Longest phrase wins. Meaning is preserved; clothing and place names stay cultural.
 */
object DialectLexicon {
    private val phrases: List<Pair<String, String>> = listOf(
        "পহেলা বৈশাখ" to "Pohela Boishakh Bengali New Year",
        "পয়লা বৈশাখ" to "Pohela Boishakh Bengali New Year",
        "বাংলাদেশের গ্রামের রাস্তা" to "a rural village road in Bangladesh",
        "গ্রামের রাস্তার পাশে" to "beside a Bangladesh village road",
        "গ্রামের রাস্তা" to "Bangladesh village road",
        "নদীর ধারে দাঁড়ায় আছে" to "standing at a riverbank",
        "নদীর পাড়ে দাঁড়ায় আছে" to "standing at a riverbank",
        "খুলনার নদীর পাড়" to "the riverbank in Khulna, Bangladesh",
        "নদীর ধারে" to "at a Bangladesh riverbank",
        "নদীর পাড়" to "Bangladesh riverbank",
        "বৃষ্টির মধ্যে" to "in the rain",
        "rain এর মধ্যে" to "in the rain",
        "sunset এর সময়" to "at sunset",
        "sunset এর সময়" to "at sunset",
        "সূর্যাস্তের সময়" to "at sunset",
        "সূর্যাস্তের সময়" to "at sunset",
        "বাঙালি বিয়ে" to "Bengali wedding",
        "বাংলা বিয়ে" to "Bengali wedding",
        "ঈদের দিন" to "on Eid day",
        "সালোয়ার কামিজ" to "salwar kameez",
        "সালোয়ার কামীজ" to "salwar kameez",
        "কালো পাঞ্জাবি" to "black panjabi kurta",
        "black panjabi" to "black panjabi kurta",
        "লাল শাড়ি" to "red sari",
        "লাল শাড়ী" to "red sari",
        "গোলাপি জামা" to "pink kameez dress",
        "গোলাপী জামা" to "pink kameez dress",
        "দাঁড়াইয়া আছে" to "standing",
        "দাঁড়ায় আছে" to "standing",
        "দাড়াইয়া আছে" to "standing",
        "দাঁড়িয়ে আছে" to "standing",
        "বইসা আছে" to "sitting",
        "বসে আছে" to "sitting",
        "বসে আছে" to "sitting",
        "হাঁটতেছে" to "walking",
        "হাঁটছে" to "walking",
        "হাঁটতেছে" to "walking",
        "ধীরে ধীরে সামনে যাবে" to "slowly moving forward",
        "ক্যামেরা ধীরে ধীরে সামনে যাবে" to "slow cinematic camera push-in",
        "মেঘ নড়বে" to "clouds slowly moving",
        "একটা মাইয়া" to "a young Bengali woman",
        "একজন মাইয়া" to "a young Bengali woman",
        "একজন ছেলে" to "a young Bengali man",
        "একটা ছেলে" to "a young Bengali man",
        "একটা পোলা" to "a young Bengali boy",
        "লুঙ্গি পরা" to "wearing a lungi",
        "লুঙ্গি পইরা" to "wearing a lungi",
        "শাড়ি পইরা" to "wearing a sari",
        "শাড়ী পইরা" to "wearing a sari",
        "জামা পইরা" to "wearing a kameez dress",
        "পাঞ্জাবি পরে" to "wearing a panjabi kurta",
        "পাঞ্জাবি পইরা" to "wearing a panjabi kurta",
        "cinematic ছবি" to "cinematic image",
        "realistic photo" to "realistic photograph",
        "realistic ছবি" to "realistic photograph",
        "সুন্দর পাহাড়" to "beautiful mountains",
        "পাহাড়ের দৃশ্য" to "mountain landscape",
        "পাহাড়ের video" to "mountain video",
        "pink cafe তে" to "in a pink cafe",
        "ক্যাফেতে বসে আছে" to "sitting in a cafe",
        "ক্যাফেতে" to "in a cafe",
        "মাইয়া" to "young Bengali woman",
        "মেয়ে" to "young woman",
        "পোলা" to "Bengali boy",
        "ছেলে" to "young man",
        "গোলাপি" to "pink",
        "গোলাপী" to "pink",
        "পইরা" to "wearing",
        "পইরা" to "wearing",
        "শাড়ি" to "sari",
        "শাড়ী" to "sari",
        "লুঙ্গি" to "lungi",
        "পাঞ্জাবি" to "panjabi kurta",
        "জামা" to "kameez dress",
        "ওড়না" to "dupatta",
        "রিকশা" to "cycle rickshaw",
        "গ্রাম" to "Bangladesh village",
        "ঝর্ণা" to "waterfall",
        "জলপ্রপাত" to "waterfall",
        "পাহাড়" to "mountains",
        "মেঘ" to "clouds",
        "নদী" to "river",
        "বৃষ্টি" to "rain",
        "সূর্যাস্ত" to "sunset",
        "বিয়ের" to "wedding",
        "ঈদ" to "Eid",
        "ইদ" to "Eid",
        "খুলনা" to "Khulna, Bangladesh",
        "ঢাকা" to "Dhaka, Bangladesh",
        "বাংলাদেশ" to "Bangladesh",
        "বাঙালি" to "Bengali",
        "সুন্দর" to "beautiful",
        "যেখানে" to "where",
        "সময়" to "time",
        "সময়" to "time",
        "একটা" to "a",
        "একটি" to "a",
        "একজন" to "a",
        "ছবি বানাও" to "",
        "ভিডিও বানাও" to "",
        "বানাও" to "",
        "ছবি" to "image",
        "ভিডিও" to "video",
        "ladki" to "young woman",
        "ladka" to "young man",
        "kapde" to "clothes",
        "nadi" to "river",
        "pahad" to "mountains",
        "barish" to "rain",
        "shaadi" to "wedding"
    ).sortedByDescending { it.first.length }

    private val taskNoise = listOf(
        "create an image of", "create a video of", "generate an image of",
        "make a picture of", "make an image of", "please create",
        "create an image", "create a video", "generate an image",
        "make a photo", "make a picture"
    )

    fun rewrite(raw: String): String {
        var text = raw.trim()
        phrases.forEach { (from, to) ->
            text = text.replace(from, " $to ", ignoreCase = true)
        }
        taskNoise.forEach { noise ->
            text = text.replace(noise, " ", ignoreCase = true)
        }
        return text.replace(Regex("\\s+"), " ").trim().trim(',', ' ')
    }

    fun stillNeedsModel(original: String, rewritten: String): Boolean {
        if (LanguageScripts.nonLatinRatio(rewritten) >= 0.12f) return true
        if (LanguageScripts.isMixed(original)) return true
        if (rewritten.equals(original.trim(), ignoreCase = true) &&
            LanguageScripts.nonLatinRatio(original) >= 0.08f
        ) return true
        return false
    }
}

object LanguageScripts {
    fun nonLatinRatio(text: String): Float {
        var latin = 0
        var other = 0
        text.forEach { c ->
            when (c.code) {
                in 0x0980..0x09FF,
                in 0x0900..0x097F,
                in 0x0A00..0x0A7F,
                in 0x0A80..0x0AFF,
                in 0x0B00..0x0B7F,
                in 0x0B80..0x0BFF,
                in 0x0C00..0x0C7F,
                in 0x0C80..0x0CFF,
                in 0x0D00..0x0D7F,
                in 0x0600..0x06FF,
                in 0x0750..0x077F,
                in 0x0400..0x04FF,
                in 0x4E00..0x9FFF,
                in 0x3040..0x30FF,
                in 0xAC00..0xD7AF,
                in 0x0E00..0x0E7F,
                in 0x10A0..0x10FF -> other++
                in 0x0041..0x007A, in 0x00C0..0x024F -> latin++
            }
        }
        val total = latin + other
        return if (total == 0) 0f else other.toFloat() / total
    }

    fun isMixed(text: String): Boolean {
        val hasLatinWord = text.split(Regex("\\s+")).any { token ->
            token.any { it.code in 0x41..0x7A } && token.length >= 3
        }
        val hasOther = nonLatinRatio(text) >= 0.08f
        return hasLatinWord && hasOther
    }
}

object CulturalTerms {
    private val terms = listOf(
        "sari", "shaari", "lungi", "panjabi", "punjabi", "kurta", "salwar", "kameez",
        "dupatta", "rickshaw", "khulna", "dhaka", "bangladesh", "bengali", "bengal",
        "eid", "pohela boishakh", "pohela", "monsoon", "riverbank", "village"
    )

    fun extract(text: String): List<String> {
        val lower = text.lowercase()
        return terms.filter { lower.contains(it) }
    }

    fun ensure(english: String, original: String, rewritten: String): String {
        val needed = (extract(original) + extract(rewritten) + extract(english)).distinct()
        val lower = english.lowercase()
        val missing = needed.filter { term -> !lower.contains(term) }
        return if (missing.isEmpty()) english
        else english.trim().trimEnd(',') + ", " + missing.joinToString(", ")
    }
}
