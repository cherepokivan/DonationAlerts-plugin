# DonationAlerts

Плагин для Paper 26.2 (Minecraft 26.2), который получает новые донаты DonationAlerts в реальном времени и одновременно публикует их в Minecraft и выбранном текстовом канале Discord. Discord-бот запускается внутри `DonationAlerts.jar`: отдельные Node.js, Python, VPS или процессы не нужны.

## Установка

1. Откройте запуск GitHub Actions **Build DonationAlerts** и скачайте artifact `DonationAlerts`.
2. Поместите `DonationAlerts.jar` в папку `plugins/` Paper-сервера.
3. Запустите сервер: плагин создаст `plugins/DonationAlerts/config.yml`.
4. Остановите сервер и заполните этот файл.
5. Запустите сервер снова. Готовый JAR уже содержит JDA и прочие runtime-зависимости.

Сборка выполняется GitHub Actions с Java 25 и Maven Wrapper; пользователю ничего собирать или устанавливать не нужно. Готовый результат workflow — `target/DonationAlerts.jar`.

## Discord

1. В [Discord Developer Portal](https://discord.com/developers/applications) создайте Application, на вкладке **Bot** нажмите **Add Bot** и скопируйте токен в `discord.bot-token`.
2. В **OAuth2 → URL Generator** выберите scope `bot`, а в Bot Permissions — как минимум `View Channel` и `Send Messages`. Откройте созданную ссылку и добавьте бота на сервер.
3. В Discord включите Developer Mode, щёлкните правой кнопкой по нужному текстовому каналу и выберите **Copy Channel ID**. Поместите ID в `discord.channel-id`.

При запуске плагин проверяет, что это доступный текстовый канал и что бот имеет право отправлять туда сообщения. Ошибки Discord не останавливают Minecraft.

## DonationAlerts

Интеграция использует официальный [DonationAlerts API](https://www.donationalerts.com/apidoc): REST API для профиля/подписки и Centrifugo WebSocket для моментальных событий. В приложении DonationAlerts создайте OAuth-приложение, затем получите `client-id`, `client-secret`, `access-token` и `refresh-token`. Нужны scopes:

`oauth-user-show oauth-donation-subscribe oauth-goal-subscribe`

Заполните соответствующие поля `donationalerts` в конфигурации. `access-token` может быть обновлён автоматически по `refresh-token`; его новое значение не печатается в журнал. Подписка на `$alerts:donation_<user_id>` получает новые донаты, а `$goals:goal_<user_id>` обновляет актуальную Donation Goal.

## Настройка

Все секреты оставьте только в `plugins/DonationAlerts/config.yml`. Корневой `config.yml` добавлен в `.gitignore`, а [config.yml.example](config.yml.example) не содержит секретов.

`messages.minecraft` — MiniMessage для чата Minecraft; `messages.discord` — Markdown Discord. Доступны placeholder'ы:

| Placeholder | Значение |
| --- | --- |
| `{username}` | Ник донатера |
| `{amount_raw}` | Сумма без валюты, например `500` |
| `{currency}` | ISO-код, например `RUB` |
| `{amount}` | Сумма с настроенным отображением валюты, например `500₽` |
| `{goal}` | Текущая цель DonationAlerts либо `fallback-goal-name` |

### Валюта

Значения из `currency-format` дописываются к сумме без пробела:

| Настройка | Результат для 500 RUB |
| --- | --- |
| `RUB: "₽"` | `500₽` |
| `RUB: "RUB"` | `500RUB` |
| `RUB: ""` | `500` |

Если кода нет в `currency-format`, используется исходный ISO-код с пробелом: `100 CHF`. Чтобы получить `500 RUB` с любым форматом, используйте `{amount_raw} {currency}`.

## Команда

`/donationalerts reload` — перечитывает `config.yml`, перезапускает встроенного Discord-бота и подключение DonationAlerts. Требуется permission `donationalerts.admin`, по умолчанию доступный OP.

## Надёжность

Сетевые операции выполняются вне основного потока Paper. При обрыве DonationAlerts плагин ждёт `reconnect-delay-seconds` и подключается заново. Уникальные ID донатов хранятся в памяти, поэтому донат не дублируется после переподключения. При остановке сервера WebSocket, JDA и фоновые ресурсы закрываются.
