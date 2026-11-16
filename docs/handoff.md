# Handoff: редизайн интерфейса Vaiwu

> Для продолжения работы в новой сессии. Состояние на 16 ноября 2026,
> `main = 7b92105`, релиз `v0.1.3`. Рабочее дерево чистое; этап 1 закоммичен
> и прошёл зелёный CI (см. разделы 1–2 и 13).

## 1. Где остановились

Сделаны два шага:

1. Аудит интерфейса превращён в нормативную спецификацию `docs/ui-spec.md`.
2. Реализован и проверен в CI этап 0 — фундамент дизайн-системы.

**Этап 1 (каркас, навигация, дом, остальные экраны) закоммичен** в
`bee5a1d`, после чего последовали исправления по кодревью и ошибкам CI
(`38ee515`, `1a5513a`, `d6dba72`, `7b92105`). Матрица в `ui-spec.md` §12
обновлена: этап 1 — «готово».

В рамках этапа 1 сделано:

- каркас: `VaiwuScaffold` (top/bottom bar, snackbar, insets), `VaiwuApp`;
- навигация: `Navigation` (`AppScreen`, `AppTab`, back-правила), `PetViewModel`
  (стек экранов, snackbar/reply, подтверждение выхода из раунда);
- дом: `HomeScreen`, `HomeActions`, `ActionFeedback`, `VaiwuIcons`,
  `Presentation`, `strings.xml` (включая `plurals`);
- онбординг: `OnboardingScreen` («Поселить» вместо мгновенного создания);
- **остальные экраны тоже написаны**: `GameScreens.kt` (игры, 4 мини-игры,
  бой), `EconomyScreens.kt` (магазин, питомец, «камень-ножницы-бумага»),
  `AssistantScreens.kt` (чат, настройки, справка);
- JVM-тесты: `NavigationTest`, `HomeActionsTest`, `PluralRulesTest`,
  обновлённый `ActionFeedbackTest`; 5 `@Preview` дома (включая font scale 1.3).

**Этап 1 закрыт:** UI-код закоммичен, `Android APK` и `CodeQL` на `7b92105`
— зелёные. Все находки кодревью и ошибки компиляции, найденные CI, исправлены
(раздел 13).

Механику не трогали: изменения только в UI-слое и ресурсах.

## 2. Состояние репозитория

```
main = 7b92105  Fix FilterChip and exhaustive when flagged by CI
       d6dba72  Fix more Compose API mismatches from CI
       1a5513a  Fix Compose API mismatches flagged by CI
       bee5a1d  Complete stage 1 UI redesign and address review findings
       38ee515  Validate PNG row count before encoding
       447e48a  Add reproducible sprite slicing pipeline and refresh AI art
```

- Релиз **v0.1.3** опубликован (тег `447e48a`): подписанный `vaiwu-android.apk`
  (~9.7 МБ) с новыми изображениями. ⚠️ Релиз собран **до** UI-редизайна —
  новый интерфейс этапа 1 в нём ещё нет; для него нужен следующий тег.
- **CI на `7b92105` (текущий `main`): `Android APK` success, `CodeQL` success.**
  Промежуточные коммиты `bee5a1d`, `1a5513a`, `d6dba72` падали на компиляции
  Kotlin — все ошибки исправлены.
- Submodule `external/vaiwu` → `9a56022` (прозрачные AI-спрайты), чистое.
- Рабочее дерево чистое. Локальные проверки: native-тесты PASSED,
  art-тесты 101/101 OK. Kotlin/Compose проверены только в CI.

Локально **нет JDK и Android SDK**: `java`, `kotlinc`, `ANDROID_HOME`
отсутствуют. Любая проверка Kotlin/Compose — только через GitHub Actions.

## 3. Документы

| Файл | Роль |
|---|---|
| `docs/ui-spec.md` | **нормативный** документ: инварианты, токены, компоненты, требования с ID, трассировка, DoD, матрица этапов |
| `docs/ui-redesign-plan.md` | обоснование, аудит, макеты экранов, оценки сроков |
| `docs/stabilization-plan.md` | качество, механики, регрессии |
| `docs/review-v0.1.2.md` | ревью релиза |
| `docs/mechanics-check.md` | разбор смерти от болезни во сне (исправлено) |

При расхождении приоритет у `ui-spec.md`.

## 4. Что уже реализовано (этап 0)

