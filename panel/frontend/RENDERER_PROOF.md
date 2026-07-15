# Deepslate renderer proof

Дата проверки: 15 июля 2026 года. Целевая схема resource pack: Minecraft Java 26.1.

Проверена официальная npm-версия `deepslate@0.26.0`. Временная установка не меняет зависимости проекта:

```bash
rm -rf /tmp/issue-isekai-deepslate-proof
npm install --prefix /tmp/issue-isekai-deepslate-proof deepslate@0.26.0
node panel/frontend/proof/deepslate-proof.mjs \
  /tmp/issue-isekai-deepslate-proof/node_modules/deepslate/lib/index.js
```

Mesh-proof использует три fixture: vanilla 2D item, custom 2D texture и custom 3D model с parent chain и `display.gui`. Он проверяет item definition, число faces, texture id, конечные UV и наследование GUI transform.

## Результат

- `PASS`: Deepslate 0.26.0 разбирает все три item definitions и строит ожидаемый mesh.
- `FAIL` для production acceptance: Node proof не создаёт настоящий WebGL canvas, не сравнивает pixels/UV с клиентом Minecraft 26.1 и в проекте нет принадлежащего владельцу реального pack fixture с эталонными screenshots.

Итог: `deepslate` не добавляется в `package.json`. 2D texture renderer остаётся рабочим, 3D model показывает подписанный placeholder. Повторить browser proof и подключить `ItemRenderer` можно после появления реального 26.1 pack и pixel fixtures.

Исходники проверки: [Deepslate](https://github.com/misode/deepslate), [npm package](https://www.npmjs.com/package/deepslate).
