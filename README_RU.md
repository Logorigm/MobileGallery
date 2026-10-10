# Offline Media Vault — Android

Оффлайн-приложение на Java для Android: тёмная тема, импорт фото и видео, импорт ZIP, теги, поиск по тегам/названию, просмотр фото и воспроизведение видео внутри приложения.

## Как получить APK (бесплатно)
1. Установите **Android Studio** с официального сайта: https://developer.android.com/studio
2. Распакуйте архив `OfflineMediaVaultAndroid.zip`.
3. В Android Studio выберите **Open** и укажите распакованную папку `OfflineMediaVaultAndroid`.
4. Дождитесь окончания синхронизации Gradle. Если Android Studio предложит установить Android SDK Platform 35 или Build Tools, согласитесь.
5. В меню выберите **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
6. После успешной сборки нажмите **locate** в уведомлении. APK будет в `app/build/outputs/apk/debug/app-debug.apk`.
7. Передайте APK на Android-телефон и откройте файл. Если система спросит, разрешите установку приложений из этого источника.

## Версия Gradle
Проект настроен на Gradle 8.9 (`gradle/wrapper/gradle-wrapper.properties`). В Android Studio используйте Gradle Wrapper проекта.

## Сборка через терминал
Из корня проекта при установленном Android SDK и Gradle:
`gradle assembleDebug`
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Примечания
- Интернет нужен только для первой настройки/сборки Android Studio и загрузки Gradle/SDK. Само приложение работает оффлайн и не запрашивает разрешение на интернет.
- Файлы копируются в приватную папку приложения. При удалении приложения Android удалит эти файлы, поэтому сохраняйте оригиналы отдельно.
- Поддерживаются JPG/JPEG, PNG, GIF, BMP, WEBP, MP4, MOV, M4V, 3GP, MKV, WEBM и AVI. Возможность воспроизведения зависит от кодека, поддерживаемого Android.
- ZIP импортирует поддерживаемые медиафайлы из вложенных папок архива; сам ZIP не сохраняется.
- Проект подготовлен, но APK здесь не был собран и проверен на устройстве. Для получения APK следуйте инструкции выше.


## Автоматическая установка JDK, Gradle и sdkmanager (Windows/Linux/macOS)

В папке `tools/` есть скрипты, которые загружают в `tools/portable/`:
- JDK 17 (Temurin);
- Gradle 8.9;
- Android SDK Command-line Tools (`sdkmanager`);
- Android SDK Platform 35, Platform Tools и Build Tools 35.0.0.

**Windows (PowerShell):** открой PowerShell в корне проекта и выполни:
`Set-ExecutionPolicy -Scope Process Bypass`
`./tools/setup-windows.ps1`

**Linux/macOS (Terminal):**
`chmod +x tools/setup-linux-macos.sh`
`./tools/setup-linux-macos.sh`

После завершения скрипт покажет команду сборки APK. Установка загружает компоненты с официальных серверов и требует интернет на этапе настройки. Скрипты скачивают компоненты под текущую ОС/архитектуру; сами большие дистрибутивы не включены в ZIP. На macOS сборка Android APK поддерживается, но скрипт рассчитан на Intel/Apple Silicon.
