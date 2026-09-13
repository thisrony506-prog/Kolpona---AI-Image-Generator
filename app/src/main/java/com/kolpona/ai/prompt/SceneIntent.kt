package com.kolpona.ai.prompt

enum class SceneKind {
    PERSON, ANIMAL, LANDSCAPE, PRODUCT, FASHION, CINEMATIC, ANIME, FOOD, VEHICLE, ARCHITECTURE, ABSTRACT, GENERAL
}

data class SceneIntent(
    val kind: SceneKind,
    val sitting: Boolean,
    val standing: Boolean,
    val walking: Boolean,
    val running: Boolean,
    val flying: Boolean,
    val flowingWater: Boolean,
    val landscape: Boolean,
    val raining: Boolean,
    val night: Boolean,
    val day: Boolean,
    val sunset: Boolean,
    val culturalRegion: Boolean,
    val quotedText: List<String>,
    val userCamera: String?,
    val colors: List<String>
) {
    companion object {
        fun extract(original: String, normalized: String): SceneIntent {
            val text = "$original $normalized".lowercase()
            val quoted = QUOTE_REGEX.findAll(original).map { it.groupValues[1].ifBlank { it.groupValues[2] } }
                .filter { it.isNotBlank() }
                .toList()
            val colors = COLOR_WORDS.filter { text.contains(it) }
            val kind = when {
                containsAny(text, "anime", "manga") -> SceneKind.ANIME
                containsAny(
                    text, "pant", "pants", "shirt", "shoe", "dress", "sari", "lungi", "panjabi",
                    "kameez", "kurta", "fashion", "jacket", "jeans", "clothing", "suit", "hoodie"
                ) -> SceneKind.FASHION
                containsAny(text, "watch", "bag", "product", "bottle") &&
                    !containsAny(text, "wearing", "girl", "woman", "man", "boy") -> SceneKind.PRODUCT
                containsAny(
                    text, "woman", "man", "girl", "boy", "person", "people", "face", "portrait",
                    "মাইয়া", "মেয়ে", "মহিলা", "পুরুষ", "ছেলে", "পোলা", "ladki", "ladka", "mujer", "homme"
                ) -> SceneKind.PERSON
                containsAny(text, "cat", "dog", "bird", "tiger", "horse", "animal", "kitten", "বিড়াল", "কুকুর", "chat", "gato") ->
                    SceneKind.ANIMAL
                containsAny(
                    text, "waterfall", "mountain", "river", "village", "landscape", "forest",
                    "cloud", "beach", "ঝর্ণা", "পাহাড়", "নদী", "গ্রাম", "samudra", "laut"
                ) -> SceneKind.LANDSCAPE
                containsAny(text, "castle", "building", "architecture", "temple", "mosque") -> SceneKind.ARCHITECTURE
                containsAny(text, "car", "bus", "train", "truck", "bike") -> SceneKind.VEHICLE
                containsAny(text, "food", "meal", "rice", "curry") -> SceneKind.FOOD
                containsAny(text, "abstract") -> SceneKind.ABSTRACT
                containsAny(text, "cinematic", "film still") -> SceneKind.CINEMATIC
                else -> SceneKind.GENERAL
            }
            val camera = when {
                containsAny(text, "drone") -> "smooth cinematic drone shot"
                containsAny(text, "push-in", "push in", "সামনে যাবে") -> "slow cinematic camera push-in"
                containsAny(text, "zoom") -> "gentle cinematic zoom"
                containsAny(text, "tracking") -> "tracking shot"
                containsAny(text, "pan") -> "slow cinematic pan"
                else -> null
            }
            return SceneIntent(
                kind = kind,
                sitting = containsAny(text, "sit", "seated", "বসে", "বইসা", "बैठा", "duduk"),
                standing = containsAny(text, "stand", "দাঁড়", "দাড়া", "खड़ा"),
                walking = containsAny(text, "walk", "হাঁট", "चल", "jalan"),
                running = containsAny(text, "run", "দৌড়", "दौड़", "lari"),
                flying = containsAny(text, "fly", "flying", "উড়", "उड़"),
                flowingWater = containsAny(text, "waterfall", "flowing", "ঝর্ণা", "জলপ্রপাত"),
                landscape = kind == SceneKind.LANDSCAPE,
                raining = containsAny(text, "rain", "বৃষ্টি", "barish"),
                night = containsAny(text, "night", "midnight", "রাত"),
                day = containsAny(text, "daytime", "morning", "noon", "সকাল", "দুপুর"),
                sunset = containsAny(text, "sunset", "dusk", "সূর্যাস্ত"),
                culturalRegion = containsAny(
                    text, "bangladesh", "bengali", "khulna", "dhaka", "sari", "lungi",
                    "panjabi", "rickshaw", "eid", "pohela", "বাংলা", "খুলনা", "গ্রাম"
                ),
                quotedText = quoted,
                userCamera = camera,
                colors = colors
            )
        }

        private fun containsAny(text: String, vararg words: String): Boolean =
            words.any { text.contains(it) }

        private val QUOTE_REGEX = Regex("[\"“]([^\"”]+)[\"”]|'([^']+)'")
        private val COLOR_WORDS = listOf(
            "red", "blue", "green", "yellow", "pink", "black", "white", "gold",
            "orange", "purple", "brown", "silver", "লাল", "কালো", "সাদা", "নীল", "গোলাপি"
        )
    }
}

data class OptimizedGeneration(
    val prompt: String,
    val negativePrompt: String?,
    val kind: SceneKind
)
