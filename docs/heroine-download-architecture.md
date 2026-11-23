# Архитектура: загрузка героинь (Android-клиент)

**Статус:** утверждённый проект (v2.1 — синхронизирован с единым бэкендом `vaiwu-service`).  
**Серверная часть:** отдельный приватный репозиторий [`zimin3d/vaiwu-service`](https://github.com/zimin3d/vaiwu-service) (`docs/architecture.md`) — единый сервис для LLM-прокси, каталога/паков героинь, STT и TTS (для Android и ESP32).  
**Связанные файлы:** `PetViewModel.kt`, `SecretStore.kt`, `Sprite.kt`, `GitHubUpdateManager.kt`, `OnboardingScreen.kt`, `AssistantScreens.kt`, `combat/EncounterBuilder.kt`, `combat/BattlefieldCanvas.kt`, `res/xml/backup_rules.xml`.

---

## 1. Цель и ключевые принципы

Позволить пользователю скачивать дополнительных героинь (персонажей) с нашего сервера и использовать их в `vaiwu-android` офлайн — без изменения C++-ядра `external/vaiwu` (ESP32) и без нарушения обратной совместимости сохранений `pet.save` (V1–V6).

Каждый пак героини содержит:
- **визуальные ассеты**: готовые прозрачные PNG/WebP спрайты эмоций (`sprite_<id>_<emotion>.png` + опциональные кадры анимации `_01`, `_02`, ...), иконку (`icon.png`) и портрет (`portrait.png`);
- **метаданные (`meta.json`)**: версия схемы, минимальная версия приложения, базовый архетип (`archetypeId: 0..7`), имя, характер, любимая еда, стартовые статы, тайминги анимаций и контекстные реплики.

Доступ к каталогу и скачиванию паков открыт **только при наличии валидного ключа доступа** (`heroineAccessKey`). Без ключа или при невалидном ключе сервер возвращает `401/403` и не отдаёт ни список, ни архив.

---

## 2. Ключевые архитектурные решения

| Вопрос | Решение | Обоснование |
|---|---|---|
| Ключ доступа | Единый `Vaiwu Access Key` (`vw_...`) в `SecretStore` (AES-256-GCM в AndroidKeyStore), выдаваемый сервером `vaiwu-service` со scopes (`chat`, `heroines`, `stt`, `tts`). | Один ключ для всех функций Vaiwu; мастер-ключи OpenRouter/STT/TTS хранятся только на сервере и не попадают на устройства. |
| Интеграция с C++ (`CharacterId 0..7`) | **Архетип + оверлей:** `meta.json` задаёт `archetypeId: 0..7` для C++ и тактического боя; выбранный `activeHeroineId: String?` хранится в `filesDir/active_heroine.json`. | Не ломает `VaiwuPet` (ESP32), форматы `pet.save` V1–V6 и баланс умений. |
| Хранение паков | Тяжёлые файлы паков — в `context.noBackupFilesDir/heroines/<id>/`; лёгкие индексы (`heroines_index.json`, `active_heroine.json`) — в `filesDir`. | Android Auto Backup ограничен 25 МБ; `noBackupFilesDir` не ломает бэкап `pet.save` и не очищается ОС как `cacheDir`. |
| Формат спрайтов | Готовые прозрачные PNG/WebP (≤ 640×640 px), подготовленные на сервере/в CI. Без magenta-конвертации на телефоне. | Исключает OOM, тормоза при установке и артефакты розовой каймы на Android. |
| Безопасность ZIP | Проверка SHA-256, лимит архива (25 МБ), лимит распакованного объёма (48 МБ, Zip Bomb), лимит файлов (80), allowlist расширений (`.json`, `.png`, `.webp`), проверка `canonicalPath`, атомарный staging-swap. | Защита от Zip Bomb, path traversal и повреждения установленной героини при обрыве обновления. |
| Отзыв ключа (`401/403`) | Уже скачанные героини **не удаляются** и продолжают работать офлайн; блокируется только обновление каталога и загрузка новых паков. | Уважение к пользователю и честный офлайн-режим. |
| Редиректы и CDN | Ручной обход редиректов с allowlist хостов; заголовок `Authorization` **никогда** не пересылается при смене хоста (либо сервер выдаёт короткоживущий signed URL). | Исключает утечку `Bearer`-ключа в логи стороннего CDN/S3. |
| Серверная часть | Self-hosted сервис на Python (FastAPI) + SQLite + Docker Compose + Caddy (авто-HTTPS) + CLI-инструмент сборки паков `build_pack.py`. | Минимальный ops, быстрый деплой, совместимость с существующей инфраструктурой `Caddyfile` / Docker. |

---

## 3. Общая архитектура системы

```text
┌──────────────────────────────────────────────────────────────────────┐
│                        Android (vaiwu-android)                       │
│                                                                      │
│  UI: HeroinesScreen (Settings -> Героини) + OnboardingScreen         │
│       │                                                              │
│  HeroineRepository (координатор, Mutex, отмена Job, StateFlow)       │
│   ├── HeroineApi (HTTPS, Bearer heroineAccessKey, ETag, allowlist)   │
│   ├── HeroineDownloader (ZIP, SHA-256, Zip Bomb guard, staging-swap) │
│   └── HeroineStore (noBackupFilesDir/heroines + filesDir/index)      │
│                                                                      │
│  SpriteResolver (HeroineStore -> fallback assets/) + Cache Eviction  │
│   └── используется в Sprite.kt и combat/BattlefieldCanvas.kt         │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ HTTPS (TLS 1.2+)
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│                      Caddy (Reverse Proxy + TLS)                     │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│                   Vaiwu Heroines Service (FastAPI)                   │
│                                                                      │
│  Auth Middleware (SHA-256 хэш ключа, constant-time compare, RL)      │
│   ├── GET /v1/health                  (без ключа, мониторинг)        │
│   ├── GET /v1/heroines                (каталог + ETag)               │
│   ├── GET /v1/heroines/{id}/preview   (миниатюра <= 512 КБ)          │
│   └── GET /v1/heroines/{id}/pack      (ZIP + X-Pack-Sha256)          │
│                                                                      │
│  Storage: SQLite (keys, audit log) + Volume (/data/packs/*.zip)      │
│  CLI: manage_keys.py (issue/revoke) · build_pack.py (validate/zip)   │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 4. Серверный API-контракт (`v1`)

Базовый URL: конфигурируемый (`https://heroines.vaiwu.example/v1`, пинится через allowlist хостов на клиенте).

### 4.1. Аутентификация и ошибки
- Заголовок на всех защищённых эндпоинтах: `Authorization: Bearer <heroineAccessKey>`.
- Ключи в базе сервера хранятся **только в виде SHA-256 хэша с солью** (никакого plaintext в БД и логах); сравнение через `hmac.compare_digest`.
- Rate limiting: например, `60 req/min` на ключ/IP для каталога, `10 req/min` для скачивания паков.
- Коды ответов:
  - `200 OK` — успех.
  - `304 Not Modified` — каталог не изменился (по `If-None-Match`).
  - `401 Unauthorized` — ключ отсутствует или невалиден.
  - `403 Forbidden` — ключ отозван или не имеет доступа к этому паку.
  - `404 Not Found` — героиня не найдена.
  - `429 Too Many Requests` — превышен лимит запросов (заголовок `Retry-After`).

### 4.2. Эндпоинты

#### `GET /v1/heroines`
Возвращает список доступных для данного ключа героинь. Поддерживает `ETag` / `If-None-Match`.
```json
{
  "schemaVersion": 1,
  "heroines": [
    {
      "id": "luna",
      "version": "1.0.0",
      "minAppVersion": "0.2.2",
      "archetypeId": 4,
      "name": "Luna",
      "title": "Лунная алхимик",
      "description": "Спокойная любительница ночных зелий и тёплого чая.",
      "sizeBytes": 4210880,
      "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "previewPath": "/v1/heroines/luna/preview"
    }
  ]
}
```

#### `GET /v1/heroines/{id}/preview`
Возвращает оптимизированный PNG/WebP превью (≤ 256×256 px, ≤ 512 КБ) с `Cache-Control: private, max-age=86400`. Требует валидный ключ.

#### `GET /v1/heroines/{id}/pack`
Отдаёт ZIP-архив пака потоком (`application/zip`) с заголовками:
- `X-Pack-Sha256: <64-hex>`
- `Content-Length: <bytes>`
- `Content-Disposition: attachment; filename="luna-1.0.0.zip"`

*(Опциональное расширение для масштабирования через S3/R2: возврат `200 JSON` с короткоживущим `downloadUrl` + `sha256`, без передачи `Authorization` на внешний хост.)*

#### `GET /v1/health`
Публичный healthcheck (`{"status": "ok"}`) без утечки версий и метаданных.

---

## 5. Формат пака и схема `meta.json` (v1)

### 5.1. Структура ZIP-архива
```text
meta.json
icon.png                     # 64..128 px, прозрачный PNG/WebP
portrait.png                 # до 640 px, прозрачный PNG/WebP
sprites/
  sprite_<id>_idle.png       # ОБЯЗАТЕЛЬНО (минимум 1 кадр idle)
  sprite_<id>_idle_01.png    # опциональные кадры анимации
  sprite_<id>_happy.png      # опционально (при отсутствии fallback -> idle)
  sprite_<id>_sad.png
  sprite_<id>_angry.png
  sprite_<id>_sleepy.png
  sprite_<id>_sick.png
  sprite_<id>_dead.png
```

### 5.2. Схема `meta.json`
```json
{
  "schemaVersion": 1,
  "minAppVersion": "0.2.2",
  "id": "luna",
  "version": "1.0.0",
  "archetypeId": 4,
  "name": "Luna",
  "title": "Лунная алхимик",
  "personality": "Спокойная и загадочная",
  "favoriteFood": "Чай с моти",
  "baseStats": {
    "str": 10,
    "vit": 11,
    "agi": 13,
    "mag": 16,
    "luck": 12
  },
  "frameDurationMs": {
    "idle": 420,
    "happy": 260,
    "sleepy": 650,
    "other": 220
  },
  "phrases": {
    "idle": ["Звёзды сегодня особенно яркие."],
    "feed": ["Ммм, тёплый чай и моти!"],
    "victory": ["Алхимия снова не подвела!"],
    "fainted": ["Нужно немного отдохнуть..."]
  }
}
```
Валидация на клиенте:
- `id` — только `^[a-z0-9_-]{2,32}$` (никаких точек, слэшей и коллизий со встроенными `characterIds`).
- `schemaVersion == 1`, `isVersionAtLeast(BuildConfig.VERSION_NAME, minAppVersion)`.
- `archetypeId` в диапазоне `0..7`.
- Все значения `baseStats` клампятся в допустимый диапазон (`5..25`), строки обрезаются по лимитам длины.

---

## 6. Клиентская архитектура (Android)

### 6.1. `SecretStore` (расширение)
- Добавить `readHeroineKey(): String`, `writeHeroineKey(rawKey: String): Boolean`, `hasHeroineKey(): Boolean`.
- Хранение в том же `EncryptedKeyPref`-формате (`AES/GCM/NoPadding`, `AndroidKeyStore`), под отдельным ключом преференсов `heroine_access_key_enc`.

### 6.2. `HeroineApi`
- Использует `HttpURLConnection` с `instanceFollowRedirects = false` (ручной контроль редиректов по allowlist, аналогично `GitHubUpdateManager`).
- При редиректе на другой хост заголовок `Authorization` **не отправляется**.
- Ограничение размера JSON-ответа (`256 КБ`), таймауты (`10s` connect, `15s` read / `60s` pack).
- Поддержка `ETag` / `If-None-Match` с кэшем каталога в `cacheDir/heroines_catalog.json`.

### 6.3. `HeroineDownloader`
- Потоковое скачивание в `cacheDir/heroines/<id>.zip.part` с колбэком прогресса `(receivedBytes, totalBytes)` и поддержкой `CancellationException`.
- Лимиты: `MaxPackZipBytes = 25 MB`, `MaxPackUnpackedBytes = 48 MB`, `MaxPackEntries = 80`.
- Потоковый подсчёт `SHA-256` и сверка с `meta.sha256` (и `X-Pack-Sha256`, если задан).
- Распаковка во временную директорию `noBackupFilesDir/heroines/<id>.staging/`:
  - отклонение записей с `..`, ведущим `/`, обратным слэшем `\`, неразрешёнными расширениями;
  - проверка `dest.canonicalPath.startsWith(stagingDir.canonicalPath + File.separator)`;
  - валидация `meta.json` и наличия `sprites/sprite_<id>_idle.png` (или `_00.png`).
- Атомарная замена: `<id>` → `<id>.old` → `<id>.staging` → `<id>` → удаление `<id>.old` и временного `.zip.part`. При любой ошибке — полный откат.

### 6.4. `HeroineStore`
- Паки: `context.noBackupFilesDir/heroines/<id>/`.
- Индекс установленных: `AtomicFile(File(context.filesDir, "heroines_index.json"))`.
- Активная кастомная героиня: `AtomicFile(File(context.filesDir, "active_heroine.json"))` (`{"heroineId": "luna"}` или удалён для встроенной).
- Методы: `listInstalled()`, `getMeta(id)`, `spriteDir(id)`, `installStaged(id, stagingDir, meta)`, `delete(id)` (с проверкой, что `id != activeHeroineId`), `activeHeroineId()`, `setActiveHeroineId(id?)`.

### 6.5. `HeroineRepository`
- `StateFlow<HeroineCatalogState>` объединяет онлайн-каталог (или кэш в офлайне) + установленные паки + текущие состояния загрузки (`NotInstalled`, `Downloading(received, total)`, `UpdateAvailable`, `Installed`, `Error(message)`).
- Конкурентность: `Mutex` на запись индекса и `Map<String, Job>` для защиты от двойного старта и поддержки отмены загрузки (`cancelDownload(id)`).
- После `download()` или `delete()` вызывает `SpriteBitmapCache.evictByPrefix("heroine:$id/")`.

### 6.6. `SpriteResolver` и интеграция с игрой
- Выделить единый резолвер спрайтов (`SpriteSource.Asset(name)` vs `SpriteSource.File(file)`):
  - если активна кастомная героиня `id`, `PetSprite` и `BattlefieldCanvas` сначала ищут кадры `sprite_<id>_<emotion>(_NN)?.png` в `HeroineStore.spriteDir(id)`;
  - если конкретной эмоции нет — fallback на `sprite_<id>_idle.png`;
  - если файл удалён/повреждён — fallback на встроенные `assets/` по `archetypeId`.
- `OnboardingScreen`: отображает встроенных героинь (8) + установленных кастомных героинь с бейджем; выбор кастомной вызывает `createPet(archetypeId, name, customHeroineId = id)`.
- `PetViewModel`:
  - при старте читает `activeHeroineId`; если файл пака отсутствует (например, после восстановления бэкапа на новом телефоне) — мягко падает на встроенный `archetypeId` и показывает уведомление «Докачайте героиню в настройках»;
  - `phrase(context)` сначала проверяет `meta.phrases[context]`, при отсутствии — fallback на C++ `pet.phrase(context)`.

---

## 7. UI/UX (по дизайн-системе `vaiwu-android`)

- Вход: кнопка **«Героини»** в `SettingsScreen` (внутри `SectionCard` «Ассистент» или отдельной секции) + ссылка «Скачать ещё» в `OnboardingScreen`.
- Экран `HeroinesScreen`:
  - использует `SectionCard`, `VaiwuPrimaryButton`, `VaiwuSecondaryButton`, `VaiwuSpacing`, `pressScale`, минимум `48.dp` на все кнопки;
  - **нет ключа:** `SectionCard` с пояснением и кнопкой «Указать ключ в настройках»;
  - **ключ невалиден (`401/403`):** баннер «Ключ не зарегистрирован или отозван» + переход в настройки; установленные героини остаются видимыми ниже;
  - **карточка героини:** превью, имя, подпись, размер (`МБ`), статус:
    - не установлена → «Скачать»;
    - загружается → `LinearProgressIndicator` + кнопка «Отмена»;
    - есть новая версия → «Обновить»;
    - установлена → «Удалить» (отключена с подписью «Активна», если это текущий питомец);
  - **офлайн:** показывает установленных героинь + кэшированный список (если есть) с чипом «Офлайн» и кнопкой «Обновить».

---

## 8. Серверная структура (отдельный модуль `server/heroines-service/`)

```text
heroines-service/
├── app/
│   ├── main.py              # FastAPI приложение, роуты /v1/*
│   ├── auth.py              # Проверка Bearer-ключа (SHA-256 + salt, rate limit)
│   ├── catalog.py           # Чтение и кэширование каталога паков + ETag
│   ├── db.py                # SQLite: таблицы api_keys, download_audit
│   └── config.py            # Настройки из env (DATA_DIR, KEY_SALT, RATE_LIMIT)
├── tools/
│   ├── build_pack.py        # Валидация meta.json, ресайз <=640px, сборка ZIP + SHA-256
│   └── manage_keys.py       # CLI: выдача, просмотр, отзыв ключей доступа
├── tests/
│   ├── test_api.py          # Тесты 401/403/200/304/404, скачивание, SHA-256
│   └── test_build_pack.py   # Тесты валидации паков и схемы
├── Dockerfile
├── docker-compose.yml       # FastAPI + Caddy (auto-HTTPS)
├── Caddyfile
└── requirements.txt
```

---

## 9. Поэтапный план реализации

### Этап 1. Сервер и сборщик паков (`heroines-service/`)
1. Реализовать `tools/build_pack.py` (валидация `meta.json` v1, проверка/ресайз спрайтов, сборка ZIP, расчёт SHA-256).
2. Реализовать `app/` (FastAPI + SQLite + `manage_keys.py` + `ETag` + rate limit) и `tests/`.
3. Добавить `Dockerfile`, `docker-compose.yml`, `Caddyfile` и инструкцию по деплою.

### Этап 2. Android — слой данных, сети и безопасности
1. `SecretStore`: добавить `heroineAccessKey` + unit-тесты.
2. `HeroineModels.kt` + парсинг/валидация `meta.json` v1 + unit-тесты.
3. `HeroineApi.kt` (HTTPS allowlist, `ETag`, защита `Authorization` при редиректах) + unit-тесты.
4. `HeroineStore.kt` (`noBackupFilesDir`, `AtomicFile` индексы) + `HeroineDownloader.kt` (SHA-256, Zip Bomb guard, path traversal guard, staging-swap) + unit-тесты.

### Этап 3. Android — интеграция и UI
1. `SpriteResolver` + `SpriteBitmapCache.evictByPrefix` (подключить в `Sprite.kt` и `BattlefieldCanvas.kt`).
2. `HeroineRepository` + интеграция `activeHeroineId` в `PetViewModel` и `OnboardingScreen`.
3. `HeroinesScreen` + поле «Ключ доступа к героиням» в `SettingsScreen` + локализация в `strings.xml`.
4. Прогон всех unit-тестов, `SourceGuardTest`, CI (`Android APK` + `CodeQL`).
