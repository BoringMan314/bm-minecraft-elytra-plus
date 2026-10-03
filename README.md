# [B.M] Minecraft 鞘翅 PLUS

[![Paper](https://img.shields.io/badge/Paper-26.3-2D2D2D)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![GitHub](https://img.shields.io/badge/GitHub-bm--minecraft--elytra--plus-181717?logo=github)](https://github.com/BoringMan314/bm-minecraft-elytra-plus)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

適用於 **Minecraft Paper 26.3** 的插件：鐵砧把鞘翅與胸甲合成並保留雙方能力，砂輪分離還原，工作檯用兩隻消失詛咒鞘翅淨化成無詞條鞘翅。

*适用于 **Minecraft Paper 26.3** 的插件：铁砧把鞘翅与胸甲合成并保留双方能力，砂轮分离还原，工作台用两只消失诅咒鞘翅净化成无词条鞘翅。*<br>
*Minecraft Paper 26.3 向け：金床でエリトラとチェストプレートを合成し、砥石で分離、作業台で消滅の呪いのエリトラ2つを無エンチャントにします。*<br>
*A **Minecraft Paper 26.3** plugin that combines an elytra and chestplate on an anvil, splits them on a grindstone, and cleanses two vanishing-curse elytra on a crafting table.*

> **說明**：僅支援 Paper 26.3 與 Java 25。合成／分離工作檯依 GitHub 常見做法（Vanilla Tweaks / Merged Elytra）：**鐵砧合成、砂輪分離**。

---

## 目錄

- [功能](#功能)
- [系統需求](#系統需求)
- [安裝方式](#安裝方式)
- [指令與權限](#指令與權限)
- [設定檔](#設定檔)
- [本機建置](#本機建置)
- [專案結構](#專案結構)
- [版本與多語系](#版本與多語系)
- [資料與隱私說明](#資料與隱私說明)
- [授權](#授權)
- [問題與建議](#問題與建議)

---

## 功能

- **鐵砧**：左格當主體與外觀（移除插件後也是這件物品）。放入鞘翅與任一材質胸甲即可結合，雙方附魔取較高等級，並保留滑翔與胸甲防禦。
- **砂輪**：單獨放入結合物品即可分離，還原原來的鞘翅與胸甲。飛行中損耗的耐久會回到當主體的那一件。
- **工作檯**：兩隻帶消失詛咒、尚未結合的鞘翅無序合成，得到一隻無詞條的乾淨鞘翅，耐久取較好的那隻。
- 已結合的鞘翅不能再與胸甲結合。

---

## 系統需求

- **Paper 26.3** 伺服器。
- **Java 25**。

---

## 安裝方式

1. 從 [`dist/`](dist/) 選擇所需語系 JAR。
2. 將 JAR 放入 Paper 伺服器的 `plugins/` 資料夾。
3. 啟動伺服器後，設定檔建立於 `plugins/bm-minecraft-elytra-plus/`。

> 請勿同時安裝多個語系 JAR；它們是同一插件的不同預設語言版本。

---

## 指令與權限

| 指令 | 說明 | 權限 |
| --- | --- | --- |
| `/bm-minecraft-elytra-plus 0/1` | 開關功能 | `.admin` |
| `/bm-minecraft-elytra-plus reload` | 重載設定 | `.admin` |
| `/bm-minecraft-elytra-plus info` | 顯示資訊 | `.admin` |
| `/bm-minecraft-elytra-plus status` | 顯示狀態 | `.admin` |

`bm-minecraft-elytra-plus.use` 預設所有玩家可用結合、分離與淨化；`.admin` 預設 OP 可用。

---

## 設定檔

```yml
enabled: true
admin-require-op: true
anvil-cost: 1
```

---

## 本機建置

執行 `build.bat`；預設會在結束時暫停。自動化環境使用：

```bat
build.bat --no-pause
```

會在 `dist/` 產生 `zh_TW`、`zh_CN`、`ja_JP`、`en_US` JAR。

---

## 專案結構

```text
src/main/java/bm.minecraft.elytra.plus/
src/main/resources/lang/
src/main/resources/config.yml
```

---

## 版本與多語系

版本為 `26.3_0.0.1`；建置時以 `active-language.yml` 選擇四種語系之一。

---

## 資料與隱私說明

本插件不另外儲存玩家資料。結合資訊寫在該物品的 PDC 上，僅供本機伺服器合成與分離使用。

---

## 授權

本專案以 [MIT License](LICENSE) 授權。

---

## 問題與建議

歡迎透過 [GitHub Issues](https://github.com/BoringMan314/bm-minecraft-elytra-plus/issues) 回報錯誤或提出改善建議。回報時請一併提供 Paper 版本、Java 版本、設定檔與完整錯誤日誌。
