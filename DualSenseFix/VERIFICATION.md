# Результаты проверки

Проверено в облачной Linux-среде 24 октября 2026 г.:

- `scripts/setup-cloud.sh`: успешный полный запуск и повторный запуск с сохранёнными зависимостями.
- Gradle Wrapper 8.9: контрольная сумма дистрибутива проверена; `assembleDebug lintDebug` — BUILD SUCCESSFUL.
- Kotlin/JNI/C++17: нативная библиотека собрана NDK 27.2.12479018 для arm64-v8a.
- Host suite `scripts/test-host.sh`: PASS под AddressSanitizer/UndefinedBehaviorSanitizer. Проверены перестановка осей, нормализация и границы, deadzone, Circle press/release, игнорирование неизвестных кнопок, различия Xbox/Sony, D-pad, цифровые триггеры, SYN_REPORT, сериализация и отклонение некорректных карт. Это один исполняемый набор assertions; Android instrumentation-тесты не запускались.
- `lintDebug`: 0 ошибок, 16 предупреждений. Среди предупреждений — строки UI без ресурсов локализации, отсутствие ChromeOS ABI и значка, backup-конфигурация, System.load. JNI загружается абсолютным путём исключительно из `applicationInfo.nativeLibraryDir` установленного APK: путь не принимается от пользователя. Минимальный интерфейс русскоязычный, APK предназначен для ARM64.
- `apksigner verify --verbose`: подпись APK v2 успешно проверена, один подписант, debug signing.
- `aapt dump badging`: dev.dualsense.fix 0.1, minSdk 26, targetSdk 35, arm64-v8a.
- Исходный checkout `/workspace/Snake-js` остался без изменений.

## Реализовано

Shizuku v13+ UserService с проверкой UID 2000 и JNI в shell-процессе; поиск Sony VID/PID без фиксированного eventN; безопасная диагностика; обучение кнопок и осей; два профиля uinput; проверка установленного keylayout до создания виртуального устройства; тест A/Cross; EVIOCGRAB с явным согласием; восстановление состояний после SYN_DROPPED; переподключение; нейтрализация и закрытие FD; foreground-служба с Stop; watchdog; встроенный Android Input Tester; dumpsys input и экспорт логов.

## Не подтверждено без телефона

Ни Poco X3 Pro, ни контроллер к облачной машине не подключены. Работа Shizuku UserService/JNI на MIUI, доступ shell к ioctl, фактический EVIOCGRAB, регистрация Android InputReader, выбранный KeyLayoutFile, Sources/Motion Ranges, доставка тестовой кнопки, отсутствие двойного ввода, Bluetooth reconnect и исправление PPSSPP **не проверены на реальном устройстве**.

APK готов для испытаний по README; заявление о полностью работающем исправлении на Poco до этих испытаний было бы необоснованным. Предполагаемую карту осей из ТЗ нужно проверить, остальные кнопки — обучить.

## Среда

Установлены полный Temurin JDK 17.0.21+9, Android SDK 35, Build Tools 35.0.0, NDK 27.2.12479018 и CMake 3.22.1. SHA256 JDK/Gradle проверены, command-line tools сверены с официальной metadata Google; TLS-проверка сохраняется и использует системное хранилище доверенных CA среды.

Поля `install_script` и `start_skill` сохранены в черновике конфигурации. Сохранение не публикует снимок: для сохранения среды в продукте проверьте и сохраните настройки, затем опубликуйте её. Новый проект создан отдельно от Snake-js и должен сохраняться в файловом снимке; удалённый GitHub-репозиторий для него не создавался.