| Файл | Содержимое |
|---|---|
| `app/src/main/java/su/neit/vaiwu/Palette.kt` | 21 цветовой токен в виде ARGB `Long`, единый источник значений |
| `app/src/main/java/su/neit/vaiwu/DesignTokens.kt` | `VaiwuSpacing`, `VaiwuRadius`, `VaiwuSizes`, `StatTone`, `statTone()` |
| `app/src/main/java/su/neit/vaiwu/Theme.kt` | полная схема Material 3, типографика (10 стилей), формы |
| `app/src/main/java/su/neit/vaiwu/Components.kt` | `VaiwuPrimaryButton`, `VaiwuSecondaryButton`, `StatMeter`, `StatusChip`, `CoinChip`, `SectionCard`, `EmptyState`, `toneColor()` |
| `app/src/main/java/su/neit/vaiwu/VaiwuApp.kt` | главный экран переведён на `StatMeter`, новые кнопки и чип «Болеет» |
| `app/src/main/res/values/styles.xml` | фон окна `#141021` = `colorScheme.background` |
| `app/src/main/res/values/strings.xml` | добавлены `pet_sick_short`, `stat_a11y` |
| `app/src/test/java/su/neit/vaiwu/DesignTokensTest.kt` | контраст текстовых пар, непрозрачность палитры, пороги статов, значения токенов |

Проверенные контрасты: текст 16.5:1, вторичный 8.1:1, danger 6.8:1,
обводка 3.6:1. Все текстовые пары ≥ 4.5:1.

## 5. Решения, которые нельзя менять без обсуждения

1. **Вариант A** — «уютная тёмная комната». Тёмная тема, пастель, пиксельные
   иконки, плоские карточки с обводкой.
2. **Четыре вкладки**: Дом, Игры, Питомец, Чат. Настройки — из шестерёнки.
3. **Шрифт** — системный. Nunito/пиксельный шрифт только после отдельного
   решения и проверки кириллицы.
4. **Светлой темы нет**; при появлении запроса — отдельная задача.
5. **Механика не меняется.** UI показывает реальный результат, а не обещает
   недоступное действие.
6. **Ассеты не генерируются** без `VAIWU_ART_API_KEY` / `VAIWU_ART_BASE_URL` /
   `VAIWU_ART_MODEL`. Встроенного генератора изображений у агента нет.

## 6. Правила работы

- Русские документы и тексты интерфейса; коммиты — по-английски.
- Каждый исправленный дефект — с регрессионным тестом, если слой тестируем.
- Перед отчётом о готовности: native-тесты, art-тесты и зелёный `Android APK`.
- Нельзя ослаблять или удалять тесты ради сборки.
- Пуш в `main` запускает CI; ждать финальный результат.

Локальные проверки:

```sh
python3 app/src/test/cpp/run_host_tests.py
python3 -m unittest discover -s tools/art -t tools/art
```

CI после пуша (без `gh`):

```sh
SHA=$(git rev-parse HEAD)
curl -s "https://api.github.com/repos/zimin3d/vaiwu-android/actions/runs?head_sha=$SHA" \
  | python3 -c "import json,sys; [print(r['name'], r['status'], r.get('conclusion'), r['html_url']) for r in json.load(sys.stdin)['workflow_runs']]"
```

## 7. Следующий шаг: этап 1

Требования: `I-1…I-6`, `UI-NAV-01…07`, `UI-HOME-01…07`, `UI-ONB-01…03`,
`UI-CONTENT-01/02` из `docs/ui-spec.md`.

### 7.1 Каркас и навигация (P0)

Файлы: новый `VaiwuScaffold.kt`, `VaiwuApp.kt`, `Economy.kt` (`AppScreen`),
`PetViewModel.kt`.

- `VaiwuScaffold`: top bar, bottom bar, snackbar, `WindowInsets`.
- `VaiwuTopBar`: назад/шестерёнка, заголовок, 1–2 действия; убрать версию
  сборки из шапки (`UI-AST-08`).
- `VaiwuBottomBar`: 4 вкладки, скрыт на фокусных экранах (`UI-NAV-01/02`).
- `AppScreen` разделить на вкладки и фокусные экраны; Back на вкладке →
  «Дом», на детальном → назад (`UI-NAV-03`).
