package org.opencairn.cairn.ui.charts

import androidx.compose.ui.graphics.Color
import org.opencairn.cairn.core.domain.model.TrainingLoadDay
import org.opencairn.cairn.ui.design.theme.CairnColors

/** The three curves of the training-load model. */
enum class LoadSeries(
    val color: Color,
) {
    FITNESS(CairnColors.teal400),
    FATIGUE(CairnColors.red400),
    FORM(CairnColors.amber400),
    ;

    fun value(day: TrainingLoadDay): Double =
        when (this) {
            FITNESS -> day.fitness
            FATIGUE -> day.fatigue
            FORM -> day.form
        }
}
