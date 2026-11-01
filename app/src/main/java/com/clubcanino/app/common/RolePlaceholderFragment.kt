package com.clubcanino.app.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.clubcanino.app.R

/**
 * Shared placeholder UI for role destinations that will be completed in later stages.
 */
class RolePlaceholderFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_role_placeholder, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        view.findViewById<TextView>(R.id.textTitle).setText(args.getInt(ARG_TITLE))
        view.findViewById<TextView>(R.id.textSubtitle).setText(args.getInt(ARG_SUBTITLE))
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_SUBTITLE = "arg_subtitle"

        fun newInstance(
            @StringRes titleRes: Int,
            @StringRes subtitleRes: Int
        ): RolePlaceholderFragment {
            return RolePlaceholderFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TITLE, titleRes)
                    putInt(ARG_SUBTITLE, subtitleRes)
                }
            }
        }
    }
}
