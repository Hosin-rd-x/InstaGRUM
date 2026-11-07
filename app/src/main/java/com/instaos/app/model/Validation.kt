package com.instaos.app.model

/**
 * Comment threading cap. The simulation-era validators
 * (`SimulationSettings.validated()`, `LiveConfig.validated()`) are deleted
 * with the engine in phase 3; this cap is data-independent and stays.
 */
fun List<Comment>.threadWindow(limit: Int = 180): List<Comment> {
    val byId = associateBy { it.id }
    val retained = linkedSetOf<String>()
    val prioritized = filter { it.own }.asReversed() + asReversed()
    for (comment in prioritized) {
        val chain = linkedSetOf<String>()
        var current: Comment? = comment
        while (current != null && chain.add(current.id)) current = byId[current.parentId]
        if ((retained + chain).size <= limit) retained += chain
    }
    return filter { it.id in retained }
}
