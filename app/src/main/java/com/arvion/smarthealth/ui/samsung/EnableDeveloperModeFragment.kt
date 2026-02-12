package com.arvion.smarthealth.ui.samsung

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.arvion.smarthealth.R

class EnableDeveloperModeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_enable_developer_mode, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.open_samsung_health_button).setOnClickListener {
            val intent = requireActivity().packageManager.getLaunchIntentForPackage("com.sec.android.app.shealth")
            if (intent != null) {
                startActivity(intent)
            }
        }
    }
}