- Подтверждение выхода из активного раунда (`UI-NAV-04`, `UI-GAME-09`):
  перехватывать и `BackHandler`, и кнопки «Домой» на экранах игр/боя.
- Удалить `ScreenHeader` с `Spacer(72.dp)`.

Риск: `openHome()` в `PetViewModel` сейчас молча отменяет раунд
(`cancelPlay()`, `leaveBattle()`). Подтверждение должно быть в UI до вызова,
иначе теряется награда.

### 7.2 Главный экран (P0)

- `PetStage`: сцена 35–45% высоты, градиентные скримы вместо `0xB318122B`
  на весь экран (`UI-HOME-01`).
- Сетка ухода 2×3 с `CareActionButton`; кормление — одно нажатие
  (`UI-HOME-02`).
- Состояния сна и смерти без потери навигации (`UI-HOME-03/04`).
- Баннер болезни с лекарством (`UI-HOME-06`).
- Snackbar для системных сообщений, облачко — для речи питомца
  (`UI-HOME-05`).

### 7.3 Онбординг (P0)

- Выбор персонажа не создаёт питомца; создаёт «Поселить» (`UI-NAV-07`,
  `UI-ONB-01`).
- Пустое имя → имя персонажа (`UI-ONB-02`).

### 7.4 Быстрые победы (можно отдельным PR)

1. Не скрывать чат/настройки/справку при сне и смерти.
2. Подтверждение выхода из раунда.
3. «Поселить» вместо мгновенного создания.
4. Snackbar + `navigationBarsPadding()` вместо нижней строки.
5. `FilterQuality.None` для фонов, плейсхолдер для спрайтов.
6. `plurals` для монет и дней.
7. Версия — только в настройках.

### 7.5 Тесты этапа 1

- JVM: доступные действия по состоянию питомца; правила навигации
  (активная вкладка, показывать ли back); форматирование монет/дней.
- Compose/ручные сценарии из `ui-spec.md`, раздел 10.2.
- `@Preview` для «Дома»: норма, сон, болезнь, смерть, длинные строки,
  font scale 1.3.

## 8. Что не делать сейчас

Этапы 2–5 (игры/бой, магазин/питомец, чат/настройки/справка, полировка)
не начинать, пока не закрыт этап 1: иначе навигация переедет дважды.

## 9. Известные ловушки

- **`AssetImage`** (`Sprite.kt`) не умеет `FilterQuality`; при загрузке
  рисует пустой `Box` — экран «прыгает». Кэш 16 MB.
- **Фоны** `home_*.png` — 320×140, растягиваются сильно; `FilterQuality.None`
  обязателен, но сделает пиксели резкими — проверить на устройстве.
- **Строки**: ~190 уникальных русских литералов в Kotlin (55 в
  `GameScreens.kt`). Полный вынос — задача этапа 1/4, не делать частично.
- **`pet_sick` и `percent`** больше не используются в коде, но оставлены
  в `strings.xml` под баннер болезни. Lint может предупреждать об
  неиспользуемых ресурсах — это не ошибка сборки.
- **`internal`-токены** (`Palette`, `VaiwuSpacing`) доступны unit-тестам:
  friend paths работают, проверено на `SoundMappingTest`.
- **`Scaffold` уже применяет insets**; проблема была в цвете окна, а не в
  их отсутствии. Не добавлять `enableEdgeToEdge()` вслепую: сначала
  проверить на API 35.
- **`PetHome`** использует `java.util.Calendar` напрямую в композиции —
  при рефакторинге вынести час в состояние/параметр.
- **`Dimensions`**: `Button` в остальных экранах остаются Material-кнопками
  (pill), новые `Vaiwu*Button` — 12 dp. Это осознанно до этапа 1.

## 10. Ассеты

Состояние на 16 ноября 2026: **все 55 ассетов сгенерированы и опубликованы**.

- Документация: `tools/art/README.md`.
- Промпты: `tools/art/art_prompts.py`, справочник `tools/art/prompts/prompts.md`.
- CLI: `tools/art/gen_art.py` (`--list`, `--no-generate`, `--characters`,
  `--emotions`, `--monsters`, `--assets`, `--force`, `--retries`,
  `--retry-delay`).
- Нужны `VAIWU_ART_API_KEY`, `VAIWU_ART_BASE_URL`, `VAIWU_ART_MODEL`
  (любой OpenAI-совместимый `/v1/images/generations`).

