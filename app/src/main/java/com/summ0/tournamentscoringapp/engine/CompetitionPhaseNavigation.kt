package com.summ0.tournamentscoringapp.engine

internal data class CompetitionPhaseStatus(
    val available: Boolean,
    val started: Boolean,
    val complete: Boolean
)

internal data class CompetitionPhaseProgress(
    val weapons: CompetitionPhaseStatus,
    val hyungs: CompetitionPhaseStatus,
    val sparring: CompetitionPhaseStatus
) {
    fun status(screen: CompetitionScreen): CompetitionPhaseStatus? = when (screen) {
        CompetitionScreen.WEAPONS_SCORING -> weapons
        CompetitionScreen.HYUNGS_SCORING -> hyungs
        CompetitionScreen.SPARRING_BRACKET -> sparring
        CompetitionScreen.CHECK_IN,
        CompetitionScreen.OVERALL_AWARDS -> null
    }

    fun completedScreens(): Set<CompetitionScreen> = buildSet {
        if (weapons.available && weapons.complete) add(CompetitionScreen.WEAPONS_SCORING)
        if (hyungs.available && hyungs.complete) add(CompetitionScreen.HYUNGS_SCORING)
        if (sparring.available && sparring.complete) add(CompetitionScreen.SPARRING_BRACKET)
    }
}

internal fun canOpenOverallAwards(progress: CompetitionPhaseProgress): Boolean {
    val phases = listOf(progress.weapons, progress.hyungs, progress.sparring)
    return phases.any { it.available } &&
        phases.all { !it.available || it.complete }
}

internal fun canNavigateCompetitionPhase(
    current: CompetitionScreen,
    target: CompetitionScreen,
    progress: CompetitionPhaseProgress
): Boolean {
    if (current == target) return true

    val currentStatus = progress.status(current)
    if (currentStatus?.started == true && !currentStatus.complete) return false

    val weaponsComplete = !progress.weapons.available || progress.weapons.complete
    val hyungsComplete = !progress.hyungs.available || progress.hyungs.complete
    return when (target) {
        CompetitionScreen.CHECK_IN -> true
        CompetitionScreen.WEAPONS_SCORING -> progress.weapons.available
        CompetitionScreen.HYUNGS_SCORING ->
            progress.hyungs.available && weaponsComplete
        CompetitionScreen.SPARRING_BRACKET ->
            progress.sparring.available && weaponsComplete && hyungsComplete
        CompetitionScreen.OVERALL_AWARDS -> canOpenOverallAwards(progress)
    }
}
