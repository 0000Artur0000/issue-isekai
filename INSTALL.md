# Установка на Ubuntu

Поддерживаются Ubuntu 22.04+ на amd64/arm64. Installer проверяет release checksums, при необходимости ставит Docker Engine и Compose plugin из официального apt repository, создаёт `.env` с mode `0600` и ждёт readiness панели.

## Последний стабильный release

```shell
curl -fLO https://github.com/0000Artur0000/issue-isekai/releases/latest/download/install.sh
chmod +x install.sh
./install.sh
```

Для конкретной версии используйте `./install.sh --version 1.2.3`. По умолчанию файлы устанавливаются в `/opt/issue-isekai`, а панель слушает только `127.0.0.1:8080`.

## Локальный release bundle

```shell
./install.sh --bundle-dir /path/to/release --install-dir /opt/issue-isekai
```

При первом запуске installer запросит параметры PostgreSQL, администратора, язык и Telegram. DB password генерируется автоматически; секреты вводятся без отображения. При публичном bind будет показано предупреждение о необходимости HTTPS/reverse proxy.

Повторный запуск сохраняет `.env`. Перед сменой версии создаётся `backups/postgres-<UTC>.sql`; при неудачном обновлении автоматический rollback Flyway не выполняется.

Для обновления до последнего release повторно запустите установленный скрипт:

```shell
cd /opt/issue-isekai
./install.sh
```

Файл `issue-isekai-plugin-<version>.jar` нужно вручную скопировать в `plugins/` Paper-сервера. Installer не настраивает Paper, plugin config или server API keys.
