/*
 * SPDX-FileCopyrightText: Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.font.data.repository

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.os.UserHandle
import android.util.Log
import com.android.customization.model.ResourceConstants
import com.android.customization.model.ResourceConstants.ANDROID_PACKAGE
import com.android.customization.model.ResourceConstants.CONFIG_BODY_FONT_FAMILY
import com.android.customization.model.ResourceConstants.CONFIG_HEADLINE_FONT_FAMILY
import com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_FONT
import com.android.customization.model.ResourceConstants.THEME_SETTING
import com.android.customization.model.theme.OverlayManagerCompat
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.themepicker.R
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** [key] is the overlay package name, or [DEFAULT_KEY] for the system default font. */
data class FontOptionModel(
    val key: String,
    val title: String,
    val headlineFont: Typeface,
    val bodyFont: Typeface,
)

@Singleton
class FontRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val secureSettingsRepository: SecureSettingsRepository,
    @BackgroundDispatcher private val bgDispatcher: CoroutineDispatcher,
) {

    val fontOptions: Flow<List<FontOptionModel>> =
        flow { emit(loadOptions()) }.flowOn(bgDispatcher)

    val selectedFontKey: Flow<String> =
        secureSettingsRepository.stringSetting(THEME_SETTING).map {
            parseOverlays(it)?.optString(OVERLAY_CATEGORY_FONT).orEmpty()
        }

    suspend fun applyFont(key: String) =
        withContext(bgDispatcher) {
            val json =
                parseOverlays(secureSettingsRepository.getString(THEME_SETTING)) ?: JSONObject()
            json.remove(OVERLAY_CATEGORY_FONT)
            if (key != DEFAULT_KEY) json.put(OVERLAY_CATEGORY_FONT, key)
            secureSettingsRepository.setString(THEME_SETTING, json.toString())
        }

    private fun parseOverlays(value: String?): JSONObject? =
        try {
            JSONObject(value ?: "{}")
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse $THEME_SETTING", e)
            null
        }

    private fun loadOptions(): List<FontOptionModel> {
        val pm = context.packageManager
        val system = Resources.getSystem()
        val default =
            FontOptionModel(
                key = DEFAULT_KEY,
                title = context.getString(R.string.default_theme_title),
                headlineFont = system.fontFamily(ANDROID_PACKAGE, CONFIG_HEADLINE_FONT_FAMILY),
                bodyFont = system.fontFamily(ANDROID_PACKAGE, CONFIG_BODY_FONT_FAMILY),
            )
        val overlays =
            OverlayManagerCompat(context)
                .getOverlayPackagesForCategory(
                    OVERLAY_CATEGORY_FONT,
                    UserHandle.myUserId(),
                    *ResourceConstants.getPackagesToOverlay(context),
                )
                .mapNotNull { pkg ->
                    try {
                        val res = pm.getResourcesForApplication(pkg)
                        FontOptionModel(
                            key = pkg,
                            title = pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString(),
                            headlineFont = res.fontFamily(pkg, CONFIG_HEADLINE_FONT_FAMILY),
                            bodyFont = res.fontFamily(pkg, CONFIG_BODY_FONT_FAMILY),
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't load font overlay $pkg, skipping", e)
                        null
                    }
                }
                .sortedBy { it.title.lowercase() }
        return listOf(default) + overlays
    }

    private fun Resources.fontFamily(pkg: String, name: String): Typeface =
        Typeface.create(getString(getIdentifier(name, "string", pkg)), Typeface.NORMAL)

    companion object {
        private const val TAG = "FontRepository"
        const val DEFAULT_KEY = ""
    }
}
