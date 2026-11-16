# Handoff: редизайн интерфейса Vaiwu

> Для продолжения работы в новой сессии. Состояние на 16 ноября 2026.
> `main` — см. `git log -1` (последний подтверждённый CI: `80027ba`).
> Последний релиз: **`v0.1.4` = `11c5494`**. Коммиты после него (`7440e2b`,
> `80027ba`) в релиз **не входят**, новый релиз — только по решению владельца.
> Рабочее дерево чистое. Полная история — раздел 13.

## 1. Где остановились

Сделаны два шага:

1. Аудит интерфейса превращён в нормативную спецификацию `docs/ui-spec.md`.
2. Реализован и проверен в CI этап 0 — фундамент дизайн-системы.

**Этап 1 (каркас, навигация, дом, остальные экраны) закоммичен** в
`bee5a1d`, после чего последовали исправления по кодревью и ошибкам CI
(`38ee515`, `1a5513a`, `d6dba72`, `7b92105`), а затем два коммита с финальной
документацией (`a77117c`, `31ef960`). Матрица в `ui-spec.md` §12 обновлена:
этап 1 — «готово».

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

**Этап 1 — код закоммичен и зелёный в CI** (последний подтверждённый прогон —
`80027ba`). После закрытия этапа добавлены: исправление падения при продаже
(`a162cc5`), вынос строк и часы в бою (`7440e2b`), версия в настройках
(`80027ba`). **Ручная проверка по DoD не выполнена** — нужен телефон (раздел 13.4).
Все находки кодревью другого агента и ошибки компиляции, найденные CI,
исправлены и задокументированы (раздел 13).

Механику не трогали: изменения только в UI-слое и ресурсах.

## 2. Состояние репозитория

```
main = 80027ba  Show app version in settings instead of nowhere          <- код
       7440e2b  Move remaining user-facing text to resources and fix clock read in battle
       11c5494  Update handoff after sell crash fix and prepare v0.1.4     <- релиз v0.1.4
       a162cc5  Fix crash when selling items: read plural with quantity
       4dee793  Record full review cycle and CI history in handoff
       31ef960  Clarify review scope in handoff
       a77117c  Record final review results and green CI in handoff docs
       7b92105  Fix FilterChip and exhaustive when flagged by CI
       d6dba72  Fix more Compose API mismatches from CI
       1a5513a  Fix Compose API mismatches flagged by CI
       bee5a1d  Complete stage 1 UI redesign and address review findings
       38ee515  Validate PNG row count before encoding
       447e48a  Add reproducible sprite slicing pipeline and refresh AI art
```

- Релиз **v0.1.3** (тег `447e48a`): подписанный `vaiwu-android.apk` (~9.7 МБ)
  с новыми изображениями, **без** UI-редизайна и **с** багом продажи (падение).
- Релиз **v0.1.4** (тег на коммите, который содержит этот абзац): новый
  интерфейс этапа 1 и исправление падения при продаже. Подписанный APK
  публикуется workflow'ом `release` по тегу `v*`.
