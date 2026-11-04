package com.quinton.sandroid

import android.speech.tts.TextToSpeech
import java.util.Locale

enum class Engine {
    SAPI5, SAPI4, TRUVOICE, ANNA, ONECORE, NEURAL;

    /** Settings group only; native dispatch and asset availability still use the real engine. */
    val settingsGroup: Engine get() = if (this == ANNA) SAPI5 else this
}

data class VoiceDef(
    val id: String, val name: String, val engine: Engine, val language: String = "en-US",
    val dataName: String = "", val effect: String = "none", val pitch: Double = 0.0,
    val female: Boolean = false, val emotion: String = ""
) {
    fun locale(): Locale = Locale.forLanguageTag(language)
    fun androidVoice(): android.speech.tts.Voice = android.speech.tts.Voice(
        name, locale(), if (engine == Engine.NEURAL) android.speech.tts.Voice.QUALITY_VERY_HIGH
            else android.speech.tts.Voice.QUALITY_NORMAL,
        android.speech.tts.Voice.LATENCY_NORMAL, false, mutableSetOf()
    )
}

object VoiceCatalog {
    private fun sapi4(id: String, name: String, female: Boolean = false) =
        VoiceDef("sapi4_$id", "$name (SAPI 4)", Engine.SAPI4, dataName = name, female = female)
    private fun tv(id: String, name: String, mode: String, female: Boolean = false) =
        VoiceDef("truvoice_$id", name, Engine.TRUVOICE, dataName = "$mode, American English (TruVoice)", female = female)

    val voices: List<VoiceDef> = buildList {
        listOf(
            Triple("Jenny", "en-US", true), Triple("Aria", "en-US", true), Triple("Guy", "en-US", false),
            Triple("Sonia", "en-GB", true), Triple("Ryan", "en-GB", false),
            Triple("Neerja", "en-IN", true), Triple("Prabhat", "en-IN", false)
        ).forEach { (name, locale, female) ->
            add(VoiceDef("neural_${name.lowercase()}", "$name (Natural)", Engine.NEURAL,
                locale, name, female = female))
        }
        addAll(listOf(
            VoiceDef("sam", "Sam (SAPI 5)", Engine.SAPI5, "en-US", "Sam"),
            VoiceDef("mike", "Mike (SAPI 5)", Engine.SAPI5, "en-US", "Mike"),
            VoiceDef("mary", "Mary (SAPI 5)", Engine.SAPI5, "en-US", "Mary", female = true),
            VoiceDef("mike_hall", "Mike in Hall (SAPI 5)", Engine.SAPI5, "en-US", "Mike", "hall"),
            VoiceDef("mike_stadium", "Mike in Stadium (SAPI 5)", Engine.SAPI5, "en-US", "Mike", "stadium"),
            VoiceDef("mike_space", "Mike in Space (SAPI 5)", Engine.SAPI5, "en-US", "Mike", "space"),
            VoiceDef("mary_hall", "Mary in Hall (SAPI 5)", Engine.SAPI5, "en-US", "Mary", "hall", female = true),
            VoiceDef("mary_stadium", "Mary in Stadium (SAPI 5)", Engine.SAPI5, "en-US", "Mary", "stadium", female = true),
            VoiceDef("mary_space", "Mary in Space (SAPI 5)", Engine.SAPI5, "en-US", "Mary", "space", female = true),
            VoiceDef("robosoft1", "RoboSoft One (SAPI 5)", Engine.SAPI5, "en-US", "Sam", "robosoft1", 75.0),
            VoiceDef("robosoft2", "RoboSoft Two (SAPI 5)", Engine.SAPI5, "en-US", "Sam", "robosoft2", 120.0),
            VoiceDef("robosoft3", "RoboSoft Three (SAPI 5)", Engine.SAPI5, "en-US", "Mike", "robosoft3"),
            VoiceDef("robosoft4", "RoboSoft Four (SAPI 5)", Engine.SAPI5, "en-US", "Mary", "robosoft4", female = true),
            VoiceDef("robosoft6", "RoboSoft Six (SAPI 5)", Engine.SAPI5, "en-US", "Sam", "robosoft6", 100.0),
            VoiceDef("male_whisper", "Male Whisper (SAPI 5)", Engine.SAPI5, "en-US", "Mike", "whisper"),
            VoiceDef("female_whisper", "Female Whisper (SAPI 5)", Engine.SAPI5, "en-US", "Mary", "whisper", female = true),
            VoiceDef("anna", "Anna", Engine.ANNA, "en-US", female = true)
        ))
        addAll(listOf(
            sapi4("sam", "Sam"), sapi4("mike", "Mike"), sapi4("mary", "Mary", true),
            sapi4("mike_telephone", "Mike (for Telephone)"), sapi4("mary_telephone", "Mary (for Telephone)", true),
            sapi4("mike_hall", "Mike in Hall"), sapi4("mike_stadium", "Mike in Stadium"), sapi4("mike_space", "Mike in Space"),
            sapi4("mary_hall", "Mary in Hall", true), sapi4("mary_stadium", "Mary in Stadium", true), sapi4("mary_space", "Mary in Space", true),
            sapi4("robosoft1", "RoboSoft One"), sapi4("robosoft2", "RoboSoft Two"), sapi4("robosoft3", "RoboSoft Three"),
            sapi4("robosoft4", "RoboSoft Four", true), sapi4("robosoft5", "RoboSoft Five", true), sapi4("robosoft6", "RoboSoft Six", true),
            sapi4("male_whisper", "Male Whisper"), sapi4("female_whisper", "Female Whisper", true)
        ))
        addAll(listOf(
            tv("adult_male_1", "Peter", "Adult Male #1"), tv("adult_male_2", "Sidney", "Adult Male #2"),
            tv("adult_male_3", "Eager Eddie", "Adult Male #3"), tv("adult_male_4", "Deep Douglas", "Adult Male #4"),
            tv("adult_male_5", "Biff", "Adult Male #5"), tv("adult_male_6", "Grandpa Amos", "Adult Male #6"),
            tv("adult_male_7", "Melvin", "Adult Male #7"), tv("adult_male_8", "Alex", "Adult Male #8"),
            tv("adult_female_1", "Wanda", "Adult Female #1", true), tv("adult_female_2", "Julia", "Adult Female #2", true)
        ))
        val oneCore = listOf(
            Triple("David", false, "en-US"), Triple("Zira", true, "en-US"), Triple("Mark", false, "en-US"),
            Triple("Hazel", true, "en-GB"), Triple("George", false, "en-GB"), Triple("Susan", true, "en-GB"),
            Triple("Eva", true, "en-US"), Triple("Sarah", true, "en-GB"), Triple("Catherine", true, "en-AU"),
            Triple("James", false, "en-AU"), Triple("Linda", true, "en-US"), Triple("Richard", false, "en-US"),
            Triple("Matilda", true, "en-AU")
        )
        oneCore.forEach { (person, female, locale) ->
            val moods = if (person in setOf("David", "Zira", "Mark", "Eva")) listOf("", "happy", "sad", "angry") else listOf("")
            moods.forEach { mood ->
                add(VoiceDef("onecore_${person.lowercase()}${if (mood.isEmpty()) "" else "_$mood"}",
                    person + if (mood.isEmpty()) "" else " ${mood.replaceFirstChar(Char::uppercase)}",
                    Engine.ONECORE, locale, person, female = female, emotion = mood))
            }
        }
    }.distinctBy { it.id }