### 10.1 Как сгенерированы

- 49 спрайтов (7 персонажей × 7 эмоций) + 6 монстров, модель `gpt-image-1`
  через релей `https://api.a6api.com/v1` (ключ вне репозитория).
- Сырые картинки 1254×1254 лежат в `tools/art/out/` (каталог в `.gitignore`,
  в репозиторий не коммитится).
- `gpt-image-1` через этот релей отдаёт честный PNG, но квадрат 1254×1254
  вместо запрошенных 1024×1536.
- `nano-banana` для спрайтов не годится: отдаёт JPEG и игнорирует `size`
  (ландшафт 1376×768).

### 10.2 Нарезка и публикация

- Галерея: **https://vaiwu.t.neit.su/** (`tools/gen_site.py` из прошивки).
  Этот хост и есть сервер: nginx-сайт `/etc/nginx/sites-available/vaiwu`,
  корень `/var/www/vaiwu`, картинки в `arts/`.
- Пайплайн **закоммичен** в `447e48a`: `tools/art/slice_art.py` + `pngio.py`
  (PNG-кодек на стандартной библиотеке, **без Pillow в CI**), 101 тест в
  `test_slice_art.py`. Кроп по содержимому на белом фоне + вписывание в кадр →
  96×128 для спрайтов, 96×96 для монстров; пересобирает оба контактных листа.
- Историческая публикация на сайте делалась разовым скриптом с Pillow в
  отдельном venv (`/tmp/opencode/artvenv`); в систему ничего не ставилось.
  Теперь воспроизводится коммитируемым шагом:
  `python3 tools/art/gen_art.py && python3 tools/art/slice_art.py`.
- Опубликованы все 55 файлов в `/var/www/vaiwu/arts/` (владелец `www-data`),
  проверено по HTTPS: 200, размеры и SHA-256 совпадают.
- Бэкап прежних процедурных картинок: `/tmp/opencode/web-arts-backup-all/`.

### 10.3 Что осталось

- ✅ ~~Оформить нарезку как коммитируемый шаг с тестами~~ — сделано в
  `447e48a` (`pngio.py`, `slice_art.py`, `test_slice_art.py`, stdlib-only).
- ✅ ~~Собрать релиз с новыми изображениями~~ — v0.1.3 опубликован.
- ❌ Генерация `sprites.c` из нарезанных PNG: прошивка по-прежнему рисует
  спрайты процедурно (`tools/gen_sprites.py`), PNG — только превью.
- Информация: повторный запуск генерации пропускает уже существующие файлы
  (`--force` для перегенерации).

## 11. Открытые вопросы

1. Нужен ли пятый пункт «Ещё» в bottom bar? Текущее решение — нет.
2. Nunito или пиксельный шрифт? Текущее решение — системный.
3. Скриншот-тесты (Paparazzi/Roborazzi) — сразу после этапа 1 или позже?
4. Нужна ли отдельная тема для планшета/landscape? Пока только правила
   из `UI-A11Y-04/05`.

## 12. Чек-лист старта новой сессии

1. `git status` — чистое; `git log -1` — `7b92105` или новее.
2. Прочитать `docs/ui-spec.md`, разделы 3, 4, 6.2, 12, и раздел 13 этого файла
   (результаты кодревью).
3. Этап 1 закрыт. Дальше — этап 2 (игры/бой) по §8, но только после снятия
   open-вопросов §11.
4. После изменений: native-тесты, art-тесты, пуш, зелёный `Android APK`.
5. Обновить матрицу в `docs/ui-spec.md`, раздел 12.
6. Если нужен релиз с новым UI — создать тег `v0.1.4` (в v0.1.3 UI ещё старый).

## 13. Кодревью последних изменений (16 ноября 2026)

Проверены: коммит `447e48a` (арт-пайплайн) и UI-код этапа 1, который на
момент ревью был ещё незакоммичен (позже зафиксирован в `bee5a1d`).

### Арт-пайплайн (`447e48a`)

- Найден и исправлен дефект: `pngio.encode_png()` проверял длину каждой
  строки, но не количество строк — при несовпадении молча писал PNG, у
  которого IHDR обещал больше сканлайнов, чем было в IDAT. Добавлена проверка
  `len(rows) == height` и регрессионный тест `test_encode_validates_the_row_count`.
  Исправление запушено отдельным коммитом `38ee515`.