- **CI зелёный:** `Android APK` #50 и `CodeQL` #42 на `4dee793` (равно как
  #49/#41 на `31ef960`, #48 на `a77117c`, #47 на `7b92105`). Промежуточные
  коммиты `bee5a1d` (#44), `1a5513a` (#45), `d6dba72` (#46) падали только на
  компиляции Kotlin — все ошибки исправлены.
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

## 7. Этап 1 (код выполнен, см. статус ниже)

> **Статус:** код этапа 1 написан и прошёл CI (раздел 1). Ниже — исходный план,
> сохранён для трассировки. Проверка по DoD (`ui-spec.md` §11) **не закрыта**:
> нет ручного прогона §10 и Compose-тестов — см. раздел 13.4.

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
- **Строки**: вынос завершён (раздел 13.5). Русский текст в Kotlin-коде
  запрещён `SourceGuardTest.russianTextIsNotHardcodedInKotlin`; исключение —
  только образцы текста для `@Preview`.
- **`pet_sick` и `percent`** больше не используются в коде, но оставлены
  в `strings.xml` под баннер болезни. Lint может предупреждать об
  неиспользуемых ресурсах — это не ошибка сборки.
- **`internal`-токены** (`Palette`, `VaiwuSpacing`) доступны unit-тестам:
  friend paths работают, проверено на `SoundMappingTest`.
- **`Scaffold` уже применяет insets**; проблема была в цвете окна, а не в
  их отсутствии. Не добавлять `enableEdgeToEdge()` вслепую: сначала
  проверить на API 35.
- **Часы в композиции**: закрыто (раздел 13.5). Час читается один раз в
  `VaiwuApp` и передаётся параметром; `SourceGuardTest.composablesDoNotReadTheClock`
  не даёт вернуть `Calendar` в composable-файлы.
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

1. `git status` — чистое; `git log -1` — `80027ba` или новее.
2. Прочитать `docs/ui-spec.md`, разделы 3, 4, 6.2, 12, и раздел 13 этого файла
   (результаты кодревью).
3. Этап 1 закрыт. Дальше — этап 2 (игры/бой) по §8, но только после снятия
   open-вопросов §11.
4. После изменений: native-тесты, art-тесты, пуш, зелёный `Android APK`.
5. Обновить матрицу в `docs/ui-spec.md`, раздел 12.
6. Релиз `v0.1.4` собран на `11c5494`. Коммиты `7440e2b` и `80027ba` в релиз не
   входят: новый тег — по решению владельца (см. раздел 13.4).

## 13. Полная история ревью и исправлений (16 ноября 2026)

### 13.0 Кто и что ревьюил

- **Арт-пайплайн** (`447e48a`) ревьюился основным агентом.
- **UI-код этапа 1** ревьюился **отдельным агентом (subagent `explore`)** с
  инструкцией «very thorough, read-only». На момент ревью код был ещё
  незакоммичен (позже зафиксирован в `bee5a1d`); локального JDK/SDK нет,
  поэтому ревью было **статическим** — ни один Kotlin-файл не компилировался.
- Все находки другого агента были проверены основным агентом по исходникам
  и либо исправлены, либо (в одном случае) опровергнуты. Затем код прошёл
  реальную компиляцию в GitHub Actions, где нашлись дополнительные ошибки.

Итоговая схема: **ревью агента → проверка и исправления основным агентом →
CI-прогон → исправление CI-ошибок → зелёный CI → фиксация в документации**.

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
   неверный пакет. Агент предложил `androidx.compose.ui.graphics.BorderStroke`,
   **но это тоже неверно** — правильный класс `androidx.compose.foundation.BorderStroke`.
   Ложная рекомендация была обнаружена CI (`Unresolved reference 'BorderStroke'`)
   и исправлена в `d6dba72`. Все 5 файлов (включая `Components.kt`) теперь
   импортируют `androidx.compose.foundation.BorderStroke`.
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

### 13.1 История прогонов CI (Android APK)

| Run | Коммит | Результат | Что нашёл / сделал |
|---|---|---|---|
| #43 | `38ee515` | ✅ success | Арт-фикс (проверка числа строк PNG) |
| #44 | `bee5a1d` | ❌ failure | Первый UI-коммит: `strokeCap`, nullable `bottomBar`, `surfaceElevated`, `selectedContentColor` |
| #45 | `1a5513a` | ❌ failure | `BorderStroke` (ложная рекомендация агента), `surfaceElevated` в `HomeScreen`, `application` в ViewModel, `Image(painter, filterQuality)`, internal-типы |
| #46 | `d6dba72` | ❌ failure | `AssistChip(selected=…)`, неисчерпывающий `when (lifeState)` |
| #47 | `7b92105` | ✅ success | Все ошибки компиляции устранены |
| #48 | `a77117c` | ✅ success | Документация (финальные результаты ревью) |
| #49 | `31ef960` | ✅ success | Уточнение текста про ревью |
| #50 | `4dee793` | ✅ success | Полная история ревью зафиксирована в handoff |
| #52 | `a162cc5` | ✅ success | Исправление падения при продаже + source-guard тест |
| #53 | `11c5494` | ✅ success | Документация + подготовка релиза |
| #54 | тег `v0.1.4` (`11c5494`) | ✅ success | Сборка подписанного APK, релиз v0.1.4 опубликован |
| #55 | `7440e2b` | ✅ success | Вынос строк, часы в бою, `SourceGuardTest` |
| #56 | `80027ba` | ✅ success | Версия в настройках |

`CodeQL` на `4dee793` — #42, тоже success. Run'ы #44–#46 падали **только** на
`:app:compileDebugKotlin` / `:app:compileReleaseKotlin`; native-тесты, art-тесты
и остальные шаги проходили.

### 13.2 Итог

- Все 12 находок другого агента рассмотрены; 11 исправлены, 1 (пакет
  `BorderStroke`) уточнён после сообщения от CI.
- Все 10+ ошибок компиляции, найденных CI, исправлены.
- Текущий `main = 80027ba`: `Android APK` #56 ✅ (включая `testDebugUnitTest` с
  `SourceGuardTest`), native-тесты ✅, art-тесты 101/101 ✅, `git status` чистый.
