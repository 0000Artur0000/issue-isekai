# Установка release bundle

Нужны Linux, Docker Engine и Compose plugin. Полный автоматический установщик для Ubuntu будет добавлен на шаге 12.

1. Скачайте все файлы одного GitHub Release в отдельный каталог.
2. Проверьте их: `sha256sum -c SHA256SUMS`.
3. Запустите `chmod +x install.sh && ./install.sh`.
4. Измените созданный `.env`: обязательно задайте новые `POSTGRES_PASSWORD` и `BOOTSTRAP_ADMIN_PASSWORD`.
5. Повторно запустите `./install.sh` и откройте адрес из `PANEL_PORT`.

`compose.yaml` закрепляет panel image по digest. Файл `issue-isekai-plugin-<version>.jar` скопируйте в каталог `plugins/` Paper-сервера.
