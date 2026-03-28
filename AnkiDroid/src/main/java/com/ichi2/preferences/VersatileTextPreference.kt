/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.preferences

import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.res.use
import androidx.core.widget.TextViewCompat
import androidx.core.widget.addTextChangedListener
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.utils.dp
import com.ichi2.utils.setPaddingRelative
import kotlin.jvm.Throws
import com.google.android.material.R as MaterialR

/**
 * A drop-in alternative to [EditTextPreference] with some extra functionality.
 *
 *   * It supports changing input type via `android:inputType` XML attribute,
 *     which can help the keyboard app with choosing a more suitable keyboard layout.
 *     For the list of available input types, see [TextView.getInputType].
 *
 *   * If [continuousValidator] is set, the dialog uses it to prevent user
 *     from entering invalid data. On each text change, the validator is run;
 *     if it throws, the positive button gets disabled.
 */
open class VersatileTextPreference(
    context: Context,
    attrs: AttributeSet?,
) : EditTextPreference(context, attrs),
    DialogFragmentProvider {
    fun interface Validator {
        @Throws(Exception::class)
        fun validate(value: String)
    }

    val dialogHint: CharSequence? =
        context.obtainStyledAttributes(attrs, R.styleable.CustomPreference).use {
            it.getText(R.styleable.CustomPreference_dialogHint)
        }

    val referenceEditText = AppCompatEditText(context, attrs)

    var continuousValidator: Validator? = null

    override fun makeDialogFragment() = VersatileTextPreferenceDialogFragment()
}

open class VersatileTextPreferenceDialogFragment : EditTextPreferenceDialogFragmentCompat() {
    private val versatileTextPreference get() = preference as VersatileTextPreference

    protected lateinit var editText: EditText

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        val titleText = preference.dialogTitle ?: preference.title
        if (titleText.isNullOrEmpty()) {
            return
        }
        // Use a custom MaterialTextView to match the title text appearance and padding,
        // as seen in other dialogs (e.g. "Create deck"). The default TextView renders
        // the title too small and inconsistent with Material dialog standards.
        val tv =
            MaterialTextView(requireContext()).apply {
                text = titleText
                setPaddingRelative(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 8.dp)

                val outValue = TypedValue()
                val hasStyle =
                    context.theme.resolveAttribute(
                        MaterialR.attr.materialAlertDialogTitleTextStyle,
                        outValue,
                        true,
                    )
                val styleRes = if (hasStyle) outValue.resourceId else 0
                if (styleRes != 0) {
                    TextViewCompat.setTextAppearance(this, styleRes)
                } else {
                    TextViewCompat.setTextAppearance(
                        this,
                        MaterialR.style.TextAppearance_Material3_HeadlineSmall,
                    )
                }
            }

        builder.setCustomTitle(tv)
    }

    override fun onCreateDialogView(context: Context): View =
        LayoutInflater
            .from(context)
            .inflate(R.layout.dialog_versatile_text_preference, null, false)

    // This changes input type first, as it resets the cursor,
    // And only then calls super, which sets up text and moves the cursor to end.
    //
    // Positive button isn't present in a dialog until it is shown, which happens around onStart;
    // for simplicity, obtain it in the listener itself.
    override fun onBindDialogView(contentView: View) {
        val textInputLayout = contentView.findViewById<TextInputLayout>(R.id.text_input_layout)

        editText = contentView.findViewById(android.R.id.edit)!!
        editText.inputType = versatileTextPreference.referenceEditText.inputType

        super.onBindDialogView(contentView)

        textInputLayout.hint =
            versatileTextPreference.dialogHint ?: preference.dialogTitle ?: preference.title

        versatileTextPreference.continuousValidator?.let {
            editText.addTextChangedListener(afterTextChanged = { updatePositiveButtonState() })
        }
    }

    override fun onStart() {
        super.onStart()

        (dialog as? AlertDialog)
            ?.getButton(DialogInterface.BUTTON_POSITIVE)
            ?.text = TR.actionsSave()

        updatePositiveButtonState()
    }

    private fun updatePositiveButtonState() {
        val validator = versatileTextPreference.continuousValidator ?: return
        val positiveButton =
            (dialog as? AlertDialog)?.getButton(DialogInterface.BUTTON_POSITIVE) ?: return

        positiveButton.isEnabled =
            try {
                validator.validate(editText.text.toString())
                true
            } catch (_: Exception) {
                false
            }
    }
}
