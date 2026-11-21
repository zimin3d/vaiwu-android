package su.neit.vaiwu

data class CharacterProfile(
    val id: Int,
    val name: String,
    val personality: String,
    val favoriteFood: String,
    val strength: Int,
    val vitality: Int,
    val agility: Int,
    val magic: Int,
    val luck: Int,
)

data class AssistantIntent(
    val kind: Int,
    val hour: Int,
    val minute: Int,
    val afterMinutes: Int,
    val conversion: String,
)

data class ChatMessage(
    val text: String,
    val fromPet: Boolean,
    val id: Long = 0L,
)

data class AssistantSettings(
    val speechEnabled: Boolean = false,
    val hasApiKey: Boolean = false,
    val locationConfigured: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

object AssistantKind {
    const val None = 0
    const val SetAlarm = 1
    const val MissingTime = 2
    const val Reminder = 3
    const val Joke = 4
    const val Time = 5
    const val Weather = 6
    const val Cancel = 7
    const val Pomodoro = 8
    const val Convert = 9
}

object PhraseContext {
    const val Greeting = 0
    const val Fed = 1
    const val Played = 2
    const val Washed = 3
    const val Toilet = 4
    const val Talked = 5
    const val Hugged = 6
    const val Patted = 7
    const val Treated = 8
    const val RefusedFood = 9
    const val Sleeping = 10
    const val Sick = 11
    const val Sad = 12
    const val Dead = 13
    const val Poked = 14
    const val Woken = 15
    const val Victory = 16
    const val Fainted = 17
}
