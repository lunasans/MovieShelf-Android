package info.movieshelf.ui.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Ein Text, der erst dort in Worte gefasst wird, wo die Sprache bekannt ist.
 *
 * ViewModels und Repositories haben keinen Composable-Kontext und koennen
 * [stringResource] nicht aufrufen. Hielten sie stattdessen fertige
 * Zeichenketten, waeren diese in genau einer Sprache eingefroren — und
 * ausgerechnet die Schicht, die am wenigsten mit Darstellung zu tun hat,
 * entschiede ueber die Formulierung.
 *
 * Stattdessen reichen sie einen Verweis weiter: [Resource] fuer uebersetzbare
 * Texte, [Raw] fuer das, was bereits feststeht und nicht uebersetzt werden kann
 * — eine Meldung des Servers etwa oder ein Filmtitel.
 */
sealed interface UiText {

    /** Ein uebersetzbarer Text, optional mit Platzhaltern. */
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** Bereits feststehender Text: Servermeldungen, Titel, Zahlen. */
    data class Raw(val value: String) : UiText

    /** Aufloesen in der Oberflaeche. */
    @Composable
    fun asString(): String = when (this) {
        is Raw -> value
        is Resource -> stringResource(id, *args.toTypedArray())
    }

    /**
     * Aufloesen ausserhalb der Oberflaeche — etwa fuer die Meldung in der
     * Statusleiste, wo kein Composable zur Verfuegung steht.
     */
    fun asString(context: Context): String = when (this) {
        is Raw -> value
        is Resource -> context.getString(id, *args.toTypedArray())
    }

    companion object {
        /** Kurzschreibweise: `UiText.of(R.string.error_movie_not_found)`. */
        fun of(@StringRes id: Int, vararg args: Any): UiText = Resource(id, args.toList())
    }
}
