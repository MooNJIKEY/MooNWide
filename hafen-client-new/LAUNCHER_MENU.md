# MooNWide Launcher Menu

## Текущая реализация

- **Экран 0 — Launcher (Splash / Main Menu)**  
  Показывает «MooNWide» + версию (git), кнопки **Play** и **Exit**.  
  Play → переход к экрану входа (Bootstrap / LoginScreen).  
  Exit → выход из клиента.

- **Экран 1 — Login**  
  Существующий LoginScreen: сессии слева сверху, форма входа по центру, Options, статус сервера.

- **В игре**  
  Обычный GameUI с меню и окнами.

## Целевая структура

### Экраны

1. **Splash / Boot** — MooNWide + версия, загрузка конфигов, сессий, ресурсов, темы, Logger, проверка ресурсов → Main Menu.
2. **Main Menu** — Continue/Play, Sessions, Account, Settings, Exit; справа — инфо по выбранной сессии, «resources ok», cache size, last login.
3. **Sessions** — список профилей (название, сервер, аккаунт, путь к кешу), Select / Edit / Duplicate / Delete, Create new, Import/Export.
4. **Login** — логин, пароль, Remember login/password, Login, Back.
5. **Settings (до входа)** — Graphics, UI Theme, Network, Cache, Sound, Advanced.

### Системы

- **ScreenManager** — активный экран, переходы (fade/instant), back stack (Back).
- **SessionManager** — CRUD сессий, activeSessionId, импорт/экспорт, валидация (host, cachePath). Хранение: `sessions.json`.
- **AuthService** — логин по активной сессии, выдаёт token/connection в GameStart. Пароли: не в json, лучше OS keychain/keystore или только логин.
- **LaunchConfig** — графика, язык, пути кеша, параметры JVM.

### Модель сессии (SessionProfile)

- `id` (uuid)
- `name`
- `serverHost`, `serverPort`
- `accountLabel`, `username` (опционально)
- `cacheDir`, `useCustomCacheDir`
- `lastUsedAt`, `notes`

Credentials отдельно: `username`, `password` (не сохранять в json).

## Файлы

- `haven/LauncherRunner.java` — Runner лаунчера, LauncherMenu (Play/Exit), ExitRunner.
- `haven/MainFrame.java` — uiloop() стартует с `LauncherRunner` вместо `Bootstrap`.

Дальнейшие шаги: добавить экран Sessions, SessionManager, Settings до входа и при необходимости ScreenManager с back stack.
