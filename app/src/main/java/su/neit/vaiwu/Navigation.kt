package su.neit.vaiwu

/**
 * Top level tabs of the application. Every tab owns exactly one screen, so
 * the bottom bar can never point at a screen that does not exist.
 */
enum class AppTab {
    Home,
    Games,
    Pet,
    Chat,
}

/** The screen owned by a tab; inverse of [AppScreen.tab]. */
val AppTab.screen: AppScreen
    get() = when (this) {
        AppTab.Home -> AppScreen.Home
        AppTab.Games -> AppScreen.Games
        AppTab.Pet -> AppScreen.Pet
        AppTab.Chat -> AppScreen.Chat
    }

/**
 * Navigation rules of `docs/ui-spec.md` (section 5).
 *
 * They are pure functions on purpose: the behaviour of the back button and of
 * the bottom bar is verified by unit tests instead of by hand.
 */

/** The tab a screen belongs to; `null` for focus screens and gear screens. */
val AppScreen.tab: AppTab?
    get() = when (this) {
        AppScreen.Home -> AppTab.Home
        AppScreen.Games -> AppTab.Games
        AppScreen.Pet -> AppTab.Pet
        AppScreen.Chat -> AppTab.Chat
        else -> null
    }

/** Focus and gear screens hide the bottom bar and show a back arrow. */
val AppScreen.isFocusScreen: Boolean
    get() = tab == null

/** Where a back press leads when the navigation history is empty. */
val AppScreen.parentScreen: AppScreen
    get() = when (this) {
        AppScreen.Shop -> AppScreen.Home
        AppScreen.Rps, AppScreen.MiniGame, AppScreen.Battle -> AppScreen.Games
        AppScreen.Settings -> AppScreen.Home
        AppScreen.Help -> AppScreen.Settings
        else -> AppScreen.Home
    }

/**
 * Screen that a back press has to open.
 *
 * * On a tab the back press leads to "Home" instead of closing the app
 *   (UI-NAV-03).
 * * On a focus screen it returns to the screen the user came from.
 * * `null` means "nothing to do": the app may close itself.
 */
fun backTarget(screen: AppScreen, history: List<AppScreen>): AppScreen? = when {
    screen == AppScreen.Home -> null
    screen == AppScreen.Settings -> AppScreen.Home
    screen == AppScreen.Help -> AppScreen.Settings
    screen.tab != null -> AppScreen.Home
    else -> history.lastOrNull() ?: screen.parentScreen
}

/**
 * True while a round is in progress. Leaving such a screen forfeits the
 * reward, so the UI has to ask for a confirmation first (UI-NAV-04).
 *
 * The battle counts as a round while the fight is running and while the pet
 * may still walk away with loot; a finished dungeon keeps its reward
 * already, so leaving it needs no confirmation.
 */
fun isRoundActive(
    screen: AppScreen,
    rpsResult: RpsResult?,
    miniGame: MiniGameState?,
    battle: BattleSnapshot?,
): Boolean = when (screen) {
    AppScreen.Rps -> rpsResult == null
    AppScreen.MiniGame -> miniGame != null && !miniGame.finished
    AppScreen.Battle -> battle != null && (!battle.over || battle.awaitingRest)
    else -> false
}
