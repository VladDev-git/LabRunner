package ua.labrunner.app

actual val platformName: String = "Android ${android.os.Build.VERSION.RELEASE}"

actual val apiBaseUrl: String = BuildConfig.API_BASE_URL
