package io.legado.app.ui.config

import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import io.legado.app.R
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.urlRecord.UrlRecordActivity
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.startActivity

class SettingsHomeFragment : PreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_settings_home)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.setting)
        listView.setEdgeEffectColor(primaryColor)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "backup_restore" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.BACKUP_CONFIG)
            }

            "theme_setting" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.THEME_CONFIG)
            }

            "other_setting" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.OTHER_CONFIG)
            }

            "url_record" -> startActivity<UrlRecordActivity>()
        }
        return super.onPreferenceTreeClick(preference)
    }
}
