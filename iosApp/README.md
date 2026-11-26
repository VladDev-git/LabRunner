# iosApp

SwiftUI-обгортка над спільним UI з `composeApp` (Compose Multiplatform).

Xcode-проєкт (`iosApp.xcodeproj`) створюється на macOS; збірка iOS-версії запускається на macOS-раннері в CI (див. ADR-0003). Порядок:

1. Створити в Xcode проєкт `iosApp` (App, SwiftUI) у цій папці та додати файли `iosApp/*.swift`.
2. Додати Run Script фазу перед компіляцією:
   ```sh
   cd "$SRCROOT/.."
   ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
   ```
3. У `Info.plist` дозволити локальну мережу для dev-API: `NSAppTransportSecurity → NSAllowsLocalNetworking = YES`.