- Локально Kotlin не компилируется (нет JDK/SDK) — подтверждение только CI.

### 13.3 Баг продажи снаряжения (исправлен в `a162cc5`)

- **Симптом:** подтверждение продажи в «Питомце» → приложение падает.
- **Причина:** `PetViewModel.sell()` вызывал `text(R.plurals.sell, gold, gold)`.
  `text()` использует `getString()`, а тот не работает с `<plurals>`
  (`Resources.NotFoundException`). Диалог подтверждения (мажор #7) этот путь не
  затрагивал, поэтому кодревью и CI его пропустили.
- **Исправление:** вызов переведён на `quantity()` (`getQuantityString`).
- **Регрессионная защита:** в `PluralRulesTest` тест
  `pluralResourcesAreNeverReadWithGetString` читает исходники и падает, если
  `text(R.plurals.` или `stringResource(R.plurals.` появляется в коде. Ограничение:
  Robolectric в проекте нет, поэтому реальный вызов ресурса JVM-тестом не
  проверяется. Проверено: регулярное выражение ловит старую строку и пропускает
  новую; `grep` по репозиторию — совпадений нет.

### 13.4 Статус открытых пунктов

Закрыто в этой итерации (см. 13.5):

- ✅ Ловушка «Calendar в композиции» (`GameScreens.kt`).
- ✅ Вынос пользовательских строк в `strings.xml` (был ~43 литерала в шести файлах).
- ✅ Версия показана в настройках (§7.4 п.7).

Не закрыто и почему:

- ❌ **Ручная проверка DoD `ui-spec.md` §11, пункт 2** (чек-лист §10: сон, смерть,
  болезнь, навигация при сне, подтверждение выхода, продажа). Нужен физический
  Android-телефон или эмулятор с ручным прогоном. В этой среде устройства нет.
- ❌ **Compose/Robolectric-тесты** для «Дома», навигации и плюралей. Robolectric
  требует новые зависимости, а проект проверяет их по `gradle/verification-metadata.xml`
  (350 записей). Пересчитать его можно только workflow'ом `refresh-dependencies`
  (`workflow_dispatch`), который запускается с GitHub-токеном. Без него зависимость
  не подключить. Плюрали пока покрыты только source-guard'ом (13.3).
- ❌ **Этапы 2–5** не начаты (§8: нельзя начинать до закрытия этапа 1, а у этапа 1
  открыт пункт DoD №2).
- ❌ **`sprites.c` из PNG** — решение нужно принять, а не просто реализовать:
  формат пикселей (RGB565/RGBA), размер (55 спрайтов 96×128 ≈ 1.3 МБ в RGB565 при
  выборе «всё в прошивку») и то, кто владеет палитрой. Изменение в `external/vaiwu`.
- ❌ **`FilterQuality.None` и `navigationBarsPadding()`** (§7.4 п.4–5) — решения
  «проверить на API 35» не выполнены: нужно устройство.
- Открытые вопросы §11 (вкладка «Ещё», шрифт, скриншот-тесты, планшет) — без
  решения владельца продукта.

### 13.5 Вынос строк и часы (`7440e2b`, `80027ba`)

- `BattleScreen` получает `hour` параметром; `VaiwuApp` читает час через
  `remember { currentHour() }`. Регрессия: `Calendar.getInstance()` в теле
  композаба (`GameScreens.kt:326`). Гард проверен: на старой версии файла
  находит строку 326.
- Вынесено в `strings.xml` (новые ключи `battle_terrain`, `pomodoro_done`,
  `pet_talked_fallback`, `reminder_*`, `voice_*`, `assistant_*`, `weather_*`,
  `update_*`, `settings_version`):
  - `GitHubUpdateManager` — 15 сообщений (`context.getString`);
  - `ReminderScheduler` — канал, заголовок, текст по умолчанию;
  - `VoiceController` — 5 сообщений (`activity.getString`);
  - `AssistantClient` — теперь принимает `Context`; погода, ошибки и системный
    промпт (`assistant_system_prompt` с `%1$s`/`%2$s`).
- Гард `SourceGuardTest.russianTextIsNotHardcodedInKotlin` на старой версии
  `GitHubUpdateManager.kt` и `GameScreens.kt` находит 16 строк.
- Исключения (только `@Preview`): «Длинная строка для проверки…» и «Очень длинная
  фраза…» в `HomeScreen.kt`.
- Версия в настройках: `settings_version` = «Версия %1$s» (`BuildConfig.VERSION_NAME`).

