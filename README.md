# DonationAlerts Fabric  

Серверный мод Fabric для Minecraft 26.2. Он получает новые донаты DonationAlerts в реальном времени и публикует их одновременно в Minecraft-чат и указанный текстовый канал Discord. Discord-бот работает внутри мода: отдельные Python, Node.js, VPS или процессы не нужны.

## Требования

- Minecraft 26.2;
- Fabric Loader 0.19.5 или новее;
- Fabric API для Minecraft 26.2;
- Java 25.

## Установка

1. Скачайте artifact `DonationAlerts-Fabric` из завершившегося GitHub Actions workflow.
2. Установите Fabric Loader и Fabric API на сервер Minecraft 26.2.
3. Поместите `DonationAlerts-Fabric-1.0.0.jar` в папку `mods/` сервера, а не в `plugins/`.
4. Запустите сервер. Мод создаст `config/DonationAlerts/config.yml`.
5. Остановите сервер, заполните настройки и включите сервер снова.

Готовый JAR собирается только GitHub Actions при помощи Gradle Wrapper и Java 25. Пользователю не требуется собирать мод локально.

## Настройка DonationAlerts

Мод использует официальный [DonationAlerts API](https://www.donationalerts.com/apidoc): REST API, OAuth и Centrifugo WebSocket. Создайте OAuth-приложение DonationAlerts и заполните в конфигурации `client-id`, `client-secret`, `access-token` и `refresh-token`. Нужные scopes: `oauth-user-show`, `oauth-donation-subscribe`, `oauth-goal-subscribe`.

## Настройка Discord

Создайте приложение и Bot в [Discord Developer Portal](https://discord.com/developers/applications), добавьте бота на сервер с правами `View Channel` и `Send Messages`. Скопируйте его token в `discord.bot-token`, а ID текстового канала — в `discord.channel-id`.

## Сообщения и placeholder'ы

`messages.minecraft` поддерживает текст и цветовые MiniMessage-теги `<gold>`, `<green>`, `<gray>`, `<aqua>`, `<yellow>`, `<red>`, `<white>`. Discord использует Markdown.

| Placeholder | Значение |
| --- | --- |
| `{username}` | Ник донатера |
| `{amount_raw}` | Сумма без валюты |
| `{currency}` | ISO-код валюты |
| `{amount}` | Сумма с `currency-format` |
| `{goal}` | Активная Donation Goal или `fallback-goal-name` |

Для неизвестной валюты `{amount}` будет выглядеть как `100 CHF`. Настройка `RUB: "₽"` даёт `500₽`; `RUB: ""` даёт `500`.

## Команда

`/donationalerts reload` — перечитывает `config/DonationAlerts/config.yml` и перезапускает Discord/DonationAlerts-подключения. Нужен уровень доступа оператора сервера (permission level 4).

## Получение токена

Для получения токена в исходном коде есть скрипт: `get_donationalerts_tokens.py`.
Откройте скрипт с помощью редактора и вставьте полученные на сайте https://www.donationalerts.com/application/clients `CLIENT_ID = "paste"` и `CLIENT_SECRET = "paste"`, и запустите скрипт.
