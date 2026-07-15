# Decision: 3D item renderer отложен до proof

Дата: 2026-07-15. Контекст: FRONTEND_PLAN шаг 4.

План требует proof Deepslate на реальном 26.1 pack (geometry/UV/parent chain в WebGL)
до включения его в bundle. Proof выполняется вручную в браузере с настоящим server
pack — в текущей итерации его нет, поэтому:

- Deepslate **не добавлен** в зависимости.
- Модель с `elements` (3D geometry) показывает placeholder с причиной
  «3D-модель: рендерер подключается после proof» — путь fallback из плана.
- Resolver item definitions (`items.mjs`) и REST-контракт от рендерера не зависят;
  точка подключения — `modelIcon()` в `Inventory.tsx` (ветка `model.elements`).

Как снять блок: загрузить реальный 26.1 pack на Servers, проверить в браузере
vanilla item / custom 2D / custom 3D через Deepslate standalone-страницу; если
geometry/UV сходятся — добавить deepslate и заменить ветку `elements` на рендер
в offscreen WebGL canvas с кэшем bitmap по (packSha256, modelKey, visualComponents).