    /** Resolves an id, a display name ("Sam (SAPI 5)"), or a locale tag ("en-US", "eng-usa"). */
    fun byNameOrLocale(value: String?): VoiceDef? {
        if (value == null) return null
        voices.firstOrNull { it.id == value || it.name == value }?.let { return it }
        val parts = value.split('-', '_')
        if (parts[0].length !in 2..3) return null
        val tag = localeTag(parts[0], parts.getOrNull(1) ?: "")
        return voices.firstOrNull { it.language.equals(tag, true) }
    }

    fun byId(id: String?, pool: List<VoiceDef> = voices): VoiceDef {
        val resolved = byNameOrLocale(id)
        if (resolved != null && resolved in pool) return resolved
        return pool.firstOrNull() ?: resolved ?: voices.first()
    }

    /** Normalizes an ISO-3 ("eng", "USA") or BCP-47 ("en-US") language/country pair into a language tag. */
    private fun localeTag(language: String, country: String): String {
        val rawLanguage = Locale.forLanguageTag(language).language
        val lang = if (rawLanguage.length == 3) Locale.getAvailableLocales().firstOrNull {
            runCatching { it.getISO3Language() == rawLanguage }.getOrDefault(false)
        }?.language ?: rawLanguage else rawLanguage
        val rawCountry = if (country.length == 3) Locale.getAvailableLocales().firstOrNull {
            runCatching { it.getISO3Country() == country }.getOrDefault(false)
        }?.country ?: country else country
        return Locale(lang, rawCountry).toLanguageTag()
    }

    /**
     * Android asks the engine for a concrete voice name before it can show a language or
     * synthesize anything. The platform default answers with a bare locale tag ("en-US"),
     * which is not one of our voice names, so the request must be answered from the catalog.
     */
    fun defaultVoiceFor(language: String, country: String, pool: List<VoiceDef> = voices): VoiceDef? {
        val tag = localeTag(language, country)
        val lang = Locale.forLanguageTag(tag).language
        return pool.firstOrNull { it.language.equals(tag, true) }
            ?: pool.firstOrNull { it.locale().language.equals(lang, true) }
    }

    fun languageStatus(language: String, country: String, pool: List<VoiceDef> = voices): Int {
        val tag = localeTag(language, country)
        val lang = Locale.forLanguageTag(tag).language
        if (pool.any { it.language.equals(tag, true) }) return TextToSpeech.LANG_COUNTRY_AVAILABLE
        return if (pool.any { it.locale().language.equals(lang, true) }) TextToSpeech.LANG_AVAILABLE
        else TextToSpeech.LANG_NOT_SUPPORTED
    }
}
