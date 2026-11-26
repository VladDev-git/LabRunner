package ua.labrunner.app

import platform.UIKit.UIDevice

actual val platformName: String = "${UIDevice.currentDevice.systemName()} ${UIDevice.currentDevice.systemVersion}"

// The iOS simulator shares the host network, so the local docker compose API is on localhost.
actual val apiBaseUrl: String = "http://localhost:8080"
