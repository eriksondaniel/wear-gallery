/**
 * Copyright (C) 2020 Chenhe
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cc.chenhe.weargallery.watchface

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.lifecycle.observe
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import cc.chenhe.weargallery.R
import cc.chenhe.weargallery.uilts.*
import cc.chenhe.weargallery.wearvision.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.FileOutputStream

private const val REQUEST_BACKGROUND = 10

private const val TAG = "WfPreference"

private const val URI_APP_AW = "market://details?id=com.cotwf.watchfaceplatform.wearos"
private const val URI_APP_TW = "market://details?id=com.cotwf.watchfaceplatform.ticwear"

class PreferenceFr : PreferenceFragmentCompat() {

    private lateinit var analogStyle: String
    private lateinit var digitalStyle: String

    private val model by viewModel<PreferenceViewModel>()

    private lateinit var positionPreference: Preference
    private lateinit var textPreference: Preference
    private lateinit var textColorPreference: Preference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analogStyle = getString(R.string.wf_preference_type_entry_value_analog)
        digitalStyle = getString(R.string.wf_preference_type_entry_value_digital)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_wf_main, rootKey)

        positionPreference = requireNotNull(findPreference("preference_watchface_text_position"))
        textPreference = requireNotNull(findPreference("preference_watchface_text"))
        textColorPreference = requireNotNull(findPreference("preference_watchface_text_color"))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.styleType.observe(viewLifecycleOwner) { style ->
            when (style) {
                digitalStyle -> setDigitalPreferencesVisible(true)
                analogStyle -> setDigitalPreferencesVisible(false)
                else -> setDigitalPreferencesVisible(false)
            }
        }
    }

    private fun setDigitalPreferencesVisible(visible: Boolean) {
        positionPreference.isVisible = visible
        textPreference.isVisible = visible
        textColorPreference.isVisible = visible
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        when (preference?.key) {
            "preference_watchface_image" -> {
                val intent = Intent(Intent.ACTION_PICK).apply {
                    setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                }
                startActivityForResult(intent, REQUEST_BACKGROUND)
            }
            "preference_watchface_text_position" -> {
                findNavController().navigate(R.id.timeTextStyleFr)
            }
            "preference_watchface_text" -> {
                findNavController().navigate(R.id.timeTextFr)
            }
            "preference_watchface_text_color" -> {
                findNavController().navigate(R.id.timeTextColorFr)
            }
            "preference_cot" -> {
                startIntent(Intent(Intent.ACTION_VIEW,
                        Uri.parse(if (isTicwear()) URI_APP_TW else URI_APP_AW)))
                { toast(R.string.wf_preference_intent_err) }
            }
            else -> return super.onPreferenceTreeClick(preference)
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BACKGROUND && resultCode == Activity.RESULT_OK) {
            val uri = data!!.data!!
            GlobalScope.launch {
                copyBackground(uri)
            }
        }
    }

    private suspend fun copyBackground(uri: Uri) = withContext(Dispatchers.IO) {
        logd(TAG, "Copying background image, uri=$uri")
        val targetFile = File(getWatchFaceResFolder(requireContext()), WATCH_FACE_BACKGROUND)
        if (targetFile.isFile) {
            targetFile.delete()
        }
        requireContext().contentResolver.openInputStream(uri)?.use { ins ->
            FileOutputStream(targetFile).use { fos ->
                ins.copyTo(fos)
            }
        }
        LocalBroadcastManager.getInstance(get()).sendBroadcast(Intent(ACTION_WATCH_FACE_BACKGROUND_CHANGED))
        logd(TAG, "Copy background image complete.")
    }

    private fun testIntent(intent: Intent): Boolean {
        return context?.let { intent.resolveActivity(it.packageManager) != null } ?: false
    }

    private inline fun startIntent(intent: Intent, err: () -> Unit) {
        try {
            if (testIntent(intent)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                err()
            }
        } catch (e: Exception) {
            err()
        }
    }

}