- Остальное (кодек, нарезка, ретраи, фильтрация) замечаний не вызвало;
  101 art-тест зелёный.

### UI-WIP этапа 1 — найдено и исправлено

**Блокеры (компиляция/тесты):**

1. `VaiwuApp.kt:293` — `stringResource(R.plurals.pet_day, quantity=...)`:
   у `stringResource` нет параметра `quantity`, и `<plurals>` нельзя читать
   через `stringResource`. Заменено на `pluralStringResource(...)`.
2. `strings.xml` — `settings_sound_title/label/hint` объявлены дважды
   (строки 85–87 и 239–241): aapt2 уронил бы сборку. Дубликат удалён.
3. `BorderStroke` импортировался как `androidx.compose.foundation.border.BorderStroke`
   в 4 файлах (`GameScreens`, `EconomyScreens`, `HomeScreen`, `OnboardingScreen`) —
   неверный пакет. Заменено на `androidx.compose.ui.graphics.BorderStroke`.
4. `NavigationTest.kt:16` — ожидал `AppScreen.Home` от `backTarget(Home, [])`,
   хотя функция возвращает `null`. Тест исправлен на `assertNull`.

**Мажоры (регрессии поведения):**

5. `GameScreens.kt` — у «ловли сердец» потерялся `.clickable { onChoice(0) }`,
   игра завершалась только по таймауту. Клик возвращён (тач-цель 72 dp).
6. `VaiwuApp.kt` — стрелка «назад» в top bar обходила подтверждение выхода из
   раунда (I-3 / UI-NAV-04 / UI-GAME-09). Теперь использует тот же guard, что
   и `BackHandler`, и скрывается на вкладках.
7. `EconomyScreens.kt` — продажа снаряжения потеряла диалог подтверждения
   (I-3 / UI-ECO-07). Возвращён `ConfirmDialog` с названием и ценой.

**Миноры:**

8. Заголовок данжа показывал сырые `%1$d/%2$d` — теперь подставляет `room/rooms`.
9. Озвучка ответа питомца не срабатывала на новое сообщение — `LaunchedEffect`
   теперь привязан к тексту последнего ответа.
10. Вернул кнопку «Снаряжение» в магазине (параметр `onInventory` был неиспользуемым).
11. Переименован вводящий в заблуждение тест `tabScreensHideTheBottomBar`.
12. Баннер болезни больше не показывается спящему/мёртвому питомцу.

**Осталось открытым:** `PluralRulesTest` покрывает `russianPluralForm`, который
в проде не используется (плюрали выбираются ресурсами Android). Тест не
вреден, но не проверяет реальный вызов `pluralStringResource` — для этого
нужны Compose-тесты (см. §11, вопрос 3).

### Ошибки, найденные уже в CI (и исправленные)

Статическое ревью не поймало несовместимости с Compose BOM 2024.12.01 — их
выявил первый же прогон `Android APK` в CI. Все исправлены:

- `VaiwuIcons.kt` — `strokeCap` → `strokeLineCap` в `path()`;
- `VaiwuScaffold.kt` — nullable `bottomBar ?: {}`, `surfaceElevated` → `surface`,
  удалены `selectedContentColor`/`unselectedContentColor` у `NavigationBarItem`;
- `HomeScreen.kt` — `surfaceElevated` → `surface`;
- `Sprite.kt` — `Image(painter=…, filterQuality=…)` → `Image(bitmap=…, …)`
  (только overload с `bitmap` принимает `filterQuality`);
- `PetViewModel.kt` — `SystemMessage`/`PetSpeech` сделаны публичными,
  `application.getString` → `getApplication<Application>().getString`;
- `Navigation.kt` — добавлено `AppTab.screen` (использовалось, но не было);
- `AssistantScreens.kt` — `PasswordVisualTransformation` из `ui.text.input`,
  а не из `material3`;
- `EconomyScreens.kt` — `AssistChip(selected=…)` → `FilterChip`, импорт
  правильного `androidx.compose.foundation.BorderStroke`;
- `HomeActions.kt` — `when (lifeState)` expression дополнился веткой `else`
  (`LifeState` — это `object` с Int-константами, не enum).

Итог: `Android APK` и `CodeQL` на `7b92105` — **зелёные**. Локально Kotlin
не компилируется (нет JDK/SDK), подтверждение — только CI.
